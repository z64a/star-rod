package game.message.editor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Toolkit;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import app.SwingUtils;
import game.message.Message;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.FilteredListPanel;

public class MessageListTab extends JPanel
{
	private final MessageEditor editor;
	private final FilteredListPanel<Message> filteredList;

	private MessageAsset asset;

	public MessageListTab(MessageEditor editor)
	{
		this.editor = editor;

		filteredList = new FilteredListPanel<>(new MessageCellRenderer()) {
			@Override
			public String getFilterableText(Message msg)
			{
				return msg.name.replaceAll("_", " ") + " " + msg.toString();
			}

			@Override
			public void handleSelection(Message msg)
			{
				editor.invokeLater(() -> {
					editor.setString(msg);
				});
			}
		};

		JButton createButton = new JButton("Create");
		createButton.addActionListener((e) -> createMessage());

		JButton deleteButton = new JButton("Delete");
		deleteButton.addActionListener((e) -> deleteMessage());
		deleteButton.setEnabled(false); //TODO

		JButton renameButton = new JButton("Rename");
		renameButton.addActionListener((e) -> renameMessage());

		JButton sectionButton = new JButton("Set Section");
		sectionButton.addActionListener((e) -> setMessageSection());

		setLayout(new MigLayout("fill, ins 0"));
		add(filteredList, "grow, pushy, span, wrap");
		add(renameButton, "sg but, growx, pushx, h 32!, split 2");
		add(sectionButton, "sg but, growx, pushx, wrap");
		add(createButton, "sg but, growx, pushx, h 32!, split 2");
		add(deleteButton, "sg but, growx, pushx");
	}

	public void createMessage()
	{
		if (asset != null) {

			int sectionID = 0;
			if (asset.messages.size() > 0) {
				sectionID = asset.messages.get(asset.messages.size() - 1).section;
			}

			Message msg = new Message(asset);
			msg.section = sectionID;

			msg.name = promptForUniqueName(null);
			if (msg.name != null) {
				asset.messages.add(msg);
				filteredList.setContent(asset.messages);

				filteredList.setSelected(msg);
			}
		}
	}

	public void deleteMessage()
	{
		Message selected = filteredList.getSelected();

		if (asset != null && selected != null) {
			Toolkit.getDefaultToolkit().beep();
			//TODO	asset.messages.remove(selected);
		}
	}

	public void renameMessage()
	{
		Message selected = filteredList.getSelected();
		if (selected == null)
			return;

		String newName = promptForUniqueName(selected.name);

		if (newName != null) {
			selected.name = newName;
			filteredList.repaint();
		}
	}

	public void setMessageSection()
	{
		Message selected = filteredList.getSelected();
		if (selected == null)
			return;

		String newSection = SwingUtils.getInputDialog()
			.setTitle("Choose Message Section")
			.setMessage("Enter the new message section (hex)")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.setDefault(String.format("%02X", selected.section))
			.prompt();

		if (newSection != null) {
			try {
				selected.section = Integer.parseInt(newSection, 16);
				filteredList.repaint();
			}
			catch (NumberFormatException e) {
				Toolkit.getDefaultToolkit().beep();
				Logger.logError(newSection + " is not a valid section!");
			}
		}
	}

	public String promptForUniqueName(String originalName)
	{
		while (true) {
			String name = SwingUtils.getInputDialog()
				.setTitle("Choose Message Name")
				.setMessage("Choose a unique message name")
				.setMessageType(JOptionPane.QUESTION_MESSAGE)
				.setDefault(originalName)
				.prompt();

			if (name == null || name.isBlank()) {
				// empty name provided
				return null;
			}

			if (originalName != null && name.equals(originalName)) {
				// name did not change
				return originalName;
			}

			if (!editor.hasMessage(name)) {
				// name is unique
				return name;
			}

			SwingUtils.getWarningDialog()
				.setTitle("Name Conflict")
				.setMessage(name + " is already in use!")
				.show();
		}
	}

	public void setAsset(MessageAsset asset)
	{
		this.asset = asset;

		if (asset == null) {
			filteredList.setContent(null);
		}
		else {
			filteredList.setContent(asset.messages);
		}
	}

	private static class MessageCellRenderer extends JPanel implements ListCellRenderer<Message>
	{
		private JLabel contentLabel;
		private JLabel idLabel;

		public MessageCellRenderer()
		{
			idLabel = new JLabel();
			contentLabel = new JLabel();

			contentLabel.setForeground(SwingUtils.getGreyTextColor());

			setLayout(new MigLayout("ins 0, fillx"));
			add(idLabel, "gapleft 8, w 15%");
			add(contentLabel, "growx, pushx, gapright push");

			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends Message> list,
			Message str,
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

			setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			if (str != null) {
				Color textColor = null;
				if (!isSelected) {
					if (str.hasError()) {
						textColor = SwingUtils.getRedTextColor();
					}
					else if (str.isModified()) {
						textColor = SwingUtils.getGreenTextColor();
					}
				}

				idLabel.setForeground(textColor);
				contentLabel.setForeground(textColor);

				setToolTipText(str.hasError() ? str.getErrorMessage() : null);

				idLabel.setText(String.format("%02X-%03X", str.section, str.index));

				String name = str.name;

				if (name.length() > 40) {
					int lastSpace = name.lastIndexOf(" ", 32);
					if (lastSpace < 0) {
						name = name.substring(0, 40) + " (...)";
					}
					else {
						name = name.substring(0, lastSpace) + " (...)";
					}
				}

				if (str.hasError()) {
					contentLabel.setText("! " + name);
				}
				else if (str.isModified()) {
					contentLabel.setText("* " + name);
				}
				else {
					contentLabel.setText(name);
				}
			}
			else {
				idLabel.setText("null");
				contentLabel.setText("error!");
			}

			return this;
		}
	}
}
