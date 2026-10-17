package game.audio;

import static game.audio.SfxArchive.Allocation.DYNAMIC;
import static game.audio.SfxArchive.Allocation.FIXED;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;




import game.audio.SfxArchive.Allocation;
import game.audio.SfxArchive.Command;
import game.audio.SfxArchive.Definition;
import game.audio.SfxArchive.Empty;
import game.audio.SfxArchive.Envelope;
import game.audio.SfxArchive.Label;
import game.audio.SfxArchive.Node;
import game.audio.SfxArchive.OneShot;
import game.audio.SfxArchive.Op;
import game.audio.SfxArchive.Routing;
import game.audio.SfxArchive.Sequence;
import game.audio.SfxArchive.Sound;
import game.audio.SfxArchive.SpawnedEffect;
import game.audio.SfxArchive.Track;

public final class SfxBinary
{
	private static final int HEADER_SIZE = 0x22;
	private static final int EXT_HEADER_SIZE = 0x34;
	private static final int ROUTED_PER_SECTION = 0xC0;
	private static final int DIRECT_PER_SECTION = 0x40;
	private static final int ROUTED_SECTION_SIZE = ROUTED_PER_SECTION * 4;
	private static final int DIRECT_SECTION_SIZE = DIRECT_PER_SECTION * 4;
	private static final int VANILLA_EXTRA_COUNT = 0x140;
	private static final int EXTRA_COUNT = 0x200;
	private static final int SECTIONS = 16;
	private static final int VANILLA_SECTIONS = 8;
	private static final int HAS_EXTRA_SECTION = 1;
	private static final int HAS_EXT_SECTIONS = 2;
	private static final int EXT_SECTIONS_OFFSET = 0x22;
	private static final int ROUTING_KNOWN_MASK = 0x1BE7;

	private SfxBinary()
	{}

	public record DecodeResult(SfxArchive archive, List<String> warnings)
	{}

	public static DecodeResult decode(byte[] input)
	{
		return new Decoder(input).decode();
	}

	public static byte[] encode(SfxArchive archive)
	{
		return new Encoder(archive).encode();
	}

	public static boolean isCompactRepresentable(OneShot oneShot)
	{
		return oneShot.pan == 64
			&& oneShot.reverb == 0
			&& oneShot.pitch == 48
			&& oneShot.volume >= 3
			&& oneShot.volume <= 127
			&& (oneShot.volume & 3) == 3
			&& oneShot.randomPitch >= 0
			&& oneShot.randomPitch <= 56
			&& (oneShot.randomPitch & 7) == 0;
	}

	private static final class Decoder
	{
		private final byte[] data;
		private final SfxArchive archive = new SfxArchive();
		private final List<String> warnings = new ArrayList<>();
		private final int[] sections = new int[SECTIONS];
		private final Map<Integer, Envelope> envelopesByOffset = new HashMap<>();
		private final Map<String, Envelope> envelopesByContent = new LinkedHashMap<>();

		private Decoder(byte[] input)
		{
			if (input.length < HEADER_SIZE)
				throw new SfxFormatException("SEF is shorter than its 0x22-byte header");
			if (!"SEF ".equals(new String(input, 0, 4, StandardCharsets.US_ASCII)))
				throw new SfxFormatException("Expected SEF signature");

			int declaredSize = readU32(input, 4);
			if (declaredSize < HEADER_SIZE || declaredSize > input.length)
				throw new SfxFormatException(String.format(
					"Invalid declared SEF size 0x%X for a 0x%X-byte input", declaredSize, input.length));
			this.data = Arrays.copyOf(input, declaredSize);
		}

		private DecodeResult decode()
		{
			String archiveName = new String(data, 8, 4, StandardCharsets.US_ASCII);
			if (!archiveName.equals("DAT1"))
				throw new SfxFormatException("Expected DAT1 SEF archive name, found " + archiveName);

			int kind = u8(0x0E);
			boolean hasExtraSection = kind == HAS_EXTRA_SECTION || kind == HAS_EXT_SECTIONS;
			boolean extended = kind == HAS_EXT_SECTIONS;
			if (extended && data.length < EXT_HEADER_SIZE)
				throw new SfxFormatException("SEF is shorter than its 0x34-byte extended header");

			for (int i = 0; i < VANILLA_SECTIONS; i++)
				sections[i] = u16(0x10 + i * 2);
			for (int i = VANILLA_SECTIONS; i < SECTIONS && extended; i++)
				sections[i] = u16(EXT_SECTIONS_OFFSET + (i - VANILLA_SECTIONS) * 2);
			int extraSection = u16(0x20);

			for (int i = 0; i < SECTIONS; i++) {
				if (i < VANILLA_SECTIONS || sections[i] != 0)
					checkRange(sections[i], isRouted(i) ? ROUTED_SECTION_SIZE : DIRECT_SECTION_SIZE, "sound table " + i);
			}
			int extraCount = extended ? EXTRA_COUNT : VANILLA_EXTRA_COUNT;
			if (hasExtraSection) {
				if (extraSection == 0)
					throw new SfxFormatException("SEF declares an extra section but its offset is zero");
				checkRange(extraSection, extraCount * 4, "extra sound table");
			}

			for (int section = 0; section < SECTIONS; section++) {
				if (sections[section] == 0)
					continue;
				if (isRouted(section))
					decodeRoutedSection(section);
				else
					decodeDirectSection(section);
			}
			if (hasExtraSection) {
				for (int index = 0; index < extraCount; index++)
					decodeDirectSlot(SfxArchive.FIRST_EXTRA_ID + index, extraSection + index * 4);
			}

			return new DecodeResult(archive, List.copyOf(warnings));
		}

		private static boolean isRouted(int section)
		{
			return (section & 4) == 0;
		}

		private static int sectionIdBase(int section)
		{
			return (section & 8) != 0 ? SfxArchive.EXT_FLAG | (section & 3) << 8 : (section & 3) << 8;
		}

		private void decodeRoutedSection(int section)
		{
			int base = sections[section];
			for (int index = 0; index < ROUTED_PER_SECTION; index++) {
				int id = sectionIdBase(section) + index + 1;
				int entry = base + index * 4;
				int root = u16(entry);
				int info = u16(entry + 2);
				if (root == 0) {
					keepNamedEmpty(id);
					continue;
				}

				Sound sound = new Sound(id, AudioNames.getSoundName(id));
				int polyMode = (info >>> 5) & 3;
				int trackCount = polyMode == 0 ? 1 : 2 << (polyMode - 1);
				sound.routing = decodeRouting(info, trackCount, String.format("sound %04X", id));
				DecodeContext context = new DecodeContext(sound);

				try {
					if (trackCount == 1) {
						sound.tracks.add(new Track(0, decodeDefinition(root, context)));
					}
					else {
						checkRange(root, trackCount * 4, String.format("sound %04X polyphony table", id));
						for (int slot = 0; slot < trackCount; slot++) {
							int trackRoot = u16(root + slot * 4);
							int trackInfo = u16(root + slot * 4 + 2);
							Track track = new Track(slot,
								trackRoot == 0 ? Empty.INSTANCE : decodeDefinition(trackRoot, context));
							if (sound.routing.allocation == FIXED && trackRoot != 0) {
								track.player = trackInfo & 7;
								track.priority = (trackInfo >>> 8) & 3;
							}
							sound.tracks.add(track);
						}
					}
				}
				catch (SfxFormatException e) {
					// vanilla 0x00B5 has a track root inside another sequence and reads past the end of the file
					warnings.add(String.format("Sound %04X was made empty: %s", id, e.getMessage()));
					keepNamedEmpty(id);
					continue;
				}
				archive.sounds.put(id, sound);
			}
		}

		/** Silent slots that have a real name are kept, as code plays them by name. */
		private void keepNamedEmpty(int id)
		{
			String name = AudioNames.getSoundName(id);
			if (!AudioNames.isPlaceholderSound(name))
				archive.sounds.put(id, new Sound(id, name));
		}

