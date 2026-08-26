package game.map.scripts.extract;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;

import app.Directories;
import app.Environment;
import app.LoadingBar;
import app.StarRodException;
import app.input.IOUtils;
import assets.AssetManager;
import game.map.Map;
import game.map.MapObject.MapObjectType;
import game.map.marker.Marker;
import game.map.scripts.extract.entity.ArrowSign;
import game.map.scripts.extract.entity.BasicEntity;
import game.map.scripts.extract.entity.BlueSwitch;
import game.map.scripts.extract.entity.BlueWarpPipe;
import game.map.scripts.extract.entity.Chest;
import game.map.scripts.extract.entity.CoinBlock;
import game.map.scripts.extract.entity.ExtractedEntity;
import game.map.scripts.extract.entity.HeartBlock;
import game.map.scripts.extract.entity.HiddenPanel;
import game.map.scripts.extract.entity.ItemBlock;
import game.map.scripts.extract.entity.ItemEntity;
import game.map.scripts.extract.entity.OptionalScriptEntity;
import game.map.scripts.extract.entity.SimpleSpring;
import game.map.scripts.extract.entity.SpinningFlower;
import game.map.scripts.extract.entity.SuperBlock;
import game.map.scripts.extract.entity.Tweester;
import game.map.scripts.extract.entity.WoodenCrate;
import game.map.tree.MapObjectNode;
import game.sprite.SpriteLoader;
import util.Logger;
import util.NameUtils;
import util.Priority;

public class MapExtractor
{
	// KNOWN PROBLEMS MATCHING:
	// - mac_06 / EVS_Main -- two different GEN_TEX_PANNER_1 created
	//  drip volumes min/max are not respected tik_05 and tik_07 are known bad
	// - trd_06 / FallPath -- needs Float values for Path marker -- skip??
	// - kpa_16 / EVS_TexPan_Steam -- revert the generated panner here
	// - kzn_03 and kzn_09 have non-integer Vec3f for Zipline_Endpoints
	// - flo_14 bubbles / EVS_SetupBubbles -- EVT_FLOWER_SPAWN_REGION bad min/max

