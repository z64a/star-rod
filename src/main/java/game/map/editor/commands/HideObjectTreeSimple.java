package game.map.editor.commands;

import java.util.ArrayList;
import java.util.List;

import game.map.MapObject;
import game.map.tree.MapObjectTreeModel;

public class HideObjectTreeSimple extends AbstractCommand
{
	private final List<MapObject> objs;
	private final boolean[] wasHidden;
	private final boolean hiding;

	public HideObjectTreeSimple(String name, MapObjectTreeModel<?> tree, boolean hide)
	{
		super(hide ? "Hide " + name : "Show " + name);
		objs = new ArrayList<>(128);

		for (MapObject obj : tree)
			objs.add(obj);

		wasHidden = new boolean[objs.size()];
		for (int i = 0; i < objs.size(); i++)
			wasHidden[i] = objs.get(i).hidden;

		this.hiding = hide;
	}

	@Override
	public boolean modifiesMap()
	{
		return false;
	}

	@Override
	public void exec()
	{
		super.exec();

		for (MapObject obj : objs)
			obj.hidden = hiding;

		editor.gui.repaintObjectPanel();
	}

	@Override
	public void undo()
	{
		super.undo();

		for (int i = 0; i < objs.size(); i++)
			objs.get(i).hidden = wasHidden[i];

		editor.gui.repaintObjectPanel();
	}
}
