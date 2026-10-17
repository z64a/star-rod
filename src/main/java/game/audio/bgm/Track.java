package game.audio.bgm;

import java.nio.ByteBuffer;
import java.util.HashMap;

import app.StarRodException;
import game.audio.bgm.CommandStream.StreamType;

/** BGM track commands decoded using the same opcodes as the game and Classic's player. */
public class Track
{
	private static final int PolyphonicVoiceCounts[] = {
			0, 1, 0, 0,
			0, 2, 3, 4
	};

	private static final int SPECIAL_OPCODE = 0xFF;

	public static boolean debugPrint = false;
	private static final String CMD_FMT = "%5X %5d: ";

	public final Phrase phrase;
	protected CommandStream commands = new CommandStream(StreamType.TRACK);
	// Reuse shared substreams by their binary offset.
	private final HashMap<Integer, TrackDetour> detourLookup = new HashMap<>();
	private final HashMap<Integer, TrackBranch> branchLookup = new HashMap<>();

	public int index;
	public boolean enabled;
	public boolean unkFlag;

	public int linkedIndex = -1;
	public int polyphonicIndex = 1;
	public int polyphonicVoiceCount = PolyphonicVoiceCounts[polyphonicIndex];

	// allow tracks to be a direct duplicate of another track
	// only used for one phrase in 26_210.bgm (required to match)
	public int copyOf = -1;

	public Track(Phrase phrase)
	{
		this.phrase = phrase;
	}

	public boolean hasBranch()
	{
		for (TrackCommand cmd : commands.all) {
			if (cmd instanceof Branch) {
				return true;
			}
		}

		return false;
	}

	public CommandStream getCommands()
	{
		return commands;
	}

	public boolean isDrum()
	{
		return commands.isDrum;
	}

	public Track(Phrase phrase, int i, ByteBuffer bb, int trackInfo)
	{
		this(phrase);

		enabled = (trackInfo != 0);
		index = i;

		if (!enabled) {
			return;
		}

		// unpack track info
		int offset = (trackInfo >> 16) & 0xFFFF;
		polyphonicIndex = (trackInfo >> 13) & 0x7;
		int linkedID = (trackInfo >> 9) & 0xF;
		linkedIndex = linkedID == 0 ? -1 : linkedID - 1;
		unkFlag = ((trackInfo >> 8) & 1) == 0;
		commands.isDrum = ((trackInfo >> 7) & 1) != 0;

		polyphonicVoiceCount = PolyphonicVoiceCounts[polyphonicIndex];

		if (debugPrint)
			System.out.printf("Track %X: poly %d, drum %b, linked %d%n", i, polyphonicIndex, commands.isDrum, linkedIndex);

		TrackStreamReader reader = new TrackStreamReader(this, bb, phrase.filePos + offset);
		readStream(reader, commands);

	}

	private static class TrackStreamReader
	{
		private final Track track;
		private final ByteBuffer bb;
		private final int startPos;

		private int commandPos;
		private int time;

		public TrackStreamReader(Track track, ByteBuffer bb, int startPos)
		{
			this.track = track;

			this.bb = bb;
			this.startPos = startPos;

			bb.position(startPos);
		}

		public ByteBuffer getBufferView()
		{
			return bb.duplicate();
		}

		public void beginNext()
		{
			commandPos = bb.position();
		}

		public int getCommandPos()
		{
			return commandPos;
		}

		public void addTime(int ticks)
		{
			time += ticks;
		}

		public int getTime()
		{
			return time;
		}

		public int getS8()
		{
			return bb.get();
		}

		public int getU8()
		{
			return bb.get() & 0xFF;
		}

		public int getS16()
		{
			return bb.getShort();
		}

		public int getU16()
		{
			return bb.getShort() & 0xFFFF;
		}

		public int getS32()
		{
			return bb.getInt();
		}
	}

	public static void readStream(TrackStreamReader reader, CommandStream commands)
	{
		readStream(reader, commands, -1);
	}

