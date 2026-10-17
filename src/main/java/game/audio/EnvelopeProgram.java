package game.audio;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

/** The bytecode and XML forms of the custom press envelopes that sound effect sequences can select. */
public final class EnvelopeProgram
{
	private enum Key implements XmlKey
	{
		// @formatter:off
		Point, SetScale, AddScale, StartLoop, EndLoop, End, duration, volume, scale, amount, count
		// @formatter:on
	}

	private static final int CMD_END_LOOP = 0xFB;
	private static final int CMD_START_LOOP = 0xFC;
	private static final int CMD_ADD_SCALE = 0xFD;
	private static final int CMD_SET_SCALE = 0xFE;
	private static final int CMD_END = 0xFF;
	private static final int MAX_VOLUME = 127;
	private static final int MAX_SCALE = 128;

	/** Names of the engine's 95 point durations, in table order. The engine has no 15- or 13-unit entries. */
	private static final List<String> DURATIONS = List.of(
		"60s", "55s", "50s", "45s", "40s", "35s", "30s", "27.5s", "25s", "22.5s",
		"20s", "19s", "18s", "17s", "16s", "15s", "14s", "13s", "12s", "11s",
		"10s", "9s", "8s", "7s", "6s", "5s", "4.5s", "4s", "3.5s", "3s",
		"2750ms", "2500ms", "2250ms", "2s", "1900ms", "1800ms", "1700ms", "1600ms", "1500ms", "1400ms",
		"1300ms", "1200ms", "1100ms", "1s", "950ms", "900ms", "850ms", "800ms", "750ms", "700ms",
		"650ms", "600ms", "550ms", "500ms", "450ms", "400ms", "375ms", "350ms", "325ms", "300ms",
		"290ms", "280ms", "270ms", "260ms", "250ms", "240ms", "230ms", "220ms", "210ms", "200ms",
		"190ms", "180ms", "170ms", "160ms", "150ms", "140ms", "130ms", "120ms", "110ms", "100ms",
		"16units", "14units", "12units", "11units", "10units", "9units", "8units", "7units",
		"6units", "5units", "4units", "3units", "2units", "1unit", "0");

	private EnvelopeProgram()
	{}

	public static EnvelopeCommand decodeCommand(int op, int arg)
	{
		if (op < DURATIONS.size()) {
			EnvelopeCommand command = new EnvelopeCommand(EnvelopeOp.POINT, arg & 0x7F);
			command.durationIndex = op;
			return command;
		}
		return switch (op) {
			case CMD_END_LOOP -> new EnvelopeCommand(EnvelopeOp.END_LOOP);
			case CMD_START_LOOP -> new EnvelopeCommand(EnvelopeOp.START_LOOP, arg);
			case CMD_ADD_SCALE -> new EnvelopeCommand(EnvelopeOp.ADD_SCALE, (byte) arg);
			case CMD_SET_SCALE -> new EnvelopeCommand(EnvelopeOp.SET_SCALE, Math.min(arg, MAX_SCALE));
			case CMD_END -> new EnvelopeCommand(EnvelopeOp.END);
			default -> throw new IllegalArgumentException(String.format("unknown envelope opcode %02X", op));
		};
	}

	public static byte[] encode(List<EnvelopeCommand> commands)
	{
		byte[] bytes = new byte[commands.size() * 2];
		for (int i = 0; i < commands.size(); i++) {
			EnvelopeCommand command = commands.get(i);
			int op = switch (command.op) {
				case POINT -> command.durationIndex;
				case SET_SCALE -> CMD_SET_SCALE;
				case ADD_SCALE -> CMD_ADD_SCALE;
				case START_LOOP -> CMD_START_LOOP;
				case END_LOOP -> CMD_END_LOOP;
				case END -> CMD_END;
			};
			bytes[i * 2] = (byte) op;
			bytes[i * 2 + 1] = (byte) command.value;
		}
		return bytes;
	}

