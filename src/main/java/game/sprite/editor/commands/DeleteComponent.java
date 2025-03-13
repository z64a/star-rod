package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;

public class DeleteComponent extends AbstractCommand
{
	private final SpriteAnimation anim;
	private final SpriteComponent comp;
	private final int pos;

	public DeleteComponent(SpriteAnimation anim, int pos)
	{
		super("Delete Component");

		this.anim = anim;
		this.comp = anim.components.get(pos);
		this.pos = pos;
	}

	public DeleteComponent(SpriteAnimation anim, SpriteComponent comp)
	{
		super("Delete Component");

		this.anim = anim;
		this.comp = comp;
		this.pos = anim.components.indexOf(comp);
	}

	@Override
	public void exec()
	{
		super.exec();

		anim.components.remove(pos);
		comp.deleted = true;
		anim.parentSprite.reindex();
		anim.incrementModified();
	}

	@Override
	public void undo()
	{
		super.undo();

		anim.components.add(pos, comp);
		comp.deleted = false;
		anim.parentSprite.reindex();
		anim.decrementModified();
	}
}
