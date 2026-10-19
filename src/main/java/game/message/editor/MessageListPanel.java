package game.message.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;

import app.SwingUtils;
import game.message.Message;
import net.miginfocom.swing.MigLayout;

/** Browses effective messages; filename filters match any definition in their stack. */
final class MessageListPanel extends JPanel
{
	private final MessageEditor editor;
	private final DefaultListModel<Message> model = new DefaultListModel<>();
	private final JList<Message> list = new JList<>(model);
	private final JTextField search = new JTextField();
	private final JPanel chips = new JPanel(new MigLayout("fillx, ins 0, gap 4, wrap 1", "[grow,fill]"));
	private final Set<String> filenames = new LinkedHashSet<>();
	private final JLabel count = new JLabel();
	private final JCheckBox showPreview = new JCheckBox("Show preview", true);
	private final JButton create = new JButton("Create");
	private final JButton duplicate = new JButton("Duplicate");
	private final JButton remove = new JButton("Delete");
	private final JButton addFilter = new JButton("Add file filter…");
	private boolean refreshing;

	MessageListPanel(MessageEditor editor)
	{
		this.editor = editor;
		setLayout(new MigLayout("fill, ins 12, wrap 1, hidemode 3", "[grow,fill]"));
		add(SwingUtils.getLabel("Messages", 14), "gapbottom 4");
		search.putClientProperty("JTextField.placeholderText", "Search name or text");
		search.setMargin(SwingUtils.TEXTBOX_INSETS);
		SwingUtils.addBorderPadding(search);
		SwingUtils.addChangeListener(search, event -> refresh(getSelected(), false));
		add(search);
		addFilter.addActionListener(event -> {
			String filename = MessageFileDialog.choose(editor, "Add File Filter", null, false);
			if (filename != null) {
				filenames.add(filename);
				refreshChips();
				refresh(getSelected(), false);
			}
		});
		SwingUtils.addBorderPadding(addFilter);
		add(addFilter, "alignx left, growx 0, w 50%!, h 32!");
		add(chips, "growx");
		refreshChips();
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		MessageRenderer renderer = new MessageRenderer();
		list.setCellRenderer(renderer);
		// Rows fill the viewport and stay on one line. Supplying both dimensions avoids
		// asking the renderer to measure the entire library whenever a filter changes.
		list.setFixedCellWidth(1);
		updateRowHeight(renderer);
		list.addPropertyChangeListener("font", event -> updateRowHeight(renderer));
		list.addListSelectionListener(event -> {
			if (!refreshing && !event.getValueIsAdjusting()) {
				updateActions();
				editor.selectMessage(getSelected());
			}
		});
		JScrollPane listScroll = new JScrollPane(list);
		listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(listScroll, "grow, push");
		JPanel summary = new JPanel(new MigLayout("ins 0, fillx", "[grow][]"));
		summary.add(count);
		summary.add(showPreview, "alignx right");
		showPreview.addActionListener(event -> list.repaint());
		add(summary);
		create.addActionListener(event -> createMessage(false));
		duplicate.addActionListener(event -> createMessage(true));
		remove.addActionListener(event -> editor.removeMessage(getSelected()));
		JPanel actions = new JPanel(new MigLayout("ins 0, fillx", "[grow,fill][grow,fill][grow,fill]"));
		for (JButton button : List.of(create, duplicate, remove)) {
			SwingUtils.addBorderPadding(button);
			actions.add(button, "sg action, h 32!");
		}
		add(actions);
		updateActions();
	}

	Message getSelected()
	{
		return list.getSelectedValue();
	}

	void refresh(Message preferred, boolean reveal)
	{
		if (refreshing)
			return;
		refreshing = true;
		try {
			MessageLibrary library = editor.getLibrary();
			List<Message> messages = library == null ? List.of() : library.getMessages();
			if (preferred != null && !messages.contains(preferred)) {
				String key = MessageLibrary.key(preferred);
				preferred = messages.stream().filter(message -> MessageLibrary.key(message).equals(key)).findFirst().orElse(null);
			}
			if (reveal && preferred != null && !matches(preferred, search.getText().toLowerCase(Locale.ROOT))) {
				search.setText("");
				filenames.clear();
				refreshChips();
			}
			int oldIndex = list.getSelectedIndex();
			String query = search.getText().toLowerCase(Locale.ROOT);
			List<Message> filtered = messages.stream().filter(message -> matches(message, query)).toList();
			model.clear();
			model.addAll(filtered);
			if (preferred != null)
				list.setSelectedValue(preferred, true);
			if (list.getSelectedIndex() < 0 && !model.isEmpty())
				list.setSelectedIndex(Math.max(0, Math.min(oldIndex, model.size() - 1)));
			count.setText(model.size() + " / " + messages.size() + " messages");
			updateActions();
		}
		finally {
			refreshing = false;
		}
		editor.selectMessage(getSelected());
	}

