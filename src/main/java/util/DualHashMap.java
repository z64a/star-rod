package util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DualHashMap<K extends Object, V extends Object>
{
	private Map<K, V> forward = new LinkedHashMap<>();
	private Map<V, K> backward = new LinkedHashMap<>();

	public synchronized void add(K key, V value)
	{
		forward.put(key, value);
		backward.put(value, key);
	}

	public void clear()
	{
		forward.clear();
		backward.clear();
	}

	public boolean isEmpty()
	{ return forward.isEmpty(); }

	public Set<K> getKeySet()
	{ return forward.keySet(); }

	public Set<V> getValues()
	{ return backward.keySet(); }

	public boolean contains(K key)
	{
		return forward.containsKey(key);
	}

	public synchronized V get(K key)
	{
		return forward.get(key);
	}

	public boolean containsInverse(V key)
	{
		return backward.containsKey(key);
	}

	public synchronized K getInverse(V key)
	{
		return backward.get(key);
	}

	// handle with care!
	public void remove(K key)
	{
		V value = forward.get(key);
		forward.remove(key);
		backward.remove(value);
	}
}
