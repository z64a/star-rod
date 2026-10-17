package game.audio;

import static game.audio.SfxXmlKey.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import game.audio.AudioTables.Entry;
import game.audio.SfxArchive.Allocation;
import game.audio.SfxArchive.Command;
import game.audio.SfxArchive.Definition;
import game.audio.SfxArchive.Empty;
import game.audio.SfxArchive.Envelope;
import game.audio.SfxArchive.Label;
import game.audio.SfxArchive.OneShot;
import game.audio.SfxArchive.Op;
import game.audio.SfxArchive.Routing;
import game.audio.SfxArchive.Sequence;
import game.audio.SfxArchive.Slot;
import game.audio.SfxArchive.Sound;
import game.audio.SfxArchive.SpawnedEffect;
import game.audio.SfxArchive.Track;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

/**
 * The sound effect sources: sound_effects.xml, a table with an entry per sound that holds an inline one-shot
 * or names a sequence file under sfx/, and sfx/envelopes.xml, a table of the press envelopes that
 * sequences select by name.
 */
public final class SfxXml
{
	private static final String TABLE = "sound_effects.xml";
	private static final String ENVELOPES = "sfx/envelopes.xml";
	private static final int MAX_ID = 0x63FF;

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
	private static final Pattern SOURCE = Pattern.compile("sfx/[A-Za-z0-9_-]+\\.xml");

	private SfxXml()
	{}

	/**
	 * Reads the merged sound effect table and every sequence file it uses. A sound without an ID gets the
	 * first free slot of the kind it needs, in ascending ID order: vanilla slots, the extra section, and then
	 * the extended sections.
	 */
	public static SfxArchive read(AudioLayers layers, Presets.Addresses addresses)
	{
		Map<String, Entry> entries = AudioTables.merge(layers, TABLE, AudioTables.Key.Sounds, AudioTables.Key.Sound);

		Map<Sound, Entry> sounds = new LinkedHashMap<>();
		for (Entry e : entries.values()) {
			AudioTables.checkIdentifier(e);
			sounds.put(readSound(layers, addresses, e), e);
		}

		SfxArchive archive = new SfxArchive();
		for (Map.Entry<Sound, Entry> pair : sounds.entrySet()) {
			Sound sound = pair.getKey();
			Entry e = pair.getValue();
			if (e.id == null)
				continue;

			Slot slot = SfxArchive.slotOf(e.id);
			if (slot == null)
				e.complain(String.format("ID %04X is not a sound ID the engine can address", e.id));
			if (!sound.isEmpty() && slot != slotNeeded(sound))
				e.complain(String.format("ID %04X is a %s slot, but the sound %s Routing", e.id,
					slot.name().toLowerCase(), slot == Slot.DIRECT ? "has" : "has no"));
			sound.id = e.id;
			Sound other = archive.sounds.put(sound.id, sound);
			if (other != null)
				e.complain(String.format("ID %04X is already used by '%s'", sound.id, other.name));
		}

		int[] next = new int[Slot.values().length];
		Arrays.fill(next, 1);
		for (Map.Entry<Sound, Entry> pair : sounds.entrySet().stream()
			.sorted(Comparator.comparing(p -> p.getKey().name)).toList()) {
			Sound sound = pair.getKey();
			if (pair.getValue().id != null)
				continue;

			Slot slot = slotNeeded(sound);
			int id = next[slot.ordinal()];
			while (id <= MAX_ID && (SfxArchive.slotOf(id) != slot || archive.sounds.containsKey(id)))
				id++;
			if (id > MAX_ID)
				pair.getValue().complain("there are no free " + slot.name().toLowerCase() + " sound slots");
			next[slot.ordinal()] = id + 1;
			sound.id = id;
			archive.sounds.put(id, sound);
		}

		readEnvelopes(layers, archive);

		SfxValidator.validate(archive);
		return archive;
	}

	/** A routed slot holds anything with routing; a direct slot holds only a compact one-shot. Empty sounds fit either. */
	private static Slot slotNeeded(Sound sound)
	{
		return sound.routing != null ? Slot.ROUTED : Slot.DIRECT;
	}

	private static Sound readSound(AudioLayers layers, Presets.Addresses addresses, Entry e)
	{
		XmlReader reader = e.xmr;
		Element element = e.elem;
		Path manifest = reader.getSourceFile().toPath();
		checkAttributes(manifest, element, ATTR_ID, ATTR_NAME, ATTR_SRC, ATTR_EMPTY, ATTR_DELETE);

		Sound sound = new Sound(0, e.name);
		sound.file = manifest.toFile();

		Element routingElement = null;
		Element oneShotElement = null;
		for (Element child : childElements(manifest, element)) {
			switch (tagKey(manifest, element, child)) {
				case TAG_ROUTING:
					if (routingElement != null)
						throw error(manifest, child, "a sound cannot contain more than one Routing element");
					routingElement = child;
					break;
				case TAG_ONE_SHOT:
					if (oneShotElement != null)
						throw error(manifest, child, "a sound cannot contain more than one OneShot element");
					oneShotElement = child;
					break;
				default:
					throw unknownElement(manifest, element, child);
			}
		}

		boolean hasSource = reader.hasAttribute(element, ATTR_SRC);
		boolean isEmpty = flag(reader, element, ATTR_EMPTY);
		if ((hasSource ? 1 : 0) + (isEmpty ? 1 : 0) + (oneShotElement != null ? 1 : 0) != 1)
			throw error(manifest, element, "a sound must have exactly one of src, empty, or OneShot");
		if (isEmpty && routingElement != null)
			throw error(manifest, element, "an empty sound cannot have Routing");

		if (hasSource) {
			sound.source = reader.getAttribute(element, ATTR_SRC);
			if (!SOURCE.matcher(sound.source).matches() || sound.source.equals(ENVELOPES))
				throw error(manifest, element, "src must be sfx/<file>.xml: " + sound.source);
			File effectXml = layers.getFile(sound.source);
			if (effectXml == null)
				e.complain("there is no " + sound.source + " file in any layer");
			sound.file = effectXml;
			readEffect(sound, effectXml.toPath(), addresses);
			if (routingElement == null)
				throw error(manifest, element, "a sequence needs a Routing element");
		}
		else if (!isEmpty) {
			sound.tracks.add(new Track(0, readOneShot(reader, manifest, oneShotElement, addresses)));
			if (routingElement == null && !SfxBinary.isCompactRepresentable((OneShot) sound.tracks.get(0).definition))
				throw error(manifest, element, "a OneShot without Routing is stored in a compact slot, which needs the "
					+ "default pan, reverb, and pitch, a volume of 03, 07, 0B and so on up to 7F, and a randomPitch that "
					+ "is a multiple of 8 up to 56");
		}

		if (routingElement != null)
			sound.routing = readRouting(reader, manifest, routingElement, sound.tracks.size());
		return sound;
	}

