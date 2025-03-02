package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteRaster;
import game.sprite.editor.RastersList;

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

		list.ignoreSelectionChange = true;
		sprite.rasters.removeElement(img);
		sprite.rasters.insertElementAt(img, next);
		list.setSelectedValue(img, true);
		list.ignoreSelectionChange = false;

		sprite.revalidate();
	}

	@Override
	public void undo()
	{
		super.undo();

		list.ignoreSelectionChange = true;
		sprite.rasters.removeElement(img);
		sprite.rasters.insertElementAt(img, prev);
		list.setSelectedValue(img, true);
		list.ignoreSelectionChange = false;

		sprite.revalidate();
	}
}
