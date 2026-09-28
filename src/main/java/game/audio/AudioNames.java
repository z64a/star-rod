package game.audio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.Resource;
import app.Resource.ResourceType;

/** Names of the vanilla songs and ambient sounds, and the C identifiers generated from names. */
public abstract class AudioNames
{
	private static final Pattern ENUM_ENTRY = Pattern.compile("([0-9A-Fa-f]+)\\s*=\\s*(\\w+)");
	private static final Pattern UNUSED = Pattern.compile("Unused_[0-9A-F]{2}");

	private static final Map<Integer, String> songs = readEnum("songs.enum");
	private static final Map<Integer, String> ambients = readEnum("ambient_sounds.enum");
	private static final Map<Integer, String> sounds = readEnum("sounds.enum");
	private static final Map<String, String> samples = readSamples();

	public static String getSongName(int id)
	{
		return songs.getOrDefault(id, String.format("Unused_%02X", id));
	}

	public static String getAmbientName(int id)
	{
		return ambients.getOrDefault(id, String.format("Unused_%02X", id));
	}

	/** The name of a sound effect. A sound without a vanilla name is called Unk_ and its ID in hex. */
	public static String getSoundName(int id)
	{
		String name = sounds.get(id);
		return name == null || name.startsWith("Empty_") ? String.format("Unk_%04X", id) : name;
	}

	/** Names that stand in for slots without a real name get no C identifier. */
	public static boolean isPlaceholderSound(String name)
	{
		return name.startsWith("Empty_") || name.startsWith("Unused_") || name.startsWith("Unk_") || name.startsWith("Invalid_");
	}

	/** The name of a vanilla sound bank sample, given as the bank name, an underscore, and the instrument index in hex. */
	public static String getSampleName(String rawName)
	{
		return samples.getOrDefault(rawName, rawName);
	}

	/** Names of files in the order the vanilla SBN stores them. */
	public static List<String> getFileOrder()
	{
		return Resource.getText(ResourceType.Audio, "FileOrder.txt");
	}

	/** Placeholder names are kept in the tables but get no C identifier. */
	public static boolean isUnused(String name)
	{
		return UNUSED.matcher(name).matches();
	}

	/** Prefixes a PascalCase name and converts it to upper snake case, so HuffNPuff1 becomes HUFF_N_PUFF_1. */
	public static String toCName(String prefix, String name)
	{
		StringBuilder sb = new StringBuilder(prefix);
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (i > 0 && isBoundary(name.charAt(i - 1), c, i + 1 < name.length() ? name.charAt(i + 1) : 0))
				sb.append('_');
			sb.append(Character.toUpperCase(c));
		}
		return sb.toString();
	}

	private static boolean isBoundary(char prev, char c, char next)
	{
		if (Character.isLowerCase(prev))
			return Character.isUpperCase(c) || Character.isDigit(c);
		if (Character.isDigit(prev))
			return Character.isUpperCase(c) && Character.isLowerCase(next);
		return Character.isUpperCase(prev) && Character.isUpperCase(c) && Character.isLowerCase(next);
	}

	private static Map<String, String> readSamples()
	{
		Map<String, String> names = new HashMap<>();
		for (String line : Resource.getText(ResourceType.Audio, "Samples.txt")) {
			String[] columns = line.trim().split("\\s+");
			if (columns.length == 2 && !columns[0].startsWith("#"))
				names.put(columns[0], columns[1]);
		}
		return names;
	}

	private static Map<Integer, String> readEnum(String resource)
	{
		Map<Integer, String> names = new HashMap<>();
		for (String line : Resource.getText(ResourceType.Audio, resource)) {
			Matcher m = ENUM_ENTRY.matcher(line.trim());
			if (m.lookingAt())
				names.put(Integer.parseInt(m.group(1), 16), m.group(2));
		}
		return names;
	}
}
