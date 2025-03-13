package game.sprite.editor.commands;

import java.awt.Component;

import common.commands.AbstractCommand;
import game.sprite.SpriteComponent;

public class RenameComponent extends AbstractCommand
{
	private final Component ui;
	private final SpriteComponent comp;
	private final String newName;
	private final String oldName;

	public RenameComponent(Component ui, SpriteComponent comp, String newName)
	{
		super("Rename Component");

		this.ui = ui;
		this.comp = comp;
		this.newName = newName;
		this.oldName = comp.name;
	}

	@Override
	public void exec()
	{
		super.exec();

		comp.name = newName;
		comp.incrementModified();

		ui.repaint();
	}

	@Override
	public void undo()
	{
		super.undo();

		comp.name = oldName;
		comp.decrementModified();

		ui.repaint();
	}
}
