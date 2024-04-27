package assets.ui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import app.Environment;
import app.SwingUtils;
import assets.AssetHandle;
import assets.AssetManager;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.DialogResult;
import util.ui.FilteredListModel;

public class SelectBackgroundDialog extends JDialog
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		SelectBackgroundDialog.showPrompt();
		Environment.exit();
	}

	public static File showPrompt()
	{
		try {
			SelectBackgroundDialog chooser = new SelectBackgroundDialog(AssetManager.getBackgrounds());
			SwingUtils.showModalDialog(chooser, "Choose Background");
			if (chooser.result == DialogResult.ACCEPT)
				return chooser.getSelectedFile();
		}
		catch (IOException e) {
			Logger.logError("IOException during SelectBackgroundDialog");
			Logger.logError(e.getMessage());
		}
		return null;
	}

	private final FilteredListModel<BackgroundAsset> filteredListModel;
	private final JTextField filterTextField;

	private DialogResult result = DialogResult.NONE;
	private BackgroundAsset selectedObject;

	private SelectBackgroundDialog(Collection<AssetHandle> assets)
	{
		super(null, java.awt.Dialog.ModalityType.TOOLKIT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		DefaultListModel<BackgroundAsset> listModel = new DefaultListModel<>();
		listModel.addElement(null);
		for (AssetHandle ah : assets) {
			// ignore .alt background
			if (ah.assetPath.endsWith("_bg.png")) {
				listModel.addElement(new BackgroundAsset(ah));
			}
		}

		JList<BackgroundAsset> list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		list.setCellRenderer(new BackgroundAssetCellRenderer());

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;
			setValue(list.getSelectedValue());
		});

		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt)
			{
				if (SwingUtilities.isLeftMouseButton(evt) && evt.getClickCount() == 1) {
					if (list.getSelectedIndex() != -1) {
						int index = list.locationToIndex(evt.getPoint());
						if (index >= 0)
							setValue(list.getModel().getElementAt(index));
					}
				}
			}
		});

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

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScrollPane.setWheelScrollingEnabled(true);

		JButton cancelButton = new JButton("Cancel");
		SwingUtils.addBorderPadding(cancelButton);
		cancelButton.addActionListener((e) -> {
			result = DialogResult.CANCEL;
			dispose();
		});

		JButton selectButton = new JButton("Select");
		SwingUtils.addBorderPadding(selectButton);
		selectButton.addActionListener((e) -> {
			result = DialogResult.ACCEPT;
			dispose();
		});

		setLayout(new MigLayout("ins 16, fill, wrap"));

		add(SwingUtils.getLabel("Filter:", 12), "w 40!, split 2");
		add(filterTextField, "growx");
		add(listScrollPane, "grow, w 320, h 480, push, gapbottom 8");

		add(new JLabel(""), "split 4, sg but");
		add(new JLabel(""), "sg but");
		add(cancelButton, "sg but");
		add(selectButton, "sg but");

		if (listModel.size() > 0)
			list.setSelectedIndex(0);
		else
			setValue(null);
	}

	private void updateListFilter()
	{
		filteredListModel.setFilter(element -> {
			BackgroundAsset bg = (BackgroundAsset) element;
			String bgName = bg.assetPath.toUpperCase();
			String filterText = filterTextField.getText().toUpperCase();

			return bgName.contains(filterText);
		});
	}

	private void setValue(BackgroundAsset object)
	{
		selectedObject = object;
	}

	private File getSelectedFile()
	{
		return selectedObject;
	}
}
