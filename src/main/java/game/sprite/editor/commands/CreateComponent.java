package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;

public class CreateComponent extends AbstractCommand
{
	private final SpriteAnimation anim;
	private final SpriteComponent newComp;
	private final int pos;

	public CreateComponent(String name, SpriteAnimation anim, SpriteComponent newComp)
	{
		super(name);
		this.anim = anim;
		this.newComp = newComp;
		pos = anim.components.size();
	}

	public CreateComponent(String name, SpriteAnimation anim, SpriteComponent newComp, int pos)
	{
		super(name);
		this.anim = anim;
		this.newComp = newComp;
		this.pos = pos;
	}

	@Override
	public void exec()
	{
		super.exec();

		anim.components.add(pos, newComp);
		anim.parentSprite.reindex();
		anim.incrementModified();
	}

	@Override
	public void undo()
	{
		super.undo();

		anim.components.remove(pos);
		anim.parentSprite.reindex();
		anim.decrementModified();
	}
}
