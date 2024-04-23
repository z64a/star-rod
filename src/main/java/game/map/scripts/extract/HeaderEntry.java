package game.map.scripts.extract;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.Directories;
import app.Environment;
import app.LoadingBar;
import app.input.IOUtils;
import util.Logger;
import util.NameUtils;

public class HeaderEntry
{
	public static class HeaderParseException extends Exception
	{
		public HeaderParseException(String msg)
		{
			super(msg);
		}
	}

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		LoadingBar.show("Testing Map Data");

		File f = Directories.PROJ_SRC_WORLD.toFile();
		File[] worldDirs = f.listFiles();
		Arrays.sort(worldDirs);
		int mismatchCount = 0;
		int mapCount = 0;

		for (File worldDir : worldDirs) {
			if (worldDir.isDirectory() && worldDir.getName().matches("area_\\w+")) {
				String areaName = worldDir.getName().substring(5);
				File[] mapDirs = worldDir.listFiles();
				Arrays.sort(mapDirs);

				for (File mapDir : mapDirs) {
					if (mapDir.isDirectory() && mapDir.getName().startsWith(areaName)) {
						boolean matched = test(worldDir.getName() + "/" + mapDir.getName());
						if (!matched) {
							System.out.println("X " + mapDir.getName());
							mismatchCount++;
						}
						mapCount++;
					}
				}
			}
		}

		System.out.printf("Matched: %d / %d%n", mapCount - mismatchCount, mapCount);
		if (mismatchCount == 0)
			System.out.println(":)");

