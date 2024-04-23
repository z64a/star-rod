package util;

import java.util.Stack;

public class EvictingStack<T> extends Stack<T>
{
	private int capacity;

	public EvictingStack(int capacity)
	{
		super();
		this.capacity = capacity;
	}

	public void setCapacity(int value)
	{
		capacity = value;

		while ((size() > 0) && (size() + 1 > capacity))
			remove(0);
	}

	@Override
	public T push(T item)
	{
		while ((size() > 0) && (size() + 1 > capacity))
			remove(0);

		return super.push(item);
	}

	@Override
	public boolean add(T item)
	{
		return this.push(item) != null;
	}
}
