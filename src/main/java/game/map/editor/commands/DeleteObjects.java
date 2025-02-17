package game.map.editor.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import common.commands.AbstractCommand;
import game.map.MapObject;
import game.map.editor.MapEditor;
import game.map.editor.selection.Selection;

public class DeleteObjects extends AbstractCommand
{
	private final ArrayList<MapObject> deleteList;

	public DeleteObjects(Selection<MapObject> selection)
	{
		super("Delete Assorted Selection");

		Set<MapObject> deleteSet = MapObject.getSetWithDescendents(selection.selectableList);
		deleteList = new ArrayList<>(deleteSet);
		Collections.sort(deleteList, new MapObject.DeleteComparator());
	}

	@Override
	public boolean shouldExec()
	{
		return !deleteList.isEmpty();
	}

	@Override
	public void exec()
	{
		super.exec();

		MapEditor editor = MapEditor.instance();

		for (MapObject obj : deleteList) {
			editor.map.remove(obj);
			editor.selectionManager.deleteObject(obj);
		}
	}

	@Override
	public void undo()
	{
		super.undo();

		MapEditor editor = MapEditor.instance();

		for (int i = deleteList.size() - 1; i >= 0; i--) {
			MapObject obj = deleteList.get(i);
			editor.map.add(obj);
			editor.selectionManager.createObject(obj);
		}
	}
}
