package assets.ui;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import org.apache.commons.io.FilenameUtils;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;
import util.ui.ImagePanel;

public class TexArchiveAssetCellRenderer extends JPanel implements ListCellRenderer<TexturesAsset>
{
	private final ImagePanel previewPanel1;
	private final ImagePanel previewPanel2;
	private final ImagePanel previewPanel3;
	private final JLabel nameLabel;

	public TexArchiveAssetCellRenderer()
	{
		previewPanel1 = new ImagePanel();
		previewPanel2 = new ImagePanel();
		previewPanel3 = new ImagePanel();
		nameLabel = new JLabel("");

		setLayout(new MigLayout("ins 0", "16[]4[]4[]16[grow]16"));
		add(previewPanel1, "grow, h 32!, w 32!");
		add(previewPanel2, "grow, h 32!, w 32!");
		add(previewPanel3, "grow, h 32!, w 32!");
		add(nameLabel);

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends TexturesAsset> list,
		TexturesAsset bg,
		int index,
		boolean isSelected,
		boolean cellHasFocus)
	{
		if (isSelected) {
			setBackground(list.getSelectionBackground());
			setForeground(list.getSelectionForeground());
		}
		else {
			setBackground(list.getBackground());
			setForeground(list.getForeground());
		}

		if (bg != null) {
			String assetDirName = bg.assetDir.getName();
			String mapFileName = FilenameUtils.getBaseName(bg.assetPath);
			nameLabel.setText(assetDirName + " / " + mapFileName);
			nameLabel.setForeground(null);
			previewPanel1.setImage(bg.bimg1);
			previewPanel2.setImage(bg.bimg2);
			previewPanel3.setImage(bg.bimg3);
		}
		else {
			nameLabel.setText("ERROR");
			nameLabel.setForeground(SwingUtils.getRedTextColor());
			previewPanel1.setImage(null);
			previewPanel2.setImage(null);
			previewPanel3.setImage(null);
		}

		return this;
	}
}
