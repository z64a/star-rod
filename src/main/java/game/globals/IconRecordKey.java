package game.globals;

import util.xml.XmlKey;

public enum IconRecordKey implements XmlKey
{
	// @formatter:off
	TAG_ROOT		("Icons"),
	TAG_ICON		("Icon"),
	ATTR_TYPE			("type"),
	ATTR_NAME			("name");
	// @formatter:on

	private final String key;

	private IconRecordKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
