package game.sprite.editor.commands;

import javax.swing.DefaultListModel;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElementsList;

public class DeleteCommand extends AbstractCommand
{
	private final DefaultListModel<AnimElement> model;
	private final AnimElement cmd;
	private final int pos;

	@SuppressWarnings("unchecked")
	public DeleteCommand(AnimElementsList<?> list, int pos)
	{
		super("Delete Command");

		this.model = (DefaultListModel<AnimElement>) list.getModel();
		this.cmd = model.getElementAt(pos);

		this.pos = pos;
	}

	@Override
	public void exec()
	{
		super.exec();

		model.remove(pos);
		cmd.owner.calculateTiming();
		cmd.owner.incrementModified();
	}

	@Override
	public void undo()
	{
		super.undo();

		model.add(pos, cmd);
		cmd.owner.calculateTiming();
		cmd.owner.decrementModified();
	}
}