	private static void readEffect(Sound sound, Path effectXml, Presets.Addresses addresses)
	{
		XmlReader reader = parseDocument(effectXml);
		Element root = requireRoot(reader, effectXml, TAG_EFFECT);
		checkAttributes(effectXml, root);

		Element tracksElement = null;
		Element spawnedElement = null;
		for (Element child : childElements(effectXml, root)) {
			switch (tagKey(effectXml, root, child)) {
				case TAG_TRACKS:
					if (tracksElement != null)
						throw error(effectXml, child, "SoundEffect cannot contain more than one Tracks element");
					tracksElement = child;
					break;
				case TAG_SPAWNED_EFFECTS:
					if (spawnedElement != null)
						throw error(effectXml, child, "SoundEffect cannot contain more than one SpawnedEffects element");
					spawnedElement = child;
					break;
				case TAG_ROUTING:
					throw error(effectXml, child, "logical sound routing belongs in sound_effects.xml, not the effect file");
				default:
					throw unknownElement(effectXml, root, child);
			}
		}
		if (tracksElement == null)
			throw error(effectXml, root, "SoundEffect is missing required Tracks element");
		sound.tracks.addAll(readTracks(reader, effectXml, tracksElement, addresses));

		if (spawnedElement != null) {
			checkAttributes(effectXml, spawnedElement);
			for (Element child : childElements(effectXml, spawnedElement)) {
				if (!child.getTagName().equals(TAG_SPAWNED_EFFECT.toString()))
					throw unknownElement(effectXml, spawnedElement, child);
				sound.spawnedEffects.add(readSpawnedEffect(reader, effectXml, child, addresses));
			}
		}
	}

	private static SpawnedEffect readSpawnedEffect(XmlReader reader, Path source, Element element,
		Presets.Addresses addresses)
	{
		checkAttributes(source, element, ATTR_NAME);
		SpawnedEffect spawned = new SpawnedEffect(readIdentifier(reader, source, element, ATTR_NAME, true));
		Element routingElement = null;
		Element tracksElement = null;
		for (Element child : childElements(source, element)) {
			switch (tagKey(source, element, child)) {
				case TAG_ROUTING:
					if (routingElement != null)
						throw error(source, child, "SpawnedEffect cannot contain more than one Routing element");
					routingElement = child;
					break;
				case TAG_TRACKS:
					if (tracksElement != null)
						throw error(source, child, "SpawnedEffect cannot contain more than one Tracks element");
					tracksElement = child;
					break;
				default:
					throw unknownElement(source, element, child);
			}
		}
		if (tracksElement == null || routingElement == null)
			throw error(source, element, "SpawnedEffect requires exactly one Routing and one Tracks element");
		spawned.tracks.addAll(readTracks(reader, source, tracksElement, addresses));
		spawned.routing = readRouting(reader, source, routingElement, spawned.tracks.size());
				return spawned;
	}

	private static List<Track> readTracks(XmlReader reader, Path source, Element element,
		Presets.Addresses addresses)
	{
		checkAttributes(source, element);
		List<Track> tracks = new ArrayList<>();
		for (Element child : childElements(source, element)) {
			if (!child.getTagName().equals(TAG_TRACK.toString()))
				throw unknownElement(source, element, child);
			tracks.add(readTrack(reader, source, child, addresses));
		}
		return tracks;
	}

	private static Track readTrack(XmlReader reader, Path source, Element element,
		Presets.Addresses addresses)
	{
		checkAttributes(source, element, ATTR_SLOT, ATTR_PLAYER, ATTR_PRIORITY, ATTR_EMPTY);
		int slot = readRangedInt(reader, source, element, ATTR_SLOT, 0, 7, false);
		Integer player = reader.hasAttribute(element, ATTR_PLAYER)
			? readRangedInt(reader, source, element, ATTR_PLAYER, 0, 7, false)
			: null;
		Integer priority = reader.hasAttribute(element, ATTR_PRIORITY)
			? readRangedInt(reader, source, element, ATTR_PRIORITY, 0, 3, false)
			: null;

		List<Element> children = childElements(source, element);
		boolean empty = reader.hasAttribute(element, ATTR_EMPTY);
		if (empty && !flag(reader, element, ATTR_EMPTY))
			throw error(source, element, "empty, when present, must be true");

		Definition definition;
		if (empty) {
			if (!children.isEmpty())
				throw error(source, element, "an empty track cannot contain a definition");
			definition = Empty.INSTANCE;
		}
		else {
			if (children.size() != 1)
				throw error(source, element, "Track must contain exactly one OneShot or Sequence definition");
			Element child = children.get(0);
			switch (tagKey(source, element, child)) {
				case TAG_ONE_SHOT:
					definition = readOneShot(reader, source, child, addresses);
					break;
				case TAG_SEQUENCE:
					definition = readSequence(reader, source, child, addresses);
					break;
				default:
					throw unknownElement(source, element, child);
			}
		}

		Track track = new Track(slot, definition);
		track.player = player;
		track.priority = priority;
		return track;
	}

