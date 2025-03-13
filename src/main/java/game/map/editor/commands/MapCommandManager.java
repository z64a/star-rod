package game.map.editor.commands;

import common.commands.CommandManager;
import game.map.editor.MapEditor;

public class MapCommandManager extends CommandManager
{
	private final MapEditor editor;

	public MapCommandManager(MapEditor editor, int undoLimit)
	{
		super(undoLimit);

		this.editor = editor;
	}

	@Override
	public void onModified()
	{
		editor.map.modified = true;
	}
}
