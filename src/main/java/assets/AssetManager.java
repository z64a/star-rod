package assets;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import app.Environment;
import app.input.IOUtils;
import util.Logger;

public class AssetManager
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		long t0 = System.nanoTime();

		Collection<AssetHandle> assets = getMessages();

		long t1 = System.nanoTime();
		System.out.println("Took " + ((t1 - t0) / 1e6) + " ms");

		System.out.println(assets.size());

		Environment.exit();
	}

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

	public static AssetHandle getBase(AssetSubdir subdir, String path)
	{
		return new AssetHandle(getBaseAssetDir(), subdir + path);
	}

	public static AssetHandle getTextureArchive(String texName)
	{
		return get(AssetSubdir.MAP_TEX, texName + EXT_NEW_TEX);
	}

	public static File getTexBuildDir()
	{
		return AssetSubdir.MAP_TEX.getModDir();
	}

	public static AssetHandle getMap(String mapName)
	{
		return get(AssetSubdir.MAP_GEOM, mapName + EXT_MAP);
	}

	public static File getSaveMapFile(String mapName)
	{
		return new File(getMapBuildDir(), mapName + EXT_MAP);
	}

	public static File getMapBuildDir()
	{
		return AssetSubdir.MAP_GEOM.getModDir();
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

	public static Collection<AssetHandle> getMapSources() throws IOException
	{
		return getAssets(AssetSubdir.MAP_GEOM, EXT_MAP, (p) -> {
			// skip crash and backup files
			String filename = p.getFileName().toString();
			return !(filename.endsWith(MAP_CRASH_SUFFIX) || filename.endsWith(MAP_BACKUP_SUFFIX));
		});
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
