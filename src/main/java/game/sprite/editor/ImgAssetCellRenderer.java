package game.sprite.editor;

import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.LEFT;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import game.sprite.ImgAsset;
import net.miginfocom.swing.MigLayout;

public class ImgAssetCellRenderer extends JPanel implements ListCellRenderer<ImgAsset>
{
	private JLabel iconLabel;
	private JLabel nameLabel;

	public ImgAssetCellRenderer()
	{
		iconLabel = new JLabel();

		nameLabel = new JLabel();
		nameLabel.setHorizontalAlignment(LEFT);
		nameLabel.setVerticalAlignment(CENTER);
		nameLabel.setVerticalTextPosition(CENTER);

		setLayout(new MigLayout("fill, ins 0, hidemode 3"));
		setOpaque(true);

		add(iconLabel, "sgy x, w 20!");
		add(nameLabel, "sgy x, gapleft 8");
		add(new JLabel(), "growx, pushx");
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends ImgAsset> list,
		ImgAsset value,
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

		setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

		if (value == null) {
			iconLabel.setIcon(null);
			nameLabel.setText("NULL");
		}
		else {
			iconLabel.setIcon(value.preview.getTinyIcon());
			nameLabel.setText(value.toString());
		}

		return this;
	}
}
