package game.sprite.editor.commands;

import javax.swing.DefaultListModel;

import common.commands.AbstractCommand;
import game.sprite.SpriteComponent;
import game.sprite.editor.ComponentsList;

public class ReorderComponent extends AbstractCommand
{
	private final ComponentsList list;
	private final DefaultListModel<SpriteComponent> model;
	private final SpriteComponent comp;
	private final int prev;
	private final int next;

	public ReorderComponent(ComponentsList list, SpriteComponent comp, int pos)
	{
		super("Move Component");

		this.list = list;
		this.comp = comp;
		this.model = comp.parentAnimation.components;
		this.prev = model.indexOf(comp);
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

		list.ignoreChanges.increment();
		model.removeElement(comp);
		model.insertElementAt(comp, next);
		list.setSelectedValue(comp, true);
		list.ignoreChanges.decrement();

		comp.parentAnimation.parentSprite.reindex();
		comp.parentAnimation.incrementModified();
	}

	@Override
	public void undo()
	{
		super.undo();

		list.ignoreChanges.increment();
		model.removeElement(comp);
		model.insertElementAt(comp, prev);
		list.setSelectedValue(comp, true);
		list.ignoreChanges.decrement();

		comp.parentAnimation.parentSprite.reindex();
		comp.parentAnimation.decrementModified();
	}
}
