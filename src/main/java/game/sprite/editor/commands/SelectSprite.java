package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteLoader.SpriteMetadata;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.SpriteList;

public class SelectSprite extends AbstractCommand
{
	private final SpriteList list;
	private final SpriteMetadata prev;
	private final SpriteMetadata next;

	public SelectSprite(SpriteList list, SpriteMetadata next)
	{
		super("Select Sprite");
		this.list = list;
		this.next = next;

		SpriteEditor editor = SpriteEditor.instance();
		Sprite spr = editor.getSprite();
		prev = (spr != null) ? spr.metadata : null;
	}

	@Override
	public boolean shouldExec()
	{
		return prev != next;
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

		// force list selection to update, but suppress generating a new command
		list.ignoreSelectionChange = true;
		list.setSelected(next);
		list.ignoreSelectionChange = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setSprite(next, false);
	}

	@Override
	public void undo()
	{
		super.undo();

		// force list selection to update, but suppress generating a new command
		list.ignoreSelectionChange = true;
		list.setSelected(prev);
		list.ignoreSelectionChange = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setSprite(prev, false);
	}
}
