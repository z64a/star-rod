package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.SpriteComponent;
import game.sprite.editor.ComponentsList;
import game.sprite.editor.SpriteEditor;

public class SelectComponent extends AbstractCommand
{
	private final ComponentsList list;
	private final SpriteComponent prev;
	private final SpriteComponent next;

	public SelectComponent(ComponentsList list, SpriteComponent comp)
	{
		super("Select Component");
		this.list = list;
		this.next = comp;

		SpriteEditor editor = SpriteEditor.instance();
		this.prev = editor.getComponent();
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
		list.ignoreChanges.increment();
		list.setSelectedValue(next, true);
		list.ignoreChanges.decrement();

		SpriteEditor editor = SpriteEditor.instance();
		editor.setComponent(next);
		editor.postEditableError(next);

		if (next != null)
			next.calculateTiming();
	}

	@Override
	public void undo()
	{
		super.undo();

		// force list selection to update, but suppress generating a new command
		list.ignoreChanges.increment();
		list.setSelectedValue(prev, true);
		list.ignoreChanges.decrement();

		SpriteEditor editor = SpriteEditor.instance();
		editor.setComponent(prev);
	}
}
