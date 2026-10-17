package game.map.editor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

import common.Vector3f;
import common.commands.AbstractCommand;

import app.SwingUtils;
import game.map.editor.render.RenderingOptions.SurfaceMode;
import game.map.editor.render.TextureManager;
import game.map.editor.ui.GuiCommand;
import game.map.editor.ui.SwingGUI;
import game.map.mesh.Vertex;
import net.miginfocom.swing.MigLayout;
import util.identity.IdentityHashSet;
import util.ui.HexTextField;
import util.ui.LimitedLengthDocument;

public class PaintManager
{
	public static enum BrushFallOffType
	{
		None, Linear, Quadratic, Cosine
	}

	private static enum ColorModel
	{
		RGB, HSL
	}

	private static PaintVertexPanel paintVertexTab = null;
	private static Deque<Color> recentColors = new LinkedList<>();

	// hidden feature
	private static int[] rainbowRGB = new int[3];
	private static boolean usingRainbow = false;

	public static JPanel createPaintVertexTab(SwingGUI gui)
	{
		if (paintVertexTab == null)
			paintVertexTab = new PaintVertexPanel(button -> gui.addButtonCommand(button, GuiCommand.SHOW_CHOOSE_COLOR_DIALOG));
		return paintVertexTab;
	}

	public static void update(MapEditor editor, double deltaTime)
	{
		if (editor.keyboard.isAltDown()) {
			int[] out_hsl = new int[3];
			out_hsl[0] = (int) (hmax * (editor.getFrame() % 60) / 60.0);
			out_hsl[1] = smax;
			out_hsl[2] = lmax / 2;
			rainbowRGB = HSLtoRGB(out_hsl);
			paintVertexTab.colorPreview.setForeground(new Color(rainbowRGB[0], rainbowRGB[1], rainbowRGB[2]));
			usingRainbow = true;
		}
		else if (usingRainbow) {
			paintVertexTab.colorPreview.setForeground(paintVertexTab.selectedColor);
			usingRainbow = false;
		}
	}

