package game.audio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import app.input.InputFileException;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

/**
 * An ambient sequence (MSEQ): a stream of timed commands for up to nine instrument tracks and a drum track,
 * plus a list of volume and tuning ramps. On disk it is an XML file, and in the archive it is an MSEQ file.
 * Instruments are named by sample, and the banks in banks.xml (and the radio bank, which is the auxiliary
 * bank in slot 2) say where each sample is.
 */
public class Mseq
{
	public enum Key implements XmlKey
	{
		// @formatter:off
		Mseq, TrackRamps, TrackRamp, Commands,
		code, firstVoice, track, type, time, delta, goal, wav, envelope, volume, pan, reverb, pitch, tune, drum,
		duration, loopID, count, resumable
		// @formatter:on
	}

	private enum Kind
	{
		INT, HEX, BOOL
	}

	private record Attr(Key key, Kind kind, int min, int max)
	{
		Attr(Key key, int min, int max)
		{
			this(key, Kind.INT, min, max);
		}
	}

	private static final Attr TRACK = new Attr(Key.track, 0, 9);
	private static final Attr INSTRUMENT_TRACK = new Attr(Key.track, 0, 8);
	private static final Attr PITCH = new Attr(Key.pitch, 0, 255);
	private static final Attr VOLUME = new Attr(Key.volume, Kind.HEX, 0, 127);
	private static final Attr LOOP = new Attr(Key.loopID, 0, 1);

	/** The commands of the stream. The tag of a command is the name of its Op. */
	public enum Op implements XmlKey
	{
		// @formatter:off
		Delay(new Attr(Key.duration, 1, 0x877)),
		SetVolume(TRACK, VOLUME),
		SetPan(TRACK, new Attr(Key.pan, 0, 127)),
		SetReverb(TRACK, new Attr(Key.reverb, 0, 255)),
		SetInstrument(INSTRUMENT_TRACK, new Attr(Key.envelope, 0, 3)),
		StopSound(TRACK, PITCH),
		PlaySound(INSTRUMENT_TRACK, PITCH, VOLUME),
		PlayDrum(new Attr(Key.drum, 0, 127), VOLUME),
		StartLoop(LOOP),
		EndLoop(LOOP, new Attr(Key.count, 0, 31)),
		SetTune(INSTRUMENT_TRACK, new Attr(Key.tune, -32768, 32767)),
		SetResumable(TRACK, new Attr(Key.resumable, Kind.BOOL, 0, 1));
		// @formatter:on

		private final Attr[] attrs;

		Op(Attr ... attrs)
		{
			this.attrs = attrs;
		}
	}

	/** The values are in the order of the attributes of the op; a SetInstrument also has a wav. */
	public record Command(Op op, int[] values, String wav)
	{
		Command(Op op, int ... values)
		{
			this(op, values, null);
		}
	}

	public record Ramp(int track, boolean isVolume, int time, int delta, int goal)
	{}

	public static final String EXTENSION = ".mseq";

	private static final int HEADER_SIZE = 0x18;
	private static final int RAMP_SIZE = 8;
	private static final int MAX_RAMPS = 0xFF;
	private static final int DRUM_TRACK = 9;
	private static final int VOICES = 24;
	private static final int MAX_DELAY = 0x877;
	private static final int LONG_DELAY = 0x78;

	private static final int STOP_SOUND = 0x8;
	private static final int PLAY_SOUND = 0x9;
	private static final int VOLUME_PAN = 0xA;
	private static final int MULTI = 0xB;
	private static final int SET_INSTRUMENT = 0xC;
	private static final int TUNING = 0xE;
	private static final int START_LOOP = 0x66;
	private static final int END_LOOP = 0x67;
	private static final int SET_REVERB = 0x68;
	private static final int SET_RESUMABLE = 0x69;

	private String code;
	private int firstVoice;
	private final List<Ramp> ramps = new ArrayList<>();
	private final List<Command> commands = new ArrayList<>();

	public List<Command> commands()
	{
		return commands;
	}

	public List<Ramp> ramps()
	{
		return ramps;
	}

	/** Reads an MSEQ file. */
	public static Mseq decode(File source, byte[] data, Presets.Addresses addresses)
	{
		try {
			return decode(source, ByteBuffer.wrap(data), addresses);
		}
		catch (BufferUnderflowException | IndexOutOfBoundsException e) {
			throw new InputFileException(source, "the file ends inside a command or the header");
		}
	}

