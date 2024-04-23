package game.globals.editor.renderers;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import app.IconResource;
import app.SwingUtils;
import game.globals.ItemRecord;
import game.globals.editor.GlobalsData;
import net.miginfocom.swing.MigLayout;

public class ItemListRenderer extends JPanel implements ListCellRenderer<ItemRecord>
{
	private final GlobalsData globals;
	private final JLabel iconLabel;
	private final JLabel nameLabel;
	private final JLabel idLabel;

	public ItemListRenderer(GlobalsData globals)
	{
		this.globals = globals;
		iconLabel = new JLabel(IconResource.CROSS_16, SwingConstants.CENTER);
		idLabel = new JLabel();
		nameLabel = new JLabel();
		idLabel.setHorizontalAlignment(SwingConstants.CENTER);

		setLayout(new MigLayout("ins 0, fill"));
		add(iconLabel, "w 16!, h 16!");
		add(idLabel, "w 32!");
		add(nameLabel, "growx, pushx, gapright push");

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends ItemRecord> list,
		ItemRecord item,
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

		if (item != null) {
			String previewName = item.iconName;
			Icon preview = globals.getSmallIcon(previewName);
			iconLabel.setIcon((preview != null) ? preview : IconResource.CROSS_16);

			idLabel.setText(String.format("%03X", item.getIndex()));
			nameLabel.setText(item.name + (item.getModified() ? " *" : ""));

			idLabel.setForeground(item.getModified() && !isSelected ? SwingUtils.getBlueTextColor() : null);
			nameLabel.setForeground(item.getModified() && !isSelected ? SwingUtils.getBlueTextColor() : null);
		}
		else {
			iconLabel.setIcon(IconResource.CROSS_16);
			idLabel.setText("");
			nameLabel.setText("Missing!");

			idLabel.setForeground(SwingUtils.getRedTextColor());
			nameLabel.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}
}