	public static void paintVertices(Vector3f brushPos, IdentityHashSet<Vertex> paintingVertexSet)
	{
		BrushFallOffType fallOff = getFallOffType();
		float rin = getInnerBrushRadius();
		float rout = getOuterBrushRadius();
		float rout2 = rout * rout;

		for (Vertex v : paintingVertexSet) {
			float dx = brushPos.x - v.getCurrentX();
			float dy = brushPos.y - v.getCurrentY();
			float dz = brushPos.z - v.getCurrentZ();
			float r2 = dx * dx + dy * dy + dz * dz;

			if (r2 < rout2) {
				double r = Math.sqrt(r2);
				double s = 1.0;
				if (r > rin) {
					double f = (r - rin) / (rout - rin);
					switch (fallOff) {
						case None:
							s = 1.0;
							break;
						case Linear:
							s = 1.0 - f;
							break;
						case Quadratic:
							s = (1.0 - f) * (1.0 - f);
							break;
						case Cosine:
							s = Math.cos((Math.PI / 2) * f);
							break;
					}
					s = Math.max(Math.min(s, 1.0), 0.0); // clamp
				}

				int brushStrength = (int) (s * paintVertexTab.forceSlider.getValue());

				int[] out_rgb = new int[3];

				if (usingRainbow) {
					out_rgb = rainbowRGB;
				}
				else {
					switch (paintVertexTab.selectedColorModel) {
						case RGB:
							out_rgb[0] = getNewComponent(paintVertexTab.channelR, v.r & 0xFF, brushStrength);
							out_rgb[1] = getNewComponent(paintVertexTab.channelG, v.g & 0xFF, brushStrength);
							out_rgb[2] = getNewComponent(paintVertexTab.channelB, v.b & 0xFF, brushStrength);
							break;
						case HSL:
							int[] vhsl = RGBtoHSL(new int[] { v.r & 0xFF, v.g & 0xFF, v.b & 0xFF });
							int[] out_hsl = new int[3];
							out_hsl[0] = getNewHue(paintVertexTab.channelH, vhsl[0], brushStrength);
							out_hsl[1] = getNewComponent(paintVertexTab.channelS, vhsl[1], brushStrength);
							out_hsl[2] = getNewComponent(paintVertexTab.channelV, vhsl[2], brushStrength);
							out_rgb = HSLtoRGB(out_hsl);
							break;
						default:
							throw new RuntimeException("Unknown color model.");
					}
				}

				v.r = (byte) out_rgb[0];
				v.g = (byte) out_rgb[1];
				v.b = (byte) out_rgb[2];
				v.a = (byte) getNewComponent(paintVertexTab.channelA, v.a & 0xFF, brushStrength);
				v.painted = true;
			}
		}
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

	public static Color getSelectedColor()
	{
		return paintVertexTab.getSelectedColor();
	}

	public static void setSelectedColor(Color c)
	{
		commitSelectedColor(getSelectedColor(), c);
	}

	private static void commitSelectedColor(Color oldColor, Color newColor)
	{
		int[] oldValues = paintVertexTab.getColorValues();
		paintVertexTab.setSelectedColor(newColor);
		MapEditor.execute(new SetPaintColor(oldValues, paintVertexTab.getColorValues(), oldColor, newColor));
	}

	// Slider values cannot always be recovered from RGB; for example, hue 0 and 3600 produce the same color.
	private static void commitSelectedColor(PaintSlider slider, int oldSliderValue, int newSliderValue, Color oldColor, Color newColor)
	{
		int[] oldValues = paintVertexTab.getColorValues();
		PaintSlider[] sliders = paintVertexTab.getColorSliders();
		for (int i = 0; i < sliders.length; i++)
			if (sliders[i] == slider)
				oldValues[i] = oldSliderValue;
		paintVertexTab.applySliderColor(slider, newSliderValue, newColor);
		MapEditor.execute(new SetPaintColor(oldValues, paintVertexTab.getColorValues(), oldColor, newColor));
	}

	private static final class SetPaintColor extends AbstractCommand
	{
		private final int[] oldValues;
		private final int[] newValues;
		private final Color oldColor;
		private final Color newColor;

		private SetPaintColor(int[] oldValues, int[] newValues, Color oldColor, Color newColor)
		{
			super("Set Paint Color");
			this.oldValues = oldValues;
			this.newValues = newValues;
			this.oldColor = oldColor;
			this.newColor = newColor;
		}

		@Override
		public boolean shouldExec()
		{
			return !Arrays.equals(oldValues, newValues) || !oldColor.equals(newColor);
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
			apply(newColor, newValues);
		}

		@Override
		public void undo()
		{
			super.undo();
			apply(oldColor, oldValues);
		}

		private void apply(Color color, int[] values)
		{
			paintVertexTab.colorBeforeAdjustment = null;
			paintVertexTab.setColorValues(values);
			paintVertexTab.previewSelectedColor(color);
		}
	}

	public static void pushSelectedColor()
	{
		pushColor(getSelectedColor());
	}

	public static void pushColor(Color c)
	{
		if (!recentColors.contains(c)) {
			recentColors.addFirst(c);

			if (recentColors.size() > 24)
				recentColors.removeLast();

			int i = 0;
			for (Color rc : recentColors)
				paintVertexTab.recentColorPreviews[i++].setForeground(rc);
		}
	}

	public static SurfaceMode getRenderMode()
	{
		if (paintVertexTab.cbShowTextures.isSelected())
			return SurfaceMode.TEXTURED;
		else
			return SurfaceMode.SHADED;
	}

	private static BrushFallOffType getFallOffType()
	{
		return (BrushFallOffType) paintVertexTab.fallOffComboBox.getSelectedItem();
	}

	public static int getInnerBrushRadius()
	{
		return paintVertexTab.innerRadiusSlider.getValue();
	}

	public static int getOuterBrushRadius()
	{
		return paintVertexTab.outerRadiusSlider.getValue();
	}

	public static boolean shouldDrawInnerRadius()
	{
		return getFallOffType() != BrushFallOffType.None && getInnerBrushRadius() > 0;
	}

	private static class PaintVertexPanel extends JPanel
	{
		private JLabel colorPreview;
		private JLabel[] recentColorPreviews;
		private PaintSlider channelR, channelG, channelB;
		private PaintSlider channelH, channelS, channelV;
		private PaintSlider channelA;
		private HexTextField colorHexField;
		private JRadioButton rgbButton;
		private JRadioButton hslButton;

		private PaintSlider innerRadiusSlider;
		private PaintSlider outerRadiusSlider;
		private PaintSlider forceSlider;
		private JComboBox<BrushFallOffType> fallOffComboBox;

		private JCheckBox cbShowTextures;

		private ColorModel selectedColorModel = ColorModel.RGB;
		private Color selectedColor;
		private Color colorBeforeAdjustment;
		private BrushFallOffType committedFallOff;

		private boolean ignoreSliderUpdates = false;
		private boolean ignoreBrushUpdates = false;

		private PaintVertexPanel(Consumer<JButton> configureColorPicker)
		{
			colorPreview = new JLabel();
			ImageIcon icon = new ImageIcon(TextureManager.background) {
				@Override
				public void paintIcon(Component c, Graphics g, int x, int y)
				{
					g.drawImage(TextureManager.background, x, y, null);
					g.fillRect(x, y, getIconWidth(), getIconHeight());
				}
			};
			colorPreview.setIcon(icon);
			colorPreview.setForeground(new Color(255, 255, 255, 255));
			selectedColor = Color.white;

			rgbButton = new JRadioButton(ColorModel.RGB.toString());
			rgbButton.setSelected(true);
			rgbButton.addActionListener((e) -> {
				if (rgbButton.isSelected())
					setColorModel(ColorModel.RGB);
			});

			hslButton = new JRadioButton(ColorModel.HSL.toString());
			hslButton.setSelected(false);
			hslButton.addActionListener((e) -> {
				if (hslButton.isSelected())
					setColorModel(ColorModel.HSL);
			});

			ButtonGroup group = new ButtonGroup();
			group.add(rgbButton);
			group.add(hslButton);

			// update the color preview when the sliders are adjusted
			SliderListener colorPreviewListener = (slider, preview, oldValue, value) -> {
				if (ignoreSliderUpdates)
					return;

				Color c = getColorFromControls();

				if (preview) {
					if (colorBeforeAdjustment == null)
						colorBeforeAdjustment = selectedColor;
					previewSelectedColor(c);
				}
				else {
					Color oldColor = colorBeforeAdjustment == null ? selectedColor : colorBeforeAdjustment;
					colorBeforeAdjustment = null;
					commitSelectedColor(slider, oldValue, value, oldColor, c);
				}
			};

			channelR = new PaintSlider("R", "w 30!", colorPreviewListener, 0, 255, 255, 32, true);
			channelG = new PaintSlider("G", "w 30!", colorPreviewListener, 0, 255, 255, 32, true);
			channelB = new PaintSlider("B", "w 30!", colorPreviewListener, 0, 255, 255, 32, true);

			channelH = new PaintSlider("H", "w 30!", colorPreviewListener, 0, hmax, hmax, 600, true);
			channelS = new PaintSlider("S", "w 30!", colorPreviewListener, 0, smax, smax, 100, true);
			channelV = new PaintSlider("L", "w 30!", colorPreviewListener, 0, lmax, lmax, 100, true);

			channelH.setVisible(false);
			channelS.setVisible(false);
			channelV.setVisible(false);

			channelA = new PaintSlider("A", "w 30!", colorPreviewListener, 0, 255, 255, 32, true);

			colorHexField = new HexTextField(6, (rgb) -> {
				int r = (rgb >>> 16) & 0xFF;
				int g = (rgb >>> 8) & 0xFF;
				int b = rgb & 0xFF;
				PaintManager.setSelectedColor(new Color(r, g, b, selectedColor.getAlpha()));
			});
			colorHexField.setHorizontalAlignment(SwingConstants.CENTER);
			colorHexField.setToolTipText("RGB color in RRGGBB format");
			SwingUtils.setFontSize(colorHexField, 12);
			SwingUtils.addBorderPadding(colorHexField);
			colorHexField.setValue(0xFFFFFF);

			makePaintChannelUndoable(channelR, "Red");
			makePaintChannelUndoable(channelG, "Green");
			makePaintChannelUndoable(channelB, "Blue");
			makePaintChannelUndoable(channelH, "Hue");
			makePaintChannelUndoable(channelS, "Saturation");
			makePaintChannelUndoable(channelV, "Lightness");
			makePaintChannelUndoable(channelA, "Alpha");

			innerRadiusSlider = new PaintSlider("Inner", "w 50!", (slider, preview, oldValue, value) -> {
				if (!preview)
					commitBrushSettings("Set Inner Brush Radius");
			}, 0, 150, 0, 50, false);
			outerRadiusSlider = new PaintSlider("Outer", "w 50!", (slider, preview, oldValue, value) -> {
				innerRadiusSlider.setMaximum(value);
				if (!preview)
					commitBrushSettings("Set Outer Brush Radius");
			}, 1, 500, 150, 50, false);
			forceSlider = new PaintSlider("Power", "w 50!", (slider, preview, oldValue, value) -> {
				if (!preview)
					commitBrushSettings("Set Brush Power");
			}, 1, 100, 100, 10, false);

			fallOffComboBox = new JComboBox<>(BrushFallOffType.values());
			committedFallOff = (BrushFallOffType) fallOffComboBox.getSelectedItem();
			fallOffComboBox.addActionListener((e) -> {
				if (!ignoreBrushUpdates)
					commitBrushSettings("Set Brush Falloff");
			});
			SwingUtils.addBorderPadding(fallOffComboBox);

			cbShowTextures = new JCheckBox(" Show textures while painting");
			cbShowTextures.setVerticalAlignment(SwingConstants.CENTER);
			cbShowTextures.addActionListener((e) -> {
				boolean newValue = cbShowTextures.isSelected();
				MapEditor.execute(new SetShowTextures(!newValue, newValue));
			});

			JButton pickerButton = new JButton("Open Color Picker");
			SwingUtils.addBorderPadding(pickerButton);
			configureColorPicker.accept(pickerButton);

			Border border = BorderFactory.createEtchedBorder();

			JPanel rgbaPanel = new JPanel(new MigLayout("fill, wrap, hidemode 3, ins 16 16 16 16"));
			rgbaPanel.setBorder(border);

			rgbaPanel.add(colorPreview, "span, split 2, h 96!, w 96!, gap 16 8 16 16");
			rgbaPanel.add(getColorSwatchPanel());

			rgbaPanel.add(SwingUtils.getLabel("Color Model:", 12), "span, split 5, gapright 10, gapbottom 16");
			rgbaPanel.add(rgbButton, "gapleft 8, sg radio");
			rgbaPanel.add(hslButton, "gapleft 8, sg radio");
			rgbaPanel.add(new JLabel(), "growx, pushx");
			rgbaPanel.add(colorHexField, "w 80!");
			SwingUtils.setFontSize(rgbButton, 12);
			SwingUtils.setFontSize(hslButton, 12);

			rgbaPanel.add(channelR, "grow");
			rgbaPanel.add(channelG, "grow");
			rgbaPanel.add(channelB, "grow");

			rgbaPanel.add(channelH, "grow");
			rgbaPanel.add(channelS, "grow");
			rgbaPanel.add(channelV, "grow");

			rgbaPanel.add(channelA, "grow");

			rgbaPanel.add(pickerButton, "span, center, gaptop 16");

			JPanel brushPanel = new JPanel(new MigLayout("fill, wrap, ins 16 16 16 16"));
			brushPanel.setBorder(border);

			brushPanel.add(innerRadiusSlider, "grow");
			brushPanel.add(outerRadiusSlider, "grow");
			brushPanel.add(forceSlider, "grow, gapbottom 16");
			brushPanel.add(SwingUtils.getLabel("Fall Off", SwingConstants.CENTER, 12), "span, split 2, w 60!");
			brushPanel.add(fallOffComboBox, "w 160!");

			JPanel renderingPanel = new JPanel(new MigLayout("ins 16 16 16 16"));
			renderingPanel.setBorder(border);
			renderingPanel.add(cbShowTextures, "growx");

			setLayout(new MigLayout("wrap, fillx, insets 8"));
			add(SwingUtils.getLabel("Current Color", 14));
			add(rgbaPanel, "grow, gapbottom 16");

			add(SwingUtils.getLabel("Brush Settings", 14), "gapbottom 4");

			add(brushPanel, "grow, gapbottom 16");

			add(SwingUtils.getLabel("Rendering", 14), "gapbottom 4");
			add(renderingPanel, "grow");
		}

		private JPanel getColorSwatchPanel()
		{
			JPanel colorPanel = new JPanel(new MigLayout("fill, gap 4"));

			ImageIcon iconEven = new ImageIcon(TextureManager.background) {
				@Override
				public void paintIcon(Component c, Graphics g, int x, int y)
				{
					g.drawImage(TextureManager.background, x, y + 4, null);
					g.fillRect(x, y, getIconWidth(), getIconHeight());
				}
			};

			ImageIcon iconOdd = new ImageIcon(TextureManager.background) {
				@Override
				public void paintIcon(Component c, Graphics g, int x, int y)
				{
					g.drawImage(TextureManager.background, x - 8, y + 4, null);
					g.fillRect(x, y, getIconWidth(), getIconHeight());
				}
			};

			int columns = 6;
			recentColorPreviews = new JLabel[24];

			for (int i = 0; i < recentColorPreviews.length; i++) {
				final int row = i / columns;
				final int col = i % columns;
				boolean evenParity = (row + col) % 2 == 0;

				JLabel color = new JLabel();
				color.setIcon(evenParity ? iconEven : iconOdd);
				color.setForeground(new Color(
					(int) (Math.random() * 255),
					(int) (Math.random() * 255),
					(int) (Math.random() * 255),
					255));

				String fmt = "h 24!, w 24!" + (((i + 1) % columns == 0) ? ", wrap" : "");
				colorPanel.add(color, fmt);

				color.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e)
					{
						PaintManager.setSelectedColor(color.getForeground());
					}
				});

				recentColorPreviews[i] = color;
			}

