package game.map.scripts;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.Map;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.ui.LabelWithTip;
import game.map.editor.ui.SimpleEditableJTree;
import game.map.editor.ui.SimpleEditableJTree.DragAndDropMode;
import game.map.editor.ui.SwingGUI;
import game.map.scripts.generators.Entrance;
import game.map.scripts.generators.Entrance.EntranceType;
import game.map.scripts.generators.EntranceInfoPanel;
import game.map.scripts.generators.Exit;
import game.map.scripts.generators.Exit.ExitType;
import game.map.scripts.generators.ExitInfoPanel;
import game.map.scripts.generators.Generator;
import game.map.scripts.generators.Generator.GeneratorType;
import game.map.scripts.generators.foliage.Foliage;
import game.map.scripts.generators.foliage.Foliage.FoliageDataCategory;
import game.map.scripts.generators.foliage.Foliage.FoliageType;
import game.map.scripts.generators.foliage.FoliageData;
import game.map.scripts.generators.foliage.FoliageInfoPanel;
import game.map.tree.CategoryTreeModel;
import game.map.tree.CategoryTreeModel.CategoryTreeCellRenderer;
import game.map.tree.CategoryTreeModel.CategoryTreeNode;
import net.miginfocom.swing.MigLayout;

public class GeneratorsPanel extends JPanel implements IShutdownListener
{
	private Map map;
	private SimpleEditableJTree generatorsTree;
	private Container infoContainer;

	private CategoryTreeNode<GeneratorType, Generator> popupNode;

	private JPopupMenu createEntryPopup;
	private JPopupMenu createExitPopup;
	private JPopupMenu createTreePopup;
	private JPopupMenu createBushPopup;
	private JPopupMenu objectPopup;

	// singleton
	private static GeneratorsPanel instance = null;

	public static GeneratorsPanel instance()
	{
		if (instance == null) {
			instance = new GeneratorsPanel();
			MapEditor.instance().registerOnShutdown(instance);
		}
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
	}

