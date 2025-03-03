package game.sprite;

import static game.texture.TileFormat.CI_4;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;

import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import assets.AssetHandle;
import assets.AssetManager;
import game.sprite.editor.ImgPreview;
import game.texture.Palette;
import game.texture.Tile;

public class ImgAsset implements GLResource
{
	// source is not final because it updates upon saving
	private AssetHandle source;

	public final Tile img;
	public final ImgPreview preview;

	// members used to lay out the rasters tab sprite atlas
	public transient int atlasRow, atlasX, atlasY;
	public transient boolean inUse;

	public ImgAsset(AssetHandle ah) throws IOException
	{
		source = ah;
		img = Tile.load(ah, CI_4);
		preview = new ImgPreview();
	}

	public void save() throws IOException
	{
		//TODO perhaps some way to check for edited palettes?
		source = AssetManager.getTopLevel(source);
		img.savePNG(source.getAbsolutePath());
	}

	public String getFilename()
	{
		return source.getName();
	}

	public String getName()
	{
		return FilenameUtils.removeExtension(source.getName());
	}

	@Override
	public String toString()
	{
		return getName();
	}

	//FIXME remove?
	public Palette getPalette()
	{
		return img.palette;
	}

	public void loadEditorImages()
	{
		preview.load(img, img.palette);
	}

	@Override
	public void glLoad()
	{
		if (img != null) {
			img.glLoad(GL_CLAMP_TO_BORDER, GL_CLAMP_TO_BORDER, false);
			img.palette.glLoad();
		}
	}

	@Override
	public void glDelete()
	{
		if (img != null) {
			img.glDelete();
			img.palette.glDelete();
		}
	}
}
