package game.sprite;

import java.io.File;

import game.sprite.SpriteLoader.Indexable;
import game.texture.Palette;

public final class SpritePalette implements Indexable<SpritePalette>
{
	private final Sprite spr;

	public PalAsset asset;
	public String filename = "";
	public boolean frontOnly = false;

	public boolean disabled = false;

	// editor fields
	protected transient int listIndex;
	public transient boolean dirty; // needs reupload to GPU
	public transient boolean modified;

	public transient boolean deleted;
	public transient boolean hasError;

	public SpritePalette(Sprite spr)
	{
		this.spr = spr;
	}

	public SpritePalette(SpritePalette other)
	{
		this.spr = other.spr;
		this.asset = other.asset;
		this.filename = other.filename;
		this.frontOnly = other.frontOnly;
		this.disabled = other.disabled;
	}

	public Sprite getSprite()
	{
		return spr;
	}

	@Override
	@Deprecated //TODO remove
	public String toString()
	{
		return filename;
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
		System.out.println(out.getAbsolutePath());
	}

	public static SpritePalette createDummy(Sprite spr, PalAsset pa, String name)
	{
		SpritePalette sp = new SpritePalette(spr);
		sp.asset = pa;
		sp.filename = name;
		sp.disabled = true;
		return sp;
	}
}
