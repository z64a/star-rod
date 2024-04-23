package game.sprite;

import java.util.LinkedHashMap;

public class SpriteRaster
{
	private final Sprite spr;

	public ImgRef front;
	public ImgRef back;

	// keep track of these, but do not expose them to users
	public int specialWidth;
	public int specialHeight;

	// editor fields
	protected transient int listIndex;
	public transient String name = "";

	//TODO
	public boolean deleted;

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

	public Sprite getSprite()
	{ return spr; }

	public ImgAsset getFront()
	{ return front.asset; }

	public ImgAsset getBack()
	{ return back.asset; }

	public void bindRasters(LinkedHashMap<String, ImgAsset> imgAssets)
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

	@Override
	public String toString()
	{
		return name;
	}

	public int getIndex()
	{ return listIndex; }

	public boolean hasMissing()
	{
		return !(front.resolved && back.resolved);
	}
}
