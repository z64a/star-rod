package game.sprite.editor;

import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.LEFT;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import game.sprite.SpriteRaster;
import net.miginfocom.swing.MigLayout;

public class RasterCellRenderer extends JPanel implements ListCellRenderer<SpriteRaster>
{
	private JLabel iconLabel;
	private JLabel nameLabel;

	public RasterCellRenderer()
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
		JList<? extends SpriteRaster> list,
		SpriteRaster value,
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
			nameLabel.setText("none");
		}
		else if (value.front.asset == null) {
			iconLabel.setIcon(null);
			nameLabel.setText(value.toString());
		}
		else {
			iconLabel.setIcon(value.front.asset.tiny);
			nameLabel.setText(value.toString());
		}

		return this;
	}
}
