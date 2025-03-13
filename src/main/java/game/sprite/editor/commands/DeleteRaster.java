package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteRaster;
import game.sprite.editor.SpriteEditor;

public class DeleteRaster extends AbstractCommand
{
	private final Sprite sprite;
	private final SpriteRaster img;
	private final int pos;

	public DeleteRaster(Sprite sprite, int pos)
	{
		super("Delete Raster");

		this.sprite = sprite;
		this.img = sprite.rasters.get(pos);
		this.pos = pos;
	}

	@Override
	public void exec()
	{
		super.exec();

		SpriteEditor.instance().imgBoxRegistry.lockBoxes();

		sprite.rasters.remove(pos);
		img.deleted = true;
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

		sprite.rasters.add(pos, img);
		img.deleted = false;
		sprite.reindex();
		sprite.decrementModified();

		SpriteEditor.instance().imgBoxRegistry.updateModels(true);
		SpriteEditor.instance().imgBoxRegistry.unlockBoxes();
	}
}
