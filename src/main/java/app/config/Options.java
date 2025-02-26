package app.config;

import java.util.HashMap;

import app.Environment;

/**
 * These options are used with {@link Config} objects to set user options for the map editor,
 * the dumping process, the patching process, etc. The {@link Scope} describes which of these
 * domains the Option pertains to and the {@link Type} defines acceptable values for it to hold.
 * Type checking is enforced by getter/setter methods in the Config class.
 *
 * Options with Main or Dump scope belong to /cfg/main.cfg
 * Options with Editor or Patch scope belong to {mod.dir}/mod.cfg
 */
public enum Options
{
	// @formatter:off
	// options for main.cfg, mostly to keep track of user directories
	ProjPath			(true, Scope.Main, Type.String, "ProjPath", null),
	GameVersion			(true, Scope.Main, Type.String, "GameVersion", "us"),

	LogDetails			(true, Scope.Main, Type.Boolean, "LogDetails", "false"),
	Theme				(true, Scope.Main, Type.String, "Theme", "FlatLight"),
	ExitToMenu			(true, Scope.Main, Type.Boolean, "ExitToMenu", "true"),
	CheckForUpdates		(true, Scope.Main, Type.Boolean, "CheckForUpdates", "true"),

	ExtractedMapData	(true, Scope.Project, Type.Boolean, "ExtractedMapData", "false"),

	// options for dumping assets
	DumpVersion			(true, Scope.Dump, Type.String, "DumpVersion", Environment.getVersionString()),

	// editor options
	EditorDebugMode		(false, Scope.MapEditor, Type.Boolean, "EditorDebugMode", "False"),
	ShowCurrentMode		(true, Scope.MapEditor, Type.Boolean, "ShowCurrentMode", "True", "Show Mode in Viewport", ""),
	UndoLimit			(true, Scope.MapEditor, Type.Integer, "UndoLimit", "32", "Undo Limit", "", 1.0),
	BackupInterval		(true, Scope.MapEditor, Type.Integer, "BackupInterval", "-1", "Backup Interval",
			"How often (in mintues) to automatically save backups. Negative values mean 'never'."),
	AngleSnap			(true, Scope.MapEditor, Type.Float,  "AngleSnap", "15.0", "Angle Snap Increment",
			"Sets the angle increment used for rotations with rotation snap enabled.", 1.0, 180.0, 1.0),
	uvScale				(true, Scope.MapEditor, Type.Float,  "UVScale", "16.0", "Default UV Scale",
			"The default scale in texels to world units to use for UV generation.", 1.0, 128.0, 1.0),
	ScrollSensitivity	(true, Scope.MapEditor, Type.Integer, "ScrollWheelSensitivity", "32", "Scroll Sensitivity",
			"Set how sensitive scroll panels are to mouse wheel scrolling", 1.0, 1024.0, 1.0),
	NormalsLength		(true, Scope.MapEditor, Type.Float,  "NormalsLength", "16.0", "Normals Draw Length",
					"The length of normal visualizations draw in the editor.", 1.0, 1024.0, 1.0),
	RenameOnSave		(true, Scope.MapEditor, Type.String, "RenameOnSave", "prompt"),
	RecentMap0			(true, Scope.MapEditor, Type.String, "RecentMap0", ""),
	RecentMap1			(true, Scope.MapEditor, Type.String, "RecentMap1", ""),
	RecentMap2			(true, Scope.MapEditor, Type.String, "RecentMap2", ""),
	RecentMap3			(true, Scope.MapEditor, Type.String, "RecentMap3", ""),
	RecentMap4			(true, Scope.MapEditor, Type.String, "RecentMap4", ""),
	RecentMap5			(true, Scope.MapEditor, Type.String, "RecentMap5", ""),
	CrashedMap			(true, Scope.MapEditor, Type.String, "CrashedMap", ""),

