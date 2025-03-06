package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpritePalette;

public class DeletePalette extends AbstractCommand
{
	private final Sprite sprite;
	private final SpritePalette pal;
	private final int pos;

	public DeletePalette(Sprite sprite, int pos)
	{
		super("Delete Palette");

		this.sprite = sprite;
		this.pal = sprite.palettes.get(pos);
		this.pos = pos;
	}

	@Override
	public void exec()
	{
		super.exec();

		sprite.palettes.remove(pos);
		pal.deleted = true;
		sprite.reindex();
		sprite.incrementModified();
	}

	@Override
	public void undo()
	{
		super.undo();

		sprite.palettes.add(pos, pal);
		pal.deleted = false;
		sprite.reindex();
		sprite.decrementModified();
	}
}
