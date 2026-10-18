package game.message.editor;

import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.Locale;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;

/** Chooses a relative message container from the entire stack, never a physical OS path. */
final class MessageFileDialog
{
	private MessageFileDialog()
	{}

	static String displayName(String filename)
	{
		return filename.endsWith(".msg") ? filename.substring(0, filename.length() - 4) : filename;
	}

	static String choose(MessageEditor editor, String title, String initial, boolean allowNew)
	{
		List<String> filenames = editor.getLibrary().getFilenames();
		JTextField search = new JTextField();
		JTextField filename = new JTextField(initial == null ? "" : displayName(initial));
		for (JTextField field : List.of(search, filename)) {
			field.setMargin(SwingUtils.TEXTBOX_INSETS);
			SwingUtils.addBorderPadding(field);
		}
		DefaultListModel<String> model = new DefaultListModel<>();
		JList<String> list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused)
			{
				super.getListCellRendererComponent(owner, value, index, selected, focused);
				setText(displayName((String) value));
				return this;
			}
		});
		Runnable filter = () -> {
			String query = search.getText().toLowerCase(Locale.ROOT);
			model.clear();
			filenames.stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(query)).forEach(model::addElement);
		};
		SwingUtils.addChangeListener(search, event -> filter.run());
		list.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting() && list.getSelectedValue() != null)
				filename.setText(displayName(list.getSelectedValue()));
		});
		filter.run();
		list.setSelectedValue(initial, true);
		filename.setEditable(allowNew);
		JPanel panel = new JPanel(new MigLayout("fill, insets 8, wrap 1", "[grow,fill]"));
		panel.add(new JLabel("Search filenames across all asset layers"));
		panel.add(search);
		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(440, 280));
		panel.add(scroll, "grow, push");
		panel.add(new JLabel(allowNew ? "Choose an existing filename or enter a new one:" : "Selected filename:"));
		panel.add(filename);
		if (allowNew)
			panel.add(new JLabel("Saved in " + editor.getLibrary().getWritableLayerName() + "; subfolders are allowed."));
		editor.incrementDialogsOpen();
		try {
			int answer = JOptionPane.showConfirmDialog(editor.getFrame(), panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			String entered = filename.getText().trim();
			if (answer != JOptionPane.OK_OPTION || entered.isEmpty())
				return null;
			// Keep existing file identities intact, including names containing another .msg suffix.
			return filenames.stream().filter(name -> displayName(name).equals(entered)).findFirst()
				.orElse(entered.endsWith(".msg") ? entered : entered + ".msg");
		}
		finally {
			editor.decrementDialogsOpen();
		}
	}
}
