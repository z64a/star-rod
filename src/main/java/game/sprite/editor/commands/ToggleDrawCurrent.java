package game.sprite.editor.commands;

import javax.swing.JCheckBox;

import common.commands.AbstractCommand;

public class ToggleDrawCurrent extends AbstractCommand
{
	private final JCheckBox checkbox;
	private final boolean prev;
	private final boolean next;

	public ToggleDrawCurrent(JCheckBox checkbox)
	{
		super("Toggle Draw Current Only");

		this.checkbox = checkbox;

		this.next = checkbox.isSelected();
		this.prev = !next;
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

		checkbox.setSelected(next);
	}

	@Override
	public void undo()
	{
		super.undo();

		checkbox.setSelected(prev);
	}
}
