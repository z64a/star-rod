package game.texture.editor.dialogs;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

public class ShorcutListPanel extends JPanel
{
	public ShorcutListPanel()
	{
		setLayout(new MigLayout("fill", "[50%][50%]"));

		// @formatter:off
		addHeader(this, "Navigation");
		addShortcut(this, "Pan",			"WASD");
		addShortcut(this, "Zoom",			"Mouse Wheel");
		addShortcut(this, "Recenter", 		"Space");

		addHeader(this, "Drawing");
		addShortcut(this, "Draw Pixel",		"Left Click");
		addShortcut(this, "Sample Pixel",	"Right Click");
		addShortcut(this, "Fill Selection",	"F");

		addHeader(this, "Selecting");
		addShortcut(this, "Add Pixel",		"Shift + Left Click");
		addShortcut(this, "Remove Pixel",	"Alt + Left Click");
		addShortcut(this, "Clear Selection", "Ctrl + K");
		addShortcut(this, "Clear Selection", "'Add Pixel' Outside Image");
		addShortcut(this, "Select Color",	 "'Add Pixel' on Palette");
		addShortcut(this, "Selection Flood", "Shift + F");
		addShortcut(this, "Deselection Flood","Alt + F");

		addHeader(this, "Misc");
		addShortcut(this, "Undo",			"Ctrl + Z");
		addShortcut(this, "Redo",			"Ctrl + Y");
		addShortcut(this, "Toggle Grid",	"G");
		addShortcut(this, "Toggle Background", "B");
		// @formatter:on
	}

	private void addHeader(JPanel panel, String text)
	{
		boolean first = panel.getComponentCount() == 0;
		String fmt = first ? "span, wrap" : "span, wrap, gaptop 8";

		JLabel lbl = new JLabel(text);
		lbl.setFont(new Font(lbl.getFont().getFontName(), Font.BOLD, 12));

		panel.add(lbl, fmt);
	}

	private void addShortcut(JPanel panel, String name, String keys)
	{
		addShortcut(panel, name, keys, "");
	}

	private void addShortcut(JPanel panel, String desc, String keys, String tip)
	{
		String lblText = tip.isEmpty() ? desc : desc + "*";
		JLabel lbl = new JLabel(lblText);
		if (!tip.isEmpty())
			lbl.setToolTipText(tip);
		panel.add(lbl);
		panel.add(new JLabel(keys), "wrap");
	}
}
