package game.sprite.editor;

import java.awt.Toolkit;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import common.commands.CommandBatch;
import game.sprite.ImgAsset;
import game.sprite.Sprite;
import game.sprite.SpriteRaster;
import game.sprite.SpriteRasterFace;
import game.sprite.editor.commands.BindImgAsset;
import game.sprite.editor.commands.CreateRaster;
import game.sprite.editor.commands.SelectImgAsset;
import game.sprite.editor.commands.SelectRaster;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.ThemedIcon;

public class RastersTab extends JPanel
{
	private Sprite sprite = null;

	private AssetList<ImgAsset> imgAssetList;
	private RastersList rasterList;

	private RasterFacePanel frontImgPanel;
	private RasterFacePanel backImgPanel;

	public RastersTab(SpriteEditor editor)
	{
		imgAssetList = new AssetList<>();
		imgAssetList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		imgAssetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		imgAssetList.setCellRenderer(new ImgAssetCellRenderer());

		imgAssetList.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			if (imgAssetList.ignoreChanges.disabled())
				SpriteEditor.execute(new SelectImgAsset(imgAssetList, editor.getSprite(),
					imgAssetList.getSelectedValue(), this::setImgAsset));
		});

		rasterList = new RastersList(editor, this);

		JButton btnRefreshAssets = new JButton(ThemedIcon.REFRESH_16);
		btnRefreshAssets.setToolTipText("Reload assets");
		btnRefreshAssets.addActionListener((e) -> {
			if (sprite == null) {
				return;
			}

			imgAssetList.ignoreChanges.increment();

			// remember name of currently selected, so we can try reselecting after refreshing
			ImgAsset selected = imgAssetList.getSelectedValue();
			String selectedName = (selected == null) ? null : selected.getFilename();

			// clear current selection
			imgAssetList.setSelectedValue(null, true);
			sprite.lastSelectedImgAsset = -1;
			setImgAsset(null);

			// reload the assets
			sprite.reloadRasterAssets();

			// recreate the atlas
			sprite.makeImgAtlas();
			editor.resetAtlasCamera();

			// prepare new selection for setSprite
			if (sprite.imgAssets.size() > 0)
				sprite.lastSelectedImgAsset = 0;

			// refresh this tab
			setSprite(sprite);
			RastersTab.this.repaint();

			// try reselecting asset with name matching previous selection
			for (ImgAsset newAsset : sprite.imgAssets) {
				if (newAsset.getFilename().equals(selectedName)) {
					imgAssetList.setSelectedValue(newAsset, true);
				}
			}

			editor.flushUndoRedo();

			imgAssetList.ignoreChanges.decrement();
		});

		JButton btnAddRaster = new JButton(ThemedIcon.ADD_16);
		btnAddRaster.setToolTipText("Add new raster");
		btnAddRaster.addActionListener((e) -> {
			if (sprite == null) {
				return;
			}

			SpriteRaster img = new SpriteRaster(sprite);
			String newName = img.createUniqueName(String.format("Img_%X", sprite.palettes.size()));

			if (newName == null) {
				Logger.logError("Could not generate valid name for new raster!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			ImgAsset asset = imgAssetList.getSelectedValue();
			if (asset != null) {
				img.front.assignAsset(asset);
				// assign a palette for the new raster
				if (sprite.selectedPalette != null)
					img.front.assignPal(sprite.selectedPalette);
				else if (sprite.palettes.size() > 0)
					img.front.assignPal(sprite.palettes.get(0));
			}

			img.loadEditorImages();

			img.name = newName;
			CommandBatch batch = new CommandBatch("Add Raster");
			batch.addCommand(new CreateRaster("Add Raster", sprite, img));
			batch.addCommand(new SelectRaster(rasterList, sprite, img, this::setRaster));
			SpriteEditor.execute(batch);
		});

		frontImgPanel = new RasterFacePanel(this, false);
		backImgPanel = new RasterFacePanel(this, true);

		JScrollPane assetScrollPane = new JScrollPane(imgAssetList);
		assetScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		JScrollPane rasterScrollPane = new JScrollPane(rasterList);
		rasterScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		JPanel listsPanel = new JPanel(new MigLayout("fill, ins 0, wrap 2", "[grow, sg col][grow, sg col]"));

		listsPanel.add(btnRefreshAssets, "split 2");
		listsPanel.add(new JLabel("Assets"), "growx");

		listsPanel.add(btnAddRaster, "split 2");
		listsPanel.add(new JLabel("Rasters"), "growx");

		listsPanel.add(assetScrollPane, "grow, push, sg list");
		listsPanel.add(rasterScrollPane, "grow, push, sg list");

		listsPanel.add(frontImgPanel, "grow");
		listsPanel.add(backImgPanel, "grow");

		setLayout(new MigLayout("fill, ins 16 16 32 16, wrap, hidemode 0"));
		add(listsPanel, "grow, push");
	}

	public void tryBindAsset(SpriteRasterFace face, Consumer<SpriteRasterFace> callback)
	{
		ImgAsset asset = imgAssetList.getSelectedValue();

		// null asset is allowed here
		SpriteEditor.execute(new BindImgAsset(this, face, asset, callback));
	}

	public void trySelectAsset(SpriteRasterFace face)
	{
		if (face.asset == null) {
			Logger.logError("No asset is bound for " + face.parentRaster.name + "!");
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		if (!sprite.imgAssets.contains(face.asset)) {
			Logger.logError("Asset for " + face.parentRaster.name + " is missing!");
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		SpriteEditor.execute(new SelectImgAsset(imgAssetList, SpriteEditor.instance().getSprite(),
			face.asset, this::setImgAsset));
	}

	public void setSprite(Sprite sprite)
	{
		assert (SwingUtilities.isEventDispatchThread());
		assert (sprite != null);

		this.sprite = sprite;

		frontImgPanel.setSprite(sprite);
		backImgPanel.setSprite(sprite);

		imgAssetList.ignoreChanges.increment();
		imgAssetList.setModel(sprite.imgAssets.getListModel());
		imgAssetList.setSelectedValue(sprite.selectedImgAsset, true);
		imgAssetList.ignoreChanges.decrement();

		setImgAsset(sprite.selectedImgAsset);

		rasterList.ignoreChanges.increment();
		rasterList.setModel(sprite.rasters);
		rasterList.setSelectedValue(sprite.selectedRaster, true);
		rasterList.ignoreChanges.decrement();

		setRaster(sprite.selectedRaster);
	}

	public void setRaster(SpriteRaster sr)
	{
		assert (SwingUtilities.isEventDispatchThread());

		if (sr != null) {
			frontImgPanel.setRaster(sr);
			backImgPanel.setRaster(sr);

			frontImgPanel.setVisible(true);
			backImgPanel.setVisible(sprite.hasBack);
		}
		else {
			frontImgPanel.setRaster(null);
			backImgPanel.setRaster(null);

			frontImgPanel.setVisible(false);
			backImgPanel.setVisible(false);
		}
	}

	public void setImgAsset(ImgAsset asset)
	{
		assert (SwingUtilities.isEventDispatchThread());
	}

	public void selectAsset(ImgAsset asset)
	{
		assert (SwingUtilities.isEventDispatchThread());

		imgAssetList.setSelectedValue(asset, true);
	}
}
