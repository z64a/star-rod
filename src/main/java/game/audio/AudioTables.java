package game.audio;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import app.input.InputFileException;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;

/**
 * The audio tables (songs, banks, ambient sounds, sound effects, presets, drums, and envelopes). Each is an XML file that can exist in several layers of
 * the asset stack. Entries are keyed by name; the highest layer's entry replaces the whole entry of
 * the same name below it, and delete="true" removes it.
 */
public abstract class AudioTables
{
	public enum Key implements XmlKey
	{
		// @formatter:off
		Sounds, Sound, Envelopes, Envelope, SongList, Song, BankList, Bank, AmbientSounds, AmbientSound, Radio, Station,
		id, name, delete, bgm, bk1, bk2, bk3, group, index, mseq, bank, player,
		Instruments, Instrument, Drums, Drum, wav, envelope, volume, pan, reverb, coarseTune, fineTune,
		keyBase, randTune, randVolume, randPan, randReverb
		// @formatter:on
	}

	public static final int MAX_SONG_ID = 0xFF;
	public static final int MAX_BANKS = 79;
	public static final int MIN_BANK_GROUP = 2;
	public static final int MAX_BANK_GROUP = 6;
	/** Ambient IDs 0 to 15 are the vanilla sounds and 16 is the radio; IDs above it are added to the INIT resource list. */
	public static final int RADIO_ID = 0x10;
	public static final int MAX_AMBIENT_ID = 0xFF;
	public static final int RADIO_STATIONS = 4;
	public static final int MAX_PRESET_ID = 0x3F;
	public static final int MAX_DRUM_ID = 71;
	public static final int DEFAULT_VOLUME = 0x7F;
	public static final int DEFAULT_PAN = 64;
	public static final int DEFAULT_KEY_BASE = 4800;

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

	/** bk holds three auxiliary bank file names, null where the song uses none. */
	public record Song(String name, int id, String bgm, String[] bk, File source)
	{}

	public record Bank(String file, int group, int index, File source)
	{}

	public record AmbientSound(String name, int id, String mseq, File source)
	{}

	public record Radio(String name, String bank, String[] mseq, File source)
	{}

	/** An instrument that songs select by ID: a bank instrument with its own volume, pan, reverb, and tuning. */
	public record Preset(String name, int id, String wav, int envelope, int volume, int pan, int reverb, int coarseTune,
		int fineTune, File source)
	{}

	/** A percussion instrument that songs select by ID. */
	public record Drum(String name, int id, String wav, int envelope, int keyBase, int volume, int pan, int reverb,
		int randTune, int randVolume, int randPan, int randReverb, File source)
	{}

	public static class Ambient
	{
		/** In order of ID. */
		public final List<AmbientSound> sounds = new ArrayList<>();
		public Radio radio;
	}

	static class Entry
	{
		final String name;
		final XmlReader xmr;
		final Element elem;
		Integer id;

		private Entry(String name, XmlReader xmr, Element elem)
		{
			this.name = name;
			this.xmr = xmr;
			this.elem = elem;
		}

		private String optional(Key key)
		{
			return xmr.hasAttribute(elem, key) ? xmr.getAttribute(elem, key) : null;
		}

		private String required(Key key)
		{
			xmr.requiresAttribute(elem, key);
			return xmr.getAttribute(elem, key);
		}

		private int readInt(Key key, int defaultValue, int min, int max)
		{
			return AudioXml.readInt(xmr, elem, key, defaultValue, min, max);
		}

		private int readHex(Key key, int defaultValue, int min, int max)
		{
			return optional(key) == null ? defaultValue : AudioXml.readHex(xmr, elem, key, min, max);
		}

		void complain(String message)
		{
			xmr.complain(elem.getTagName() + " '" + name + "': " + message);
		}
	}

	/** Songs indexed by ID. Unused IDs below the highest one repeat the lowest song. */
	public static Song[] readSongs(AudioLayers layers)
	{
		Map<String, Entry> entries = merge(layers, "songs.xml", Key.SongList, Key.Song);
		assignIds(entries, MAX_SONG_ID);

		Song[] songs = new Song[entries.values().stream().mapToInt(e -> e.id).max().orElse(-1) + 1];
		for (Entry e : entries.values()) {
			checkIdentifier(e);
			String[] bk = { e.optional(Key.bk1), e.optional(Key.bk2), e.optional(Key.bk3) };
			songs[e.id] = new Song(e.name, e.id, e.required(Key.bgm), bk, e.xmr.getSourceFile());
		}

		Song filler = Arrays.stream(songs).filter(s -> s != null).findFirst()
			.orElseThrow(() -> new InputFileException(new File("songs.xml"), "there are no songs"));
		Arrays.setAll(songs, i -> songs[i] == null ? filler : songs[i]);
		return songs;
	}