	/** Returns the first problem in an envelope, or null if there is none. */
	public static String validate(List<EnvelopeCommand> commands)
	{
		boolean loopActive = false;
		for (int i = 0; i < commands.size(); i++) {
			EnvelopeCommand command = commands.get(i);
			switch (command.op) {
				case POINT -> {
					if (command.value < 0 || command.value > MAX_VOLUME)
						return "Point volume must be from 0 to " + MAX_VOLUME;
				}
				case SET_SCALE -> {
					if (command.value < 0 || command.value > MAX_SCALE)
						return "SetScale scale must be from 0 to " + MAX_SCALE;
				}
				case ADD_SCALE -> {
					if (command.value < -128 || command.value > 127)
						return "AddScale amount must be from -128 to 127";
				}
				case START_LOOP -> {
					if (command.value < 0 || command.value > 255)
						return "StartLoop count must be from 0 to 255";
					if (loopActive)
						return "the engine has one envelope loop register, so loops cannot nest";
					loopActive = true;
				}
				case END_LOOP -> {
					if (!loopActive)
						return "EndLoop comes before any StartLoop";
					loopActive = false;
				}
				case END -> {
					if (i + 1 != commands.size())
						return "End must be the last command";
				}
			}
		}
		if (loopActive)
			return "StartLoop has no EndLoop";
		if (commands.isEmpty() || commands.get(commands.size() - 1).op != EnvelopeOp.END)
			return "the envelope must end with End";
		return null;
	}

	public static List<EnvelopeCommand> readCommands(XmlReader xmr, Element parent)
	{
		List<EnvelopeCommand> commands = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (!(child instanceof Element elem))
				continue;

			Key key = null;
			for (Key k : Key.values()) {
				if (k.toString().equals(elem.getTagName()) && k.ordinal() <= Key.End.ordinal())
					key = k;
			}
			if (key == null)
				xmr.complain("unknown envelope command " + elem.getTagName());

			EnvelopeCommand command = switch (key) {
				case Point -> {
					xmr.requiresAttribute(elem, Key.duration);
					String duration = xmr.getAttribute(elem, Key.duration);
					if (!DURATIONS.contains(duration))
						xmr.complain("unknown envelope duration " + duration);
					EnvelopeCommand point = new EnvelopeCommand(EnvelopeOp.POINT,
						AudioXml.readHex(xmr, elem, Key.volume, 0, MAX_VOLUME));
					point.durationIndex = DURATIONS.indexOf(duration);
					yield point;
				}
				case SetScale -> new EnvelopeCommand(EnvelopeOp.SET_SCALE, AudioXml.readHex(xmr, elem, Key.scale, 0, MAX_SCALE));
				case AddScale -> new EnvelopeCommand(EnvelopeOp.ADD_SCALE, AudioXml.readHex(xmr, elem, Key.amount, -128, 127));
				case StartLoop -> new EnvelopeCommand(EnvelopeOp.START_LOOP, AudioXml.readInt(xmr, elem, Key.count, 0, 255));
				case EndLoop -> new EnvelopeCommand(EnvelopeOp.END_LOOP);
				default -> new EnvelopeCommand(EnvelopeOp.END);
			};
			commands.add(command);
		}

		String problem = validate(commands);
		if (problem != null)
			xmr.complain(parent.getTagName() + " '" + parent.getAttribute("name") + "': " + problem);
		return commands;
	}

	public static void writeCommands(XmlWriter xmw, List<EnvelopeCommand> commands)
	{
		for (EnvelopeCommand command : commands) {
			XmlTag tag;
			switch (command.op) {
				case POINT -> {
					tag = xmw.createTag(Key.Point, true);
					xmw.addAttribute(tag, Key.duration, DURATIONS.get(command.durationIndex));
					AudioXml.addHex(xmw, tag, Key.volume, 2, command.value);
				}
				case SET_SCALE -> {
					tag = xmw.createTag(Key.SetScale, true);
					AudioXml.addHex(xmw, tag, Key.scale, 2, command.value);
				}
				case ADD_SCALE -> {
					tag = xmw.createTag(Key.AddScale, true);
					AudioXml.addHex(xmw, tag, Key.amount, 2, command.value);
				}
				case START_LOOP -> {
					tag = xmw.createTag(Key.StartLoop, true);
					xmw.addInt(tag, Key.count, command.value);
				}
				case END_LOOP -> tag = xmw.createTag(Key.EndLoop, true);
				default -> tag = xmw.createTag(Key.End, true);
			}
			xmw.printTag(tag);
		}
	}
}
