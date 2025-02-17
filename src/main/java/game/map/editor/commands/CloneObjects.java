package game.map.editor.commands;

import java.util.ArrayList;

import common.commands.AbstractCommand;
import game.map.MapObject;
import game.map.editor.MapEditor;
import game.map.editor.selection.Selection;

public class CloneObjects extends AbstractCommand
{
	private ArrayList<MapObject> copies;
	private ArrayList<MapObject> originals;

	public CloneObjects(Selection<MapObject> selection)
	{
		super("Clone Selection");
		copies = new ArrayList<>();
		originals = new ArrayList<>();

		for (MapObject obj : selection.selectableList) {
			originals.add(obj);

			if (obj.allowsCopy())
				copies.add(obj.deepCopy());
		}
	}

	@Override
	public boolean shouldExec()
	{
		return copies.size() > 0;
	}

	@Override
	public void exec()
	{
		super.exec();

		MapEditor editor = MapEditor.instance();
		editor.selectionManager.clearObjectSelection();

		for (MapObject obj : copies) {
			editor.map.create(obj);
			editor.selectionManager.createObject(obj);
		}
	}

	@Override
	public void undo()
	{
		super.undo();

		MapEditor editor = MapEditor.instance();
		editor.selectionManager.clearObjectSelection();

		for (MapObject obj : copies) {
			editor.map.remove(obj);
			editor.selectionManager.deleteObject(obj);
		}

		for (MapObject obj : originals)
			editor.selectionManager.selectObject(obj);
	}
}