	public static void readStream(TrackStreamReader reader, CommandStream commands, int lengthLimit)
	{
		int lastNoteTime = -1;
		int polyAlloc = 0;

		while (true) {
			reader.beginNext();
			int readLen = reader.getCommandPos() - reader.startPos;

			if (lengthLimit > 0 && readLen >= lengthLimit) {
				assert (readLen == lengthLimit);
				if (debugPrint)
					System.out.printf(CMD_FMT + "End of Detour%n", reader.getCommandPos(), reader.getTime());
				break;
			}

			int op = reader.getU8();
			if (op == 0) {
				if (debugPrint)
					System.out.printf(CMD_FMT + "End of Stream%n", reader.getCommandPos(), reader.getTime());
				break;
			}

			if (op < 0x78) {
				int delay = op;
				commands.all.add(new Delay(reader, delay));

				if (debugPrint)
					System.out.printf(CMD_FMT + "Delay ~ %d%n", reader.getCommandPos(), reader.getTime(), delay);

				reader.addTime(delay);
			}
			else if (op < 0x80) {
				int delay = ((op & 7) << 8) + reader.getU8() + 0x78;
				commands.all.add(new Delay(reader, delay));

				if (debugPrint)
					System.out.printf(CMD_FMT + "Delay ~ %d%n", reader.getCommandPos(), reader.getTime(), delay);

				reader.addTime(delay);
			}
			else if (op < 0xD4) {
				int pitch = op & 0x7F;
				int velocity = reader.getU8();
				int length = reader.getU8();
				if (length >= 0xC0) {
					length = ((length & 0x3F) << 8) + reader.getU8() + 0xC0;
				}
				commands.all.add(new Note(reader, pitch, velocity, length));

				int curTime = reader.getTime();

				if (debugPrint)
					System.out.printf(CMD_FMT + "Note ~ %d %d %d%n", reader.getCommandPos(), curTime, pitch, velocity, length);

				if (curTime == lastNoteTime) {
					polyAlloc++;

					if (debugPrint && polyAlloc > reader.track.polyphonicVoiceCount) {
						System.out.println("POLYPHONY OVERFLOW!");
					}
				}
				else {
					polyAlloc = 1;
				}
				lastNoteTime = curTime;
			}
			else if (op < 0xE0) {
				throw new StarRodException("Unknown track command: %02X", op);
			}
			else {
				switch (op) {
					case SetMasterTempo.OPCODE:
						commands.all.add(new SetMasterTempo(reader));
						break;
					case SetMasterVolume.OPCODE:
						commands.all.add(new SetMasterVolume(reader));
						break;
					case SetMasterDetune.OPCODE:
						commands.all.add(new SetMasterDetune(reader));
						break;
					case SetBusEffect.OPCODE:
						commands.all.add(new SetBusEffect(reader));
						break;
					case MasterTempoLerp.OPCODE:
						commands.all.add(new MasterTempoLerp(reader));
						break;
					case MasterVolumeLerp.OPCODE:
						commands.all.add(new MasterVolumeLerp(reader));
						break;
					case SetMasterEffect.OPCODE:
						commands.all.add(new SetMasterEffect(reader));
						break;
					case OverridePatch.OPCODE:
						commands.all.add(new OverridePatch(reader));
						break;
					case InstrumentVolume.OPCODE:
						commands.all.add(new InstrumentVolume(reader));
						break;
					case InstrumentPan.OPCODE:
						commands.all.add(new InstrumentPan(reader));
						break;
					case InstrumentReverb.OPCODE:
						commands.all.add(new InstrumentReverb(reader));
						break;
					case TrackVolume.OPCODE:
						commands.all.add(new TrackVolume(reader));
						break;
					case InstrumentCoarseTune.OPCODE:
						commands.all.add(new InstrumentCoarseTune(reader));
						break;
					case InstrumentFineTune.OPCODE:
						commands.all.add(new InstrumentFineTune(reader));
						break;
					case TrackDetune.OPCODE:
						commands.all.add(new TrackDetune(reader));
						break;
					case TrackTremolo.OPCODE:
						commands.all.add(new TrackTremolo(reader));
						break;
					case TrackTremoloRate.OPCODE:
						commands.all.add(new TrackTremoloRate(reader));
						break;
					case TrackTremoloDepth.OPCODE:
						commands.all.add(new TrackTremoloDepth(reader));
						break;
					case TrackTremoloStop.OPCODE:
						commands.all.add(new TrackTremoloStop(reader));
						break;
					case RandomPan.OPCODE:
						commands.all.add(new RandomPan(reader));
						break;
					case UseInstrument.OPCODE:
						commands.all.add(new UseInstrument(reader));
						break;
					case InstrumentVolumeLerp.OPCODE:
						commands.all.add(new InstrumentVolumeLerp(reader));
						break;
					case ReverbType.OPCODE:
						commands.all.add(new ReverbType(reader));
						break;
					case Branch.OPCODE:
						if (commands.type != StreamType.TRACK)
						throw new StarRodException("Nested BGM branches and detours are not supported");
						commands.all.add(new Branch(reader));
						break;
					case EventTrigger.OPCODE:
						commands.all.add(new EventTrigger(reader));
						break;
					case Detour.OPCODE:
						if (commands.type != StreamType.TRACK)
						throw new StarRodException("Nested BGM branches and detours are not supported");
						commands.all.add(new Detour(reader));
						break;
					case SPECIAL_OPCODE:
						int type = reader.getU8();
						switch (type) {
							case SetStereoDelay.SUBOP:
								commands.all.add(new SetStereoDelay(reader));
								break;
							case SeekCustomEnv.SUBOP:
								commands.all.add(new SeekCustomEnv(reader));
								break;
							case WriteCustomEnv.SUBOP:
								commands.all.add(new WriteCustomEnv(reader));
								break;
							case UseCustomEnv.SUBOP:
								commands.all.add(new UseCustomEnv(reader));
								break;
							case TriggerSound.SUBOP:
								commands.all.add(new TriggerSound(reader));
								break;
							case ProxMixOverride.SUBOP:
								commands.all.add(new ProxMixOverride(reader));
								break;
							default:
								throw new StarRodException("Unknown special command type: %02X", type);
						}
						break;
					default:
						throw new StarRodException("Unknown track command: %02X", op);
				}
			}
		}

		commands.duration = reader.getTime();
	}

