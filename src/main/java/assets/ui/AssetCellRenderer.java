package assets.ui;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import org.apache.commons.io.FilenameUtils;

import app.SwingUtils;
import assets.AssetHandle;
import net.miginfocom.swing.MigLayout;

public class AssetCellRenderer extends JPanel implements ListCellRenderer<AssetHandle>
{
	private final JLabel nameLabel;

	public AssetCellRenderer()
	{
		nameLabel = new JLabel("");

		setLayout(new MigLayout("ins 0", "16[grow]16"));
		add(nameLabel);

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends AssetHandle> list,
		AssetHandle asset,
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

		if (asset != null) {
			String assetDirName = asset.assetDir.getName();
			String mapFileName = FilenameUtils.getBaseName(asset.assetPath);
			nameLabel.setText(assetDirName + " / " + mapFileName);
			nameLabel.setForeground(null);
		}
		else {
			nameLabel.setText("ERROR");
			nameLabel.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}
}
