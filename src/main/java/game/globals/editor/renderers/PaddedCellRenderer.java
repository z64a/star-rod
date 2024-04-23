package game.globals.editor.renderers;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;

public class PaddedCellRenderer<T> extends JPanel implements ListCellRenderer<T>
{
	private final JLabel label;
	private final int maxChars;

	public PaddedCellRenderer()
	{
		this(-1);
	}

	public PaddedCellRenderer(int maxChars)
	{
		label = new JLabel();
		this.maxChars = maxChars;

		setLayout(new MigLayout("ins 0, fill"));
		add(label);

		setOpaque(true);
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends T> list,
		T value,
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

		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 0));
		if (value != null) {
			String s = value.toString();
			if (maxChars > 4 && s.length() > maxChars)
				s = s.substring(0, maxChars - 3) + "...";
			label.setText(s);
			label.setForeground(null);
		}
		else {
			label.setText("Missing!");
			label.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}
}
