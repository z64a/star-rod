package assets;

import java.io.File;

public enum AssetSubdir
{
	// @formatter:off
	MAPFS				(			"/mapfs/"),
	MAP_GEOM			(MAPFS,			"/geom/"),
	MAP_TEX				(MAPFS,			"/tex/"),
	MAP_BG 				(MAPFS,			"/bg/"),

	ENTITY				(			"/entity/"),

	MSG					(			"/msg/"),

	SPRITE				(			"/sprite/"),
	PLR_SPRITE			(SPRITE,		"/player/"),
	PLR_SPRITE_IMG		(PLR_SPRITE,		"/rasters/"),
	PLR_SPRITE_PAL		(PLR_SPRITE,		"/palettes/"),
	NPC_SPRITE			(SPRITE,		"/npc/"),

	CHARSET				(			"/charset/"),
	STANDARD_CHARS		(CHARSET,		"/standard/"),
	STANDARD_CHARS_PAL	(STANDARD_CHARS,	"/palette/"),
	TITLE_CHARS			(CHARSET,		"/title/"),
	SUBTITLE_CHARS		(CHARSET,		"/subtitle/"),

	UI					(			"/ui/"),
	UI_MSG				(UI,			"/msg/"),
	UI_PAUSE			(UI,			"/pause/"),

	PAUSE				(			"/pause/"),

	ICON				(			"/icon/");
	// @formatter:on

	private final String path;

	private AssetSubdir(String path)
	{
		this.path = path;
	}

	private AssetSubdir(AssetSubdir parent, String path)
	{
		String fullPath = parent.path + "/" + path;
		this.path = fullPath.replaceAll("/+", "/");
	}

	public File get(File assetDir)
	{
		return new File(assetDir, path);
	}

	public File getBaseDir()
	{ return get(AssetManager.getBaseAssetDir()); }

	public File getModDir()
	{ return get(AssetManager.getTopLevelAssetDir()); }

	@Override
	public String toString()
	{
		return path;
	}
}
