package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.SpriteAnimation;
import game.sprite.editor.AnimationsList;
import game.sprite.editor.SpriteEditor;

public class SelectAnimation extends AbstractCommand
{
	private final AnimationsList list;
	private final SpriteAnimation prev;
	private final SpriteAnimation next;

	public SelectAnimation(AnimationsList list, SpriteAnimation anim)
	{
		super("Select Animation");
		this.list = list;
		this.next = anim;

		SpriteEditor editor = SpriteEditor.instance();
		this.prev = editor.getAnimation();
	}

	@Override
	public boolean shouldExec()
	{
		return prev != next;
	}

	@Override
	public void exec()
	{
		super.exec();

		// force list selection to update, but suppress generating a new command
		list.ignoreSelectionChange = true;
		list.setSelectedValue(next, true);
		list.ignoreSelectionChange = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setAnimation(next);
	}

	@Override
	public void undo()
	{
		super.undo();

		// force list selection to update, but suppress generating a new command
		list.ignoreSelectionChange = true;
		list.setSelectedValue(prev, true);
		list.ignoreSelectionChange = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setAnimation(prev);
	}
}