	private void updateRowHeight(MessageRenderer renderer)
	{
		var insets = renderer.getInsets();
		list.setFixedCellHeight(list.getFontMetrics(list.getFont()).getHeight() + insets.top + insets.bottom);
	}

	private boolean matches(Message message, String query)
	{
		if (!editor.getLibrary().matchesFiles(message, filenames))
			return false;
		return query.isEmpty() || (message.name + " " + message.getFilterableString()).toLowerCase(Locale.ROOT).contains(query);
	}

	private void refreshChips()
	{
		chips.removeAll();
		chips.setVisible(!filenames.isEmpty());
		for (String filename : filenames) {
			JButton chip = new JButton(MessageFileDialog.displayName(filename) + " ×");
			SwingUtils.addBorderPadding(chip);
			chip.setToolTipText("Remove filter. Matches this filename in any layer.");
			chip.addActionListener(event -> {
				filenames.remove(filename);
				refreshChips();
				refresh(getSelected(), false);
			});
			chips.add(chip);
		}
		chips.revalidate();
		chips.repaint();
	}

	void updateActions()
	{
		MessageLibrary library = editor.getLibrary();
		Message selected = getSelected();
		create.setEnabled(library != null);
		addFilter.setEnabled(library != null);
		duplicate.setEnabled(selected != null);
		remove.setEnabled(library != null && library.isModMessage(selected));
		remove.setToolTipText(selected != null && library.hasInheritedDefinition(selected)
			? "Remove the writable override and reveal the inherited message"
			: "Delete the selected message");
		repaint();
	}

	private void createMessage(boolean copying)
	{
		Message original = copying ? editor.getDisplayedMessage() : null;
		String name = editor.promptMessageName(original == null ? "NewMessage" : original.name + "_Copy", null);
		if (name == null)
			return;
		String initial = getSelected() == null ? "messages.msg"
			: editor.getLibrary().getDestination(getSelected()).assetPath.replaceFirst("^msg/", "");
		String filename = MessageFileDialog.choose(editor, copying ? "Duplicate Message Into File" : "Create Message In File", initial, true);
		if (filename == null)
			return;
		try {
			Message created = editor.getLibrary().create(name, filename, original);
			refresh(created, true);
		}
		catch (IllegalArgumentException error) {
			JOptionPane.showMessageDialog(editor.getFrame(), error.getMessage(), "Could Not Create Message", JOptionPane.WARNING_MESSAGE);
		}
	}

	private final class MessageRenderer extends JPanel implements ListCellRenderer<Message>
	{
		private final JLabel nameLabel = new JLabel();
		private final JLabel previewLabel = new JLabel();

		MessageRenderer()
		{
			super(new BorderLayout(8, 0));
			setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
			nameLabel.putClientProperty("html.disable", true);
			previewLabel.putClientProperty("html.disable", true);
			add(nameLabel, BorderLayout.WEST);
			add(previewLabel, BorderLayout.CENTER);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Message> list, Message message, int index, boolean selected, boolean focused)
		{
			setBackground(selected ? list.getSelectionBackground() : list.getBackground());
			Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
			nameLabel.setFont(list.getFont());
			previewLabel.setFont(list.getFont());
			nameLabel.setForeground(message.hasError() ? SwingUtils.getRedTextColor()
				: !selected && message.modified ? SwingUtils.getGreenTextColor() : foreground);
			Color background = getBackground();
			Color previewColor = selected ? foreground : SwingUtils.getGrayTextColor();
			previewLabel.setForeground(new Color((2 * previewColor.getRed() + background.getRed()) / 3,
				(2 * previewColor.getGreen() + background.getGreen()) / 3, (2 * previewColor.getBlue() + background.getBlue()) / 3));
			String name = message.name.isEmpty() ? message.getIDName() : message.name;
			nameLabel.setText((message.hasError() ? "! " : message.modified ? "* " : "") + name);
			String preview = message.toString();
			previewLabel.setText(preview.length() > 120 ? preview.substring(0, 120) + "..." : preview);
			previewLabel.setVisible(showPreview.isSelected());
			setToolTipText(message.hasError() ? message.getErrorMessage() : message.toString());
			return this;
		}
	}
}
