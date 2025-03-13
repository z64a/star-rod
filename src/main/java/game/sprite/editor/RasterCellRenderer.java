package game.sprite.editor;

import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.LEFT;

import java.awt.Color;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import app.SwingUtils;
import game.sprite.SpriteRaster;
import net.miginfocom.swing.MigLayout;

public class RasterCellRenderer extends JPanel implements ListCellRenderer<SpriteRaster>
{
	private JLabel iconLabel;
	private JLabel nameLabel;

	// for two-sided sprites
	private JPanel iconPairPanel;
	private JLabel frontLabel;
	private JLabel backLabel;

	public RasterCellRenderer()
	{
		iconLabel = new JLabel();

		nameLabel = new JLabel();
		nameLabel.setHorizontalAlignment(LEFT);
		nameLabel.setVerticalAlignment(CENTER);
		nameLabel.setVerticalTextPosition(CENTER);

		iconPairPanel = new JPanel(new MigLayout("fill, ins 0, hidemode 3"));
		iconPairPanel.setOpaque(false);

		frontLabel = new JLabel();
		backLabel = new JLabel();
		iconPairPanel.add(frontLabel, "growx");
		iconPairPanel.add(backLabel, "growx");

		setLayout(new MigLayout("fill, ins 0, hidemode 3"));
		setOpaque(true);

		add(iconPairPanel, "sgy x, w 48!");
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

		Color textColor = null;

		if (value == null) {
			iconLabel.setIcon(null);
			nameLabel.setText("none");
			return this;
		}

		if (value.parentSprite.hasBack) {
			iconPairPanel.setVisible(true);
			iconLabel.setVisible(false);

			if (value.front.asset == null) {
				frontLabel.setIcon(null);
				textColor = SwingUtils.getRedTextColor();
			}
			else {
				frontLabel.setIcon(value.front.preview.getTinyIcon());
			}

			backLabel.setVisible(value.hasIndependentBack);

			if (value.hasIndependentBack) {
				if (value.back.asset == null) {
					backLabel.setIcon(null);
					textColor = SwingUtils.getRedTextColor();
				}
				else {
					backLabel.setIcon(value.back.preview.getTinyIcon());
				}
			}
		}
		else {
			iconPairPanel.setVisible(false);
			iconLabel.setVisible(true);

			if (value.front.asset == null) {
				iconLabel.setIcon(null);
				textColor = SwingUtils.getRedTextColor();
			}
			else {
				iconLabel.setIcon(value.front.preview.getTinyIcon());
			}
		}

		nameLabel.setForeground(textColor);
		nameLabel.setText(value.toString());

		return this;
	}
}
