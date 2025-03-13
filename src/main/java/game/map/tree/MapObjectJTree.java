package game.map.tree;

import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import game.map.MapObject;
import game.map.editor.EditorShortcut;
import game.map.editor.MapEditor;
import common.commands.AbstractCommand;
import game.map.editor.ui.GuiCommand;
import game.map.editor.ui.MapObjectPanel;
import game.map.editor.ui.MapObjectTreeCellRenderer;
import game.map.editor.ui.SwingGUI;
import util.BasicNode;
import util.Logger;
import util.identity.IdentityHashSet;

public abstract class MapObjectJTree<T extends MapObject> extends JTree implements ActionListener
{
	// required to bind button events to gui command callbacks
	protected final MapEditor editor;
	private final MapObjectPanel objectPanel;

	protected static enum TreeCommand
	{
		DND_MOVE_SELECTION,
		POPUP_MOVE_SELECTION,
		POPUP_SELECT_CHILDREN,
		POPUP_NEW_GROUP,
		POPUP_NEW_PRIMITIVE,
		POPUP_NEW_MARKER,
		POPUP_IMPORT_HERE,
		POPUP_PASTE_HERE
	}

	private HashMap<String, TreeCommand> treeCommandMap;
	private JPopupMenu popupMenu;

	public MapObjectNode<T> popupSource = null;

	public MapObjectNode<T> dropDestination = null;
	public int dropChildIndex = 0;

	/**
	 * The JTree implementation does not include a mechanism for TreeSelectionListeners
	 * to distinguish additive vs replacement selection changes. This presents a problem
	 * when synchronizing the MapObject selection.
	 *
	 * This hack provides a channel for obtaining this information. The additiveSelection
	 * flag is properly set before fireValueChanged() is called, so TreeSelectionListeners
	 * can assume it holds a meaningful value.
	 */
	public boolean additiveSelection = false;

	public MapObjectJTree(MapEditor editor, SwingGUI gui, final MapObjectPanel panel)
	{
		this.editor = editor;
		this.objectPanel = panel;

		setCellRenderer(new MapObjectTreeCellRenderer());

		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setSelectionModel(new DefaultTreeSelectionModel() {
			// control + click selection uses this, calls addSelectionPaths
			@Override
			public void addSelectionPath(TreePath path)
			{
				additiveSelection = true;
				super.addSelectionPath(path);
			}

			@Override
			public void addSelectionPaths(TreePath[] paths)
			{
				additiveSelection = true;
				super.addSelectionPaths(paths);
			}

			// replacement selection click uses this, which then calls setSelectionPaths
			@Override
			public void setSelectionPath(TreePath path)
			{
				additiveSelection = false;
				super.setSelectionPath(path);
			}

			// shift + arrow selection uses this
			@Override
			public void setSelectionPaths(TreePath[] paths)
			{
				additiveSelection = (paths.length > 1); // be wary of calls from setSelectionPath
				super.setSelectionPaths(paths);
			}
		});

		getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

		setLargeModel(true);
		setShowsRootHandles(true);

		treeCommandMap = new HashMap<>();
		popupMenu = new JPopupMenu();
		gui.registerPopupMenu(popupMenu);
		createPopupMenu(popupMenu);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!isEnabled())
					return;

				TreePath path = getPathForLocation(e.getX(), e.getY());
				Rectangle pathBounds = getUI().getPathBounds(MapObjectJTree.this, path);
				boolean inBounds = (pathBounds != null && pathBounds.contains(e.getX(), e.getY()));

