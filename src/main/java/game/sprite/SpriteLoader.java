package game.sprite;

import static game.sprite.SpriteKey.ATTR_SPRITE_HAS_BACK;
import static game.sprite.SpriteTableKey.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.w3c.dom.Element;

import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetSubdir;
import game.sprite.Sprite.SpriteSummary;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;

public class SpriteLoader
{
	// represents an object with an ID
	public static interface Indexable<T>
	{
		public T getObject();

		public int getIndex();
	}

	public static class SpriteMetadata implements Indexable<String>
	{
		public final int id;
		public final String name;
		public final boolean hasBack;
		private final File xml;

		private SpriteMetadata(int id, String name, File xml, boolean hasBack)
		{
			this.id = id;
			this.name = name;
			this.xml = xml;
			this.hasBack = hasBack;
		}

		@Override
		public String getObject()
		{ return name; }

		@Override
		public int getIndex()
		{ return id; }

		public long lastModified()
		{
			return xml.lastModified();
		}
	}

	public static enum SpriteSet
	{
		Player, Npc
	}

	private static boolean loaded = false;

	private static TreeMap<Integer, SpriteMetadata> playerSpriteData = null;
	private static TreeMap<Integer, SpriteMetadata> npcSpriteData = null;

	private HashMap<Integer, Sprite> playerSpriteCache = new HashMap<>();
	private HashMap<Integer, Sprite> npcSpriteCache = new HashMap<>();

	private boolean loadedPlayerAssets = false;
	private LinkedHashMap<String, ImgAsset> playerImgAssets;
	private LinkedHashMap<String, PalAsset> playerPalAssets;

	public static void initialize()
	{
		if (!loaded) {
			readSpriteTable();
			loaded = true;
		}
	}

	public SpriteLoader()
	{
		initialize();
	}

	private static TreeMap<Integer, SpriteMetadata> getMap(SpriteSet set)
	{
		switch (set) {
			case Npc:
				return npcSpriteData;
			case Player:
				return playerSpriteData;
		}
		throw new IllegalArgumentException("Unknown sprite set: " + set);
	}

	public static Collection<SpriteMetadata> getValidSprites(SpriteSet set)
	{
		if (!loaded)
			throw new IllegalStateException("getValidSprites invoked before initializing SpriteLoader!");

		TreeMap<Integer, SpriteMetadata> spriteMap = getMap(set);
		return spriteMap.values();
	}

	public Sprite getSprite(SpriteSet set, int id)
	{
		return getSprite(set, id, false);
	}

	public Sprite getSprite(SpriteSet set, int id, boolean forceReload)
	{
		if (!loaded)
			throw new IllegalStateException("getSprite invoked before initializing SpriteLoader!");

		Sprite spr = null;
		switch (set) {
			case Npc:
				spr = getNpcSprite(id, forceReload);
				break;
			case Player:
				spr = getPlayerSprite(id, forceReload);
				break;
			default:
				throw new IllegalArgumentException("Unknown sprite set: " + set);
		}

		if (spr != null) {
			spr.assignRasterPalettes();
		}
		return spr;
	}

	private Sprite getNpcSprite(int id, boolean forceReload)
	{
		if (!forceReload && npcSpriteCache.containsKey(id))
			return npcSpriteCache.get(id);

		if (!npcSpriteData.containsKey(id))
			return null;

		SpriteMetadata md = npcSpriteData.get(id);
		File xmlFile = md.xml;
		Sprite npcSprite = null;

		try {
			npcSprite = Sprite.readNpc(xmlFile, md.name);
			npcSprite.imgAssets = loadSpriteImages(AssetManager.getNpcSpriteRasters(md.name));
			npcSprite.palAssets = loadSpritePalettes(AssetManager.getNpcSpritePalettes(md.name));
			npcSprite.bindPalettes();
			npcSprite.bindRasters();
			npcSprite.recalculateIndices();
			npcSpriteCache.put(id, npcSprite);
		}
		catch (Throwable e) {
			Logger.logWarning("Error while loading NPC sprite! " + e.getMessage());
			e.printStackTrace();
			return null;
		}

		return npcSprite;
	}

