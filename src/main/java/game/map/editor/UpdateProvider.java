package game.map.editor;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class UpdateProvider
{
	private List<UpdateListener> updateListeners = new LinkedList<>();

	protected void notifyListeners()
	{
		notifyListeners("");
	}

	protected void notifyListeners(String tag)
	{
		for (UpdateListener listener : updateListeners)
			listener.update(tag);
	}

	public void registerListener(UpdateListener listener)
	{
		for (UpdateListener ul : updateListeners) {
			if (ul == listener)
				return;
		}
		updateListeners.add(listener);
	}

	public void deregisterListener(UpdateListener listener)
	{
		Iterator<UpdateListener> iter = updateListeners.iterator();
		while (iter.hasNext()) {
			if (iter.next() == listener)
				iter.remove();
		}
	}
}
