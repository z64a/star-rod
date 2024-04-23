package game.texture.editor;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.EtchedBorder;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;
import util.ui.ColorSlider;
import util.ui.ColorSlider.SliderListener;

public class ImageColorChooser extends JPanel
{
	private JPanel rgbaPanel;
	private ColorSlider channelR, channelG, channelB;
	private ColorSlider channelH, channelS, channelV;
	private ColorSlider channelI;
	private ColorSlider channelA;

	public static enum ColorModel
	{
		Intensity, RGB, HSL
	}

	private ColorModel selectedColorModel = ColorModel.RGB;
	private boolean hasAlpha = true;

	private JPanel buttonPanel;
	private JRadioButton rgbButton;
	private JRadioButton hslButton;

	private boolean ignoreSliderUpdates = false;

	public static interface ColorUpdateListener
	{
		public void updateColor(Color c, boolean bAdjusting);
	}

	public ImageColorChooser(ColorUpdateListener listener)
	{
		rgbButton = new JRadioButton(ColorModel.RGB.toString());
		rgbButton.setSelected(true);
		rgbButton.addActionListener((e) -> {
			if (rgbButton.isSelected())
				setColorModel(ColorModel.RGB, true);
		});

		hslButton = new JRadioButton(ColorModel.HSL.toString());
		hslButton.setSelected(false);
		hslButton.addActionListener((e) -> {
			if (hslButton.isSelected())
				setColorModel(ColorModel.HSL, true);
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
				case Intensity:
					rgb[0] = channelI.getValue();
					rgb[1] = rgb[0];
					rgb[2] = rgb[0];
					break;
				default:
					throw new RuntimeException("Unknown color model.");
			}

			int a = hasAlpha ? channelA.getValue() : 255;
			Color c = new Color(rgb[0], rgb[1], rgb[2], a);

			listener.updateColor(c, preview);
		};

		channelR = new ColorSlider("R", "w 30!", 0, 255, 255, 32, colorPreviewListener);
		channelG = new ColorSlider("G", "w 30!", 0, 255, 255, 32, colorPreviewListener);
		channelB = new ColorSlider("B", "w 30!", 0, 255, 255, 32, colorPreviewListener);

		channelH = new ColorSlider("H", "w 30!", 0, hmax, hmax, 600, colorPreviewListener);
		channelS = new ColorSlider("S", "w 30!", 0, smax, smax, 100, colorPreviewListener);
		channelV = new ColorSlider("L", "w 30!", 0, lmax, lmax, 100, colorPreviewListener);

		channelI = new ColorSlider("I", "w 30!", 0, 255, 255, 32, colorPreviewListener);
		channelA = new ColorSlider("A", "w 30!", 0, 255, 255, 32, colorPreviewListener);

		channelH.setVisible(false);
		channelS.setVisible(false);
		channelV.setVisible(false);
		channelI.setVisible(false);

		rgbaPanel = new JPanel(new MigLayout("fill, hidemode 3, wrap"));
		rgbaPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		buttonPanel = new JPanel(new MigLayout("fill, ins 0"));
		buttonPanel.add(SwingUtils.getLabel("Color Model:", 12), "span, split 3, gapright 10");
		buttonPanel.add(rgbButton, "w 50!, sg radio");
		buttonPanel.add(hslButton, "w 50!, sg radio");
		SwingUtils.setFontSize(rgbButton, 12);
		SwingUtils.setFontSize(hslButton, 12);

		rgbaPanel.add(buttonPanel);

		rgbaPanel.add(channelR);
		rgbaPanel.add(channelG);
		rgbaPanel.add(channelB);

		rgbaPanel.add(channelH);
		rgbaPanel.add(channelS);
		rgbaPanel.add(channelV);

		rgbaPanel.add(channelI);

		rgbaPanel.add(channelA);

		setLayout(new MigLayout("ins 0, fill, wrap"));
		add(rgbaPanel, "gaptop 8");

		setSelectedColor(new Color(255, 0, 255, 255));
	}

	public void setSelectedColor(Color c)
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
			case Intensity:
				int intensity = RGBtoIntensity(new int[] { c.getRed(), c.getGreen(), c.getBlue() });
				channelI.setValue(intensity);
				break;
			default:
				throw new RuntimeException("Unknown color model.");
		}

		if (hasAlpha)
			channelA.setValue(c.getAlpha());
		ignoreSliderUpdates = false;
	}

	public void enableAlpha(boolean hasAlpha)
	{
		if (this.hasAlpha == hasAlpha)
			return;

		this.hasAlpha = hasAlpha;

		channelA.setVisible(hasAlpha);
		rgbaPanel.revalidate();
	}

	public void setColorModel(ColorModel mdl)
	{
		setColorModel(mdl, false);
	}

	private void setColorModel(ColorModel mdl, boolean fromUI)
	{
		if (mdl == selectedColorModel)
			return;

		// current model to RGB
		int[] rgb = new int[3];
		switch (selectedColorModel) {
			case RGB:
				rgb = new int[] { channelR.getValue(), channelG.getValue(), channelB.getValue() };
				break;
			case HSL:
				rgb = HSLtoRGB(new int[] { channelH.getValue(), channelS.getValue(), channelV.getValue() });
				break;
			case Intensity:
				rgb = new int[] { channelI.getValue(), channelI.getValue(), channelI.getValue() };
				break;
		}

		// RGB to new model
		switch (mdl) {
			case RGB:
				channelI.setVisible(false);
				buttonPanel.setVisible(true);
				channelR.setVisible(true);
				channelG.setVisible(true);
				channelB.setVisible(true);
				channelH.setVisible(false);
				channelS.setVisible(false);
				channelV.setVisible(false);
				channelR.setValue(rgb[0]);
				channelG.setValue(rgb[1]);
				channelB.setValue(rgb[2]);
				if (!fromUI)
					rgbButton.setSelected(true);
				break;
			case HSL:
				channelI.setVisible(false);
				buttonPanel.setVisible(true);
				channelR.setVisible(false);
				channelG.setVisible(false);
				channelB.setVisible(false);
				channelH.setVisible(true);
				channelS.setVisible(true);
				channelV.setVisible(true);
				int[] hsl = RGBtoHSL(rgb);
				channelH.setValue(hsl[0]);
				channelS.setValue(hsl[1]);
				channelV.setValue(hsl[2]);
				if (!fromUI)
					hslButton.setSelected(true);
				break;
			case Intensity:
				channelI.setVisible(true);
				buttonPanel.setVisible(false);
				channelR.setVisible(false);
				channelG.setVisible(false);
				channelB.setVisible(false);
				channelH.setVisible(false);
				channelS.setVisible(false);
				channelV.setVisible(false);
				int intensity = RGBtoIntensity(rgb);
				channelI.setValue(intensity);
				break;
			default:
				throw new RuntimeException("Unknown color model.");
		}

		selectedColorModel = mdl;
		rgbaPanel.revalidate();
	}

	private static int RGBtoIntensity(int[] rgb)
	{
		return Math.round(0.33f * rgb[0] + 0.33f * rgb[1] + 0.33f * rgb[2]);
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
}
