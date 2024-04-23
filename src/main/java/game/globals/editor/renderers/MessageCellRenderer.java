package game.globals.editor.renderers;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import app.SwingUtils;
import game.message.Message;
import net.miginfocom.swing.MigLayout;

public class MessageCellRenderer extends JPanel implements ListCellRenderer<Message>
{
	private final JLabel identifierLabel;
	private final JLabel textLabel;
	private final int maxChars;

	public MessageCellRenderer(int maxChars)
	{
		if (maxChars < 16)
			throw new IllegalArgumentException("Expected maxChars > 16");

		identifierLabel = new JLabel("");
		textLabel = new JLabel("");
		this.maxChars = maxChars;

		setLayout(new MigLayout("ins 0, fill"));
		add(identifierLabel, "w 72");
		add(textLabel, "grow");
		add(new JLabel(), "pushx");

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends Message> list,
		Message msg,
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

		if (msg != null) {
			String id = msg.getIdentifier();
			identifierLabel.setText(id);

			String s = msg.toString();

			if (s.length() > (maxChars - 4)) {
				s = s.substring(0, maxChars - 4);
				int lastSpace = s.lastIndexOf(" ");
				if (lastSpace > maxChars - 16)
					s = s.substring(0, lastSpace);
				else
					s = s.substring(0, maxChars - 8);
				s += " ...";
			}
			textLabel.setText(s);

			identifierLabel.setForeground(null);
			textLabel.setForeground(null);
		}
		else {
			identifierLabel.setText("???");
			textLabel.setText("Missing!");

			identifierLabel.setForeground(SwingUtils.getRedTextColor());
			textLabel.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}
}
