package game.message.editor;

import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import org.apache.commons.io.FilenameUtils;

import app.SwingUtils;
import assets.AssetHandle;
import assets.AssetManager;
import game.message.Message;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.FilteredListPanel;

public class AssetListTab extends JPanel
{
	private final FilteredListPanel<MessageAsset> filteredList;
	private final ArrayList<MessageAsset> assets;

	public AssetListTab(MessageEditor editor, MessageListTab listPanel)
	{
		assets = new ArrayList<>();

		filteredList = new FilteredListPanel<>(new AssetCellRenderer()) {
			@Override
			public String getFilterableText(MessageAsset group)
			{
				if (group != null) {
					return FilenameUtils.getBaseName(group.asset.getName());
				}
				else {
					return "";
				}
			}

			@Override
			public void handleSelection(MessageAsset asset)
			{
				listPanel.setAsset(asset);
			}
		};

		JButton reloadButton = new JButton("Reload Resources");
		reloadButton.addActionListener((e) -> fullReload());

		JButton saveAllButton = new JButton("Save Changes");
		saveAllButton.addActionListener((e) -> saveChanges());

		setLayout(new MigLayout("fill, ins 0"));
		add(filteredList, "grow, pushy, span, wrap");
		add(reloadButton, "sg but, growx, pushx, h 32!, split 2");
		add(saveAllButton, "sg but, growx, pushx");
	}

	public void saveChanges()
	{
		/*
		Stack<StringTreeNode> nodes = new Stack<>();
		nodes.push(startNode);
		while(!nodes.isEmpty())
		{
			StringTreeNode node = nodes.pop();
			FileMetadata nodeData = node.getUserObject();
		
			if(nodeData.getType() == FileType.Resource && nodeData.modified)
			{
				StringResource res = nodeData.getResource();
				if(!editor.resourcesToSave.contains(res))
					editor.resourcesToSave.add(res);
			}
		
			for(int i = 0; i < node.getChildCount(); i++)
				nodes.push(node.getChildAt(i));
		}
		*/
		//TODO
	}

	public void fullReload()
	{
		assets.clear();

		try {
			for (AssetHandle ah : AssetManager.getMessages()) {
				Logger.log("Reading messages from: " + ah.getName());
				assets.add(new MessageAsset(ah));
			}
		}
		catch (IOException e) {
			Logger.logError(e.getMessage());
		}

		filteredList.setContent(assets);
	}

	public boolean hasMessage(String name)
	{
		if (name == null)
			return false;

		for (MessageAsset asset : assets) {
			for (Message msg : asset.messages) {
				if (name.equals(msg.name))
					return true;
			}
		}

		return false;
	}

	public static class AssetCellRenderer extends JPanel implements ListCellRenderer<MessageAsset>
	{
		private final JLabel nameLabel;

		public AssetCellRenderer()
		{
			nameLabel = new JLabel("");

			setLayout(new MigLayout("ins 0, fillx"));
			add(nameLabel, "gapleft 8");

			setOpaque(true);
			setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends MessageAsset> list,
			MessageAsset group,
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

			if (group != null) {
				String filename = FilenameUtils.getBaseName(group.asset.getName());
				nameLabel.setForeground(null);

				if (group.hasError) {
					nameLabel.setText("! " + filename);
				}
				else if (group.hasModified) {
					nameLabel.setText("* " + filename);
				}
				else {
					nameLabel.setText(filename);
				}
			}
			else {
				nameLabel.setText("ERROR");
				nameLabel.setForeground(SwingUtils.getRedTextColor());
			}

			return this;
		}
	}
}
