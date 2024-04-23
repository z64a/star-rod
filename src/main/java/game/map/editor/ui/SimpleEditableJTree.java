package game.map.editor.ui;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Stack;
import java.util.function.Consumer;

import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import game.map.editor.MapEditor;
import game.map.editor.commands.AbstractCommand;
import util.BasicNode;
import util.Logger;
import util.identity.IdentityHashSet;

/**
 * Supports only single selection.
 */
public class SimpleEditableJTree extends JTree
{
	public static enum DragAndDropMode
	{
		ANYTHING, // anything can be moved anywhere (except the root)
		KEEP_TOP_LEVEL, // top level cant be reordered; nothing can move there
		SAME_LEVEL // only move things within same level, no changing parents
	}

	private DragAndDropMode dndMode;
	private Runnable preCallback;
	private Runnable postCallback;

	public SimpleEditableJTree(DefaultTreeModel model)
	{
		super(model);
		setRootVisible(false);
		setShowsRootHandles(true);
		getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
	}

	public void installDragAndDrop(DragAndDropMode mode, Runnable preCallback, Runnable postCallback)
	{
		this.dndMode = mode;
		this.preCallback = preCallback;
		this.postCallback = postCallback;

		setDragEnabled(true);
		setDropMode(DropMode.ON_OR_INSERT);
		setTransferHandler(new SimpleEditableTreeTransferHandler());
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
				Rectangle pathBounds = getUI().getPathBounds(SimpleEditableJTree.this, path);
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

		public void show(JPopupMenu menu)
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

	private class SimpleEditableTreeTransferHandler extends TransferHandler
	{
		private DataFlavor treeFlavor;
		private DataFlavor nodeFlavor;
		private DataFlavor[] flavors = new DataFlavor[2];

		private DefaultMutableTreeNode dropDestination = null;
		private int dropChildIndex = 0;

		public SimpleEditableTreeTransferHandler()
		{
			String mimeType;
			try {
				mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + SimpleEditableJTree.class.getName() + "\"";
				treeFlavor = new DataFlavor(mimeType);
				flavors[0] = treeFlavor;

				mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + DefaultMutableTreeNode.class.getName() + "\"";
				nodeFlavor = new DataFlavor(mimeType);
				flavors[1] = nodeFlavor;
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
			DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) getLastSelectedPathComponent();
			if (selectedNode == null)
				return null;
			else
				return new TreeTransferable(SimpleEditableJTree.this, selectedNode);
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			if (!support.isDrop())
				return false;

			if (!support.isDataFlavorSupported(treeFlavor) || !support.isDataFlavorSupported(nodeFlavor))
				return false;

			SimpleEditableJTree transferTree;
			DefaultMutableTreeNode transferNode;

			try {
				Transferable t = support.getTransferable();
				transferTree = (SimpleEditableJTree) t.getTransferData(treeFlavor);
				transferNode = (DefaultMutableTreeNode) t.getTransferData(nodeFlavor);
			}
			catch (UnsupportedFlavorException | IOException e) {
				return false;
			}

			if (transferTree != SimpleEditableJTree.this)
				return false;

			JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
			DefaultMutableTreeNode target = (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();

			if (transferNode.equals(target))
				return false;

			switch (dndMode) {
				case ANYTHING:
					break;

				case KEEP_TOP_LEVEL:
					if (transferNode.getParent() == transferTree.getModel().getRoot())
						return false;
					break;

				case SAME_LEVEL:
					if (transferNode.getParent() == transferTree.getModel().getRoot())
						return false;

					if (target != transferNode.getParent() && target.getParent() != transferNode.getParent())
						return false;
					break;
			}

			// determine where we're dropping
			if (!target.getAllowsChildren()) {
				if (target.getParent() != null) {
					DefaultMutableTreeNode parent = (DefaultMutableTreeNode) target.getParent();

					int childIndex = 0;
					for (; childIndex < parent.getChildCount(); childIndex++) {
						if (parent.getChildAt(childIndex).equals(target)) {
							childIndex++;
							break;
						}
					}

					dropDestination = parent;
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
		public boolean importData(TransferSupport support)
		{
			if (!canImport(support))
				return false;

			DefaultMutableTreeNode transferNode;

			try {
				Transferable t = support.getTransferable();
				transferNode = (DefaultMutableTreeNode) t.getTransferData(nodeFlavor);
			}
			catch (UnsupportedFlavorException | IOException e) {
				return false;
			}

			MapEditor.execute(new MoveNode(
				SimpleEditableJTree.this,
				preCallback,
				postCallback,
				transferNode,
				dropDestination,
				dropChildIndex));
			return true;
		}

		private class TreeTransferable implements Transferable
		{
			private final SimpleEditableJTree tree;
			private final DefaultMutableTreeNode node;

			public TreeTransferable(SimpleEditableJTree tree, DefaultMutableTreeNode node)
			{
				this.tree = tree;
				this.node = node;
			}

			@Override
			public DataFlavor[] getTransferDataFlavors()
			{ return flavors; }

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor)
			{
				return flavor.equals(treeFlavor) || flavor.equals(nodeFlavor);
			}

			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
			{
				if (!isDataFlavorSupported(flavor))
					throw new UnsupportedFlavorException(flavor);

				if (flavor == treeFlavor)
					return tree;

				if (flavor == nodeFlavor)
					return node;

				throw new UnsupportedFlavorException(flavor);
			}
		}
	}

	public class MoveNode extends AbstractCommand
	{
		private final Runnable preCallback;
		private final Runnable postCallback;

		private final SimpleEditableJTree tree;
		private final DefaultMutableTreeNode moveNode;

		private final BasicNode<DefaultMutableTreeNode> oldTreeTemplate;
		private final BasicNode<DefaultMutableTreeNode> newTreeTemplate;

		private HashMap<DefaultMutableTreeNode, Boolean> extendedState;
		private boolean valid = true;

		public MoveNode(
			SimpleEditableJTree tree,
			Runnable preCallback,
			Runnable postCallback,
			DefaultMutableTreeNode node,
			DefaultMutableTreeNode newParentNode,
			int insertionIndex)
		{
			super("Reorder Tree");

			this.preCallback = preCallback;
			this.postCallback = postCallback;

			this.tree = tree;
			this.moveNode = node;

			// reject moving to oneself
			if (node == newParentNode)
				valid = false;

			// reject moving in with your children
			if (newParentNode.isNodeAncestor(node))
				valid = false;

			if (insertionIndex == -1)
				insertionIndex = newParentNode.getChildCount();

			if (newParentNode == node.getParent()) {
				int childIndex = getModel().getIndexOfChild(node.getParent(), node);
				if (childIndex < insertionIndex)
					insertionIndex--;
			}

			if (insertionIndex < 0)
				insertionIndex = 0;

			DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();

			// create a template from the existing tree without skipping any nodes
			IdentityHashSet<DefaultMutableTreeNode> nodesToSkip = new IdentityHashSet<>();
			oldTreeTemplate = copyTree(root, nodesToSkip);

			// create a template from the existing tree, skipping the moved nodes
			nodesToSkip.add(moveNode);
			newTreeTemplate = copyTree(root, nodesToSkip);

			// add the moved nodes to their new location in template tree
			BasicNode<DefaultMutableTreeNode> copyParent = find(newTreeTemplate, newParentNode);
			BasicNode<DefaultMutableTreeNode> bnode = new BasicNode<>(moveNode);
			copyParent.addChild(bnode, insertionIndex);

			extendedState = tree.saveExtendedState();
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
			return valid;
		}

		@Override
		public void exec()
		{
			super.exec();

			// must run in the swing thread
			SwingUtilities.invokeLater(() -> {
				preCallback.run();

				setTree(newTreeTemplate);
				tree.restoreExtendedState(extendedState);

				tree.addSelectionPath(new TreePath(moveNode.getPath()));

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
				tree.restoreExtendedState(extendedState);

				tree.addSelectionPath(new TreePath(moveNode.getPath()));

				postCallback.run();
			});
		}
	}
}