		private void decodeDirectSection(int section)
		{
			int base = sections[section];
			for (int index = 0; index < DIRECT_PER_SECTION; index++) {
				int id = sectionIdBase(section) + ROUTED_PER_SECTION + 1 + index;
				decodeDirectSlot(id, base + index * 4);
			}
		}

		private void decodeDirectSlot(int id, int offset)
		{
			if (SfxArchive.slotOf(id) == null) {
				if (u16(offset) != 0)
					warnings.add(String.format("Unaddressable table slot %04X is populated and was ignored", id));
				return;
			}

			// This is the same presence test used by the player. Bytes 2-3 are
			// ignored when the first halfword is zero.
			if (u16(offset) == 0) {
				keepNamedEmpty(id);
				return;
			}

			Sound sound = new Sound(id, AudioNames.getSoundName(id));
			DecodeContext context = new DecodeContext(sound);
			Definition definition = decodeDefinition(offset, context);
			if (!(definition instanceof OneShot))
				warnings.add(String.format(
					"Direct sound %04X uses a non-COMPACT stream; rebuilding currently rejects it", id));
			sound.tracks.add(new Track(0, definition));
			archive.sounds.put(id, sound);
		}

		private Routing decodeRouting(int info, int trackCount, String owner)
		{
			Allocation allocation = (info & 0x80) != 0 ? DYNAMIC : FIXED;
			Routing routing = new Routing(allocation);
			routing.maxPlayer = trackCount > 1 ? 7 : info & 7;
			routing.player = info & 7;
			routing.priority = (info >>> 8) & 3;
			routing.exclusiveGroup = (info >>> 11) & 3;
			int unknown = info & ~ROUTING_KNOWN_MASK;
			if (unknown != 0)
				warnings.add(String.format("%s drops ignored routing bits %04X", owner, unknown));
			return routing;
		}

		private Definition decodeDefinition(int offset, DecodeContext context)
		{
			checkRange(offset, 1, "sound stream");
			int flags = u8(offset);
			int mode = flags & 3;
			if ((flags & 0xC0) != 0)
				warnings.add(String.format("Stream at %04X drops unknown flags %02X", offset, flags & 0xC0));

			switch (mode) {
				case 0:
					return decodeBasic(offset);
				case 1:
					return decodeSequence(offset, context);
				case 2:
					return decodeCompact(offset);
				default:
					throw new SfxFormatException(String.format("Invalid stream mode at 0x%04X", offset));
			}
		}

		private OneShot decodeBasic(int offset)
		{
			checkRange(offset, 8, "BASIC stream");
			OneShot oneShot = new OneShot();
			applyLocks(oneShot, u8(offset));
			oneShot.bank = u8(offset + 1);
			oneShot.patch = u8(offset + 2);
			oneShot.volume = u8(offset + 3);
			oneShot.pan = u8(offset + 4);
			oneShot.reverb = u8(offset + 5);
			oneShot.pitch = u8(offset + 6) & 0x7F;
			oneShot.randomPitch = (u8(offset + 7) & 0xF) * 8;
			return oneShot;
		}

		private OneShot decodeCompact(int offset)
		{
			checkRange(offset, 4, "COMPACT stream");
			OneShot oneShot = new OneShot();
			applyLocks(oneShot, u8(offset));
			oneShot.bank = u8(offset + 1);
			oneShot.patch = u8(offset + 2);
			int packed = u8(offset + 3);
			oneShot.volume = (packed >>> 1) | 3;
			oneShot.pan = 64;
			oneShot.reverb = 0;
			oneShot.pitch = 48;
			oneShot.randomPitch = (packed & 7) * 8;
			return oneShot;
		}

		private void applyLocks(OneShot oneShot, int flags)
		{
			oneShot.lockVolume = (flags & 0x04) != 0;
			oneShot.lockPan = (flags & 0x08) != 0;
			oneShot.lockPitch = (flags & 0x10) != 0;
			oneShot.lockReverb = (flags & 0x20) != 0;
		}

		private Sequence decodeSequence(int root, DecodeContext context)
		{
			Sequence sequence = new Sequence();
			int flags = u8(root);
			sequence.lockVolume = (flags & 0x04) != 0;
			sequence.lockPan = (flags & 0x08) != 0;
			sequence.lockPitch = (flags & 0x10) != 0;
			sequence.lockReverb = (flags & 0x20) != 0;

			TreeMap<Integer, WireCommand> commands = walkSequence(root + 1);
			if (!commands.containsKey(root + 1))
				throw new SfxFormatException(String.format("Sequence at 0x%04X has no entry command", root));

			List<WireRegion> physicalRegions = makePhysicalRegions(commands);
			Map<Integer, WireRegion> regionAt = new HashMap<>();
			for (WireRegion region : physicalRegions)
				for (WireCommand command : region.commands)
					regionAt.put(command.offset, region);

			WireRegion entryRegion = regionAt.get(root + 1);
			List<WireRegion> orderedRegions = new ArrayList<>();
			Set<WireRegion> seen = new LinkedHashSet<>();
			Queue<WireRegion> pending = new ArrayDeque<>();
			pending.add(entryRegion);
			while (!pending.isEmpty()) {
				WireRegion region = pending.remove();
				if (!seen.add(region))
					continue;
				orderedRegions.add(region);
				for (WireCommand command : region.commands) {
					if (command.op == Op.JUMP
						|| (command.op == Op.SET_ALTERNATIVE && command.alternativeCanTrigger)) {
						WireRegion target = regionAt.get(command.target);
						if (target == null)
							throw new SfxFormatException(String.format(
								"Sequence target 0x%04X is not a decoded command boundary", command.target));
						pending.add(target);
					}
				}
			}
			for (WireRegion region : physicalRegions) {
				if (seen.add(region))
					orderedRegions.add(region);
			}

			Map<Integer, String> labels = new LinkedHashMap<>();
			labels.put(root + 1, Sequence.START_LABEL);
			int nextLabel = 1;
			for (WireRegion region : orderedRegions) {
				for (WireCommand command : region.commands) {
					if (command.op == Op.JUMP
						|| (command.op == Op.SET_ALTERNATIVE && command.alternativeCanTrigger)) {
						if (!labels.containsKey(command.target))
							labels.put(command.target, "label" + nextLabel++);
					}
				}
			}
			for (WireRegion region : orderedRegions) {
				int start = region.commands.get(0).offset;
				if (!labels.containsKey(start))
					labels.put(start, "label" + nextLabel++);
			}

			for (WireRegion region : orderedRegions) {
				for (WireCommand wire : region.commands) {
					String label = labels.get(wire.offset);
					if (label != null)
						sequence.nodes.add(new Label(label));
					sequence.nodes.add(toModelCommand(wire, labels, context));
				}
			}
			return sequence;
		}