	private static Mseq decode(File source, ByteBuffer bb, Presets.Addresses addresses)
	{
		if (!Sbn.readString(bb.array(), 0, 4).equals("MSEQ"))
			throw new InputFileException(source, "not an MSEQ file");

		Mseq mseq = new Mseq();
		mseq.code = Sbn.readString(bb.array(), 8, 4);
		mseq.firstVoice = bb.get(12) & 0xFF;
		int rampCount = bb.get(13) & 0xFF;
		int rampsOffset = bb.getShort(14) & 0xFFFF;
		bb.position(rampsOffset);
		for (int i = 0; i < rampCount; i++) {
			int track = bb.get() & 0xFF;
			int type = bb.get() & 0xFF;
			if (type > 1)
				throw new InputFileException(source, "track ramp %d has the unknown type %d", i, type);
			mseq.ramps.add(new Ramp(track, type == 1, bb.getShort() & 0xFFFF, bb.getShort(), bb.getShort()));
		}

		bb.position(bb.getShort(16) & 0xFFFF);
		for (int op = bb.get() & 0xFF; op != 0; op = bb.get() & 0xFF) {
			if (op < 0x80) {
				mseq.commands.add(new Command(Op.Delay, op >= LONG_DELAY ? ((op & 7) << 8) + (bb.get() & 0xFF) + LONG_DELAY : op));
				continue;
			}

			int track = op & 0xF;
			int arg = bb.get() & 0xFF;
			Command command = switch (op >> 4) {
				case STOP_SOUND -> new Command(Op.StopSound, track, arg);
				case PLAY_SOUND -> track == DRUM_TRACK ? new Command(Op.PlayDrum, arg, bb.get() & 0xFF)
					: new Command(Op.PlaySound, track, arg, bb.get() & 0xFF);
				case VOLUME_PAN -> (arg & 0x80) != 0 ? new Command(Op.SetPan, track, arg & 0x7F) : new Command(Op.SetVolume, track, arg);
				case MULTI -> {
					int value = bb.get() & 0xFF;
					yield switch (arg) {
						case START_LOOP -> new Command(Op.StartLoop, value & 1);
						case END_LOOP -> new Command(Op.EndLoop, value & 1, (value & 0x7C) >> 2);
						case SET_REVERB -> new Command(Op.SetReverb, track, value);
						case SET_RESUMABLE -> new Command(Op.SetResumable, track, value == 1 ? 1 : 0);
						default -> throw new InputFileException(source, "unknown command %02X %02X", op, arg);
					};
				}
				case SET_INSTRUMENT -> new Command(Op.SetInstrument, new int[] { track, arg & 3 },
					addresses.name(source, "SetInstrument", arg, bb.get() & 0xFF));
				case TUNING -> new Command(Op.SetTune, track, (short) (arg << 8 | bb.get() & 0xFF));
				default -> throw new InputFileException(source, "unknown command %02X", op);
			};
			mseq.commands.add(command);
		}
		return mseq;
	}

	/** Reads an MSEQ source file. */
	public static Mseq read(File source)
	{
		XmlReader xmr = new XmlReader(source);
		Element root = xmr.getRootElement();
		if (!root.getTagName().equals(Key.Mseq.toString()))
			xmr.complain("expected root tag " + Key.Mseq);

		Mseq mseq = new Mseq();
		xmr.requiresAttribute(root, Key.code);
		mseq.code = xmr.getAttribute(root, Key.code);
		if (!mseq.code.matches("[\\x21-\\x7E]{1,4}"))
			xmr.complain("code must be one through four printable characters without spaces: " + mseq.code);
		mseq.firstVoice = AudioXml.readInt(xmr, root, Key.firstVoice, 0, VOICES - 1);

		Element rampList = xmr.getUniqueTag(root, Key.TrackRamps);
		for (Element elem : rampList == null ? List.<Element>of() : xmr.getTags(rampList, Key.TrackRamp)) {
			if (mseq.ramps.size() == MAX_RAMPS)
				xmr.complain("there can be at most " + MAX_RAMPS + " track ramps");
			mseq.ramps.add(readRamp(xmr, elem));
		}

		int index = 0;
		for (Node child = xmr.getUniqueRequiredTag(root, Key.Commands).getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element elem) {
				try {
					mseq.commands.add(readCommand(xmr, elem));
				}
				catch (InputFileException e) {
					throw new InputFileException(source, "command %d, %s: %s", index, elem.getTagName(), e.getMessage());
				}
				index++;
			}
		}