	@SuppressWarnings("unchecked")
	private GeneratorsPanel()
	{
		generatorsTree = new SimpleEditableJTree(new CategoryTreeModel<GeneratorType, Generator>("Default"));
		generatorsTree.setRowHeight(20);
		generatorsTree.setCellRenderer(new CategoryTreeCellRenderer());

		generatorsTree.setBorder(BorderFactory.createCompoundBorder(
			generatorsTree.getBorder(),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));

		generatorsTree.installDragAndDrop(DragAndDropMode.SAME_LEVEL, () -> {}, () -> {});

		generatorsTree.addPopupListener((evt) -> {
			popupNode = (CategoryTreeNode<GeneratorType, Generator>) evt.getNode();
			if (popupNode.isCategory()) {
				switch (popupNode.getCategory()) {
					case Entrance:
						evt.show(createEntryPopup);
						break;
					case Exit:
						evt.show(createExitPopup);
						break;
					case Tree:
						evt.show(createTreePopup);
						break;
					case Bush:
						evt.show(createBushPopup);
						break;
					default:
						throw new UnsupportedOperationException("Unknown category: " + popupNode.getCategory().getName());
				}

			}
			else if (popupNode.isObject())
				evt.show(objectPopup);
		});

		generatorsTree.getSelectionModel().addTreeSelectionListener(e -> {
			CategoryTreeNode<GeneratorType, Generator> node = (CategoryTreeNode<GeneratorType, Generator>) generatorsTree
				.getLastSelectedPathComponent();
			setSelectedNode(node);
		});

		generatorsTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "DeleteCommands");
		generatorsTree.getActionMap().put("DeleteCommands", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				CategoryTreeNode<FoliageDataCategory, FoliageData> selectedNode = (CategoryTreeNode<FoliageDataCategory, FoliageData>) generatorsTree
					.getLastSelectedPathComponent();

				if (selectedNode != null && selectedNode.getUserObject() instanceof Generator) {
					Generator toRemove = (Generator) selectedNode.getUserObject();
					map.scripts.removeGenerator("Delete " + toRemove.type.getName(), generatorsTree, selectedNode);
				}
			}
		});

		createEntryPopup = buildEntryPopupMenu(this);
		createExitPopup = buildExitPopupMenu(this);
		createTreePopup = buildTreePopupMenu(this);
		createBushPopup = buildBushPopupMenu(this);
		objectPopup = buildObjectPopupMenu(this);

		infoContainer = new Container();
		infoContainer.setLayout(new MigLayout("fill, wrap, ins 0"));
		infoContainer.add(ExitInfoPanel.instance());

		LabelWithTip titleLabel = new LabelWithTip("Script Generators", 12,
			"Right click on items in the tree to edit them via pop-up menus.");

		JScrollPane scrollPane = new JScrollPane(generatorsTree);

		setLayout(new MigLayout("fill, wrap, hidemode 3, ins n n 0 n"));

		//TODO
		JLabel lblUnderCosntruction = new JLabel("Not available with decomp yet!", SwingConstants.CENTER);
		SwingUtils.setFontSize(lblUnderCosntruction, 16);
		add(lblUnderCosntruction, "gaptop 32, growx");
		add(infoContainer, "grow, pushy, gaptop 8");

		/*
		add(titleLabel, "gaptop 8, gapbottom 4");
		add(scrollPane, "growx, h 40%!");
		add(infoContainer, "grow, pushy, gaptop 8");
		*/
	}

	private static final Dimension POPUP_OPTION_SIZE = new Dimension(150, 24);

	private static JPopupMenu buildEntryPopupMenu(GeneratorsPanel panel)
	{
		JMenuItem item;

		JPopupMenu menu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(menu);

		for (EntranceType type : EntranceType.values()) {
			item = new JMenuItem("Add " + type.toString());
			item.setPreferredSize(POPUP_OPTION_SIZE);
			item.addActionListener(e -> {
				panel.map.scripts.addGenerator("Add " + type.toString() + " Entrance", panel.generatorsTree, new Entrance(type));
			});
			menu.add(item);
		}

		return menu;
	}

	private static JPopupMenu buildExitPopupMenu(GeneratorsPanel panel)
	{
		JMenuItem item;

		JPopupMenu menu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(menu);

		for (ExitType type : ExitType.values()) {
			item = new JMenuItem("Add " + type.toString());
			item.setPreferredSize(POPUP_OPTION_SIZE);
			item.addActionListener(e -> {
				panel.map.scripts.addGenerator("Add " + type.toString() + " Exit", panel.generatorsTree, new Exit(type));
			});
			menu.add(item);
		}

		return menu;
	}

	private static JPopupMenu buildTreePopupMenu(GeneratorsPanel panel)
	{
		JMenuItem item;

		JPopupMenu menu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(menu);

		item = new JMenuItem("Add Tree");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			panel.map.scripts.addGenerator("Add Tree", panel.generatorsTree, new Foliage(FoliageType.Tree));
		});
		menu.add(item);

		return menu;
	}

	private static JPopupMenu buildBushPopupMenu(GeneratorsPanel panel)
	{
		JMenuItem item;

		JPopupMenu menu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(menu);

		item = new JMenuItem("Add Bush");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			panel.map.scripts.addGenerator("Add Bush", panel.generatorsTree, new Foliage(FoliageType.Bush));
		});
		menu.add(item);

		return menu;
	}

	private static JPopupMenu buildObjectPopupMenu(GeneratorsPanel panel)
	{
		JMenuItem item;

		JPopupMenu menu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(menu);

		item = new JMenuItem("Duplicate");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			if (panel.popupNode != null) {
				Generator original = (Generator) panel.popupNode.getUserObject();
				panel.map.scripts.addGenerator("Duplicate " + original.type.getName(), panel.generatorsTree, original.deepCopy());
			}
		});
		menu.add(item);

		item = new JMenuItem("Delete");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			if (panel.popupNode != null) {
				Generator toRemove = (Generator) panel.popupNode.getUserObject();
				panel.map.scripts.removeGenerator("Delete " + toRemove.type.getName(), panel.generatorsTree, panel.popupNode);
			}
		});
		menu.add(item);

		return menu;
	}

	public void setMap(Map m)
	{
		map = m;
		generatorsTree.clearSelection();
		generatorsTree.setModel(m.scripts.generatorsTreeModel);
		setSelectedNode(null);
	}

	public void repaintTree()
	{
		generatorsTree.repaint();
	}

	private void setSelectedNode(CategoryTreeNode<GeneratorType, Generator> node)
	{
		if (node != null && node.isCategory())
			node = null;

		updateInfoPanel((node == null) ? null : node.getUserObject());
	}

	private void updateInfoPanel(Object obj)
	{
		infoContainer.removeAll();

		if (obj instanceof Entrance) {
			infoContainer.add(EntranceInfoPanel.instance(), "grow");
			EntranceInfoPanel.instance().setEntrance((Entrance) obj);
		}

		if (obj instanceof Exit) {
			infoContainer.add(ExitInfoPanel.instance(), "grow");
			ExitInfoPanel.instance().setExit((Exit) obj);
		}

		if (obj instanceof Foliage) {
			infoContainer.add(FoliageInfoPanel.instance(), "grow");
			FoliageInfoPanel.instance().setSelected((Foliage) obj);
		}

		infoContainer.repaint();
		infoContainer.revalidate();
	}
}
