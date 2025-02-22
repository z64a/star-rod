package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.SpriteList;

public class SelectSprite extends AbstractCommand
{
	private final SpriteList list;
	private final int prevID;
	private final int nextID;

	public SelectSprite(SpriteList list, int id)
	{
		super("Select Sprite");
		this.list = list;
		this.nextID = id;

		SpriteEditor editor = SpriteEditor.instance();
		this.prevID = editor.getSpriteID();
	}

	@Override
	public boolean shouldExec()
	{
		return prevID != nextID;
	}

	@Override
	public void exec()
	{
		super.exec();

		// force list selection to update, but suppress generating a new command
		list.ignoreSelectionChange = true;
		list.setSelectedID(nextID);
		list.ignoreSelectionChange = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setSprite(nextID, false);
	}

	@Override
	public void undo()
	{
		super.undo();

		// force list selection to update, but suppress generating a new command
		list.ignoreSelectionChange = true;
		list.setSelectedID(prevID);
		list.ignoreSelectionChange = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setSprite(prevID, false);
	}
}