	SprLastNpcSprite		(true, Scope.SpriteEditor, Type.String, "LastNpcSprite", ""),
	SprLastPlayerSprite		(true, Scope.SpriteEditor, Type.String, "SprLastPlayerSprite", ""),
	SprUseFiltering			(true, Scope.SpriteEditor, Type.Boolean, "UseFiltering", "false"),
	SprFlipHorizontal		(true, Scope.SpriteEditor, Type.Boolean, "FlipHorizontal", "false"),
	SprBackFacing			(true, Scope.SpriteEditor, Type.Boolean, "FlipHorizontal", "false"),
	SprShowScaleReference	(true, Scope.SpriteEditor, Type.Boolean, "ShowScaleReference", "false"),
	SprHighlightCommand		(true, Scope.SpriteEditor, Type.Boolean, "HighlightCommand", "true"),
	SprHighlightSelected	(true, Scope.SpriteEditor, Type.Boolean, "HighlightSelected", "true"),
	SprEnableBackground		(true, Scope.SpriteEditor, Type.Boolean, "EnableBackground", "false"),
	SprEnableAxes			(true, Scope.SpriteEditor, Type.Boolean, "EnableAxes", "true"),

	StrPrintDelay			(true, Scope.StringEditor, Type.Boolean, "PrintDelay", "true"),
	StrViewportGuides		(true, Scope.StringEditor, Type.Boolean, "ViewportGuides", "true"),
	StrUseCulling			(true, Scope.StringEditor, Type.Boolean, "UseCulling", "true");
	// @formatter:on

	public final Scope scope;
	public final boolean required;
	public final Type type;
	public final String key;
	public final String defaultValue;
	public final String guiName;
	public final String guiDesc;
	public final double min;
	public final double max;
	public final double step;

	// most general option
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue,
		String guiName, String guiDesc, double min, double max, double step)
	{
		this.key = key;
		this.required = required;
		this.defaultValue = defaultValue;
		this.guiName = guiName;
		this.guiDesc = guiDesc;
		this.scope = scope;
		this.type = type;
		this.min = min;
		this.max = max;
		this.step = step;
	}

	// for options without maximum limits
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue,
		String checkBoxLabel, String checkBoxDesc, double min)
	{
		this(required, scope, type, key, defaultValue, checkBoxLabel, checkBoxDesc, min, Double.POSITIVE_INFINITY, 1.0);
	}

	// for options without limits
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue,
		String checkBoxLabel, String checkBoxDesc)
	{
		this(required, scope, type, key, defaultValue, checkBoxLabel, checkBoxDesc, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1.0);
	}

	// for bounded numeric options with no UI representation
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue, double min, double max, double step)
	{
		this(required, scope, type, key, defaultValue, "", "", min, max, step);
	}

	// for lower-bounded numeric options with no UI representation
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue, double min)
	{
		this(required, scope, type, key, defaultValue, "", "", min, Double.POSITIVE_INFINITY, 1.0);
	}

	// for options with no UI representation
	private Options(boolean required, Scope scope, Type type, String key, String defaultValue)
	{
		this(required, scope, type, key, defaultValue, "", "", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1.0);
	}

	public void setToDefault(Config cfg)
	{
		switch (type) {
			case Boolean:
				cfg.setBoolean(this, this.defaultValue);
				break;
			case Integer:
				cfg.setInteger(this, this.defaultValue);
				break;
			case Hex:
				cfg.setHex(this, this.defaultValue);
				break;
			case Float:
				cfg.setFloat(this, this.defaultValue);
				break;
			case String:
				cfg.setString(this, this.defaultValue);
				break;
		}
	}

	public static enum Scope
	{
		Main,
		Project,
		MapEditor,
		SpriteEditor,
		StringEditor,
		Dump,
	}

	public static enum Type
	{
		Boolean,
		Integer,
		Hex,
		Float,
		String
	}

	public static interface ConfigOptionEditor
	{
		public void read(Config cfg);

		public boolean write(Config cfg);
	}

	private static HashMap<String, Options> optNameMap;

	static {
		optNameMap = new HashMap<>();
		for (Options opt : Options.values())
			optNameMap.put(opt.key, opt);
	}

	public static Options getOption(String key)
	{
		return optNameMap.get(key);
	}
}
