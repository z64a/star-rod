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

import org.apache.commons.io.FilenameUtils;

import app.Environment;
import app.SwingUtils;
import assets.AssetHandle;
import assets.AssetManager;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.DialogResult;
import util.ui.FilteredListModel;

public class SelectTexDialog extends JDialog
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		SelectTexDialog.showPrompt();
		Environment.exit();
	}

	public static File showPrompt()
	{
		return showPrompt("");
	}

	public static File showPrompt(String initialSelection)
	{
		try {
			SelectTexDialog chooser = new SelectTexDialog(AssetManager.getTextureArchives(), initialSelection);
			SwingUtils.showModalDialog(chooser, "Choose Textures");
			if (chooser.result == DialogResult.ACCEPT)
				return chooser.getSelectedFile();
		}
		catch (IOException e) {
			Logger.logError("IOException during SelectTexDialog");
			Logger.logError(e.getMessage());
		}
		return null;
	}

	private final FilteredListModel<TexturesAsset> filteredListModel;
	private final JTextField filterTextField;

	private DialogResult result = DialogResult.NONE;
	private TexturesAsset selectedObject;

	private SelectTexDialog(Collection<AssetHandle> assets, String initialSelection)
	{
		super(null, java.awt.Dialog.ModalityType.TOOLKIT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		DefaultListModel<TexturesAsset> listModel = new DefaultListModel<>();
		for (AssetHandle ah : assets) {
			listModel.addElement(new TexturesAsset(ah));
		}

		JList<TexturesAsset> list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		list.setCellRenderer(new TexArchiveAssetCellRenderer());

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

		if (listModel.size() > 0) {
			if (initialSelection != null && !initialSelection.isBlank()) {
				for (int i = 0; i < listModel.getSize(); i++) {
					TexturesAsset tex = listModel.getElementAt(i);
					if (initialSelection.equals(FilenameUtils.getBaseName(tex.assetPath))) {
						list.setSelectedIndex(i);
						break;
					}
				}
			}
			else {
				list.setSelectedIndex(0);
			}
		}
		else {
			setValue(null);
		}
	}

	private void updateListFilter()
	{
		filteredListModel.setFilter(element -> {
			TexturesAsset bg = (TexturesAsset) element;
			String bgName = bg.assetPath.toUpperCase();
			String filterText = filterTextField.getText().toUpperCase();

			return bgName.contains(filterText);
		});
	}

	private void setValue(TexturesAsset object)
	{ selectedObject = object; }

	private File getSelectedFile()
	{ return selectedObject; }
}
