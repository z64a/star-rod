package game.texture.editor.dialogs;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;
import game.texture.TileFormat;
import net.miginfocom.swing.MigLayout;

public class CreateOptionsPanel extends JPanel
{
	private static final TileFormat DEFAULT_FORMAT = TileFormat.CI_4;

	private final JComboBox<TileFormat> fmtComboBox;
	private final SpinnerNumberModel widthModel;
	private final SpinnerNumberModel heightModel;

	public CreateOptionsPanel()
	{
		fmtComboBox = new JComboBox<>(TileFormat.values());
		fmtComboBox.removeItem(TileFormat.YUV_16);
		fmtComboBox.setSelectedItem(DEFAULT_FORMAT);
		fmtComboBox.setMaximumRowCount(TileFormat.values().length - 1);
		SwingUtils.setFontSize(fmtComboBox, 14);

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
		add(SwingUtils.getLabel("Image Format", 14), "gapbottom 4");
		add(fmtComboBox, "growx, gapbottom 8");
		add(SwingUtils.getLabel("Image Size", 14), "gapbottom 4");
		add(sizePanel, "center");
	}

	public TileFormat getFormat()
	{ return (TileFormat) fmtComboBox.getSelectedItem(); }

	public int getImageWidth()
	{ return widthModel.getNumber().intValue(); }

	public int getImageHeight()
	{ return heightModel.getNumber().intValue(); }
}
