package game.sprite.editor;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;

import game.sprite.SpriteLoader.Indexable;

public class IndexableComboBoxRenderer extends DefaultListCellRenderer
{
	private final String nullString;

	public IndexableComboBoxRenderer()
	{
		this("None");
	}

	public IndexableComboBoxRenderer(String nullString)
	{
		this.nullString = nullString;
	}

	@Override
	public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
	{
		JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

		lbl.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
		Indexable<?> idx = (Indexable<?>) value;

		if (idx == null) {
			lbl.setText(nullString);
		}
		else {
			String name = (idx.getObject() == null) ? "" : idx.getObject().toString();
			lbl.setText(String.format("%02X | %s", idx.getIndex(), name));
		}

		return lbl;
	}
}
