package game.map.editor.selection;

import java.util.concurrent.LinkedBlockingQueue;

public class GuiUpdateRecord<T>
{
	public boolean updated = false;
	public LinkedBlockingQueue<T> added = new LinkedBlockingQueue<>();
	public LinkedBlockingQueue<T> removed = new LinkedBlockingQueue<>();

	public void select(Iterable<? extends T> selected)
	{
		updated = true;
		for (T obj : selected) {
			if (!added.contains(obj))
				added.add(obj);

			if (removed.contains(obj))
				removed.remove(obj);
		}
	}

	public void deselect(Iterable<? extends T> deselected)
	{
		updated = true;
		for (T obj : deselected) {
			if (!removed.contains(obj))
				removed.add(obj);

			if (added.contains(obj))
				added.remove(obj);
		}
	}

	public void reset()
	{
		updated = false;
		added.clear();
		removed.clear();
	}
}
