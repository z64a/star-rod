package util;

import java.util.Iterator;

import javax.swing.DefaultListModel;

public class IterableListModel<T> extends DefaultListModel<T> implements Iterable<T> //, ComboBoxModel<T>
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

	/*
	private Object selectedItem;
	
	@Override
	public void setSelectedItem(Object anItem)
	{
		if ((selectedItem != null && !selectedItem.equals(anItem)) ||
			(selectedItem == null && anItem != null)) {
			selectedItem = anItem;
			// Notify that the selected item has changed
			fireContentsChanged(this, -1, -1);
		}
	}
	
	@Override
	public Object getSelectedItem()
	{
		return selectedItem;
	}
	*/
}
