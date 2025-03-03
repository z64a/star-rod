package game.sprite.editor.commands;

import java.util.function.Consumer;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteRaster;
import game.sprite.editor.RastersList;

public class SelectRaster extends AbstractCommand
{
	private final Sprite sprite;
	private final RastersList list;
	private final SpriteRaster prev;
	private final SpriteRaster next;
	private final Consumer<SpriteRaster> callback;

	public SelectRaster(RastersList list, Sprite sprite, SpriteRaster img, Consumer<SpriteRaster> callback)
	{
		super("Select Raster");

		this.list = list;
		this.sprite = sprite;
		this.callback = callback;

		this.next = img;
		this.prev = sprite.selectedRaster;
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
		list.ignoreChanges.increment();
		list.setSelectedValue(next, true);
		list.ignoreChanges.decrement();

		sprite.selectedRaster = next;
		callback.accept(next);
	}

	@Override
	public void undo()
	{
		super.undo();

		// force list selection to update, but suppress generating a new command
		list.ignoreChanges.increment();
		list.setSelectedValue(prev, true);
		list.ignoreChanges.decrement();

		sprite.selectedRaster = prev;
		callback.accept(prev);
	}
}
