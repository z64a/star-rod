package util.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;

import app.SwingUtils;

public class LabeledTabbedPane extends JTabbedPane
{
	private static class LabeledTab
	{
		private int id;
		private String name;
		private Component tabContent;
		private boolean enabled;
	}

	private final boolean useCenteredLabels;

	public LabeledTabbedPane(boolean useCenteredLabels)
	{
		this.useCenteredLabels = useCenteredLabels;
	}

	public void addTab(int id, String name, Container contents)
	{
		addTab(id, name, contents, true);
	}

	public void addTab(int id, String name, Container contents, boolean useScrollPane)
	{

	}

	public void setTabEnabled(int id)
	{

	}

	private LabeledTab createTab(JTabbedPane tabs, String name, Container contents, boolean useScrollPane)
	{
		LabeledTab tab = new LabeledTab();

		JLabel lbl = SwingUtils.getLabel(name, 12);
		lbl.setPreferredSize(new Dimension(60, 20));

		if (useScrollPane) {
			JScrollPane scrollPane = new JScrollPane(contents);
			scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			scrollPane.setBorder(null);
			tab.tabContent = scrollPane;
		}
		else
			tab.tabContent = contents;

		tabs.addTab(null, tab.tabContent);
		tabs.setTabComponentAt(tabs.getTabCount() - 1, lbl);

		return tab;
	}
}
