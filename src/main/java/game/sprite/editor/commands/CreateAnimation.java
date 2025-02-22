package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;

public class CreateAnimation extends AbstractCommand
{
	private final Sprite sprite;
	private final SpriteAnimation anim;
	private final int pos;

	public CreateAnimation(String name, Sprite sprite, SpriteAnimation anim)
	{
		super(name);
		this.sprite = sprite;
		this.anim = anim;
		pos = sprite.animations.size();
	}

	public CreateAnimation(String name, Sprite sprite, SpriteAnimation anim, int pos)
	{
		super(name);
		this.sprite = sprite;
		this.anim = anim;
		this.pos = pos;
	}

	@Override
	public void exec()
	{
		super.exec();
		sprite.animations.add(pos, anim);
		sprite.recalculateIndices();
	}

	@Override
	public void undo()
	{
		super.undo();
		sprite.animations.remove(pos);
		sprite.recalculateIndices();
	}
}