	/** Always-loaded banks in table order. */
	public static List<Bank> readBanks(AudioLayers layers)
	{
		Map<String, Entry> entries = merge(layers, "banks.xml", Key.BankList, Key.Bank);

		Map<String, Integer> slots = new HashMap<>();
		Set<Integer> used = new HashSet<>();
		for (Entry e : entries.values()) {
			boolean hasGroup = e.optional(Key.group) != null;
			if (hasGroup != (e.optional(Key.index) != null))
				e.complain("group and index must be given together");
			if (hasGroup) {
				int slot = readHex(e, Key.group, MIN_BANK_GROUP, MAX_BANK_GROUP) << 4 | readHex(e, Key.index, 0, 0xF);
				if (!used.add(slot))
					e.complain(String.format("group %X index %X is used by another bank", slot >> 4, slot & 0xF));
				slots.put(e.name, slot);
			}
		}

		if (entries.size() > MAX_BANKS)
			throw new InputFileException(new File("banks.xml"), "there are " + entries.size()
				+ " banks, but the engine loads at most " + MAX_BANKS);

		int next = MIN_BANK_GROUP << 4;
		for (Entry e : sorted(entries)) {
			if (slots.containsKey(e.name))
				continue;
			while (used.contains(next))
				next++;
			if (next >> 4 > MAX_BANK_GROUP)
				e.complain("there are no free bank slots");
			used.add(next);
			slots.put(e.name, next);
		}

		List<Bank> banks = new ArrayList<>();
		for (Entry e : entries.values())
			banks.add(new Bank(e.name, slots.get(e.name) >> 4, slots.get(e.name) & 0xF, e.xmr.getSourceFile()));
		return banks;
	}

	/** Presets indexed by ID, with null for unused IDs. */
	public static Preset[] readPresets(AudioLayers layers)
	{
		Map<String, Entry> entries = merge(layers, "presets.xml", Key.Instruments, Key.Instrument);
		assignIds(entries, MAX_PRESET_ID);

		Preset[] presets = new Preset[entries.values().stream().mapToInt(e -> e.id).max().orElse(-1) + 1];
		for (Entry e : entries.values()) {
			checkIdentifier(e);
			presets[e.id] = new Preset(e.name, e.id, e.required(Key.wav), e.readInt(Key.envelope, 0, 0, 3),
				e.readHex(Key.volume, DEFAULT_VOLUME, 0, 255), e.readInt(Key.pan, DEFAULT_PAN, -128, 127),
				e.readInt(Key.reverb, 0, 0, 255), e.readInt(Key.coarseTune, 0, -128, 127),
				e.readInt(Key.fineTune, 0, -128, 127), e.xmr.getSourceFile());
		}
		return presets;
	}

	/** Drums indexed by ID, with null for unused IDs. */
	public static Drum[] readDrums(AudioLayers layers)
	{
		Map<String, Entry> entries = merge(layers, "drums.xml", Key.Drums, Key.Drum);
		assignIds(entries, MAX_DRUM_ID);

		Drum[] drums = new Drum[entries.values().stream().mapToInt(e -> e.id).max().orElse(-1) + 1];
		for (Entry e : entries.values()) {
			checkIdentifier(e);
			drums[e.id] = new Drum(e.name, e.id, e.required(Key.wav), e.readInt(Key.envelope, 0, 0, 3),
				e.readInt(Key.keyBase, DEFAULT_KEY_BASE, 0, 0xFFFF), e.readHex(Key.volume, DEFAULT_VOLUME, 0, 255),
				e.readInt(Key.pan, DEFAULT_PAN, -128, 127), e.readInt(Key.reverb, 0, 0, 255),
				e.readInt(Key.randTune, 0, 0, 255), e.readHex(Key.randVolume, 0, 0, 255),
				e.readInt(Key.randPan, 0, 0, 255), e.readInt(Key.randReverb, 0, 0, 255), e.xmr.getSourceFile());
		}
		return drums;
	}

