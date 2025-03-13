package game;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.Directories;
import app.input.IOUtils;
import game.map.shading.SpriteShadingData;
import game.map.shading.SpriteShadingEditor;
import util.CaseInsensitiveMap;

public class ProjectDatabase
{
	private static CaseInsensitiveMap<DecompEnum> decompEnums;

	public static DecompEnum ESurfaceTypes;
	public static DecompEnum ELocations;
	public static DecompEnum EStoryProgress;
	public static DecompEnum EDoorSounds;
	public static DecompEnum EDoorSwings;
	public static DecompEnum EItemSpawnModes;

	public static DecompEnum ESongs;
	public static DecompEnum EAmbientSounds;
	public static DecompEnum EMoveType;
	public static DecompEnum EBattleMessages;
	public static DecompEnum TargetFlags;
	public static DecompEnum ItemTypeFlags;

	public static SpriteShadingData SpriteShading;

	private static List<SimpleItem> items;
	private static List<String> savedFlags;
	private static List<String> savedBytes;

	private static boolean initialized = false;

	public static void initialize() throws IOException
	{
		if (initialized)
			return;

		savedFlags = loadSavedVarNames(Directories.PROJ_INCLUDE.file("saved_flag_names.h"));
		savedBytes = loadSavedVarNames(Directories.PROJ_INCLUDE.file("saved_byte_names.h"));

		// TODO the following block takes a half-second at startup and could be optimized better
		{
			decompEnums = new CaseInsensitiveMap<>();
			DecompEnum.addEnums(decompEnums, Directories.PROJ_INCLUDE.file("enums.h").getAbsolutePath());
			DecompEnum.addEnums(decompEnums, Directories.PROJ_INCLUDE.file("effects.h").getAbsolutePath());
			DecompEnum.addEnums(decompEnums, Directories.PROJ_SRC.file("battle/battle_names.h").getAbsolutePath());
			DecompEnum.addEnums(decompEnums, Directories.PROJ_SRC.file("battle/stage_names.h").getAbsolutePath());
		}

		ESurfaceTypes = decompEnums.get("SurfaceType");
		ELocations = decompEnums.get("Locations");
		EStoryProgress = decompEnums.get("StoryProgress");

		EMoveType = decompEnums.get("MoveType");
		EBattleMessages = decompEnums.get("BattleMessages");
		ItemTypeFlags = decompEnums.get("ItemTypeFlags");
		TargetFlags = decompEnums.get("TargetFlags");

		ESongs = decompEnums.get("SongIDs");
		EAmbientSounds = decompEnums.get("AmbientSounds");

		EDoorSounds = decompEnums.get("DoorSounds");
		EDoorSwings = decompEnums.get("DoorSwing");
		EItemSpawnModes = decompEnums.get("ItemSpawnModes");

		SpriteShading = SpriteShadingEditor.loadData();

		items = SimpleItem.readAll();

		initialized = true;
	}

	public static List<String> getSavedFlagNames()
	{
		return savedFlags;
	}

	public static List<String> getSavedByteNames()
	{
		return savedBytes;
	}

	private static final Matcher SavedVarMatcher = Pattern.compile("\\s*(\\w+)\\s*=.+").matcher("");

	private static List<String> loadSavedVarNames(File header) throws IOException
	{
		List<String> names = new ArrayList<>();
		for (String line : IOUtils.readPlainTextFile(header)) {
			SavedVarMatcher.reset(line);
			if (SavedVarMatcher.matches()) {
				names.add(SavedVarMatcher.group(1));
			}
		}

		return names;
	}

	public static List<SimpleItem> getItemList()
	{
		return new ArrayList<>(items);
	}

	public static Integer getItemID(String name)
	{
		for (SimpleItem item : items) {
			if (item.enumName.equals(name))
				return item.index;
		}
		return null;
	}

	public static List<String> getItemNames()
	{
		List<String> names = new ArrayList<>(items.size());
		for (SimpleItem item : items) {
			names.add(item.enumName);
		}
		return names;
	}
}
