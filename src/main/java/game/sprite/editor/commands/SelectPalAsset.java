package game.sprite.editor.commands;

import java.util.function.Consumer;

import common.commands.AbstractCommand;
import game.sprite.PalAsset;
import game.sprite.Sprite;
import game.sprite.editor.AssetList;

public class SelectPalAsset extends AbstractCommand
{
	private final Sprite sprite;
	private final AssetList<PalAsset> list;
	private final PalAsset prev;
	private final PalAsset next;
	private final Consumer<PalAsset> callback;

	public SelectPalAsset(AssetList<PalAsset> list, Sprite sprite, PalAsset pal, Consumer<PalAsset> callback)
	{
		super("Select Palette Asset");

		this.list = list;
		this.sprite = sprite;
		this.callback = callback;

		this.next = pal;
		this.prev = sprite.selectedPalAsset;
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

		sprite.selectedPalAsset = next;
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

		sprite.selectedPalAsset = prev;
		callback.accept(prev);
	}
}
