package game.map.editor.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JCheckBoxMenuItem;

import common.commands.AbstractCommand;
import game.map.MapObject;
import game.map.editor.MapEditor;
import game.map.tree.MapObjectTreeModel;

public class HideObjectTree extends AbstractCommand
{
	private final List<MapObject> objs;
	private final boolean[] wasHidden;

	private final Consumer<Boolean> setter;
	private final JCheckBoxMenuItem checkBox;
	private final boolean hiding;

	public HideObjectTree(String name, MapObjectTreeModel<?> tree, JCheckBoxMenuItem checkBox, Consumer<Boolean> setter, boolean hide)
	{
		super(hide ? "Hide " + name : "Show " + name);
		objs = new ArrayList<>(128);

		for (MapObject obj : tree)
			objs.add(obj);

		wasHidden = new boolean[objs.size()];
		for (int i = 0; i < objs.size(); i++)
			wasHidden[i] = objs.get(i).hidden;

		this.setter = setter;
		this.checkBox = checkBox;
		this.hiding = hide;
	}

	@Override
	public boolean modifiesData()
	{
		return false;
	}

	@Override
	public void exec()
	{
		super.exec();

		for (MapObject obj : objs)
			obj.hidden = hiding;

		setter.accept(!hiding);
		checkBox.setSelected(!hiding);

		MapEditor editor = MapEditor.instance();
		editor.gui.repaintObjectPanel();
	}

	@Override
	public void undo()
	{
		super.undo();

		for (int i = 0; i < objs.size(); i++)
			objs.get(i).hidden = wasHidden[i];

		setter.accept(hiding);
		checkBox.setSelected(hiding);

		MapEditor editor = MapEditor.instance();
		editor.gui.repaintObjectPanel();
	}
}
