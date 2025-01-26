package assets;

import java.io.File;

public enum ExpectedAsset
{
	// @formatter:off
	ICON_FIRE_FLOWER	(AssetSubdir.ICON, "menu/items.png", true),
	ICON_POWER_JUMP		(AssetSubdir.ICON, "badge/PowerJump.png", true),
	ICON_MAP_EDITOR		(AssetSubdir.ICON, "menu/hammer_2.png", true),
	ICON_SPRITE_EDITOR	(AssetSubdir.ICON, "peach/BakingStrawberry.png", true),
	ICON_GLOBALS_EDITOR	(AssetSubdir.ICON, "menu/items.png", true),
	ICON_MSG_EDITOR		(AssetSubdir.ICON, "key/dictionary.png", true),
	ICON_WORLD_EDITOR	(AssetSubdir.UI_PAUSE, "unused_compass.png", true),
	ICON_IMAGE_EDITOR	(AssetSubdir.ICON, "food/TastyTonic.png", true),
	ICON_THEMES			(AssetSubdir.ICON, "badge/PUpDDown.png", true),
	ICON_EXTRACT		(AssetSubdir.ICON, "badge/Peekaboo.png", true),
	ICON_GOLD			(AssetSubdir.ICON, "key/card_gold.png", true),
	ICON_SILVER			(AssetSubdir.ICON, "key/card_silver.png", true),
	ICON_APP			(AssetSubdir.ICON, "battle/ShootingStar.png", true),
	ICON_X				(AssetSubdir.ICON, "battle/XBandage.png", true),
	CIRCLE_SHADOW		(AssetSubdir.ENTITY, "shadow/circle.png", false),
	SQUARE_SHADOW		(AssetSubdir.ENTITY, "shadow/square.png", false),
	KMR_BG				(AssetSubdir.MAP_BG, "kmr_bg.png", true),
	WORLD_MAP_BG		(AssetSubdir.PAUSE, "world_map.png", true),
	CRASH_GUY			(AssetSubdir.NPC_SPRITE, "ShyGuy/rasters/Raster1A.png", true);
	// @formatter:on

	private final AssetSubdir subdir;
	private final String path;
	private final boolean useBase;

	private ExpectedAsset(AssetSubdir subdir, String path, boolean useBase)
	{
		this.subdir = subdir;
		this.path = path;
		this.useBase = useBase;
	}

	public File getFile()
	{
		if (useBase) {
			return AssetManager.getBase(subdir, path);
		}
		else {
			return AssetManager.get(subdir, path);
		}
	}

	public String getPath()
	{
		return subdir.toString() + path;
	}
}
