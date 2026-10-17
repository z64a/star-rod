package assets;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import app.Environment;
import app.input.IOUtils;
import util.Logger;

public class AssetManager
{
	public static File getTopLevelAssetDir()
	{
		return Environment.assetDirectories.get(0);
	}

	public static File getBaseAssetDir()
	{
		int numDirs = Environment.assetDirectories.size();
		return Environment.assetDirectories.get(numDirs - 1);
	}

	public static AssetHandle get(AssetSubdir subdir, String path)
	{
		for (File assetDir : Environment.assetDirectories) {
			AssetHandle ah = new AssetHandle(assetDir, subdir + path);

			if (ah.exists())
				return ah;
		}
		return new AssetHandle(AssetManager.getTopLevelAssetDir(), subdir + path);
	}

	public static AssetHandle getTopLevel(AssetHandle source)
	{
		return new AssetHandle(getTopLevelAssetDir(), source.assetPath);
	}

	/**
	 * Resolves an asset into the writable overlay and prepares only its parent
	 * directories. The configured overlay root itself must already exist.
	 */
	public static AssetHandle getTopLevelForSave(AssetHandle source) throws IOException
	{
		AssetHandle destination = getTopLevel(source);
		Path assetRoot = destination.assetDir.toPath().toAbsolutePath().normalize();
		if (!Files.isDirectory(assetRoot))
			throw new IOException("Top-level asset directory does not exist: " + destination.assetDir);

		Path parent = destination.toPath().toAbsolutePath().normalize().getParent();
		if (parent == null || !parent.startsWith(assetRoot))
			throw new IOException("Asset output is outside the top-level asset directory: " + destination);

		Files.createDirectories(parent);
		return destination;
	}

	public static AssetHandle getBase(AssetSubdir subdir, String path)
	{
		return new AssetHandle(getBaseAssetDir(), subdir + path);
	}

	/**
	 * Delete an asset at all levels of the asset stack
	 * @param asset
	 */
	public static void deleteAll(AssetHandle asset)
	{
		for (File assetDir : Environment.assetDirectories) {
			File f = new File(assetDir, asset.assetPath);
			if (f.exists())
				FileUtils.deleteQuietly(f);
		}
	}

	public static AssetHandle getTextureArchive(String texName)
	{
		return get(AssetSubdir.MAP_TEX, texName + EXT_NEW_TEX);
	}

	public static File getTexBuildDir()
	{
		return AssetSubdir.MAP_TEX.getModDir();
	}

	/**
	 * A map source's path within an asset layer. In papermario-dx, each map has a directory at the path of its
	 * code under src, and other projects keep every map in mapfs/geom.
	 */
	public static String getMapSourcePath(String mapName)
	{
		if (game.map.Map.hasAreaDirs())
			return game.map.Map.getSourcePath(mapName) + MAP_SOURCE_NAME + EXT_MAP;
		return AssetSubdir.MAP_GEOM + mapName + EXT_MAP;
	}

	/** A map's source from the highest layer that has it, or a handle that doesn't exist if a sidecar deletes it. */
	public static AssetHandle getMap(String mapName)
	{
		String path = getMapSourcePath(mapName);
		if (new Sidecars(Environment.assetDirectories).isDeleted(path))
			return new AssetHandle(getTopLevelAssetDir(), path);

		for (File assetDir : Environment.assetDirectories) {
			AssetHandle ah = new AssetHandle(assetDir, path);
			if (ah.exists())
				return ah;
		}
		return new AssetHandle(getTopLevelAssetDir(), path);
	}

	/** Where a map source is saved: in the top asset layer. */
	public static AssetHandle getSaveMapFile(String mapName)
	{
		return new AssetHandle(getTopLevelAssetDir(), getMapSourcePath(mapName));
	}

	/** Where the editor keeps a crash or backup copy of a map, beside the source it saves. */
	public static File getMapRecoveryFile(String mapName, String suffix)
	{
		File source = getSaveMapFile(mapName);
		String baseName = source.getName().substring(0, source.getName().length() - EXT_MAP.length());
		return new File(source.getParentFile(), baseName + suffix + EXT_MAP);
	}

