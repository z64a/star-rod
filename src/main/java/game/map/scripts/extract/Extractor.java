package game.map.scripts.extract;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
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
import game.map.scripts.extract.HeaderEntry.HeaderParseException;
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

public class Extractor
{
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

		for (File worldDir : worldDirs) {
			if (worldDir.isDirectory() && worldDir.getName().matches("area_\\w+")) {
				String areaName = worldDir.getName().substring(5);
				File[] mapDirs = worldDir.listFiles();
				Arrays.sort(mapDirs);

				for (File mapDir : mapDirs) {
					if (mapDir.isDirectory() && mapDir.getName().startsWith(areaName)) {
						new Extractor(mapDir.getName(), true);
					}
				}
			}
		}

		LoadingBar.dismiss();
	}

	protected HashSet<String> usedNames = new HashSet<>();
	protected List<Marker> markers = new ArrayList<>();

	private List<HeaderEntry> entries = new ArrayList<>();

	// used when extracting data from source files
	private String fileText;
	public boolean fileModified = false;

	public Extractor(String mapName, boolean fromSource) throws IOException
	{
		Logger.log("Extracting data from " + mapName, Priority.IMPORTANT);

		File mapFile = AssetManager.getMap(mapName);
		if (!mapFile.exists()) {
			throw new StarRodException("Couldn't find map file for " + mapName);
		}

		SpriteLoader.loadAnimsMetadata(false);
		Map map = Map.loadMap(mapFile);

		extractToMap(map, fromSource);

		try {
			map.saveMap();
		}
		catch (Exception e) {
			Logger.printStackTrace(e);
			Logger.logError("Failed to save " + mapName + "!");
		}
	}

	public Extractor(Map map, boolean fromSource) throws IOException
	{
		Logger.log("Extracting data for " + map.getName(), Priority.IMPORTANT);
		extractToMap(map, fromSource);
	}

	private void extractToMap(Map map, boolean fromSource) throws IOException
	{
		if (fromSource) {
			for (File src : IOUtils.getFilesWithExtension(map.getProjDir(), ".c", true)) {
				digest(map, src);
			}
		}
		else {
			File header = new File(map.getProjDir(), "generated.h");
			entries.addAll(HeaderEntry.parseFile(header));
		}

		processHeaderEntries(map);
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

	public void addHeaderEntry(HeaderEntry h)
	{
		entries.add(h);
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

		PathExtractor.findAndReplace(this);

		if (fileText.contains("CreatePushBlockGrid"))
			PushGridExtractor.findAndReplace(this);

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
		return NameUtils.toEnumStyle("GEN_" + name);
	}

	public void addMarker(Marker marker)
	{
		markers.add(marker);
	}

	public void addMarkers(Map map)
	{
		List<Marker> entryMarkers = new ArrayList<>();
		List<Marker> npcMarkers = new ArrayList<>();
		List<Marker> entityMarkers = new ArrayList<>();
		List<Marker> otherMarkers = new ArrayList<>();

		HashSet<String> existingMarkerNames = new HashSet<>(map.getNameList(MapObjectType.MARKER, true));

		for (Marker m : markers) {
			// filter out any markers with conflicting names
			if (existingMarkerNames.contains(m.getName())) {
				Logger.logfWarning("%s already has Marker named %s", map.getName(), m.getName());
				continue;
			}

			switch (m.getType()) {
				case Entry:
					entryMarkers.add(m);
					break;
				case NPC:
					npcMarkers.add(m);
					break;
				case BlockGrid:
				case Entity:
					entityMarkers.add(m);
					break;
				default:
					otherMarkers.add(m);
					break;
			}
		}

		MapObjectNode<Marker> rootNode = map.markerTree.getRoot();

		if (entryMarkers.size() > 0) {
			Marker group = Marker.createGroup("Entrances");
			MapObjectNode<Marker> groupNode = group.getNode();
			groupNode.parentNode = rootNode;
			groupNode.childIndex = rootNode.getChildCount();
			rootNode.add(groupNode);

			for (Marker m : entryMarkers) {
				m.getNode().parentNode = groupNode;
				m.getNode().childIndex = groupNode.getChildCount();
				groupNode.add(m.getNode());
			}
		}

		if (npcMarkers.size() > 0) {
			Marker group = Marker.createGroup("NPCs");
			MapObjectNode<Marker> groupNode = group.getNode();
			groupNode.parentNode = rootNode;
			groupNode.childIndex = rootNode.getChildCount();
			rootNode.add(groupNode);

			for (Marker m : npcMarkers) {
				m.getNode().parentNode = groupNode;
				m.getNode().childIndex = groupNode.getChildCount();
				groupNode.add(m.getNode());
			}
		}

		if (entityMarkers.size() > 0) {
			Marker group = Marker.createGroup("Entities");
			MapObjectNode<Marker> groupNode = group.getNode();
			groupNode.parentNode = rootNode;
			groupNode.childIndex = rootNode.getChildCount();
			rootNode.add(groupNode);

			for (Marker m : entityMarkers) {
				m.getNode().parentNode = groupNode;
				m.getNode().childIndex = groupNode.getChildCount();
				groupNode.add(m.getNode());
			}
		}

		for (Marker m : otherMarkers) {
			m.getNode().parentNode = rootNode;
			m.getNode().childIndex = rootNode.getChildCount();
			rootNode.add(m.getNode());
		}
	}

	private void processHeaderEntries(Map map)
	{
		for (HeaderEntry h : entries) {
			try {
				String type = h.getProperty("type");
				if (type == null) {
					Logger.logError("HeaderEntry is missing type!");
					return;
				}

				String[] subtype = type.split(":");

				switch (subtype[0]) {
					case "EntryList":
						EntryListExtractor.parse(this, h);
						break;
					case "MapProperties":
						MapPropertiesExtractor.parse(h, map);
						break;
					case "TexPanner":
						TexPannerExtractor.parse(h, map);
						break;
					case "Marker":
						Marker m = Marker.fromHeader(h);
						addMarker(m);
						break;
				}
			}
			catch (HeaderParseException e) {
				Logger.printStackTrace(e);
			}
		}

		addMarkers(map);
	}
}
