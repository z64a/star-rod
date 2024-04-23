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

public class MapAssetCellRenderer extends JPanel implements ListCellRenderer<MapAsset>
{
	private final JLabel nameLabel;
	private final JLabel descLabel;

	public MapAssetCellRenderer()
	{
		nameLabel = new JLabel("");
		descLabel = new JLabel("");

		setLayout(new MigLayout("ins 0", "16[30%]10[grow]16"));
		add(nameLabel);
		add(descLabel);

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends MapAsset> list,
		MapAsset map,
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

		if (map != null) {
			String assetDirName = map.assetDir.getName();
			String mapFileName = FilenameUtils.getBaseName(map.assetPath);
			nameLabel.setText(assetDirName + " / " + mapFileName);

			String s = map.desc.toString();
			int maxChars = 32;
			if (s.length() > (maxChars - 4)) {
				s = s.substring(0, maxChars - 4);
				int lastSpace = s.lastIndexOf(" ");
				if (lastSpace > maxChars - 16)
					s = s.substring(0, lastSpace);
				else
					s = s.substring(0, maxChars - 8);
				s += " ...";
			}
			descLabel.setText(s);

			nameLabel.setForeground(null);
		}
		else {
			nameLabel.setText("ERROR");
			descLabel.setText("");

			nameLabel.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}
}
