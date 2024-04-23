package game.map.scripts.generators.foliage;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.ui.BoundObjectPanel;
import game.map.editor.ui.LabelWithTip;
import game.map.editor.ui.ScriptManager;
import game.map.editor.ui.SimpleEditableJTree;
import game.map.editor.ui.SimpleEditableJTree.DragAndDropMode;
import game.map.editor.ui.SwingGUI;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.UISelectionHelper;
import game.map.scripts.generators.Generator.GeneratorType;
import game.map.scripts.generators.foliage.Foliage.FoliageDataCategory;
import game.map.scripts.generators.foliage.FoliageDrop.EditFoliageDrop;
import game.map.tree.CategoryTreeModel;
import game.map.tree.CategoryTreeModel.CategoryTreeCellRenderer;
import game.map.tree.CategoryTreeModel.CategoryTreeNode;
import net.miginfocom.swing.MigLayout;
import util.ui.StringField;

public class FoliageInfoPanel extends JPanel implements IShutdownListener
{
	private static final Dimension POPUP_OPTION_SIZE = new Dimension(150, 24);

	private static FoliageInfoPanel instance = null;

	public static FoliageInfoPanel instance()
	{
		if (instance == null) {
			instance = new FoliageInfoPanel();
			MapEditor.instance().registerOnShutdown(instance);
		}
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
	}

	private Foliage selected;
	private CategoryTreeNode<FoliageDataCategory, FoliageData> popupNode;

	private boolean ignoreChanges = false;

	private JLabel foliageNameLabel;

	private StringField nameField;

	private BoundObjectPanel colliderNamePanel;
	private BoundObjectPanel bombPosPanel;
	private JCheckBox cbStarTree;
	private JCheckBox cbHasCallback;
	private SimpleEditableJTree foliageDataTree;

	private JPopupMenu categoryPopup;
	private JPopupMenu modelPopup;
	private JPopupMenu vectorPopup;
	private JPopupMenu dropPopup;

