package game.audio;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

/**
 * A volume envelope: up to four scripts, each a press program and a release program. A program is a
 * list of two-byte commands ending in End. Points ramp to a volume over a duration, and the other
 * commands scale the volume or repeat a section.
 */
public class Envelope
{
	public enum Key implements XmlKey
	{
		// @formatter:off
		Envelope, Commands, Press, Release, Point, SetScale, AddScale, StartLoop, EndLoop, End,
		envName, relative, duration, volume, scale, amount, count
		// @formatter:on
	}

	private enum Op
	{
		POINT, SET_SCALE, ADD_SCALE, START_LOOP, END_LOOP, END
	}

	private static class Command
	{
		private final Op op;
		private final int value;
		private final int durationIndex;

		private Command(Op op, int value, int durationIndex)
		{
			this.op = op;
			this.value = value;
			this.durationIndex = durationIndex;
		}
	}

	private static final int CMD_END_LOOP = 0xFB;
	private static final int CMD_START_LOOP = 0xFC;
	private static final int CMD_ADD_SCALE = 0xFD;
	private static final int CMD_SET_SCALE = 0xFE;
	private static final int CMD_END = 0xFF;
	private static final int MAX_VOLUME = 127;
	private static final int MAX_SCALE = 128;

	public static final int MAX_SCRIPTS = 4;

	private static final List<String> DURATIONS = List.of(
		"60s", "55s", "50s", "45s", "40s", "35s", "30s", "27.5s", "25s", "22.5s",
		"20s", "19s", "18s", "17s", "16s", "15s", "14s", "13s", "12s", "11s",
		"10s", "9s", "8s", "7s", "6s", "5s", "4.5s", "4s", "3.5s", "3s",
		"2750ms", "2500ms", "2250ms", "2s", "1900ms", "1800ms", "1700ms", "1600ms", "1500ms", "1400ms",
		"1300ms", "1200ms", "1100ms", "1s", "950ms", "900ms", "850ms", "800ms", "750ms", "700ms",
		"650ms", "600ms", "550ms", "500ms", "450ms", "400ms", "375ms", "350ms", "325ms", "300ms",
		"290ms", "280ms", "270ms", "260ms", "250ms", "240ms", "230ms", "220ms", "210ms", "200ms",
		"190ms", "180ms", "170ms", "160ms", "150ms", "140ms", "130ms", "120ms", "110ms", "100ms",
		// the engine has no 15- or 13-unit entries
		"16units", "14units", "12units", "11units", "10units", "9units", "8units", "7units",
		"6units", "5units", "4units", "3units", "2units", "1unit", "0");

	/** Each script is { press bytes, release bytes }. */
	public final List<int[][]> scripts = new ArrayList<>();

	public String name;

	/** Reads an envelope at an offset in a bank file. */
	public static Envelope read(byte[] data, int offset)
	{
		Envelope env = new Envelope();
		int count = data[offset] & 0xFF;
		for (int i = 0; i < count; i++) {
			int press = offset + readShort(data, offset + 4 + 4 * i);
			int release = offset + readShort(data, offset + 6 + 4 * i);
			env.scripts.add(new int[][] { readProgram(data, press), readProgram(data, release) });
		}
		return env;
	}

	private static int readShort(byte[] data, int at)
	{
		return (data[at] & 0xFF) << 8 | data[at + 1] & 0xFF;
	}

	private static int[] readProgram(byte[] data, int offset)
	{
		List<Integer> bytes = new ArrayList<>();
		for (int at = offset; at + 1 < data.length;) {
			int op = data[at++] & 0xFF;
			bytes.add(op);
			bytes.add(data[at++] & 0xFF);
			if (op == CMD_END)
				return bytes.stream().mapToInt(Integer::intValue).toArray();
		}
		throw new IllegalArgumentException("an envelope program does not end");
	}

