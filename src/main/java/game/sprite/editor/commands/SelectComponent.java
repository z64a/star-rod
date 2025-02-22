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
	public void exec()
	{
		super.exec();

		// force list selection to update, but suppress generating a new command
		list.ignoreSelectionChange = true;
		list.setSelectedValue(next, true);
		list.ignoreSelectionChange = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setComponent(next);
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
		editor.setComponent(prev);
	}
}
