package game.sprite.editor.commands;

import java.util.function.Consumer;

import common.commands.AbstractCommand;
import game.sprite.ImgAsset;
import game.sprite.Sprite;
import game.sprite.editor.AssetList;

public class SelectImgAsset extends AbstractCommand
{
	private final Sprite sprite;
	private final AssetList<ImgAsset> list;
	private final ImgAsset prev;
	private final ImgAsset next;
	private final Consumer<ImgAsset> callback;

	public SelectImgAsset(AssetList<ImgAsset> list, Sprite sprite, ImgAsset img, Consumer<ImgAsset> callback)
	{
		super("Select Raster Asset");

		this.list = list;
		this.sprite = sprite;
		this.callback = callback;

		this.next = img;
		this.prev = sprite.selectedImgAsset;
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

		sprite.selectedImgAsset = next;
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

		sprite.selectedImgAsset = prev;
		callback.accept(prev);
	}
}
