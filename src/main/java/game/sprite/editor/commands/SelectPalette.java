package game.sprite.editor.commands;

import java.util.function.Consumer;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.sprite.editor.PalettesList;

public class SelectPalette extends AbstractCommand
{
	private final Sprite sprite;
	private final PalettesList list;
	private final SpritePalette prev;
	private final SpritePalette next;
	private final Consumer<SpritePalette> callback;

	public SelectPalette(PalettesList list, Sprite sprite, SpritePalette pal, Consumer<SpritePalette> callback)
	{
		super("Select Palette");

		this.list = list;
		this.sprite = sprite;
		this.callback = callback;

		this.next = pal;
		this.prev = sprite.selectedPalette;
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

		sprite.selectedPalette = next;
		callback.accept(next);
	}

	@Override
	public void undo()
	{
		super.undo();

		// force list selection to update, but suppress generating a new command
		list.ignoreSelectionChange = true;
		list.setSelectedValue(prev, true);
		list.ignoreSelectionChange = false;

		sprite.selectedPalette = prev;
		callback.accept(prev);
	}
}