	private Sprite getPlayerSprite(int id, boolean forceReload)
	{
		Sprite playerSprite = null;

		if (!forceReload && playerSpriteCache.containsKey(id))
			return playerSpriteCache.get(id);

		if (!playerSpriteData.containsKey(id))
			return null;

		tryLoadingPlayerAssets(forceReload);

		SpriteMetadata md = playerSpriteData.get(id);
		File xmlFile = md.xml;

		try {
			playerSprite = Sprite.readPlayer(xmlFile, md.name);
			playerSprite.imgAssets = playerImgAssets;
			playerSprite.palAssets = playerPalAssets;
			playerSprite.bindPalettes();
			playerSprite.bindRasters();
			playerSprite.recalculateIndices();
			playerSpriteCache.put(id, playerSprite);
		}
		catch (Throwable e) {
			Logger.logWarning("Error while loading player sprite " + md.id + "! " + e.getMessage());
			e.printStackTrace();
			playerSpriteData.remove(id); // file is invalid
		}

		return playerSprite;
	}

	public void tryLoadingPlayerAssets(boolean force)
	{
		if (!loadedPlayerAssets || force) {
			try {
				playerImgAssets = loadSpriteImages(AssetManager.getPlayerSpriteRasters());
				playerPalAssets = loadSpritePalettes(AssetManager.getPlayerSpritePalettes());
				loadedPlayerAssets = true;
			}
			catch (IOException e) {
				Logger.logError("IOException while loading player images: " + e.getMessage());
			}
		}
	}

	// load all raster assets
	private static LinkedHashMap<String, ImgAsset> loadSpriteImages(Map<String, AssetHandle> assets)
	{
		LinkedHashMap<String, ImgAsset> imgAssets = new LinkedHashMap<>();

		for (Entry<String, AssetHandle> entry : assets.entrySet()) {
			String name = entry.getKey();
			AssetHandle ah = entry.getValue();

			try {
				ImgAsset ia = new ImgAsset(ah);
				imgAssets.put(name, ia);
			}
			catch (Throwable e) {
				Logger.logWarning("Failed to load raster: " + ah.getName());
			}
		}

		return imgAssets;
	}

	// load all palette assets
	public static LinkedHashMap<String, PalAsset> loadSpritePalettes(Map<String, AssetHandle> assets)
	{
		LinkedHashMap<String, PalAsset> palAssets = new LinkedHashMap<>();

		for (Entry<String, AssetHandle> entry : assets.entrySet()) {
			String name = entry.getKey();
			AssetHandle ah = entry.getValue();

			try {
				PalAsset pa = new PalAsset(ah);
				palAssets.put(name, pa);
			}
			catch (IOException e) {
				Logger.logWarning("Failed to load palette: " + ah.getName());
			}
		}

		return palAssets;
	}

	private static void readSpriteTable()
	{
		npcSpriteData = new TreeMap<>();
		playerSpriteData = new TreeMap<>();

		try {
			AssetHandle xmlHandle = AssetManager.get(AssetSubdir.SPRITE, "npc.xml");
			if (!xmlHandle.exists()) {
				throw new IOException(xmlHandle.assetPath + " does not exist!");
			}

			XmlReader xmr = new XmlReader(xmlHandle);
			Element rootElem = xmr.getRootElement();

			int curID = 1;
			Element npcListElem = xmr.getUniqueRequiredTag(rootElem, TAG_SPRITES);
			List<Element> npcElems = xmr.getTags(npcListElem, TAG_SPRITE);
			for (Element npcElem : npcElems) {
				xmr.requiresAttribute(npcElem, ATTR_NAME);

				String name = xmr.getAttribute(npcElem, ATTR_NAME);
				AssetHandle ah = AssetManager.getNpcSprite(name);
				if (!ah.exists()) {
					Logger.logWarning("Cannot find npc sprite '" + name + "'!");
					continue;
				}

				npcSpriteData.put(curID, new SpriteMetadata(curID, name, ah, false));
				curID++;
			}
		}
		catch (Exception e) {
			Logger.logWarning("Error while loading npc sprite list: \n" + e.getMessage());
			e.printStackTrace();
		}

		try {
			AssetHandle xmlHandle = AssetManager.get(AssetSubdir.SPRITE, "player.xml");
			if (!xmlHandle.exists()) {
				throw new IOException(xmlHandle.assetPath + " does not exist!");
			}

			XmlReader xmr = new XmlReader(xmlHandle);
			Element rootElem = xmr.getRootElement();

			int curID = 1;
			Element playerListElem = xmr.getUniqueRequiredTag(rootElem, TAG_SPRITES);
			List<Element> playerElems = xmr.getTags(playerListElem, TAG_SPRITE);
			for (Element playerElem : playerElems) {
				xmr.requiresAttribute(playerElem, ATTR_NAME);

				String name = xmr.getAttribute(playerElem, ATTR_NAME);
				AssetHandle ah = AssetManager.getPlayerSprite(name);
				if (!ah.exists()) {
					Logger.logWarning("Cannot find player sprite '" + name + "'!");
					continue;
				}

				XmlReader spriteXmr = new XmlReader(ah);
				Element spriteRoot = spriteXmr.getRootElement();

				boolean hasBack = false;
				if (spriteXmr.hasAttribute(spriteRoot, ATTR_SPRITE_HAS_BACK)) {
					hasBack = spriteXmr.readBoolean(spriteRoot, ATTR_SPRITE_HAS_BACK);
				}

				playerSpriteData.put(curID, new SpriteMetadata(curID, name, ah, hasBack));
				curID++;
				if (hasBack) {
					curID++; // consume extra ID for back sprite
				}
			}
		}
		catch (Exception e) {
			Logger.logWarning("Error while loading player sprite list: \n" + e.getMessage());
			e.printStackTrace();
		}

		Logger.log("Loaded sprite lists.");
	}

