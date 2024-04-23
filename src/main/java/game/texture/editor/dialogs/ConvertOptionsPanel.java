package game.texture.editor.dialogs;

import java.awt.Window;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import app.SwingUtils;
import game.texture.TileFormat;
import game.texture.editor.Dither.DitherMethod;
import game.texture.editor.dialogs.ConvertOptionsPanel.ConvertSettings.IntensityMethod;
import net.miginfocom.swing.MigLayout;

public class ConvertOptionsPanel extends JPanel
{
	private static final TileFormat DEFAULT_FORMAT = TileFormat.CI_4;

	private final JComboBox<TileFormat> fmtComboBox;
	private final JComboBox<DitherMethod> ditherComboBox;
	private final JComboBox<IntensityMethod> intensityComboBox;

	private final JPanel quantPanel;
	private final JPanel intensityPanel;

	public ConvertOptionsPanel()
	{
		fmtComboBox = new JComboBox<>(TileFormat.values());
		fmtComboBox.removeItem(TileFormat.YUV_16);
		fmtComboBox.setMaximumRowCount(TileFormat.values().length - 1);
		SwingUtils.setFontSize(fmtComboBox, 14);

		ditherComboBox = new JComboBox<>(DitherMethod.values());
		ditherComboBox.setSelectedItem(DitherMethod.FloydSteinberg);
		SwingUtils.setFontSize(ditherComboBox, 14);

		quantPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));
		quantPanel.add(SwingUtils.getLabel("Dither Method", 14), "gapbottom 4");
		quantPanel.add(ditherComboBox, "growx, gapbottom 8");

		intensityComboBox = new JComboBox<>(IntensityMethod.values());
		intensityComboBox.setSelectedItem(IntensityMethod.Luminance);
		SwingUtils.setFontSize(intensityComboBox, 14);

		intensityPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));
		intensityPanel.add(SwingUtils.getLabel("Intensity Calculation Method", 14), "gapbottom 4");
		intensityPanel.add(intensityComboBox, "growx, gapbottom 8");

		fmtComboBox.addActionListener((e) -> {
			TileFormat fmt = (TileFormat) fmtComboBox.getSelectedItem();
			quantPanel.setVisible(fmt.type == TileFormat.TYPE_CI);
			intensityPanel.setVisible((fmt.type == TileFormat.TYPE_I) || (fmt.type == TileFormat.TYPE_IA));

			Window w = SwingUtilities.getWindowAncestor(this);
			if (w != null)
				w.pack();
		});

		setLayout(new MigLayout("fill, wrap, hidemode 3"));
		add(SwingUtils.getLabel("Image Format", 14), "gapbottom 4");
		add(fmtComboBox, "growx, gapbottom 8");
		add(intensityPanel, "growx, gapbottom 8");
		add(quantPanel, "growx, gapbottom 8");

		fmtComboBox.setSelectedItem(DEFAULT_FORMAT);
	}

	public ConvertSettings getSettings()
	{
		return new ConvertSettings(
			(TileFormat) fmtComboBox.getSelectedItem(),
			(IntensityMethod) intensityComboBox.getSelectedItem(),
			(DitherMethod) ditherComboBox.getSelectedItem());
	}

	public static class ConvertSettings
	{
		public final TileFormat fmt;
		public final IntensityMethod intensityMethod;
		public final DitherMethod ditherMethod;

		public ConvertSettings(TileFormat fmt, IntensityMethod intensityMethod, DitherMethod ditherMethod)
		{
			this.fmt = fmt;
			this.intensityMethod = intensityMethod;
			this.ditherMethod = ditherMethod;
		}

		public static enum IntensityMethod
		{
			Luminance,
			Balanced,
			Average
		}
	}
}
