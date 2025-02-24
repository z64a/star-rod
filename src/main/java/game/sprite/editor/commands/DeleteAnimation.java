package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;

public class DeleteAnimation extends AbstractCommand
{
	private final Sprite sprite;
	private final SpriteAnimation anim;
	private final int pos;

	public DeleteAnimation(Sprite sprite, int pos)
	{
		super("Delete Animation");

		this.sprite = sprite;
		this.anim = sprite.animations.get(pos);
		this.pos = pos;
	}

	@Override
	public void exec()
	{
		super.exec();
		sprite.animations.remove(pos);
		anim.deleted = true;
		sprite.revalidate();
	}

	@Override
	public void undo()
	{
		super.undo();
		sprite.animations.add(pos, anim);
		anim.deleted = false;
		sprite.revalidate();
	}
}
