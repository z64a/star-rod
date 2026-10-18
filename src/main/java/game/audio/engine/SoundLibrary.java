package game.audio.engine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.io.FilenameUtils;

import app.input.InputFileException;
import game.audio.AmbientSounds;
import game.audio.AudioLayers;
import game.audio.AudioTables;
import game.audio.AudioTables.Drum;
import game.audio.AudioTables.Preset;
import game.audio.AudioTasks;
import game.audio.Banks;
import game.audio.Banks.Bank;
import game.audio.Mseq;
import game.audio.Presets;
import game.audio.SfxArchive;
import game.audio.SfxBinary;
import game.audio.SfxFormatException;
import game.audio.SfxXml;
import game.audio.Songs;
import game.audio.SoundBank;
import game.audio.bgm.Song;

/**
 * The audio of the project as it would build: the banks, presets, drums, sound effects, ambience, and songs of
 * the merged asset stack, ready to play. Banks are read from their sources or from a BK file in a layer.
 */
public class SoundLibrary
{
	private static final String SEF_FILE = "sfx/DAT1.sef";

	private final AudioLayers layers;
	private final Map<String, SoundBank> banks = new LinkedHashMap<>();
	private final Map<String, Instrument> samples = new HashMap<>();
	private final Map<String, List<Instrument>> bankInstruments = new HashMap<>();
	private final Instrument silent = Instrument.silent();

	private Presets.Addresses addresses;
	private Presets.Addresses ambientAddresses;
	private AmbientSounds ambient;
	private Drum[] drums = new Drum[0];
	private Preset[] presets = new Preset[0];

	/** Reads every bank and the tables that name instruments. */
	public SoundLibrary()
	{
		layers = AudioLayers.fromEnvironment();
		layers.checkTables();

		ambient = AmbientSounds.read(layers);

		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		names.addAll(layers.listDirectories("bank"));
		for (String file : layers.listFiles("bank", ".bk"))
			names.add(FilenameUtils.removeExtension(file));

		Map<String, SoundBank> files = new HashMap<>();
		for (String name : names) {
			SoundBank bank = readBank(name);
			banks.put(name, bank);
			files.put(name + ".bk", bank);

			List<Instrument> instruments = new ArrayList<>();
			for (SoundBank.Instrument ins : bank.instruments) {
				Instrument instrument = new Instrument(name, ins, bank.envelopes.stream()
					.filter(env -> env.name.equals(ins.envName)).findFirst().orElseThrow());
				instruments.add(instrument);
				samples.putIfAbsent(ins.wav, instrument);
			}
			bankInstruments.put(name, instruments);
		}

		String radioBank = ambient.radioBank(layers, wav -> samples.containsKey(wav) ? samples.get(wav).bank : null);
		List<Bank> bankTable = Banks.read(layers, files.keySet(), () -> Banks.songBanks(listSongs(), radioBank));
		addresses = new Presets.Addresses(bankTable, files);
		ambientAddresses = radioBank != null && files.containsKey(radioBank)
			? addresses.withAuxiliary(AudioTasks.RADIO_BANK_SLOT, files.get(radioBank))
			: addresses;
		if (AudioTasks.usesPresetTable(layers, "drums.xml", "per/SET1.per"))
			drums = AudioTables.readDrums(layers);
		else if (layers.getFile("per/SET1.per") != null) {
			File file = layers.getFile("per/SET1.per");
			drums = Presets.readDrums(file, readFile(file), addresses);
		}
		if (AudioTasks.usesPresetTable(layers, "presets.xml", "prg/SET1.prg"))
			presets = AudioTables.readPresets(layers);
		else if (layers.getFile("prg/SET1.prg") != null) {
			File file = layers.getFile("prg/SET1.prg");
			presets = Presets.readPresets(file, readFile(file), addresses);
		}
	}

	private SoundBank readBank(String name)
	{
		File conflict = layers.findConflict("bank/" + name, "bank/" + name + ".bk");
		if (conflict != null)
			throw new InputFileException(conflict, "the layer has both this directory and %s.bk; use only one", name);

		File dir = layers.getDirectory("bank/" + name);
		File binary = layers.getFile("bank/" + name + ".bk");
		if (binary != null && (dir == null || layers.getPriority(binary) < layers.getPriority(dir)))
			return SoundBank.read(binary, name, readFile(binary));
		return SoundBank.load(layers, name);
	}

	private static byte[] readFile(File file)
	{
		try {
			return Files.readAllBytes(file.toPath());
		}
		catch (IOException e) {
			throw new InputFileException(file, e.getMessage());
		}
	}

