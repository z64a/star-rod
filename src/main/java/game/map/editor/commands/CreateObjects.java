package game.map.editor.commands;

import common.commands.AbstractCommand;
import game.map.Map;
import game.map.MapObject;
import game.map.editor.MapEditor;

public class CreateObjects extends AbstractCommand
{
	private final Iterable<? extends MapObject> objs;

	public CreateObjects(Iterable<? extends MapObject> objs)
	{
		super("Create Objects");
		this.objs = objs;
	}

	@Override
	public void exec()
	{
		super.exec();

		MapEditor editor = MapEditor.instance();

		for (MapObject obj : objs) {
			editor.map.create(obj);
			editor.selectionManager.createObject(obj);
		}

		Map.validateObjectData(editor.map);
	}

	@Override
	public void undo()
	{
		super.undo();

		MapEditor editor = MapEditor.instance();

		for (MapObject obj : objs) {
			editor.map.remove(obj);
			editor.selectionManager.deleteObject(obj);
		}

		Map.validateObjectData(editor.map);
	}
}
