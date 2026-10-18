package game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import app.StarRodException;
import util.CaseInsensitiveMap;

public class DecompEnum
{
	private final LinkedHashMap<String, Integer> encodeMap = new LinkedHashMap<>();
	private final LinkedHashMap<Integer, String> decodeMap = new LinkedHashMap<>();

	public static void addEnums(CaseInsensitiveMap<DecompEnum> enums, String headerFileLocation)
	{
		addEnums(enums, headerFileLocation, Set.of());
	}

	public static void addEnums(CaseInsensitiveMap<DecompEnum> enums, String headerFileLocation, Set<String> required)
	{
		try {
			// NIO can read WSL paths directly; no cached copy of the header is needed.
			String source = Files.readString(Path.of(headerFileLocation));
			enums.putAll(new DecompEnumParser(headerFileLocation, source).parse(required));
		}
		catch (IOException e) {
			throw new StarRodException(e);
		}
	}

	DecompEnum()
	{}

	void add(String name, int value)
	{
		encodeMap.put(name, value);
		decodeMap.put(value, name);
	}

	public String[] getValues()
	{
		return decodeMap.values().toArray(new String[0]);
	}

	public List<String> getValueList()
	{
		return new ArrayList<>(decodeMap.values());
	}

	public String getName(int id)
	{
		return decodeMap.get(id);
	}

	public Integer getID(String name)
	{
		return encodeMap.get(name);
	}

	public Collection<Entry<String, Integer>> getEntries()
	{
		return encodeMap.entrySet();
	}
}
