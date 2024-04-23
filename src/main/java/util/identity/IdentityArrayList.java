package util.identity;

import java.util.AbstractList;
import java.util.ArrayList;

public class IdentityArrayList<T> extends AbstractList<T>
{
	private final ArrayList<IdentityWrapper<T>> list;

	private static class IdentityWrapper<T>
	{
		private final T obj;

		private IdentityWrapper(T obj)
		{
			this.obj = obj;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null)
				return false;

			if (!(o instanceof IdentityWrapper))
				return false;

			@SuppressWarnings("unchecked")
			IdentityWrapper<T> other = (IdentityWrapper<T>) o;

			return this.obj == other.obj;
		}

		@Override
		public int hashCode()
		{
			return System.identityHashCode(obj);
		}
	}

	public IdentityArrayList()
	{
		list = new ArrayList<>();
	}

	public IdentityArrayList(int size)
	{
		list = new ArrayList<>(size);
	}

	public IdentityArrayList(Iterable<T> objList)
	{
		list = new ArrayList<>();
		for (T obj : objList)
			add(obj);
	}

	@Override
	public boolean add(T obj)
	{
		return list.add(new IdentityWrapper<>(obj));
	}

	@Override
	public void add(int i, T obj)
	{
		list.add(i, new IdentityWrapper<>(obj));
	}

	@Override
	public boolean remove(Object o)
	{
		@SuppressWarnings("unchecked")
		T obj = (T) o;
		return list.remove(new IdentityWrapper<>(obj));
	}

	@Override
	public T remove(int index)
	{
		IdentityWrapper<T> wrapper = list.remove(index);
		return (wrapper == null) ? null : wrapper.obj;
	}

	@Override
	public T set(int i, T obj)
	{
		IdentityWrapper<T> wrapper = list.set(i, new IdentityWrapper<>(obj));
		return (wrapper == null) ? null : wrapper.obj;
	}

	@Override
	public T get(int i)
	{
		IdentityWrapper<T> wrapper = list.get(i);
		return (wrapper == null) ? null : wrapper.obj;
	}

	@Override
	public int size()
	{
		return list.size();
	}
}
