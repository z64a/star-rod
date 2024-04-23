package assets;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import app.Environment;
import app.input.IOUtils;

public class AssetManager
{
	public static File getTopLevelAssetDir()
	{ return Environment.assetDirectories.get(0); }

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
	{ return AssetSubdir.MAP_TEX.getModDir(); }

	public static AssetHandle getMap(String mapName)
	{
		return get(AssetSubdir.MAP_GEOM, mapName + EXT_MAP);
	}

	public static File getSaveMapFile(String mapName)
	{
		return new File(getMapBuildDir(), mapName + EXT_MAP);
	}

	public static File getMapBuildDir()
	{ return AssetSubdir.MAP_GEOM.getModDir(); }

	public static AssetHandle getBackground(String bgName)
	{
		return get(AssetSubdir.MAP_BG, bgName + EXT_PNG);
	}

	public static File getBackgroundBuildDir()
	{ return AssetSubdir.MAP_BG.getModDir(); }

	public static AssetHandle getNpcSprite(String spriteName)
	{
		return get(AssetSubdir.NPC_SPRITE, spriteName + "/" + FN_SPRITESHEET);
	}

	public static Map<String, AssetHandle> getNpcSpriteRasters(String spriteName) throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, AssetHandle> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
			File npcDir = AssetSubdir.NPC_SPRITE.get(assetDir);
			File imgDir = new File(npcDir, spriteName + "/rasters/");
			if (!imgDir.exists())
				continue;

			for (File file : IOUtils.getFilesWithExtension(imgDir, EXT_PNG, false)) {
				String filename = file.getName();
				String relPath = AssetSubdir.NPC_SPRITE + spriteName + "/rasters/" + filename;

				AssetHandle ah = new AssetHandle(assetDir, relPath);
				if (!assetMap.containsKey(filename)) {
					assetMap.put(filename, ah);
				}
			}
		}

		return assetMap;
	}

	public static Map<String, AssetHandle> getNpcSpritePalettes(String spriteName) throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, AssetHandle> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
			File npcDir = AssetSubdir.NPC_SPRITE.get(assetDir);
			File imgDir = new File(npcDir, spriteName + "/palettes/");
			if (!imgDir.exists())
				continue;

			for (File file : IOUtils.getFilesWithExtension(imgDir, EXT_PNG, false)) {
				String filename = file.getName();
				String relPath = AssetSubdir.NPC_SPRITE + spriteName + "/palettes/" + filename;

				AssetHandle ah = new AssetHandle(assetDir, relPath);
				if (!assetMap.containsKey(filename)) {
					assetMap.put(filename, ah);
				}
			}
		}

		return assetMap;
	}

	public static AssetHandle getPlayerSprite(String spriteName)
	{
		return get(AssetSubdir.PLR_SPRITE, spriteName + EXT_SPRITE);
	}

	public static Map<String, AssetHandle> getPlayerSpriteRasters() throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, AssetHandle> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
			File imgDir = AssetSubdir.PLR_SPRITE_IMG.get(assetDir);
			if (!imgDir.exists())
				continue;

			for (File file : IOUtils.getFilesWithExtension(imgDir, EXT_PNG, false)) {
				String filename = file.getName();
				String relPath = AssetSubdir.PLR_SPRITE_IMG + filename;

				AssetHandle ah = new AssetHandle(assetDir, relPath);
				if (!assetMap.containsKey(filename)) {
					assetMap.put(filename, ah);
				}
			}
		}

		return assetMap;
	}

	public static Map<String, AssetHandle> getPlayerSpritePalettes() throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, AssetHandle> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
			File imgDir = AssetSubdir.PLR_SPRITE_PAL.get(assetDir);
			if (!imgDir.exists())
				continue;

			for (File file : IOUtils.getFilesWithExtension(imgDir, EXT_PNG, false)) {
				String filename = file.getName();
				String relPath = AssetSubdir.PLR_SPRITE_PAL + filename;

				AssetHandle ah = new AssetHandle(assetDir, relPath);
				if (!assetMap.containsKey(filename)) {
					assetMap.put(filename, ah);
				}
			}
		}

		return assetMap;
	}

	public static Collection<AssetHandle> getMapSources() throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, AssetHandle> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
			File mapDir = AssetSubdir.MAP_GEOM.get(assetDir);
			if (!mapDir.exists())
				continue;

			for (File file : IOUtils.getFilesWithExtension(mapDir, EXT_MAP, false)) {
				String filename = file.getName();
				if (filename.endsWith(MAP_CRASH_SUFFIX)) {
					continue;
				}
				if (filename.endsWith(MAP_BACKUP_SUFFIX)) {
					continue;
				}
				AssetHandle ah = new AssetHandle(assetDir, AssetSubdir.MAP_GEOM + filename);
				if (!assetMap.containsKey(filename)) {
					assetMap.put(filename, ah);
				}
			}
		}

		return assetMap.values();
	}

	public static Collection<AssetHandle> getBackgrounds() throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, AssetHandle> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
			File bgDir = AssetSubdir.MAP_BG.get(assetDir);
			if (!bgDir.exists())
				continue;

			for (File file : IOUtils.getFilesWithExtension(bgDir, EXT_PNG, false)) {
				String filename = file.getName();
				AssetHandle ah = new AssetHandle(assetDir, AssetSubdir.MAP_BG + filename);
				if (!assetMap.containsKey(filename)) {
					assetMap.put(filename, ah);
				}
			}
		}

		return assetMap.values();
	}

	public static Collection<AssetHandle> getLegacyTextureArchives() throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, AssetHandle> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
			File texDir = AssetSubdir.MAP_TEX.get(assetDir);
			if (!texDir.exists())
				continue;

			for (File file : IOUtils.getFilesWithExtension(texDir, EXT_OLD_TEX, false)) {
				String filename = file.getName();
				AssetHandle ah = new AssetHandle(assetDir, AssetSubdir.MAP_TEX + filename);
				if (!assetMap.containsKey(filename)) {
					assetMap.put(filename, ah);
				}
			}
		}

		return assetMap.values();
	}

	public static Collection<AssetHandle> getTextureArchives() throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, AssetHandle> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
			File texDir = AssetSubdir.MAP_TEX.get(assetDir);
			if (!texDir.exists())
				continue;

			for (File file : IOUtils.getFilesWithExtension(texDir, EXT_NEW_TEX, false)) {
				String filename = file.getName();
				AssetHandle ah = new AssetHandle(assetDir, AssetSubdir.MAP_TEX + filename);
				if (!assetMap.containsKey(filename)) {
					assetMap.put(filename, ah);
				}
			}
		}

		return assetMap.values();
	}

	public static Collection<AssetHandle> getMessages() throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, AssetHandle> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
			File msgDir = AssetSubdir.MSG.get(assetDir);
			if (!msgDir.exists())
				continue;

			for (File file : IOUtils.getFilesWithExtension(msgDir, EXT_MSG, false)) {
				String filename = file.getName();
				AssetHandle ah = new AssetHandle(assetDir, AssetSubdir.MSG + filename);
				if (!assetMap.containsKey(filename)) {
					assetMap.put(filename, ah);
				}
			}
		}

		return assetMap.values();
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
