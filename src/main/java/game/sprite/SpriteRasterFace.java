package game.sprite;

import org.apache.commons.io.FilenameUtils;

import game.sprite.editor.ImgPreview;
import game.sprite.editor.SpriteAssetCollection;
import util.Logger;

public class SpriteRasterFace
{
	public final SpriteRaster parentRaster;

	public String filename;
	public ImgAsset asset;
	public SpritePalette pal;
	public boolean resolved;

	public ImgPreview preview;

	public SpriteRasterFace(SpriteRaster parentRaster)
	{
		this.parentRaster = parentRaster;

		filename = "";
		asset = null;
		pal = null;
		resolved = true;

		preview = new ImgPreview();
	}

	public String getName()
	{
		if (filename.isBlank())
			return "";
		else
			return FilenameUtils.removeExtension(filename);
	}

	public void set(SpriteRasterFace other)
	{
		this.filename = other.filename;
		this.asset = other.asset;
		this.pal = other.pal;
		this.resolved = other.resolved;
		preview.set(other.preview);
	}

	public void loadEditorImages()
	{
		if (asset != null && pal != null)
			preview.load(asset, pal.asset);
		else
			preview.loadMissing();
	}

	public void loadEditorImages(PalAsset filter)
	{
		if (asset != null && pal != null && pal.asset == filter)
			preview.load(asset, filter);
		else
			preview.loadMissing();
	}

	public void resolve(SpriteAssetCollection<ImgAsset> imgAssets)
	{
		if (filename.isBlank()) {
			asset = null;
			resolved = true;
		}
		else {
			asset = imgAssets.get(filename);
			resolved = (asset != null);
			if (!resolved)
				Logger.logWarning("Can't find raster: " + filename);
		}
	}

	public void assignAsset(ImgAsset img)
	{
		asset = img;
		resolved = true;

		filename = (img == null) ? "" : img.getName();
	}

	public void assignPal(SpritePalette selectedPal)
	{
		pal = selectedPal;
	}
}