	public static class AnimMetadata
	{
		public final String enumName;
		public final String name;
		public final String spriteName;
		public final String palName;
		public final int spriteIndex;
		public final int palIndex;
		public final int animIndex;
		public final int animID;
		public final int uniqueID;
		public final boolean isPlayer;

		private AnimMetadata(String spriteName, int spriteIndex, String palName,
			int palIndex, String animName, int animIndex, boolean isPlayer)
		{
			this.isPlayer = isPlayer;
			this.spriteName = spriteName;
			this.palName = palName;
			this.name = animName;
			this.spriteIndex = spriteIndex;
			this.palIndex = palIndex;
			this.animIndex = animIndex;
			this.uniqueID = makeID(spriteIndex, palIndex, animIndex, isPlayer);
			this.animID = uniqueID & 0xFFFFFF;

			if (isPlayer ? palIndex == 0 : palName.equals("Default"))
				enumName = String.format("ANIM_%s_%s", spriteName, animName);
			else
				enumName = String.format("ANIM_%s_%s_%s", spriteName, palName, animName);
		}
	}

	public static int makeID(int spriteIndex, int palIndex, int animIndex, boolean isPlayer)
	{
		int id = ((spriteIndex & 0xFF) << 16) | ((palIndex & 0xFF) << 8) | (animIndex & 0xFF);
		if (isPlayer)
			id |= (1 << 24);
		return id;
	}

	private static boolean animMetadataLoaded = false;
	private static HashMap<String, AnimMetadata> animNamesMap = null;
	private static HashMap<Integer, AnimMetadata> animIDsMap = null;

	public static void loadAnimsMetadata(boolean force)
	{
		if (force || !animMetadataLoaded) {
			initialize();
			animNamesMap = new HashMap<>();
			animIDsMap = new HashMap<>();
			putAnimsEnum(npcSpriteData.values(), false);
			putAnimsEnum(playerSpriteData.values(), true);
			animMetadataLoaded = true;
		}
	}

	private static void putAnimsEnum(Iterable<SpriteMetadata> sprites, boolean isPlayer)
	{
		for (SpriteMetadata mdata : sprites) {
			SpriteSummary summary = Sprite.readSummary(mdata.xml, mdata.name);
			int spriteID = mdata.id;

			for (int palID = 0; palID < summary.palettes.size(); palID++) {
				String palName = summary.palettes.get(palID);
				for (int animID = 0; animID < summary.animations.size(); animID++) {
					String animName = summary.animations.get(animID);

					AnimMetadata anim = new AnimMetadata(mdata.name, spriteID, palName, palID, animName, animID, isPlayer);
					animNamesMap.put(anim.enumName, anim);
					animIDsMap.put(anim.uniqueID, anim);
				}
			}
		}
	}

	public static AnimMetadata getAnimMetadata(String enumName)
	{
		return animNamesMap.get(enumName);
	}

	public static AnimMetadata getAnimMetadata(int spriteIndex, int palIndex, int animIndex, boolean isPlayer)
	{
		return animIDsMap.get(makeID(spriteIndex, palIndex, animIndex, isPlayer));
	}
}
