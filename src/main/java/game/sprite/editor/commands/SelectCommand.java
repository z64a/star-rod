package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElementsList;
import game.sprite.editor.animators.AnimationEditor;

public class SelectCommand extends AbstractCommand
{
	private final AnimationEditor parent;
	private final AnimElementsList<AnimElement> list;
	private final AnimElement prev;
	private final AnimElement next;

	@SuppressWarnings("unchecked")
	public SelectCommand(AnimElementsList<? extends AnimElement> list, AnimationEditor parent, AnimElement cmd)
	{
		super("Select " + (cmd == null ? null : cmd.getName()));

		this.parent = parent;
		this.list = (AnimElementsList<AnimElement>) list;
		this.next = cmd;
		this.prev = parent.getSelected();
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
		list.ignoreSelectionChange = true;
		list.setSelectedValue(next, true);
		list.ignoreSelectionChange = false;

		parent.setSelected(next);
	}

	@Override
	public void undo()
	{
		super.undo();

		// force list selection to update, but suppress generating a new command
		list.ignoreSelectionChange = true;
		list.setSelectedValue(prev, true);
		list.ignoreSelectionChange = false;

		parent.setSelected(prev);
	}
}
