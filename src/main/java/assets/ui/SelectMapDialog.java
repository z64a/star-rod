package assets.ui;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.Environment;
import app.SwingUtils;
import assets.AssetHandle;
import assets.AssetManager;
import game.map.Map;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.FilteredListModel;

public class SelectMapDialog extends JDialog
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		SelectMapDialog.showPrompt();
		Environment.exit();
	}

	public enum SelectMapResult
	{
		OPEN,
		NEW,
		DUPLICATE,
		CANCEL
	}

	public static File showPrompt()
	{
		try {
			SelectMapDialog chooser = new SelectMapDialog(AssetManager.getMapSources());
			SwingUtils.showModalDialog(chooser, "Choose Map");

			MapAsset selectedFile = chooser.list.getSelectedValue();
			if (selectedFile == null)
				return null;

			File newMapFile;

			switch (chooser.result) {
				case DUPLICATE:
					newMapFile = promptCopyMap(selectedFile);
					if (newMapFile == null) {
						chooser.result = SelectMapResult.CANCEL;
					}
					return newMapFile;
				case NEW:
					newMapFile = promptNewMap();
					if (newMapFile == null) {
						chooser.result = SelectMapResult.CANCEL;
					}
					return newMapFile;
				case OPEN:
					return selectedFile;
				case CANCEL:
					break;
			}
		}
		catch (IOException e) {
			Logger.logError("IOException during SelectMapDialog");
			Logger.logError(e.getMessage());
		}
		return null;
	}

	private static File promptCopyMap(File selectedFile)
	{
		// prompt for a name
		File newMapFile = requestNewMapFile();
		if (newMapFile == null)
			return null;

		// create the new map
		String newMapName = FilenameUtils.getBaseName(newMapFile.getName());
		Map newMap = Map.loadMap(selectedFile);

		// prompt for a description
		String newMapDesc = SwingUtils.getInputDialog()
			.setTitle("New Map Description")
			.setMessage("Provide a map description")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.prompt();

		newMap.desc = (newMapDesc != null) ? newMapDesc : "";

		// save the new map
		try {
			newMap.saveMapAs(newMapFile);
		}
		catch (Exception e) {
			Logger.logError("Exception while creating new map: " + newMapName);
			Logger.logError(e.getMessage());
			return null;
		}

		return newMapFile;
	}

	public static File promptNewMap()
	{
		// prompt for a name
		File newMapFile = requestNewMapFile();
		if (newMapFile == null)
			return null;

		// create the new map
		String newMapName = FilenameUtils.getBaseName(newMapFile.getName());
		Map newMap = new Map(newMapName);

		// prompt for a description
		String newMapDesc = SwingUtils.getInputDialog()
			.setTitle("New Map Description")
			.setMessage("Provide a map description")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.prompt();

		newMap.desc = (newMapDesc != null) ? newMapDesc : "";

		// prompt for textures
		File texFile = SelectTexDialog.showPrompt(newMap.getExpectedTexFilename());
		newMap.texName = FilenameUtils.getBaseName(texFile.getName());

		// prompt for background
		File bgFile = SelectBackgroundDialog.showPrompt();
		if (bgFile != null) {
			newMap.bgName = FilenameUtils.getBaseName(bgFile.getName());
			newMap.hasBackground = true;
		}

		// save the new map
		try {
			newMap.saveMapAs(newMapFile);
		}
		catch (Exception e) {
			Logger.logError("Exception while saving new map: " + newMapName);
			Logger.logError(e.getMessage());
			return null;
		}

		return newMapFile;
	}

	public static File requestNewMapFile()
	{
		String name = SwingUtils.getInputDialog()
			.setTitle("New Map Name")
			.setMessage("Provide a new map name")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.prompt();

		if (name == null)
			return null;

		// sanitize name
		name = name.trim().toLowerCase();
		String ext = Directories.EXT_MAP;
		if (name.endsWith(ext)) {
			name = name.substring(0, name.length() - ext.length());
		}

		if (name.isBlank())
			return null;

		File existing = AssetManager.getSaveMapFile(name);
		if (!existing.exists())
			return existing;

		int choice = SwingUtils.getConfirmDialog()
			.setTitle("Map Already Exists")
			.setMessage(name + " already exists. Overwrite it?")
			.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
			.choose();

		return (choice == JOptionPane.YES_OPTION) ? existing : null;
	}

	private final JList<MapAsset> list;
	private final FilteredListModel<MapAsset> filteredListModel;
	private final JTextField filterTextField;
	private final JCheckBox filterWithDesc;

	private SelectMapResult result = SelectMapResult.CANCEL;

	private SelectMapDialog(Collection<AssetHandle> assets)
	{
		super(null, java.awt.Dialog.ModalityType.TOOLKIT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		DefaultListModel<MapAsset> listModel = new DefaultListModel<>();
		for (AssetHandle ah : assets) {
			listModel.addElement(new MapAsset(ah));
		}

		list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		list.setCellRenderer(new MapAssetCellRenderer());
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		filteredListModel = new FilteredListModel<>(listModel);
		list.setModel(filteredListModel);

		filterTextField = new JTextField(20);
		filterTextField.setMargin(SwingUtils.TEXTBOX_INSETS);
		filterTextField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateListFilter();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateListFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateListFilter();
			}
		});
		SwingUtils.addBorderPadding(filterTextField);

		filterWithDesc = new JCheckBox("Name Only");
		filterWithDesc.setSelected(true);
		filterWithDesc.addActionListener((e) -> {
			updateListFilter();
		});

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScrollPane.setWheelScrollingEnabled(true);

		JButton cancelButton = new JButton("Cancel");
		SwingUtils.addBorderPadding(cancelButton);
		cancelButton.addActionListener((e) -> {
			result = SelectMapResult.CANCEL;
			dispose();
		});

		JButton createButon = new JButton("Create");
		SwingUtils.addBorderPadding(createButon);
		createButon.addActionListener((e) -> {
			result = SelectMapResult.NEW;
			dispose();
		});

		JButton duplicateButton = new JButton("Duplicate");
		SwingUtils.addBorderPadding(duplicateButton);
		duplicateButton.addActionListener((e) -> {
			result = SelectMapResult.DUPLICATE;
			dispose();
		});

		JButton selectButton = new JButton("Select");
		SwingUtils.addBorderPadding(selectButton);
		selectButton.addActionListener((e) -> {
			result = SelectMapResult.OPEN;
			dispose();
		});

		setLayout(new MigLayout("ins 16, fill, wrap"));
		add(SwingUtils.getLabel("Filter:", 12), "w 40!, split 3");
		add(filterTextField, "growx");
		add(filterWithDesc, "growx");
		add(listScrollPane, "growx, h 600!, push, gapbottom 8");

		add(cancelButton, "split 4, grow, sg but");
		add(createButon, "sg but");
		add(duplicateButton, "sg but");
		add(selectButton, "sg but");

		if (listModel.size() > 0)
			list.setSelectedIndex(0);
		else
			list.setSelectedValue(null, true);
	}

	private void updateListFilter()
	{
		filteredListModel.setFilter(element -> {
			MapAsset map = (MapAsset) element;
			String mapName = map.assetPath.toUpperCase();
			String mapDesc = map.desc.toUpperCase();
			String filterText = filterTextField.getText().toUpperCase();

			if (filterWithDesc.isSelected()) {
				return mapName.contains(filterText);
			}
			else {
				return mapName.contains(filterText) || mapDesc.contains(filterText);
			}
		});
	}
}
