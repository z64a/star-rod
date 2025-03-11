package game.sprite;

import java.util.List;

import game.sprite.editor.Editable;
import game.sprite.editor.SpriteAssetCollection;

public class SpriteRaster implements Editable
{
	public final Sprite parentSprite;

	public final SpriteRasterFace front;
	public final SpriteRasterFace back;

	public boolean hasIndependentBack;

	// keep track of these, but do not expose them to users
	public int specialWidth;
	public int specialHeight;

	// editor fields
	protected transient int listIndex; // required to compile Set Image commands
	public transient String name = "";

	public transient boolean deleted;

	// members used to lay out the palettes tab sprite atlas
	public transient int atlasRow, atlasX, atlasY;

	// used for accounting during cleanup actions
	public transient boolean inUse;

	public SpriteRaster(Sprite spr)
	{
		this.parentSprite = spr;
		front = new SpriteRasterFace(this);
		back = new SpriteRasterFace(this);
	}

	public SpriteRaster(SpriteRaster original)
	{
		this(original.parentSprite);
		name = original.name;
		front.set(original.front);
		back.set(original.back);
		this.hasIndependentBack = original.hasIndependentBack;

		specialWidth = original.specialWidth;
		specialHeight = original.specialHeight;
	}

	public SpriteRaster copy()
	{
		return new SpriteRaster(this);
	}

	public void bindRasters(SpriteAssetCollection<ImgAsset> imgAssets)
	{
		front.resolve(imgAssets);
		back.resolve(imgAssets);
	}

	public void loadEditorImages()
	{
		front.loadEditorImages();
		back.loadEditorImages();
	}

	public void loadEditorImages(PalAsset filter)
	{
		front.loadEditorImages(filter);
		back.loadEditorImages(filter);
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

	private final EditableData editableData = new EditableData(this);

	@Override
	public EditableData getEditableData()
	{
		return editableData;
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{
		downstream.add(front.pal);

		if (hasIndependentBack)
			downstream.add(back.pal);
	}

	@Override
	public String checkErrorMsg()
	{
		if (deleted)
			return "Raster: in use while deleted";

		if (front.asset == null)
			return "Raster: undefined asset for front face";

		if (hasIndependentBack && back.asset == null)
			return "Raster: undefined asset for back face";

		return null;
	}
}