		private TreeMap<Integer, WireCommand> walkSequence(int entry)
		{
			TreeMap<Integer, WireCommand> commands = new TreeMap<>();
			Deque<ExecutionState> pending = new ArrayDeque<>();
			Set<ExecutionState> visited = new HashSet<>();
			pending.add(new ExecutionState(entry, -1, -1, 0, -1, -1));

			while (!pending.isEmpty()) {
				ExecutionState initial = pending.remove();
				int pc = initial.pc;
				int saved = initial.saved;
				int loopStart = initial.loopStart;
				int loopCount = initial.loopCount;
				int alternative = initial.alternative;
				int alternativeSource = initial.alternativeSource;

				while (true) {
					ExecutionState state = new ExecutionState(
						pc, saved, loopStart, loopCount, alternative, alternativeSource);
					if (!visited.add(state))
						break;
					if (visited.size() > 200_000)
						throw new SfxFormatException("Sequence control flow exceeds 200,000 states");

					WireCommand command = decodeWireCommand(pc);
					addWireCommand(commands, command);

					int next = pc + command.size;
					if (command.op == Op.END)
						break;
					if (command.op == Op.JUMP) {
						saved = next;
						pc = command.target;
						continue;
					}
					if (command.op == Op.RESTART) {
						if (saved < 0) {
							warnings.add(String.format("Top-level Restart at 0x%04X", pc));
							break;
						}
						pc = saved;
						continue;
					}
					if (command.op == Op.START_LOOP) {
						loopStart = next;
						loopCount = command.a;
						pc = next;
						continue;
					}
					if (command.op == Op.END_LOOP) {
						if (loopStart < 0) {
							warnings.add(String.format("EndLoop without StartLoop at 0x%04X", pc));
							break;
						}
						if (loopCount == 0) {
							pc = loopStart;
						}
						else {
							loopCount--;
							pc = loopCount == 0 ? next : loopStart;
						}
						continue;
					}
					if (command.op == Op.SET_ALTERNATIVE) {
						// The player only consults alternativeDataPos at the start of an
						// update.  A second SetAlternative before the sequence yields
						// therefore replaces the first one without ever branching to it.
						if (command.a >= 1 && command.a <= 3) {
							alternative = command.target;
							alternativeSource = command.offset;
						}
						else {
							alternative = -1;
							alternativeSource = -1;
						}
					}
					if (alternative >= 0 && isSequenceYield(command.op)) {
						// Taking the alternative clears alternativeDataPos, but leaves the
						// Jump return position and loop state untouched.  Those values must
						// be captured here, at the branch target, rather than where EE was read.
						WireCommand setter = commands.get(alternativeSource);
						if (setter != null)
							setter.alternativeCanTrigger = true;
						pending.add(new ExecutionState(alternative, saved, loopStart, loopCount, -1, -1));
					}
					pc = next;
				}
			}
			return commands;
		}

		private void addWireCommand(TreeMap<Integer, WireCommand> commands, WireCommand command)
		{
			Map.Entry<Integer, WireCommand> floor = commands.floorEntry(command.offset);
			if (floor != null) {
				WireCommand previous = floor.getValue();
				if (previous.offset == command.offset) {
					if (previous.size != command.size || previous.op != command.op)
						throw new SfxFormatException(String.format(
							"Conflicting command boundary at 0x%04X", command.offset));
					return;
				}
				if (previous.offset + previous.size > command.offset)
					throw new SfxFormatException(String.format(
						"Command at 0x%04X begins inside command at 0x%04X..0x%04X",
						command.offset, previous.offset, previous.offset + previous.size));
			}

			Map.Entry<Integer, WireCommand> ceiling = commands.ceilingEntry(command.offset);
			if (ceiling != null && ceiling.getKey() < command.offset + command.size)
				throw new SfxFormatException(String.format(
					"Command at 0x%04X..0x%04X overlaps command boundary at 0x%04X",
					command.offset, command.offset + command.size, ceiling.getKey()));
			commands.put(command.offset, command);
		}

		private boolean isSequenceYield(Op op)
		{
			return op == Op.DELAY || op == Op.WAIT_FOR_END || op == Op.WAIT_FOR_RELEASE;
		}

		private List<WireRegion> makePhysicalRegions(TreeMap<Integer, WireCommand> commands)
		{
			List<WireRegion> regions = new ArrayList<>();
			WireRegion current = null;
			WireCommand previous = null;
			for (WireCommand command : commands.values()) {
				boolean connected = previous != null
					&& previous.offset + previous.size == command.offset
					&& previous.op != Op.END
					&& previous.op != Op.RESTART;
				if (!connected) {
					current = new WireRegion();
					regions.add(current);
				}
				current.commands.add(command);
				previous = command;
			}
			return regions;
		}

		private WireCommand decodeWireCommand(int pc)
		{
			checkRange(pc, 1, "sequence command");
			int opcode = u8(pc);
			if (opcode == 0)
				return new WireCommand(pc, 1, Op.END);
			if (opcode < 0x78)
				return new WireCommand(pc, 1, Op.DELAY, opcode, 0, 0);
			if (opcode < 0x80) {
				checkRange(pc, 2, "long Delay");
				int ticks = (opcode & 7) * 256 + u8(pc + 1) + 0x78;
				return new WireCommand(pc, 2, Op.DELAY, ticks, 0, 0);
			}
			if (opcode < 0xD8) {
				checkRange(pc, 3, "Play");
				int lengthByte = u8(pc + 2);
				int size = lengthByte >= 0xC0 ? 4 : 3;
				checkRange(pc, size, "Play");
				int length = lengthByte < 0xC0
					? lengthByte
					: ((lengthByte & 0x3F) << 8) + 0xC0 + u8(pc + 3);
				return new WireCommand(pc, size, Op.PLAY, opcode & 0x7F, u8(pc + 1) & 0x7F, length);
			}
			if (opcode < 0xE0 || opcode > 0xF8)
				throw new SfxFormatException(String.format("Invalid sequence opcode %02X at 0x%04X", opcode, pc));

			switch (opcode) {
				case 0xE0:
					return wire1(pc, Op.SET_VOLUME);
				case 0xE1:
					return wire1(pc, Op.SET_PAN);
				case 0xE2:
					return wire2(pc, Op.SET_INSTRUMENT);
				case 0xE3:
					return wire1(pc, Op.SET_REVERB);
				case 0xE4:
					return wire1(pc, Op.SET_ENVELOPE);
				case 0xE5:
					checkRange(pc, 2, "CoarseTune");
					return new WireCommand(pc, 2, Op.COARSE_TUNE, (byte) data[pc + 1], 0, 0);
				case 0xE6:
					return wire1(pc, Op.FINE_TUNE);
				case 0xE7:
					return new WireCommand(pc, 1, Op.WAIT_FOR_END);
				case 0xE8:
					WireCommand wire = wireU16Byte(pc, Op.PITCH_SWEEP);
					// The player deliberately ignores the wire-format high bit here.
					wire.b &= 0x7F;
					return wire;
				case 0xE9:
					return wire1(pc, Op.START_LOOP);
				case 0xEA:
					return new WireCommand(pc, 1, Op.END_LOOP);
				case 0xEB:
					return new WireCommand(pc, 1, Op.WAIT_FOR_RELEASE);
				case 0xEC:
					return wire1(pc, Op.SET_CURRENT_VOLUME);
				case 0xED:
					return wireU16Byte(pc, Op.VOLUME_RAMP);
				case 0xEE:
					checkRange(pc, 4, "SetAlternative");
					WireCommand alternative = new WireCommand(pc, 4, Op.SET_ALTERNATIVE, u8(pc + 1), 0, 0);
					alternative.target = u16(pc + 2);
					return alternative;
				case 0xEF:
					return new WireCommand(pc, 1, Op.STOP);
				case 0xF0:
					checkRange(pc, 3, "Jump");
					WireCommand jump = new WireCommand(pc, 3, Op.JUMP);
					jump.target = u16(pc + 1);
					return jump;
				case 0xF1:
					return new WireCommand(pc, 1, Op.RESTART);
				case 0xF2:
					return new WireCommand(pc, 1, Op.NOP);
				case 0xF3:
					return wire1(pc, Op.SET_RANDOM_PITCH);
				case 0xF4:
					return wire1(pc, Op.SET_RANDOM_VELOCITY);
				case 0xF5:
					return wire1(pc, Op.SET_RANDOM_PAN);
				case 0xF6:
					checkRange(pc, 3, "SetPressEnvelope");
					WireCommand envelope = new WireCommand(pc, 3, Op.SET_PRESS_ENVELOPE);
					envelope.target = u16(pc + 1);
					return envelope;
				case 0xF7:
					checkRange(pc, 5, "Spawn");
					WireCommand spawn = new WireCommand(pc, 5, Op.SPAWN);
					spawn.target = u16(pc + 1);
					spawn.a = u16(pc + 3);
					return spawn;
				case 0xF8:
					return wire1(pc, Op.SET_ALTERNATIVE_VOLUME);
				default:
					throw new AssertionError();
			}
		}

		private WireCommand wire1(int pc, Op op)
		{
			checkRange(pc, 2, op.name());
			return new WireCommand(pc, 2, op, u8(pc + 1), 0, 0);
		}

