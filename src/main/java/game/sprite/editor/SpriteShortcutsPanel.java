package game.sprite.editor;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import net.miginfocom.swing.MigLayout;
import util.ui.HelpIcon;

public class SpriteShortcutsPanel extends JPanel
{
	public SpriteShortcutsPanel()
	{
		setLayout(new MigLayout("fill"));

		JTabbedPane tabs = new JTabbedPane();

		tabs.addTab("Lists", getListsTab());
		tabs.addTab("Camera", getCameraTab());
		tabs.addTab("Playback", getPlaybackTab());
		tabs.addTab("Misc", getMiscTab());

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
		JLabel lbl = new JLabel(desc);

		if (!tip.isEmpty()) {
			HelpIcon help = new HelpIcon(tip);
			panel.add(lbl, "split 2");
			panel.add(help);
		}
		else {
			panel.add(lbl);
		}

		panel.add(new JLabel(keys), "wrap");
	}

	private JPanel getListsTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		// @formatter:off
		addShortcut(tab, "Select",      "Left Click");
		addShortcut(tab, "Rename",      "F2 (or Double Click)");
		addShortcut(tab, "Reorder",     "Click + Drag");
		addShortcut(tab, "Duplicate",   "Ctrl + D");
		addShortcut(tab, "Copy",        "Ctrl + C");
		addShortcut(tab, "Paste",       "Ctrl + V");
		addShortcut(tab, "Delete",      "Delete (while selected)");
		// @formatter:on

		return tab;
	}

	private JPanel getCameraTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		// @formatter:off
		addShortcut(tab, "WASD",        "Pan");
		addShortcut(tab, "Mouse Wheel", "Zoom");
		addShortcut(tab, "Space",       "Reset");
		// @formatter:on

		return tab;
	}

	private JPanel getPlaybackTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		// @formatter:off
		addShortcut(tab, "Play/Pause",	"Alt + Up Arrow");
		addShortcut(tab, "Stop",		"Alt + Down Arrow");
		addShortcut(tab, "Prev Frame",	"Alt + Left Arrow");
		addShortcut(tab, "Next Frame",	"Alt + Right Arrow");
		addShortcut(tab, "Restart",		"Alt + Home");
		// @formatter:on

		return tab;
	}

	private JPanel getMiscTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		// @formatter:off
		addHeader(tab, "Commands List");
		addShortcut(tab, "Skip Animation To", "Right Click", "Play the current animation until the command is reached, if possible.");

		addHeader(tab, "Animations Viewport");
		addShortcut(tab, "Move Component", "Click + Drag");
		// @formatter:on

		return tab;
	}
}
