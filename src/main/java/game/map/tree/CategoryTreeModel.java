package game.map.tree;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import game.map.editor.DeepCopyable;
import game.map.editor.commands.AbstractCommand;

public class CategoryTreeModel<K extends Enum<?>, T extends DeepCopyable> extends DefaultTreeModel
{
	private final HashMap<K, CategoryTreeNode<K, T>> categoryMap;

	public CategoryTreeModel(String rootName)
	{
		super(new CategoryTreeNode<K, T>(rootName));
		categoryMap = new HashMap<>();
	}

	public K getCategory(CategoryTreeNode<K, T> n)
	{
		return null;
	}

	public void addCategory(K key)
	{
		if (categoryMap.containsKey(key))
			return;

		CategoryTreeNode<K, T> categoryRoot = new CategoryTreeNode<>(key);
		((DefaultMutableTreeNode) getRoot()).add(categoryRoot);
		categoryMap.put(key, categoryRoot);
	}

	public void removeCategory(K key)
	{
		CategoryTreeNode<K, T> categoryRoot = getRootForCategory(key);
		((DefaultMutableTreeNode) getRoot()).remove(categoryRoot);
		categoryMap.remove(key);
	}

	private CategoryTreeNode<K, T> getRootForCategory(K key)
	{
		CategoryTreeNode<K, T> categoryRoot = categoryMap.get(key);

		if (categoryRoot == null)
			throw new IllegalStateException("Unknown tree category: " + key.toString());

		return categoryRoot;
	}

	@SuppressWarnings("unchecked")
	public List<T> getObjectsInCategory(K key)
	{
		CategoryTreeNode<K, T> categoryRoot = getRootForCategory(key);
		List<T> list = new ArrayList<>(categoryRoot.getChildCount());

		for (int i = 0; i < categoryRoot.getChildCount(); i++)
			list.add((T) ((CategoryTreeNode<K, T>) categoryRoot.getChildAt(i)).getUserObject());

		return list;
	}

	public void addToCategory(K key, T obj)
	{
		CategoryTreeNode<K, T> categoryRoot = getRootForCategory(key);
		CategoryTreeNode<K, T> node = new CategoryTreeNode<>(key, obj);
		node.setAllowsChildren(false);
		categoryRoot.add(node);
	}

	public void clearCategory(K key)
	{
		CategoryTreeNode<K, T> categoryRoot = getRootForCategory(key);
		categoryRoot.removeAllChildren();
	}

	public void clearAll()
	{
		for (CategoryTreeNode<K, T> categoryRoot : categoryMap.values())
			categoryRoot.removeAllChildren();
	}

	public class AddObject extends AbstractCommand
	{
		private final JTree tree;
		private final CategoryTreeNode<K, T> parent;
		private final CategoryTreeNode<K, T> node;
		private final TreePath[] selected;

		public AddObject(String cmdName, JTree tree, K key, T obj)
		{
			super(cmdName);

			this.tree = tree;
			selected = tree.getSelectionPaths();

			this.parent = getRootForCategory(key);
			this.node = new CategoryTreeNode<>(key, obj);
			this.node.setAllowsChildren(false);
		}

		@Override
		public void exec()
		{
			super.exec();
			insertNodeInto(node, parent, 0);
			tree.addSelectionPath(new TreePath(node.getPath()));
		}

		@Override
		public void undo()
		{
			super.undo();
			removeNodeFromParent(node);
			tree.setSelectionPaths(selected);
		}
	}

	public class RemoveObject extends AbstractCommand
	{
		private final JTree tree;
		private final CategoryTreeNode<K, T> parent;
		private final CategoryTreeNode<K, T> node;
		private final int pos;

		@SuppressWarnings("unchecked")
		public RemoveObject(String cmdName, JTree tree, CategoryTreeNode<K, T> node)
		{
			super(cmdName);

			this.tree = tree;
			this.node = node;
			this.parent = (CategoryTreeNode<K, T>) node.getParent();
			pos = parent.getIndex(node);
		}

		@Override
		public void exec()
		{
			super.exec();
			removeNodeFromParent(node);
			tree.setSelectionPath(null);
		}

		@Override
		public void undo()
		{
			super.undo();
			insertNodeInto(node, parent, pos);
			tree.addSelectionPath(new TreePath(node.getPath()));
		}
	}

	private static final Dimension DEFAULT_SIZE = new Dimension(300, 20);

	public static class CategoryTreeCellRenderer extends DefaultTreeCellRenderer implements TreeCellRenderer
	{
		private final Dimension size;

		public CategoryTreeCellRenderer()
		{
			this(DEFAULT_SIZE);
		}

		public CategoryTreeCellRenderer(Dimension size)
		{
			this.size = size;
		}

		@Override
		public Dimension getPreferredSize()
		{ return size; }

		/*
		@Override
		public boolean isVisible()
		{
			return false;
		}
		*/

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object obj,
			boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
		{
			super.getTreeCellRendererComponent(tree, obj, selected, expanded, leaf, row, hasFocus);

			CategoryTreeNode<?, ?> node = (CategoryTreeNode<?, ?>) obj;

			if (node.getAllowsChildren())
				if (expanded)
					setIcon(openIcon);
				else
					setIcon(closedIcon);
			else
				setIcon(leafIcon);

			if (node.isCategory)
				setText(node.getCategory().toString() + " (" + node.getChildCount() + ")");
			else if (node.isObject)
				setText(node.getObject().toString());

			return this;
		}
	}

	public static class CategoryTreeNode<K, T> extends DefaultMutableTreeNode
	{
		private final boolean isCategory;
		private final boolean isObject;
		private final K category;

		private CategoryTreeNode(String rootName)
		{
			super(rootName);
			isObject = false;
			isCategory = false;
			category = null;
		}

		private CategoryTreeNode(K category)
		{
			super(category);
			isObject = false;
			isCategory = true;
			this.category = category;
		}

		private CategoryTreeNode(K category, T obj)
		{
			super(obj);
			isObject = true;
			isCategory = false;
			this.category = category;
		}

		public K getCategory()
		{ return category; }

		@SuppressWarnings("unchecked")
		public T getObject()
		{
			if (isCategory)
				return null;
			else
				return (T) getUserObject();
		}

		public boolean isCategory()
		{ return isCategory; }

		public boolean isObject()
		{ return isObject; }
	}
}