	public static class TrackDetour
	{
		public final Track track;

		protected CommandStream commands = new CommandStream(StreamType.DETOUR);

		public TrackDetour(Track track)
		{
			this.track = track;
		}

		public CommandStream getCommands()
		{
			return commands;
		}

	}

	public static class TrackBranch
	{
		public final Track track;

		protected CommandStream[] options = new CommandStream[Song.MAX_BRANCH_OPTIONS];

		public transient int tablePos;

		public TrackBranch(Track track)
		{
			this.track = track;
		}

		public CommandStream getOption(int index)
		{
			if (index < 0 || index >= options.length)
				return null;
			return options[index];
		}

	}

	public static abstract class TrackCommand
	{
		public final Track track;
		public transient int time;
		public transient int streamPos;

		public TrackCommand(TrackStreamReader reader)
		{
			this.track = reader.track;
			this.streamPos = reader.getCommandPos();
			this.time = reader.getTime();
		}

	}

	public static class Note extends TrackCommand
	{
		private int pitch;
		private int velocity;
		private int length;

		public Note(TrackStreamReader reader, int pitch, int velocity, int length)
		{
			super(reader);
			this.pitch = pitch;
			this.velocity = velocity;
			this.length = length;
		}

		public int getPitch()
		{
			return pitch;
		}

		public int getVelocity()
		{
			return velocity;
		}

		public int getLength()
		{
			return length;
		}

	}

	public static class Delay extends TrackCommand
	{
		public int ticks;

		public Delay(TrackStreamReader reader, int length)
		{
			super(reader);
			this.ticks = length;
		}

	}

	public static class SetMasterTempo extends TrackCommand
	{
		public static final int OPCODE = 0xE0;

		public int bpm;

		public SetMasterTempo(TrackStreamReader reader)
		{
			super(reader);

			bpm = reader.getU16();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Tempo ~ %d%n", streamPos, time, bpm);
		}

	}

	public static class SetMasterVolume extends TrackCommand
	{
		public static final int OPCODE = 0xE1;

		public int value;

		public SetMasterVolume(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Volume ~ %d%n", streamPos, time, value);
		}

	}

	public static class SetMasterDetune extends TrackCommand
	{
		public static final int OPCODE = 0xE2;

