package game.map.tree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;

import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import util.Logger;

public class MapObjectNode<T extends MapObject>
	extends DefaultMutableTreeNode
	implements Comparable<MapObjectNode<T>>
{
	public MapObjectNode<T> parentNode; // deleted nodes should remember their parents
	public int childIndex; // ... which child they were

	// where this node exists in the tree, used by scripts to index objects
	private int treeIndex = -1;

	public MapObjectNode(T obj)
	{
		super(obj);
	}

	public MapObjectType getObjectType()
	{
		return getUserObject().getObjectType();
	}

	@Override
	public void add(MutableTreeNode child)
	{
		super.add(child);

		@SuppressWarnings("unchecked")
		MapObjectNode<T> childNode = (MapObjectNode<T>) child;

		childNode.parentNode = this;
		childNode.childIndex = getIndex(childNode);

		assert (childIndex >= 0);
	}

	// cuts down on the amount of casting required
	@SuppressWarnings("unchecked")
	@Override
	public MapObjectNode<T> getParent()
	{
		return (MapObjectNode<T>) super.getParent();
	}

	// cuts down on the amount of casting required
	@SuppressWarnings("unchecked")
	@Override
	public MapObjectNode<T> getChildAt(int index)
	{
		if (super.getChildCount() <= index) {
			Logger.log("Tried to get invalid child " + index + " from " + getUserObject());
			return null;
		}
		return (MapObjectNode<T>) super.getChildAt(index);
	}

	// cuts down on the amount of casting required
	@SuppressWarnings("unchecked")
	@Override
	public T getUserObject()
	{
		return (T) super.getUserObject();
	}

	/**
	 * It is assumed that any node which allows children is a group and
	 * any node that does not is an object! (See: MoveNodes)
	 */
	@Override
	public boolean getAllowsChildren()
	{
		return getUserObject().allowsChildren();
	}

	public int reassignIndexDepthFirstPost(int current)
	{
		for (int i = 0; i < getChildCount(); i++) {
			current = getChildAt(i).reassignIndexDepthFirstPost(current);
			getChildAt(i).childIndex = i;
		}

		treeIndex = current++;

		return current;
	}

	public int countDescendents()
	{
		return countDescendents(-1);
	}

	private int countDescendents(int accumulator)
	{
		for (int i = 0; i < getChildCount(); i++) {
			accumulator = getChildAt(i).countDescendents(accumulator);
		}
		accumulator++;
		return accumulator;
	}

	public int getTreeIndex()
	{
		return treeIndex;
	}

	@Override
	public int compareTo(MapObjectNode<T> other)
	{
		return treeIndex - other.treeIndex;
	}
}
