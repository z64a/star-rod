package game.sprite.editor;

import javax.swing.DefaultListModel;

import assets.AssetManager;
import game.sprite.ImgAsset;
import game.sprite.PalAsset;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.animators.command.AnimCommand;
import game.sprite.editor.animators.command.Label;
import util.IterableListModel;
import util.Logger;

public class SpriteCleanup
{
	private final Sprite sprite;

	public boolean removeUnusedLabels;
	public IterableListModel<UnusedLabel> unusedLabels = new IterableListModel<>();

	public boolean removeUnusedRasters;
	public IterableListModel<Unused<SpriteRaster>> unusedRasters = new IterableListModel<>();

	public boolean removeUnusedPalettes;
	public IterableListModel<Unused<SpritePalette>> unusedPalettes = new IterableListModel<>();

	public boolean removeUnusedImgAssets;
	public IterableListModel<UnusedAsset<ImgAsset>> unusedImgAssets = new IterableListModel<>();

	public boolean removeUnusedPalAssets;
	public IterableListModel<UnusedAsset<PalAsset>> unusedPalAssets = new IterableListModel<>();

	public static class UnusedLabel
	{
		public final DefaultListModel<AnimCommand> model;
		public final Label label;

		public UnusedLabel(Label label, DefaultListModel<AnimCommand> model)
		{
			this.label = label;
			this.model = model;
		}
	}

	public static class Unused<T>
	{
		public final DefaultListModel<T> model;
		public final T item;

		public Unused(T item, DefaultListModel<T> model)
		{
			this.item = item;
			this.model = model;
		}
	}

	public static class UnusedAsset<T>
	{
		public final SpriteAssetCollection<T> model;
		public final T item;

		public UnusedAsset(T item, SpriteAssetCollection<T> model)
		{
			this.item = item;
			this.model = model;
		}
	}

	public SpriteCleanup(Sprite sprite)
	{
		this.sprite = sprite;

		for (SpriteRaster raster : sprite.rasters)
			raster.inUse = false;

		for (SpritePalette palette : sprite.palettes)
			palette.inUse = false;

		for (ImgAsset asset : sprite.imgAssets)
			asset.inUse = false;

		for (PalAsset asset : sprite.palAssets)
			asset.inUse = false;

		for (SpriteAnimation anim : sprite.animations) {
			for (SpriteComponent comp : anim.components) {
				comp.addUnused(this);
			}
		}

		for (SpriteRaster raster : sprite.rasters) {
			if (raster.front.asset != null)
				raster.front.asset.inUse = true;

			if (raster.front.pal != null)
				raster.front.pal.inUse = true;

			if (sprite.hasBack && raster.hasIndependentBack) {
				if (raster.back.asset != null)
					raster.back.asset.inUse = true;

				if (raster.back.pal != null)
					raster.back.pal.inUse = true;
			}

			if (!raster.inUse)
				unusedRasters.addElement(new Unused<SpriteRaster>(raster, sprite.rasters));
		}

		for (SpritePalette palette : sprite.palettes) {
			if (palette.asset != null)
				palette.asset.inUse = true;

			if (!palette.inUse)
				unusedPalettes.addElement(new Unused<SpritePalette>(palette, sprite.palettes));
		}

		if (!sprite.metadata.isPlayer) {
			for (ImgAsset asset : sprite.imgAssets) {
				if (!asset.inUse) {
					unusedImgAssets.addElement(new UnusedAsset<ImgAsset>(asset, sprite.imgAssets));
				}
			}

			for (PalAsset asset : sprite.palAssets) {
				if (!asset.inUse) {
					unusedPalAssets.addElement(new UnusedAsset<PalAsset>(asset, sprite.palAssets));
				}
			}
		}
	}

	public int getActionsCount()
	{
		int actions = 0;

		if (removeUnusedLabels)
			actions++;

		if (removeUnusedRasters)
			actions++;

		if (removeUnusedPalettes)
			actions++;

		if (!sprite.metadata.isPlayer) {
			if (removeUnusedImgAssets)
				actions++;

			if (removeUnusedPalAssets)
				actions++;
		}

		return actions;
	}

	public void execute()
	{
		if (removeUnusedLabels) {
			for (UnusedLabel unused : unusedLabels) {
				unused.model.removeElement(unused.label);
			}
			Logger.log("Removed " + unusedLabels.size() + " unused labels");
		}

		if (removeUnusedRasters) {
			for (Unused<SpriteRaster> unused : unusedRasters) {
				unused.model.removeElement(unused.item);
			}
			Logger.log("Removed " + unusedRasters.size() + " unused rasters");
		}

		if (removeUnusedPalettes) {
			for (Unused<SpritePalette> unused : unusedPalettes) {
				unused.model.removeElement(unused.item);
			}
			Logger.log("Removed " + unusedPalettes.size() + " unused palettes");
		}

		if (!sprite.metadata.isPlayer) {
			if (removeUnusedPalAssets) {
				for (UnusedAsset<PalAsset> asset : unusedPalAssets) {
					AssetManager.deleteAll(asset.item.getSource());
				}
				Logger.log("Removed " + unusedPalAssets.size() + " unused palette assets");

				sprite.reloadPaletteAssets();
			}

			if (removeUnusedImgAssets) {
				for (UnusedAsset<ImgAsset> asset : unusedImgAssets) {
					AssetManager.deleteAll(asset.item.getSource());
				}
				Logger.log("Removed " + unusedImgAssets.size() + " unused image assets");

				sprite.reloadRasterAssets();

				// recreate the atlas
				sprite.makeImgAtlas();
			}
		}

		SpriteEditor.instance().flushUndoRedo();
	}
}
