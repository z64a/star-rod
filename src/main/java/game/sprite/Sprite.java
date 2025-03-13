package game.sprite;

import static game.sprite.SpriteKey.*;

import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.SwingUtilities;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.input.InputFileException;
import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetSubdir;
import common.Vector3f;
import game.map.BoundingBox;
import game.map.shading.ShadingProfile;
import game.sprite.SpriteLoader.SpriteMetadata;
import game.sprite.editor.Editable;
import game.sprite.editor.SpriteAssetCollection;
import game.sprite.editor.SpriteCamera;
import game.sprite.editor.SpriteCamera.BasicTraceHit;
import game.sprite.editor.SpriteCamera.BasicTraceRay;
import game.sprite.editor.SpriteEditor;
import game.texture.Palette;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.SpriteShader;
import util.IterableListModel;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Sprite implements XmlSerializable, Editable
{
	public static final float WORLD_SCALE = 0.714286f; // ~ 5.0f / 7.0f

	public static final int MAX_ANIMATIONS = 255;
	public static final int MAX_COMPONENTS = 255;

	private static final int ATLAS_TILE_PADDING = 8;
	private static final int ATLAS_SELECT_PADDING = 1;

	public final SpriteMetadata metadata;

	public final IterableListModel<SpriteAnimation> animations = new IterableListModel<>();
	public final IterableListModel<SpriteRaster> rasters = new IterableListModel<>();
	public final IterableListModel<SpritePalette> palettes = new IterableListModel<>();

	// these are 'effectively' final, but must be assigned once to the global player assets for player sprites
	public SpriteAssetCollection<ImgAsset> imgAssets = new SpriteAssetCollection<>();
	public SpriteAssetCollection<PalAsset> palAssets = new SpriteAssetCollection<>();

	public boolean usesKeyframes = false;

	private transient boolean texturesLoaded = false;
	private transient boolean readyForEditor = false;
	public transient boolean enableStencilBuffer = false;

	// rasters tab state to restore when this sprite is selected
	public transient int lastSelectedImgAsset = -1;
	public transient ImgAsset selectedImgAsset = null;
	public transient int lastSelectedRaster = -1;
	public transient SpriteRaster selectedRaster = null;
	// palettes tab state to restore when this sprite is selected
	public transient int lastSelectedPalAsset = -1;
	public transient PalAsset selectedPalAsset = null;
	public transient int lastSelectedPalette = -1;
	public transient SpritePalette selectedPalette = null;
	// animations tab state to restore when this sprite is selected
	public transient int lastSelectedAnim = -1;
	public transient boolean usingOverridePalette;
	public transient SpritePalette overridePalette = null;

	// have the animators generate their animation commands
	public void prepareForEditor()
	{
		if (readyForEditor)
			return;

		if (imgAssets.size() > 0) {
			selectedImgAsset = imgAssets.get(0);
			lastSelectedImgAsset = 0;
		}

		if (palAssets.size() > 0) {
			selectedPalAsset = palAssets.get(0);
			lastSelectedPalAsset = 0;
		}

		if (palettes.size() > 0)
			overridePalette = palettes.get(0);

		for (SpriteAnimation anim : animations)
			anim.prepareForEditor();

		if (animations.size() > 0)
			lastSelectedAnim = 0;

		reindex();

		readyForEditor = true;
	}

	/**
	 * Computes list indices for objects which need them and recomputes hasError propagation.
	 */
	public void reindex()
	{
		for (int i = 0; i < palettes.size(); i++) {
			SpritePalette pal = palettes.get(i);
			pal.listIndex = i;
		}

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster raster = rasters.get(i);
			raster.listIndex = i;
		}

		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			anim.listIndex = i;

			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);
				comp.listIndex = j;
			}
		}
	}

	public void assignDefaultAnimationNames()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			anim.name = String.format("Anim_%02X", i);
			for (int j = 0; j < anim.components.size(); j++)
				anim.components.get(j).name = String.format("Comp_%02X", j);
		}
	}

	// duplicated in SpriteMetadata
	public boolean hasBack;

	public String name = "";
	public List<String> variationNames;
	public int numVariations;
	public int maxComponents;

	private int imgAtlasH, imgAtlasW;
	private int rasterAtlasH, rasterAtlasW;

	public transient BoundingBox aabb = new BoundingBox();

	protected Sprite(SpriteMetadata metadata)
	{
		this.metadata = metadata;
	}

	@Override
	public String toString()
	{
		return name.isEmpty() ? "Unnamed" : name;
	}

	public AssetHandle getAsset()
	{
		if (metadata.isPlayer)
			return AssetManager.getPlayerSprite(name);
		else
			return AssetManager.getNpcSprite(name);
	}

	public AssetHandle getAssetDir(boolean modDir)
	{
		AssetHandle ah;

		if (metadata.isPlayer)
			ah = AssetManager.getBase(AssetSubdir.PLR_SPRITE, "");
		else
			ah = AssetManager.getBase(AssetSubdir.NPC_SPRITE, name);

		if (modDir)
			return AssetManager.getTopLevel(ah);
		else
			return ah;
	}

	public AssetHandle getRastersDir(boolean modDir)
	{
		AssetHandle ah;

		if (metadata.isPlayer)
			ah = AssetManager.get(AssetSubdir.PLR_SPRITE_IMG, "");
		else
			ah = AssetManager.get(AssetSubdir.NPC_SPRITE, name + "/rasters/");

		if (modDir)
			return AssetManager.getTopLevel(ah);
		else
			return ah;
	}

	public AssetHandle getPalettesDir(boolean modDir)
	{
		AssetHandle ah;

		if (metadata.isPlayer)
			ah = AssetManager.get(AssetSubdir.PLR_SPRITE_PAL, "");
		else
			ah = AssetManager.get(AssetSubdir.NPC_SPRITE, name + "/palettes/");

		if (modDir)
			return AssetManager.getTopLevel(ah);
		else
			return ah;
	}

	public static Sprite readNpc(SpriteMetadata metadata, File xmlFile, String name)
	{
		XmlReader xmr = new XmlReader(xmlFile);
		Sprite spr = new Sprite(metadata);
		spr.name = name;
		spr.fromXML(xmr, xmr.getRootElement());
		return spr;
	}

	public static Sprite readPlayer(SpriteMetadata metadata, File xmlFile, String name)
	{
		XmlReader xmr = new XmlReader(xmlFile);
		Sprite spr = new Sprite(metadata);
		spr.name = name;
		spr.fromXML(xmr, xmr.getRootElement());
		return spr;
	}

	public static class SpriteSummary
	{
		public final String name;
		public final List<String> palettes;
		public final List<String> animations;

		public SpriteSummary(String name)
		{
			this.name = name;
			palettes = new ArrayList<>();
			animations = new ArrayList<>();
		}
	}

	public static SpriteSummary readSummary(File xmlFile, String name)
	{
		SpriteSummary summary = new SpriteSummary(name);

		XmlReader xmr = new XmlReader(xmlFile);
		Element spriteElem = xmr.getRootElement();

		Element palettesElem = xmr.getUniqueRequiredTag(spriteElem, TAG_PALETTE_LIST);
		List<Element> paletteElems = xmr.getRequiredTags(palettesElem, TAG_PALETTE);
		for (int i = 0; i < paletteElems.size(); i++) {
			Element paletteElem = paletteElems.get(i);

			xmr.requiresAttribute(paletteElem, ATTR_SOURCE);
			String filename = xmr.getAttribute(paletteElem, ATTR_SOURCE);
			summary.palettes.add(FilenameUtils.removeExtension(filename));
		}

		Element animationsElem = xmr.getUniqueRequiredTag(spriteElem, TAG_ANIMATION_LIST);
		List<Element> animationElems = xmr.getTags(animationsElem, TAG_ANIMATION);
		for (Element animationElem : animationElems) {
			xmr.requiresAttribute(animationElem, ATTR_NAME);
			summary.animations.add(xmr.getAttribute(animationElem, ATTR_NAME));
		}

		return summary;
	}

	@Override
	public void fromXML(XmlReader xmr, Element spriteElem)
	{
		File source = xmr.getSourceFile();

		// read root attributes

		if (xmr.hasAttribute(spriteElem, ATTR_SPRITE_NUM_COMPONENTS)) {
			maxComponents = xmr.readHex(spriteElem, ATTR_SPRITE_NUM_COMPONENTS);
		}
		else {
			xmr.requiresAttribute(spriteElem, ATTR_SPRITE_A);
			maxComponents = xmr.readHex(spriteElem, ATTR_SPRITE_A);
		}

		if (xmr.hasAttribute(spriteElem, ATTR_SPRITE_NUM_VARIATIONS)) {
			numVariations = xmr.readHex(spriteElem, ATTR_SPRITE_NUM_VARIATIONS);
		}
		else {
			xmr.requiresAttribute(spriteElem, ATTR_SPRITE_B);
			numVariations = xmr.readHex(spriteElem, ATTR_SPRITE_B);
		}

		if (xmr.hasAttribute(spriteElem, ATTR_SPRITE_VARIATIONS)) {
			variationNames = xmr.readStringList(spriteElem, ATTR_SPRITE_VARIATIONS);
		}

		if (xmr.hasAttribute(spriteElem, ATTR_KEYFRAMES)) {
			usesKeyframes = xmr.readBoolean(spriteElem, ATTR_KEYFRAMES);
		}

		if (metadata.isPlayer && xmr.hasAttribute(spriteElem, ATTR_SPRITE_HAS_BACK)) {
			hasBack = xmr.readBoolean(spriteElem, ATTR_SPRITE_HAS_BACK);
		}

		// read palettes list

		Element palettesElem = xmr.getUniqueRequiredTag(spriteElem, TAG_PALETTE_LIST);
		List<Element> paletteElems = xmr.getRequiredTags(palettesElem, TAG_PALETTE);
		for (int i = 0; i < paletteElems.size(); i++) {
			Element paletteElem = paletteElems.get(i);
			SpritePalette pal = new SpritePalette(this);

			xmr.requiresAttribute(paletteElem, ATTR_ID);
			int id = xmr.readHex(paletteElem, ATTR_ID);

			if (id != i)
				throw new InputFileException(source, "Palettes are out of order!");

			xmr.requiresAttribute(paletteElem, ATTR_SOURCE);
			pal.filename = xmr.getAttribute(paletteElem, ATTR_SOURCE);

			if (xmr.hasAttribute(paletteElem, ATTR_NAME)) {
				pal.name = xmr.getAttribute(paletteElem, ATTR_NAME);
			}
			else {
				pal.name = FilenameUtils.removeExtension(pal.filename);
			}

			if (xmr.hasAttribute(paletteElem, ATTR_FRONT_ONLY)) {
				pal.frontOnly = xmr.readBoolean(paletteElem, ATTR_FRONT_ONLY);
			}

			palettes.addElement(pal);
		}

		// read rasters list

		Element rastersElem = xmr.getUniqueRequiredTag(spriteElem, TAG_RASTER_LIST);
		List<Element> rasterElems = xmr.getTags(rastersElem, TAG_RASTER);
		for (int i = 0; i < rasterElems.size(); i++) {
			Element rasterElem = rasterElems.get(i);
			SpriteRaster sr = new SpriteRaster(this);

			xmr.requiresAttribute(rasterElem, ATTR_ID);
			int id = xmr.readHex(rasterElem, ATTR_ID);

			if (id != i)
				throw new InputFileException(source, "Rasters are out of order!");

			if (xmr.hasAttribute(rasterElem, ATTR_NAME))
				sr.name = xmr.getAttribute(rasterElem, ATTR_NAME);

			xmr.requiresAttribute(rasterElem, ATTR_SOURCE);
			sr.front.filename = xmr.getAttribute(rasterElem, ATTR_SOURCE);

			if (sr.name.isEmpty()) {
				sr.name = FilenameUtils.removeExtension(sr.front.filename);
			}

			xmr.requiresAttribute(rasterElem, ATTR_PALETTE);
			int frontPalID = xmr.readHex(rasterElem, ATTR_PALETTE);

			sr.back.filename = "";
			int backPalID = frontPalID;

			if (hasBack) {
				if (xmr.hasAttribute(rasterElem, ATTR_BACK)) {
					sr.back.filename = xmr.getAttribute(rasterElem, ATTR_BACK);
					sr.hasIndependentBack = true;
				}
				else if (xmr.hasAttribute(rasterElem, ATTR_SPECIAL_SIZE)) {
					int[] size = xmr.readHexArray(rasterElem, ATTR_SPECIAL_SIZE, 2);
					sr.specialWidth = size[0];
					sr.specialHeight = size[1];
					sr.hasIndependentBack = false;
				}
				else {
					throw new InputFileException(source, "Raster requires 'back' or 'special' for sprite supporting back-facing");
				}

				// this can be set independently of the back filename
				if (xmr.hasAttribute(rasterElem, ATTR_BACK_PAL)) {
					backPalID = xmr.readHex(rasterElem, ATTR_BACK_PAL);
				}
			}

			if (frontPalID >= palettes.size())
				throw new InputFileException(source, "Palette is out of range for raster %02X: %X", i, frontPalID);
			sr.front.pal = palettes.get(frontPalID);

			if (hasBack) {
				if (backPalID >= palettes.size())
					throw new InputFileException(source, "Palette is out of range for raster %02X: %X", i, frontPalID);
				sr.back.pal = palettes.get(backPalID);
			}

			rasters.addElement(sr);
		}

		// read animations list

		HashMap<String, SpriteRaster> imgMap = new HashMap<>();
		HashMap<String, SpritePalette> palMap = new HashMap<>();
		HashMap<String, SpriteComponent> compMap = new HashMap<>();

		for (SpriteRaster img : rasters) {
			imgMap.put(img.name, img);
		}

		for (SpritePalette pal : palettes) {
			palMap.put(pal.name, pal);
		}

		Element animationsElem = xmr.getUniqueRequiredTag(spriteElem, TAG_ANIMATION_LIST);
		List<Element> animationElems = xmr.getTags(animationsElem, TAG_ANIMATION);
		for (Element animationElem : animationElems) {
			SpriteAnimation anim = new SpriteAnimation(this);
			animations.addElement(anim);

			if (xmr.hasAttribute(animationElem, ATTR_NAME))
				anim.name = xmr.getAttribute(animationElem, ATTR_NAME);

			compMap.clear();

			List<Element> componentElems = xmr.getRequiredTags(animationElem, TAG_COMPONENT);
			for (Element componentElem : componentElems) {
				SpriteComponent comp = new SpriteComponent(anim);
				anim.components.addElement(comp);
				compMap.put(comp.name, comp);
				comp.fromXML(xmr, componentElem);
			}

			for (SpriteComponent comp : anim.components) {
				comp.updateReferences(imgMap, palMap, compMap);
			}
		}
	}

	public void reloadRasterAssets()
	{
		assert (SwingUtilities.isEventDispatchThread());

		for (ImgAsset img : imgAssets) {
			SpriteEditor.instance().queueDeleteResource(img);
		}

		try {
			if (metadata.isPlayer) {
				imgAssets.set(SpriteLoader.loadSpriteImages(AssetManager.getPlayerSpriteRasters()));
			}
			else {
				imgAssets.set(SpriteLoader.loadSpriteImages(AssetManager.getNpcSpriteRasters(name)));
			}

			Logger.log("Loaded " + imgAssets.size() + " assets");
		}
		catch (IOException e) {
			Logger.logError("IOException while reloading palettes: " + e.getMessage());
			Toolkit.getDefaultToolkit().beep();
		}

		for (ImgAsset img : imgAssets) {
			SpriteEditor.instance().queueLoadResource(img);
		}

		// assign new ImgAssets to SpriteRasters
		bindRasters();

		loadEditorImages();
	}

	public void reloadPaletteAssets()
	{
		assert (SwingUtilities.isEventDispatchThread());

		for (PalAsset pal : palAssets) {
			SpriteEditor.instance().queueDeleteResource(pal);
		}

		try {
			if (metadata.isPlayer) {
				palAssets.set(SpriteLoader.loadSpritePalettes(AssetManager.getPlayerSpritePalettes()));
			}
			else {
				palAssets.set(SpriteLoader.loadSpritePalettes(AssetManager.getNpcSpritePalettes(name)));
			}

			Logger.log("Loaded " + palAssets.size() + " assets");
		}
		catch (IOException e) {
			Logger.logError("IOException while reloading palettes: " + e.getMessage());
			Toolkit.getDefaultToolkit().beep();
		}

		for (PalAsset pal : palAssets) {
			SpriteEditor.instance().queueLoadResource(pal);
		}

		// assign new PalAssets to SpritePalettes
		bindPalettes();

		for (SpriteRaster raster : rasters) {
			raster.loadEditorImages();
		}
	}

	/**
	 * Connect SpritePalettes to PalAssets by filename lookup
	 */
	public void bindPalettes()
	{
		for (SpritePalette sp : palettes) {
			PalAsset asset = palAssets.get(sp.filename);

			if (asset == null) {
				Logger.logWarning("Can't find palette: " + sp.filename);
				sp.asset = null;
			}
			else {
				sp.asset = asset;
			}
		}
	}

	/**
	 * Connect SpriteRasters to ImgAssets by filename lookup
	 */
	public void bindRasters()
	{
		for (SpriteRaster sr : rasters) {
			sr.bindRasters(imgAssets);
		}
	}

	public void savePalettes()
	{
		int count = 0;
		for (PalAsset asset : palAssets) {
			if (asset.isModified()) {
				try {
					asset.save();
					asset.clearModified();
					count++;
				}
				catch (IOException e) {
					Logger.logError("IOException while saving " + asset.getFilename() + ": " + e.getMessage());
				}
			}
		}

		if (count == 0)
			Logger.log("No modified palettes found");
		else if (count == 1)
			Logger.log("Saved " + count + " modified palette");
		else
			Logger.log("Saved " + count + " modified palettes");
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		reindex();

		XmlTag root = xmw.createTag(TAG_SPRITE, false);
		xmw.addHex(root, ATTR_SPRITE_NUM_COMPONENTS, maxComponents);
		xmw.addHex(root, ATTR_SPRITE_NUM_VARIATIONS, numVariations);
		if (usesKeyframes)
			xmw.addBoolean(root, ATTR_KEYFRAMES, true);
		if (metadata.isPlayer && hasBack)
			xmw.addBoolean(root, ATTR_SPRITE_HAS_BACK, true);
		xmw.openTag(root);

		XmlTag palettesTag = xmw.createTag(TAG_PALETTE_LIST, false);
		xmw.openTag(palettesTag);
		for (int i = 0; i < palettes.size(); i++) {
			SpritePalette sp = palettes.get(i);

			XmlTag paletteTag = xmw.createTag(TAG_PALETTE, true);
			xmw.addHex(paletteTag, ATTR_ID, i);

			xmw.addAttribute(paletteTag, ATTR_NAME, sp.name);
			xmw.addAttribute(paletteTag, ATTR_SOURCE, sp.filename);

			if (sp.frontOnly) {
				xmw.addBoolean(paletteTag, ATTR_FRONT_ONLY, true);
			}

			xmw.printTag(paletteTag);
		}
		xmw.closeTag(palettesTag);

		XmlTag rastersTag = xmw.createTag(TAG_RASTER_LIST, false);
		xmw.openTag(rastersTag);
		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);
			XmlTag rasterTag = xmw.createTag(TAG_RASTER, true);
			xmw.addHex(rasterTag, ATTR_ID, i);

			if (!sr.name.isEmpty())
				xmw.addAttribute(rasterTag, ATTR_NAME, sr.name);

			xmw.addHex(rasterTag, ATTR_PALETTE, sr.front.pal.getIndex());
			xmw.addAttribute(rasterTag, ATTR_SOURCE, sr.front.filename);

			if (hasBack) {
				if (sr.hasIndependentBack) {
					xmw.addAttribute(rasterTag, ATTR_BACK, sr.back.filename);
				}
				else {
					xmw.addHexArray(rasterTag, ATTR_SPECIAL_SIZE, (sr.specialWidth & 0xFF), (sr.specialHeight & 0xFF));
				}

				if (sr.back.pal != sr.front.pal) {
					xmw.addHex(rasterTag, ATTR_BACK_PAL, sr.back.pal.getIndex());
				}
			}

			xmw.printTag(rasterTag);
		}
		xmw.closeTag(rastersTag);

		XmlTag animationsTag = xmw.createTag(TAG_ANIMATION_LIST, false);
		xmw.openTag(animationsTag);
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.elementAt(i);
			XmlTag animationTag = xmw.createTag(TAG_ANIMATION, false);
			if (!anim.name.isEmpty())
				xmw.addAttribute(animationTag, ATTR_NAME, anim.name);
			xmw.openTag(animationTag);

			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent component = anim.components.elementAt(j);
				component.toXML(xmw);
			}

			xmw.closeTag(animationTag);
		}
		xmw.closeTag(animationsTag);

		xmw.closeTag(root);
	}

	public void convertToKeyframes()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);
				comp.convertToKeyframes();
			}
		}
	}

	public void convertToCommands()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);
				comp.convertToCommands();
			}
		}
	}

	public boolean areTexturesLoaded()
	{
		return texturesLoaded;
	}

	public void loadTextures()
	{
		for (ImgAsset ia : imgAssets) {
			ia.glLoad();
		}

		for (PalAsset pa : palAssets) {
			pa.pal.glLoad();
		}

		texturesLoaded = true;
	}

	/**
	 * Generate editor images for all ImgAssets
	 */
	public void loadEditorImages()
	{
		for (ImgAsset asset : imgAssets) {
			asset.loadEditorImages();
		}

		for (SpriteRaster raster : rasters) {
			raster.loadEditorImages();
		}
	}

	/**
	 * Generate editor images for all ImgAssets which use a particular PalAsset
	 * @param filter
	 */
	public void loadEditorImages(PalAsset filter)
	{
		for (SpriteRaster img : rasters) {
			img.loadEditorImages(filter);
		}
	}

	public void unloadTextures()
	{
		for (ImgAsset ia : imgAssets) {
			ia.glDelete();
		}

		for (PalAsset pa : palAssets) {
			pa.pal.glDelete();
		}
	}

	public void glRefreshRasters()
	{
		for (ImgAsset ia : imgAssets) {
			ia.glLoad();
		}
	}

	public void glRefreshPalettes()
	{
		for (PalAsset pa : palAssets) {
			pa.pal.glReload();
		}
	}

	public int getPaletteCount()
	{
		return palettes.size();
	}

	public int lastValidPaletteID()
	{
		return palettes.size() - 1;
	}

	public int lastValidAnimationID()
	{
		return animations.size() - 1;
	}

	// update anim based on ID
	public void resetAnimation(int animationID)
	{
		if (animationID >= animations.size())
			throw new IllegalArgumentException(String.format(
				"Animation ID is out of range: %X of %X", animationID, animations.size()));

		animations.get(animationID).reset();
	}

	// update anim based on ID
	public void updateAnimation(int animationID)
	{
		if (animationID >= animations.size())
			throw new IllegalArgumentException(String.format(
				"Animation ID is out of range: %X of %X", animationID, animations.size()));

		animations.get(animationID).step();
	}

	public static class SpriteRenderingOpts
	{
		boolean useBack;
		boolean enableSelectedHighlight;
		boolean useSelectShading;
		boolean useFiltering;
	}

	// render based on IDs -- these are used by the map editor
	public void render(ShadingProfile spriteShading, int animationID, int paletteOverride, boolean useBack, boolean useSelectShading,
		boolean useFiltering)
	{
		if (animationID >= animations.size())
			throw new IllegalArgumentException(String.format(
				"Animation ID is out of range: %X of %X", animationID, animations.size()));

		if (paletteOverride >= palettes.size())
			throw new IllegalArgumentException(String.format(
				"Palette ID is out of range: %X of %X", paletteOverride, palettes.size()));

		render(spriteShading, animations.get(animationID), palettes.get(paletteOverride), useBack, true, useFiltering, useSelectShading);
	}

	// render based on reference
	public void render(ShadingProfile spriteShading, SpriteAnimation anim, SpritePalette paletteOverride,
		boolean useBack, boolean enableSelectedHighlight, boolean useSelectShading, boolean useFiltering)
	{
		if (!animations.contains(anim)) {
			Logger.logError(anim + " does not belong to " + toString());
			return;
		}

		aabb.clear();

		for (int i = 0; i < anim.components.size(); i++) {
			SpriteComponent comp = anim.components.get(i);
			comp.render(spriteShading, paletteOverride, useBack, enableStencilBuffer, enableSelectedHighlight, useSelectShading, false, useFiltering);
			comp.addCorners(aabb);
		}
	}

	// render single component based on references
	public void render(ShadingProfile spriteShading, SpriteAnimation anim, SpriteComponent comp, SpritePalette paletteOverride,
		boolean useBack, boolean enableSelectedHighlight, boolean useSelectShading, boolean useFiltering)
	{
		if (!animations.contains(anim)) {
			Logger.logError(anim + " does not belong to " + toString());
			return;
		}

		if (!anim.components.contains(comp)) {
			Logger.logError(comp + " does not belong to " + anim);
			return;
		}

		aabb.clear();

		comp.render(spriteShading, paletteOverride, useBack, enableStencilBuffer, enableSelectedHighlight, useSelectShading, false, useFiltering);
		comp.addCorners(aabb);
	}

	public void makeImgAtlas()
	{
		int totalWidth = ATLAS_TILE_PADDING;
		int totalHeight = ATLAS_TILE_PADDING;
		int validRasterCount = 0;

		for (ImgAsset ia : imgAssets) {
			totalWidth += ATLAS_TILE_PADDING + ia.img.width;
			totalHeight += ATLAS_TILE_PADDING + ia.img.height;
			validRasterCount++;
		}

		float aspectRatio = 1.0f; // H/W
		int maxWidth = (int) Math.sqrt(totalWidth * totalHeight / (aspectRatio * validRasterCount));
		maxWidth = (maxWidth + 7) & 0xFFFFFFF8; // pad to multiple of 8

		int currentX = ATLAS_TILE_PADDING;
		int currentY = -ATLAS_TILE_PADDING;

		ArrayList<Integer> rowPosY = new ArrayList<>();
		ArrayList<Integer> rowTallest = new ArrayList<>();
		int currentRow = 0;
		rowTallest.add(0);

		for (ImgAsset ia : imgAssets) {
			if (currentX + ia.img.width + ATLAS_TILE_PADDING > maxWidth) {
				// start new row
				currentY -= rowTallest.get(currentRow);
				rowPosY.add(currentY);
				rowTallest.add(0);

				// next row
				currentX = ATLAS_TILE_PADDING;
				currentY -= ATLAS_TILE_PADDING;
				currentRow++;
			}

			ia.atlasX = currentX;
			ia.atlasRow = currentRow;

			// move forward for next in the row
			currentX += ia.img.width;
			currentX += ATLAS_TILE_PADDING;

			if (ia.img.height > rowTallest.get(currentRow))
				rowTallest.set(currentRow, ia.img.height);
		}

		// finish row
		currentY -= rowTallest.get(currentRow);
		rowPosY.add(currentY);
		currentY -= ATLAS_TILE_PADDING;

		imgAtlasW = maxWidth;
		imgAtlasH = currentY;

		for (ImgAsset ia : imgAssets) {
			ia.atlasY = rowPosY.get(ia.atlasRow) + ia.img.height;
		}

		// center the atlas
		for (ImgAsset ia : imgAssets) {
			ia.atlasX -= imgAtlasW / 2.0f;
			ia.atlasY -= imgAtlasH / 2.0f;
		}

		// negative -> positive
		imgAtlasH = Math.abs(imgAtlasH);
	}

	public void centerImgAtlas(SpriteCamera sheetCamera, int canvasW, int canvasH)
	{
		sheetCamera.centerOn(canvasW, canvasH, 0, 0, 0, imgAtlasW, imgAtlasH, 0);
		sheetCamera.setMaxPos(Math.round(imgAtlasW / 2.0f), Math.round(imgAtlasH / 2.0f));
	}

	public ImgAsset tryImgAtlasPick(BasicTraceRay trace)
	{
		for (ImgAsset ia : imgAssets) {
			Vector3f min = new Vector3f(
				ia.atlasX - ATLAS_SELECT_PADDING,
				ia.atlasY - ia.img.height - ATLAS_SELECT_PADDING,
				0);

			Vector3f max = new Vector3f(
				ia.atlasX + ia.img.width + ATLAS_SELECT_PADDING,
				ia.atlasY + ATLAS_SELECT_PADDING,
				0);

			BasicTraceHit hit = BasicTraceRay.getIntersection(trace, min, max);

			if (!hit.missed())
				return ia;
		}

		return null;
	}

	public void renderImgAtlas(ImgAsset selected, ImgAsset highlighted, boolean useFiltering)
	{
		for (ImgAsset ia : imgAssets) {
			ia.inUse = false;
		}

		for (SpriteRaster sr : rasters) {
			ImgAsset front = sr.front.asset;
			if (front != null) {
				front.inUse = true;
			}

			ImgAsset back = sr.back.asset;
			if (back != null) {
				back.inUse = true;
			}
		}

		SpriteShader shader = ShaderManager.use(SpriteShader.class);
		shader.useFiltering.set(useFiltering);

		for (ImgAsset ia : imgAssets) {
			shader.selected.set(ia == selected);
			shader.highlighted.set(ia == highlighted);

			shader.alpha.set(ia.inUse ? 1.0f : 0.4f);

			ia.img.glBind(shader.texture);
			ia.getPalette().glBind(shader.palette);

			float x1 = ia.atlasX;
			float y1 = ia.atlasY;
			float x2 = ia.atlasX + ia.img.width;
			float y2 = ia.atlasY - ia.img.height;

			shader.setXYQuadCoords(x1, y2, x2, y1, 0); // NOTE: upside down
			shader.renderQuad();
		}
	}

	public void makeRasterAtlas()
	{
		int totalWidth = ATLAS_TILE_PADDING;
		int totalHeight = ATLAS_TILE_PADDING;
		int validRasterCount = 0;

		for (SpriteRaster sr : rasters) {
			if (sr.front.asset == null)
				continue;

			totalWidth += ATLAS_TILE_PADDING + sr.front.asset.img.width;
			totalHeight += ATLAS_TILE_PADDING + sr.front.asset.img.height;
			validRasterCount++;
		}

		float aspectRatio = 1.0f; // H/W
		int maxWidth = (int) Math.sqrt(totalWidth * totalHeight / (aspectRatio * validRasterCount));
		maxWidth = (maxWidth + 7) & 0xFFFFFFF8; // pad to multiple of 8

		int currentX = ATLAS_TILE_PADDING;
		int currentY = -ATLAS_TILE_PADDING;

		ArrayList<Integer> rowPosY = new ArrayList<>();
		ArrayList<Integer> rowTallest = new ArrayList<>();
		int currentRow = 0;
		rowTallest.add(0);

		for (SpriteRaster sr : rasters) {
			if (sr.front.asset == null)
				continue;

			int width = sr.front.asset.img.width;
			int height = sr.front.asset.img.height;

			if (currentX + width + ATLAS_TILE_PADDING > maxWidth) {
				// start new row
				currentY -= rowTallest.get(currentRow);
				rowPosY.add(currentY);
				rowTallest.add(0);

				// next row
				currentX = ATLAS_TILE_PADDING;
				currentY -= ATLAS_TILE_PADDING;
				currentRow++;
			}

			sr.atlasX = currentX;
			sr.atlasRow = currentRow;

			// move forward for next in the row
			currentX += width;
			currentX += ATLAS_TILE_PADDING;

			if (height > rowTallest.get(currentRow))
				rowTallest.set(currentRow, height);
		}

		// finish row
		currentY -= rowTallest.get(currentRow);
		rowPosY.add(currentY);
		currentY -= ATLAS_TILE_PADDING;

		rasterAtlasW = maxWidth;
		rasterAtlasH = currentY;

		for (SpriteRaster sr : rasters) {
			if (sr.front.asset == null)
				continue;

			sr.atlasY = rowPosY.get(sr.atlasRow) + sr.front.asset.img.height;
		}

		// center the atlas
		for (SpriteRaster sr : rasters) {
			sr.atlasX -= rasterAtlasW / 2.0f;
			sr.atlasY -= rasterAtlasH / 2.0f;
		}

		// negative -> positive
		rasterAtlasH = Math.abs(rasterAtlasH);
	}

	public void centerRasterAtlas(SpriteCamera sheetCamera, int canvasW, int canvasH)
	{
		sheetCamera.centerOn(canvasW, canvasH, 0, 0, 0, rasterAtlasW, rasterAtlasH, 0);
		sheetCamera.setMaxPos(Math.round(rasterAtlasW / 2.0f), Math.round(rasterAtlasH / 2.0f));
	}

	public void renderRasterAtlas(Palette overridePalette, boolean useFiltering)
	{
		SpriteShader shader = ShaderManager.use(SpriteShader.class);
		shader.useFiltering.set(useFiltering);

		for (SpriteRaster sr : rasters) {
			if (sr.front.asset == null)
				continue;

			ImgAsset ia = sr.front.asset;
			Palette renderPalette;
			if (overridePalette != null) {
				// use fixed palette if provided
				renderPalette = overridePalette;
			}
			else if (sr.front.pal != null) {
				// use palette asssigned to this face
				renderPalette = sr.front.pal.getPal();
			}
			else {
				// fallback to default palette of asset
				renderPalette = ia.getPalette();
			}

			ia.img.glBind(shader.texture);
			renderPalette.glBind(shader.palette);

			float x1 = sr.atlasX;
			float y1 = sr.atlasY;
			float x2 = sr.atlasX + ia.img.width;
			float y2 = sr.atlasY - ia.img.height;

			shader.setXYQuadCoords(x1, y2, x2, y1, 0); // NOTE: upside down
			shader.renderQuad();
		}
	}

	private final EditableData editableData = new EditableData(this);

	@Override
	public EditableData getEditableData()
	{
		return editableData;
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{
		for (PalAsset asset : palAssets)
			downstream.add(asset);

		for (SpritePalette pal : palettes)
			downstream.add(pal);

		for (SpriteRaster img : rasters)
			downstream.add(img);

		for (SpriteAnimation anim : animations)
			downstream.add(anim);
	}

	@Override
	public String checkErrorMsg()
	{
		// no errors for Sprite objects
		return null;
	}
}
