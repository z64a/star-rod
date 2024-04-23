package game.worldmap;

import util.xml.XmlKey;

public enum WorldMapKey implements XmlKey
{
	// @formatter:off
	TAG_ROOT		("WorldMap"),
	TAG_LOCATION		("Location"),
	ATTR_NAME				("id"),
	ATTR_PARENT				("parent"),
	ATTR_REQUIRES			("requires"),
	ATTR_START_X			("startX"),
	ATTR_START_Y			("startY"),
	ATTR_PATH				("path");
	// @formatter:on

	private final String key;

	private WorldMapKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
