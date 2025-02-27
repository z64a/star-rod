package game.sprite;

import game.sprite.editor.SpriteAssetCollection;

public class SpriteRaster
{
	private final Sprite spr;

	public final ImgRef front;
	public final ImgRef back;

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
		this.spr = spr;
		front = new ImgRef();
		back = new ImgRef();
	}

	public SpriteRaster(SpriteRaster original)
	{
		this(original.spr);
		name = original.name;
		front.copy(original.front);
		back.copy(original.back);

		specialWidth = original.specialWidth;
		specialHeight = original.specialHeight;

		loadEditorImages();
	}

	public SpriteRaster copy()
	{
		return new SpriteRaster(this);
	}

	public Sprite getSprite()
	{
		return spr;
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

	public void loadEditorImages()
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
			for (SpriteRaster other : spr.rasters) {
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
				iteration++;
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

	public boolean hasMissing()
	{
		return !(front.resolved && back.resolved);
	}
}
