package game.sprite;

import game.sprite.editor.SpriteAssetCollection;

public class SpriteRaster
{
	public final Sprite parentSprite;

	public final ImgRef front;
	public final ImgRef back;

	public boolean independentBack;

	// keep track of these, but do not expose them to users
	public int specialWidth;
	public int specialHeight;

	// editor fields
	protected transient int listIndex; // required to compile Set Image commands
	public transient String name = "";

	public transient boolean deleted;
	public transient boolean hasError;

	public SpriteRaster(Sprite spr)
	{
		this.parentSprite = spr;
		front = new ImgRef(this);
		back = new ImgRef(this);
	}

	public SpriteRaster(SpriteRaster original)
	{
		this(original.parentSprite);
		name = original.name;
		front.setAll(original.front);
		back.setAll(original.back);
		this.independentBack = original.independentBack;

		specialWidth = original.specialWidth;
		specialHeight = original.specialHeight;
	}

	public SpriteRaster copy()
	{
		return new SpriteRaster(this);
	}

	public ImgAsset getFront()
	{
		return front.asset;
	}

	public ImgAsset getBack()
	{
		return back.asset;
	}

	public void bindRasters(SpriteAssetCollection<ImgAsset> imgAssets)
	{
		front.lookup(imgAssets);
		back.lookup(imgAssets);
	}

	public void reloadEditorImages()
	{
		if (front.asset != null)
			front.asset.loadEditorImages();

		if (back.asset != null)
			back.asset.loadEditorImages();
	}

	public String createUniqueName(String name)
	{
		String baseName = name;

		for (int iteration = 0; iteration < 256; iteration++) {
			boolean conflict = false;

			// compare to all other names
			for (SpriteRaster other : parentSprite.rasters) {
				if (other != this && other.name.equals(name)) {
					conflict = true;
					break;
				}
			}

			if (!conflict) {
				// name is valid
				return name;
			}
			else {
				// try next iteration
				name = baseName + "_" + iteration;
			}
		}

		// could not form a valid name
		return null;
	}

	@Override
	public String toString()
	{
		return name;
	}

	public int getIndex()
	{
		return listIndex;
	}
}
