package game.map.editor.ui;

import java.awt.Component;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import game.map.editor.MapEditor;
import game.map.editor.commands.AbstractCommand;
import util.BasicNode;
import util.Logger;
import util.identity.IdentityArrayList;
import util.identity.IdentityHashSet;

public class ComplexEditableJTree extends JTree
{
	public static enum DragAndDropMode
	{
		ANYTHING, // anything can be moved anywhere (except the root)
		KEEP_TOP_LEVEL, // top level cant be reordered; nothing can move there
		KEEP_PARENTS, // only move things within same level, no changing parents
		ONLY_LEAVES // only leaves can be reordered, no changing parents
	}

	private DragAndDropMode dndMode;
	private Runnable preCallback;
	private Runnable postCallback;

	public void installDragAndDrop(DragAndDropMode mode, Runnable preCallback, Runnable postCallback)
	{
		this.dndMode = mode;
		this.preCallback = preCallback;
		this.postCallback = postCallback;

		setDragEnabled(true);
		setDropMode(DropMode.ON_OR_INSERT);
		setTransferHandler(new MapObjectTreeTransferHandler());
	}

	public void addPopupListener(Consumer<PopupEvent> popupListener)
	{
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!isEnabled())
					return;

				TreePath path = getPathForLocation(e.getX(), e.getY());
				Rectangle pathBounds = getUI().getPathBounds(ComplexEditableJTree.this, path);
				boolean inBounds = (pathBounds != null) && pathBounds.contains(e.getX(), e.getY());