	public static Ambient readAmbient(AudioLayers layers)
	{
		Map<String, Entry> entries = merge(layers, "ambient_sounds.xml", Key.AmbientSounds, Key.AmbientSound, Key.Radio);

		Ambient ambient = new Ambient();
		Map<String, Entry> sounds = new LinkedHashMap<>();
		for (Entry e : entries.values()) {
			checkIdentifier(e);
			if (e.elem.getTagName().equals(Key.AmbientSound.toString())) {
				sounds.put(e.name, e);
				continue;
			}

			if (ambient.radio != null)
				e.complain("there can be only one Radio");
			if (e.id != null && e.id != RADIO_ID)
				e.complain(String.format("the radio must use ID %X", RADIO_ID));

			String[] mseq = new String[RADIO_STATIONS];
			for (Element station : e.xmr.getTags(e.elem, Key.Station)) {
				int player = e.xmr.readInt(station, Key.player);
				if (player < 0 || player >= RADIO_STATIONS || mseq[player] != null)
					e.complain("Station player must be a unique number from 0 to " + (RADIO_STATIONS - 1));
				e.xmr.requiresAttribute(station, Key.mseq);
				mseq[player] = mseqFile(e.xmr.getAttribute(station, Key.mseq));
			}
			if (Arrays.asList(mseq).contains(null))
				e.complain("there must be " + RADIO_STATIONS + " stations");
			ambient.radio = new Radio(e.name, e.required(Key.bank), mseq, e.xmr.getSourceFile());
		}

		assignIds(sounds, MAX_AMBIENT_ID, RADIO_ID);
		for (Entry e : sounds.values())
			ambient.sounds.add(new AmbientSound(e.name, e.id, mseqFile(e.required(Key.mseq)), e.xmr.getSourceFile()));
		ambient.sounds.sort(Comparator.comparingInt(AmbientSound::id));
		return ambient;
	}

	/** Ambient sounds name an MSEQ with or without its extension. */
	private static String mseqFile(String name)
	{
		return name.endsWith(Mseq.EXTENSION) ? name : name + Mseq.EXTENSION;
	}

	static Map<String, Entry> merge(AudioLayers layers, String table, Key root, Key ... tags)
	{
		List<File> files = layers.getTables(table);
		if (files.isEmpty())
			throw new InputFileException(new File(table), "not found in any asset layer");

		Map<String, Entry> merged = new LinkedHashMap<>();
		for (File file : files) {
			XmlReader xmr = new XmlReader(file);
			if (!xmr.getRootElement().getTagName().equals(root.toString()))
				xmr.complain("expected root tag " + root);

			Set<String> seen = new HashSet<>();
			for (Node child = xmr.getRootElement().getFirstChild(); child != null; child = child.getNextSibling()) {
				if (!(child instanceof Element elem))
					continue;
				if (Arrays.stream(tags).noneMatch(tag -> tag.toString().equals(elem.getTagName())))
					xmr.complain("unexpected tag " + elem.getTagName());

				xmr.requiresAttribute(elem, Key.name);
				String name = xmr.getAttribute(elem, Key.name);
				if (!seen.add(name))
					xmr.complain(elem.getTagName() + " '" + name + "' is listed more than once");

				if (xmr.hasAttribute(elem, Key.delete) && xmr.readBoolean(elem, Key.delete)) {
					merged.remove(name);
					continue;
				}

				Entry entry = new Entry(name, xmr, elem);
				Entry replaced = merged.get(name);
				if (entry.optional(Key.id) != null)
					entry.id = readHex(entry, Key.id, 0, Integer.MAX_VALUE);
				else if (replaced != null)
					entry.id = replaced.id;
				merged.put(name, entry);
			}
		}
		return merged;
	}

	/** Gives each entry without an ID the lowest free one, going through the entries by name. IDs in reserved are never used. */
	private static void assignIds(Map<String, Entry> entries, int maxId, int ... reserved)
	{
		Map<Integer, Entry> used = new HashMap<>();
		for (Entry e : entries.values()) {
			if (e.id == null)
				continue;
			if (e.id > maxId)
				e.complain(String.format("ID %X is above the maximum of %X", e.id, maxId));
			if (contains(reserved, e.id))
				e.complain(String.format("ID %X is reserved", e.id));
			Entry other = used.put(e.id, e);
			if (other != null)
				e.complain(String.format("ID %X is already used by '%s'", e.id, other.name));
		}

		int next = 0;
		for (Entry e : sorted(entries)) {
			if (e.id != null)
				continue;
			while (used.containsKey(next) || contains(reserved, next))
				next++;
			if (next > maxId)
				e.complain("there are no free IDs");
			e.id = next;
			used.put(next, e);
		}
	}

	private static boolean contains(int[] values, int value)
	{
		return Arrays.stream(values).anyMatch(v -> v == value);
	}

	private static List<Entry> sorted(Map<String, Entry> entries)
	{
		return entries.values().stream().sorted(Comparator.comparing(e -> e.name)).toList();
	}

	static void checkIdentifier(Entry e)
	{
		if (!IDENTIFIER.matcher(e.name).matches())
			e.complain("the name must start with a letter and contain only letters, digits, and underscores");
	}

	private static int readHex(Entry e, Key key, int min, int max)
	{
		int value = e.xmr.readHex(e.elem, key);
		if (value < min || value > max)
			e.complain(String.format("%s must be from %X to %X", key, min, max));
		return value;
	}
}
