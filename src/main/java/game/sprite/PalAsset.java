package game.sprite;

import static game.texture.TileFormat.CI_4;

import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import assets.AssetHandle;
import assets.AssetManager;
import game.texture.Palette;
import game.texture.Tile;

public class PalAsset
{
	private AssetHandle source;
	private final Tile sourceImg;

	public final Palette pal;

	public PalAsset(AssetHandle ah) throws IOException
	{
		source = ah;
		sourceImg = Tile.load(source, CI_4);
		pal = sourceImg.palette;
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
}
