package app;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import util.Logger;

public enum Directories
{
	// @formatter:off
	//=======================================================================================
	// Directories not related to any specific project

	DATABASE			(Root.CONFIG,			"/database/"),
	DATABASE_EDITOR		(Root.CONFIG, DATABASE,		"/editor/"),
	DATABASE_THEMES		(Root.CONFIG, DATABASE,		"/themes/"),

	LOGS				(Root.STATE, 			"/logs/"),
	TEMP				(Root.STATE, 			"/temp/"),

	//=======================================================================================
	// Directories contain dumped content needed for Star Rod to function
	// These should all eventually become unnecessary and be removed

	DUMP_WORLD			(Root.DUMP,				"/world/"),

	DUMP_ENTITY 		(Root.DUMP, DUMP_WORLD,		"/entity/"),
	DUMP_ENTITY_RAW		(Root.DUMP, DUMP_ENTITY,		"/raw/"),
	DUMP_ENTITY_SRC		(Root.DUMP, DUMP_ENTITY,		"/src/"),

	DUMP_MSG 			(Root.DUMP,				"/message/"),
	DUMP_MSG_FONT		(Root.DUMP, DUMP_MSG,		"/font/"),
	DUMP_FONT_STD		(Root.DUMP, DUMP_MSG_FONT,		"/normal/"),
	DUMP_FONT_STD_PAL	(Root.DUMP, DUMP_FONT_STD,			"/palette/"),
	DUMP_FONT_CR1		(Root.DUMP, DUMP_MSG_FONT,		"/credits-title/"),
	DUMP_FONT_CR1_PAL	(Root.DUMP, DUMP_FONT_CR1,			"/palette/"),
	DUMP_FONT_CR2		(Root.DUMP, DUMP_MSG_FONT,		"/credits-name/"),
	DUMP_FONT_CR2_PAL	(Root.DUMP, DUMP_FONT_CR2,			"/palette/"),

	//=======================================================================================
	// Directories relative to the current project

	PROJ_STAR_ROD		(Root.PROJECT,					"/.starrod/"),
	PROJ_CFG			(Root.PROJECT, PROJ_STAR_ROD,		"/cfg/"),
	PROJ_THUMBNAIL		(Root.PROJECT,						"/thumbnail/"),
	PROJ_SRC			(Root.PROJECT,					"/src/"),
	PROJ_SRC_WORLD		(Root.PROJECT, PROJ_SRC,			"/world/"),
	PROJ_SRC_STAGE		(Root.PROJECT, PROJ_SRC,			"/battle/common/stage/"),
	PROJ_INCLUDE		(Root.PROJECT,					"/include/"),
	PROJ_INCLUDE_MAPFS	(Root.PROJECT, PROJ_INCLUDE,		"/mapfs/");

	// @formatter:on
	//=======================================================================================

	public static final String FN_BASE_ROM = "baserom.z64";

	public static final String FN_SPRITE_SHADING = "sprite_shading_profiles.json";

	public static final String FN_EDITOR_GUIDES = "EditorGuides.json";

	public static final String FN_MAP_EDITOR_CONFIG = "map_editor.cfg";
	public static final String FN_STRING_EDITOR_CONFIG = "string_editor.cfg";
	public static final String FN_SPRITE_EDITOR_CONFIG = "sprite_editor.cfg";

	// extensions
	public static final String EXT_MSG = ".msg";
	public static final String EXT_NEW_TEX = ".json";
	public static final String EXT_OLD_TEX = ".txa";
	public static final String EXT_MAP = ".xml";
	public static final String EXT_PNG = ".png";
	public static final String MAP_BACKUP_SUFFIX = ".backup";
	public static final String MAP_CRASH_SUFFIX = ".crash";

	public static final String EXT_SPRITE = ".xml";
	public static final String FN_SPRITE_TABLE = "SpriteTable.xml";
	public static final String FN_SPRITESHEET = "SpriteSheet.xml";

	public static final String FN_STRING_CONSTANTS = "StringConstants.xml";

	public static final String FN_WORLD_MAP = "world_map.xml";

	private final Root root;
	private final String path;
	private final boolean optional;

	private Directories(Root root, String path)
	{
		this(root, path, false);
	}

	private Directories(Root root, Directories parent, String path)
	{
		this(root, parent, path, false);
	}

	private Directories(Root root, Directories parent, String path, boolean optional)
	{
		this.root = root;
		String fullPath = parent.path + "/" + path;
		this.path = fullPath.replaceAll("/+", "/");
		this.optional = optional;
	}

	private Directories(Root root, String path, boolean optional)
	{
		this.root = root;
		this.path = path;
		this.optional = optional;
	}

	@Override
	public String toString()
	{
		return getRootPath(root) + path;
	}

	public File toFile()
	{
		return new File(this.toString());
	}

	public File file(String filename)
	{
		return new File(this.toString(), filename);
	}

	private enum Root
	{
		NONE, DUMP, PROJECT, CONFIG, STATE
	}

	private static String getRootPath(Root root)
	{
		switch (root) {
			case NONE:
				return Environment.getWorkingDirectory().getAbsolutePath();
			case DUMP:
				return dumpPath;
			case PROJECT:
				return projPath;
			case CONFIG:
				return Environment.getUserConfigDir().getAbsolutePath();
			case STATE:
				return Environment.getUserStateDir().getAbsolutePath();

		}
		return null;
	}

	private static String dumpPath = null;
	private static String projPath = null;

	public static void setDumpDirectory(String path)
	{
		if (path.contains("\\"))
			path = path.replaceAll("\\\\", "/");
		if (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);

		dumpPath = path;
	}

	public static String getDumpPath()
	{
		return dumpPath;
	}

	public static void setProjectDirectory(String path)
	{
		if (path.contains("\\"))
			path = path.replaceAll("\\\\", "/");
		if (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);
		projPath = path;

		Logger.log("Project directory: " + projPath);
	}

	public static void createDumpDirectories() throws IOException
	{
		if (dumpPath == null)
			throw new IOException("Dump directory is not set.");

		for (Directories dir : Directories.values()) {
			if (dir.root == Root.DUMP && !dir.optional)
				FileUtils.forceMkdir(dir.toFile());
		}
	}
}
