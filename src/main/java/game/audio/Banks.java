package game.audio;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.input.InputFileException;

/**
 * The banks the engine always loads, each in a slot: a group and an index within it. A vanilla project lists them in
 * banks.xml. In papermario-dx, every bank in the bank directory is always loaded, except the song banks that songs and
 * the radio load into the engine's aux banks when they play.
 */
public abstract class Banks
{
	public static final int MAX_BANKS = 79;
	public static final int MIN_GROUP = 2;
	public static final int MAX_GROUP = 6;

	/**
	 * An always-loaded bank: its BK file, such as "MAR0.bk", and its slot. The source is the table that lists it, or the
	 * bank directory in papermario-dx.
	 */
	public record Bank(String file, int group, int index, File source)
	{}

	private static final Map<String, Integer> VANILLA_SLOTS = readVanillaSlots();

	/**
	 * The always-loaded banks. In papermario-dx, they are the bankFiles, such as "MAR0.bk", except the songBanks; other
	 * projects list them in banks.xml.
	 */
	public static List<Bank> read(AudioLayers layers, Collection<String> bankFiles, Supplier<Set<String>> songBanks)
	{
		return Environment.isDX() ? alwaysLoaded(bankFiles, songBanks.get()) : AudioTables.readBanks(layers);
	}

	/** The BK files that songs and the radio, whose bank is null when there is no radio, load into aux banks. */
	public static Set<String> songBanks(List<Songs.Song> songs, String radioBank)
	{
		Set<String> banks = new HashSet<>();
		for (Songs.Song song : songs) {
			for (String bank : song.auxBanks()) {
				if (bank != null)
					banks.add(bank);
			}
		}
		if (radioBank != null)
			banks.add(radioBank);
		return banks;
	}

	/**
	 * The always-loaded banks, in order of name. Banks the vanilla game always loads keep their slots, and the rest
	 * get the free slots in order of name.
	 */
	private static List<Bank> alwaysLoaded(Collection<String> bankFiles, Set<String> songBanks)
	{
		Set<String> files = new TreeSet<>(bankFiles);
		files.removeAll(songBanks);
		if (files.size() > MAX_BANKS)
			throw new InputFileException(new File("bank"), "%d banks are always loaded, but the engine loads at most %d; "
				+ "banks that no song loads are always loaded", files.size(), MAX_BANKS);

		Map<String, Integer> slots = new HashMap<>();
		Set<Integer> used = new HashSet<>();
		for (String file : files) {
			Integer slot = VANILLA_SLOTS.get(file);
			if (slot != null) {
				slots.put(file, slot);
				used.add(slot);
			}
		}

		int next = MIN_GROUP << 4;
		List<Bank> banks = new ArrayList<>();
		for (String file : files) {
			if (!slots.containsKey(file)) {
				while (used.contains(next))
					next++;
				used.add(next);
				slots.put(file, next);
			}
			banks.add(new Bank(file, slots.get(file) >> 4, slots.get(file) & 0xF, new File("bank")));
		}
		return banks;
	}

	private static Map<String, Integer> readVanillaSlots()
	{
		Map<String, Integer> slots = new HashMap<>();
		for (String line : Resource.getText(ResourceType.Audio, "BankSlots.txt")) {
			String[] columns = line.trim().split("\\s+");
			if (columns.length == 3 && !columns[0].startsWith("#"))
				slots.put(columns[0] + ".bk", Integer.parseInt(columns[1], 16) << 4 | Integer.parseInt(columns[2], 16));
		}
		return slots;
	}
}