		mseq.checkLoops(xmr);
		return mseq;
	}

	private static Ramp readRamp(XmlReader xmr, Element elem)
	{
		int track = AudioXml.readInt(xmr, elem, Key.track, 0, DRUM_TRACK);
		xmr.requiresAttribute(elem, Key.type);
		String type = xmr.getAttribute(elem, Key.type);
		if (!type.equals("tune") && !type.equals("volume"))
			xmr.complain("TrackRamp type must be tune or volume: " + type);

		boolean isVolume = type.equals("volume");
		int time = AudioXml.readInt(xmr, elem, Key.time, 1, 32767);
		if (!isVolume)
			return new Ramp(track, false, time, AudioXml.readInt(xmr, elem, Key.delta, -32768, 32767),
				AudioXml.readInt(xmr, elem, Key.goal, -32768, 32767));
		return new Ramp(track, true, time, AudioXml.readHex(xmr, elem, Key.delta, -32768, 32767),
			AudioXml.readHex(xmr, elem, Key.goal, 0, 32767));
	}

	private static Command readCommand(XmlReader xmr, Element elem)
	{
		Op op = null;
		for (Op candidate : Op.values()) {
			if (candidate.toString().equals(elem.getTagName()))
				op = candidate;
		}
		if (op == null)
			xmr.complain("unknown command " + elem.getTagName());

		int[] values = new int[op.attrs.length];
		for (int i = 0; i < values.length; i++) {
			Attr a = op.attrs[i];
			values[i] = switch (a.kind) {
				case HEX -> AudioXml.readHex(xmr, elem, a.key, a.min, a.max);
				case BOOL -> {
					xmr.requiresAttribute(elem, a.key);
					yield xmr.readBoolean(elem, a.key) ? 1 : 0;
				}
				default -> a.key == Key.envelope ? AudioXml.readInt(xmr, elem, a.key, 0, a.min, a.max)
					: AudioXml.readInt(xmr, elem, a.key, a.min, a.max);
			};
		}

		String wav = null;
		if (op == Op.SetInstrument) {
			xmr.requiresAttribute(elem, Key.wav);
			wav = xmr.getAttribute(elem, Key.wav);
		}
		return new Command(op, values, wav);
	}

	/** A loop that repeats forever must contain a delay, or the sequence never yields. */
	private void checkLoops(XmlReader xmr)
	{
		int[] starts = { -1, -1 };
		for (int i = 0; i < commands.size(); i++) {
			Command c = commands.get(i);
			if (c.op == Op.StartLoop) {
				starts[c.values[0]] = i;
			}
			else if (c.op == Op.EndLoop && c.values[1] == 0 && starts[c.values[0]] >= 0
				&& commands.subList(starts[c.values[0]], i).stream().noneMatch(other -> other.op == Op.Delay)) {
				xmr.complain(String.format("command %d, EndLoop: loop %d repeats forever without a Delay", i, c.values[0]));
			}
		}
	}

	public void write(File out) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(out)) {
			XmlTag root = xmw.createTag(Key.Mseq, false);
			xmw.addAttribute(root, Key.code, code);
			xmw.addInt(root, Key.firstVoice, firstVoice);
			xmw.openTag(root);

			XmlTag rampList = xmw.createTag(Key.TrackRamps, ramps.isEmpty());
			if (ramps.isEmpty())
				xmw.printTag(rampList);
			else
				xmw.openTag(rampList);
			for (Ramp ramp : ramps) {
				XmlTag tag = xmw.createTag(Key.TrackRamp, true);
				xmw.addInt(tag, Key.track, ramp.track);
				xmw.addAttribute(tag, Key.type, ramp.isVolume ? "volume" : "tune");
				xmw.addInt(tag, Key.time, ramp.time);
				if (ramp.isVolume) {
					AudioXml.addHex(xmw, tag, Key.delta, 4, ramp.delta);
					AudioXml.addHex(xmw, tag, Key.goal, 4, ramp.goal);
				}
				else {
					xmw.addInt(tag, Key.delta, ramp.delta);
					xmw.addInt(tag, Key.goal, ramp.goal);
				}
				xmw.printTag(tag);
			}
			if (!ramps.isEmpty())
				xmw.closeTag(rampList);

			XmlTag commandList = xmw.createTag(Key.Commands, false);
			xmw.openTag(commandList);
			for (Command c : commands) {
				XmlTag tag = xmw.createTag(c.op, true);
				for (int i = 0; i < c.values.length; i++) {
					Attr a = c.op.attrs[i];
					if (c.op == Op.SetInstrument && a.key == Key.envelope) {
						xmw.addAttribute(tag, Key.wav, c.wav);
						if (c.values[i] == 0)
							continue;
					}

					switch (a.kind) {
						case HEX -> AudioXml.addHex(xmw, tag, a.key, 2, c.values[i]);
						case BOOL -> xmw.addBoolean(tag, a.key, c.values[i] == 1);
						default -> xmw.addInt(tag, a.key, c.values[i]);
					}
				}
				xmw.printTag(tag);
			}
			xmw.closeTag(commandList);

			xmw.closeTag(root);
			xmw.save();
		}
	}

	/**
	 * Clears the bits of an MSEQ that the engine ignores, so files that play the same compare equal: all but
	 * the loop ID of a StartLoop, the second bit of an EndLoop, and the difference between the SetResumable
	 * values other than 1.
	 */
	static byte[] canonical(File source, byte[] data)
	{
		byte[] copy = data.clone();
		try {
			int at = ByteBuffer.wrap(copy).getShort(16) & 0xFFFF;
			while (copy[at] != 0) {
				int op = copy[at] & 0xFF;
				if (op < 0x80) {
					at += op >= LONG_DELAY ? 2 : 1;
					continue;
				}

				int cmd = op >> 4;
				if (cmd == MULTI && copy[at + 1] == START_LOOP)
					copy[at + 2] &= 1;
				else if (cmd == MULTI && copy[at + 1] == END_LOOP)
					copy[at + 2] &= 0x7D;
				else if (cmd == MULTI && copy[at + 1] == SET_RESUMABLE)
					copy[at + 2] = (byte) (copy[at + 2] == 1 ? 1 : 0);
				at += cmd == STOP_SOUND || cmd == VOLUME_PAN ? 2 : 3;
			}
		}
		catch (IndexOutOfBoundsException e) {
			throw new InputFileException(source, "the file ends inside a command or the header");
		}
		return copy;
	}

	public byte[] encode(File source, Presets.Addresses addresses)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[HEADER_SIZE]);
		for (Command c : commands) {
			int[] v = c.values;
			switch (c.op) {
				case Delay -> {
					if (v[0] < LONG_DELAY) {
						out.write(v[0]);
					}
					else {
						out.write(LONG_DELAY | ((v[0] - LONG_DELAY) >> 8) & 7);
						out.write((v[0] - LONG_DELAY) & 0xFF);
					}
				}
				case SetVolume -> out.writeBytes(new byte[] { (byte) (VOLUME_PAN << 4 | v[0]), (byte) v[1] });
				case SetPan -> out.writeBytes(new byte[] { (byte) (VOLUME_PAN << 4 | v[0]), (byte) (v[1] | 0x80) });
				case SetReverb -> out.writeBytes(new byte[] { (byte) (MULTI << 4 | v[0]), SET_REVERB, (byte) v[1] });
				case SetInstrument -> {
					int[] address = addresses.address(source, "SetInstrument", c.wav, v[1]);
					out.writeBytes(new byte[] { (byte) (SET_INSTRUMENT << 4 | v[0]), (byte) address[0], (byte) address[1] });
				}
				case StopSound -> out.writeBytes(new byte[] { (byte) (STOP_SOUND << 4 | v[0]), (byte) v[1] });
				case PlaySound -> out.writeBytes(new byte[] { (byte) (PLAY_SOUND << 4 | v[0]), (byte) v[1], (byte) v[2] });
				case PlayDrum -> out.writeBytes(new byte[] { (byte) (PLAY_SOUND << 4 | DRUM_TRACK), (byte) v[0], (byte) v[1] });
				case StartLoop -> out.writeBytes(new byte[] { (byte) (MULTI << 4), START_LOOP, (byte) v[0] });
				case EndLoop -> out.writeBytes(new byte[] { (byte) (MULTI << 4), END_LOOP, (byte) (v[1] << 2 | v[0]) });
				case SetTune -> out.writeBytes(new byte[] { (byte) (TUNING << 4 | v[0]), (byte) (v[1] >> 8), (byte) v[1] });
				case SetResumable -> out.writeBytes(new byte[] { (byte) (MULTI << 4 | v[0]), SET_RESUMABLE, (byte) v[1] });
			}
		}
		out.write(0);

		int rampsOffset = 0;
		if (!ramps.isEmpty()) {
			while (out.size() % 4 != 0)
				out.write(0);
			rampsOffset = out.size();
			for (Ramp r : ramps) {
				out.writeBytes(ByteBuffer.allocate(RAMP_SIZE).put((byte) r.track).put((byte) (r.isVolume ? 1 : 0))
					.putShort((short) r.time).putShort((short) r.delta).putShort((short) r.goal).array());
			}
		}

		byte[] bytes = out.toByteArray();
		ByteBuffer.wrap(bytes).put("MSEQ".getBytes(StandardCharsets.US_ASCII)).putInt(bytes.length)
			.put(String.format("%-4s", code).getBytes(StandardCharsets.US_ASCII)).put((byte) firstVoice)
			.put((byte) ramps.size()).putShort((short) rampsOffset).putShort((short) HEADER_SIZE);
		return bytes;
	}
}
