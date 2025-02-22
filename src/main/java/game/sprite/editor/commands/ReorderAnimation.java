package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.editor.AnimationsList;

public class ReorderAnimation extends AbstractCommand
{
	private final AnimationsList list;
	private final Sprite sprite;
	private final SpriteAnimation anim;
	private final int prev;
	private final int next;

	public ReorderAnimation(AnimationsList list, SpriteAnimation anim, int pos)
	{
		super("Move Animation");

		this.list = list;
		this.anim = anim;
		this.sprite = anim.parentSprite;
		this.prev = sprite.animations.indexOf(anim);
		this.next = pos;
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

		list.ignoreSelectionChange = true;
		sprite.animations.removeElement(anim);
		sprite.animations.insertElementAt(anim, next);
		list.setSelectedValue(anim, true);
		list.ignoreSelectionChange = false;

		anim.parentSprite.recalculateIndices();
	}

	@Override
	public void undo()
	{
		super.undo();

		list.ignoreSelectionChange = true;
		sprite.animations.removeElement(anim);
		sprite.animations.insertElementAt(anim, prev);
		list.setSelectedValue(anim, true);
		list.ignoreSelectionChange = false;

		anim.parentSprite.recalculateIndices();
	}
}
