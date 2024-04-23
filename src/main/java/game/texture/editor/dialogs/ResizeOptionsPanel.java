package game.texture.editor.dialogs;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;
import game.texture.editor.dialogs.ResizeOptionsPanel.ResizeOptions.ResizeMethodH;
import game.texture.editor.dialogs.ResizeOptionsPanel.ResizeOptions.ResizeMethodV;
import net.miginfocom.swing.MigLayout;

public class ResizeOptionsPanel extends JPanel
{
	private static final ResizeMethodH DEFAULT_METHOD_H = ResizeMethodH.FixCenter;
	private static final ResizeMethodV DEFAULT_METHOD_V = ResizeMethodV.FixCenter;

	private final JComboBox<ResizeMethodH> hComboBox;
	private final JComboBox<ResizeMethodV> vComboBox;

	private final SpinnerNumberModel widthModel;
	private final SpinnerNumberModel heightModel;

	public ResizeOptionsPanel()
	{
		hComboBox = new JComboBox<>(ResizeMethodH.values());
		hComboBox.setSelectedItem(DEFAULT_METHOD_H);
		SwingUtils.setFontSize(hComboBox, 14);

		vComboBox = new JComboBox<>(ResizeMethodV.values());
		vComboBox.setSelectedItem(DEFAULT_METHOD_V);
		SwingUtils.setFontSize(vComboBox, 14);

		widthModel = new SpinnerNumberModel(32, 0, 512, 2);
		heightModel = new SpinnerNumberModel(32, 0, 512, 1);

		JSpinner widthSpinner = new JSpinner(widthModel);
		JSpinner heightSpinner = new JSpinner(heightModel);

		SwingUtils.setFontSize(widthSpinner, 14);
		SwingUtils.setFontSize(heightSpinner, 14);
		SwingUtils.centerSpinnerText(widthSpinner);
		SwingUtils.centerSpinnerText(heightSpinner);

		JPanel sizePanel = new JPanel(new MigLayout("fill, ins 0"));
		sizePanel.add(SwingUtils.getLabel("W:", 14), "sg dim");
		sizePanel.add(widthSpinner, "sg spinner");
		sizePanel.add(SwingUtils.getLabel("H:", 14), "sg dim, gapleft 16");
		sizePanel.add(heightSpinner, "sg spinner");

		setLayout(new MigLayout("fill, wrap"));

		add(SwingUtils.getLabel("New Image Size", 14), "gapbottom 4");
		add(sizePanel, "center");

		add(SwingUtils.getLabel("Horizontal Resize Method", 14), "gapbottom 4");
		add(hComboBox, "growx, gapbottom 8");

		add(SwingUtils.getLabel("Vertical Resize Method", 14), "gapbottom 4");
		add(vComboBox, "growx, gapbottom 8");
	}

	public ResizeOptions getOptions()
	{
		return new ResizeOptions(
			(ResizeMethodH) hComboBox.getSelectedItem(),
			(ResizeMethodV) vComboBox.getSelectedItem(),
			widthModel.getNumber().intValue(),
			heightModel.getNumber().intValue());
	}

	public static class ResizeOptions
	{
		public final ResizeMethodH methodH;
		public final ResizeMethodV methodV;
		public final int width;
		public final int height;

		private ResizeOptions(ResizeMethodH methodH, ResizeMethodV methodV, int width, int height)
		{
			this.methodH = methodH;
			this.methodV = methodV;
			this.width = width;
			this.height = height;
		}

		public static enum ResizeMethodH
		{
			FixCenter("Fixed Center"),
			FixLeft("Fixed Left Side"),
			FixRight("Fixed Right Side");

			private final String name;

			private ResizeMethodH(String name)
			{
				this.name = name;
			}

			@Override
			public String toString()
			{
				return name;
			}
		}

		public static enum ResizeMethodV
		{
			// @formatter:off
			FixCenter	("Fixed Center"),
			FixBottom	("Fixed Bottom"),
			FixTop		("Fixed Top");
			// @formatter:on

			private final String name;

			private ResizeMethodV(String name)
			{
				this.name = name;
			}

			@Override
			public String toString()
			{
				return name;
			}
		}
	}
}