	private static OneShot readOneShot(XmlReader reader, Path source, Element element,
		Presets.Addresses addresses)
	{
		checkAttributes(source, element,
			ATTR_WAV, ATTR_ENVELOPE, ATTR_VOLUME, ATTR_PAN, ATTR_REVERB, ATTR_PITCH, ATTR_RANDOM_PITCH,
			ATTR_LOCK_VOLUME, ATTR_LOCK_PAN, ATTR_LOCK_PITCH, ATTR_LOCK_REVERB);
		requireNoChildren(source, element);
		OneShot oneShot = new OneShot();
		int[] address = readWavAddress(reader, source, element, addresses);
		oneShot.bank = address[0];
		oneShot.patch = address[1];
		oneShot.volume = AudioXml.readHex(reader, element, ATTR_VOLUME, 0, 255);
		oneShot.pan = readRangedInt(reader, source, element, ATTR_PAN, 0, 255, true, 64);
		oneShot.reverb = readRangedInt(reader, source, element, ATTR_REVERB, 0, 255, true, 0);
		oneShot.pitch = readRangedInt(reader, source, element, ATTR_PITCH, 0, 127, true, 48);
		oneShot.randomPitch = readRangedInt(reader, source, element, ATTR_RANDOM_PITCH, 0, 120, true, 0);
		if ((oneShot.randomPitch & 7) != 0)
			throw error(source, element, "randomPitch must be a multiple of 8");
		oneShot.lockVolume = flag(reader, element, ATTR_LOCK_VOLUME);
		oneShot.lockPan = flag(reader, element, ATTR_LOCK_PAN);
		oneShot.lockPitch = flag(reader, element, ATTR_LOCK_PITCH);
		oneShot.lockReverb = flag(reader, element, ATTR_LOCK_REVERB);
		return oneShot;
	}

	private static Sequence readSequence(XmlReader reader, Path source, Element element,
		Presets.Addresses addresses)
	{
		checkAttributes(source, element,
			ATTR_LOCK_VOLUME, ATTR_LOCK_PAN, ATTR_LOCK_PITCH, ATTR_LOCK_REVERB);
		Sequence sequence = new Sequence();
		sequence.lockVolume = flag(reader, element, ATTR_LOCK_VOLUME);
		sequence.lockPan = flag(reader, element, ATTR_LOCK_PAN);
		sequence.lockPitch = flag(reader, element, ATTR_LOCK_PITCH);
		sequence.lockReverb = flag(reader, element, ATTR_LOCK_REVERB);

		Set<String> labels = new LinkedHashSet<>();
		labels.add(Sequence.START_LABEL);
		sequence.nodes.add(new Label(Sequence.START_LABEL));
		for (Element child : childElements(source, element)) {
			if (child.getTagName().equals(TAG_LABEL.toString())) {
				checkAttributes(source, child, ATTR_NAME);
				requireNoChildren(source, child);
				String name = readIdentifier(reader, source, child, ATTR_NAME, true);
				if (!labels.add(name))
					throw error(source, child, "duplicate label: " + name);
				sequence.nodes.add(new Label(name));
			}
			else {
				sequence.nodes.add(readCommand(reader, source, child, addresses));
			}
		}

		for (SfxArchive.Node node : sequence.nodes) {
			if (node instanceof Command command && (command.op == Op.JUMP || command.op == Op.SET_ALTERNATIVE)) {
				String label = labelReferenceName(source, element, command.ref);
				if (!labels.contains(label))
					throw error(source, element, "missing label referenced by " + command.op + ": " + label);
			}
		}
		return sequence;
	}

