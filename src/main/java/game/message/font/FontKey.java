package game.message.font;

import util.xml.XmlKey;

public enum FontKey implements XmlKey
{
	// @formatter:off
	TAG_ROOT		("Fonts"),
	ATTR_NAME		("name"),

	TAG_FONT			("Font"),
	ATTR_UNK				("unk"),
	ATTR_LINE_HEIGHT		("lineHeight"),
	ATTR_SPACE_WIDTH		("fullspaceWidth"),
	ATTR_BASE_HEIGHT		("baseHeightOffset"),

	TAG_CHARSET			("CharSet"),
	ATTR_SIZE				("imgSize"),
	TAG_CHARACTER			("Character"),
	ATTR_WIDTH					("width");
	// @formatter:on

	private final String key;

	private FontKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
