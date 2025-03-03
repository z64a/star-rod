package game.sprite;

import static game.texture.TileFormat.CI_4;

import java.awt.Color;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import assets.AssetHandle;
import assets.AssetManager;
import game.texture.Palette;
import game.texture.Tile;

public class PalAsset implements GLResource
{
	private AssetHandle source;
	private final Tile sourceImg;

	public final Palette pal;

	// remember colors before commands which adjust them are applied
	public final Color[] savedColors = new Color[16];

	public transient boolean dirty; // needs reupload to GPU
	public transient boolean modified;

	public PalAsset(AssetHandle ah) throws IOException
	{
		source = ah;
		sourceImg = Tile.load(source, CI_4);
		pal = sourceImg.palette;
	}

	public void stashColors()
	{
		for (int i = 0; i < 16; i++) {
			savedColors[i] = pal.getColor(i);
		}
	}

	public void save() throws IOException
	{
		source = AssetManager.getTopLevel(source);
		sourceImg.savePNG(source.getAbsolutePath());
	}

	public String getFilename()
	{
		return source.getName();
	}

	@Override
	public String toString()
	{
		return FilenameUtils.removeExtension(source.getName());
	}

	@Override
	public void glLoad()
	{
		pal.glLoad();
	}

	@Override
	public void glDelete()
	{
		pal.glDelete();
	}
}