	/** Where compiled maps go. In papermario-dx, that is the build directory, where the build puts them. */
	public static File getMapBuildDir()
	{
		if (game.map.Map.hasAreaDirs())
			return new File(Environment.getBuildDirectory(), "include/mapfs");
		return AssetSubdir.MAP_GEOM.getModDir();
	}

	/** Where the headers of compiled maps go. */
	public static File getMapHeaderDir()
	{
		if (game.map.Map.hasAreaDirs())
			return getMapBuildDir();
		return PROJ_INCLUDE_MAPFS.toFile();
	}

	public static AssetHandle getBackground(String bgName)
	{
		return get(AssetSubdir.MAP_BG, bgName + EXT_PNG);
	}

	public static File getBackgroundBuildDir()
	{
		return AssetSubdir.MAP_BG.getModDir();
	}

	public static AssetHandle getNpcSprite(String spriteName)
	{
		return get(AssetSubdir.NPC_SPRITE, spriteName + "/" + FN_SPRITESHEET);
	}

	public static Map<String, AssetHandle> getNpcSpriteRasters(String spriteName) throws IOException
	{
		return getAssetMap(AssetSubdir.NPC_SPRITE, spriteName + "/rasters/", EXT_PNG);
	}

	public static Map<String, AssetHandle> getNpcSpritePalettes(String spriteName) throws IOException
	{
		return getAssetMap(AssetSubdir.NPC_SPRITE, spriteName + "/palettes/", EXT_PNG);
	}

	public static AssetHandle getPlayerSprite(String spriteName)
	{
		return get(AssetSubdir.PLR_SPRITE, spriteName + EXT_SPRITE);
	}

	public static Map<String, AssetHandle> getPlayerSpriteRasters() throws IOException
	{
		return getAssetMap(AssetSubdir.PLR_SPRITE_IMG, EXT_PNG);
	}

	public static Map<String, AssetHandle> getPlayerSpritePalettes() throws IOException
	{
		return getAssetMap(AssetSubdir.PLR_SPRITE_PAL, EXT_PNG);
	}

	/** Every map source in the asset stack that no sidecar deletes, sorted by map name. */
	public static Collection<AssetHandle> getMapSources() throws IOException
	{
		if (!game.map.Map.hasAreaDirs()) {
			return getAssets(AssetSubdir.MAP_GEOM, EXT_MAP, (p) -> {
				// skip crash and backup files
				String filename = p.getFileName().toString();
				return !(filename.endsWith(MAP_CRASH_SUFFIX + EXT_MAP) || filename.endsWith(MAP_BACKUP_SUFFIX + EXT_MAP));
			});
		}

		Map<String, AssetHandle> assetMap = new TreeMap<>();
		Sidecars sidecars = new Sidecars(Environment.assetDirectories);

		for (File stackDir : Environment.assetDirectories) {
			// maps in their own directories sit two levels below these, as in world/area/kmr/kmr_02
			for (String parent : List.of("world/area/", "battle/common/stage/")) {
				File[] groups = new File(stackDir, parent).listFiles(File::isDirectory);
				if (groups == null)
					continue;
				for (File group : groups) {
					File[] mapDirs = group.listFiles(File::isDirectory);
					if (mapDirs == null)
						continue;
					for (File mapDir : mapDirs) {
						String relPath = parent + group.getName() + "/" + mapDir.getName() + "/" + MAP_SOURCE_NAME + EXT_MAP;
						AssetHandle ah = new AssetHandle(stackDir, relPath);
						if (ah.isFile() && !sidecars.isDeleted(relPath))
							assetMap.putIfAbsent(mapDir.getName(), ah);
					}
				}
			}
		}

		return assetMap.values();
	}

