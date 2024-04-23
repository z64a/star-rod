package game.map.editor.commands;

import game.map.Map;
import game.map.MapObject;

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

		for (MapObject obj : objs) {
			editor.map.remove(obj);
			editor.selectionManager.deleteObject(obj);
		}

		Map.validateObjectData(editor.map);
	}
}
