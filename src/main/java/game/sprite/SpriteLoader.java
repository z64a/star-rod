package game.sprite;

import static game.sprite.SpriteKey.ATTR_SPRITE_HAS_BACK;
import static game.sprite.SpriteTableKey.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.w3c.dom.Element;

import app.Environment;
import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetSubdir;
import game.sprite.Sprite.SpriteSummary;
import game.sprite.editor.SpriteAssetCollection;
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

	/**
	 * Represent entries in the sprite list files npc.xml and player.xml
	 * These associate sprite IDs with directories and hold a reference to the loaded sprites.
	 */
	public static class SpriteMetadata implements Indexable<String>
	{
		public final int id;
		public final String name;
		public final boolean isPlayer;
		public final boolean hasBack;
		private final File xml;

		public transient Sprite loadedSprite;

		private SpriteMetadata(int id, String name, File xml, boolean isPlayer, boolean hasBack)
		{
			this.id = id;
			this.name = name;
			this.xml = xml;
			this.isPlayer = isPlayer;
			this.hasBack = hasBack;
		}

		@Override
		public String getObject()
		{
			return name;
		}

		@Override
		public int getIndex()
		{
			return id;
		}

		public long lastModified()
		{
			return xml.lastModified();
		}

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + id;
			result = prime * result + (isPlayer ? 1231 : 1237);
			return result;
		}
	}

	public static enum SpriteSet
	{
		Player, Npc
	}

	private static boolean loaded = false;

	private static TreeMap<Integer, SpriteMetadata> playerSpriteData = null;
	private static TreeMap<Integer, SpriteMetadata> npcSpriteData = null;

	private HashMap<SpriteMetadata, Sprite> playerSpriteCache = new HashMap<>();
	private HashMap<SpriteMetadata, Sprite> npcSpriteCache = new HashMap<>();

	private boolean loadedPlayerAssets = false;
	private final SpriteAssetCollection<ImgAsset> playerImgAssets = new SpriteAssetCollection<>();
	private final SpriteAssetCollection<PalAsset> playerPalAssets = new SpriteAssetCollection<>();

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

	private SpriteMetadata getMetadata(SpriteSet set, int id)
	{
		return getMap(set).get(id);
	}

	public static Collection<SpriteMetadata> getValidSprites(SpriteSet set)
	{
		if (!loaded)
			throw new IllegalStateException("getValidSprites invoked before initializing SpriteLoader!");

		return getMap(set).values();
	}

	public Sprite getSprite(SpriteSet set, int id)
	{
		SpriteMetadata metadata = getMetadata(set, id);
		if (metadata == null) {
			Logger.logfError("Unknown sprite: %s %02X", set, id);
			return null;
		}

		return getSprite(metadata, false);
	}

	public Sprite getSprite(SpriteMetadata metadata, boolean forceReload)
	{
		if (!loaded)
			throw new IllegalStateException("getSprite invoked before initializing SpriteLoader!");

		Sprite spr = null;

		if (metadata.isPlayer)
			spr = getPlayerSprite(metadata, forceReload);
		else
			spr = getNpcSprite(metadata, forceReload);

		metadata.loadedSprite = spr;

		return spr;
	}

	private Sprite getNpcSprite(SpriteMetadata md, boolean forceReload)
	{
		if (!forceReload && npcSpriteCache.containsKey(md))
			return npcSpriteCache.get(md);

		File xmlFile = md.xml;
		Sprite npcSprite = null;

		try {
			npcSprite = Sprite.readNpc(md, xmlFile, md.name);
			npcSprite.imgAssets.set(loadSpriteImages(AssetManager.getNpcSpriteRasters(md.name)));
			npcSprite.palAssets.set(loadSpritePalettes(AssetManager.getNpcSpritePalettes(md.name)));

			npcSprite.bindPalettes();
			npcSprite.bindRasters();
			npcSprite.reindex();
			npcSpriteCache.put(md, npcSprite);
		}
		catch (Throwable e) {
			Logger.logWarning("Error while loading NPC sprite! " + e.getMessage());
			e.printStackTrace();
			return null;
		}

		return npcSprite;
	}

	private Sprite getPlayerSprite(SpriteMetadata md, boolean forceReload)
	{
		Sprite playerSprite = null;

		if (!forceReload && playerSpriteCache.containsKey(md))
			return playerSpriteCache.get(md);

		tryLoadingPlayerAssets(forceReload);

		File xmlFile = md.xml;

		try {
			playerSprite = Sprite.readPlayer(md, xmlFile, md.name);
			playerSprite.imgAssets = playerImgAssets;
			playerSprite.palAssets = playerPalAssets;
			playerSprite.bindPalettes();
			playerSprite.bindRasters();
			playerSprite.reindex();
			playerSpriteCache.put(md, playerSprite);
		}
		catch (Throwable e) {
			Logger.logWarning("Error while loading player sprite " + md.id + "! " + e.getMessage());
			e.printStackTrace();
		}

		return playerSprite;
	}

	public void tryLoadingPlayerAssets(boolean force)
	{
		if (!loadedPlayerAssets || force) {
			try {
				playerImgAssets.set(loadSpriteImages(AssetManager.getPlayerSpriteRasters()));
				playerPalAssets.set(loadSpritePalettes(AssetManager.getPlayerSpritePalettes()));
				loadedPlayerAssets = true;
			}
			catch (IOException e) {
				Logger.logError("IOException while loading player images: " + e.getMessage());
			}
		}
	}

	// load all raster assets in parallel
	public static LinkedHashMap<String, ImgAsset> loadSpriteImages(Map<String, AssetHandle> assets)
	{
		ConcurrentHashMap<String, ImgAsset> imgAssets = new ConcurrentHashMap<>();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (Entry<String, AssetHandle> entry : assets.entrySet()) {
			String name = entry.getKey();
			AssetHandle ah = entry.getValue();

			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				try {
					ImgAsset ia = new ImgAsset(ah);
					imgAssets.put(name, ia);
				}
				catch (Throwable e) {
					String assetName = ah.getName();
					if ("PSR_1F880.png".equals(assetName) || "PSR_9CD50.png".equals(assetName))
						; //TODO these assets should probably be removed in dx?
					else
						Logger.logWarning("Failed to load raster: " + assetName);
				}
			}, Environment.getExecutor());

			futures.add(future);
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		// return a sorted map
		return imgAssets.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.collect(Collectors.toMap(
				Map.Entry::getKey,
				Map.Entry::getValue,
				(e1, e2) -> e1,
				LinkedHashMap::new
			));
	}

	// load all palette assets in parallel
	public static LinkedHashMap<String, PalAsset> loadSpritePalettes(Map<String, AssetHandle> assets)
	{
		ConcurrentHashMap<String, PalAsset> palAssets = new ConcurrentHashMap<>();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (Entry<String, AssetHandle> entry : assets.entrySet()) {
			String name = entry.getKey();
			AssetHandle ah = entry.getValue();

			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				try {
					PalAsset pa = new PalAsset(ah);
					palAssets.put(name, pa);
				}
				catch (Throwable e) {
					Logger.logWarning("Failed to load palette: " + ah.getName());
				}
			}, Environment.getExecutor());

			futures.add(future);
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		// return a sorted map
		return palAssets.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.collect(Collectors.toMap(
				Map.Entry::getKey,
				Map.Entry::getValue,
				(e1, e2) -> e1,
				LinkedHashMap::new
			));
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

				npcSpriteData.put(curID, new SpriteMetadata(curID, name, ah, false, false));
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

				playerSpriteData.put(curID, new SpriteMetadata(curID, name, ah, true, hasBack));
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