	private static Command readCommand(XmlReader reader, Path source, Element element,
		Presets.Addresses addresses)
	{
		SfxXmlKey tag = SfxXmlKey.forTag(element.getTagName());
		if (tag == null)
			throw error(source, element, "unknown sequence command element: " + element.getTagName());
		switch (tag) {
			case TAG_END:
				return noArgCommand(reader, source, element, Op.END);
			case TAG_DELAY:
				return new Command(Op.DELAY,
					decimalAttribute(reader, source, element, ATTR_TICKS, 1, 2167));
			case TAG_PLAY:
				checkAttributes(source, element, ATTR_PITCH, ATTR_VELOCITY, ATTR_LENGTH);
				requireNoChildren(source, element);
				return new Command(Op.PLAY,
					readRangedInt(reader, source, element, ATTR_PITCH, 0, 87, false),
					readRangedInt(reader, source, element, ATTR_VELOCITY, 0, 127, false),
					readRangedInt(reader, source, element, ATTR_LENGTH, 0, 16575, false));
			case TAG_SET_VOLUME:
				return new Command(Op.SET_VOLUME,
					hexAttribute(reader, source, element, ATTR_VOLUME, 0, 255));
			case TAG_SET_PAN:
				return new Command(Op.SET_PAN,
					decimalAttribute(reader, source, element, ATTR_PAN, 0, 255));
			case TAG_SET_INSTRUMENT:
				checkAttributes(source, element, ATTR_WAV, ATTR_ENVELOPE);
				requireNoChildren(source, element);
				int[] instrument = readWavAddress(reader, source, element, addresses);
				return new Command(Op.SET_INSTRUMENT, instrument[0], instrument[1]);
			case TAG_SET_REVERB:
				return new Command(Op.SET_REVERB,
					decimalAttribute(reader, source, element, ATTR_REVERB, 0, 255));
			case TAG_SET_ENVELOPE:
				return new Command(Op.SET_ENVELOPE,
					decimalAttribute(reader, source, element, ATTR_PRESET, 0, 255));
			case TAG_COARSE_TUNE:
				return new Command(Op.COARSE_TUNE,
					decimalAttribute(reader, source, element, ATTR_SEMITONES, -128, 127));
			case TAG_FINE_TUNE:
				return new Command(Op.FINE_TUNE,
					decimalAttribute(reader, source, element, ATTR_CENTS, 0, 255));
			case TAG_WAIT_FOR_END:
				return noArgCommand(reader, source, element, Op.WAIT_FOR_END);
			case TAG_PITCH_SWEEP:
				checkAttributes(source, element, ATTR_TICKS, ATTR_PITCH);
				requireNoChildren(source, element);
				return new Command(Op.PITCH_SWEEP,
					readRangedInt(reader, source, element, ATTR_TICKS, 0, 65535, false),
					readRangedInt(reader, source, element, ATTR_PITCH, 0, 127, false));
			case TAG_START_LOOP:
				return new Command(Op.START_LOOP,
					decimalAttribute(reader, source, element, ATTR_COUNT, 0, 255));
			case TAG_END_LOOP:
				return noArgCommand(reader, source, element, Op.END_LOOP);
			case TAG_WAIT_FOR_RELEASE:
				return noArgCommand(reader, source, element, Op.WAIT_FOR_RELEASE);
			case TAG_SET_CURRENT_VOLUME:
				return new Command(Op.SET_CURRENT_VOLUME,
					hexAttribute(reader, source, element, ATTR_VOLUME, 0, 255));
			case TAG_VOLUME_RAMP:
				checkAttributes(source, element, ATTR_TICKS, ATTR_TARGET);
				requireNoChildren(source, element);
				return new Command(Op.VOLUME_RAMP,
					readRangedInt(reader, source, element, ATTR_TICKS, 0, 65535, false),
					AudioXml.readHex(reader, element, ATTR_TARGET, 0, 255));
			case TAG_SET_ALTERNATIVE:
				checkAttributes(source, element, ATTR_TYPE, ATTR_TARGET);
				requireNoChildren(source, element);
				Command command = Command.reference(Op.SET_ALTERNATIVE,
					readIdentifier(reader, source, element, ATTR_TARGET, true));
				command.a = readRangedInt(reader, source, element, ATTR_TYPE, 1, 3, false);
				return command;
			case TAG_STOP:
				return noArgCommand(reader, source, element, Op.STOP);
			case TAG_JUMP:
				checkAttributes(source, element, ATTR_TARGET);
				requireNoChildren(source, element);
				return Command.reference(Op.JUMP,
					readIdentifier(reader, source, element, ATTR_TARGET, true));
			case TAG_RESTART:
				return noArgCommand(reader, source, element, Op.RESTART);
			case TAG_NOP:
				return noArgCommand(reader, source, element, Op.NOP);
			case TAG_SET_RANDOM_PITCH:
				return new Command(Op.SET_RANDOM_PITCH,
					decimalAttribute(reader, source, element, ATTR_AMOUNT, 0, 255));
			case TAG_SET_RANDOM_VELOCITY:
				return new Command(Op.SET_RANDOM_VELOCITY,
					decimalAttribute(reader, source, element, ATTR_AMOUNT, 0, 255));
			case TAG_SET_RANDOM_PAN:
				return new Command(Op.SET_RANDOM_PAN,
					decimalAttribute(reader, source, element, ATTR_AMOUNT, 0, 255));
			case TAG_SET_PRESS_ENVELOPE:
				checkAttributes(source, element, ATTR_REF);
				requireNoChildren(source, element);
				Command envelopeCommand = new Command(Op.SET_PRESS_ENVELOPE);
				if (reader.hasAttribute(element, ATTR_REF))
					envelopeCommand.ref = readIdentifier(reader, source, element, ATTR_REF, true);
				return envelopeCommand;
			case TAG_SPAWN:
				checkAttributes(source, element, ATTR_REF);
				requireNoChildren(source, element);
				reader.requiresAttribute(element, ATTR_REF);
				String ref = reader.getAttribute(element, ATTR_REF);
				if (ref.startsWith("shared:"))
					throw error(source, element, "shared spawned effects are not supported by the prototype");
				if (!IDENTIFIER.matcher(ref).matches())
					throw error(source, element, "Spawn ref is not a valid local spawned-effect name: " + ref);
				return Command.reference(Op.SPAWN, ref);
			case TAG_SET_ALTERNATIVE_VOLUME:
				return new Command(Op.SET_ALTERNATIVE_VOLUME,
					hexAttribute(reader, source, element, ATTR_VOLUME, 0, 255));
			default:
				throw error(source, element, "unknown sequence command element: " + element.getTagName());
		}
	}

	private static Command noArgCommand(XmlReader reader, Path source, Element element, Op op)
	{
		checkAttributes(source, element);
		requireNoChildren(source, element);
		return new Command(op);
	}

	private static int decimalAttribute(XmlReader reader, Path source, Element element,
		SfxXmlKey name, int min, int max)
	{
		checkAttributes(source, element, name);
		requireNoChildren(source, element);
		return readRangedInt(reader, source, element, name, min, max, false);
	}

