package game.sprite.editor.commands;

import javax.swing.DefaultListModel;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElementsList;

public class CreateCommand extends AbstractCommand
{
	private final DefaultListModel<AnimElement> model;
	private final AnimElement cmd;
	private final int pos;

	@SuppressWarnings("unchecked")
	public CreateCommand(String name, AnimElementsList<?> list, AnimElement cmd)
	{
		super(name);

		this.model = (DefaultListModel<AnimElement>) list.getModel();
		this.cmd = cmd;
		this.pos = model.getSize();
	}

	@SuppressWarnings("unchecked")
	public CreateCommand(String name, AnimElementsList<?> list, AnimElement cmd, int pos)
	{
		super(name);

		this.model = (DefaultListModel<AnimElement>) list.getModel();
		this.cmd = cmd;
		this.pos = pos;
	}

	@Override
	public void exec()
	{
		super.exec();

		model.add(pos, cmd);
		cmd.owner.calculateTiming();
		cmd.owner.incrementModified();
	}

	@Override
	public void undo()
	{
		super.undo();

		model.remove(pos);
		cmd.owner.calculateTiming();
		cmd.owner.decrementModified();
	}
}
