package game.sprite;

import org.apache.commons.io.FilenameUtils;

import game.sprite.editor.SpriteAssetCollection;
import util.Logger;

public class ImgRef
{
	public final SpriteRaster parentRaster;

	public String filename;
	public ImgAsset asset;
	public SpritePalette pal;
	public boolean resolved;

	public ImgRef(SpriteRaster parentRaster)
	{
		this.parentRaster = parentRaster;

		filename = "";
		asset = null;
		pal = null;
		resolved = true;
	}

	public String getName()
	{
		if (filename.isBlank()) {
			return "";
		}
		else {
			return FilenameUtils.removeExtension(filename);
		}
	}

	public void setAll(ImgRef other)
	{
		this.filename = other.filename;
		this.asset = other.asset;
		this.pal = other.pal;
		this.resolved = other.resolved;
	}

	public void lookup(SpriteAssetCollection<ImgAsset> imgAssets)
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

		if (img == null) {
			filename = "";
		}
		else {
			filename = img.getName();
			img.boundPal = pal;
		}
	}

	public void assignPal(SpritePalette selectedPal)
	{
		pal = selectedPal;

		if (asset != null) {
			asset.boundPal = pal;
		}
	}
}
