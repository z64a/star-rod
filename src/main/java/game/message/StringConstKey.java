package game.message;

import util.xml.XmlKey;

public enum StringConstKey implements XmlKey
{
	// @formatter:off
	TAG_ROOT		("Constants"),
	TAG_CONSTANT	("Constant"),
	ATTR_NAME		("name"),
	ATTR_VALUE		("value");
	// @formatter:on

	private final String key;

	private StringConstKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
