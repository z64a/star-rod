package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteRaster;
import game.sprite.editor.RastersList;
import game.sprite.editor.SpriteEditor;

public class ReorderRaster extends AbstractCommand
{
	private final RastersList list;
	private final Sprite sprite;
	private final SpriteRaster img;
	private final int prev;
	private final int next;

	public ReorderRaster(RastersList list, SpriteRaster img, int pos)
	{
		super("Move Raster");

		this.list = list;
		this.img = img;
		this.sprite = img.parentSprite;
		this.prev = sprite.rasters.indexOf(img);
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

		SpriteEditor.instance().imgBoxRegistry.lockBoxes();

		list.ignoreChanges.increment();
		sprite.rasters.removeElement(img);
		sprite.rasters.insertElementAt(img, next);
		list.setSelectedValue(img, true);
		list.ignoreChanges.decrement();

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

		list.ignoreChanges.increment();
		sprite.rasters.removeElement(img);
		sprite.rasters.insertElementAt(img, prev);
		list.setSelectedValue(img, true);
		list.ignoreChanges.decrement();

		sprite.reindex();
		sprite.decrementModified();

		SpriteEditor.instance().imgBoxRegistry.updateModels(true);
		SpriteEditor.instance().imgBoxRegistry.unlockBoxes();
	}
}
