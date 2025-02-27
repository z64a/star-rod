package game.sprite;

import java.awt.Color;
import java.io.File;

import game.sprite.SpriteLoader.Indexable;
import game.texture.Palette;

/**
 * Represents an entry from a sprite's palette list
 */
public final class SpritePalette implements Indexable<SpritePalette>
{
	private final Sprite spr;

	public String name = "";

	public String filename = ""; // soft link to PalAsset used only for serialization/deserialization
	public PalAsset asset; // active link to PalAsset during editor runtime

	public boolean frontOnly = false;

	// editor fields
	protected transient int listIndex;
	public transient boolean dirty; // needs reupload to GPU
	public transient boolean modified;

	// remember colors before commands which adjust them are applied
	public final Color[] savedColors = new Color[16];

	public transient boolean deleted;
	public transient boolean hasError;

	public SpritePalette(Sprite spr)
	{
		this.spr = spr;
	}

	public SpritePalette(SpritePalette other)
	{
		this.spr = other.spr;
		this.name = other.name;
		this.asset = other.asset;
		this.frontOnly = other.frontOnly;
	}

	public SpritePalette copy()
	{
		return new SpritePalette(this);
	}

	public Sprite getSprite()
	{
		return spr;
	}

	public void stashColors()
	{
		for (int i = 0; i < 16; i++) {
			savedColors[i] = asset.pal.getColor(i);
		}
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
			for (SpritePalette other : spr.palettes) {
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

	public void saveAs(File out)
	{
		//TODO
		System.out.println(out.getAbsolutePath());
	}
}
