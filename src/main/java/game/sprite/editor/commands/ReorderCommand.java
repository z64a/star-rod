package game.sprite.editor.commands;

import javax.swing.DefaultListModel;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElementsList;

public class ReorderCommand extends AbstractCommand
{
	private final AnimElementsList<AnimElement> list;
	private final DefaultListModel<AnimElement> model;
	private final AnimElement cmd;
	private final int prev;
	private final int next;

	@SuppressWarnings("unchecked")
	public ReorderCommand(AnimElementsList<? extends AnimElement> list, AnimElement cmd, int pos)
	{
		super("Move " + (cmd == null ? null : cmd.getName()));

		this.list = (AnimElementsList<AnimElement>) list;
		this.cmd = cmd;
		this.model = this.list.getDefaultModel();
		this.prev = model.indexOf(cmd);
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
		model.removeElement(cmd);
		model.insertElementAt(cmd, next);
		list.setSelectedValue(cmd, true);
		list.ignoreSelectionChange = false;

		cmd.ownerComp.calculateTiming();
	}

	@Override
	public void undo()
	{
		super.undo();

		list.ignoreSelectionChange = true;
		model.removeElement(cmd);
		model.insertElementAt(cmd, prev);
		list.setSelectedValue(cmd, true);
		list.ignoreSelectionChange = false;

		cmd.ownerComp.calculateTiming();
	}
}