	@SuppressWarnings("unchecked")
	private FoliageInfoPanel()
	{
		nameField = new StringField((s) -> {
			if (ignoreChanges || selected == null)
				return;
			MapEditor.execute(selected.overrideName.mutator(s));
		});
		nameField.setHorizontalAlignment(SwingConstants.LEFT);

		colliderNamePanel = new BoundObjectPanel(MapObjectType.COLLIDER, "Collider",
			"Interaction trigger for searching/smashing this foliage.", (s) -> {
				if (ignoreChanges || selected == null)
					return;
				MapEditor.execute(selected.colliderName.mutator(s));
			});

		bombPosPanel = new BoundObjectPanel(MarkerType.Sphere, "Bomb Pos",
			"Trigger marker for bombable location of tree.", (s) -> {
				if (ignoreChanges || selected == null)
					return;
				MapEditor.execute(selected.bombPosName.mutator(s));
			});

		cbStarTree = new JCheckBox(" Generate as star tree (see: hos_03)");
		cbStarTree.addActionListener((e) -> {
			if (ignoreChanges || selected == null)
				return;
			MapEditor.execute(selected.isStarTree.mutator(cbStarTree.isSelected()));
		});

		cbHasCallback = new JCheckBox(" Generate Callback Script");
		cbHasCallback.addActionListener((e) -> {
			if (ignoreChanges || selected == null)
				return;
			MapEditor.execute(selected.hasCallback.mutator(cbHasCallback.isSelected()));
		});

		foliageDataTree = new SimpleEditableJTree(new CategoryTreeModel<FoliageDataCategory, FoliageData>("Default"));
		foliageDataTree.setRowHeight(20);
		foliageDataTree.setCellRenderer(new CategoryTreeCellRenderer());

		foliageDataTree.setBorder(BorderFactory.createCompoundBorder(
			foliageDataTree.getBorder(),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));

		foliageDataTree.installDragAndDrop(DragAndDropMode.SAME_LEVEL, () -> {}, () -> {});

		foliageDataTree.addPopupListener((evt) -> {
			popupNode = (CategoryTreeNode<FoliageDataCategory, FoliageData>) evt.getNode();
			if (popupNode.isCategory()) {
				evt.show(categoryPopup);
			}
			else if (popupNode.isObject()) {
				switch (popupNode.getCategory()) {
					case BushModels:
					case TrunkModels:
					case LeafModels:
						evt.show(modelPopup);
						break;
					case FXPositions:
						evt.show(vectorPopup);
						break;
					case Drops:
						evt.show(dropPopup);
						break;
				}
			}
		});

		foliageDataTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "DeleteCommands");
		foliageDataTree.getActionMap().put("DeleteCommands", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (selected == null)
					return;

				CategoryTreeNode<FoliageDataCategory, FoliageData> selectedNode = (CategoryTreeNode<FoliageDataCategory, FoliageData>) foliageDataTree
					.getLastSelectedPathComponent();
				if (selectedNode == null || !selectedNode.isObject())
					return;

				MapEditor.execute(selected.dataTreeModel.new RemoveObject(
					"Delete Entry", foliageDataTree, selectedNode));
			}
		});

		categoryPopup = buildCategoryPopupMenu(this);
		modelPopup = buildModelPopupMenu(this);
		vectorPopup = buildVectorPopupMenu(this);
		dropPopup = buildDropPopupMenu(this);

		setLayout(new MigLayout("fill, wrap, hidemode 3, ins n n 0 n"));

		foliageNameLabel = SwingUtils.getLabel("", 14);
		add(foliageNameLabel, "gapbottom 8");

		add(new LabelWithTip("Name", "Expected to be unique."), "w 80!, gapleft 8, gapright 8, split 2");
		add(nameField, "growx");

		add(colliderNamePanel, "growx");
		add(bombPosPanel, "growx");

		add(cbStarTree, "gapleft 8, gaptop 4, growx");
		add(cbHasCallback, "gapleft 8, gaptop 4, growx");

		JScrollPane scrollPane = new JScrollPane(foliageDataTree);

		LabelWithTip treeLabel = new LabelWithTip("Foliage Data",
			"Right click on items in the tree to edit them via pop-up menus.");
		add(treeLabel, "gaptop 8, gapbottom 4");

		add(scrollPane, "grow");
	}

	public void updateFields(Foliage foliage)
	{
		if (foliage != null && selected == foliage) {
			ignoreChanges = true;

			foliageNameLabel.setText(foliage.toString());

			nameField.setText(foliage.overrideName.get());
			colliderNamePanel.setText(foliage.colliderName.get());
			bombPosPanel.setText(foliage.bombPosName.get());
			cbStarTree.setSelected(foliage.isStarTree.get());

			bombPosPanel.setVisible(foliage.type == GeneratorType.Tree);
			cbStarTree.setVisible(foliage.type == GeneratorType.Tree);

			cbHasCallback.setSelected(foliage.hasCallback.get());

			foliageDataTree.setModel(foliage.dataTreeModel);
			foliageDataTree.repaint();

			ignoreChanges = false;
		}

		ScriptManager.instance().updateGeneratorTree();
	}

	public void setSelected(Foliage foliage)
	{
		selected = foliage;

		setVisible(foliage != null);
		if (foliage == null)
			return;

		updateFields(foliage);
	}

	private static JPopupMenu buildCategoryPopupMenu(FoliageInfoPanel panel)
	{
		JMenuItem item;

		JPopupMenu menu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(menu);

		item = new JMenuItem("Add New Entry");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			FoliageDataCategory cat = panel.popupNode.getCategory();
			FoliageData newData;
			switch (cat) {
				case BushModels:
				case TrunkModels:
				case LeafModels:
					newData = new FoliageModel(panel.selected, "???");
					break;

				case FXPositions:
					newData = new FoliageVector(panel.selected, "???");
					break;

				case Drops:
					newData = new FoliageDrop(panel.selected);
					break;

				default:
					throw new UnsupportedOperationException("Can't create template for " + cat);
			}

			MapEditor.execute(panel.selected.dataTreeModel.new AddObject(
				"Add Entry to " + cat.toString(),
				panel.foliageDataTree, cat, newData));
		});
		menu.add(item);

		return menu;
	}

	private static JPopupMenu buildModelPopupMenu(FoliageInfoPanel panel)
	{
		JMenuItem item;

		JPopupMenu menu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(menu);

		item = new JMenuItem("Edit");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			tryEditModel((FoliageModel) panel.popupNode.getObject());
		});
		menu.add(item);

		item = new JMenuItem("Use Selected");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			MapObject obj = UISelectionHelper.getLastObject(MapObjectType.MODEL);
			if (obj == null)
				return;
			FoliageModel m = (FoliageModel) panel.popupNode.getObject();
			MapEditor.execute(m.modelName.mutator(obj.getName()));

		});
		menu.add(item);

		item = new JMenuItem("Select Object");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			FoliageModel m = (FoliageModel) panel.popupNode.getObject();
			if (m == null)
				return;
			UISelectionHelper.selectObject(MapObjectType.MODEL, m.modelName.get());
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Duplicate");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			MapEditor.execute(panel.selected.dataTreeModel.new AddObject(
				"Duplicate Model",
				panel.foliageDataTree,
				panel.popupNode.getCategory(),
				(FoliageModel) panel.popupNode.getObject().deepCopy()));
		});
		menu.add(item);

		item = new JMenuItem("Delete");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			MapEditor.execute(panel.selected.dataTreeModel.new RemoveObject(
				"Delete Model",
				panel.foliageDataTree,
				panel.popupNode));
		});
		menu.add(item);

		return menu;
	}

	private static JPopupMenu buildVectorPopupMenu(FoliageInfoPanel panel)
	{
		JMenuItem item;

		JPopupMenu menu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(menu);

		item = new JMenuItem("Edit");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			tryEditVector((FoliageVector) panel.popupNode.getObject());
		});
		menu.add(item);

		item = new JMenuItem("Use Selected");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			MapObject obj = UISelectionHelper.getLastMarker(MarkerType.Position);
			if (obj == null)
				return;
			FoliageVector vec = (FoliageVector) panel.popupNode.getObject();
			MapEditor.execute(vec.modelName.mutator(obj.getName()));
		});
		menu.add(item);

		item = new JMenuItem("Select Object");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			FoliageVector vec = (FoliageVector) panel.popupNode.getObject();
			if (vec == null)
				return;
			UISelectionHelper.selectObject(MapObjectType.MARKER, vec.modelName.get());
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Duplicate");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			MapEditor.execute(panel.selected.dataTreeModel.new AddObject(
				"Duplicate Vector",
				panel.foliageDataTree,
				panel.popupNode.getCategory(),
				(FoliageVector) panel.popupNode.getObject().deepCopy()));
		});
		menu.add(item);

		item = new JMenuItem("Delete");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			MapEditor.execute(panel.selected.dataTreeModel.new RemoveObject(
				"Delete Vector",
				panel.foliageDataTree,
				panel.popupNode));
		});
		menu.add(item);

		return menu;
	}

	private static JPopupMenu buildDropPopupMenu(FoliageInfoPanel panel)
	{
		JMenuItem item;

		JPopupMenu menu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(menu);

		item = new JMenuItem("Edit");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			tryEditDrop((FoliageDrop) panel.popupNode.getObject());
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Duplicate");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			MapEditor.execute(panel.selected.dataTreeModel.new AddObject(
				"Duplicate Drop",
				panel.foliageDataTree,
				panel.popupNode.getCategory(),
				(FoliageDrop) panel.popupNode.getObject().deepCopy()));
		});
		menu.add(item);

		item = new JMenuItem("Delete");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			MapEditor.execute(panel.selected.dataTreeModel.new RemoveObject(
				"Delete Drop",
				panel.foliageDataTree,
				panel.popupNode));
		});
		menu.add(item);

		return menu;
	}

	private static void tryEditModel(FoliageModel folMdl)
	{
		SwingGUI gui = MapEditor.instance().gui;
		FoliageModelEditor editorPanel = new FoliageModelEditor(MapEditor.instance().map, folMdl);

		gui.notify_OpenDialog();
		int userAction = SwingUtils.showFramedConfirmDialog(gui, editorPanel, "Edit Foliage Model",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		gui.notify_CloseDialog();

		if (userAction == JOptionPane.OK_OPTION)
			MapEditor.execute(folMdl.modelName.mutator(editorPanel.getValue()));
	}

	private static void tryEditVector(FoliageVector folVec)
	{
		SwingGUI gui = MapEditor.instance().gui;
		FoliageVectorEditor editorPanel = new FoliageVectorEditor(MapEditor.instance().map, folVec);

		gui.notify_OpenDialog();
		int userAction = SwingUtils.showFramedConfirmDialog(gui, editorPanel, "Edit Foliage Model",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		gui.notify_CloseDialog();

		if (userAction == JOptionPane.OK_OPTION)
			MapEditor.execute(folVec.modelName.mutator(editorPanel.getValue()));
	}

	private static void tryEditDrop(FoliageDrop folDrop)
	{
		SwingGUI gui = MapEditor.instance().gui;
		FoliageDropEditor editorPanel = new FoliageDropEditor(MapEditor.instance().map, folDrop);

		gui.notify_OpenDialog();
		int userAction = SwingUtils.showFramedConfirmDialog(gui, editorPanel, "Edit Foliage Drop",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		gui.notify_CloseDialog();

		if (userAction == JOptionPane.OK_OPTION)
			MapEditor.execute(new EditFoliageDrop(folDrop, editorPanel.getEditedDrop()));
	}
}
