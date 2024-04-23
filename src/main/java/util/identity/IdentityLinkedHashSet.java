package util.identity;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashSet;

public class IdentityLinkedHashSet<T> extends AbstractSet<T>
{
	private LinkedHashSet<IdentityWrapper<T>> set = new LinkedHashSet<>();

	private static class IdentityWrapper<T>
	{
		private final T obj;

		private IdentityWrapper(T obj)
		{
			this.obj = obj;
		}

		@Override
		public boolean equals(Object obj)
		{
			return this.obj == obj;
		}

		@Override
		public int hashCode()
		{
			return System.identityHashCode(obj);
		}
	}

	@Override
	public boolean add(T obj)
	{
		return set.add(new IdentityWrapper<>(obj));
	}

	@Override
	public Iterator<T> iterator()
	{
		final Iterator<IdentityWrapper<T>> wrapperIterator = set.iterator();

		return new Iterator<>() {
			@Override
			public boolean hasNext()
			{
				return wrapperIterator.hasNext();
			}

			@Override
			public T next()
			{
				return wrapperIterator.next().obj;
			}
		};
	}

	@Override
	public int size()
	{
		return set.size();
	}
}
