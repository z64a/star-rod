package game.sprite;

import util.xml.XmlKey;

public enum SpriteKey implements XmlKey
{
	// @formatter:off
	ATTR_AUTHOR			("author"),
	ATTR_ID				("id"),
	ATTR_SOURCE			("src"),
	ATTR_BACK			("back"),
	ATTR_BACK_PAL		("backPalette"),

	TAG_SPRITE			("SpriteSheet"),
	ATTR_SPRITE_A		("a"), //deprecated
	ATTR_SPRITE_B		("b"), //deprecated
	ATTR_SPRITE_NUM_COMPONENTS	("maxComponents"),
	ATTR_SPRITE_NUM_VARIATIONS	("paletteGroups"),
	ATTR_SPRITE_VARIATIONS	("variations"),
	ATTR_SPRITE_HAS_BACK	("hasBack"),

	TAG_PALETTE_LIST	("PaletteList"),
	TAG_PALETTE			("Palette"),
	ATTR_FRONT_ONLY		("front_only"), //TODO fix casing

	TAG_RASTER_LIST		("RasterList"),
	TAG_RASTER			("Raster"),
	ATTR_PALETTE		("palette"),

	ATTR_SPECIAL_SIZE	("special"), //deprecated
	ATTR_OVERRIDE_SIZE	("backSize"),

	TAG_ANIMATION_LIST	("AnimationList"),
	TAG_ANIMATION		("Animation"),
	TAG_COMPONENT		("Component"),
	ATTR_X				("x"), //deprecated
	ATTR_Y				("y"), //deprecated
	ATTR_Z				("z"), //deprecated
	ATTR_OFFSET			("xyz"),
	ATTR_SEQ			("seq"),

	TAG_COMMAND			("Command"),
	ATTR_VAL			("val"),

	TAG_LABEL			("Label"),
	ATTR_NAME			("name"),
	ATTR_POS			("pos"),

	TAG_CMD_LABEL		("Label"),
	TAG_CMD_WAIT		("Wait"),
	TAG_CMD_GOTO		("Goto"),
	TAG_CMD_LOOP		("Loop"),
	TAG_CMD_SET_IMG		("SetRaster"),
	TAG_CMD_SET_PAL		("SetPalette"),
	TAG_CMD_SET_PAR		("SetParent"),
	TAG_CMD_SET_POS		("SetPos"),
	TAG_CMD_SET_ROT		("SetRot"),
	TAG_CMD_SET_SCALE	("SetScale"),
	TAG_CMD_SET_NOTIFY	("SetNotify"),
	TAG_CMD_SET_UNK		("SetUnknown"),
	TAG_CMD_KEYFRAME	("Keyframe"),

	// attributes for decomp-style command tags
	ATTR_DURATION		("duration"),
	ATTR_DEST			("dest"),
	ATTR_FLAG			("flag"),
	ATTR_XYZ			("xyz"),
	ATTR_MODE			("mode"),
	ATTR_PERCENT		("percent"),
	ATTR_COUNT			("count"),
	ATTR_VALUE			("value"),
	ATTR_INDEX			("index"),

	ATTR_KEYFRAMES		("keyframes"),
	ATTR_KF_IMG			("img"),
	ATTR_KF_PAL			("pal"),
	ATTR_KF_POS			("pos"),
	ATTR_KF_ROT			("rot"),
	ATTR_KF_SCALE		("scale");
	// @formatter:on

	private final String key;

	private SpriteKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
