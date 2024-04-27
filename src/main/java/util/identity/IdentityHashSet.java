package util.identity;

import java.util.IdentityHashMap;
import java.util.Iterator;

public class IdentityHashSet<E> implements Iterable<E>
{
	private static final Object DUMMY = new Object();
	private IdentityHashMap<E, Object> map;

	public IdentityHashSet()
	{
		this(16);
	}

	public IdentityHashSet(int initialCapacity)
	{
		map = new IdentityHashMap<>(initialCapacity);
	}

	public boolean add(E item)
	{
		return (map.put(item, DUMMY) == null);
	}

	public boolean remove(E item)
	{
		return (map.remove(item) == null);
	}

	public int size()
	{
		return map.size();
	}

	public void clear()
	{
		map.clear();
	}

	public boolean contains(Object obj)
	{
		return map.containsKey(obj);
	}

	public boolean isEmpty()
	{
		return map.isEmpty();
	}

	@Override
	public Iterator<E> iterator()
	{
		return map.keySet().iterator();
	}
}
