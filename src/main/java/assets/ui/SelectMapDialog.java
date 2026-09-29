package assets.ui;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
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
		SelectMapDialog.showPrompt(null);
		Environment.exit();
	}

	public enum SelectMapResult
	{
		OPEN,
		NEW,
		DUPLICATE,
		CANCEL
	}

	public static File showPrompt(Component parent)
	{
		try {
			SelectMapDialog chooser = new SelectMapDialog(AssetManager.getMapSources());
			SwingUtils.showModalDialog(chooser, parent, "Choose Map");

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
					newMapFile = promptNewMap(parent);
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
		String newMapName = Map.deriveName(newMapFile);
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

	public static File promptNewMap(Component parent)
	{
		// prompt for a name
		File newMapFile = requestNewMapFile();
		if (newMapFile == null)
			return null;

		// create the new map
		String newMapName = Map.deriveName(newMapFile);
		Map newMap = new Map(newMapName);

		// prompt for a description
		String newMapDesc = SwingUtils.getInputDialog()
			.setTitle("New Map Description")
			.setMessage("Provide a map description")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.prompt();

		newMap.desc = (newMapDesc != null) ? newMapDesc : "";

		// prompt for textures
		File texFile = SelectTexDialog.showPrompt(parent, newMap.getExpectedTexFilename());
		newMap.texName = FilenameUtils.getBaseName(texFile.getName());

		// prompt for background
		File bgFile = SelectBackgroundDialog.showPrompt(parent, "");
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

	/** Asks for a new map's name, and in papermario-dx the area it belongs to, and returns where to save it. */
	public static File requestNewMapFile()
	{
		File file = Map.hasAreaDirs() ? promptAreaAndName() : promptName();
		if (file == null || !file.exists())
			return file;

		int choice = SwingUtils.getConfirmDialog()
			.setTitle("Map Already Exists")
			.setMessage(Map.deriveName(file) + " already exists. Overwrite it?")
			.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
			.choose();

		return (choice == JOptionPane.YES_OPTION) ? file : null;
	}

	private static File promptName()
	{
		String name = SwingUtils.getInputDialog()
			.setTitle("New Map Name")
			.setMessage("Provide a new map name")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.prompt();

		if (name == null)
			return null;

		name = sanitizeName(name);
		if (name.isBlank())
			return null;

		return AssetManager.getSaveMapFile(name);
	}

	/**
	 * Asks for a map's area and name together. A map whose code folder already exists keeps that folder's area,
	 * and a stage, named like kzn_bt05, has no area to choose.
	 */
	private static File promptAreaAndName()
	{
		String[] areas = Directories.PROJ_SRC_WORLD.file("area").list((dir, n) -> new File(dir, n).isDirectory());
		Arrays.sort(areas);

		JComboBox<String> areaBox = new JComboBox<>(areas);
		areaBox.setEditable(true);
		areaBox.setSelectedItem("");
		JTextField nameField = new JTextField(32);
		JLabel pathLabel = new JLabel(" ");

		Runnable update = () -> {
			String name = sanitizeName(nameField.getText());
			boolean isStage = name.contains("_bt");
			File existing = name.isEmpty() ? null : Directories.PROJ_SRC.file(Map.getSourcePath(name));
			boolean fixedArea = isStage || (existing != null && existing.isDirectory());
			areaBox.setEnabled(!fixedArea);

			// show the area the map is fixed to; a listener can't edit the text it's notified about, so this waits
			if (fixedArea) {
				String area = isStage ? "" : existing.getParentFile().getName();
				if (!area.equals(areaBox.getEditor().getItem()))
					SwingUtilities.invokeLater(() -> areaBox.getEditor().setItem(area));
			}

			File file = getNewMapFile(name, (String) areaBox.getEditor().getItem());
			pathLabel.setText(file == null ? " " : relativeToProject(file));
		};
		nameField.getDocument().addDocumentListener(onChange(update));
		((JTextField) areaBox.getEditor().getEditorComponent()).getDocument().addDocumentListener(onChange(update));
		areaBox.addActionListener(e -> update.run());

		JPanel panel = new JPanel(new MigLayout("fillx, ins 0", "[][grow, fill]"));
		panel.add(new JLabel("Area:"));
		panel.add(areaBox, "wrap");
		panel.add(new JLabel("Name:"));
		panel.add(nameField, "wrap");
		panel.add(new JLabel("Saves to:"), "span, gaptop 8, wrap");
		panel.add(pathLabel, "span");

		int choice = SwingUtils.getConfirmDialog()
			.setTitle("New Map")
			.setMessage(panel)
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
			.choose();

		if (choice != JOptionPane.OK_OPTION)
			return null;

		return getNewMapFile(sanitizeName(nameField.getText()), (String) areaBox.getEditor().getItem());
	}

	/** Where a new map is saved: beside its code if that exists, else in the chosen area. Null if either is missing. */
	private static File getNewMapFile(String name, String area)
	{
		if (name.isBlank())
			return null;

		if (name.contains("_bt") || Directories.PROJ_SRC.file(Map.getSourcePath(name)).isDirectory())
			return AssetManager.getSaveMapFile(name);

		area = area == null ? "" : area.trim();
		if (area.isBlank())
			return null;

		String path = "world/area/" + area + "/" + name + "/" + Directories.MAP_SOURCE_NAME + Directories.EXT_MAP;
		return new AssetHandle(AssetManager.getTopLevelAssetDir(), path);
	}

	private static String relativeToProject(File file)
	{
		return Environment.getProjectDirectory().toPath().toAbsolutePath().normalize()
			.relativize(file.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
	}

	private static String sanitizeName(String name)
	{
		name = name.trim().toLowerCase();
		if (name.endsWith(Directories.EXT_MAP))
			name = name.substring(0, name.length() - Directories.EXT_MAP.length());
		return name;
	}

	private static DocumentListener onChange(Runnable action)
	{
		return new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				action.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				action.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				action.run();
			}
		};
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
