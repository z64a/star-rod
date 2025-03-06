package game.sprite.editor.commands;

import java.awt.Component;

import common.commands.AbstractCommand;
import game.sprite.PalAsset;
import game.sprite.SpritePalette;

public class BindPalAsset extends AbstractCommand
{
	private final Component ui;
	private final SpritePalette pal;
	private final PalAsset next;
	private final PalAsset prev;

	public BindPalAsset(Component ui, SpritePalette pal, PalAsset asset)
	{
		super("Bind Palette");

		this.ui = ui;
		this.pal = pal;
		this.next = asset;
		this.prev = pal.asset;
	}

	@Override
	public void exec()
	{
		super.exec();

		pal.assignAsset(next);
		pal.incrementModified();

		ui.repaint();
	}

	@Override
	public void undo()
	{
		super.undo();

		pal.assignAsset(prev);
		pal.decrementModified();

		ui.repaint();
	}
}
