package util;

import java.util.Iterator;

import javax.swing.DefaultListModel;

public class IterableListModel<T> extends DefaultListModel<T> implements Iterable<T>
{
	@Override
	public Iterator<T> iterator()
	{
		return new Iterator<>() {
			private int index = 0;

			@Override
			public boolean hasNext()
			{
				return index < getSize();
			}

			@Override
			public T next()
			{
				return getElementAt(index++);
			}
		};
	}
}