				if (SwingUtilities.isRightMouseButton(e)) {
					if (inBounds) {
						@SuppressWarnings("unchecked")
						MapObjectNode<T> node = (MapObjectNode<T>) path.getLastPathComponent();
						T obj = node.getUserObject();

						if (obj.allowsPopup()) {
							popupMenu.show(e.getComponent(), e.getX(), e.getY());
							popupSource = node;
						}
					}
				}
				else {
					// clear all selected in this tree (but not in other trees)
					//			if(!inBounds)
					//				clearSelection();
				}
			}
		});

		setDragEnabled(true);
		setDropMode(DropMode.ON_OR_INSERT);
		setTransferHandler(new MapObjectTreeTransferHandler());

		getInputMap().put(KeyStroke.getKeyStroke("control A"), "SelectAll");
		getActionMap().put("SelectAll", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				//	boolean selectableRoot = (objectType == MapObjectType.MODEL);
				setSelectionInterval(0, getRowCount());
			}
		});

		/*
		getInputMap().put(KeyStroke.getKeyStroke("control O"), "SelectObjects");
		getActionMap().put("SelectAllWithGroups", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				editor.enqueueKeyEvent(EditorShortcut.SELECT_ALL);
			}
		});
		 */

		getInputMap().put(KeyStroke.getKeyStroke("control C"), "CopySelected");
		getActionMap().put("CopySelected", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				@SuppressWarnings("unchecked")
				MapObjectNode<T> lastSelected = (MapObjectNode<T>) getLastSelectedPathComponent();

				if (lastSelected == null)
					return;

				editor.enqueueKeyEvent(EditorShortcut.COPY_OBJECTS);
			}
		});

		getInputMap().put(KeyStroke.getKeyStroke("control V"), "PasteSelected");
		getActionMap().put("PasteSelected", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				@SuppressWarnings("unchecked")
				MapObjectNode<T> lastSelected = (MapObjectNode<T>) getLastSelectedPathComponent();

				if (lastSelected == null)
					return;

				editor.enqueueKeyEvent(EditorShortcut.PASTE_OBJECTS);
			}
		});

		getInputMap().put(KeyStroke.getKeyStroke("control D"), "DuplicateSelected");
		getActionMap().put("DuplicateSelected", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				@SuppressWarnings("unchecked")
				MapObjectNode<T> lastSelected = (MapObjectNode<T>) getLastSelectedPathComponent();

				if (lastSelected == null)
					return;

				editor.enqueueKeyEvent(EditorShortcut.DUPLICATE_SELECTED);
			}
		});

		getInputMap().put(KeyStroke.getKeyStroke("control G"), "SelectChildren");
		getActionMap().put("SelectChildren", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				@SuppressWarnings("unchecked")
				MapObjectNode<T> lastSelected = (MapObjectNode<T>) getLastSelectedPathComponent();

				if (lastSelected == null || lastSelected.isLeaf())
					return;

				if (lastSelected.getChildCount() > 0) {
					clearSelection();
					selectChildren(lastSelected, true);
				}
			}
		});

		getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "DeleteSelection");
		getActionMap().put("DeleteSelection", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				editor.enqueueKeyEvent(EditorShortcut.DELETE_SELECTED);
			}
		});
	}

	public void setModel(MapObjectTreeModel<T> model)
	{
		super.setModel(model);
		model.setTree(this);
	}

	protected abstract JPopupMenu createPopupMenu(JPopupMenu popupMenu);

	protected void handleTreeCommand(TreeCommand cmd)
	{
		switch (cmd) {
			case POPUP_SELECT_CHILDREN:
				clearSelection();
				selectChildren(popupSource, true);
				break;

			case POPUP_NEW_PRIMITIVE:
				editor.gui.prompt_CreatePrimitiveObject(popupSource);
				break;

			case POPUP_NEW_GROUP:
				editor.doNextFrame(() -> {
					editor.action_CreateGroup(popupSource);
				});
				break;

			case POPUP_IMPORT_HERE:
				editor.gui.prompt_ImportObjects(popupSource);
				break;

			case POPUP_PASTE_HERE:
				editor.doNextFrame(() -> {
					editor.action_PasteObjectsTo(popupSource);
				});

			default:
		}
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		handleTreeCommand(treeCommandMap.get(e.getActionCommand()));
	}

	protected void addButtonCommand(AbstractButton button, TreeCommand cmd)
	{
		button.setActionCommand(cmd.name());
		treeCommandMap.put(button.getActionCommand(), cmd);
		button.addActionListener(this);
	}

	protected void addGuiCommand(AbstractButton button, GuiCommand cmd)
	{
		button.setActionCommand(cmd.name());
		SwingGUI.instance().addButtonCommand(button, cmd);
	}

	protected void selectChildren(MapObjectNode<T> parent, boolean deselectParent)
	{
		Stack<MapObjectNode<T>> children = new Stack<>();

		if (deselectParent)
			removeSelectionPath(new TreePath(parent.getPath()));

		for (int i = 0; i < parent.getChildCount(); i++)
			children.push(parent.getChildAt(i));

		while (!children.isEmpty()) {
			parent = children.pop();

			addSelectionPath(new TreePath(parent.getPath()));

			for (int i = 0; i < parent.getChildCount(); i++)
				children.push(parent.getChildAt(i));
		}
	}

	private class MapObjectTreeTransferHandler extends TransferHandler
	{
		private DataFlavor nodesFlavor;
		private DataFlavor[] flavors = new DataFlavor[1];

		public MapObjectTreeTransferHandler()
		{
			try {
				String mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + MapObjectNode[].class.getName() + "\"";
				nodesFlavor = new DataFlavor(mimeType);
				flavors[0] = nodesFlavor;
			}
			catch (ClassNotFoundException e) {
				Logger.logWarning("ClassNotFound: " + e.getMessage());
			}
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			if (!support.isDrop())
				return false;

			support.setShowDropLocation(true);

			if (!support.isDataFlavorSupported(nodesFlavor))
				return false;

			JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();

			@SuppressWarnings("unchecked")
			MapObjectNode<T> target = (MapObjectNode<T>) dl.getPath().getLastPathComponent();

			// determine where we're dropping -- that's all we need the DnD code for
			if (!target.getAllowsChildren()) {
				if (target.getParent() != null) {
					MapObjectNode<T> parent = target.parentNode;
					dropDestination = target.parentNode;

					int childIndex = 0;
					for (; childIndex < target.parentNode.getChildCount(); childIndex++) {
						if (parent.getChildAt(childIndex).equals(target)) {
							childIndex++;
							break;
						}
					}

					dropChildIndex = childIndex;
				}
				else
					return false;
			}
			else {
				dropDestination = target;
				dropChildIndex = dl.getChildIndex();
			}

			return true;
		}

		@Override
		protected Transferable createTransferable(JComponent c)
		{
			JTree tree = (JTree) c;
			TreePath[] paths = tree.getSelectionPaths();
			if (paths != null) {
				String[] names = { "star", "rod" };
				return new DummyTransferable(names);
			}
			return null;
		}

		@Override
		public int getSourceActions(JComponent c)
		{
			return COPY_OR_MOVE;
		}

		@Override
		public boolean importData(TransferHandler.TransferSupport support)
		{
			if (!canImport(support))
				return false;

			handleTreeCommand(TreeCommand.DND_MOVE_SELECTION);
			return true;
		}

		// we don't actually transfer anything
		private class DummyTransferable implements Transferable
		{
			public String[] objectNames;

			public DummyTransferable(String[] names)
			{
				objectNames = names;
			}

			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
			{
				if (!isDataFlavorSupported(flavor))
					throw new UnsupportedFlavorException(flavor);

				return objectNames;
			}

			@Override
			public DataFlavor[] getTransferDataFlavors()
			{
				return flavors;
			}

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor)
			{
				return nodesFlavor.equals(flavor);
			}
		}
	}

	protected static boolean printTreeDebug = false;

	public class MoveNodes extends AbstractCommand
	{
		private final MapObjectTreeModel<T> treeModel;
		private final List<MapObjectNode<T>> selectedNodes;
		private final List<MapObjectNode<T>> moveList;

		private final BasicNode<MapObjectNode<T>> oldTreeTemplate;
		private final BasicNode<MapObjectNode<T>> newTreeTemplate;

		private HashMap<MapObjectNode<T>, Boolean> wasExtended;

		public MoveNodes(
			final MapObjectTreeModel<T> treeModel,
			final List<MapObjectNode<T>> selectedNodes,
			final MapObjectNode<T> newParentNode,
			int insertionIndex)
		{
			super("Move Selected Objects");
			this.treeModel = treeModel;
			this.selectedNodes = selectedNodes;

			// selection order not important, keep them in tree index order
			Collections.sort(selectedNodes);

			assert (newParentNode.getAllowsChildren());

			if (insertionIndex == -1)
				insertionIndex = newParentNode.getChildCount();

			if (printTreeDebug) {
				System.out.println("NEW PARENT: " + newParentNode);
				System.out.println("INSERTION INDEX: " + insertionIndex);
			}

			moveList = new LinkedList<>();
			int insertionIndexOffset = 0;
			for (MapObjectNode<T> node : selectedNodes) {
				// reject moving to oneself
				if (node == newParentNode) {
					if (printTreeDebug)
						System.out.println("[S] REJECTED " + node);
					continue;
				}

				// reject moving in with your children
				if (newParentNode.isNodeAncestor(node)) {
					if (printTreeDebug)
						System.out.println("[I] REJECTED " + node);
					continue;
				}

				moveList.add(node);

				if (newParentNode == node.getParent()) {
					int childIndex = treeModel.getIndexOfChild(node.getParent(), node);
					if (childIndex < insertionIndex)
						insertionIndexOffset++;
				}
			}

			insertionIndex -= insertionIndexOffset;
			if (insertionIndex < 0)
				insertionIndex = 0;

			if (printTreeDebug)
				System.out.println("INSERTION INDEX: " + insertionIndex);

			// create a template from the existing tree without skipping any nodes
			IdentityHashSet<MapObjectNode<T>> nodesToSkip = new IdentityHashSet<>();
			oldTreeTemplate = copyTree(treeModel.getRoot(), nodesToSkip);

			// create a template from the existing tree, skipping the moved nodes
			for (MapObjectNode<T> jnode : moveList)
				nodesToSkip.add(jnode);
			newTreeTemplate = copyTree(treeModel.getRoot(), nodesToSkip);

			// add the moved nodes to their new location in template tree
			BasicNode<MapObjectNode<T>> copyParent = find(newTreeTemplate, newParentNode);
			for (MapObjectNode<T> jnode : moveList) {
				BasicNode<MapObjectNode<T>> bnode = new BasicNode<>(jnode);
				copyParent.addChild(bnode, insertionIndex++);
			}

			saveExtendedState();

			if (printTreeDebug) {
				System.out.println(" ================== OLD TREE ================== ");
				System.out.println();
				oldTreeTemplate.print();
				System.out.println();

				System.out.println(" ================== NEW TREE ================== ");
				System.out.println();
				newTreeTemplate.print();
				System.out.println();
			}
		}

		private void saveExtendedState()
		{
			wasExtended = new HashMap<>();
			MapObjectJTree<T> jtree = treeModel.getTree();
			Stack<MapObjectNode<T>> nodes = new Stack<>();

			nodes.push(treeModel.getRoot());
			while (!nodes.isEmpty()) {
				MapObjectNode<T> node = nodes.pop();
				TreePath nodePath = new TreePath(node.getPath());

				wasExtended.put(node, jtree.isExpanded(nodePath));

				for (int i = 0; i < node.getChildCount(); i++)
					nodes.push(node.getChildAt(i));
			}
		}

		private void restoreExtendedState()
		{
			MapObjectJTree<T> jtree = treeModel.getTree();
			Stack<MapObjectNode<T>> nodes = new Stack<>();

			nodes.push(treeModel.getRoot());
			while (!nodes.isEmpty()) {
				MapObjectNode<T> node = nodes.pop();
				TreePath nodePath = new TreePath(node.getPath());

				if (!node.isLeaf())
					jtree.setExpandedState(nodePath, wasExtended.get(node));

				for (int i = 0; i < node.getChildCount(); i++)
					nodes.push(node.getChildAt(i));
			}
		}

		private BasicNode<MapObjectNode<T>> copyTree(MapObjectNode<T> jroot, IdentityHashSet<MapObjectNode<T>> nodesToSkip)
		{
			BasicNode<MapObjectNode<T>> broot = new BasicNode<>(jroot);
			buildTreeNode(jroot, broot, nodesToSkip);
			return broot;
		}

		private void buildTreeNode(MapObjectNode<T> jnode, BasicNode<MapObjectNode<T>> bnode, IdentityHashSet<MapObjectNode<T>> nodesToSkip)
		{
			for (int i = 0; i < jnode.getChildCount(); i++) {
				MapObjectNode<T> jchild = jnode.getChildAt(i);

				if (nodesToSkip.contains(jchild))
					continue;

				BasicNode<MapObjectNode<T>> bchild = new BasicNode<>(jchild);
				bnode.addChild(bchild);

				buildTreeNode(jchild, bchild, nodesToSkip);
			}
		}

		private <X> BasicNode<X> find(BasicNode<X> root, X obj)
		{
			Stack<BasicNode<X>> stack = new Stack<>();

			stack.push(root);
			while (!stack.isEmpty()) {
				BasicNode<X> node = stack.pop();
				if (node.data.equals(obj))
					return node;

				for (BasicNode<X> child : node.children)
					stack.push(child);
			}

			return null;
		}

		private void setTree(BasicNode<MapObjectNode<T>> broot)
		{
			treeModel.getRoot().removeAllChildren();
			setNode(broot, treeModel.getRoot());
			treeModel.reload();
		}

		private void setNode(BasicNode<MapObjectNode<T>> bnode, MapObjectNode<T> jnode)
		{
			for (BasicNode<MapObjectNode<T>> bchild : bnode.children) {
				MapObjectNode<T> jchild = bchild.data;
				jnode.add(jchild);
				setNode(bchild, jchild);
			}
		}

		@Override
		public boolean shouldExec()
		{
			return moveList.size() > 0;
		}

		@Override
		public void exec()
		{
			super.exec();

			// must run in the swing thread
			SwingUtilities.invokeLater(() -> {
				objectPanel.disableListener = true;

				setTree(newTreeTemplate);
				restoreExtendedState();

				for (MapObjectNode<T> node : selectedNodes)
					addSelectionPath(new TreePath(node.getPath()));

				treeModel.recalculateIndicies();
				objectPanel.disableListener = false;
			});
		}

		@Override
		public void undo()
		{
			super.undo();

			// must run in the swing thread
			SwingUtilities.invokeLater(() -> {
				objectPanel.disableListener = true;

				setTree(oldTreeTemplate);
				restoreExtendedState();

				for (MapObjectNode<T> node : selectedNodes)
					addSelectionPath(new TreePath(node.getPath()));

				treeModel.recalculateIndicies();
				objectPanel.disableListener = false;
			});
		}
	}
}
