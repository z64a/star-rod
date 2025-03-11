package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteRaster;
import game.sprite.editor.SpriteEditor;

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

		SpriteEditor.instance().imgBoxRegistry.lockBoxes();

		sprite.rasters.add(pos, img);
		sprite.reindex();
		sprite.incrementModified();

		SpriteEditor.instance().imgBoxRegistry.updateModels(true);
		SpriteEditor.instance().imgBoxRegistry.unlockBoxes();
	}

	@Override
	public void undo()
	{
		super.undo();

		SpriteEditor.instance().imgBoxRegistry.lockBoxes();

		sprite.rasters.remove(pos);
		sprite.reindex();
		sprite.decrementModified();

		SpriteEditor.instance().imgBoxRegistry.updateModels(true);
		SpriteEditor.instance().imgBoxRegistry.unlockBoxes();
	}
}