	public static Collection<AssetHandle> getBackgrounds() throws IOException
	{
		return getAssets(AssetSubdir.MAP_BG, EXT_PNG);
	}

	public static Collection<AssetHandle> getLegacyTextureArchives() throws IOException
	{
		return getAssets(AssetSubdir.MAP_TEX, EXT_OLD_TEX);
	}

	public static Collection<AssetHandle> getTextureArchives() throws IOException
	{
		return getAssets(AssetSubdir.MAP_TEX, EXT_NEW_TEX);
	}

	public static Collection<AssetHandle> getMessages() throws IOException
	{
		return getAssets(AssetSubdir.MSG, EXT_MSG);
	}

	private static Collection<AssetHandle> getAssets(AssetSubdir dir, String ext)
	{
		return getAssets(dir, "", ext, null);
	}

	private static Collection<AssetHandle> getAssets(AssetSubdir dir, String subdir, String ext)
	{
		return getAssets(dir, subdir, ext, null);
	}

	private static Collection<AssetHandle> getAssets(AssetSubdir dir, String ext, Predicate<Path> shouldAccept)
	{
		return getAssets(dir, "", ext, shouldAccept);
	}

	private static Collection<AssetHandle> getAssets(AssetSubdir dir, String subdir, String ext, Predicate<Path> shouldAccept)
	{
		Map<String, AssetHandle> assetMap = getAssetMap(dir, subdir, ext, shouldAccept);

		// return sorted by filename
		return assetMap.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.collect(Collectors.toList());
	}

	private static Map<String, AssetHandle> getAssetMap(AssetSubdir dir, String ext)
	{
		return getAssetMap(dir, "", ext, null);
	}

	private static Map<String, AssetHandle> getAssetMap(AssetSubdir dir, String subdir, String ext)
	{
		return getAssetMap(dir, subdir, ext, null);
	}

	private static Map<String, AssetHandle> getAssetMap(AssetSubdir dir, String ext, Predicate<Path> shouldAccept)
	{
		return getAssetMap(dir, "", ext, shouldAccept);
	}

	private static Map<String, AssetHandle> getAssetMap(AssetSubdir dir, String subdir, String ext, Predicate<Path> shouldAccept)
	{
		Map<String, AssetHandle> assetMap = new HashMap<>();

		for (File stackDir : Environment.assetDirectories) {
			Path assetDir = dir.get(stackDir).toPath();

			if (!subdir.isEmpty())
				assetDir = assetDir.resolve(subdir);

			if (!Files.exists(assetDir) || !Files.isDirectory(assetDir)) {
				continue;
			}

			// only single directory depth allowed, we can use DirectoryStream instead of Files.walk
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(assetDir, "*" + ext)) {
				for (Path file : stream) {
					String filename = file.getFileName().toString();

					if (shouldAccept != null && !shouldAccept.test(file))
						continue;

					String relPath = dir + subdir + filename;
					AssetHandle ah = new AssetHandle(stackDir, relPath);

					// only add first occurance down the asset stack traversal
					assetMap.putIfAbsent(filename, ah);
				}
			}
			catch (IOException e) {
				Logger.logError("Failed to read directory: " + assetDir);
			}
		}

		return assetMap;
	}

	public static Collection<AssetHandle> getIcons() throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, AssetHandle> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
			File iconDir = AssetSubdir.ICON.get(assetDir);
			if (!iconDir.exists())
				continue;

			Path dirPath = iconDir.toPath();

			for (File file : IOUtils.getFilesWithExtension(iconDir, EXT_PNG, true)) {
				Path filePath = file.toPath();
				Path relativePath = dirPath.relativize(filePath);
				String relativeString = relativePath.toString();

				if (relativeString.endsWith(".disabled.png")) {
					continue;
				}

				AssetHandle ah = new AssetHandle(assetDir, AssetSubdir.ICON + relativeString);
				if (!assetMap.containsKey(relativeString)) {
					assetMap.put(relativeString, ah);
				}
			}
		}

		return assetMap.values();
	}
}
