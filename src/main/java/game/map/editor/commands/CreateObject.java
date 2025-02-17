package game.map.editor.commands;

import common.commands.AbstractCommand;
import game.map.Map;
import game.map.MapObject;
import game.map.editor.MapEditor;

public class CreateObject extends AbstractCommand
{
	private final MapObject obj;

	public CreateObject(MapObject obj)
	{
		super("Create Object");
		this.obj = obj;
		obj.initialize();
	}

	@Override
	public void exec()
	{
		super.exec();

		MapEditor editor = MapEditor.instance();

		editor.map.create(obj);
		editor.selectionManager.createObject(obj);

		Map.validateObjectData(editor.map);
	}

	@Override
	public void undo()
	{
		super.undo();

		MapEditor editor = MapEditor.instance();

		editor.map.remove(obj);
		editor.selectionManager.deleteObject(obj);

		Map.validateObjectData(editor.map);
	}
}
