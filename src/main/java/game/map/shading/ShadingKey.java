package game.map.shading;

import util.xml.XmlKey;

public enum ShadingKey implements XmlKey
{
	// @formatter:off
	TAG_SPRITE_SHADING	("SpriteShading"),

	TAG_GROUP			("ShadingGroup"),

	TAG_PROFILE			("ShadingProfile"),
	ATTR_PROFILE_NAME		("name"),
	ATTR_PROFILE_VANILLA	("protected"),
	ATTR_PROFILE_AMBIENT	("ambient"),
	ATTR_PROFILE_POWER		("power"),

	TAG_LIGHT			("PointLight"),
	ATTR_LIGHT_FLAGS		("flags"),
	ATTR_LIGHT_POS			("pos"),
	ATTR_LIGHT_COLOR		("color"),
	ATTR_LIGHT_COEFFICIENT	("coef"),
	ATTR_LIGHT_UNK			("unk");
	// @formatter:on

	private final String key;

	private ShadingKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