		public int cents;

		public SetMasterDetune(TrackStreamReader reader)
		{
			super(reader);

			cents = reader.getS8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Detune ~ %d%n", streamPos, time, cents);
		}

	}

	public static class SetBusEffect extends TrackCommand
	{
		public static final int OPCODE = 0xE3;
		public int effectType;

		public SetBusEffect(TrackStreamReader reader)
		{
			super(reader);

			effectType = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Bus Effect ~ %d%n", streamPos, time, effectType);
		}

	}

	public static class MasterTempoLerp extends TrackCommand
	{
		public static final int OPCODE = 0xE4;

		public int time;
		public int bpm;

		public MasterTempoLerp(TrackStreamReader reader)
		{
			super(reader);

			time = reader.getU16();
			bpm = reader.getU16();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Tempo Lerp ~ %d over %d%n", streamPos, time, bpm, time);
		}

	}

	public static class MasterVolumeLerp extends TrackCommand
	{
		public static final int OPCODE = 0xE5;

		public int time;
		public int value;

		public MasterVolumeLerp(TrackStreamReader reader)
		{
			super(reader);

			time = reader.getU16();
			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Volume Lerp ~ %d over %d%n", streamPos, time, value, time);
		}

	}

	public static class SetMasterEffect extends TrackCommand
	{
		public static final int OPCODE = 0xE6;

		public int index;
		public int value;

		public SetMasterEffect(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			value = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Master Effect ~ %d at %d%n", streamPos, time, value, index);
		}

	}

	public static class OverridePatch extends TrackCommand
	{
		public static final int OPCODE = 0xE8;

		public int bank;
		public int patch;
		public String wav;
		public int envelope;

		public OverridePatch(TrackStreamReader reader)
		{
			super(reader);

			bank = reader.getU8();
			patch = reader.getU8();

			wav = track.phrase.song.resolveInstrument(bank, patch);
			envelope = bank & 3;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Override Patch ~ %2X-%2X%n", streamPos, time, bank, patch);
		}

	}

	public static class InstrumentVolume extends TrackCommand
	{
		public static final int OPCODE = 0xE9;
		public int value;

		public InstrumentVolume(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Volume ~ %d%n", streamPos, time, value);
		}

	}

	public static class InstrumentPan extends TrackCommand
	{
		public static final int OPCODE = 0xEA;
		public int value;

		public InstrumentPan(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Pan ~ %d%n", streamPos, time, value);
		}

	}

	public static class InstrumentReverb extends TrackCommand
	{
		public static final int OPCODE = 0xEB;
		public int value;

		public InstrumentReverb(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Reverb ~ %d%n", streamPos, time, value);
		}

	}

	public static class TrackVolume extends TrackCommand
	{
		public static final int OPCODE = 0xEC;
		public int value;

		public TrackVolume(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Track Volume ~ %d%n", streamPos, time, value);
		}

	}

	public static class InstrumentCoarseTune extends TrackCommand
	{
		public static final int OPCODE = 0xED;
		public int semitones;

		public InstrumentCoarseTune(TrackStreamReader reader)
		{
			super(reader);

			semitones = reader.getS8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Coarse Tune ~ %d%n", streamPos, time, semitones);
		}

	}

	public static class InstrumentFineTune extends TrackCommand
	{
		public static final int OPCODE = 0xEE;
		public int cents;

		public InstrumentFineTune(TrackStreamReader reader)
		{
			super(reader);

			cents = reader.getS8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Fine Tune ~ %d%n", streamPos, time, cents);
		}

	}

	public static class TrackDetune extends TrackCommand
	{
		public static final int OPCODE = 0xEF;
		public int cents;

		public TrackDetune(TrackStreamReader reader)
		{
			super(reader);

			cents = reader.getS16();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Track Detune ~ %d%n", streamPos, time, cents);
		}

	}

	public static class TrackTremolo extends TrackCommand
	{
		public static final int OPCODE = 0xF0;

		public int delay;
		public int speed;
		public int depth;

		public TrackTremolo(TrackStreamReader reader)
		{
			super(reader);

			delay = reader.getU8();
			speed = reader.getU8();
			depth = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Tremolo ~ %d %d %d%n", streamPos, time, delay, speed, depth);
		}

	}