				if (SwingUtilities.isRightMouseButton(e)) {
					if (inBounds) {
						DefaultMutableTreeNode source = (DefaultMutableTreeNode) path.getLastPathComponent();
						popupListener.accept(new PopupEvent(source, e.getComponent(), e.getX(), e.getY()));
					}
				}
			}
		});
	}

	public static class PopupEvent
	{
		private final DefaultMutableTreeNode source;
		private final Component comp;
		private final int x;
		private final int y;

		private PopupEvent(DefaultMutableTreeNode node, Component comp, int x, int y)
		{
			this.source = node;
			this.comp = comp;
			this.x = x;
			this.y = y;
		}

		public DefaultMutableTreeNode getNode()
		{ return source; }

		public void show(PopupMenu menu)
		{
			menu.show(comp, x, y);
		}
	}

	private final HashMap<DefaultMutableTreeNode, Boolean> saveExtendedState()
	{
		HashMap<DefaultMutableTreeNode, Boolean> extended = new HashMap<>();
		Stack<DefaultMutableTreeNode> nodes = new Stack<>();

		nodes.push((DefaultMutableTreeNode) getModel().getRoot());
		while (!nodes.isEmpty()) {
			DefaultMutableTreeNode node = nodes.pop();
			TreePath nodePath = new TreePath(node.getPath());

			extended.put(node, isExpanded(nodePath));

			for (int i = 0; i < node.getChildCount(); i++)
				nodes.push((DefaultMutableTreeNode) node.getChildAt(i));
		}

		return extended;
	}

	private final void restoreExtendedState(HashMap<DefaultMutableTreeNode, Boolean> extended)
	{
		Stack<DefaultMutableTreeNode> nodes = new Stack<>();

		nodes.push((DefaultMutableTreeNode) getModel().getRoot());
		while (!nodes.isEmpty()) {
			DefaultMutableTreeNode node = nodes.pop();
			TreePath nodePath = new TreePath(node.getPath());

			if (!node.isLeaf())
				setExpandedState(nodePath, extended.get(node));

			for (int i = 0; i < node.getChildCount(); i++)
				nodes.push((DefaultMutableTreeNode) node.getChildAt(i));
		}
	}

	private class MapObjectTreeTransferHandler extends TransferHandler
	{
		private DataFlavor nodesFlavor;
		private DataFlavor[] flavors = new DataFlavor[1];

		private DefaultMutableTreeNode dropDestination = null;
		private int dropChildIndex = 0;

		public MapObjectTreeTransferHandler()
		{
			try {
				String mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + ComplexEditableJTree.class.getName() + "\"";
				nodesFlavor = new DataFlavor(mimeType);
				flavors[0] = nodesFlavor;
			}
			catch (ClassNotFoundException e) {
				Logger.logWarning("ClassNotFound: " + e.getMessage());
			}
		}

		@Override
		public int getSourceActions(JComponent c)
		{
			return MOVE;
		}

		@Override
		protected Transferable createTransferable(JComponent c)
		{
			TreePath[] paths = getSelectionPaths();
			if (paths == null)
				return null;
			else
				return new DummyTransferable(ComplexEditableJTree.this);
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			if (!support.isDrop())
				return false;

			//XXX	support.setShowDropLocation(true);

			if (!support.isDataFlavorSupported(nodesFlavor))
				return false;

			JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
			DefaultMutableTreeNode target = (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();

			// determine where we're dropping -- that's all we use the canImport DnD code for
			if (!target.getAllowsChildren()) {
				if (target.getParent() != null) {
					dropDestination = (DefaultMutableTreeNode) target.getParent();

					int childIndex = 0;
					for (; childIndex < dropDestination.getChildCount(); childIndex++) {
						if (dropDestination.getChildAt(childIndex).equals(target)) {
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
		public boolean importData(TransferHandler.TransferSupport support)
		{
			if (!canImport(support))
				return false;

			List<DefaultMutableTreeNode> selectedNodes = new IdentityArrayList<>();
			for (TreePath path : getSelectionPaths()) {
				selectedNodes.add((DefaultMutableTreeNode) path.getLastPathComponent());
			}

			if (selectedNodes.isEmpty())
				return false;

			MapEditor.execute(new MoveNodes(
				ComplexEditableJTree.this,
				preCallback,
				postCallback,
				selectedNodes,
				dropDestination,
				dropChildIndex));
			return true;
		}

		// we only transfer the identity of the source tree
		private class DummyTransferable implements Transferable
		{
			public ComplexEditableJTree tree;

			public DummyTransferable(ComplexEditableJTree tree)
			{
				this.tree = tree;
			}

			@Override
			public DataFlavor[] getTransferDataFlavors()
			{ return flavors; }

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor)
			{
				return nodesFlavor.equals(flavor);
			}

			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
			{
				if (!isDataFlavorSupported(flavor))
					throw new UnsupportedFlavorException(flavor);

				return tree;
			}
		}
	}

	public class MoveNodes extends AbstractCommand
	{
		private final Runnable preCallback;
		private final Runnable postCallback;

		private final ComplexEditableJTree tree;
		private final List<DefaultMutableTreeNode> selectedNodes;
		private final List<DefaultMutableTreeNode> moveList;

		private final BasicNode<DefaultMutableTreeNode> oldTreeTemplate;
		private final BasicNode<DefaultMutableTreeNode> newTreeTemplate;

		private HashMap<DefaultMutableTreeNode, Boolean> wasExtended;

		public MoveNodes(
			ComplexEditableJTree tree,
			Runnable preCallback,
			Runnable postCallback,
			List<DefaultMutableTreeNode> selectedNodes,
			DefaultMutableTreeNode newParentNode,
			int insertionIndex)
		{
			super("Move Selected");

			this.preCallback = preCallback;
			this.postCallback = postCallback;

			this.tree = tree;
			this.selectedNodes = selectedNodes;

			assert (newParentNode.getAllowsChildren());

			if (insertionIndex == -1)
				insertionIndex = newParentNode.getChildCount();

			moveList = new LinkedList<>();
			int insertionIndexOffset = 0;
			for (DefaultMutableTreeNode node : selectedNodes) {
				// reject moving to oneself
				if (node == newParentNode)
					continue;

				// reject moving in with your children
				if (newParentNode.isNodeAncestor(node))
					continue;

				moveList.add(node);

				if (newParentNode == node.getParent()) {
					int childIndex = getModel().getIndexOfChild(node.getParent(), node);
					if (childIndex < insertionIndex)
						insertionIndexOffset++;
				}
			}

			insertionIndex -= insertionIndexOffset;
			if (insertionIndex < 0)
				insertionIndex = 0;

			DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();

			// create a template from the existing tree without skipping any nodes
			IdentityHashSet<DefaultMutableTreeNode> nodesToSkip = new IdentityHashSet<>();
			oldTreeTemplate = copyTree(root, nodesToSkip);

			// create a template from the existing tree, skipping the moved nodes
			for (DefaultMutableTreeNode jnode : moveList)
				nodesToSkip.add(jnode);
			newTreeTemplate = copyTree(root, nodesToSkip);

			// add the moved nodes to their new location in template tree
			BasicNode<DefaultMutableTreeNode> copyParent = find(newTreeTemplate, newParentNode);
			for (DefaultMutableTreeNode jnode : moveList) {
				BasicNode<DefaultMutableTreeNode> bnode = new BasicNode<>(jnode);
				copyParent.addChild(bnode, insertionIndex++);
			}

			wasExtended = tree.saveExtendedState();
		}

		private BasicNode<DefaultMutableTreeNode> copyTree(DefaultMutableTreeNode jroot, IdentityHashSet<DefaultMutableTreeNode> nodesToSkip)
		{
			BasicNode<DefaultMutableTreeNode> broot = new BasicNode<>(jroot);
			buildTreeNode(jroot, broot, nodesToSkip);
			return broot;
		}

		private void buildTreeNode(DefaultMutableTreeNode jnode, BasicNode<DefaultMutableTreeNode> bnode,
			IdentityHashSet<DefaultMutableTreeNode> nodesToSkip)
		{
			for (int i = 0; i < jnode.getChildCount(); i++) {
				DefaultMutableTreeNode jchild = (DefaultMutableTreeNode) jnode.getChildAt(i);

				if (nodesToSkip.contains(jchild))
					continue;

				BasicNode<DefaultMutableTreeNode> bchild = new BasicNode<>(jchild);
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

		private void setTree(BasicNode<DefaultMutableTreeNode> broot)
		{
			DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
			root.removeAllChildren();
			setNode(broot, root);
			((DefaultTreeModel) getModel()).reload();
		}

		private void setNode(BasicNode<DefaultMutableTreeNode> bnode, DefaultMutableTreeNode jnode)
		{
			for (BasicNode<DefaultMutableTreeNode> bchild : bnode.children) {
				DefaultMutableTreeNode jchild = bchild.data;
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
				preCallback.run();

				setTree(newTreeTemplate);
				tree.restoreExtendedState(wasExtended);

				for (DefaultMutableTreeNode node : selectedNodes)
					tree.addSelectionPath(new TreePath(node.getPath()));

				postCallback.run();
			});
		}

		@Override
		public void undo()
		{
			super.undo();

			// must run in the swing thread
			SwingUtilities.invokeLater(() -> {
				preCallback.run();

				setTree(oldTreeTemplate);
				tree.restoreExtendedState(wasExtended);

				for (DefaultMutableTreeNode node : selectedNodes)
					tree.addSelectionPath(new TreePath(node.getPath()));

				postCallback.run();
			});
		}
	}
}
