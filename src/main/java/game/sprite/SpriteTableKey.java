package game.sprite;

import util.xml.XmlKey;

public enum SpriteTableKey implements XmlKey
{
	// @formatter:off
	TAG_ROOT			("Names"),
	TAG_SPRITES			("Sprites"),
	TAG_RASTERS			("Rasters"),
	TAG_SPRITE			("Sprite"),
	ATTR_NAME			("name");
	// @formatter:on

	private final String key;

	private SpriteTableKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
