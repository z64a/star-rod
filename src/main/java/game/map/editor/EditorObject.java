package game.map.editor;

import game.map.MapObject;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.mesh.AbstractMesh;
import game.map.tree.MapObjectNode;

public abstract class EditorObject extends MapObject
{
	public EditorObject()
	{
		super(MapObjectType.EDITOR);
	}

	@Override
	public boolean allowsCopy()
	{
		return false;
	}

	@Override
	public boolean allowsDelete()
	{
		return false;
	}

	@Override
	public boolean editorOnly()
	{
		return true;
	}

	@Override
	public MapObject deepCopy()
	{
		throw new IllegalStateException("Cannot copy Editor Objects!");
	}

	@Override
	public MapObjectType getObjectType()
	{ return MapObjectType.EDITOR; }

	// ==================================================
	// picking
	// --------------------------------------------------

	@Override
	public boolean shouldTryTrianglePick(PickRay ray)
	{
		return false;
	}

	@Override
	public boolean shouldTryPick(PickRay ray)
	{
		return true;
	}

	@Override
	public PickHit tryPick(PickRay ray)
	{
		PickHit hit = PickRay.getIntersection(ray, AABB);
		hit.obj = this;
		return hit;
	}

	@Override
	public boolean hasMesh()
	{
		return false;
	}

	@Override
	public AbstractMesh getMesh()
	{ return null; }

	@Override
	public void updateMeshHierarchy()
	{}

	@Override
	public boolean allowsPopup()
	{
		throw new IllegalStateException("Editor Object cannot be used with scene trees!");
	}

	@Override
	public boolean allowsChildren()
	{
		throw new IllegalStateException("Editor Object cannot be used with scene trees!");
	}

	@Override
	public MapObjectNode<? extends MapObject> getNode()
	{
		throw new IllegalStateException("Editor Object cannot be used with scene trees!");
	}
}