	// revert TEX_PAN_PARAMS_SKIP_ID

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		extractAll();
		Environment.exit();
	}

	public static void extractAll() throws IOException
	{
		LoadingBar.show("Extracting Map Data", Priority.IMPORTANT);

		File f = Directories.PROJ_SRC_WORLD.toFile();
		File[] worldDirs = f.listFiles();
		Arrays.sort(worldDirs);

		// limit during testing
		int COUNT = 0;
		int LIMIT = -1; // 243;

		for (File worldDir : worldDirs) {
			if (worldDir.isDirectory() && worldDir.getName().matches("area_\\w+")) {
				String areaName = worldDir.getName().substring(5);
				File[] mapDirs = worldDir.listFiles();
				Arrays.sort(mapDirs);

				for (File mapDir : mapDirs) {
					if (mapDir.isDirectory() && mapDir.getName().startsWith(areaName)) {
						new MapExtractor(mapDir.getName());

						if (LIMIT > 0 && ++COUNT >= LIMIT)
							return;
					}
				}
			}
		}

		LoadingBar.dismiss();
	}

	protected HashSet<String> usedNames = new HashSet<>();
	protected List<Marker> markers = new ArrayList<>();

	// used when extracting data from source files
	private String fileText;
	public boolean fileModified = false;

	public MapExtractor(String mapName) throws IOException
	{
		Logger.log("Extracting data from " + mapName, Priority.IMPORTANT);

		File mapFile = AssetManager.getMap(mapName);
		if (!mapFile.exists()) {
			throw new StarRodException("Couldn't find map file for " + mapName);
		}

		SpriteLoader.loadAnimsMetadata(false);
		Map map = Map.loadMap(mapFile);

		extractToMap(map);

		try {
			map.saveMap();
		}
		catch (Exception e) {
			Logger.printStackTrace(e);
			Logger.logError("Failed to save " + mapName + "!");
		}
	}

	public MapExtractor(Map map) throws IOException
	{
		Logger.log("Extracting data for " + map.getName(), Priority.IMPORTANT);
		extractToMap(map);
	}

	private void extractToMap(Map map) throws IOException
	{
		for (File src : IOUtils.getFilesWithExtension(map.getProjDir(), ".c", true)) {
			digest(map, src);
		}

		addMarkers(map);
	}

	public String getFileText()
	{
		return fileText;
	}

	public void setFileText(String newText)
	{
		fileText = newText;
		fileModified = true;
	}

	private void digest(Map map, File src) throws IOException
	{
		fileText = Files.readString(src.toPath());
		fileModified = false;

		MapPropertiesExtractor.findAndReplace(map, this);
		TexPannerExtractor.findAndReplace(map, this);

		if (fileText.contains("EntryList"))
			EntryListExtractor.findAndReplace(this);

		if (fileText.contains("LavaReset"))
			LavaResetExtractor.findAndReplace(this);

		if (fileText.contains("NpcData"))
			NpcExtractor.findAndReplace(this);

		if (fileText.contains("MakeItemEntity"))
			findAndReplace(ItemEntity.RegexMatcher, ItemEntity.class);

		if (fileText.contains("MakeEntity")) {
			findAndReplace(BasicEntity.RegexMatcher, BasicEntity.class);
			findAndReplace(OptionalScriptEntity.RegexMatcher, OptionalScriptEntity.class);
			findAndReplace(BlueSwitch.RegexMatcher, BlueSwitch.class);
			findAndReplace(ItemBlock.RegexMatcher, ItemBlock.class);
			findAndReplace(CoinBlock.RegexMatcher, CoinBlock.class);
			findAndReplace(HeartBlock.RegexMatcher, HeartBlock.class);
			findAndReplace(Chest.RegexMatcher, Chest.class);
			findAndReplace(ArrowSign.RegexMatcher, ArrowSign.class);
			findAndReplace(HiddenPanel.RegexMatcher, HiddenPanel.class);
			findAndReplace(SimpleSpring.RegexMatcher, SimpleSpring.class);
			findAndReplace(WoodenCrate.RegexMatcher, WoodenCrate.class);
			findAndReplace(SpinningFlower.RegexMatcher, SpinningFlower.class);
			findAndReplace(BlueWarpPipe.RegexMatcher, BlueWarpPipe.class);
			findAndReplace(Tweester.RegexMatcher, Tweester.class);
		}

		// special case for Super Blocks since they use macros
		if (fileText.contains("EVT_MAKE_SUPER_BLOCK"))
			SuperBlock.scan(this);

		if (fileText.contains("Vec3f") || fileText.contains("TweesterPath"))
			PathExtractor.findAndReplace(this);

		if (fileText.contains("EVS_Main") && !map.getName().equals("sbk_99")) //FIXME sbk_99 lol
			CamSetupExtractor.findAndReplace(map, this);

	//	if (fileText.contains("SetPanTarget"))
	//		CamTargetExtractor.findAndReplace(this);

		if (fileText.contains("FoliageDropList"))
			FoliageDropExtractor.findAndReplace(this);

		if (fileText.contains("FoliageVectorList"))
			FoliageEffectExtractor.findAndReplace(this);

		if (fileText.contains("CreatePushBlockGrid"))
			PushGridExtractor.findAndReplace(this);

		if (fileText.contains("PlayEffect"))
			EffectPosExtractor.findAndReplace(this);

		if (fileText.contains("BombTrigger"))
			BombPosExtractor.findAndReplace(this);

		if (fileText.contains("DripVolume"))
			DripVolumeExtractor.findAndReplace(this);

		if (fileText.contains("EVT_FLOWER_SPAWN_REGION"))
			FlowerVolumeExtractor.findAndReplace(this);

		if (fileModified)
			Files.writeString(src.toPath(), fileText);
	}

	private <T extends ExtractedEntity> void findAndReplace(Matcher matcher, Class<T> theClass)
	{
		matcher.reset(fileText);

		StringBuilder out = new StringBuilder(fileText.length());

		boolean modified = false;
		while (matcher.find()) {
			T obj = null;
			try {
				obj = theClass.getDeclaredConstructor().newInstance();
				obj.fromSourceMatcher(this, matcher);
			}
			catch (Exception e) {
				Logger.log("Could not extract: " + e.getMessage());
				obj = null;
			}

			if (obj != null) {
				String indent = obj.getIndent();
				String replacement = indent + String.join("\n" + indent, obj.getLines());
				matcher.appendReplacement(out, replacement);
				modified = true;
			}
			else {
				// if there was an error, append the unmodified lines
				matcher.appendReplacement(out, matcher.group());
			}

		}
		matcher.appendTail(out);

		if (modified)
			setFileText(out.toString());
	}

	public String getNextName(String baseName)
	{
		return getNextName(baseName, 1);
	}

	public String getNextName(String baseName, int startingAt)
	{
		// value of -1 will try without numerical suffix first
		if (startingAt == -1) {
			if (!usedNames.contains(baseName)) {
				usedNames.add(baseName);
				return baseName;
			}
			else {
				startingAt = 1;
			}
		}

		String nextName;
		int i = startingAt;
		do {
			nextName = baseName + "_" + i;
			i++;
		}
		while (usedNames.contains(nextName));

		usedNames.add(nextName);
		return nextName;
	}

	public String getGenName(String name)
	{
		return NameUtils.toExtractStyle("GEN_" + name);
	}

	public String getNiceItemName(String itemEnumName)
	{
		if (itemEnumName.startsWith("ITEM_")) {
			itemEnumName = itemEnumName.substring("ITEM_".length());

			return Arrays.stream(itemEnumName.toLowerCase().split("_"))
				.filter(s -> !s.isEmpty())
				.map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
				.reduce("", String::concat);
		}
		else {
			return null;
		}
	}

	public class SavedVector
	{
		public final int x;
		public final int y;
		public final int z;
		public final String name;

		public SavedVector(int x, int y, int z, String name)
		{
			this.x = x;
			this.y = y;
			this.z = z;
			this.name = name;
		}

		public boolean matches(int x, int y, int z)
		{
			return this.x == x && this.y == y && this.z == z;
		}
	}

	private final HashMap<String, ArrayList<SavedVector>> savedVectorMap = new HashMap<>();

	public void saveVectorName(String key, int x, int y, int z, String name)
	{
		savedVectorMap.computeIfAbsent(key, k -> new ArrayList<>()).add(new SavedVector(x, y, z, name));
	}

	public String findVectorName(String key, int x, int y, int z)
	{
		ArrayList<SavedVector> list = savedVectorMap.get(key);
		if (list == null)
			return null;

		for (SavedVector vec : list) {
			if (vec.matches(x, y, z))
				return vec.name;
		}

		return null;
	}

	public static enum MarkerExtractionGroup
	{
		ENTRY ("Entrances"),
		NPC ("NPCs"),
		ENTITY ("Entities"),
		CAMERA ("Camera Targets"),
		EFFECT ("Effects"),
		FOLIAGE ("Foliage"),
		NONE (null);

		private final String displayName;

		MarkerExtractionGroup(String displayName)
		{
			this.displayName = displayName;
		}

		public String getDisplayName()
		{
			return displayName;
		}
	}

	public void addMarker(Marker m)
	{
		switch (m.getType()) {
			case Entry:
				addMarker(m, MarkerExtractionGroup.ENTRY);
				break;
			case NPC:
				addMarker(m, MarkerExtractionGroup.NPC);
				break;
			case BlockGrid:
			case Entity:
				addMarker(m, MarkerExtractionGroup.ENTITY);
				break;
			case CamTarget:
				addMarker(m, MarkerExtractionGroup.CAMERA);
				break;
			default:
				addMarker(m, MarkerExtractionGroup.NONE);
				break;
		}
	}

	public void addMarker(Marker marker, MarkerExtractionGroup group)
	{
		marker.extractionGroup = group;
		markers.add(marker);
	}

	public void addMarkers(Map map)
	{
		EnumMap<MarkerExtractionGroup, List<Marker>> grouped = new EnumMap<>(MarkerExtractionGroup.class);
		for (MarkerExtractionGroup g : MarkerExtractionGroup.values()) {
			grouped.put(g, new ArrayList<>());
		}

		HashSet<String> existingMarkerNames = new HashSet<>(map.getNameList(MapObjectType.MARKER, true));

		for (Marker m : markers) {
			if (existingMarkerNames.contains(m.getName())) {
				Logger.logfWarning("%s already has Marker named %s", map.getName(), m.getName());
				continue;
			}

			grouped.get(m.extractionGroup).add(m);
		}

		MapObjectNode<Marker> rootNode = map.markerTree.getRoot();

		// add groups
		for (MarkerExtractionGroup group : MarkerExtractionGroup.values()) {
			if (group == MarkerExtractionGroup.NONE)
				continue;

			List<Marker> groupList = grouped.get(group);

			if (groupList == null || groupList.isEmpty())
				continue;

			Marker groupMarker = Marker.createGroup(group.getDisplayName());
			MapObjectNode<Marker> groupNode = groupMarker.getNode();
			addMarkerChild(rootNode, groupMarker);

			for (Marker m : groupList) {
				addMarkerChild(groupNode, m);
			}
		}

		// add ungrouped
		for (Marker m : grouped.get(MarkerExtractionGroup.NONE)) {
			addMarkerChild(rootNode, m);
		}
	}

	private void addMarkerChild(MapObjectNode<Marker> parentNode, Marker marker)
	{
		MapObjectNode<Marker> childNode = marker.getNode();
		childNode.parentNode = parentNode;
		childNode.childIndex = parentNode.getChildCount();
		parentNode.add(childNode);
	}
}
