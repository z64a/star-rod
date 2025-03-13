package game.sprite;

import java.util.List;

import game.sprite.SpriteLoader.Indexable;
import game.sprite.editor.Editable;
import game.texture.Palette;

/**
 * Represents an entry from a sprite's palette list
 */
public final class SpritePalette implements Indexable<SpritePalette>, Editable
{
	public final Sprite parentSprite;

	public String name = "";

	public String filename = ""; // soft link to PalAsset used only for serialization/deserialization
	public PalAsset asset; // active link to PalAsset during editor runtime

	public boolean frontOnly = false;

	// editor fields
	protected transient int listIndex;

	public transient boolean deleted;

	// used for accounting during cleanup actions
	public transient boolean inUse;

	public SpritePalette(Sprite spr)
	{
		this.parentSprite = spr;
	}

	public SpritePalette(SpritePalette other)
	{
		this.parentSprite = other.parentSprite;
		this.name = other.name;
		this.asset = other.asset;
		this.frontOnly = other.frontOnly;
	}

	public SpritePalette copy()
	{
		return new SpritePalette(this);
	}

	public void assignAsset(PalAsset asset)
	{
		this.asset = asset;
		this.filename = (asset == null) ? "" : asset.getFilename();
	}

	public String createUniqueName(String name)
	{
		String baseName = name;

		for (int iteration = 0; iteration < 256; iteration++) {
			boolean conflict = false;

			// compare to all other names
			for (SpritePalette other : parentSprite.palettes) {
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

	@Override
	@Deprecated //TODO remove
	public SpritePalette getObject()
	{
		return this;
	}

	@Override
	public int getIndex()
	{
		return listIndex;
	}

	public boolean hasPal()
	{
		return (asset != null);
	}

	public Palette getPal()
	{
		return (asset == null) ? null : asset.pal;
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
		if (asset != null)
			downstream.add(asset);
	}

	@Override
	public String checkErrorMsg()
	{
		if (deleted)
			return "Palette: in use while deleted";

		if (!hasPal())
			return "Palette: undefined asset";

		return null;
	}
}
