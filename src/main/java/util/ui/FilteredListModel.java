package util.ui;

import java.util.ArrayList;

import javax.swing.DefaultListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

// adapted (with minor alterations) from ATrubka
// https://stackoverflow.com/questions/14758313/filtering-jlist-based-on-jtextfield
public class FilteredListModel<T> extends DefaultListModel<T>
{
	public static interface ListFilter
	{
		boolean accept(Object element);
	}

	private final DefaultListModel<T> source;
	private final ArrayList<Integer> indices = new ArrayList<>();
	private ListFilter filter;
	private boolean ignoreChanges = false;

	public FilteredListModel(DefaultListModel<T> source)
	{
		if (source == null)
			throw new IllegalArgumentException("Source is null");

		this.source = source;
		this.source.addListDataListener(new ListDataListener() {
			@Override
			public void intervalRemoved(ListDataEvent e)
			{
				onListChange();
			}

			@Override
			public void intervalAdded(ListDataEvent e)
			{
				onListChange();
			}

			@Override
			public void contentsChanged(ListDataEvent e)
			{
				onListChange();
			}
		});
	}

	private void onListChange()
	{
		if (ignoreChanges)
			return;
		applyFilter();
	}

	public void setIgnoreChanges(boolean value)
	{
		ignoreChanges = value;
	}

	public void setFilter(ListFilter f)
	{
		filter = f;
		applyFilter();
	}

	private void applyFilter()
	{
		indices.clear();

		ListFilter f = filter;
		if (f != null) {
			int count = source.getSize();
			for (int i = 0; i < count; i++) {
				T element = source.getElementAt(i);
				if (f.accept(element))
					indices.add(i);
			}
			fireContentsChanged(this, 0, getSize() - 1);
		}
	}

	public DefaultListModel<T> getSource()
	{
		return source;
	}

	@Override
	public int getSize()
	{
		return (filter != null) ? indices.size() : source.getSize();
	}

	public int getIndexFor(int index)
	{
		return (filter != null) ? indices.get(index) : index;
	}

	@Override
	public T getElementAt(int index)
	{
		return (filter != null) ? source.getElementAt(indices.get(index)) : source.getElementAt(index);
	}
}
