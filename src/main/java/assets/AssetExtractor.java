package assets;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.Environment;
import app.LoadingBar;
import app.Resource;
import app.Resource.ResourceType;
import app.StarRodMain;
import app.input.IOUtils;
import game.map.Map;
import game.map.compiler.CollisionDecompiler;
import game.map.compiler.GeometryDecompiler;
import game.map.marker.Marker;
import util.Logger;

public class AssetExtractor
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		extractAll();
		Environment.exit();
	}

	private static class MapTemplate
	{
		private final String name;
		private String desc;

		private String shapeName;
		private String hitName;
		private String texName;
		private String bgName;

		private File shapeFile;
		private File hitFile;

		private boolean hasShapeOverride;
		private boolean hasHitOverride;
		private boolean hasTexOverride;

		public MapTemplate(String[] mapDef)
		{
			this.name = mapDef[0];
			String areaName = name.substring(0, 3);

			shapeName = mapDef[1];
			hasShapeOverride = !shapeName.equals(name + "_shape");

			hitName = mapDef[2];
			hasHitOverride = !hitName.equals(name + "_hit");

			texName = mapDef[3];
			if (texName.equalsIgnoreCase("none"))
				texName = "";
			hasTexOverride = !texName.equals(areaName + "_tex");

			bgName = mapDef[4];
			if (bgName.equalsIgnoreCase("none"))
				bgName = "";

			desc = mapDef[5];
		}
	}

	public static void extractAll() throws IOException
	{
		// only extract in base asset dir
		int numDirs = Environment.assetDirectories.size();
		File assetDir = Environment.assetDirectories.get(numDirs - 1);

		File sentinel = new File(assetDir, ".star_rod_extracted");
		if (!sentinel.exists()) {
			LoadingBar.show("Extracting Assets");
			Logger.log("Extracting assets in " + assetDir.getName());

			HashMap<String, File> assetFiles = new HashMap<>();

			// find all relevant asset files
			File subdir = AssetSubdir.MAP_GEOM.get(assetDir);
			for (File assetFile : IOUtils.getFilesWithExtension(subdir, ".bin", true)) {
				String name = FilenameUtils.getBaseName(assetFile.getName());
				assetFiles.put(name, assetFile);
			}

			// extract maps
			for (String mapInfo : Resource.getText(ResourceType.Extract, "maps.csv")) {
				String[] tokens = mapInfo.trim().split("\\s*,\\s*");
				MapTemplate template = new MapTemplate(tokens);

				template.shapeFile = assetFiles.get(template.shapeName);
				template.hitFile = assetFiles.get(template.hitName);

				Logger.log("Generating map source: " + template.name);
				Map map = generateMap(template);
				try {
					map.saveMapAs(new File(subdir, map.getName() + Directories.EXT_MAP));
				}
				catch (Exception e) {
					StarRodMain.displayStackTrace(e);
				}
			}

			// extract stages
			for (String stageInfo : Resource.getText(ResourceType.Extract, "stages.csv")) {
				String[] tokens = stageInfo.trim().split("\\s*,\\s*");
				MapTemplate template = new MapTemplate(tokens);

				template.shapeFile = assetFiles.get(template.shapeName);
				template.hitFile = assetFiles.get(template.hitName);

				Logger.log("Generating stage source: " + template.name);
				Map map = generateMap(template);
				map.isStage = true;
				for (Marker actor : map.getStageMarkers()) {
					map.create(actor);
				}

				try {
					map.saveMapAs(new File(subdir, map.getName() + Directories.EXT_MAP));
				}
				catch (Exception e) {
					StarRodMain.displayStackTrace(e);
				}
			}

			FileUtils.touch(sentinel);
		}
	}

	public static Map generateMap(MapTemplate cfg) throws IOException
	{
		Map map = new Map(cfg.name);

		map.hasBackground = !cfg.bgName.isEmpty();
		map.bgName = cfg.bgName;
		map.texName = cfg.texName;

		map.desc = cfg.desc;

		if (cfg.hasShapeOverride) {
			map.scripts.overrideShape.set(true);
			String override = cfg.shapeName;
			if (override.endsWith("_shape"))
				override = override.substring(0, override.length() - "_shape".length());
			map.scripts.shapeOverrideName.set(override);
		}

		if (cfg.hasHitOverride) {
			map.scripts.overrideHit.set(true);
			String override = cfg.shapeName;
			if (override.endsWith("_hit"))
				override = override.substring(0, override.length() - "_hit".length());
			map.scripts.hitOverrideName.set(override);
		}

		map.scripts.overrideTex.set(cfg.hasTexOverride);

		if (cfg.shapeFile != null && cfg.shapeFile.exists()) {
			new GeometryDecompiler(map, cfg.shapeFile);
		}

		if (cfg.hitFile != null && cfg.hitFile.exists()) {
			new CollisionDecompiler(map, cfg.hitFile);
		}

		return map;
	}
}