	private static int hexAttribute(XmlReader reader, Path source, Element element,
		SfxXmlKey name, int min, int max)
	{
		checkAttributes(source, element, name);
		requireNoChildren(source, element);
		return AudioXml.readHex(reader, element, name, min, max);
	}

	private static Routing readRouting(XmlReader reader, Path source, Element element, int trackCount)
	{
		checkAttributes(source, element,
			ATTR_ALLOCATION, ATTR_MAX_PLAYER, ATTR_PLAYER, ATTR_PRIORITY, ATTR_EXCLUSIVE_GROUP);
		requireNoChildren(source, element);
		reader.requiresAttribute(element, ATTR_ALLOCATION);
		String allocationText = reader.getAttribute(element, ATTR_ALLOCATION);
		Allocation allocation;
		try {
			allocation = Allocation.fromXml(allocationText);
		}
		catch (IllegalArgumentException e) {
			throw error(source, element, e.getMessage());
		}

		Routing routing = new Routing(allocation);
		routing.exclusiveGroup = readRangedInt(reader, source, element, ATTR_EXCLUSIVE_GROUP, 0, 3, true, 0);
		if (allocation == Allocation.DYNAMIC) {
			if (reader.hasAttribute(element, ATTR_PLAYER))
				throw error(source, element, "dynamic routing cannot specify player");
			routing.maxPlayer = readRangedInt(reader, source, element, ATTR_MAX_PLAYER, 0, 7, false);
			routing.priority = readRangedInt(reader, source, element, ATTR_PRIORITY, 0, 3, false);
		}
		else {
			if (reader.hasAttribute(element, ATTR_MAX_PLAYER))
				throw error(source, element, "fixed routing cannot specify maxPlayer");
			if (trackCount == 1) {
				routing.player = readRangedInt(reader, source, element, ATTR_PLAYER, 0, 7, false);
				routing.priority = readRangedInt(reader, source, element, ATTR_PRIORITY, 0, 3, false);
			}
			else if (reader.hasAttribute(element, ATTR_PLAYER) || reader.hasAttribute(element, ATTR_PRIORITY)) {
				throw error(source, element,
					"fixed polyphonic routing carries player and priority on nonempty Track elements");
			}
		}
		return routing;
	}

	private static void readEnvelopes(AudioLayers layers, SfxArchive archive)
	{
		if (layers.getTables(ENVELOPES).isEmpty())
			return;

		for (Entry e : AudioTables.merge(layers, ENVELOPES, AudioTables.Key.Envelopes, AudioTables.Key.Envelope).values()) {
			Path source = e.xmr.getSourceFile().toPath();
			checkAttributes(source, e.elem, ATTR_NAME, ATTR_DELETE);
			Envelope envelope = new Envelope(readIdentifier(e.xmr, source, e.elem, ATTR_NAME, true));
			envelope.file = source.toFile();

			envelope.commands.addAll(EnvelopeProgram.readCommands(e.xmr, e.elem));
			archive.envelopes.add(envelope);
		}
	}

