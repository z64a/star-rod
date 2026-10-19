package app;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import app.Themes.Theme;
import app.config.Options;
import net.miginfocom.swing.MigLayout;

public class ThemesEditor
{
	private static ThemeDialog themeDialog;

	public static void showThemeDialog(Component parent)
	{
		ThemeDesignerDialog.cancelOpenDesigner();
		cancelThemeDialog();

		themeDialog = new ThemeDialog(parent);
		themeDialog.setVisible(true);
	}

	static void cancelThemeDialog()
	{
		if (themeDialog != null && themeDialog.isDisplayable())
			themeDialog.cancel();
	}

	public static void addThemeMenuItem(JMenuBar menuBar, Component parent)
	{
		if (menuBar == null)
			return;

		JMenu editorMenu = null;
		for (int i = 0; i < menuBar.getMenuCount(); i++) {
			JMenu menu = menuBar.getMenu(i);
			if (menu != null && "Editor".equals(menu.getText().trim())) {
				editorMenu = menu;
				break;
			}
		}

		if (editorMenu == null) {
			editorMenu = new JMenu("Editor");
			editorMenu.getPopupMenu().setLightWeightPopupEnabled(false);
			menuBar.add(editorMenu);
		}

		addThemeMenuItem(editorMenu, parent);
	}

	public static void addThemeMenuItem(JMenu editorMenu, Component parent)
	{
		boolean hasChangeTheme = false;
		boolean hasDesignTheme = false;
		for (int i = 0; i < editorMenu.getItemCount(); i++) {
			JMenuItem item = editorMenu.getItem(i);
			if (item != null && "Change Theme".equals(item.getText()))
				hasChangeTheme = true;
			if (item != null && "Design Theme".equals(item.getText()))
				hasDesignTheme = true;
		}

		if (hasChangeTheme && hasDesignTheme)
			return;
		if (!hasChangeTheme && !hasDesignTheme && editorMenu.getItemCount() > 0)
			editorMenu.addSeparator();

		if (!hasChangeTheme) {
			JMenuItem item = new JMenuItem("Change Theme");
			item.addActionListener(e -> showThemeDialog(parent));
			editorMenu.add(item);
		}

		if (!hasDesignTheme) {
			JMenuItem item = new JMenuItem("Design Theme");
			item.addActionListener(e -> ThemeDesignerDialog.showDesigner(parent));
			editorMenu.add(item);
		}
	}

	private static class ThemeDialog extends JDialog
	{
		private final Theme initialTheme;
		private final JList<Theme> themeList;
		private boolean keepTheme;

		private ThemeDialog(Component parent)
		{
			super((parent instanceof Window) ? (Window) parent : parent == null ? null : SwingUtilities.getWindowAncestor(parent), "Change Theme",
				ModalityType.MODELESS);

			initialTheme = Themes.getCurrentTheme();
			DefaultListModel<Theme> themes = new DefaultListModel<>();
			for (Theme theme : Themes.getThemes())
				themes.addElement(theme);

			themeList = new JList<>(themes);
			themeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			themeList.setSelectedValue(initialTheme, true);
			themeList.addListSelectionListener(e -> {
				if (!e.getValueIsAdjusting())
					Themes.setTheme(themeList.getSelectedValue());
			});

			JScrollPane scrollPane = new JScrollPane(themeList);
			scrollPane.setPreferredSize(new Dimension(320, 420));

			JButton cancelButton = new JButton("Cancel");
			cancelButton.addActionListener(e -> cancel());

			JButton okButton = new JButton("OK");
			okButton.addActionListener(e -> accept());

			JPanel buttonPanel = new JPanel(new MigLayout("ins 0", "push[][]"));
			buttonPanel.add(okButton);
			buttonPanel.add(cancelButton);

			JPanel contentPanel = new JPanel(new MigLayout("fill, wrap, ins 12"));
			contentPanel.add(scrollPane, "grow, push");
			contentPanel.add(buttonPanel, "growx");
			setContentPane(contentPanel);

			setIconImage(Environment.getDefaultIconImage());
			setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e)
				{
					cancel();
				}

				@Override
				public void windowClosed(WindowEvent e)
				{
					if (!keepTheme)
						Themes.setTheme(initialTheme);
					if (themeDialog == ThemeDialog.this)
						themeDialog = null;
				}
			});

			getRootPane().setDefaultButton(okButton);
			getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
			getRootPane().getActionMap().put("cancel", new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e)
				{
					cancel();
				}
			});

			pack();
			setLocationRelativeTo(parent);
		}

		private void cancel()
		{
			Themes.setTheme(initialTheme);
			keepTheme = true;
			dispose();
		}

		private void accept()
		{
			if (Themes.getCurrentTheme() != null) {
				Environment.mainConfig.setString(Options.Theme, Themes.getConfigKey(Themes.getCurrentTheme()));
				Environment.mainConfig.saveConfigFile();
			}

			keepTheme = true;
			dispose();
		}
	}

}
