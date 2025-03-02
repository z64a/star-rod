package game.sprite.editor.commands;

import java.awt.Component;
import java.util.function.Consumer;

import common.commands.AbstractCommand;
import game.sprite.ImgAsset;
import game.sprite.ImgRef;

public class BindImgAsset extends AbstractCommand
{
	private final Component ui;
	private final Consumer<ImgRef> callback;

	private final ImgRef ref;
	private final ImgAsset next;
	private final ImgAsset prev;

	public BindImgAsset(Component ui, ImgRef ref, ImgAsset asset, Consumer<ImgRef> callback)
	{
		super("Bind Raster");

		this.ui = ui;
		this.callback = callback;

		this.ref = ref;
		this.next = asset;
		this.prev = ref.asset;
	}

	@Override
	public void exec()
	{
		super.exec();

		ref.assignAsset(next);
		callback.accept(ref);
		ui.repaint();
	}

	@Override
	public void undo()
	{
		super.undo();

		ref.assignAsset(prev);
		callback.accept(ref);
		ui.repaint();
	}
}