	private static List<Element> childElements(Path source, Element parent)
	{
		List<Element> children = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element element)
				children.add(element);
			else if ((child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
				&& !child.getTextContent().isBlank()) {
				throw error(source, parent, "text content is not allowed here");
			}
		}
		return children;
	}

	private static void requireNoChildren(Path source, Element element)
	{
		if (!childElements(source, element).isEmpty())
			throw error(source, element, element.getTagName() + " must be empty");
	}

	private static void checkAttributes(Path source, Element element, XmlKey ... allowed)
	{
		Set<String> allowedSet = new HashSet<>();
		for (XmlKey key : allowed)
			allowedSet.add(key.toString());
		NamedNodeMap attributes = element.getAttributes();
		for (int i = 0; i < attributes.getLength(); i++) {
			String name = attributes.item(i).getNodeName();
			if (!allowedSet.contains(name))
				throw error(source, element, "unknown attribute: " + name);
		}
	}

	private static String readIdentifier(XmlReader reader, Path source, Element element,
		SfxXmlKey name, boolean required)
	{
		String value = null;
		if (required) {
			reader.requiresAttribute(element, name);
			value = reader.getAttribute(element, name);
		}
		else if (reader.hasAttribute(element, name)) {
			value = reader.getAttribute(element, name);
		}
		if (value != null && !IDENTIFIER.matcher(value).matches())
			throw error(source, element, name + " is not a valid identifier: " + value);
		return value;
	}

	private static String labelReferenceName(Path source, Element element, String reference)
	{
		if (reference == null || !IDENTIFIER.matcher(reference).matches())
			throw locationError(source, element, "invalid label reference: " + reference);
		return reference;
	}

	private static SfxFormatException unknownElement(Path source, Element parent, Element child)
	{
		return error(source, child,
			"unknown element <" + child.getTagName() + "> inside <" + parent.getTagName() + ">");
	}

	private static SfxXmlKey tagKey(Path source, Element parent, Element child)
	{
		SfxXmlKey key = SfxXmlKey.forTag(child.getTagName());
		if (key == null)
			throw unknownElement(source, parent, child);
		return key;
	}

	private static SfxFormatException error(Path source, Element element, String message)
	{
		return locationError(source, element, message);
	}

	private static Map<SfxXmlKey, String> attributes(Object ... pairs)
	{
		if ((pairs.length & 1) != 0)
			throw new IllegalArgumentException("Attribute arguments must be name/value pairs");
		Map<SfxXmlKey, String> attributes = new LinkedHashMap<>();
		for (int index = 0; index < pairs.length; index += 2) {
			if (!(pairs[index] instanceof SfxXmlKey key) || !(pairs[index + 1] instanceof String value))
				throw new IllegalArgumentException("XML attributes must be SfxXmlKey/String pairs");
			attributes.put(key, value);
		}
		return attributes;
	}

	private static void putNonDefault(Map<SfxXmlKey, String> attributes, SfxXmlKey name,
		int value, int defaultValue)
	{
		if (value != defaultValue)
			attributes.put(name, Integer.toString(value));
	}

	private static void putTrue(Map<SfxXmlKey, String> attributes, SfxXmlKey name, boolean value)
	{
		if (value)
			attributes.put(name, "true");
	}

	private static XmlTag createTag(XmlWriter writer, SfxXmlKey name, boolean selfClosing,
		Map<SfxXmlKey, String> attributes)
	{
		XmlTag tag = writer.createTag(name, selfClosing);
		for (Map.Entry<SfxXmlKey, String> attribute : attributes.entrySet())
			writer.addAttribute(tag, attribute.getKey(), attribute.getValue());
		return tag;
	}

	private static XmlTag openTag(XmlWriter writer, SfxXmlKey name, Map<SfxXmlKey, String> attributes)
	{
		XmlTag tag = createTag(writer, name, false, attributes);
		writer.openTag(tag);
		return tag;
	}

	private static void printTag(XmlWriter writer, SfxXmlKey name, Map<SfxXmlKey, String> attributes)
	{
		writer.printTag(createTag(writer, name, true, attributes));
	}

	private static XmlReader parseDocument(Path source)
	{
		return new XmlReader(source.toFile());
	}

	private static Element requireRoot(XmlReader reader, Path source, SfxXmlKey expected)
	{
		Element root = reader.getRootElement();
		if (!root.getTagName().equals(expected.toString()))
			throw new SfxFormatException(source.normalize() + ": expected root element <" + expected + ">");
		return root;
	}

	private static boolean flag(XmlReader reader, Element element, SfxXmlKey key)
	{
		return reader.hasAttribute(element, key) && reader.readBoolean(element, key);
	}

	private static int[] readWavAddress(XmlReader reader, Path source, Element element, Presets.Addresses addresses)
	{
		reader.requiresAttribute(element, ATTR_WAV);
		return addresses.address(source.toFile(), element.getTagName(), reader.getAttribute(element, ATTR_WAV),
			readRangedInt(reader, source, element, ATTR_ENVELOPE, 0, 3, true, 0));
	}

	private static int readRangedInt(XmlReader reader, Path source, Element element,
		SfxXmlKey name, int min, int max, boolean optional)
	{
		return readRangedInt(reader, source, element, name, min, max, optional, min);
	}

	private static int readRangedInt(XmlReader reader, Path source, Element element,
		SfxXmlKey name, int min, int max, boolean optional, int defaultValue)
	{
		int parsed;
		if (optional && !reader.hasAttribute(element, name)) {
			parsed = defaultValue;
		}
		else {
			reader.requiresAttribute(element, name);
			parsed = reader.readInt(element, name);
		}
		if (parsed < min || parsed > max)
			throw error(source, element, name + " must be " + min + " through " + max);
		return parsed;
	}

	private static SfxFormatException locationError(Path source, Element element, String message)
	{
		String label = element == null ? "" : element.getTagName()
			+ (element.hasAttribute("name") ? " '" + element.getAttribute("name") + "'" : "") + ": ";
		return new SfxFormatException(source.normalize() + ": " + label + message);
	}

	/** Writes the sources of a vanilla archive: sound_effects.xml, sfx/envelopes.xml, and a file per sequence. */
	public static void write(SfxArchive archive, File audioDir, Presets.Addresses addresses) throws IOException
	{
		File table = new File(audioDir, TABLE);
		Dump dump = new Dump(addresses, table);
		Set<String> sources = new HashSet<>(List.of(ENVELOPES));
		for (Sound sound : archive.sounds.values()) {
			if (!sound.isEmpty() && !sound.canInlineOneShot()) {
				sound.source = "sfx/" + AudioNames.toFileName(sound.name) + ".xml";
				if (!sources.add(sound.source))
					throw new SfxFormatException(table + ": sound " + sound.name + " has the same file name as another sound: " + sound.source);
				FileUtils.forceMkdirParent(new File(audioDir, sound.source));
				dump.writeEffectXml(new File(audioDir, sound.source), sound);
			}
		}
		if (!archive.envelopes.isEmpty()) {
			FileUtils.forceMkdirParent(new File(audioDir, ENVELOPES));
			dump.writeEnvelopesXml(new File(audioDir, ENVELOPES), archive);
		}
		dump.writeTable(archive);
	}

	private static final class Dump
	{
		private final Presets.Addresses addresses;
		private final File source;
		private Sound sound;

		private Dump(Presets.Addresses addresses, File source)
		{
			this.addresses = addresses;
			this.source = source;
		}

		private void writeTable(SfxArchive archive) throws IOException
		{
			try (XmlWriter writer = new XmlWriter(source)) {
				XmlTag rootTag = openTag(writer, TAG_SOUNDS, Map.of());
				for (Sound sound : archive.sounds.values()) {
					this.sound = sound;
					Map<SfxXmlKey, String> attributes = attributes(ATTR_ID, String.format("%04X", sound.id),
						ATTR_NAME, sound.name);
					if (sound.isEmpty()) {
						attributes.put(ATTR_EMPTY, "true");
						printTag(writer, TAG_SOUND, attributes);
						continue;
					}

					if (sound.source != null)
						attributes.put(ATTR_SRC, sound.source);
					XmlTag soundTag = openTag(writer, TAG_SOUND, attributes);
					if (sound.routing != null)
						writeRouting(writer, sound.routing, sound.tracks.size());
					if (sound.source == null)
						writeOneShot(writer, (OneShot) sound.tracks.get(0).definition);
					writer.closeTag(soundTag);
				}
				writer.closeTag(rootTag);
				writer.save();
			}
		}

		private void writeEffectXml(File output, Sound sound) throws IOException
		{
			this.sound = sound;
			try (XmlWriter writer = new XmlWriter(output)) {
				XmlTag rootTag = openTag(writer, TAG_EFFECT, Map.of());
				writeTracks(writer, sound.tracks);

				if (!sound.spawnedEffects.isEmpty()) {
					XmlTag spawnedEffectsTag = openTag(writer, TAG_SPAWNED_EFFECTS, Map.of());
					for (int index = 0; index < sound.spawnedEffects.size(); index++) {
						SpawnedEffect spawned = sound.spawnedEffects.get(index);
						XmlTag spawnedTag = openTag(writer, TAG_SPAWNED_EFFECT,
							attributes(ATTR_NAME, spawned.name));
						writeRouting(writer, spawned.routing, spawned.tracks.size());
						writeTracks(writer, spawned.tracks);
						writer.closeTag(spawnedTag);
					}
					writer.closeTag(spawnedEffectsTag);
				}

				writer.closeTag(rootTag);
				writer.save();
			}
		}

		private void writeEnvelopesXml(File output, SfxArchive archive) throws IOException
		{
			try (XmlWriter writer = new XmlWriter(output)) {
				XmlTag rootTag = openTag(writer, TAG_ENVELOPES, Map.of());
				for (Envelope envelope : archive.envelopes) {
					XmlTag envelopeTag = openTag(writer, TAG_ENVELOPE, attributes(ATTR_NAME, envelope.name));
					EnvelopeProgram.writeCommands(writer, envelope.commands);
					writer.closeTag(envelopeTag);
				}

				writer.closeTag(rootTag);
				writer.save();
			}
		}

		private void writeRouting(XmlWriter writer, Routing routing, int trackCount)
		{
			Map<SfxXmlKey, String> attributes = attributes(ATTR_ALLOCATION, routing.allocation.xmlName());
			if (routing.allocation == Allocation.DYNAMIC) {
				attributes.put(ATTR_MAX_PLAYER, Integer.toString(routing.maxPlayer));
				attributes.put(ATTR_PRIORITY, Integer.toString(routing.priority));
			}
			else if (trackCount == 1) {
				attributes.put(ATTR_PLAYER, Integer.toString(routing.player));
				attributes.put(ATTR_PRIORITY, Integer.toString(routing.priority));
			}
			if (routing.exclusiveGroup != 0)
				attributes.put(ATTR_EXCLUSIVE_GROUP, Integer.toString(routing.exclusiveGroup));
			printTag(writer, TAG_ROUTING, attributes);
		}

		private void writeTracks(XmlWriter writer, List<Track> tracks)
		{
			XmlTag tracksTag = openTag(writer, TAG_TRACKS, Map.of());
			List<Track> ordered = new ArrayList<>(tracks);
			ordered.sort(Comparator.comparingInt(track -> track.slot));
			for (int index = 0; index < ordered.size(); index++) {
				Track track = ordered.get(index);
				Map<SfxXmlKey, String> trackAttributes = attributes(ATTR_SLOT, Integer.toString(track.slot));
				if (track.player != null)
					trackAttributes.put(ATTR_PLAYER, Integer.toString(track.player));
				if (track.priority != null)
					trackAttributes.put(ATTR_PRIORITY, Integer.toString(track.priority));

				if (track.definition == Empty.INSTANCE) {
					trackAttributes.put(ATTR_EMPTY, "true");
					printTag(writer, TAG_TRACK, trackAttributes);
				}
				else {
					XmlTag trackTag = openTag(writer, TAG_TRACK, trackAttributes);
					if (track.definition instanceof OneShot oneShot)
						writeOneShot(writer, oneShot);
					else
						writeSequence(writer, (Sequence) track.definition);
					writer.closeTag(trackTag);
				}
			}
			writer.closeTag(tracksTag);
		}

		private void writeOneShot(XmlWriter writer, OneShot oneShot)
		{
			Map<SfxXmlKey, String> attributes = wavAttributes(oneShot.bank, oneShot.patch);
			attributes.put(ATTR_VOLUME, String.format("%02X", oneShot.volume));
			putNonDefault(attributes, ATTR_PAN, oneShot.pan, 64);
			putNonDefault(attributes, ATTR_REVERB, oneShot.reverb, 0);
			putNonDefault(attributes, ATTR_PITCH, oneShot.pitch, 48);
			putNonDefault(attributes, ATTR_RANDOM_PITCH, oneShot.randomPitch, 0);
			putTrue(attributes, ATTR_LOCK_VOLUME, oneShot.lockVolume);
			putTrue(attributes, ATTR_LOCK_PAN, oneShot.lockPan);
			putTrue(attributes, ATTR_LOCK_PITCH, oneShot.lockPitch);
			putTrue(attributes, ATTR_LOCK_REVERB, oneShot.lockReverb);
			printTag(writer, TAG_ONE_SHOT, attributes);
		}

		private void writeSequence(XmlWriter writer, Sequence sequence)
		{
			Map<SfxXmlKey, String> attributes = attributes();
			putTrue(attributes, ATTR_LOCK_VOLUME, sequence.lockVolume);
			putTrue(attributes, ATTR_LOCK_PAN, sequence.lockPan);
			putTrue(attributes, ATTR_LOCK_PITCH, sequence.lockPitch);
			putTrue(attributes, ATTR_LOCK_REVERB, sequence.lockReverb);
			XmlTag sequenceTag = openTag(writer, TAG_SEQUENCE, attributes);
			for (SfxArchive.Node node : sequence.nodes.subList(1, sequence.nodes.size())) {
				if (node instanceof Label label)
					printTag(writer, TAG_LABEL, attributes(ATTR_NAME, label.name()));
				else
					writeCommand(writer, (Command) node);
			}
			writer.closeTag(sequenceTag);
		}

		private void writeCommand(XmlWriter writer, Command command)
		{
			switch (command.op) {
				case END:
					printTag(writer, TAG_END, Map.of());
					break;
				case DELAY:
					printTag(writer, TAG_DELAY, attributes(ATTR_TICKS, Integer.toString(command.a)));
					break;
				case PLAY:
					printTag(writer, TAG_PLAY, attributes(
						ATTR_PITCH, Integer.toString(command.a), ATTR_VELOCITY, Integer.toString(command.b),
						ATTR_LENGTH, Integer.toString(command.c)));
					break;
				case SET_VOLUME:
					printTag(writer, TAG_SET_VOLUME, attributes(ATTR_VOLUME, String.format("%02X", command.a)));
					break;
				case SET_PAN:
					printTag(writer, TAG_SET_PAN, attributes(ATTR_PAN, Integer.toString(command.a)));
					break;
				case SET_INSTRUMENT:
					printTag(writer, TAG_SET_INSTRUMENT, wavAttributes(command.a, command.b));
					break;
				case SET_REVERB:
					printTag(writer, TAG_SET_REVERB, attributes(ATTR_REVERB, Integer.toString(command.a)));
					break;
				case SET_ENVELOPE:
					printTag(writer, TAG_SET_ENVELOPE, attributes(ATTR_PRESET, Integer.toString(command.a)));
					break;
				case COARSE_TUNE:
					printTag(writer, TAG_COARSE_TUNE,
						attributes(ATTR_SEMITONES, Integer.toString(command.a)));
					break;
				case FINE_TUNE:
					printTag(writer, TAG_FINE_TUNE, attributes(ATTR_CENTS, Integer.toString(command.a)));
					break;
				case WAIT_FOR_END:
					printTag(writer, TAG_WAIT_FOR_END, Map.of());
					break;
				case PITCH_SWEEP:
					printTag(writer, TAG_PITCH_SWEEP, attributes(
						ATTR_TICKS, Integer.toString(command.a), ATTR_PITCH, Integer.toString(command.b)));
					break;
				case START_LOOP:
					printTag(writer, TAG_START_LOOP, attributes(ATTR_COUNT, Integer.toString(command.a)));
					break;
				case END_LOOP:
					printTag(writer, TAG_END_LOOP, Map.of());
					break;
				case WAIT_FOR_RELEASE:
					printTag(writer, TAG_WAIT_FOR_RELEASE, Map.of());
					break;
				case SET_CURRENT_VOLUME:
					printTag(writer, TAG_SET_CURRENT_VOLUME,
						attributes(ATTR_VOLUME, String.format("%02X", command.a)));
					break;
				case VOLUME_RAMP:
					printTag(writer, TAG_VOLUME_RAMP, attributes(
						ATTR_TICKS, Integer.toString(command.a), ATTR_TARGET, String.format("%02X", command.b)));
					break;
				case SET_ALTERNATIVE:
					printTag(writer, TAG_SET_ALTERNATIVE, attributes(
						ATTR_TYPE, Integer.toString(command.a), ATTR_TARGET, command.ref));
					break;
				case STOP:
					printTag(writer, TAG_STOP, Map.of());
					break;
				case JUMP:
					printTag(writer, TAG_JUMP, attributes(ATTR_TARGET, command.ref));
					break;
				case RESTART:
					printTag(writer, TAG_RESTART, Map.of());
					break;
				case NOP:
					printTag(writer, TAG_NOP, Map.of());
					break;
				case SET_RANDOM_PITCH:
					printTag(writer, TAG_SET_RANDOM_PITCH,
						attributes(ATTR_AMOUNT, Integer.toString(command.a)));
					break;
				case SET_RANDOM_VELOCITY:
					printTag(writer, TAG_SET_RANDOM_VELOCITY,
						attributes(ATTR_AMOUNT, Integer.toString(command.a)));
					break;
				case SET_RANDOM_PAN:
					printTag(writer, TAG_SET_RANDOM_PAN,
						attributes(ATTR_AMOUNT, Integer.toString(command.a)));
					break;
				case SET_PRESS_ENVELOPE:
					printTag(writer, TAG_SET_PRESS_ENVELOPE,
						command.ref == null ? Map.of() : attributes(ATTR_REF, command.ref));
					break;
				case SPAWN:
					printTag(writer, TAG_SPAWN, attributes(ATTR_REF, command.ref));
					break;
				case SET_ALTERNATIVE_VOLUME:
					printTag(writer, TAG_SET_ALTERNATIVE_VOLUME,
						attributes(ATTR_VOLUME, String.format("%02X", command.a)));
					break;
			}
		}

		private Map<SfxXmlKey, String> wavAttributes(int bank, int patch)
		{
			Map<SfxXmlKey, String> attributes = attributes(ATTR_WAV, addresses.name(source, "sound " + sound.name, bank, patch));
			if ((bank & 3) != 0)
				attributes.put(ATTR_ENVELOPE, Integer.toString(bank & 3));
			return attributes;
		}

	}
}