	public static class TrackTremoloRate extends TrackCommand
	{
		public static final int OPCODE = 0xF1;

		public int value;

		public TrackTremoloRate(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Tremolo ~ %d%n", streamPos, time, value);
		}

	}

	public static class TrackTremoloDepth extends TrackCommand
	{
		public static final int OPCODE = 0xF2;
		public int value;

		public TrackTremoloDepth(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Tremolo Depth ~ %d%n", streamPos, time, value);
		}

	}

	public static class TrackTremoloStop extends TrackCommand
	{
		public static final int OPCODE = 0xF3;

		public TrackTremoloStop(TrackStreamReader reader)
		{
			super(reader);

			if (debugPrint)
				System.out.printf(CMD_FMT + "Tremolo Stop%n", streamPos, time);
		}

	}

	public static class RandomPan extends TrackCommand
	{
		public static final int OPCODE = 0xF4;

		public int pan1;
		public int pan2;

		public RandomPan(TrackStreamReader reader)
		{
			super(reader);

			pan1 = reader.getU8() & 0x7F;
			pan2 = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Random Pan ~ %d %d%n", streamPos, time, pan1, pan2);
		}

	}

	public static class UseInstrument extends TrackCommand
	{
		public static final int OPCODE = 0xF5;

		public int index;
		public boolean useGlobal;

		public UseInstrument(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			useGlobal = (index >= 0x80);

			if (useGlobal) {
				index -= 0x80; // use entries from PRG
			}

			if (debugPrint)
				System.out.printf(CMD_FMT + "Use Instrument ~ %d (%s)%n", streamPos, time, index, useGlobal ? "global" : "local");
		}

	}

	public static class InstrumentVolumeLerp extends TrackCommand
	{
		public static final int OPCODE = 0xF6;

		public int time;
		public int value;

		public InstrumentVolumeLerp(TrackStreamReader reader)
		{
			super(reader);

			time = reader.getU16();
			value = reader.getU8() & 0x7F;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Instrument Volume Lerp ~ %d over %d%n", streamPos, time, value, time);
		}

	}

	public static class ReverbType extends TrackCommand
	{
		public static final int OPCODE = 0xF7;

		public int index;

		public ReverbType(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Reverb Type ~ %d%n", streamPos, time, index);
		}

	}

	public static class Branch extends TrackCommand
	{
		public static final int OPCODE = 0xFC;

		public TrackBranch branch;

		public Branch(TrackStreamReader reader)
		{
			super(reader);

			int tablePos = reader.getU16();
			int count = reader.getU8();

			Song song = track.phrase.song;
			if (count < 1 || count > Song.MAX_BRANCH_OPTIONS)
				throw new StarRodException("Invalid BGM branch count: %d", count);
			song.branchOptions = Math.max(song.branchOptions, count);

			branch = track.branchLookup.get(tablePos);

			if (branch != null) {
				if (debugPrint)
					System.out.println("--- REUSED BRANCH ---");
			}
			else {
				ByteBuffer bb = reader.getBufferView();

				branch = new TrackBranch(track);
				branch.tablePos = tablePos;

				track.branchLookup.put(tablePos, branch);

				// read the branch table
				int[] offsets = new int[count];
				boolean[] isDrum = new boolean[count];

				bb.position(tablePos);
				for (int i = 0; i < count; i++) {
					offsets[i] = bb.getShort() & 0xFFFF;
					isDrum[i] = bb.get() != 0;
				}

				if (debugPrint)
					System.out.printf(CMD_FMT + "Begin branch x%d%n", streamPos, time, count);

				bb.position(offsets[0]);
				int length = bb.get() & 0xFF;
				assert (bb.get() == 0);
				assert (length == song.branchTicks);

				for (int i = 0; i < count; i++) {
					if (debugPrint)
						System.out.printf(CMD_FMT + "Branch %X --> %X%n", streamPos, time, i, offsets[i]);
					TrackStreamReader branchReader = new TrackStreamReader(track, bb, offsets[i]);
					CommandStream stream = new CommandStream(StreamType.BRANCH);
					stream.isDrum = isDrum[i];
					branch.options[i] = stream;

					readStream(branchReader, stream);
					assert (stream.duration == song.branchTicks);

				}

				if (debugPrint)
					System.out.printf(CMD_FMT + "End of branch%n", streamPos, time);
			}
		}

	}