		LoadingBar.dismiss();
		Environment.exit();
	}

	public static boolean test(String mapPath) throws IOException
	{
		File in = Directories.PROJ_SRC_WORLD.file(mapPath + "/generated.h");
		File out = Directories.PROJ_SRC_WORLD.file(mapPath + "/test.h");

		List<HeaderEntry> entries = parseFile(in);

		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(out)) {
			pw.println("/* auto-generated, do not edit */");
			pw.println("#include \"star_rod_macros.h\"");
			pw.println();

			for (HeaderEntry h : entries) {
				h.print(pw);
			}
		}

		String inText = Files.readString(in.toPath());
		String outText = Files.readString(out.toPath());

		return inText.equals(outText);
	}

	private LinkedHashMap<String, String> properties = new LinkedHashMap<>();
	private LinkedHashMap<String, String> simpleDefines = new LinkedHashMap<>();
	private LinkedHashMap<String, List<String>> blockDefines = new LinkedHashMap<>();
	private String genName = "GEN";

	private HeaderEntry()
	{}

	public HeaderEntry(String type)
	{
		addProperty("type", type);
	}

	public void setType(String type)
	{
		addProperty("type", type);
	}

	public void setName(String name)
	{
		addProperty("name", name);
		genName = NameUtils.toEnumStyle("GEN_" + name);
	}

	public String getPropertyUnchecked(String property)
	{
		return properties.get(property);
	}

	public String getProperty(String property) throws HeaderParseException
	{
		String value = properties.get(property);
		if (value == null)
			throw new HeaderParseException("HeaderEntry missing property: " + property);
		return value;
	}

	public boolean hasDefine(String suffix)
	{
		String key = genName + "_" + suffix;
		return simpleDefines.containsKey(key);
	}

	public String getDefine(String suffix) throws HeaderParseException
	{
		String key = genName + "_" + suffix;
		String value = simpleDefines.get(key);
		if (value == null)
			throw new HeaderParseException("HeaderEntry missing #define: " + key);
		return value;
	}

	public List<String> getBlockDefine(String suffix) throws HeaderParseException
	{
		// special case for entries having a single block defined
		if ("*".equals(suffix))
			return blockDefines.values().iterator().next();

		String key = genName + "_" + suffix;
		List<String> list = blockDefines.get(key);
		if (list == null)
			throw new HeaderParseException("HeaderEntry missing #define: " + key);
		return list;
	}

	public int getIntDefine(String suffix) throws HeaderParseException
	{
		String value = getDefine(suffix);
		try {
			return Integer.decode(value);
		}
		catch (NumberFormatException e) {
			throw new HeaderParseException("HeaderEntry #define " + genName + "_" + suffix + " is not integer: " + value);
		}
	}

	public int[] getVecDefine(String suffix, int length) throws HeaderParseException
	{
		String value = getDefine(suffix);
		int[] vector = new int[length];

		try {
			String[] tokens = value.replaceAll("//s+", "").split(",");
			if (tokens.length != length)
				throw new HeaderParseException("HeaderEntry vector #define has wrong length: " + genName + "_" + suffix);

			for (int i = 0; i < vector.length; i++)
				vector[i] = Integer.decode(tokens[i]);
		}
		catch (NumberFormatException e) {
			throw new HeaderParseException("HeaderEntry #define " + genName + "_" + suffix + " is not integer: " + value);
		}

		return vector;
	}

	public String namespace(String suffix)
	{
		return genName + "_" + suffix;
	}

	public String denamespace(String name)
	{
		String prefix = genName + "_";
		if (name.startsWith(prefix))
			return name.substring(prefix.length());
		else
			return name;
	}

	public void addProperty(String key, String value)
	{
		properties.put(key, value);
	}

	public void addDefine(String suffix, String value)
	{
		String key = genName + "_" + suffix;
		simpleDefines.put(key, value);
	}

	public void addDefine(String suffix, int value)
	{
		String key = genName + "_" + suffix;
		simpleDefines.put(key, "" + value);
	}

	public void addDefine(String suffix, String fmt, Object ... args)
	{
		String key = genName + "_" + suffix;
		simpleDefines.put(key, String.format(fmt, args));
	}

	public void addDefine(String suffix, List<String> content)
	{
		String key = genName + "_" + suffix;
		blockDefines.put(key, content);
	}

	public void print(PrintWriter pw)
	{
		for (Entry<String, String> entry : properties.entrySet()) {
			pw.printf("// %s: %s%n", entry.getKey(), entry.getValue());
		}

		for (Entry<String, String> entry : simpleDefines.entrySet()) {
			pw.printf("#define %s %s%n", entry.getKey(), entry.getValue());
		}

		for (Entry<String, List<String>> entry : blockDefines.entrySet()) {
			String name = entry.getKey();
			List<String> lines = entry.getValue();

			pw.printf("#define %s", name);
			for (int i = 0; i < lines.size(); i++)
				pw.printf(" \\%n%s", lines.get(i));
			pw.println();
		}

		pw.println();
	}

	public void print()
	{
		for (Entry<String, String> entry : properties.entrySet()) {
			System.out.printf("// %s: %s%n", entry.getKey(), entry.getValue());
		}

		for (Entry<String, String> entry : simpleDefines.entrySet()) {
			System.out.printf("#define %s %s%n", entry.getKey(), entry.getValue());
		}

		for (Entry<String, List<String>> entry : blockDefines.entrySet()) {
			String name = entry.getKey();
			List<String> lines = entry.getValue();

			System.out.printf("#define %s", name);
			for (int i = 0; i < lines.size(); i++)
				System.out.printf(" \\%n%s", lines.get(i));
			System.out.println();
		}

		System.out.println();
	}

	private static final Matcher CommentLineMatcher = Pattern.compile(
		"\\s*\\/\\*.+\\*\\/\\s*").matcher("");

	private static final Matcher PropertyMatcher = Pattern.compile(
		"//\\s*(\\w+):\\s*(\\S+)\\s*").matcher("");

	private static final Matcher SimpleDefineMatcher = Pattern.compile(
		"#define\\s+(\\w+)\\s+([^\\\\]+)\\s*").matcher("");

	private static final Matcher ComplexDefineMatcher = Pattern.compile(
		"#define\\s+(\\w+)\\s+\\\\\\s*").matcher("");

	private static final Matcher ComplexLineMatcher = Pattern.compile(
		"(.+?)\\s*\\\\\\s*").matcher("");

	public static List<HeaderEntry> parseFile(File header) throws IOException
	{
		List<HeaderEntry> entries = new ArrayList<>();

		HeaderEntry h = null;
		String currentDefineName = null;
		List<String> currentDefineContent = null;

		for (String line : IOUtils.readPlainTextFile(header)) {
			if (line.startsWith("#include "))
				continue;

			CommentLineMatcher.reset(line);
			if (CommentLineMatcher.matches())
				continue;

			if (h == null) {
				// valid property indicates the start of a new header entry
				PropertyMatcher.reset(line);
				if (PropertyMatcher.matches()) {
					h = new HeaderEntry();
					entries.add(h);
					h.addProperty(PropertyMatcher.group(1), PropertyMatcher.group(2));
				}
			}
			else {
				if (currentDefineContent != null) {
					// is this another line of the #define (terminated with a line continuation character)?
					ComplexLineMatcher.reset(line);
					if (ComplexLineMatcher.matches()) {
						currentDefineContent.add(ComplexLineMatcher.group(1));
						continue; // keep adding lines
					}

					// continuation pattern didnt match, this was the last line of the define
					currentDefineContent.add(line);
					h.addDefine(currentDefineName, currentDefineContent);

					// flush current define
					currentDefineName = null;
					currentDefineContent = null;
				}
				else {
					// is this line is another property?
					PropertyMatcher.reset(line);
					if (PropertyMatcher.matches()) {
						if ("name".equals(PropertyMatcher.group(1))) {
							h.setName(PropertyMatcher.group(2));
						}
						else {
							h.addProperty(PropertyMatcher.group(1), PropertyMatcher.group(2));
						}
						continue;
					}

					// is this line is a one-line #define?
					SimpleDefineMatcher.reset(line);
					if (SimpleDefineMatcher.matches()) {
						h.addDefine(h.denamespace(SimpleDefineMatcher.group(1)), SimpleDefineMatcher.group(2));
						continue;
					}

					// is this line the start of a multi-line #define?
					ComplexDefineMatcher.reset(line);
					if (ComplexDefineMatcher.matches()) {
						currentDefineName = h.denamespace(ComplexDefineMatcher.group(1));
						currentDefineContent = new ArrayList<>();
						continue;
					}

					// is this line whitespace (indicating the end of current header entry)?
					if (line.isBlank()) {
						h = null;
						continue;
					}

					// this is an invalid line!
					Logger.logWarning("Unknown header line: " + line);
				}
			}
		}

		return entries;
	}
}
