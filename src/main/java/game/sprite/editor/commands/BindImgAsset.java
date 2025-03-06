package game.sprite.editor.commands;

import java.awt.Component;
import java.util.function.Consumer;

import common.commands.AbstractCommand;
import game.sprite.ImgAsset;
import game.sprite.SpriteRasterFace;

public class BindImgAsset extends AbstractCommand
{
	private final Component ui;
	private final Consumer<SpriteRasterFace> callback;

	private final SpriteRasterFace face;
	private final ImgAsset next;
	private final ImgAsset prev;

	public BindImgAsset(Component ui, SpriteRasterFace face, ImgAsset asset, Consumer<SpriteRasterFace> callback)
	{
		super("Bind Raster");

		this.ui = ui;
		this.callback = callback;

		this.face = face;
		this.next = asset;
		this.prev = face.asset;
	}

	@Override
	public void exec()
	{
		super.exec();

		face.assignAsset(next);
		face.loadEditorImages();
		face.parentRaster.incrementModified();

		callback.accept(face);
		ui.repaint();
	}

	@Override
	public void undo()
	{
		super.undo();

		face.assignAsset(prev);
		face.loadEditorImages();
		face.parentRaster.decrementModified();

		callback.accept(face);
		ui.repaint();
	}
}
