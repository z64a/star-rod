package game.sprite.editor.commands;

import common.commands.CommandManager;
import game.sprite.editor.SpriteEditor;

public class SpriteCommandManager extends CommandManager
{
	private final SpriteEditor editor;

	public SpriteCommandManager(SpriteEditor editor, int undoLimit)
	{
		super(undoLimit);

		this.editor = editor;
	}

	@Override
	public void onModified()
	{
		editor.modified = true;
	}
}
