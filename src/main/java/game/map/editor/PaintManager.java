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
import java.util.Deque;
import java.util.LinkedList;

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
import javax.swing.border.EtchedBorder;

import app.SwingUtils;
import common.Vector3f;
import game.map.editor.render.RenderingOptions.SurfaceMode;
import game.map.editor.render.TextureManager;
import game.map.editor.ui.GuiCommand;
import game.map.editor.ui.SwingGUI;
import game.map.mesh.Vertex;
import net.miginfocom.swing.MigLayout;
import util.identity.IdentityHashSet;
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

	private static enum RenderModeOption
	{
		Flat("Flat Shading"),
		Normal("Textured");

		private final String name;

		private RenderModeOption(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private static PaintVertexPanel paintVertexTab = null;
	private static Deque<Color> recentColors = new LinkedList<>();

	// hidden feature
	private static int[] rainbowRGB = new int[3];
	private static boolean usingRainbow = false;

	public static JPanel createPaintVertexTab(SwingGUI gui)
	{
		if (paintVertexTab == null)
			paintVertexTab = new PaintVertexPanel(gui);
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
							out_hsl[0] = getNewComponent(paintVertexTab.channelH, vhsl[0], brushStrength);
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
		paintVertexTab.setSelectedColor(c);
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
		if (paintVertexTab.rbFlatShaded.isSelected())
			return SurfaceMode.SHADED;

		if (paintVertexTab.rbTextured.isSelected())
			return SurfaceMode.TEXTURED;

		throw new IllegalStateException("No paint render mode is selected!");
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

		private PaintSlider innerRadiusSlider;
		private PaintSlider outerRadiusSlider;
		private PaintSlider forceSlider;
		private JComboBox<BrushFallOffType> fallOffComboBox;

		private JRadioButton rbFlatShaded;
		private JRadioButton rbTextured;

		private ColorModel selectedColorModel = ColorModel.RGB;
		private Color selectedColor;

		private boolean ignoreSliderUpdates = false;

		private PaintVertexPanel(SwingGUI gui)
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

				int a = channelA.isPaintEnabled() ? channelA.getValue() : 255;
				Color c = new Color(rgb[0], rgb[1], rgb[2], a);
				selectedColor = c;
				colorPreview.setForeground(c);
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

			innerRadiusSlider = new PaintSlider("Inner", "w 50!", (b, v) -> {}, 0, 150, 0, 50, false);
			outerRadiusSlider = new PaintSlider("Outer", "w 50!", (b, v) -> {
				innerRadiusSlider.setMaximum(outerRadiusSlider.getValue());
			}, 1, 500, 150, 50, false);
			forceSlider = new PaintSlider("Power", "w 50!", (b, v) -> {}, 1, 100, 100, 10, false);

			fallOffComboBox = new JComboBox<>(BrushFallOffType.values());
			SwingUtils.addBorderPadding(fallOffComboBox);

			rbFlatShaded = new JRadioButton(RenderModeOption.Flat.toString());
			rbTextured = new JRadioButton(RenderModeOption.Normal.toString());
			ButtonGroup renderModeGroup = new ButtonGroup();
			renderModeGroup.add(rbFlatShaded);
			renderModeGroup.add(rbTextured);
			rbFlatShaded.setSelected(true);
			SwingUtils.setFontSize(rbFlatShaded, 12);
			SwingUtils.setFontSize(rbTextured, 12);

			JButton colorButton = new JButton("Open Color Picker");
			gui.addButtonCommand(colorButton, GuiCommand.SHOW_CHOOSE_COLOR_DIALOG);

			Border border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);

			JPanel rgbaPanel = new JPanel(new MigLayout("fill, wrap, hidemode 3, ins 16 16 16 16"));
			rgbaPanel.setBorder(border);

			rgbaPanel.add(SwingUtils.getLabel("Color Model:", 12), "span, split 3, gapright 10, gapbottom 16");
			rgbaPanel.add(rgbButton, "gapleft 8, sg radio");
			rgbaPanel.add(hslButton, "gapleft 8, sg radio");
			SwingUtils.setFontSize(rgbButton, 12);
			SwingUtils.setFontSize(hslButton, 12);

			rgbaPanel.add(channelR, "grow");
			rgbaPanel.add(channelG, "grow");
			rgbaPanel.add(channelB, "grow");

			rgbaPanel.add(channelH, "grow");
			rgbaPanel.add(channelS, "grow");
			rgbaPanel.add(channelV, "grow");

			rgbaPanel.add(channelA, "grow");

			rgbaPanel.add(colorButton, "span, center, gaptop 16");

			JPanel brushPanel = new JPanel(new MigLayout("fill, wrap, ins 16 16 16 16"));
			brushPanel.setBorder(border);

			brushPanel.add(innerRadiusSlider, "grow");
			brushPanel.add(outerRadiusSlider, "grow");
			brushPanel.add(forceSlider, "grow, gapbottom 16");
			brushPanel.add(SwingUtils.getLabel("Fall Off", SwingConstants.CENTER, 12), "span, split 2, w 60!");
			brushPanel.add(fallOffComboBox, "w 160!");

			JPanel renderingPanel = new JPanel(new MigLayout("ins 16 16 16 16"));
			renderingPanel.setBorder(border);
			renderingPanel.add(rbFlatShaded, "w 100!");
			renderingPanel.add(rbTextured, "w 100!");

			setLayout(new MigLayout("wrap, fillx, insets 8"));
			add(SwingUtils.getLabel("Current Paint Color:", 14));
			add(colorPreview, "span, split 2, h 96!, w 96!, gap 16 8 16 16");
			add(getColorSwatchPanel());

			add(SwingUtils.getLabel("Choose Color", 14), "gapbottom 4");
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

		private void setSelectedColor(Color c)
		{
			ignoreSliderUpdates = true;

			switch (selectedColorModel) {
				case RGB:
					channelR.slider.setValue(c.getRed());
					channelG.slider.setValue(c.getGreen());
					channelB.slider.setValue(c.getBlue());
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

			channelA.slider.setValue(c.getAlpha());
			ignoreSliderUpdates = false;

			selectedColor = c;
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
		}
	}

	private static interface SliderListener
	{
		public void update(boolean preview, int value);
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

		private final JCheckBox checkbox;
		private final JTextField textField;
		private final JSlider slider;

		private final SliderListener listener;

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
			max = value;
			slider.setMaximum(value);
		}

		public int getValue()
		{
			return slider.getValue();
		}

		public void setValue(int value)
		{
			update = UpdateMode.FROM_OUTSIDE;
			textField.setText(Integer.toString(value));
			slider.setValue(value);
			update = UpdateMode.NONE;
		}

		private void updatePreview(UpdateMode mode, int value)
		{
			update = mode;
			if (mode == UpdateMode.FROM_SLIDER)
				textField.setText(Integer.toString(value));

			listener.update(true, value);
			update = UpdateMode.NONE;
		}

		private void updateValue(UpdateMode mode, int value)
		{
			update = mode;
			if (mode == UpdateMode.FROM_SLIDER)
				textField.setText(Integer.toString(value));
			else if (mode == UpdateMode.FROM_TEXTFIELD)
				slider.setValue(value);

			listener.update(false, value);
			update = UpdateMode.NONE;
		}

		public boolean isPaintEnabled()
		{
			return hasCheckbox && checkbox.isSelected();
		}
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
