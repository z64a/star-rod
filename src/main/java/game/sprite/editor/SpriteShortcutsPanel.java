package game.sprite.editor;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import net.miginfocom.swing.MigLayout;

public class SpriteShortcutsPanel extends JPanel
{
	public SpriteShortcutsPanel()
	{
		setLayout(new MigLayout("fill"));

		JTabbedPane tabs = new JTabbedPane();

		tabs.addTab("Animations", getAnimationsTab());

		add(tabs, "grow, w 320!");
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

	private JPanel getAnimationsTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		// @formatter:off
		addHeader(tab, "Commands List");
		addShortcut(tab, "Select", "Left Click");
		addShortcut(tab, "Reorder", "Drag Selected");
		addShortcut(tab, "Duplicate", "Ctrl + Left Click");
		addShortcut(tab, "Delete", "Delete (while selected)");
		addShortcut(tab, "Skip Animation To", "Right Click", "Play the current animation until the command is reached, if possible.");

		addHeader(tab, "Playback");
		addShortcut(tab, "Play/Pause",	"Alt + Up Arrow");
		addShortcut(tab, "Stop",		"Alt + Down Arrow");
		addShortcut(tab, "Prev Frame",	"Alt + Left Arrow");
		addShortcut(tab, "Next Frame",	"Alt + Right Arrow");
		addShortcut(tab, "Restart",		"Alt + Home");

		/*
		addHeader(tab, "Navigation");
		addShortcut(tab, "Prev Animation", "Alt + Up Arrow");
		addShortcut(tab, "Next Animation", "Alt + Down Arrow");
		addShortcut(tab, "Prev Component", "Alt + Left Arrow");
		addShortcut(tab, "Next Component", "Alt + Right Arrow");
		*/

		addHeader(tab, "Misc");
		addShortcut(tab, "Rename Component", "Triple Click on Tab");
		// @formatter:on

		return tab;
	}
}