	/** The banks by name, sorted by name. */
	public List<SoundBank> getBanks()
	{
		return banks.values().stream().sorted(Comparator.comparing(bank -> bank.name, String.CASE_INSENSITIVE_ORDER)).toList();
	}

	public List<Instrument> getInstruments(SoundBank bank)
	{
		return bankInstruments.get(bank.name);
	}

	/** The instrument that uses a sample, or null if no bank has it. */
	public Instrument getInstrument(String wav)
	{
		return samples.get(wav);
	}

	/**
	 * The instrument at a bank and patch address of the engine, or null if no always-loaded bank provides
	 * it. Bank set 2 is the engine's silent default instrument.
	 */
	public Instrument getInstrument(int bank, int patch)
	{
		if ((bank >> 4 & 0xF) == 2)
			return silent;

		try {
			return samples.get(addresses.name(new File(""), "", bank, patch));
		}
		catch (InputFileException e) {
			return null;
		}
	}

	public Drum getDrum(int id)
	{
		return id >= 0 && id < drums.length ? drums[id] : null;
	}

	public Preset getPreset(int id)
	{
		return id >= 0 && id < presets.length ? presets[id] : null;
	}

	/** The songs in order of ID, without the duplicate entries used to fill unused IDs. */
	public List<Songs.Song> listSongs()
	{
		if (!Songs.exist(layers))
			return List.of();
		Map<Integer, Songs.Song> songs = new TreeMap<>();
		for (Songs.Song song : Songs.read(layers))
			songs.put(song.id(), song);
		return List.copyOf(songs.values());
	}

	/** Resolves auxiliary banks independently for each song, leaving SFX and ambience untouched. */
	public Song readSong(Songs.Song entry)
	{
		File file = layers.getFile("songs/" + entry.bgm());
		if (file == null)
			throw new InputFileException(entry.source(), "song %s: songs/%s not found", entry.name(), entry.bgm());
		Presets.Addresses songAddresses = addresses;
		for (int i = 0; i < entry.auxBanks().length; i++) {
			String bankName = entry.auxBanks()[i];
			if (bankName == null)
				continue;
			SoundBank bank = banks.get(baseName(bankName));
			if (bank == null)
				throw new InputFileException(entry.source(), "song %s: bank %s not found", entry.name(), bankName);
			songAddresses = songAddresses.withAuxiliary(i, bank);
		}
		return new Song(file, readFile(file), songAddresses);
	}

	/** Reads the merged sound effect table, or the binary archive if a layer supplies one. */
	public SfxArchive readSfx()
	{
		if (AudioTasks.usesTables(layers, "sound_effects.xml", SEF_FILE))
			return SfxXml.read(layers, addresses);

		File file = layers.getFile(SEF_FILE);
		if (file == null)
			throw new InputFileException(new File(SEF_FILE), "file not found");
		try {
			return SfxBinary.decode(readFile(file)).archive();
		}
		catch (SfxFormatException e) {
			throw new InputFileException(file, e.getMessage());
		}
	}

	/** The names of the ambient sequences, sorted, with the ambient sound or radio station that uses each. */
	public Map<String, String> listMseqs()
	{
		Map<String, String> uses = new HashMap<>();
		for (AmbientSounds.Sound sound : ambient.sounds)
			uses.put(baseName(sound.mseq()), sound.name());
		if (ambient.radio != null) {
			for (int i = 0; i < ambient.radio.mseq().length; i++)
				uses.put(baseName(ambient.radio.mseq()[i]), ambient.radio.name() + " " + i);
		}

		Map<String, String> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (String extension : List.of(".xml", ".mseq")) {
			for (String file : layers.listFiles("mseq", extension))
				names.put(baseName(file), uses.get(baseName(file)));
		}
		return names;
	}

	/** Reads an ambient sequence from its XML source or MSEQ binary; the higher layer wins. */
	public Mseq readMseq(String name)
	{
		File xml = layers.getFile("mseq/" + name + ".xml");
		File binary = layers.getFile("mseq/" + name + Mseq.EXTENSION);
		if (xml != null && binary != null) {
			if (layers.getPriority(xml) == layers.getPriority(binary))
				throw new InputFileException(binary, "the layer has both %s.xml and %s; use only one", name, binary.getName());
			if (layers.getPriority(xml) < layers.getPriority(binary))
				binary = null;
			else
				xml = null;
		}
		if (xml != null)
			return Mseq.read(xml);
		if (binary == null)
			throw new InputFileException(new File("mseq/" + name), "file not found");
		return Mseq.decode(binary, readFile(binary), ambientAddresses);
	}

	private static String baseName(String file)
	{
		return FilenameUtils.removeExtension(file);
	}
}