		private WireCommand wire2(int pc, Op op)
		{
			checkRange(pc, 3, op.name());
			return new WireCommand(pc, 3, op, u8(pc + 1), u8(pc + 2), 0);
		}

		private WireCommand wireU16Byte(int pc, Op op)
		{
			checkRange(pc, 4, op.name());
			return new WireCommand(pc, 4, op, u16(pc + 1), u8(pc + 3), 0);
		}

		private Command toModelCommand(WireCommand wire, Map<Integer, String> labels, DecodeContext context)
		{
			switch (wire.op) {
				case END:
				case WAIT_FOR_END:
				case END_LOOP:
				case WAIT_FOR_RELEASE:
				case STOP:
				case RESTART:
				case NOP:
					return new Command(wire.op);
				case DELAY:
				case SET_VOLUME:
				case SET_PAN:
				case SET_REVERB:
				case SET_ENVELOPE:
				case COARSE_TUNE:
				case FINE_TUNE:
				case START_LOOP:
				case SET_CURRENT_VOLUME:
				case SET_RANDOM_PITCH:
				case SET_RANDOM_VELOCITY:
				case SET_RANDOM_PAN:
				case SET_ALTERNATIVE_VOLUME:
					return new Command(wire.op, wire.a);
				case PLAY:
					return new Command(wire.op, wire.a, wire.b, wire.c);
				case SET_INSTRUMENT:
				case PITCH_SWEEP:
				case VOLUME_RAMP:
					return new Command(wire.op, wire.a, wire.b);
				case SET_ALTERNATIVE:
					if (!wire.alternativeCanTrigger)
						return new Command(Op.NOP);
					Command command = new Command(wire.op, wire.a);
					command.ref = labels.get(wire.target);
					return command;
				case JUMP:
					return Command.reference(wire.op, labels.get(wire.target));
				case SET_PRESS_ENVELOPE:
					Command envelopeCommand = new Command(wire.op);
					if (wire.target != 0)
						envelopeCommand.ref = decodeEnvelope(wire.target).name;
					return envelopeCommand;
				case SPAWN:
					// Custom F7 commands are ignored by the engine when data[0]
					// (the root pointer) is zero, regardless of routing bits.
					if (wire.target == 0)
						return new Command(Op.NOP);
					return Command.reference(wire.op, decodeSpawn(wire.target, wire.a, context).name);
				default:
					throw new AssertionError();
			}
		}

		private Envelope decodeEnvelope(int offset)
		{
			Envelope known = envelopesByOffset.get(offset);
			if (known != null)
				return known;

			Envelope candidate = new Envelope("pending");
			int pc = offset;
			for (int count = 0; count < 4096; count++) {
				checkRange(pc, 2, "custom press envelope");
				int op = u8(pc);
				int arg = u8(pc + 1);
				pc += 2;
				EnvelopeCommand command;
				try {
					command = EnvelopeProgram.decodeCommand(op, arg);
				}
				catch (IllegalArgumentException e) {
					throw new SfxFormatException(String.format("%s at 0x%04X", e.getMessage(), pc - 2));
				}
				candidate.commands.add(command);
				if (command.op == EnvelopeOp.END)
					break;
			}
			if (candidate.commands.isEmpty()
				|| candidate.commands.get(candidate.commands.size() - 1).op != EnvelopeOp.END)
				throw new SfxFormatException(String.format("Unterminated envelope at 0x%04X", offset));

			String key = envelopeKey(candidate);
			Envelope envelope = envelopesByContent.get(key);
			if (envelope == null) {
				envelope = candidate;
				envelope.name = String.format("envelope%02d", archive.envelopes.size() + 1);
				archive.envelopes.add(envelope);
				envelopesByContent.put(key, envelope);
			}
			envelopesByOffset.put(offset, envelope);
			return envelope;
		}

		private SpawnedEffect decodeSpawn(int root, int info, DecodeContext context)
		{
			long key = ((long) root << 16) | info;
			SpawnedEffect known = context.spawns.get(key);
			if (known != null)
				return known;

			int polyMode = (info >>> 5) & 3;
			int trackCount = polyMode == 0 ? 1 : 2 << (polyMode - 1);
			SpawnedEffect spawned = new SpawnedEffect("spawn" + (context.spawns.size() + 1));
			spawned.routing = decodeRouting(info, trackCount, "spawned effect " + spawned.name);
			context.spawns.put(key, spawned);
			context.sound.spawnedEffects.add(spawned);

			if (trackCount == 1) {
				spawned.tracks.add(new Track(0, decodeDefinition(root, context)));
			}
			else {
				checkRange(root, trackCount * 4, "spawned polyphony table");
				for (int slot = 0; slot < trackCount; slot++) {
					int trackRoot = u16(root + slot * 4);
					int trackInfo = u16(root + slot * 4 + 2);
					Track track = new Track(slot,
						trackRoot == 0 ? Empty.INSTANCE : decodeDefinition(trackRoot, context));
					if (spawned.routing.allocation == FIXED && trackRoot != 0) {
						track.player = trackInfo & 7;
						track.priority = (trackInfo >>> 8) & 3;
					}
					spawned.tracks.add(track);
				}
			}
			return spawned;
		}

		private int u8(int offset)
		{
			return data[offset] & 0xFF;
		}

		private int u16(int offset)
		{
			checkRange(offset, 2, "16-bit value");
			return readU16(data, offset);
		}

		private void checkRange(int offset, int length, String description)
		{
			if (offset < 0 || length < 0 || offset > data.length - length)
				throw new SfxFormatException(String.format(
					"%s at 0x%04X..0x%04X lies outside declared file size 0x%04X",
					description, offset, offset + length, data.length));
		}
	}

	private static final class DecodeContext
	{
		final Sound sound;
		final Map<Long, SpawnedEffect> spawns = new LinkedHashMap<>();

		DecodeContext(Sound sound)
		{
			this.sound = sound;
		}
	}

	private record ExecutionState(
		int pc,
		int saved,
		int loopStart,
		int loopCount,
		int alternative,
		int alternativeSource)
	{}

	private static final class WireRegion
	{
		final List<WireCommand> commands = new ArrayList<>();
	}

	private static final class WireCommand
	{
		final int offset;
		final int size;
		final Op op;
		int a;
		int b;
		int c;
		int target = -1;
		boolean alternativeCanTrigger;

		WireCommand(int offset, int size, Op op)
		{
			this(offset, size, op, 0, 0, 0);
		}

		WireCommand(int offset, int size, Op op, int a, int b, int c)
		{
			this.offset = offset;
			this.size = size;
			this.op = op;
			this.a = a;
			this.b = b;
			this.c = c;
		}
	}

	private static String envelopeKey(Envelope envelope)
	{
		StringBuilder key = new StringBuilder();
		for (EnvelopeCommand command : envelope.commands)
			key.append(command.op).append(':').append(command.durationIndex).append(':').append(command.value).append(';');
		return key.toString();
	}

	private static int readU16(byte[] data, int offset)
	{
		return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
	}

	private static int readU32(byte[] data, int offset)
	{
		return (data[offset] & 0xFF) << 24
			| (data[offset + 1] & 0xFF) << 16
			| (data[offset + 2] & 0xFF) << 8
			| data[offset + 3] & 0xFF;
	}

	// Canonical encoder.  It globally interns one-shots and separable sequence
	// regions, including private copies of the vanilla Jump/Restart tails.
	private static final class Encoder
	{
		private final SfxArchive archive;
		private final ByteStore output = new ByteStore();
		private final int[] sections = new int[SECTIONS];
		private int extraSection;
		private boolean extended;

		private final Map<String, Envelope> envelopesByName = new LinkedHashMap<>();
		private final Map<Envelope, Integer> envelopeOffsets = new IdentityHashMap<>();
		private final Map<Definition, Integer> definitionOffsets = new IdentityHashMap<>();
		private final Set<Definition> pointerDefinitions = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		private final List<Definition> definitionOrder = new ArrayList<>();
		private final Map<Sequence, SequenceLink> sequenceLinks = new IdentityHashMap<>();
		private final List<CompiledRegion> regions = new ArrayList<>();
		private final Map<SpawnedEffect, SpawnLink> spawnLinks = new IdentityHashMap<>();
		private final List<SpawnLink> spawnOrder = new ArrayList<>();
		private final List<EffectScope> scopes = new ArrayList<>();
		private final Map<String, Integer> polyTableOffsets = new LinkedHashMap<>();
		private int nextSpawnID;

