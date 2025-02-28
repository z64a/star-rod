package game.sprite.editor;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EtchedBorder;

import app.SwingUtils;
import common.commands.AbstractCommand;
import common.commands.CommandBatch;
import game.map.editor.ui.SwatchPanel;
import game.sprite.PalAsset;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.sprite.editor.commands.BindPalAsset;
import game.sprite.editor.commands.CreatePalette;
import game.sprite.editor.commands.SelectPalAsset;
import game.sprite.editor.commands.SelectPalette;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.ColorSlider;
import util.ui.ColorSlider.SliderListener;
import util.ui.HexTextField;
import util.ui.ThemedIcon;

public class PalettesTab extends JPanel
{
	private JCheckBox cbPreviewPalette;
	private JSpinner variationsSpinner;
	private JLabel paletteGroupsLabel;

	private JPanel paletteInfoPanel;
	private SwatchPanel colorPreview;
	private HexTextField colorHexValue;
	private JRadioButton rgbButton;
	private JRadioButton hslButton;
	private JPanel rgbaPanel;
	private ColorSlider channelR, channelG, channelB;
	private ColorSlider channelH, channelS, channelV;
	private ColorSlider channelA;

	private static enum ColorModel
	{
		RGB, HSL
	}

	private ColorModel selectedColorModel = ColorModel.RGB;

	private AssetList<PalAsset> palAssetList;
	private PalettesList paletteList;
	private PaletteSwatchPanel swatchesPanel;
	private JLabel lblCurrentBoundAsset;

	private int paletteColorIndex = 1;
	private Sprite sprite = null;

	private boolean suppressCommands = false;
	private boolean ignoreSliderUpdates = false;
	private boolean ignoreTextfieldUpdates = false;

