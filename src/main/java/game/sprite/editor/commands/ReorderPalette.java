package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.sprite.editor.PalettesList;

public class ReorderPalette extends AbstractCommand
{
	private final PalettesList list;
	private final Sprite sprite;
	private final SpritePalette pal;
	private final int prev;
	private final int next;

	public ReorderPalette(PalettesList list, SpritePalette pal, int pos)
	{
		super("Move Palette");

		this.list = list;
		this.pal = pal;
		this.sprite = pal.parentSprite;
		this.prev = sprite.palettes.indexOf(pal);
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

		list.ignoreChanges.increment();
		sprite.palettes.removeElement(pal);
		sprite.palettes.insertElementAt(pal, next);
		list.setSelectedValue(pal, true);
		list.ignoreChanges.decrement();

		sprite.reindex();
		sprite.incrementModified();
	}

	@Override
	public void undo()
	{
		super.undo();

		list.ignoreChanges.increment();
		sprite.palettes.removeElement(pal);
		sprite.palettes.insertElementAt(pal, prev);
		list.setSelectedValue(pal, true);
		list.ignoreChanges.decrement();

		sprite.reindex();
		sprite.decrementModified();
	}
}
