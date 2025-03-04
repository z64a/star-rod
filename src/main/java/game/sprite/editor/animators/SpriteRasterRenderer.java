package game.sprite.editor.animators;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import game.sprite.SpriteRaster;

public class SpriteRasterRenderer extends JLabel implements ListCellRenderer<SpriteRaster>
{
	public SpriteRasterRenderer()
	{
		setOpaque(true);
		setHorizontalAlignment(CENTER);
		setVerticalAlignment(CENTER);
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends SpriteRaster> list,
		SpriteRaster img,
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

		setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
		if (img != null) {
			setIcon(img.front.preview.getFullIcon());
			setText(img.toString());
		}
		else {
			setIcon(null);
			setText("None");
		}

		setHorizontalTextPosition(SwingConstants.CENTER);
		setVerticalTextPosition(SwingConstants.BOTTOM);

		return this;
	}
}
