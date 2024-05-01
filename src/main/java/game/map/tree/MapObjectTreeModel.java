package game.map.tree;

import static game.map.MapKey.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;

import javax.swing.tree.DefaultTreeModel;

import org.w3c.dom.Element;

import game.map.MapObject;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public abstract class MapObjectTreeModel<T extends MapObject> extends DefaultTreeModel implements Iterable<T>, XmlSerializable
{
	private MapObjectJTree<T> tree;
	public boolean ignoreSelectionChanges = false;

	public MapObjectTreeModel(MapObjectNode<T> root)
	{
		super(root);
	}

	public void setTree(MapObjectJTree<T> tree)
	{
		this.tree = tree;
	}

	public MapObjectJTree<T> getTree()
	{
		return tree;
	}

	public static <T extends MapObject> MapObjectNode<T> load(XmlReader xmr, Element objTreeElement, HashMap<Integer, T> objMap)
	{
		Element rootElement = xmr.getUniqueTag(objTreeElement, TAG_NODE); // only one root
		return readNode(xmr, rootElement, objMap);
	}

	private static <T extends MapObject> MapObjectNode<T> readNode(XmlReader xmr, Element nodeElem, HashMap<Integer, T> objMap)
	{
		xmr.requiresAttribute(nodeElem, ATTR_NODE_NAME);
		String objName = xmr.getAttribute(nodeElem, ATTR_NODE_NAME);

		xmr.requiresAttribute(nodeElem, ATTR_NODE_ID);
		int objID = xmr.readHex(nodeElem, ATTR_NODE_ID);

		T obj = objMap.get(objID);
		if (obj == null)
			xmr.complain("Could not find data for MapObject: " + objName);

		@SuppressWarnings("unchecked")
		MapObjectNode<T> node = (MapObjectNode<T>) obj.getNode();

		List<Element> childElements = xmr.getTags(nodeElem, TAG_NODE);
		for (Element childElem : childElements) {
			MapObjectNode<T> childNode = readNode(xmr, childElem, objMap);
			node.add(childNode);
		}

		return node;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		// use load from non-generic trees instead instead.
		throw new UnsupportedOperationException();
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		MapObjectNode<T> root = getRoot();
		writeNode(xmw, root);
	}

	private void writeNode(XmlWriter xmw, MapObjectNode<T> node)
	{
		boolean hasChildren = node.getChildCount() > 0;

		XmlTag nodeTag = xmw.createTag(TAG_NODE, !hasChildren);

		xmw.addAttribute(nodeTag, ATTR_NODE_NAME, node.getUserObject().getName());
		xmw.addHex(nodeTag, ATTR_NODE_ID, node.getTreeIndex());

		if (hasChildren) {
			xmw.openTag(nodeTag);
			int numChildren = node.getChildCount();
			for (int i = 0; i < numChildren; i++)
				writeNode(xmw, node.getChildAt(i));
			xmw.closeTag(nodeTag);
		}
		else
			xmw.printTag(nodeTag);
	}

	@Override
	@SuppressWarnings("unchecked")
	public MapObjectNode<T> getRoot()
	{
		return (MapObjectNode<T>) super.getRoot();
	}

	/*
	 * Returns a list of MapObjects corresponding to a depth-first
	 * traversal of the tree.
	 */
	public List<T> getList()
	{
		List<T> list = new LinkedList<>();

		Stack<MapObjectNode<T>> stack = new Stack<>();
		stack.push(getRoot());

		while (!stack.isEmpty()) {
			MapObjectNode<T> node = stack.pop();
			for (int i = 0; i < node.getChildCount(); i++)
				stack.push(node.getChildAt(i));
			list.add(node.getUserObject());
		}

		return list;
	}

	@Override
	public Iterator<T> iterator()
	{
		return new MapObjectTreeModelIterator(getRoot());
	}

	private class MapObjectTreeModelIterator implements Iterator<T>
	{
		private final Queue<T> objList;

		public MapObjectTreeModelIterator(MapObjectNode<T> root)
		{
			objList = new LinkedList<>();
			addChildren(root);
		}

		private void addChildren(MapObjectNode<T> parent)
		{
			if (parent.getUserObject().shouldIterate())
				objList.add(parent.getUserObject());

			for (int i = 0; i < parent.getChildCount(); i++)
				addChildren(parent.getChildAt(i));
		}

		@Override
		public boolean hasNext()
		{
			return !objList.isEmpty();
		}

		@Override
		public T next()
		{
			return objList.poll();
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException(
				"MapObjectTreeModel traversal does not support remove().");
		}
	}

	public static interface MapObjectVisitor<T>
	{
		public void visit(T obj);
	}

	public void depthFirstTraversal(MapObjectVisitor<T> visitor)
	{
		Stack<MapObjectNode<T>> stack = new Stack<>();
		stack.push(getRoot());

		while (!stack.isEmpty()) {
			MapObjectNode<T> node = stack.pop();
			for (int i = 0; i < node.getChildCount(); i++)
				stack.push(node.getChildAt(i));

			visitor.visit(node.getUserObject());
		}
	}

	public void breadthFirstTraversal(MapObjectVisitor<T> visitor)
	{
		Queue<MapObjectNode<T>> queue = new LinkedList<>();
		queue.add(getRoot());

		while (!queue.isEmpty()) {
			MapObjectNode<T> node = queue.poll();
			for (int i = 0; i < node.getChildCount(); i++)
				queue.add(node.getChildAt(i));

			visitor.visit(node.getUserObject());
		}
	}

	private void nextIndexTraversal(MapObjectVisitor<T> visitor, MapObjectNode<T> cur)
	{
		for (int i = 0; i < cur.getChildCount(); i++) {
			MapObjectNode<T> child = cur.getChildAt(i);
			nextIndexTraversal(visitor, child);
		}

		visitor.visit(cur.getUserObject());
	}

	/**
	 * Visit the nodes of this tree in the order of the original game's node indices:<BR>
	 * post-order depth-first (i.e., children first, parent afterward; root last)
	 * @param visitor
	 */
	public void indexTraversal(MapObjectVisitor<T> visitor)
	{
		nextIndexTraversal(visitor, getRoot());
	}

	public T getObject(String name)
	{
		Stack<MapObjectNode<T>> nodes = new Stack<>();
		nodes.push(getRoot());

		while (!nodes.isEmpty()) {
			MapObjectNode<T> node = nodes.pop();
			T obj = node.getUserObject();

			if (obj.getName().equals(name))
				return obj;

			for (int i = 0; i < node.getChildCount(); i++)
				nodes.push(node.getChildAt(i));
		}

		return null;
	}

	public int getObjectID(String name)
	{
		Stack<MapObjectNode<T>> nodes = new Stack<>();
		nodes.push(getRoot());

		while (!nodes.isEmpty()) {
			MapObjectNode<T> node = nodes.pop();
			T obj = node.getUserObject();

			if (obj.getName().equals(name))
				return node.getTreeIndex();

			for (int i = 0; i < node.getChildCount(); i++)
				nodes.push(node.getChildAt(i));
		}

		return -1;
	}

	public String getObjectName(int id)
	{
		Stack<MapObjectNode<T>> nodes = new Stack<>();
		nodes.push(getRoot());

		while (!nodes.isEmpty()) {
			MapObjectNode<T> node = nodes.pop();
			T obj = node.getUserObject();

			if (node.getTreeIndex() == id)
				return obj.getName();

			for (int i = 0; i < node.getChildCount(); i++)
				nodes.push(node.getChildAt(i));
		}

		return null;
	}

	public T getObject(int id)
	{
		Stack<MapObjectNode<T>> nodes = new Stack<>();
		nodes.push(getRoot());

		while (!nodes.isEmpty()) {
			MapObjectNode<T> node = nodes.pop();
			T obj = node.getUserObject();

			if (node.getTreeIndex() == id)
				return obj;

			for (int i = 0; i < node.getChildCount(); i++)
				nodes.push(node.getChildAt(i));
		}

		return null;
	}

	public void add(T obj)
	{
		MapObjectNode<?> node = obj.getNode();
		insertNodeInto(obj.getNode(), node.parentNode, node.childIndex);
		recalculateIndicies();
	}

	public void remove(T obj)
	{
		MapObjectNode<?> node = obj.getNode();
		removeNodeFromParent(node);
		recalculateIndicies();
	}

	public void create(T obj)
	{
		@SuppressWarnings("unchecked")
		MapObjectNode<T> node = (MapObjectNode<T>) obj.getNode();
		if (node.parentNode == null)
			node.parentNode = getRoot();
		node.childIndex = node.parentNode.getChildCount();
		insertNodeInto(node, node.parentNode, node.childIndex);
		recalculateIndicies();
	}

	public abstract void recalculateIndicies();

	public void recalculateBoundingBoxes()
	{
		recalculateBoundingBoxes(getRoot());
	}

	private boolean recalculateBoundingBoxes(MapObjectNode<T> node)
	{
		boolean dirtyChild = false;
		for (int i = 0; i < node.getChildCount(); i++)
			dirtyChild = dirtyChild || recalculateBoundingBoxes(node.getChildAt(i));

		MapObject obj = node.getUserObject();

		if (obj.dirtyAABB || dirtyChild) {
			obj.AABB.clear();
			obj.recalculateAABB();

			for (int i = 0; i < node.getChildCount(); i++)
				obj.AABB.encompass(node.getChildAt(i).getUserObject().AABB);
		}

		return false;
	}

	public void print()
	{
		printNode(getRoot(), "");
	}

	public void printNode(MapObjectNode<T> node, String prefix)
	{
		System.out.println(prefix + node.getUserObject().getName());
		for (int i = 0; i < node.getChildCount(); i++)
			printNode(node.getChildAt(i), prefix + "   ");
	}
}
