package game.map.editor.commands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import common.commands.AbstractCommand;
import game.map.MapObject;
import game.map.editor.MapEditor;
import game.map.tree.MapObjectNode;

public class HideObjects extends AbstractCommand
{
	private final ArrayList<MapObject> hideList;

	public HideObjects(List<MapObject> objs)
	{
		super("Hide Objects");

		Set<MapObject> hideSet = getSetWithDescendents(objs);
		hideList = new ArrayList<>(hideSet);
	}

	// Slightly modified from the method in MapObject
	// Model groups may have a mesh, in which case the MapObject implementation makes
	// it impossible to individually hide them. This implementation checks whether
	// an object has a mesh before adding its children to the Set.
	private static Set<MapObject> getSetWithDescendents(Iterable<MapObject> objs)
	{
		HashSet<MapObject> set = new HashSet<>();
		Stack<MapObjectNode<?>> nodes = new Stack<>();

		for (MapObject obj : objs) {
			if (obj.editorOnly())
				continue;

			nodes.push(obj.getNode());
			while (!nodes.isEmpty()) {
				MapObjectNode<?> node = nodes.pop();
				MapObject current = node.getUserObject();
				if (node.isRoot()) {
					for (int i = 0; i < node.getChildCount(); i++)
						nodes.push(node.getChildAt(i));
				}
				else if (!set.contains(current)) {
					set.add(current);

					if (!current.hasMesh()) // added
					{
						for (int i = 0; i < node.getChildCount(); i++)
							nodes.push(node.getChildAt(i));
					}
				}
			}
		}

		return set;
	}

	@Override
	public boolean shouldExec()
	{
		return hideList.iterator().hasNext();
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

		for (MapObject obj : hideList)
			obj.hidden = !obj.hidden;

		MapEditor editor = MapEditor.instance();
		editor.gui.repaintObjectPanel();
	}

	@Override
	public void undo()
	{
		super.undo();

		for (MapObject obj : hideList)
			obj.hidden = !obj.hidden;

		MapEditor editor = MapEditor.instance();
		editor.gui.repaintObjectPanel();
	}
}