	public PalettesTab(SpriteEditor editor)
	{
		paletteGroupsLabel = new JLabel();

		cbPreviewPalette = new JCheckBox(" Preview");
		cbPreviewPalette.setSelected(true);
		cbPreviewPalette.addActionListener((e) -> {
			if (!suppressCommands)
				SpriteEditor.execute(new TogglePreviewPalette(cbPreviewPalette));
		});
		cbPreviewPalette.setHorizontalAlignment(SwingConstants.CENTER);

		variationsSpinner = new JSpinner();
		variationsSpinner.setModel(new SpinnerNumberModel(0, 0, 99, 1));
		variationsSpinner.addChangeListener((e) -> {
			if (sprite != null && !suppressCommands)
				SpriteEditor.execute(new SetVariationCount(sprite, (int) variationsSpinner.getValue()));
		});

		SwingUtils.setFontSize(variationsSpinner, 12);
		SwingUtils.centerSpinnerText(variationsSpinner);

		palAssetList = new AssetList<PalAsset>();
		palAssetList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		palAssetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		palAssetList.setCellRenderer(new PalAssetSlicesRenderer());

		palAssetList.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			if (!palAssetList.ignoreSelectionChange)
				SpriteEditor.execute(new SelectPalAsset(palAssetList, editor.getSprite(),
					palAssetList.getSelectedValue(), this::setPalAsset));
		});

		paletteList = new PalettesList(editor, this);

		JButton btnOpen = new JButton("Open Folder");
		btnOpen.addActionListener((evt) -> {
			if (sprite == null)
				return;
			try {
				Desktop.getDesktop().open(new File(sprite.getDirectoryName() + "/palettes/"));
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		});
		SwingUtils.addBorderPadding(btnOpen);

		JButton btnSave = new JButton("Save Changes");
		btnSave.addActionListener((e) -> {
			if (sprite != null) {
				sprite.savePalettes();
				palAssetList.repaint();
			}
		});
		SwingUtils.addBorderPadding(btnSave);

		JButton btnBind = new JButton("Bind Asset");
		btnBind.addActionListener((e) -> {
			SpritePalette pal = paletteList.getSelectedValue();

			if (pal == null) {
				Logger.logError("No palette is selected!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			PalAsset asset = palAssetList.getSelectedValue();

			// null asset is allowed here
			SpriteEditor.execute(new BindPalAsset(paletteList, pal, asset));
		});
		SwingUtils.addBorderPadding(btnBind);

		JButton btnClear = new JButton("Select Asset");
		btnClear.addActionListener((e) -> {
			SpritePalette pal = paletteList.getSelectedValue();

			if (pal == null) {
				Logger.logError("No palette is selected!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			if (pal.asset == null) {
				Logger.logError("No asset is bound for " + pal.name + "!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			if (!sprite.palAssets.contains(pal.asset)) {
				Logger.logError("Asset for " + pal.name + " is missing!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			SpriteEditor.execute(new SelectPalAsset(palAssetList, sprite, pal.asset, this::setPalAsset));
		});
		SwingUtils.addBorderPadding(btnClear);

		JButton btnRefreshAssets = new JButton(ThemedIcon.REFRESH_16);
		btnRefreshAssets.setToolTipText("Reload assets");
		btnRefreshAssets.addActionListener((e) -> {
			if (sprite == null) {
				return;
			}

			editor.invokeLater(() -> {
				sprite.reloadPaletteAssets();
				SpriteEditor.instance().flushUndoRedo();

				SwingUtilities.invokeLater(() -> {
					setSpriteEDT(sprite);
				});
			});
		});

		JButton btnAddPalette = new JButton(ThemedIcon.ADD_16);
		btnAddPalette.setToolTipText("Add new palette");
		btnAddPalette.addActionListener((e) -> {
			if (sprite == null) {
				return;
			}

			SpritePalette pal = new SpritePalette(sprite);
			String newName = pal.createUniqueName(String.format("Pal_%X", sprite.palettes.size()));

			if (newName == null) {
				Logger.logError("Could not generate valid name for new palette!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			PalAsset asset = palAssetList.getSelectedValue();
			if (asset != null)
				pal.assignAsset(asset);

			pal.name = newName;
			CommandBatch batch = new CommandBatch("Add Palette");
			batch.addCommand(new CreatePalette("Add Palette", sprite, pal));
			batch.addCommand(new SelectPalette(paletteList, sprite, pal, this::setPalette));
			SpriteEditor.execute(batch);
		});

		createColorPanel();

		JScrollPane animScrollPane = new JScrollPane(palAssetList);
		animScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		JScrollPane compScrollPane = new JScrollPane(paletteList);
		compScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		JPanel listsPanel = new JPanel(new MigLayout("fill, ins 0, wrap 2", "[grow, sg col][grow, sg col]"));

		listsPanel.add(btnRefreshAssets, "split 2");
		listsPanel.add(new JLabel("Assets"), "growx");

		listsPanel.add(btnAddPalette, "split 5");
		listsPanel.add(new JLabel("Palettes"), "growx");
		listsPanel.add(new JPanel(), "growx, pushx"); // pushing dummy
		listsPanel.add(new JLabel("Variations: "));
		listsPanel.add(variationsSpinner, "w 80!");

		listsPanel.add(animScrollPane, "grow, push, sg list");
		listsPanel.add(compScrollPane, "grow, push, sg list");

		listsPanel.add(btnSave, "split 2, growx, sg btn");
		// listsPanel.add(cbPreviewPalette, "growx, sg btn, align center");
		listsPanel.add(new JPanel(), "growx, sg btn"); // cell dummy

		listsPanel.add(btnBind, "split 2, growx, sg btn");
		listsPanel.add(btnClear, "growx, sg btn");

		setLayout(new MigLayout("fill, ins 16, wrap, hidemode 0"));
		add(listsPanel, "grow, push");
		add(paletteInfoPanel, "growx, span, wrap, gaptop 16");
	}

	private void createColorPanel()
	{
		rgbButton = new JRadioButton(ColorModel.RGB.toString());
		rgbButton.setSelected(true);
		rgbButton.addActionListener((e) -> {
			if (rgbButton.isSelected())
				SpriteEditor.execute(new SetColorModel(ColorModel.RGB));
		});

		hslButton = new JRadioButton(ColorModel.HSL.toString());
		hslButton.setSelected(false);
		hslButton.addActionListener((e) -> {
			if (hslButton.isSelected())
				SpriteEditor.execute(new SetColorModel(ColorModel.HSL));
		});

		ButtonGroup group = new ButtonGroup();
		group.add(rgbButton);
		group.add(hslButton);

		// update the color preview when the sliders are adjusted
		SliderListener colorPreviewListener = (preview, value) -> {
			if (ignoreSliderUpdates)
				return;

			int[] rgb = new int[3];

			switch (selectedColorModel) {
				case RGB:
					rgb[0] = channelR.getValue();
					rgb[1] = channelG.getValue();
					rgb[2] = channelB.getValue();
					break;
				case HSL:
					int[] hsl = new int[3];
					hsl[0] = channelH.getValue();
					hsl[1] = channelS.getValue();
					hsl[2] = channelV.getValue();
					rgb = HSLtoRGB(hsl);
					break;
				default:
					throw new RuntimeException("Unknown color model.");
			}

			int a = channelA.getValue();
			Color c = new Color(rgb[0], rgb[1], rgb[2], a);

			int packedRGB = (c.getRGB() << 8) & 0xFFFFFF00;
			colorHexValue.setValue(packedRGB | a);

			swatchesPanel.swatches[paletteColorIndex].setForeground(c);
			colorPreview.setForeground(c);

			PalAsset asset = palAssetList.getSelectedValue();
			if (asset != null) {
				asset.pal.setColor(paletteColorIndex, rgb[0], rgb[1], rgb[2], a);

				paletteList.repaint();
				palAssetList.repaint();

				asset.dirty = true;
				asset.modified = true;
			}

			if (!preview)
				SpriteEditor.execute(new SetColor(asset, paletteColorIndex, c, false));
		};

		colorHexValue = new HexTextField(8, true, (e) -> {
			if (ignoreTextfieldUpdates)
				return;

			int rgba = colorHexValue.getValue();
			int r = (rgba >>> 24) & 0xFF;
			int g = (rgba >>> 16) & 0xFF;
			int b = (rgba >>> 8) & 0xFF;
			int a = (rgba >>> 0) & 0xFF;

			Color c = new Color(r, g, b, a);

			swatchesPanel.swatches[paletteColorIndex].setForeground(c);
			colorPreview.setForeground(c);

			PalAsset asset = palAssetList.getSelectedValue();
			if (asset != null) {
				asset.pal.setColor(paletteColorIndex, r, g, b, a);

				paletteList.repaint();
				palAssetList.repaint();

				asset.dirty = true;
				asset.modified = true;
			}

			ignoreTextfieldUpdates = true;
			setSelectedColor(c);
			ignoreTextfieldUpdates = false;

			SpriteEditor.execute(new SetColor(asset, paletteColorIndex, c, true));
		});

		colorHexValue.setBorder(BorderFactory.createCompoundBorder(
			colorHexValue.getBorder(),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		colorHexValue.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.setFontSize(colorHexValue, 12.0f);

		channelR = new ColorSlider("R", "w 30!", 0, 255, 255, 32, colorPreviewListener);
		channelG = new ColorSlider("G", "w 30!", 0, 255, 255, 32, colorPreviewListener);
		channelB = new ColorSlider("B", "w 30!", 0, 255, 255, 32, colorPreviewListener);

		channelH = new ColorSlider("H", "w 30!", 0, hmax, hmax, 600, colorPreviewListener);
		channelS = new ColorSlider("S", "w 30!", 0, smax, smax, 100, colorPreviewListener);
		channelV = new ColorSlider("L", "w 30!", 0, lmax, lmax, 100, colorPreviewListener);

		channelH.setVisible(false);
		channelS.setVisible(false);
		channelV.setVisible(false);

		channelA = new ColorSlider("A", "w 30!", 0, 255, 255, 32, colorPreviewListener);

		rgbaPanel = new JPanel(new MigLayout("fill, wrap 3, hidemode 3"));
		rgbaPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		rgbaPanel.add(SwingUtils.getLabel("Color Model:", 12), "span, split 3, gapright 10");
		rgbaPanel.add(rgbButton, "w 50!, sg radio");
		rgbaPanel.add(hslButton, "w 50!, sg radio");

		rgbaPanel.add(channelR, "wrap");
		rgbaPanel.add(channelG, "wrap");
		rgbaPanel.add(channelB, "wrap");

		rgbaPanel.add(channelH, "wrap");
		rgbaPanel.add(channelS, "wrap");
		rgbaPanel.add(channelV, "wrap");

		rgbaPanel.add(channelA, "wrap");

		colorPreview = new SwatchPanel(1.32f, 1.33f);

		JPanel rightPanel = new JPanel(new MigLayout("ins 0, fill, wrap"));
		rightPanel.add(colorPreview, "h 32!, w 66%, split 2");
		rightPanel.add(colorHexValue, "h 32!, growx, pushx");
		rightPanel.add(rgbaPanel, "gaptop 8");

		swatchesPanel = new PaletteSwatchPanel(this);
		SwatchPanel swatch = swatchesPanel.swatches[paletteColorIndex];
		swatch.setBorder(BorderFactory.createLineBorder(getBackground(), 3));

		paletteInfoPanel = new JPanel(new MigLayout("ins 0, fill, wrap"));
		paletteInfoPanel.add(swatchesPanel, "split 3, gapleft 16, gapbottom push");
		paletteInfoPanel.add(rightPanel, "gapleft 24, gaptop 8");
	}

	public void setSpriteEDT(Sprite sprite)
	{
		assert (SwingUtilities.isEventDispatchThread());
		assert (sprite != null);

		this.sprite = sprite;

		palAssetList.ignoreSelectionChange = true;
		palAssetList.setModel(sprite.palAssets.getListModel());
		palAssetList.setSelectedValue(sprite.selectedPalAsset, true);
		palAssetList.ignoreSelectionChange = false;

		setPalAsset(sprite.selectedPalAsset);

		paletteList.ignoreSelectionChange = true;
		paletteList.setModel(sprite.palettes);
		paletteList.setSelectedValue(sprite.selectedPalette, true);
		paletteList.ignoreSelectionChange = false;

		setPalette(sprite.selectedPalette);

		suppressCommands = true;
		variationsSpinner.setValue(sprite.numVariations);
		suppressCommands = false;
	}

	public void setPaletteIndex(int index)
	{
		paletteColorIndex = index;

		SwatchPanel swatch = swatchesPanel.getSwatch(index);
		setSelectedColor(swatch.getForeground());
		swatch.setBorder(BorderFactory.createLineBorder(getBackground(), 3));

		// clear borders from other swatches
		for (int i = 0; i < 16; i++) {
			if (i == index)
				continue;
			swatchesPanel.getSwatch(i).setBorder(null);
		}
	}

	public void setPalette(SpritePalette pal)
	{
		// nothing
	}

	public void setPalAsset(PalAsset asset)
	{
		swatchesPanel.setPalette(asset);
		paletteInfoPanel.setVisible(asset != null);

		if (asset != null)
			asset.stashColors();
	}

	public void variationCountChanged()
	{
		if (sprite == null)
			return;

		int numPalettes = sprite.getPaletteCount();
		if (numPalettes == sprite.numVariations) {
			paletteGroupsLabel.setForeground(null);
			paletteGroupsLabel.setText("OK palette count for an overworld sprite with "
				+ sprite.numVariations + ((sprite.numVariations == 1) ? " variation." : " variations."));
		}
		else if (numPalettes == 4 * sprite.numVariations + 1) {
			paletteGroupsLabel.setForeground(null);
			paletteGroupsLabel.setText("OK palette count for a battle sprite with "
				+ sprite.numVariations + ((sprite.numVariations == 1) ? " variation." : " variations."));
		}
		else {
			paletteGroupsLabel.setForeground(SwingUtils.getRedTextColor());
			paletteGroupsLabel.setText("Unusual palette count for "
				+ sprite.numVariations + ((sprite.numVariations == 1) ? " variation " : " variations ")
				+ ((sprite.numVariations < 1) ? "." : "(expected " + (4 * sprite.numVariations + 1) + " or " + sprite.numVariations + ")."));
		}
	}

	private void setSelectedColor(Color c)
	{
		ignoreSliderUpdates = true;

		switch (selectedColorModel) {
			case RGB:
				channelR.setValue(c.getRed());
				channelG.setValue(c.getGreen());
				channelB.setValue(c.getBlue());
				break;
			case HSL:
				int[] hsl = RGBtoHSL(new int[] { c.getRed(), c.getGreen(), c.getBlue() });
				channelH.setValue(hsl[0]);
				channelS.setValue(hsl[1]);
				channelV.setValue(hsl[2]);
				break;
			default:
				throw new RuntimeException("Unknown color model.");
		}

		channelA.setValue(c.getAlpha());
		ignoreSliderUpdates = false;

		if (!ignoreTextfieldUpdates) {
			int argb = c.getRGB();
			int rgb = (argb << 8) & 0xFFFFFF00;
			int a = (argb >>> 24) & 0xFF;
			colorHexValue.setValue(rgb | a);
		}

		colorPreview.setForeground(c);
	}

	private void setColorModel(ColorModel mdl)
	{
		if (mdl == selectedColorModel)
			return;

		switch (mdl) {
			case RGB:
				channelR.setVisible(true);
				channelG.setVisible(true);
				channelB.setVisible(true);
				channelH.setVisible(false);
				channelS.setVisible(false);
				channelV.setVisible(false);
				int[] rgb = HSLtoRGB(new int[] { channelH.getValue(), channelS.getValue(), channelV.getValue() });
				channelR.setValue(rgb[0]);
				channelG.setValue(rgb[1]);
				channelB.setValue(rgb[2]);
				rgbButton.setSelected(true);
				break;
			case HSL:
				channelR.setVisible(false);
				channelG.setVisible(false);
				channelB.setVisible(false);
				channelH.setVisible(true);
				channelS.setVisible(true);
				channelV.setVisible(true);
				int[] hsl = RGBtoHSL(new int[] { channelR.getValue(), channelG.getValue(), channelB.getValue() });
				channelH.setValue(hsl[0]);
				channelS.setValue(hsl[1]);
				channelV.setValue(hsl[2]);
				hslButton.setSelected(true);
				break;
			default:
				throw new RuntimeException("Unknown color model.");
		}

		selectedColorModel = mdl;
		rgbaPanel.revalidate();
	}

	// sliders are integer-valued, so HSL colors are represented with the following
	// integer ranges, which have been chosen to make RGB -> HSL -> RGB invariant.
	// H: 0 - 3600 (0.0 - 360.0)
	// S: 0 - 1000 (0.000 - 1.000)
	// L: 0 - 255
	private static final int hmax = 3600;
	private static final int smax = 1000;
	private static final int lmax = 1000;

	// RGB <-> HSL conversion adapted from Mohsen on stackoverflow
	// https://stackoverflow.com/questions/2353211/hsl-to-rgb-color-conversion

	private static int[] RGBtoHSL(int[] rgb)
	{
		double r = rgb[0] / 255.0;
		double g = rgb[1] / 255.0;
		double b = rgb[2] / 255.0;
		double h, s, l;

		double max = Math.max(Math.max(r, g), b);
		double min = Math.min(Math.min(r, g), b);
		double diff = max - min;

		l = (max + min) / 2;

		if (Math.abs(diff) < 1e-4)
			return new int[] { 0, 0, (int) Math.round(l * lmax) };

		if (l > 0.5)
			s = diff / (2 - max - min);
		else
			s = diff / (max + min);

		if (max == r)
			h = (g - b) / diff + (g < b ? 6.0 : 0.0);
		else if (max == g)
			h = (b - r) / diff + 2.0;
		else
			h = (r - g) / diff + 4.0;
		h = h / 6.0;

		return new int[] {
				(int) Math.round(h * hmax),
				(int) Math.round(s * smax),
				(int) Math.round(l * lmax)
		};
	}

	private static int[] HSLtoRGB(int[] hsl)
	{
		double h = (double) hsl[0] / hmax;
		double s = (double) hsl[1] / smax;
		double l = (double) hsl[2] / lmax;

		if (Math.abs(s) < 1e-4) {
			int v = (int) Math.round(l * 255);
			return new int[] { v, v, v };
		}

		double q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
		double p = 2.0 * l - q;

		double r = hue2rgb(p, q, h + 1.0 / 3.0);
		double g = hue2rgb(p, q, h);
		double b = hue2rgb(p, q, h - 1.0 / 3.0);

		return new int[] {
				(int) Math.round(r * 255),
				(int) Math.round(g * 255),
				(int) Math.round(b * 255)
		};
	}

	private static double hue2rgb(double p, double q, double t)
	{
		if (t < 0.0)
			t += 1.0;
		if (t > 1.0)
			t -= 1.0;
		if (t < 1.0 / 6.0)
			return p + (q - p) * 6.0 * t;
		if (t < 1.0 / 2.0)
			return q;
		if (t < 2.0 / 3.0)
			return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
		return p;
	}

	private static class PaletteSwatchPanel extends JPanel
	{
		private final PalettesTab tab;
		private SwatchPanel[] swatches;

		public PaletteSwatchPanel(PalettesTab tab)
		{
			this.tab = tab;
			setLayout(new MigLayout("fill, ins 0"));

			swatches = new SwatchPanel[16];
			for (int i = 0; i < swatches.length; i++) {
				swatches[i] = new SwatchPanel(1.25f, 1.25f);
				add(swatches[i], "h 40!, w 40!, gapbottom 4, gapright 4" + (((i + 1) % 4 == 0) ? ", wrap" : ""));

				final int index = i;
				swatches[i].addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e)
					{
						SpriteEditor.execute(tab.new SetPaletteIndex(index));
					}
				});

			}
		}

		public void setPalette(PalAsset asset)
		{
			if (asset == null) {
				setVisible(false);
				for (SwatchPanel panel : swatches)
					panel.setForeground(Color.gray);
			}
			else {
				setVisible(true);
				Color[] colors = asset.pal.getColors();
				for (int i = 0; i < swatches.length; i++)
					swatches[i].setForeground(colors[i]);

				tab.setSelectedColor(colors[tab.paletteColorIndex]);
			}
		}

		public SwatchPanel getSwatch(int index)
		{
			return swatches[index];
		}
	}

	private class SetVariationCount extends AbstractCommand
	{
		private final Sprite sprite;
		private final int next;
		private final int prev;

		public SetVariationCount(Sprite sprite, int next)
		{
			super("Set Variation Count");

			this.sprite = sprite;
			this.next = next;
			this.prev = sprite.numVariations;
		}

		@Override
		public void exec()
		{
			sprite.numVariations = next;

			suppressCommands = true;
			variationsSpinner.setValue(next);
			suppressCommands = false;

			variationCountChanged();
		}

		@Override
		public void undo()
		{
			sprite.numVariations = prev;

			suppressCommands = true;
			variationsSpinner.setValue(prev);
			suppressCommands = false;

			variationCountChanged();
		}
	}

	private class SetColorModel extends AbstractCommand
	{
		private final ColorModel next;
		private final ColorModel prev;

		public SetColorModel(ColorModel next)
		{
			super("Change Color Model");

			this.next = next;
			this.prev = selectedColorModel;
		}

		@Override
		public boolean modifiesData()
		{
			return false;
		}

		@Override
		public void exec()
		{
			setColorModel(next);
		}

		@Override
		public void undo()
		{
			setColorModel(prev);
		}
	}

	private class SetPaletteIndex extends AbstractCommand
	{
		private final int next;
		private final int prev;

		public SetPaletteIndex(int next)
		{
			super("Change Color");

			this.next = next;
			this.prev = paletteColorIndex;
		}

		@Override
		public boolean modifiesData()
		{
			return false;
		}

		@Override
		public void exec()
		{
			setPaletteIndex(next);
		}

		@Override
		public void undo()
		{
			setPaletteIndex(prev);
		}
	}

	private class SetColor extends AbstractCommand
	{
		private final PalAsset asset;
		private final int index;
		private final Color next;
		private final Color prev;
		private boolean ignoreFirstExec;

		public SetColor(PalAsset asset, int index, Color next, boolean ignoreFirstExec)
		{
			super("Set Color");

			this.asset = asset;
			this.index = index;
			this.next = next;
			this.prev = asset.savedColors[index];

			// awkward but effective way to prevent the text field from updating itself after
			// triggering this command and disrupting user input in a frustrating way
			this.ignoreFirstExec = ignoreFirstExec;
		}

		@Override
		public void exec()
		{
			asset.pal.setColor(index, next);
			asset.savedColors[index] = next;

			swatchesPanel.swatches[index].setForeground(next);

			if (ignoreFirstExec)
				ignoreFirstExec = false;
			else
				setSelectedColor(next);

			paletteList.repaint();
			palAssetList.repaint();

			asset.dirty = true;
			asset.modified = true;
		}

		@Override
		public void undo()
		{
			asset.pal.setColor(index, prev);
			asset.savedColors[index] = prev;

			swatchesPanel.swatches[index].setForeground(prev);

			setSelectedColor(prev);

			paletteList.repaint();
			palAssetList.repaint();

			asset.dirty = true;
			asset.modified = true;
		}
	}

	private class TogglePreviewPalette extends AbstractCommand
	{
		private final JCheckBox checkbox;
		private final boolean prev;
		private final boolean next;

		public TogglePreviewPalette(JCheckBox checkbox)
		{
			super("Toggle Palette Preview");

			this.checkbox = checkbox;

			this.next = checkbox.isSelected();
			this.prev = !next;
		}

		@Override
		public boolean shouldExec()
		{
			return prev != next;
		}

		@Override
		public boolean modifiesData()
		{
			return false;
		}

		@Override
		public void exec()
		{
			super.exec();
			checkbox.setSelected(next);
		}

		@Override
		public void undo()
		{
			super.undo();
			checkbox.setSelected(prev);
		}
	}

}
