package game.worldmap;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

public class WorldShortcutsPanel extends JPanel
{
	public WorldShortcutsPanel()
	{
		setLayout(new MigLayout("fill", "[45%]32[45%]"));

		// @formatter:off
		addHeader(this, "Navigation");
		addShortcut(this, "Pan",				"WASD");
		addShortcut(this, "Zoom",				"Mouse Wheel");
		addShortcut(this, "Recenter", 			"Space");

		addHeader(this, "Editing Locations");
		addShortcut(this, "Select", 			"Left Click");
		addShortcut(this, "Move", 				"Left Click and Drag");
		addShortcut(this, "Reassign parent", 	"Right Click and Drag");
		addShortcut(this, "Add path element",	"Ctrl + Left Click");
		addShortcut(this, "Remove path element","Ctrl + Right Click");

		addHeader(this, "Visibility");
		addShortcut(this, "Toggle Lines",		"N");
		addShortcut(this, "Toggle Markers",		"M");
		addShortcut(this, "Toggle Grid",		"G");
		addShortcut(this, "Toggle Background",	"B");
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
