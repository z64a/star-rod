package game.map.editor.selection;

import java.util.IdentityHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class EditorUpdateRecord<T>
{
	public boolean updated = false;
	public final LinkedBlockingQueue<T> added;
	public final LinkedBlockingQueue<T> removed;
	public final LinkedBlockingQueue<T> deleted;
	public final LinkedBlockingQueue<T> created;

	// ensure that objects only go into ONE list at a time
	private final IdentityHashMap<T, LinkedBlockingQueue<T>> actionListMap;

	public EditorUpdateRecord()
	{
		added = new LinkedBlockingQueue<>();
		removed = new LinkedBlockingQueue<>();
		deleted = new LinkedBlockingQueue<>();
		created = new LinkedBlockingQueue<>();
		actionListMap = new IdentityHashMap<>();
	}

	private void addExclusive(T obj, LinkedBlockingQueue<T> newList)
	{
		LinkedBlockingQueue<T> list = actionListMap.get(obj);

		if (list == newList)
			return;

		if (list != null)
			list.remove(obj);

		newList.add(obj);
		actionListMap.put(obj, newList);
	}

	public void clear(Iterable<T> items)
	{
		updated = true;
		for (T item : items)
			addExclusive(item, removed);
	}

	public void select(T item)
	{
		updated = true;
		addExclusive(item, added);
	}

	public void deselect(T item)
	{
		updated = true;
		addExclusive(item, removed);
	}

	public void delete(T item)
	{
		updated = true;
		addExclusive(item, deleted);
	}

	public void create(T item)
	{
		updated = true;
		addExclusive(item, created);
	}

	public void reset()
	{
		updated = false;
		added.clear();
		removed.clear();
		deleted.clear();
		created.clear();
		actionListMap.clear();
	}
}
