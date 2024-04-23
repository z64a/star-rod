package game.map.editor.ui;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import app.SwingUtils;
import game.map.MapObject;
import game.map.MapObject.HitType;
import game.map.MapObject.ShapeType;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.shape.Model;
import net.miginfocom.swing.MigLayout;

public class FindObjectCellRenderer extends JPanel implements ListCellRenderer<MapObject>
{
	private final JLabel descLabel;
	private final JLabel nameLabel;

	public FindObjectCellRenderer()
	{
		descLabel = new JLabel("");
		nameLabel = new JLabel("");

		setLayout(new MigLayout("ins 0", "16[grow, sg even]10[grow, sg even]16"));
		add(nameLabel);
		add(descLabel);

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends MapObject> list,
		MapObject obj,
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

		if (obj != null) {
			String type = obj.getObjectType().toString();
			String desc = "";

			if (obj instanceof Model mdl) {
				if (mdl.modelType.get() != ShapeType.MODEL)
					desc = "Group";
			}
			else if (obj instanceof Collider c) {
				if (c.getType() != HitType.HIT)
					desc = "Group";
			}
			else if (obj instanceof Zone z) {
				if (z.getType() != HitType.HIT)
					desc = "Group";
			}
			else if (obj instanceof Marker m) {
				desc = m.getType().toString();
			}

			nameLabel.setText(obj.getName());
			if (!desc.isEmpty())
				descLabel.setText(type + " / " + desc);
			else
				descLabel.setText(type);

			nameLabel.setForeground(null);
			descLabel.setForeground(null);
		}
		else {
			nameLabel.setText("ERROR");
			descLabel.setText("???");

			nameLabel.setForeground(SwingUtils.getRedTextColor());
			descLabel.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}
}
