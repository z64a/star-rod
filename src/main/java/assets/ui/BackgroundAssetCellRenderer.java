package assets.ui;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import org.apache.commons.io.FilenameUtils;

import net.miginfocom.swing.MigLayout;
import util.ui.ImagePanel;

public class BackgroundAssetCellRenderer extends JPanel implements ListCellRenderer<BackgroundAsset>
{
	private final ImagePanel previewPanel;
	private final JLabel nameLabel;

	public BackgroundAssetCellRenderer()
	{
		previewPanel = new ImagePanel();
		nameLabel = new JLabel("");

		setLayout(new MigLayout("ins 0", "16[]16[grow]16"));
		add(previewPanel, "grow, h 64!, w 95!"); // 296 x 200
		add(nameLabel);

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends BackgroundAsset> list,
		BackgroundAsset bg,
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
			previewPanel.setImage(bg.thumbnail);
		}
		else {
			nameLabel.setText("none");
			previewPanel.setImage(null);
		}

		return this;
	}
}