		Encoder(SfxArchive archive)
		{
			this.archive = Objects.requireNonNull(archive);
		}

		byte[] encode()
		{
			prepareFixedLayout();
			indexObjects();
			allocateEnvelopes();
			allocateOneShots();
			compileAndAllocateRegions();
			allocateSpawns();
			writeSoundTables();
			patchRegionRelocations();
			writeHeader();

			if (output.size() > 0x10000)
				throw new SfxFormatException("SEF offsets exceed the 16-bit file-relative address space");
			return output.toByteArray();
		}

		/** Reserves the header and every table. Extended sections exist only if a sound uses them. */
		private void prepareFixedLayout()
		{
			for (Sound sound : archive.sounds.values())
				extended |= SfxArchive.isExtended(sound.id);

			int cursor = extended ? EXT_HEADER_SIZE : HEADER_SIZE;
			for (int group = 0; group < 4; group++) {
				sections[group] = cursor;
				cursor += ROUTED_SECTION_SIZE;
				sections[group + 4] = cursor;
				cursor += DIRECT_SECTION_SIZE;
			}
			extraSection = cursor;
			cursor += (extended ? EXTRA_COUNT : VANILLA_EXTRA_COUNT) * 4;

			boolean[] used = new boolean[SECTIONS];
			for (Sound sound : archive.sounds.values()) {
				if ((sound.id & SfxArchive.EXT_FLAG) != 0)
					used[sectionOf(sound.id)] = true;
			}
			for (int section = VANILLA_SECTIONS; section < SECTIONS; section++) {
				if (used[section]) {
					sections[section] = cursor;
					cursor += isRoutedSection(section) ? ROUTED_SECTION_SIZE : DIRECT_SECTION_SIZE;
				}
			}
			output.ensureSize(cursor);
		}

		private static boolean isRoutedSection(int section)
		{
			return (section & 4) == 0;
		}

		/** The section of a sound ID that selects a section, numbered as the engine does. */
		private static int sectionOf(int id)
		{
			int base = id & SfxArchive.MAX_VANILLA_ID;
			int group = ((base - 1) >>> 8) & 3;
			int section = SfxArchive.slotOf(id) == SfxArchive.Slot.ROUTED ? group : group + 4;
			return (id & SfxArchive.EXT_FLAG) != 0 ? section + VANILLA_SECTIONS : section;
		}

		private void indexObjects()
		{
			for (Envelope envelope : archive.envelopes) {
				if (envelopesByName.putIfAbsent(envelope.name, envelope) != null)
					throw new SfxFormatException("Duplicate envelope name: " + envelope.name);
			}

			for (Sound sound : archive.sounds.values()) {
				EffectScope scope = new EffectScope(sound);
				scopes.add(scope);
				for (SpawnedEffect spawned : sound.spawnedEffects) {
					if (scope.spawns.putIfAbsent(spawned.name, spawned) != null)
						throw new SfxFormatException(sound.name + " has duplicate spawned effect " + spawned.name);
					SpawnLink link = new SpawnLink(spawned, scope, nextSpawnID++);
					spawnLinks.put(spawned, link);
					spawnOrder.add(link);
				}

				boolean pointerBacked = isPointerBacked(sound.id);
				for (Track track : sound.tracks)
					registerDefinition(track.definition, scope, pointerBacked);
				for (SpawnedEffect spawned : sound.spawnedEffects) {
					for (Track track : spawned.tracks)
						registerDefinition(track.definition, scope, true);
				}
			}
		}

		private void registerDefinition(Definition definition, EffectScope scope, boolean needsPointer)
		{
			if (definition == null || definition == Empty.INSTANCE)
				return;
			if (needsPointer)
				pointerDefinitions.add(definition);
			if (!definitionOrder.contains(definition))
				definitionOrder.add(definition);
			if (definition instanceof Sequence sequence) {
				if (!needsPointer)
					throw new SfxFormatException("Direct high/extra sequence trampolines are not supported");
				sequenceLinks.computeIfAbsent(sequence, ignored -> compileSequence(sequence, scope));
			}
		}

		private void allocateEnvelopes()
		{
			Map<String, Integer> interned = new LinkedHashMap<>();
			for (Envelope envelope : archive.envelopes) {
				byte[] bytes = encodeEnvelope(envelope);
				String key = HexFormat.of().formatHex(bytes);
				Integer offset = interned.get(key);
				if (offset == null) {
					offset = output.append(bytes);
					interned.put(key, offset);
				}
				envelopeOffsets.put(envelope, offset);
			}
		}

		private void allocateOneShots()
		{
			Map<String, Integer> interned = new LinkedHashMap<>();
			for (Definition definition : definitionOrder) {
				if (!(definition instanceof OneShot oneShot) || !pointerDefinitions.contains(definition))
					continue;
				byte[] bytes = encodeOneShot(oneShot, false);
				String key = HexFormat.of().formatHex(bytes);
				Integer offset = interned.get(key);
				if (offset == null) {
					offset = output.append(bytes);
					interned.put(key, offset);
				}
				definitionOffsets.put(definition, offset);
			}
		}

		private void compileAndAllocateRegions()
		{
			resolveRegionReferences();
			markAlternativeRestartRisks();
			if (regions.isEmpty())
				return;

			int[] groups = refineGroups();
			groups = refineGroups();

			int groupCount = Arrays.stream(groups).max().orElse(-1) + 1;
			int[] groupOffsets = new int[groupCount];
			Arrays.fill(groupOffsets, -1);
			for (int i = 0; i < regions.size(); i++) {
				CompiledRegion region = regions.get(i);
				region.group = groups[i];
				if (groupOffsets[region.group] < 0)
					groupOffsets[region.group] = output.append(region.bytes);
			}
			for (CompiledRegion region : regions)
				region.outputOffset = groupOffsets[region.group];

			for (Map.Entry<Sequence, SequenceLink> entry : sequenceLinks.entrySet())
				definitionOffsets.put(entry.getKey(), entry.getValue().entry.outputOffset);
		}

		private int[] refineGroups()
		{
			int[] groups = assignInitialGroups();
			while (true) {
				Map<String, Integer> ids = new LinkedHashMap<>();
				int[] refined = new int[regions.size()];
				for (int i = 0; i < regions.size(); i++) {
					CompiledRegion region = regions.get(i);
					String key = groups[i] + "|" + region.partitionKey(groups);
					refined[i] = ids.computeIfAbsent(key, ignored -> ids.size());
				}
				if (Arrays.equals(groups, refined))
					return groups;
				groups = refined;
			}
		}

		private int[] assignInitialGroups()
		{
			Map<String, Integer> ids = new LinkedHashMap<>();
			int[] groups = new int[regions.size()];
			for (int i = 0; i < regions.size(); i++)
				groups[i] = ids.computeIfAbsent(regions.get(i).partitionKey(null), ignored -> ids.size());
			return groups;
		}

		/**
		 * A fallthrough suffix normally has to remain adjacent to its prefix.
		 * If it contains no Restart, however, a Jump is equivalent because the
		 * continuation saved by that new Jump can never be observed.  Interning
		 * those tails keeps XML definitions private while recovering the compact
		 * shared graph used by the vanilla archive.
		 */
		private boolean isContinuationIndependent(CompiledRegion entryRegion, int entryOffset)
		{
			Deque<CommandLocation> pending = new ArrayDeque<>();
			Set<CommandLocation> visited = new HashSet<>();
			pending.add(new CommandLocation(entryRegion, entryRegion.commandIndex(entryOffset)));

			while (!pending.isEmpty()) {
				CommandLocation location = pending.remove();
				while (location.commandIndex >= 0
					&& location.commandIndex < location.region.commandOps.size()
					&& visited.add(location)) {
					Op op = location.region.commandOps.get(location.commandIndex);
					int commandOffset = location.region.commandOffsets.get(location.commandIndex);
					if (op == Op.RESTART)
						return false;
					if (op == Op.END || op == Op.JUMP)
						break;
					if (op == Op.SET_ALTERNATIVE) {
						Relocation relocation = location.region.labelRelocationAt(commandOffset + 2);
						if (relocation == null)
							return false;
						pending.add(new CommandLocation(relocation.targetRegion,
							relocation.targetRegion.commandIndex(relocation.targetOffset)));
					}
					location = new CommandLocation(location.region, location.commandIndex + 1);
				}
			}
			return true;
		}