	public byte[] write()
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// every press program comes first, then every release program
		int[] offsets = new int[scripts.size() * 2];
		int at = 4 + 4 * scripts.size();
		for (int part = 0; part < 2; part++) {
			for (int i = 0; i < scripts.size(); i++) {
				offsets[2 * i + part] = at;
				at += scripts.get(i)[part].length;
			}
		}

		out.write(scripts.size());
		out.write(0);
		out.write(0);
		out.write(0);
		for (int offset : offsets) {
			out.write(offset >> 8);
			out.write(offset);
		}
		for (int part = 0; part < 2; part++) {
			for (int[][] script : scripts) {
				for (int b : script[part])
					out.write(b);
			}
		}
		return out.toByteArray();
	}

	public void toXml(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(Key.Envelope, false);
		xmw.addAttribute(tag, Key.envName, name);
		xmw.openTag(tag);

		for (int[][] script : scripts) {
			XmlTag commands = xmw.createTag(Key.Commands, false);
			xmw.openTag(commands);

			XmlTag press = xmw.createTag(Key.Press, false);
			xmw.openTag(press);
			writeCommands(xmw, decode(script[0], false));
			xmw.closeTag(press);

			XmlTag release = xmw.createTag(Key.Release, false);
			if ((script[1][1] & 0x80) != 0 && script[1][0] < DURATIONS.size())
				xmw.addBoolean(release, Key.relative, true);
			xmw.openTag(release);
			writeCommands(xmw, decode(script[1], true));
			xmw.closeTag(release);

			xmw.closeTag(commands);
		}
		xmw.closeTag(tag);
	}

	public static Envelope fromXml(XmlReader xmr, Element elem)
	{
		Envelope env = new Envelope();
		xmr.requiresAttribute(elem, Key.envName);
		env.name = xmr.getAttribute(elem, Key.envName);

		for (Element commands : xmr.getTags(elem, Key.Commands)) {
			if (env.scripts.size() == MAX_SCRIPTS)
				xmr.complain("envelope " + env.name + " has more than " + MAX_SCRIPTS + " Commands");

			Element release = xmr.getUniqueRequiredTag(commands, Key.Release);
			List<Command> pressCommands = readCommands(xmr, xmr.getUniqueRequiredTag(commands, Key.Press), false);
			List<Command> releaseCommands = readCommands(xmr, release, true);
			boolean relative = xmr.hasAttribute(release, Key.relative) && xmr.readBoolean(release, Key.relative);
			env.scripts.add(new int[][] { encode(pressCommands, false), encode(releaseCommands, relative) });
		}
		return env;
	}

	private static List<Command> decode(int[] bytes, boolean release)
	{
		List<Command> commands = new ArrayList<>();
		for (int i = 0; i < bytes.length; i += 2) {
			int op = bytes[i];
			int arg = bytes[i + 1];
			if (op < DURATIONS.size())
				commands.add(new Command(Op.POINT, arg & 0x7F, op));
			else
				commands.add(switch (op) {
					case CMD_END_LOOP -> new Command(Op.END_LOOP, 0, 0);
					case CMD_START_LOOP -> new Command(Op.START_LOOP, arg, 0);
					case CMD_ADD_SCALE -> new Command(Op.ADD_SCALE, (byte) arg, 0);
					case CMD_SET_SCALE -> new Command(Op.SET_SCALE, Math.min(arg, MAX_SCALE), 0);
					case CMD_END -> new Command(Op.END, 0, 0);
					default -> throw new IllegalArgumentException(String.format("unknown envelope opcode %02X", op));
				});
		}
		return commands;
	}

	private static int[] encode(List<Command> commands, boolean relativeRelease)
	{
		int[] bytes = new int[commands.size() * 2];
		for (int i = 0; i < commands.size(); i++) {
			Command c = commands.get(i);
			bytes[2 * i] = switch (c.op) {
				case POINT -> c.durationIndex;
				case SET_SCALE -> CMD_SET_SCALE;
				case ADD_SCALE -> CMD_ADD_SCALE;
				case START_LOOP -> CMD_START_LOOP;
				case END_LOOP -> CMD_END_LOOP;
				case END -> CMD_END;
			};
			bytes[2 * i + 1] = switch (c.op) {
				case END_LOOP, END -> 0;
				case POINT -> c.value | (relativeRelease ? 0x80 : 0);
				default -> c.value & 0xFF;
			};
		}
		return bytes;
	}

	private static void writeCommands(XmlWriter xmw, List<Command> commands)
	{
		for (Command c : commands) {
			XmlTag tag = xmw.createTag(switch (c.op) {
				case POINT -> Key.Point;
				case SET_SCALE -> Key.SetScale;
				case ADD_SCALE -> Key.AddScale;
				case START_LOOP -> Key.StartLoop;
				case END_LOOP -> Key.EndLoop;
				case END -> Key.End;
			}, true);

			switch (c.op) {
				case POINT -> {
					xmw.addAttribute(tag, Key.duration, DURATIONS.get(c.durationIndex));
					AudioXml.addHex(xmw, tag, Key.volume, 2, c.value);
				}
				case SET_SCALE -> AudioXml.addHex(xmw, tag, Key.scale, 2, c.value);
				case ADD_SCALE -> AudioXml.addHex(xmw, tag, Key.amount, 2, c.value);
				case START_LOOP -> xmw.addInt(tag, Key.count, c.value);
				default -> {}
			}
			xmw.printTag(tag);
		}
	}

	private static List<Command> readCommands(XmlReader xmr, Element parent, boolean release)
	{
		List<Command> commands = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (!(child instanceof Element elem))
				continue;

			Key tag = null;
			for (Key key : List.of(Key.Point, Key.SetScale, Key.AddScale, Key.StartLoop, Key.EndLoop, Key.End)) {
				if (key.toString().equals(elem.getTagName()))
					tag = key;
			}
			if (tag == null)
				xmr.complain("unknown envelope command " + elem.getTagName());

			commands.add(switch (tag) {
				case Point -> {
					xmr.requiresAttribute(elem, Key.duration);
					int index = DURATIONS.indexOf(xmr.getAttribute(elem, Key.duration));
					if (index < 0)
						xmr.complain("unknown envelope duration " + xmr.getAttribute(elem, Key.duration));
					yield new Command(Op.POINT, AudioXml.readHex(xmr, elem, Key.volume, 0, MAX_VOLUME), index);
				}
				case SetScale -> new Command(Op.SET_SCALE, AudioXml.readHex(xmr, elem, Key.scale, 0, MAX_SCALE), 0);
				case AddScale -> new Command(Op.ADD_SCALE, AudioXml.readHex(xmr, elem, Key.amount, -128, 127), 0);
				case StartLoop -> new Command(Op.START_LOOP, AudioXml.readInt(xmr, elem, Key.count, 0, 255), 0);
				case EndLoop -> new Command(Op.END_LOOP, 0, 0);
				default -> new Command(Op.END, 0, 0);
			});
		}

		validate(xmr, parent, commands, release);
		return commands;
	}

	private static void validate(XmlReader xmr, Element parent, List<Command> commands, boolean release)
	{
		boolean inLoop = false;
		for (int i = 0; i < commands.size(); i++) {
			Op op = commands.get(i).op;
			if (op == Op.START_LOOP && inLoop)
				xmr.complain(parent.getTagName() + " has nested loops");
			if (op == Op.END_LOOP && !inLoop)
				xmr.complain(parent.getTagName() + " has EndLoop without StartLoop");
			if (op == Op.START_LOOP || op == Op.END_LOOP)
				inLoop = op == Op.START_LOOP;
			if (op == Op.END && i != commands.size() - 1)
				xmr.complain("End must be the last command in " + parent.getTagName());
		}
		if (inLoop)
			xmr.complain(parent.getTagName() + " has StartLoop without EndLoop");
		if (commands.isEmpty() || commands.get(commands.size() - 1).op != Op.END)
			xmr.complain(parent.getTagName() + " must end with End");
		if (release && commands.get(0).op != Op.POINT)
			xmr.complain(parent.getTagName() + " must begin with a Point");
	}
}
