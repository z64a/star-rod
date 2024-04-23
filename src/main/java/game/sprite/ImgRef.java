package game.sprite;

import java.util.LinkedHashMap;

import org.apache.commons.io.FilenameUtils;

public class ImgRef
{
	public String filename;
	public ImgAsset asset;
	public SpritePalette pal;
	public boolean resolved;

	public ImgRef()
	{
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

	public void copy(ImgRef other)
	{
		this.filename = other.filename;
		this.asset = other.asset;
		this.pal = other.pal;
		this.resolved = other.resolved;
	}

	public void lookup(LinkedHashMap<String, ImgAsset> imgAssets)
	{
		if (filename.isBlank()) {
			asset = null;
			resolved = true;
		}
		else {
			asset = imgAssets.get(filename);
			resolved = (asset != null);
		}
	}

	public void assignImg(ImgAsset img)
	{
		asset = img;
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
