package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteRaster;

public class CreateRaster extends AbstractCommand
{
	private final Sprite sprite;
	private final SpriteRaster img;
	private final int pos;

	public CreateRaster(String name, Sprite sprite, SpriteRaster img)
	{
		super(name);
		this.sprite = sprite;
		this.img = img;
		pos = sprite.rasters.size();
	}

	public CreateRaster(String name, Sprite sprite, SpriteRaster img, int pos)
	{
		super(name);
		this.sprite = sprite;
		this.img = img;
		this.pos = pos;
	}

	@Override
	public void exec()
	{
		super.exec();

		sprite.rasters.add(pos, img);
		sprite.reindex();
		sprite.incrementModified();
	}

	@Override
	public void undo()
	{
		super.undo();

		sprite.rasters.remove(pos);
		sprite.reindex();
		sprite.decrementModified();
	}
}