		private void allocateSpawns()
		{
			for (SpawnLink link : spawnOrder) {
				SpawnedEffect spawned = link.spawned;
				if (spawned.tracks.size() == 1) {
					link.dataOffset = definitionOffset(spawned.tracks.get(0).definition);
				}
				else {
					link.dataOffset = allocatePolyTable(spawned.tracks, spawned.routing);
				}
				link.info = routingInfo(spawned.routing, spawned.tracks.size());
			}
		}

		private void writeSoundTables()
		{
			for (Sound sound : archive.sounds.values()) {
				TableSlot slot = tableSlot(sound.id);
				if (slot == null)
					throw new SfxFormatException(String.format("Sound ID %04X has no DAT1 table slot", sound.id));

				if (sound.isEmpty())
					continue;
				if (slot.direct) {
					if (sound.tracks.size() != 1 || !(sound.tracks.get(0).definition instanceof OneShot oneShot))
						throw new SfxFormatException(String.format(
							"Direct sound %04X must contain exactly one compact-representable OneShot", sound.id));
					byte[] bytes = encodeOneShot(oneShot, true);
					output.writeBytes(slot.offset, bytes);
					continue;
				}

				if (sound.routing == null)
					throw new SfxFormatException(String.format("Pointer-backed sound %04X is missing Routing", sound.id));
				int dataOffset;
				if (sound.tracks.size() == 1) {
					dataOffset = definitionOffset(sound.tracks.get(0).definition);
				}
				else {
					dataOffset = allocatePolyTable(sound.tracks, sound.routing);
				}
				output.writeU16(slot.offset, dataOffset);
				output.writeU16(slot.offset + 2, routingInfo(sound.routing, sound.tracks.size()));
			}
		}

		private int allocatePolyTable(List<Track> tracks, Routing routing)
		{
			byte[] bytes = new byte[tracks.size() * 4];
			for (int i = 0; i < tracks.size(); i++) {
				Track track = tracks.get(i);
				int root = definitionOffset(track.definition);
				int info = 0;
				if (root != 0 && routing.allocation == FIXED) {
					if (track.player == null || track.priority == null)
						throw new SfxFormatException("Fixed polyphonic track " + track.slot + " needs player and priority");
					info = track.player | track.priority << 8;
				}
				bytes[i * 4] = (byte) (root >>> 8);
				bytes[i * 4 + 1] = (byte) root;
				bytes[i * 4 + 2] = (byte) (info >>> 8);
				bytes[i * 4 + 3] = (byte) info;
			}
			String key = HexFormat.of().formatHex(bytes);
			Integer known = polyTableOffsets.get(key);
			if (known != null)
				return known;
			output.align(2);
			int offset = output.append(bytes);
			polyTableOffsets.put(key, offset);
			return offset;
		}

		private void patchRegionRelocations()
		{
			Set<Integer> patchedGroups = new HashSet<>();
			for (CompiledRegion region : regions) {
				if (!patchedGroups.add(region.group))
					continue;
				for (Relocation relocation : region.relocations) {
					int target;
					switch (relocation.type) {
						case LABEL:
							target = relocation.targetRegion.outputOffset + relocation.targetOffset;
							break;
						case ENVELOPE:
							target = requiredOffset(envelopeOffsets.get(relocation.envelope),
								"Unallocated envelope " + relocation.ref);
							break;
						case SPAWN:
							SpawnLink link = spawnLinks.get(relocation.spawn);
							if (link == null || link.dataOffset < 0)
								throw new SfxFormatException("Unallocated spawned effect " + relocation.ref);
							target = link.dataOffset;
							break;
						default:
							throw new AssertionError();
					}
					output.writeU16(region.outputOffset + relocation.position, target);
				}
			}
		}

		private int requiredOffset(Integer offset, String message)
		{
			if (offset == null)
				throw new SfxFormatException(message);
			return offset;
		}

		private void writeHeader()
		{
			output.writeBytes(0, "SEF ".getBytes(StandardCharsets.US_ASCII));
			output.writeU32(4, output.size());
			output.writeBytes(8, "DAT1".getBytes(StandardCharsets.US_ASCII));
			output.writeByte(0x0E, extended ? HAS_EXT_SECTIONS : HAS_EXTRA_SECTION);
			for (int i = 0; i < VANILLA_SECTIONS; i++)
				output.writeU16(0x10 + i * 2, sections[i]);
			output.writeU16(0x20, extraSection);
			if (extended) {
				for (int i = VANILLA_SECTIONS; i < SECTIONS; i++)
					output.writeU16(EXT_SECTIONS_OFFSET + (i - VANILLA_SECTIONS) * 2, sections[i]);
			}
		}

		private SequenceLink compileSequence(Sequence sequence, EffectScope scope)
		{
			SequenceLink link = new SequenceLink(sequence, scope);
			CompiledRegion current = new CompiledRegion(regions.size());
			regions.add(current);
			link.regions.add(current);
			boolean startNewRegion = false;
			Op lastOp = null;

			for (Node node : sequence.nodes) {
				if (startNewRegion) {
					current = new CompiledRegion(regions.size());
					regions.add(current);
					link.regions.add(current);
					startNewRegion = false;
				}

				if (node instanceof Label label) {
					if (link.labels.putIfAbsent(label.name(), new LabelLocation(current, current.builder.size())) != null)
						throw new SfxFormatException("Duplicate sequence label: " + label.name());
				}
				else if (node instanceof Command command) {
					writeCommand(current, command, scope);
					lastOp = command.op;
					startNewRegion = command.op == Op.END || command.op == Op.RESTART;
				}
			}

			if (lastOp == null)
				throw new SfxFormatException("Sequence has no commands");
			if (lastOp != Op.END && lastOp != Op.RESTART) {
				current.commandOffsets.add(current.builder.size());
				current.commandOps.add(Op.END);
				current.builder.write(0);
			}

			LabelLocation entry = link.labels.get(Sequence.START_LABEL);
			if (entry == null)
				throw new SfxFormatException("Sequence is missing its implicit start label");
			if (entry.offset != 0)
				throw new SfxFormatException("Sequence start must begin a separable region");
			link.entry = entry.region;
			entry.region.prepend(sequence.flags());

			for (Map.Entry<String, LabelLocation> label : link.labels.entrySet()) {
				if (label.getValue().region == entry.region)
					label.setValue(new LabelLocation(entry.region, label.getValue().offset + 1));
			}
			for (CompiledRegion region : link.regions)
				region.finish();
			return link;
		}

