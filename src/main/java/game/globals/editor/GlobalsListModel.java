package game.globals.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import util.IterableListModel;

public class GlobalsListModel<T extends GlobalsRecord> extends IterableListModel<T>
{
	private final HashMap<String, T> lookup;

	public GlobalsListModel()
	{
		lookup = new HashMap<>();

		this.addListDataListener(new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e)
			{
				//	System.out.printf("> %d to %d added!%n", e.getIndex0(), e.getIndex1());
				for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
					T rec = getElementAt(i);
					lookup.put(rec.getIdentifier(), rec);
				}
			}

			@Override
			public void intervalRemoved(ListDataEvent e)
			{
				//	System.out.printf("< %d to %d removed!%n", e.getIndex0(), e.getIndex1());
				// rebuild the map
				lookup.clear();
				for (int i = 0; i < size(); i++) {
					T rec = getElementAt(i);
					lookup.put(rec.getIdentifier(), rec);
				}
			}

			@Override
			public void contentsChanged(ListDataEvent e)
			{
				System.out.printf("# %d to %d changed!%n", e.getIndex0(), e.getIndex1());
			}
		});
	}

	public void recalculateIndices()
	{
		for (int i = 0; i < getSize(); i++) {
			T rec = getElementAt(i);
			rec.setIndex(i);
		}
	}

	public void rebuildNameCache()
	{
		lookup.clear();
		for (int i = 0; i < getSize(); i++) {
			T rec = getElementAt(i);
			lookup.put(rec.getIdentifier(), rec);
		}
	}

	public T getElement(String identifier)
	{
		return lookup.get(identifier);
	}

	public List<T> toList()
	{
		ArrayList<T> list = new ArrayList<>();
		for (T rec : this) {
			list.add(rec);
		}
		return list;
	}
}
