package util;

import java.util.Collection;
import java.util.LinkedHashMap;

public class CaseInsensitiveMap<T>
{
	private final LinkedHashMap<String, T> map = new LinkedHashMap<>();

	public T put(String key, T value)
	{
		return map.put(key.toUpperCase(), value);
	}

	public void putAll(CaseInsensitiveMap<T> other)
	{
		map.putAll(other.map);
	}

	public T get(String key)
	{
		return map.get(key.toUpperCase());
	}

	public boolean containsKey(String key)
	{
		return map.containsKey(key.toUpperCase());
	}

	public void clear()
	{
		map.clear();
	}

	public Collection<T> values()
	{
		return map.values();
	}

	public int size()
	{
		return map.size();
	}
}
