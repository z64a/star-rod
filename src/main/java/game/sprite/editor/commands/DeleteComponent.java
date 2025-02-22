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

	@Override
	public void exec()
	{
		super.exec();
		anim.components.remove(pos);
		anim.parentSprite.recalculateIndices();
	}

	@Override
	public void undo()
	{
		super.undo();
		anim.components.add(pos, comp);
		anim.parentSprite.recalculateIndices();
	}
}
