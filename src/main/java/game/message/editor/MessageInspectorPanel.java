package game.message.editor;

import java.awt.Component;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import app.SwingUtils;
import game.message.Message;
import game.message.editor.MessageLibrary.MessageSource;
import net.miginfocom.swing.MigLayout;

final class MessageInspectorPanel extends JPanel
{
	private final MessageEditor editor;
	private final JTextField name = new JTextField();
	private final JLabel section = new JLabel();
	private final JLabel status = new JLabel();
	private final JLabel error = new JLabel();
	private final DefaultListModel<MessageSource> sources = new DefaultListModel<>();
	private final JList<MessageSource> stack = new JList<>(sources);
	private final JButton rename = new JButton("Rename…");
	private final JButton move = new JButton("Move to file…");
	private final JButton remove = new JButton("Delete");
	private final JButton discard = new JButton("Discard edits");
	private final JButton setSection = new JButton("Set section…");
	private Message message;
	private boolean refreshing;

	MessageInspectorPanel(MessageEditor editor)
	{
		this.editor = editor;
		setLayout(new MigLayout("fill, ins 0, wrap 1, hidemode 3", "[grow,fill]"));
		add(SwingUtils.getLabel("Current Message", 14), "gaptop 12");
		name.setEditable(false);
		name.setMargin(SwingUtils.TEXTBOX_INSETS);
		SwingUtils.addBorderPadding(name);
		for (JButton button : List.of(rename, move, remove, discard, setSection))
			SwingUtils.addBorderPadding(button);
		add(name);
		JPanel sectionRow = new JPanel(new MigLayout("fillx, ins 0", "[grow][]"));
		sectionRow.add(section);
		sectionRow.add(setSection, "h 32!");
		add(sectionRow);
		add(new JLabel("Sources · highest priority first"));
		stack.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		stack.setCellRenderer(new SourceRenderer());
		stack.addListSelectionListener(event -> {
			if (!refreshing && !event.getValueIsAdjusting())
				showSource();
		});
		add(new JScrollPane(stack), "grow, push, h 110:140:");
		add(status);
		error.putClientProperty("html.disable", true);
		add(error, "w 0:pref:");
		JPanel actions = new JPanel(new MigLayout("fillx, ins 0", "[grow,fill][grow,fill]"));
		actions.add(rename, "sg action, h 32!");
		actions.add(move, "sg action, h 32!, wrap");
		actions.add(remove, "sg action, h 32!");
		actions.add(discard, "sg action, h 32!");
		add(actions);
		rename.addActionListener(event -> {
			String newName = editor.promptMessageName(message.name, message);
			if (newName != null)
				perform(() -> editor.getLibrary().rename(message, newName));
		});
		move.addActionListener(event -> {
			String initial = editor.getLibrary().getDestination(message).assetPath.replaceFirst("^msg/", "");
			String filename = MessageFileDialog.choose(editor, "Move Message To File", initial, true);
			if (filename != null)
				perform(() -> editor.getLibrary().move(message, filename));
		});
		remove.addActionListener(event -> editor.removeMessage(message));
		discard.addActionListener(event -> {
			if (editor.confirm("Discard Edits", "Discard unsaved changes to this message?")) {
				perform(() -> {
					editor.getLibrary().discard(message);
					editor.forgetHistory(message);
				});
			}
		});
		setSection.addActionListener(event -> {
			String text = SwingUtils.getInputDialog().setParent(editor.getFrame()).setTitle("Message Section")
				.setMessage("Section number (hex, 00–FF)").setDefault(String.format("%02X", message.section)).prompt();
			if (text != null)
				perform(() -> {
					int value;
					try {
						value = Integer.parseInt(text.trim(), 16);
					}
					catch (NumberFormatException error) {
						throw new IllegalArgumentException("Enter a hex section between 00 and FF.");
					}
					if (value < 0 || value > 0xFF)
						throw new IllegalArgumentException("Enter a hex section between 00 and FF.");
					message.setSection(value);
				});
		});
		refresh(null, false);
	}

	private void perform(Runnable action)
	{
		try {
			action.run();
			editor.refreshMessages(message, true);
		}
		catch (IllegalArgumentException error) {
			JOptionPane.showMessageDialog(editor.getFrame(), error.getMessage(), "Could Not Change Message", JOptionPane.WARNING_MESSAGE);
		}
	}

	void refresh(Message selected, boolean keepSource)
	{
		MessageSource oldSource = keepSource && selected == message ? stack.getSelectedValue() : null;
		message = selected;
		refreshing = true;
		try {
			sources.clear();
			name.setText(message == null ? "" : message.name.isEmpty() ? message.getIDName() : message.name);
			section.setText(message == null ? "No message selected" : String.format("Section %02X", message.section));
			List<MessageSource> definitions = message == null ? List.of() : editor.getLibrary().getSources(message);
			definitions.forEach(sources::addElement);
			int selectedIndex = 0;
			if (oldSource != null) {
				for (int i = 0; i < definitions.size(); i++) {
					MessageSource source = definitions.get(i);
					if (source.asset().equals(oldSource.asset()) && source.layer() == oldSource.layer())
						selectedIndex = i;
				}
			}
			if (!sources.isEmpty())
				stack.setSelectedIndex(selectedIndex);
		}
		finally {
			refreshing = false;
		}
		showSource();
	}

	private void showSource()
	{
		MessageSource selected = stack.getSelectedValue();
		boolean effective = selected != null && stack.getSelectedIndex() == 0;
		boolean mod = effective && editor.getLibrary().isModMessage(message);
		rename.setEnabled(mod);
		move.setEnabled(mod);
		remove.setEnabled(mod);
		setSection.setEnabled(effective);
		discard.setEnabled(effective && message.modified);
		boolean inherited = message != null && editor.getLibrary().hasInheritedDefinition(message);
		remove.setText(inherited ? "Remove override" : "Delete");
		status.setText(selected == null ? "Select a message to inspect its sources."
			: !effective ? "Read-only source preview"
				: selected.draft() ? "Unsaved changes"
					: mod ? "Editing writable definition" : "Edits will be saved in " + editor.getLibrary().getWritableLayerName());
		String errorMessage = selected == null ? "" : selected.message().getErrorMessage();
		error.setText(errorMessage.split("\\R", 2)[0]);
		error.setToolTipText(errorMessage.isEmpty() ? null : errorMessage);
		error.setForeground(SwingUtils.getRedTextColor());
		error.setVisible(!errorMessage.isEmpty());
		editor.displayMessage(selected == null ? null : selected.message(), effective);
	}

	private static final class SourceRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused)
		{
			super.getListCellRendererComponent(list, value, index, selected, focused);
			MessageSource source = (MessageSource) value;
			String state = source.draft() ? " (unsaved)" : source.layer() == 0 ? " (writable)" : "";
			setText((source.message().hasError() ? "! " : index == 0 ? "● " : "")
				+ source.layerName() + state + " · " + MessageFileDialog.displayName(source.filename()));
			if (source.message().hasError())
				setForeground(SwingUtils.getRedTextColor());
			setToolTipText((index == 0 ? "Effective definition: " : "Overridden definition: ") + source.asset().getAbsolutePath());
			setBorder(javax.swing.BorderFactory.createEmptyBorder(7, 6, 7, 6));
			return this;
		}
	}
}
