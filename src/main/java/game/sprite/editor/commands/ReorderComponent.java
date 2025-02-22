package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.editor.ComponentsList;

public class ReorderComponent extends AbstractCommand
{
	private final ComponentsList list;
	private final SpriteAnimation anim;
	private final SpriteComponent comp;
	private final int prev;
	private final int next;

	public ReorderComponent(ComponentsList list, SpriteComponent comp, int pos)
	{
		super("Move Component");

		this.list = list;
		this.comp = comp;
		this.anim = comp.parentAnimation;
		this.prev = anim.components.indexOf(comp);
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
		anim.components.removeElement(comp);
		anim.components.insertElementAt(comp, next);
		list.setSelectedValue(comp, true);
		list.ignoreSelectionChange = false;

		anim.parentSprite.recalculateIndices();
	}

	@Override
	public void undo()
	{
		super.undo();

		list.ignoreSelectionChange = true;
		anim.components.removeElement(comp);
		anim.components.insertElementAt(comp, prev);
		list.setSelectedValue(comp, true);
		list.ignoreSelectionChange = false;

		anim.parentSprite.recalculateIndices();
	}
}
