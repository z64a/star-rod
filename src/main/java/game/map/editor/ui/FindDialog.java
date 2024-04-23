package game.map.editor.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import app.Environment;
import app.SwingUtils;
import game.map.Map;
import game.map.MapObject;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.shape.Model;
import game.map.tree.MapObjectTreeModel;
import net.miginfocom.swing.MigLayout;
import util.ui.FilteredListModel;

public class FindDialog extends JDialog
{
	private static final String FRAME_TITLE = "Find Object";

	private enum FindResult
	{
		SELECT,
		CANCEL
	}

	private final JList<MapObject> list;
	private final FilteredListModel<MapObject> filteredListModel;
	private final JTextField filterTextField;

	private final JCheckBox enableModels;
	private final JCheckBox enableColliders;
	private final JCheckBox enableZones;
	private final JCheckBox enableMarkers;

	private FindResult result = FindResult.CANCEL;

	public FindDialog(Map map)
	{
		super(null, java.awt.Dialog.ModalityType.TOOLKIT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		DefaultListModel<MapObject> listModel = new DefaultListModel<>();
		addObjects(listModel, map.modelTree);
		addObjects(listModel, map.colliderTree);
		addObjects(listModel, map.zoneTree);
		addObjects(listModel, map.markerTree);

		list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		list.setCellRenderer(new FindObjectCellRenderer());
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

		enableModels = new JCheckBox("Models");
		enableModels.setSelected(true);
		enableModels.addActionListener((e) -> {
			updateListFilter();
		});

		enableColliders = new JCheckBox("Colliders");
		enableColliders.setSelected(true);
		enableColliders.addActionListener((e) -> {
			updateListFilter();
		});

		enableZones = new JCheckBox("Zones");
		enableZones.setSelected(true);
		enableZones.addActionListener((e) -> {
			updateListFilter();
		});

		enableMarkers = new JCheckBox("Markers");
		enableMarkers.setSelected(true);
		enableMarkers.addActionListener((e) -> {
			updateListFilter();
		});

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScrollPane.setWheelScrollingEnabled(true);

		JButton cancelButton = new JButton("Cancel");
		SwingUtils.addBorderPadding(cancelButton);
		cancelButton.addActionListener((e) -> {
			result = FindResult.CANCEL;
			dispose();
		});

		JButton selectButton = new JButton("Select");
		SwingUtils.addBorderPadding(selectButton);
		selectButton.addActionListener((e) -> {
			result = FindResult.SELECT;
			dispose();
		});

		setLayout(new MigLayout("ins 16, fill, wrap"));
		add(SwingUtils.getLabel("Filter:", 12), "w 40!, split 2");
		add(filterTextField, "growx");

		add(enableModels, "split 2, growx, sg check");
		add(enableColliders, "growx, sg check");
		add(enableZones, "split 2, growx, sg check");
		add(enableMarkers, "growx, sg check");

		add(listScrollPane, "growx, h 400!, push, gapbottom 8");

		add(new JPanel(), "split 3, growx, sg but");
		add(cancelButton, "growx, sg but");
		add(selectButton, "growx, sg but");

		if (listModel.size() > 0)
			list.setSelectedIndex(0);
		else
			list.setSelectedValue(null, true);

		setTitle(FRAME_TITLE);
		setIconImage(Environment.getDefaultIconImage());

		pack();
		setResizable(false);
	}

	private static void addObjects(DefaultListModel<MapObject> listModel, MapObjectTreeModel<? extends MapObject> tree)
	{
		for (MapObject mobj : tree.getList()) {
			if (!mobj.getNode().isRoot())
				listModel.addElement(mobj);
		}
	}

	private void updateListFilter()
	{
		filteredListModel.setFilter(element -> {
			MapObject mobj = (MapObject) element;
			if (mobj instanceof Model && !enableModels.isSelected())
				return false;
			if (mobj instanceof Collider && !enableColliders.isSelected())
				return false;
			if (mobj instanceof Zone && !enableZones.isSelected())
				return false;
			if (mobj instanceof Marker && !enableMarkers.isSelected())
				return false;

			String objName = mobj.getName().toUpperCase();
			String filterText = filterTextField.getText().toUpperCase();

			return objName.contains(filterText);
		});
	}

	public MapObject getResult()
	{
		if (result == FindResult.CANCEL)
			return null;
		else
			return list.getSelectedValue();
	}

}