			return colorPanel;
		}

		private Color getSelectedColor()
		{
			return selectedColor;
		}

		private PaintSlider[] getColorSliders()
		{
			return new PaintSlider[] { channelR, channelG, channelB, channelH, channelS, channelV, channelA };
		}

		private int[] getColorValues()
		{
			return Arrays.stream(getColorSliders()).mapToInt(PaintSlider::getValue).toArray();
		}

		private void setColorValues(int[] values)
		{
			PaintSlider[] sliders = getColorSliders();
			for (int i = 0; i < sliders.length; i++)
				sliders[i].setValue(values[i]);
		}

		private void setSelectedColor(Color c)
		{
			colorBeforeAdjustment = null;
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

			selectedColor = c;
			colorPreview.setForeground(c);
			colorHexField.setValue((c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue());
		}

		private void previewSelectedColor(Color c)
		{
			selectedColor = c;
			colorPreview.setForeground(c);
			colorHexField.setValue((c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue());
		}

		private void applySliderColor(PaintSlider slider, int value, Color c)
		{
			colorBeforeAdjustment = null;
			slider.setValue(value);
			previewSelectedColor(c);
		}

		private Color getColorFromControls()
		{
			int[] rgb = new int[3];

			switch (selectedColorModel) {
				case RGB:
					rgb[0] = channelR.isPaintEnabled() ? channelR.getValue() : 0;
					rgb[1] = channelG.isPaintEnabled() ? channelG.getValue() : 0;
					rgb[2] = channelB.isPaintEnabled() ? channelB.getValue() : 0;
					break;
				case HSL:
					int[] hsl = new int[3];
					hsl[0] = channelH.isPaintEnabled() ? channelH.getValue() : 0;
					hsl[1] = channelS.isPaintEnabled() ? channelS.getValue() : smax;
					hsl[2] = channelV.isPaintEnabled() ? channelV.getValue() : (lmax / 2);
					rgb = HSLtoRGB(hsl);
					break;
				default:
					throw new RuntimeException("Unknown color model.");
			}

			int alpha = channelA.isPaintEnabled() ? channelA.getValue() : 255;
			return new Color(rgb[0], rgb[1], rgb[2], alpha);
		}

		private void makePaintChannelUndoable(PaintSlider slider, String channelName)
		{
			slider.setCheckboxListener(() -> {
				boolean newValue = slider.isPaintEnabled();
				boolean oldValue = !newValue;
				applyPaintChannelEnabled(slider, newValue);
				MapEditor.execute(new SetPaintChannelEnabled(channelName, slider, oldValue, newValue));
			});
		}

		private void applyPaintChannelEnabled(PaintSlider slider, boolean enabled)
		{
			slider.setPaintEnabled(enabled);
		}

		private void setColorModel(ColorModel mdl)
		{
			ColorModel oldModel = selectedColorModel;
			int[] oldValues = getColorValues();
			applyColorModel(mdl);
			MapEditor.execute(new SetPaintColorModel(oldModel, mdl, oldValues, getColorValues()));
		}

		private void applyColorModel(ColorModel mdl)
		{
			rgbButton.setSelected(mdl == ColorModel.RGB);
			hslButton.setSelected(mdl == ColorModel.HSL);

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
		}

		private final class SetPaintColorModel extends AbstractCommand
		{
			private final ColorModel oldModel;
			private final ColorModel newModel;
			private final int[] oldValues;
			private final int[] newValues;

			private SetPaintColorModel(ColorModel oldModel, ColorModel newModel, int[] oldValues, int[] newValues)
			{
				super("Set Paint Color Model");
				this.oldModel = oldModel;
				this.newModel = newModel;
				this.oldValues = oldValues;
				this.newValues = newValues;
			}

			@Override
			public boolean shouldExec()
			{
				return oldModel != newModel;
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
				applyColorModel(newModel);
				setColorValues(newValues);
			}

			@Override
			public void undo()
			{
				super.undo();
				applyColorModel(oldModel);
				setColorValues(oldValues);
			}
		}

		private final class SetPaintChannelEnabled extends AbstractCommand
		{
			private final PaintSlider slider;
			private final boolean oldValue;
			private final boolean newValue;

			private SetPaintChannelEnabled(String channelName, PaintSlider slider, boolean oldValue, boolean newValue)
			{
				super("Set " + channelName + " Paint Channel");
				this.slider = slider;
				this.oldValue = oldValue;
				this.newValue = newValue;
			}

			@Override
			public boolean shouldExec()
			{
				return oldValue != newValue;
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
				applyPaintChannelEnabled(slider, newValue);
			}

			@Override
			public void undo()
			{
				super.undo();
				applyPaintChannelEnabled(slider, oldValue);
			}
		}

		private BrushSettings getCommittedBrushSettings()
		{
			return new BrushSettings(
				innerRadiusSlider.getCommittedValue(),
				outerRadiusSlider.getCommittedValue(),
				forceSlider.getCommittedValue(),
				committedFallOff);
		}

		private BrushSettings getCurrentBrushSettings()
		{
			return new BrushSettings(
				innerRadiusSlider.getValue(),
				outerRadiusSlider.getValue(),
				forceSlider.getValue(),
				(BrushFallOffType) fallOffComboBox.getSelectedItem());
		}

		private void commitBrushSettings(String commandName)
		{
			BrushSettings oldSettings = getCommittedBrushSettings();
			BrushSettings newSettings = getCurrentBrushSettings();
			applyBrushSettings(newSettings);
			MapEditor.execute(new SetBrushSettings(commandName, oldSettings, newSettings));
		}

		private void applyBrushSettings(BrushSettings settings)
		{
			ignoreBrushUpdates = true;
			outerRadiusSlider.setValue(settings.outerRadius);
			innerRadiusSlider.setMaximum(settings.outerRadius);
			innerRadiusSlider.setValue(settings.innerRadius);
			forceSlider.setValue(settings.power);
			fallOffComboBox.setSelectedItem(settings.fallOff);
			committedFallOff = settings.fallOff;
			ignoreBrushUpdates = false;
		}

		private static final class BrushSettings
		{
			private final int innerRadius;
			private final int outerRadius;
			private final int power;
			private final BrushFallOffType fallOff;

			private BrushSettings(int innerRadius, int outerRadius, int power, BrushFallOffType fallOff)
			{
				this.innerRadius = innerRadius;
				this.outerRadius = outerRadius;
				this.power = power;
				this.fallOff = fallOff;
			}
		}

		private final class SetBrushSettings extends AbstractCommand
		{
			private final BrushSettings oldSettings;
			private final BrushSettings newSettings;

			private SetBrushSettings(String commandName, BrushSettings oldSettings, BrushSettings newSettings)
			{
				super(commandName);
				this.oldSettings = oldSettings;
				this.newSettings = newSettings;
			}

			@Override
			public boolean shouldExec()
			{
				return oldSettings.innerRadius != newSettings.innerRadius
					|| oldSettings.outerRadius != newSettings.outerRadius
					|| oldSettings.power != newSettings.power
					|| oldSettings.fallOff != newSettings.fallOff;
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
				applyBrushSettings(newSettings);
			}

			@Override
			public void undo()
			{
				super.undo();
				applyBrushSettings(oldSettings);
			}
		}

		private final class SetShowTextures extends AbstractCommand
		{
			private final boolean oldValue;
			private final boolean newValue;

			private SetShowTextures(boolean oldValue, boolean newValue)
			{
				super("Show Textures While Painting");
				this.oldValue = oldValue;
				this.newValue = newValue;
			}

			@Override
			public boolean shouldExec()
			{
				return oldValue != newValue;
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
				cbShowTextures.setSelected(newValue);
			}

			@Override
			public void undo()
			{
				super.undo();
				cbShowTextures.setSelected(oldValue);
			}
		}
	}

	private static interface SliderListener
	{
		public void update(PaintSlider slider, boolean preview, int oldValue, int newValue);
	}

	private static class PaintSlider extends JComponent
	{
		private static enum UpdateMode
		{
			NONE, FROM_SLIDER, FROM_TEXTFIELD, FROM_OUTSIDE
		}

		private UpdateMode update = UpdateMode.NONE;

		private int max;
		private final int min;
		private final boolean hasCheckbox;
		private int committedValue;

		private final JCheckBox checkbox;
		private final JTextField textField;
		private final JSlider slider;

		private final SliderListener listener;
		private Runnable checkboxListener;

		private PaintSlider(String lblText, String lblLayout, SliderListener listener, int minValue, int maxValue, int initialValue, int ticks,
			boolean hasCheckbox)
		{
			this.listener = listener;
			this.hasCheckbox = hasCheckbox;
			min = minValue;
			max = maxValue;
			slider = new JSlider(min, max, initialValue);
			slider.setMajorTickSpacing(ticks);
			slider.setMinorTickSpacing(ticks / 2);
			slider.setPaintTicks(true);

			slider.addChangeListener((e) -> {
				if (update != UpdateMode.NONE)
					return;

				if (slider.getValueIsAdjusting())
					updatePreview(UpdateMode.FROM_SLIDER, slider.getValue());
				else
					updateValue(UpdateMode.FROM_SLIDER, slider.getValue());
			});

			checkbox = new JCheckBox();
			checkbox.setSelected(true);
			checkbox.addActionListener((e) -> {
				if (checkboxListener != null)
					checkboxListener.run();
			});

			textField = new JTextField("0", 5);
			textField.setFont(textField.getFont().deriveFont(12f));
			textField.setHorizontalAlignment(SwingConstants.CENTER);
			SwingUtils.addBorderPadding(textField);

			textField.setDocument(new LimitedLengthDocument(6));

			// document filter might be nicer, but this works
			textField.addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent ke)
				{
					if (update != UpdateMode.NONE)
						return;

					String text = textField.getText();
					if (text.isEmpty() || text.equals("-"))
						return;

					try {
						int value = Integer.parseInt(text);
						if (value > max) {
							value = max;
							textField.setText(Integer.toString(value));
						}
						else if (value < min) {
							value = min;
							textField.setText(Integer.toString(value));
						}
						updatePreview(UpdateMode.FROM_TEXTFIELD, value);
					}
					catch (NumberFormatException e) {
						textField.setText(Integer.toString(slider.getValue()));
					}
				}
			});

			// things that commit changes from text field
			textField.addFocusListener(new FocusListener() {
				@Override
				public void focusGained(FocusEvent e)
				{}

				@Override
				public void focusLost(FocusEvent e)
				{
					commitTextField();
				}
			});
			textField.addActionListener((e) -> {
				commitTextField();
			});

			setLayout(new MigLayout("fillx, ins 0"));

			if (hasCheckbox)
				add(checkbox);

			add(SwingUtils.getLabel(lblText, SwingConstants.CENTER, 12), lblLayout);
			add(slider, "w 60%, growy");
			add(textField, "w 60!");

			setValue(slider.getValue());
		}

		private void commitTextField()
		{
			String text = textField.getText();
			if (text.isEmpty()) {
				updateValue(UpdateMode.FROM_TEXTFIELD, min);
				return;
			}

			try {
				int value = Integer.parseInt(text);
				updateValue(UpdateMode.FROM_TEXTFIELD, value);
			}
			catch (NumberFormatException n) {
				textField.setText(Integer.toString(slider.getValue()));
			}
		}

		public int getMaxValue()
		{
			return max;
		}

		public void setMaximum(int value)
		{
			UpdateMode oldUpdate = update;
			update = UpdateMode.FROM_OUTSIDE;
			max = value;
			slider.setMaximum(value);
			textField.setText(Integer.toString(slider.getValue()));
			update = oldUpdate;
		}

		public int getValue()
		{
			return slider.getValue();
		}

		public void setValue(int value)
		{
			value = Math.max(min, Math.min(max, value));
			update = UpdateMode.FROM_OUTSIDE;
			textField.setText(Integer.toString(value));
			slider.setValue(value);
			committedValue = value;
			update = UpdateMode.NONE;
		}

		public int getCommittedValue()
		{
			return committedValue;
		}

		private void updatePreview(UpdateMode mode, int value)
		{
			value = Math.max(min, Math.min(max, value));
			update = mode;
			if (mode == UpdateMode.FROM_SLIDER)
				textField.setText(Integer.toString(value));
			else if (mode == UpdateMode.FROM_TEXTFIELD)
				slider.setValue(value);

			listener.update(this, true, committedValue, value);
			update = UpdateMode.NONE;
		}

		private void updateValue(UpdateMode mode, int value)
		{
			value = Math.max(min, Math.min(max, value));
			update = mode;
			textField.setText(Integer.toString(value));
			slider.setValue(value);

			listener.update(this, false, committedValue, value);
			committedValue = value;
			update = UpdateMode.NONE;
		}

		public void setCheckboxListener(Runnable listener)
		{
			checkboxListener = listener;
		}

		public void setPaintEnabled(boolean enabled)
		{
			checkbox.setSelected(enabled);
		}

		public boolean isPaintEnabled()
		{
			return hasCheckbox && checkbox.isSelected();
		}
	}

	private static int getNewHue(PaintSlider slider, int hue, int increment)
	{
		// RGB cannot preserve the hue branch at the red seam or for gray colors, so choose the branch nearest the target.
		if (hue == 0 && slider.isPaintEnabled() && slider.getValue() > hmax / 2)
			hue = hmax;

		return getNewComponent(slider, hue, increment);
	}

	public static int getNewComponent(PaintSlider slider, int val, int increment)
	{
		if (!slider.isPaintEnabled())
			return val;

		return blend(val, slider.getValue(), (int) Math.round((slider.getMaxValue() / 255.0) * increment));
	}

	private static int blend(int currentValue, int targetValue, int increment)
	{
		int difference = targetValue - currentValue;

		if (difference > 0)
			return (difference > increment) ? currentValue + increment : targetValue;

		if (difference < 0)
			return (-difference > increment) ? currentValue - increment : targetValue;

		return currentValue;
	}
}
