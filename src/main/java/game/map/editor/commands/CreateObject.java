package game.map.editor.commands;

import game.map.Map;
import game.map.MapObject;

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

		editor.map.create(obj);
		editor.selectionManager.createObject(obj);

		Map.validateObjectData(editor.map);
	}

	@Override
	public void undo()
	{
		super.undo();

		editor.map.remove(obj);
		editor.selectionManager.deleteObject(obj);

		Map.validateObjectData(editor.map);
	}
}
