package game.globals.editor.renderers;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import app.IconResource;
import app.SwingUtils;
import game.globals.IconRecord;
import net.miginfocom.swing.MigLayout;

public class IconListRenderer extends JPanel implements ListCellRenderer<IconRecord>
{
	private final JLabel iconLabel;
	private final JLabel nameLabel;

	public IconListRenderer()
	{
		iconLabel = new JLabel(IconResource.CROSS_16, SwingConstants.CENTER);
		nameLabel = new JLabel();

		setLayout(new MigLayout("ins 0, fill"));
		add(iconLabel, "w 32!, h 16!");
		add(nameLabel, "growx, pushx, gapright push");

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends IconRecord> list,
		IconRecord image,
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

		if (image != null && image.smallIcon != null) {
			iconLabel.setIcon(image.smallIcon);
			nameLabel.setText(image.name + (image.getModified() ? " *" : ""));

			iconLabel.setForeground(image.getModified() && !isSelected ? SwingUtils.getBlueTextColor() : null);

			if (image.smallIcon == null)
				nameLabel.setForeground(SwingUtils.getRedTextColor());
			else
				nameLabel.setForeground(image.getModified() && !isSelected ? SwingUtils.getBlueTextColor() : null);
		}
		else {
			iconLabel.setIcon(IconResource.CROSS_16);
			nameLabel.setText("Missing!");

			iconLabel.setForeground(SwingUtils.getRedTextColor());
			nameLabel.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}
}
