package game.sprite.editor;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import app.SwingUtils;
import game.map.editor.ui.SwatchPanel;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import net.miginfocom.swing.MigLayout;
import util.ui.ColorSlider;
import util.ui.ColorSlider.SliderListener;
import util.ui.HexTextField;

public class PalettesTab extends JPanel
{
	private JSpinner paletteGroupsSpinner;
	private JLabel paletteGroupsLabel;

	private JPanel paletteInfoPanel;
	private SwatchPanel colorPreview;
	private HexTextField colorHexValue;
	private JPanel rgbaPanel;
	private ColorSlider channelR, channelG, channelB;
	private ColorSlider channelH, channelS, channelV;
	private ColorSlider channelA;

	private static enum ColorModel
	{
		RGB, HSL
	}

	private ColorModel selectedColorModel = ColorModel.RGB;

	private ListPanel<SpritePalette> paletteList;
	private PaletteSwatchPanel swatchesPanel;

	private int paletteColorIndex = 1;
	private Sprite sprite = null;

	private boolean ignoreChanges = false;
	private boolean ignoreSliderUpdates = false;
	private boolean ignoreTextfieldUpdates = false;

	public PalettesTab()
	{
		paletteGroupsLabel = new JLabel();

		paletteGroupsSpinner = new JSpinner();
		SwingUtils.setFontSize(paletteGroupsSpinner, 12);
		paletteGroupsSpinner.setModel(new SpinnerNumberModel(0, 0, 99, 1));
		paletteGroupsSpinner.addChangeListener((e) -> {
			if (sprite != null) {
				int numPalettes = sprite.getPaletteCount();
				sprite.numVariations = (int) paletteGroupsSpinner.getValue();
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
		});
		SwingUtils.centerSpinnerText(paletteGroupsSpinner);
		SwingUtils.addBorderPadding(paletteGroupsSpinner);

		// create palette list

		paletteList = new ListPanel<>() {
			@Override
			public void onSelectEDT(SpritePalette selectedPal)
			{
				if (ignoreChanges)
					return;
				swatchesPanel.setPalette(selectedPal);
				paletteInfoPanel.setVisible(selectedPal != null);
			}

			@Override
			public void onDelete(SpritePalette palette)
			{
				palette.deleted = true;
			}

			@Override
			public void rename(int index, String newName)
			{
				DefaultListModel<SpritePalette> listModel = getListModel();
				SpritePalette sp = listModel.get(index);
				if (!sp.filename.equals(newName)) {
					sp.filename = makeUniqueName(sp, newName);
					// beep if the chosen name had to be changed to become unique
					if (!sp.filename.equals(newName)) {
						Toolkit.getDefaultToolkit().beep();
					}
				}
			}
		};

		paletteList.list.setCellRenderer(new PaletteSlicesRenderer());

		// create buttons

		JButton addButton = new JButton("Toggle Selected");
		addButton.addActionListener((e) -> {
			SpritePalette sp = paletteList.list.getSelectedValue();
			if (sp == null)
				return;

			sp.disabled = !sp.disabled;
			paletteList.list.repaint();
		});
		SwingUtils.addBorderPadding(addButton);

		/*
		JButton dupeButton = new JButton("Duplicate Selected");
		dupeButton.addActionListener((e) -> {
			SpritePalette original = paletteList.list.getSelectedValue();
			if(original == null)
				return;
		
			SpritePalette sp = new SpritePalette(original);
			sp.filename = makeUniqueName(sp, original.filename);
			DefaultListModel<SpritePalette> listModel = paletteList.getListModel();
			listModel.addElement(sp);
			selectPalette(sp);
		});
		SwingUtils.addBorderPadding(dupeButton);
		*/

		JButton renameButton = new JButton("Rename Selected");
		renameButton.addActionListener((e) -> {
			int index = paletteList.list.getSelectedIndex();
			if (index >= 0) {
				String oldName = paletteList.list.getSelectedValue().filename;
				paletteList.promptRenameAt(index, oldName);
				paletteList.list.repaint();
			}
		});
		SwingUtils.addBorderPadding(renameButton);

		// create color chooser

		JRadioButton rgbButton = new JRadioButton(ColorModel.RGB.toString());
		rgbButton.setSelected(true);
		rgbButton.addActionListener((e) -> {
			if (rgbButton.isSelected())
				setColorModel(ColorModel.RGB);
		});

		JRadioButton hslButton = new JRadioButton(ColorModel.HSL.toString());
		hslButton.setSelected(false);
		hslButton.addActionListener((e) -> {
			if (hslButton.isSelected())
				setColorModel(ColorModel.HSL);
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

			SpritePalette selectedPal = paletteList.list.getSelectedValue();
			if (selectedPal != null && selectedPal.hasPal()) {
				selectedPal.getPal().setColor(paletteColorIndex, rgb[0], rgb[1], rgb[2], a);
				for (ListDataListener listener : sprite.palettes.getListDataListeners()) {
					listener.contentsChanged(new ListDataEvent(sprite.palettes,
						ListDataEvent.CONTENTS_CHANGED,
						selectedPal.getIndex(), selectedPal.getIndex()));
				}
				selectedPal.dirty = true;
				selectedPal.modified = true;
			}
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

			ignoreTextfieldUpdates = true;
			setSelectedColor(c);
			ignoreTextfieldUpdates = false;

			swatchesPanel.swatches[paletteColorIndex].setForeground(c);
			colorPreview.setForeground(c);

			SpritePalette selectedPal = paletteList.list.getSelectedValue();
			if (selectedPal != null && selectedPal.hasPal()) {
				selectedPal.getPal().setColor(paletteColorIndex, r, g, b, a);
				for (ListDataListener listener : sprite.palettes.getListDataListeners()) {
					listener.contentsChanged(new ListDataEvent(sprite.palettes,
						ListDataEvent.CONTENTS_CHANGED,
						selectedPal.getIndex(), selectedPal.getIndex()));
				}
				selectedPal.dirty = true;
				selectedPal.modified = true;
			}
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

		paletteInfoPanel = new JPanel(new MigLayout("ins 0, fill, wrap"));
		paletteInfoPanel.add(swatchesPanel, "split 3, gapleft 16, gapbottom push");
		paletteInfoPanel.add(rightPanel, "gapleft 24, gaptop 8");

		setLayout(new MigLayout("fill, ins 16, wrap, hidemode 0"));

		add(SwingUtils.getLabel("Variations:", 12), "split 2, gapleft 8");
		add(paletteGroupsSpinner, "w 80!, gapleft 12, gapright 16");
		//		add(paletteGroupsLabel, "growx, gapbottom 4, gaptop 4");

		add(paletteList, "grow, pushy, span, wrap, gaptop 8");
		add(new JPanel(), "sg but, growx, split 3");
		add(addButton, "sg but, growx");
		//		add(dupeButton, "sg but, growx");
		add(renameButton, "sg but, growx");

		add(paletteInfoPanel, "growx, span, wrap, gaptop 16");
	}

	private static boolean isNameUsed(SpritePalette sp, String name)
	{
		for (SpritePalette other : sp.getSprite().palettes) {
			if (sp != other && other.filename.equals(name))
				return true;
		}
		return false;
	}

	private static String makeUniqueName(SpritePalette sp, String name)
	{
		if (!isNameUsed(sp, name))
			return name;

		int i = 1;
		Matcher m = Pattern.compile("(\\D+)(\\d+)").matcher(name);

		if (m.matches() && m.group(2) != null) {
			name = m.group(1);
			i = Integer.parseInt(m.group(2)) + 1;
		}

		while (true) {
			String newName = String.format("%s%d", name, i);
			if (!isNameUsed(sp, newName))
				return newName;
			// try the next name
			i++;
		}
	}

	public void selectPalette(SpritePalette sp)
	{
		paletteList.list.setSelectedValue(sp, true);
	}

	public void setSpriteEDT(Sprite sprite)
	{
		assert (SwingUtilities.isEventDispatchThread());
		this.sprite = sprite;

		paletteList.list.setModel(sprite.palettes);

		if (!sprite.palettes.isEmpty())
			paletteList.list.setSelectedIndex(0);

		paletteGroupsSpinner.setValue(sprite.numVariations);
	}

	public SpritePalette getOverridePalette()
	{
		return paletteList.list.getSelectedValue();
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
		private final PalettesTab palEditor;
		private SwatchPanel[] swatches;

		public PaletteSwatchPanel(PalettesTab palEditor)
		{
			this.palEditor = palEditor;
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
						int prevIndex = palEditor.paletteColorIndex;
						palEditor.paletteColorIndex = index;
						palEditor.setSelectedColor(swatches[index].getForeground());

						if (prevIndex >= 0)
							swatches[prevIndex].setBorder(null);
						swatches[index].setBorder(BorderFactory.createDashedBorder(null));
					}
				});

			}
		}

		public void setPalette(SpritePalette value)
		{
			if (value == null || !value.hasPal()) {
				setVisible(false);
				for (SwatchPanel panel : swatches)
					panel.setForeground(Color.gray);
			}
			else {
				setVisible(true);
				Color[] colors = value.getPal().getColors();
				for (int i = 0; i < swatches.length; i++)
					swatches[i].setForeground(colors[i]);

				palEditor.setSelectedColor(colors[palEditor.paletteColorIndex]);
			}
		}
	}
}