	public static class EventTrigger extends TrackCommand
	{
		public static final int OPCODE = 0xFD;

		public int eventInfo;

		public EventTrigger(TrackStreamReader reader)
		{
			super(reader);

			eventInfo = reader.getS32() & 0xFFFFFF;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Event Trigger ~ %X%n", streamPos, time, eventInfo);
		}

	}

	public static class Detour extends TrackCommand
	{
		public static final int OPCODE = 0xFE;

		public TrackDetour detour;

		public Detour(TrackStreamReader reader)
		{
			super(reader);

			int offset = reader.getU16();
			int length = reader.getU8();

			// a single detour command is broken in song 58_170.bgm, the command has length = 0, but points
			// to a detour of length 0x100. this is due to overflow. the detour command in the stream stores
			// the length as a u8, so 0x100 overflows and nothing is played.
			boolean bugged = (length == 0);

			if (bugged) {
				length = 0x100;
			}

			if (debugPrint)
				System.out.printf(CMD_FMT + "Detour ~ %X --> %X%n", streamPos, time, offset, offset + length);

			detour = track.detourLookup.get(offset);

			if (detour != null) {
				if (debugPrint)
					System.out.println("--- REUSED DETOUR ---");
			}
			else {

				detour = new TrackDetour(reader.track);
				TrackStreamReader detourReader = new TrackStreamReader(track, reader.getBufferView(), offset);

				track.detourLookup.put(offset, detour);

				if (debugPrint)
					System.out.println("--- BEGIN DETOUR ---");

				readStream(detourReader, detour.commands, length);

				if (!bugged)
					reader.addTime(detourReader.time);

				if (debugPrint)
					System.out.println("---- END DETOUR ----");
			}
		}

	}

	public static class SetStereoDelay extends TrackCommand
	{
		public static final int SUBOP = 1;

		public int index;
		public int length;
		public int side;

		public SetStereoDelay(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			int v = reader.getU8();
			length = v & 0xF;
			side = ((v >> 4) & 1) + 1;

			if (debugPrint)
				System.out.printf(CMD_FMT + "Set Stereo Delay ~ %d by %d @ %d%n", streamPos, time, index, length, side);
		}

	}

	public static class SeekCustomEnv extends TrackCommand
	{
		public static final int SUBOP = 2;

		public int index;

		public SeekCustomEnv(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			reader.getU8(); // unused

			if (debugPrint)
				System.out.printf(CMD_FMT + "Seek Custom Env ~ %d%n", streamPos, time, index);
		}

	}

	public static class WriteCustomEnv extends TrackCommand
	{
		public static final int SUBOP = 3;

		public int value;

		public WriteCustomEnv(TrackStreamReader reader)
		{
			super(reader);

			value = reader.getU16();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Write Custom Env ~ %04X%n", streamPos, time, value);
		}

	}

	public static class UseCustomEnv extends TrackCommand
	{
		public static final int SUBOP = 4;

		public int index;

		public UseCustomEnv(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			reader.getU8(); //unused

			if (debugPrint)
				System.out.printf(CMD_FMT + "Use Custom Env ~ %d%n", streamPos, time, index);
		}

	}

	public static class TriggerSound extends TrackCommand
	{
		public static final int SUBOP = 5;

		public int index;

		public TriggerSound(TrackStreamReader reader)
		{
			super(reader);

			index = reader.getU8();
			reader.getU8(); //unused

			if (debugPrint)
				System.out.printf(CMD_FMT + "Trigger Sound ~ %d%n", streamPos, time, index);
		}

	}

	public static class ProxMixOverride extends TrackCommand
	{
		public static final int SUBOP = 6;

		public int vol1;
		public int vol2;

		public ProxMixOverride(TrackStreamReader reader)
		{
			super(reader);

			vol1 = reader.getU8();
			vol2 = reader.getU8();

			if (debugPrint)
				System.out.printf(CMD_FMT + "Prox Mix Override ~ %d and %d%n", streamPos, time, vol1, vol2);
		}

	}
}
