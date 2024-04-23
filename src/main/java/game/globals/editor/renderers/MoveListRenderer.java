package game.globals.editor.renderers;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import app.SwingUtils;
import game.globals.MoveRecord;
import net.miginfocom.swing.MigLayout;

public class MoveListRenderer extends JPanel implements ListCellRenderer<MoveRecord>
{
	private final JLabel nameLabel;
	private final JLabel idLabel;

	public MoveListRenderer()
	{
		idLabel = new JLabel();
		nameLabel = new JLabel();

		setLayout(new MigLayout("ins 0, fill"));
		add(idLabel, "gapleft 16, w 32!");
		add(nameLabel, "growx, pushx, gapright push");

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends MoveRecord> list,
		MoveRecord move,
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

		if (move != null) {
			idLabel.setText(String.format("%02X", move.getIndex()));
			nameLabel.setText(move.name + (move.getModified() ? " *" : ""));

			idLabel.setForeground(move.getModified() && !isSelected ? SwingUtils.getBlueTextColor() : null);
			nameLabel.setForeground(move.getModified() && !isSelected ? SwingUtils.getBlueTextColor() : null);
		}
		else {
			idLabel.setText("");
			nameLabel.setText("Missing!");

			idLabel.setForeground(SwingUtils.getRedTextColor());
			nameLabel.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}
}
