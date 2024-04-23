package util;

import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class CountingMap<T>
{
	public static final void main(String[] args)
	{
		CountingMap<String> testMap = new CountingMap<>();
		System.out.println(testMap.add("Item"));
		System.out.println(testMap.add("Item"));
		System.out.println(testMap.add("Item"));
	}

	private TreeMap<T, Integer> map;

	public CountingMap()
	{
		map = new TreeMap<>();
	}

	public void clear()
	{
		map.clear();
	}

	public int add(T obj)
	{
		if (map.containsKey(obj)) {
			int count = map.get(obj);
			map.put(obj, count + 1);
			return count + 1;
		}
		else {
			map.put(obj, 1);
			return 1;
		}
	}

	public int getCount(T obj)
	{
		if (map.containsKey(obj))
			return map.get(obj);
		else
			return 0;
	}

	public Set<Entry<T, Integer>> entrySet()
	{
		return map.entrySet();
	}

	public void print()
	{
		for (Entry<T, Integer> e : map.entrySet())
			System.out.printf("%-5d : %s%n", e.getValue(), e.getKey());
	}
}
