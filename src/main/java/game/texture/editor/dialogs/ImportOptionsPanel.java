package game.texture.editor.dialogs;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import app.SwingUtils;
import game.texture.TileFormat;
import net.miginfocom.swing.MigLayout;

public class ImportOptionsPanel extends JPanel
{
	private static final TileFormat DEFAULT_FORMAT = TileFormat.CI_4;

	private final JComboBox<TileFormat> fmtComboBox;

	public ImportOptionsPanel()
	{
		fmtComboBox = new JComboBox<>(TileFormat.values());
		fmtComboBox.removeItem(TileFormat.YUV_16);
		fmtComboBox.setSelectedItem(DEFAULT_FORMAT);
		SwingUtils.setFontSize(fmtComboBox, 14);

		setLayout(new MigLayout("fill, wrap"));
		add(SwingUtils.getLabel("Image Format", 14), "gapbottom 4");
		add(fmtComboBox, "growx");
	}

	public TileFormat getFormat()
	{
		return (TileFormat) fmtComboBox.getSelectedItem();
	}
}
