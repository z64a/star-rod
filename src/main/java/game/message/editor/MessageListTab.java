package game.message.editor;

import java.awt.Color;
import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import app.SwingUtils;
import assets.AssetHandle;
import assets.AssetManager;
import game.message.Message;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.FilteredListPanel;

public class MessageListTab extends JPanel
{
	private FilteredListPanel<Message> filteredList;

	public MessageListTab(MessageEditor editor)
	{
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

		JButton renameButton = new JButton("Rename");
		renameButton.addActionListener((e) -> renameMessage());

		JButton createButton = new JButton("Create");
		createButton.addActionListener((e) -> createMessage());

		setLayout(new MigLayout("fill, ins 0"));
		add(filteredList, "grow, pushy, span, wrap");
		add(renameButton, "sg but, growx, pushx, h 32!, split 2");
		add(createButton, "sg but, growx, pushx");
	}

	public void renameMessage()
	{
		Message msg = filteredList.getSelected();
		if (msg == null)
			return;

		String newName = promptForUniqueName(msg.name);

		if (newName != null) {
			msg.name = newName;
			filteredList.repaint();
		}
	}

	public void createMessage()
	{

		List<MessageGroup> resources = new ArrayList<>();

		try {
			int sectionID = 0;
			for (AssetHandle ah : AssetManager.getMessages()) {
				Logger.log("Reading strings from: " + ah.getName());
				resources.add(new MessageGroup(ah, sectionID++));
			}
		}
		catch (IOException e) {
			Logger.logError(e.getMessage());
		}

		//TODO filteredList.setContent(resources);
	}

	public String promptForUniqueName(String originalName)
	{
		String name = SwingUtils.showFramedInputDialog(null,
			"Provide a message name",
			"Name New Message",
			JOptionPane.QUESTION_MESSAGE);

		if (name == null || name.isBlank()) {

		}

		return name; //TODO
	}

	public void setStrings(List<Message> messages)
	{
		filteredList.setContent(messages);
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