		private void writeCommand(CompiledRegion region, Command command, EffectScope scope)
		{
			ByteArrayOutputStream out = region.builder;
			region.commandOffsets.add(out.size());
			region.commandOps.add(command.op);
			switch (command.op) {
				case END:
					out.write(0);
					break;
				case DELAY:
					writeDelay(out, command.a);
					break;
				case PLAY:
					writePlay(out, command.a, command.b, command.c);
					break;
				case SET_VOLUME:
					writeByteCommand(out, 0xE0, command.a);
					break;
				case SET_PAN:
					writeByteCommand(out, 0xE1, command.a);
					break;
				case SET_INSTRUMENT:
					out.write(0xE2);
					out.write(command.a);
					out.write(command.b);
					break;
				case SET_REVERB:
					writeByteCommand(out, 0xE3, command.a);
					break;
				case SET_ENVELOPE:
					writeByteCommand(out, 0xE4, command.a);
					break;
				case COARSE_TUNE:
					writeByteCommand(out, 0xE5, command.a);
					break;
				case FINE_TUNE:
					writeByteCommand(out, 0xE6, command.a);
					break;
				case WAIT_FOR_END:
					out.write(0xE7);
					break;
				case PITCH_SWEEP:
					writeU16ByteCommand(out, 0xE8, command.a, command.b);
					break;
				case START_LOOP:
					writeByteCommand(out, 0xE9, command.a);
					break;
				case END_LOOP:
					out.write(0xEA);
					break;
				case WAIT_FOR_RELEASE:
					out.write(0xEB);
					break;
				case SET_CURRENT_VOLUME:
					writeByteCommand(out, 0xEC, command.a);
					break;
				case VOLUME_RAMP:
					writeU16ByteCommand(out, 0xED, command.a, command.b);
					break;
				case SET_ALTERNATIVE:
					out.write(0xEE);
					out.write(command.a);
					addRelocation(region, RelocationType.LABEL, command.ref, null, null);
					break;
				case STOP:
					out.write(0xEF);
					break;
				case JUMP:
					out.write(0xF0);
					addRelocation(region, RelocationType.LABEL, command.ref, null, null);
					break;
				case RESTART:
					out.write(0xF1);
					break;
				case NOP:
					out.write(0xF2);
					break;
				case SET_RANDOM_PITCH:
					writeByteCommand(out, 0xF3, command.a);
					break;
				case SET_RANDOM_VELOCITY:
					writeByteCommand(out, 0xF4, command.a);
					break;
				case SET_RANDOM_PAN:
					writeByteCommand(out, 0xF5, command.a);
					break;
				case SET_PRESS_ENVELOPE:
					out.write(0xF6);
					if (command.ref == null || command.ref.isBlank()) {
						writeU16(out, 0);
					}
					else {
						Envelope envelope = envelopesByName.get(command.ref);
						if (envelope == null)
							throw new SfxFormatException("Unknown envelope reference: " + command.ref);
						addRelocation(region, RelocationType.ENVELOPE, command.ref, envelope, null);
					}
					break;
				case SPAWN:
					out.write(0xF7);
					SpawnedEffect spawned = scope.spawns.get(command.ref);
					if (spawned == null)
						throw new SfxFormatException("Unknown spawned effect reference: " + command.ref);
					addRelocation(region, RelocationType.SPAWN, command.ref, null, spawned);
					writeU16(out, routingInfo(spawned.routing, spawned.tracks.size()));
					break;
				case SET_ALTERNATIVE_VOLUME:
					writeByteCommand(out, 0xF8, command.a);
					break;
			}
		}

		private void addRelocation(CompiledRegion region, RelocationType type, String ref,
			Envelope envelope, SpawnedEffect spawned)
		{
			int position = region.builder.size();
			writeU16(region.builder, 0);
			Relocation relocation = new Relocation(position, type, ref);
			relocation.envelope = envelope;
			relocation.spawn = spawned;
			if (spawned != null) {
				SpawnLink link = spawnLinks.get(spawned);
				if (link == null)
					throw new SfxFormatException("Unindexed spawned effect: " + ref);
				relocation.spawnStableID = link.stableID;
			}
			region.relocations.add(relocation);
		}

		private void resolveRegionReferences()
		{
			for (SequenceLink link : sequenceLinks.values()) {
				for (CompiledRegion region : link.regions) {
					for (Relocation relocation : region.relocations) {
						if (relocation.type != RelocationType.LABEL)
							continue;
						String labelName = labelReference(relocation.ref);
						LabelLocation target = link.labels.get(labelName);
						if (target == null)
							throw new SfxFormatException("Unknown sequence label: " + relocation.ref);
						relocation.targetRegion = target.region;
						relocation.targetOffset = target.offset;
					}
				}
			}
		}

		private void markAlternativeRestartRisks()
		{
			for (SequenceLink link : sequenceLinks.values()) {
				boolean risk = false;
				for (CompiledRegion region : link.regions) {
					for (int i = 0; i < region.commandOps.size(); i++) {
						if (region.commandOps.get(i) != Op.SET_ALTERNATIVE)
							continue;
						int commandOffset = region.commandOffsets.get(i);
						Relocation target = region.labelRelocationAt(commandOffset + 2);
						if (target == null
							|| !isContinuationIndependent(target.targetRegion, target.targetOffset)) {
							risk = true;
							break;
						}
					}
					if (risk)
						break;
				}
				if (risk) {
					for (CompiledRegion region : link.regions)
						region.alternativeRestartRisk = true;
				}
			}
		}

		private String labelReference(String ref)
		{
			if (ref == null || ref.isBlank())
				throw new SfxFormatException("Missing label reference");
			if (ref.indexOf(':') >= 0)
				throw new SfxFormatException("Scoped label references are not implemented: " + ref);
			return ref;
		}

		private int definitionOffset(Definition definition)
		{
			if (definition == null || definition == Empty.INSTANCE)
				return 0;
			Integer offset = definitionOffsets.get(definition);
			if (offset == null)
				throw new SfxFormatException("Definition was not allocated: " + definition.getClass().getSimpleName());
			return offset;
		}

		private int routingInfo(Routing routing, int trackCount)
		{
			if (routing == null)
				throw new SfxFormatException("Effect is missing Routing");
			int polyMode;
			switch (trackCount) {
				case 1:
					polyMode = 0;
					break;
				case 2:
					polyMode = 1;
					break;
				case 4:
					polyMode = 2;
					break;
				case 8:
					polyMode = 3;
					break;
				default:
					throw new SfxFormatException("Track count must be 1, 2, 4, or 8: " + trackCount);
			}
			int info = polyMode << 5;
			if (routing.allocation == DYNAMIC)
				info |= 0x80;
			if (trackCount == 1) {
				info |= routing.allocation == DYNAMIC ? routing.maxPlayer : routing.player;
				info |= routing.priority << 8;
			}
			else if (routing.allocation == DYNAMIC) {
				// The engine ignores these bits for dynamic polyphony, but seven is
				// the canonical semantic value: all eight players are searched.
				info |= 7;
				info |= routing.priority << 8;
			}
			info |= routing.exclusiveGroup << 11;
			return info;
		}

		private byte[] encodeOneShot(OneShot oneShot, boolean direct)
		{
			boolean compact = isCompactRepresentable(oneShot);
			if (direct && !compact)
				throw new SfxFormatException("Direct high/extra OneShot is not COMPACT-representable");
			if (compact) {
				int packed = ((oneShot.volume & 0x7C) << 1) | oneShot.randomPitch / 8;
				return new byte[] {
						(byte) (oneShot.flags() | 2),
						(byte) oneShot.bank,
						(byte) oneShot.patch,
						(byte) packed
				};
			}
			return new byte[] {
					(byte) oneShot.flags(),
					(byte) oneShot.bank,
					(byte) oneShot.patch,
					(byte) oneShot.volume,
					(byte) oneShot.pan,
					(byte) oneShot.reverb,
					(byte) oneShot.pitch,
					(byte) (oneShot.randomPitch / 8)
			};
		}

		private byte[] encodeEnvelope(Envelope envelope)
		{
			return EnvelopeProgram.encode(envelope.commands);
		}

		private void writeDelay(ByteArrayOutputStream out, int ticks)
		{
			if (ticks >= 1 && ticks < 0x78) {
				out.write(ticks);
				return;
			}
			if (ticks < 0x78 || ticks > 2167)
				throw new SfxFormatException("Delay ticks must be 1..2167: " + ticks);
			int encoded = ticks - 0x78;
			out.write(0x78 | encoded >>> 8);
			out.write(encoded);
		}

		private void writePlay(ByteArrayOutputStream out, int pitch, int velocity, int length)
		{
			out.write(0x80 | pitch);
			out.write(velocity);
			if (length < 0xC0) {
				out.write(length);
			}
			else {
				int encoded = length - 0xC0;
				out.write(0xC0 | encoded >>> 8);
				out.write(encoded);
			}
		}

