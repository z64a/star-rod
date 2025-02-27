package game.sprite.editor.commands;

import java.awt.Component;

import common.commands.AbstractCommand;
import game.sprite.SpritePalette;

public class RenamePalette extends AbstractCommand
{
	private final Component ui;
	private final SpritePalette pal;
	private final String newName;
	private final String oldName;

	public RenamePalette(Component ui, SpritePalette pal, String newName)
	{
		super("Rename Palette");

		this.ui = ui;
		this.pal = pal;
		this.newName = newName;
		this.oldName = pal.name;
	}

	@Override
	public void exec()
	{
		super.exec();

		pal.name = newName;
		ui.repaint();
	}

	@Override
	public void undo()
	{
		super.undo();

		pal.name = oldName;
		ui.repaint();
	}
}
