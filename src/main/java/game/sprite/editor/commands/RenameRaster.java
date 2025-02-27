package game.sprite.editor.commands;

import java.awt.Component;

import common.commands.AbstractCommand;
import game.sprite.SpriteRaster;

public class RenameRaster extends AbstractCommand
{
	private final Component ui;
	private final SpriteRaster img;
	private final String newName;
	private final String oldName;

	public RenameRaster(Component ui, SpriteRaster img, String newName)
	{
		super("Rename Palette");

		this.ui = ui;
		this.img = img;
		this.newName = newName;
		this.oldName = img.name;
	}

	@Override
	public void exec()
	{
		super.exec();

		img.name = newName;
		ui.repaint();
	}

	@Override
	public void undo()
	{
		super.undo();

		img.name = oldName;
		ui.repaint();
	}
}