		private void writeByteCommand(ByteArrayOutputStream out, int opcode, int value)
		{
			out.write(opcode);
			out.write(value);
		}

		private void writeU16ByteCommand(ByteArrayOutputStream out, int opcode, int value, int byteValue)
		{
			out.write(opcode);
			writeU16(out, value);
			out.write(byteValue);
		}

		private void writeU16(ByteArrayOutputStream out, int value)
		{
			out.write(value >>> 8);
			out.write(value);
		}

		private TableSlot tableSlot(int id)
		{
			SfxArchive.Slot slot = SfxArchive.slotOf(id);
			if (slot == null)
				return null;
			if (id >= SfxArchive.FIRST_EXTRA_ID && id <= SfxArchive.LAST_EXTRA_ID)
				return new TableSlot(extraSection + (id - SfxArchive.FIRST_EXTRA_ID) * 4, true);

			int index = (id - 1) & 0xFF;
			if (slot == SfxArchive.Slot.ROUTED)
				return new TableSlot(sections[sectionOf(id)] + index * 4, false);
			return new TableSlot(sections[sectionOf(id)] + (index - ROUTED_PER_SECTION) * 4, true);
		}

		private boolean isPointerBacked(int id)
		{
			return SfxArchive.slotOf(id) == SfxArchive.Slot.ROUTED;
		}
	}

	private static final class EffectScope
	{
		final Sound sound;
		final Map<String, SpawnedEffect> spawns = new LinkedHashMap<>();

		EffectScope(Sound sound)
		{
			this.sound = sound;
		}
	}

	private static final class SequenceLink
	{
		final Sequence sequence;
		final EffectScope scope;
		final List<CompiledRegion> regions = new ArrayList<>();
		final Map<String, LabelLocation> labels = new LinkedHashMap<>();
		CompiledRegion entry;

		SequenceLink(Sequence sequence, EffectScope scope)
		{
			this.sequence = sequence;
			this.scope = scope;
		}
	}

	private record LabelLocation(CompiledRegion region, int offset)
	{}

	private enum RelocationType
	{
		LABEL,
		ENVELOPE,
		SPAWN
	}

	private static final class Relocation
	{
		int position;
		final RelocationType type;
		final String ref;
		CompiledRegion targetRegion;
		int targetOffset;
		Envelope envelope;
		SpawnedEffect spawn;
		int spawnStableID = -1;

		Relocation(int position, RelocationType type, String ref)
		{
			this.position = position;
			this.type = type;
			this.ref = ref;
		}
	}

	private static final class CompiledRegion
	{
		final int index;
		ByteArrayOutputStream builder = new ByteArrayOutputStream();
		byte[] bytes;
		final List<Relocation> relocations = new ArrayList<>();
		final List<Integer> commandOffsets = new ArrayList<>();
		final List<Op> commandOps = new ArrayList<>();
		boolean alternativeRestartRisk;
		int group = -1;
		int outputOffset = -1;

		CompiledRegion(int index)
		{
			this.index = index;
		}

		void prepend(int value)
		{
			byte[] old = builder.toByteArray();
			builder = new ByteArrayOutputStream(old.length + 1);
			builder.write(value);
			builder.writeBytes(old);
			for (Relocation relocation : relocations)
				relocation.position++;
			for (int i = 0; i < commandOffsets.size(); i++)
				commandOffsets.set(i, commandOffsets.get(i) + 1);
		}

		void finish()
		{
			bytes = builder.toByteArray();
		}

		String partitionKey(int[] groups)
		{
			StringBuilder key = new StringBuilder(HexFormat.of().formatHex(bytes));
			for (Relocation relocation : relocations) {
				key.append('|').append(relocation.position).append(':').append(relocation.type).append(':');
				switch (relocation.type) {
					case LABEL:
						if (relocation.targetRegion == this)
							key.append('I').append(relocation.targetOffset);
						else if (groups == null)
							key.append('X').append(relocation.targetOffset);
						else
							key.append('G').append(groups[relocation.targetRegion.index]).append(':').append(relocation.targetOffset);
						break;
					case ENVELOPE:
						key.append('E').append(envelopeKey(relocation.envelope));
						break;
					case SPAWN:
						key.append('S').append(relocation.spawnStableID);
						break;
				}
			}
			return key.toString();
		}

		String suffixKey(int start, int[] groups)
		{
			StringBuilder key = new StringBuilder(HexFormat.of().formatHex(bytes, start, bytes.length));
			for (Relocation relocation : relocations) {
				if (relocation.position < start)
					continue;
				key.append('|').append(relocation.position - start).append(':').append(relocation.type).append(':');
				switch (relocation.type) {
					case LABEL:
						if (relocation.targetRegion == this && relocation.targetOffset >= start)
							key.append('I').append(relocation.targetOffset - start);
						else
							key.append('G').append(groups[relocation.targetRegion.index])
								.append(':').append(relocation.targetOffset);
						break;
					case ENVELOPE:
						key.append('E').append(envelopeKey(relocation.envelope));
						break;
					case SPAWN:
						key.append('S').append(relocation.spawnStableID);
						break;
				}
			}
			return key.toString();
		}

		int commandIndex(int offset)
		{
			return commandOffsets.indexOf(offset);
		}

		Relocation labelRelocationAt(int position)
		{
			for (Relocation relocation : relocations) {
				if (relocation.type == RelocationType.LABEL && relocation.position == position)
					return relocation;
			}
			return null;
		}
	}

	private record CommandLocation(CompiledRegion region, int commandIndex)
	{}

	private static final class SpawnLink
	{
		final SpawnedEffect spawned;
		final EffectScope scope;
		final int stableID;
		int dataOffset = -1;
		int info;

		SpawnLink(SpawnedEffect spawned, EffectScope scope, int stableID)
		{
			this.spawned = spawned;
			this.scope = scope;
			this.stableID = stableID;
		}
	}

	private record TableSlot(int offset, boolean direct)
	{}

	private static final class ByteStore
	{
		private byte[] data = new byte[0x6000];
		private int size;

		int size()
		{
			return size;
		}

		void ensureSize(int required)
		{
			ensureCapacity(required);
			if (required > size)
				size = required;
		}

		int allocate(int length)
		{
			int offset = size;
			ensureSize(size + length);
			return offset;
		}

		int append(byte[] bytes)
		{
			int offset = allocate(bytes.length);
			writeBytes(offset, bytes);
			return offset;
		}

		void align(int alignment)
		{
			int aligned = (size + alignment - 1) / alignment * alignment;
			ensureSize(aligned);
		}

		void writeByte(int offset, int value)
		{
			checkWrite(offset, 1);
			data[offset] = (byte) value;
		}

		void writeU16(int offset, int value)
		{
			if (value < 0 || value > 0xFFFF)
				throw new SfxFormatException(String.format("16-bit relocation overflow: 0x%X", value));
			checkWrite(offset, 2);
			data[offset] = (byte) (value >>> 8);
			data[offset + 1] = (byte) value;
		}

		void writeU32(int offset, int value)
		{
			checkWrite(offset, 4);
			data[offset] = (byte) (value >>> 24);
			data[offset + 1] = (byte) (value >>> 16);
			data[offset + 2] = (byte) (value >>> 8);
			data[offset + 3] = (byte) value;
		}

		void writeBytes(int offset, byte[] bytes)
		{
			checkWrite(offset, bytes.length);
			System.arraycopy(bytes, 0, data, offset, bytes.length);
		}

		byte[] toByteArray()
		{
			return Arrays.copyOf(data, size);
		}

		private void checkWrite(int offset, int length)
		{
			if (offset < 0 || length < 0 || offset > size - length)
				throw new IllegalStateException(String.format("Write outside allocated buffer: %X + %X / %X", offset, length, size));
		}

		private void ensureCapacity(int required)
		{
			if (required <= data.length)
				return;
			int capacity = data.length;
			while (capacity < required)
				capacity *= 2;
			data = Arrays.copyOf(data, capacity);
		}
	}
}
