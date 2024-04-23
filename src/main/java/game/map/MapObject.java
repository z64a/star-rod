package game.map;

import static game.map.MapKey.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.w3c.dom.Element;

import game.map.editor.UpdateProvider;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.selection.Selectable;
import game.map.editor.selection.SelectablePoint;
import game.map.editor.ui.SwingGUI;
import game.map.hit.CameraZoneData;
import game.map.mesh.AbstractMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TransformMatrix;
import game.map.tree.MapObjectNode;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public abstract class MapObject extends UpdateProvider implements Selectable, XmlSerializable
{
	private int instanceVersion = latestVersion;
	private static final int latestVersion = 1;

	private static int nextID = 0;
	public final int uniqueID;

	public MapObject(MapObjectType type)
	{
		uniqueID = nextID++;
	}

	private transient boolean transforming = false;

	/**
	 * Every map object has an axis aligned bounding box used for picking.
	 * These are rebuilt when a map is loaded and once per frame if the
	 * dirtyAABB flag is set.
	 */
	public transient BoundingBox AABB = new BoundingBox();

	/**
	 * Many things can cause the bounding box of a mesh to change.
	 * Rather than meticulously recalculating them every time the mesh changes, simply
	 * set a dirty flag which tells the editor to recalculate at the end of every frame.
	 * This is true by default so bounding boxes are rebuilt on map load.
	 */
	public transient boolean dirtyAABB = true;

	/**
	 * Decompiled objects remember their file offset. This is mostly to help debug them
	 * by comparing the original data to recompiled data in a hex editor.
	 */
	public boolean dumped = false;
	public int fileOffset;

	/**
	 * Loaded from the shape file of decompiled maps, used to reference specific MapObjects
	 * in scripts. Not necessarily unique, but expected to be so when referenced in scripts.
	 */
	private String name;

	/**
	 * Name of this object when first loaded into the editor, compared with name during build to
	 * generate rename lists.
	 */
	private String originalName = null;

	public String getName()
	{ return name; }

	public void setName(String name)
	{
		this.name = name;
		notifyListeners();
	}

	public String getOriginalName()
	{ return originalName; }

	public void captureOriginalName()
	{
		originalName = name;
	}

	public boolean hasBeenRenamed()
	{
		return (originalName != null) && !originalName.equals(name);
	}

	// used for deserialization
	public transient int deserializationID;

	// fields used by the editor
	public transient boolean hidden = false;
	public transient boolean selected = false;

	// on editor startup
	public abstract void initialize();

	// picking
	public abstract boolean shouldTryTrianglePick(PickRay ray);

	public abstract boolean shouldTryPick(PickRay ray);

	public abstract PickHit tryPick(PickRay ray);

	// mesh operations
	public abstract boolean hasMesh();

	public abstract AbstractMesh getMesh();

	public abstract void updateMeshHierarchy();

	@SuppressWarnings("unchecked")
	public static <T extends MapObject> MapObject deepCopyWithChildren(T obj)
	{
		MapObject copyObj = obj.deepCopy();
		MapObjectNode<T> node = (MapObjectNode<T>) obj.getNode();
		MapObjectNode<T> copyNode = (MapObjectNode<T>) copyObj.getNode();

		int childCount = node.getChildCount();
		for (int i = 0; i < childCount; i++) {
			MapObjectNode<?> childNode = node.getChildAt(i);
			MapObject childObj = childNode.getUserObject();

			MapObject copyChild = deepCopyWithChildren(childObj);
			MapObjectNode<T> copyChildNode = (MapObjectNode<T>) copyChild.getNode();
			copyNode.add(copyChildNode);
			//	copyChildNode.parentNode = copyNode;
			//	copyChildNode.childIndex = i;
		}

		return copyObj;
	}

	public abstract MapObject deepCopy();

	public boolean allowsCopy()
	{
		return true;
	}

	public boolean allowsDelete()
	{
		return true;
	}

	public boolean editorOnly()
	{
		return false;
	}

	public boolean hasSelectablePoints()
	{
		return hasCameraControlData();
	}

	public final List<SelectablePoint> getSelectablePoints()
	{
		List<SelectablePoint> points = new ArrayList<>();
		addSelectablePoints(points);
		return points;
	}

	public void addSelectablePoints(List<SelectablePoint> points)
	{
		if (hasCameraControlData())
			points.addAll(getCameraControlData().getPoints());
	}

	public void renderPoints(RenderingOptions opts, Renderer renderer, MapEditViewport view)
	{}

	public abstract MapObjectType getObjectType();

	@Override
	public void fromXML(XmlReader xmr, Element childElem)
	{
		Element objElement = xmr.getUniqueRequiredTag(childElem, TAG_MAP_OBJECT);

		xmr.requiresAttribute(objElement, ATTR_OBJ_NAME);
		name = xmr.getAttribute(objElement, ATTR_OBJ_NAME);

		xmr.requiresAttribute(objElement, ATTR_OBJ_ID);
		deserializationID = xmr.readHex(objElement, ATTR_OBJ_ID);

		if (xmr.hasAttribute(objElement, ATTR_OBJ_HIDDEN))
			hidden = xmr.readBoolean(objElement, ATTR_OBJ_HIDDEN);
		else
			hidden = false;

		if (xmr.hasAttribute(objElement, ATTR_OBJ_DUMPED)) {
			dumped = xmr.readBoolean(objElement, ATTR_OBJ_DUMPED);

			if (dumped)
				fileOffset = xmr.readHex(objElement, ATTR_OBJ_OFFSET);
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag objTag = xmw.createTag(TAG_MAP_OBJECT, true);

		xmw.addAttribute(objTag, ATTR_OBJ_NAME, name);

		xmw.addHex(objTag, ATTR_OBJ_ID, getNode().getTreeIndex());

		if (hidden)
			xmw.addBoolean(objTag, ATTR_OBJ_HIDDEN, hidden);

		if (dumped) {
			xmw.addBoolean(objTag, ATTR_OBJ_DUMPED, dumped);
			xmw.addHex(objTag, ATTR_OBJ_OFFSET, fileOffset);
		}

		xmw.printTag(objTag);
	}

	public boolean shouldDraw()
	{
		return !hidden && hasMesh();
	}

	public final void prepareVertexBuffers(RenderingOptions opts)
	{
		if (shouldDraw())
			getMesh().prepareVertexBuffers(opts);
	}

	public boolean hasCameraControlData()
	{
		return false;
	}

	public CameraZoneData getCameraControlData()
	{ return null; }

	@Override
	public void addTo(BoundingBox aabb)
	{
		aabb.encompass(AABB);
	}

	@Override
	public boolean transforms()
	{
		return true;
	}

	@Override
	public boolean isTransforming()
	{ return transforming; }

	@Override
	public void startTransformation()
	{
		if (!hasMesh())
			return;

		for (Triangle t : getMesh())
			for (Vertex v : t.vert)
				v.getPosition().startTransform();

		transforming = true;

		//	AABB.min.startTransform();
		//	AABB.max.startTransform();
	}

	@Override
	public void endTransformation()
	{
		if (!hasMesh())
			return;

		for (Triangle t : getMesh())
			for (Vertex v : t.vert)
				v.getPosition().endTransform();

		transforming = false;

		//	AABB.min.endTransform();
		//	AABB.max.endTransform();
	}

	@Override
	public void recalculateAABB()
	{
		AABB.clear();
		if (hasMesh())
			AABB.encompass(getMesh());
	}

	@Override
	public boolean allowRotation(Axis axis)
	{
		return true;
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positionSet)
	{
		positionSet.add(AABB.min);
		positionSet.add(AABB.max);

		if (!hasMesh())
			return;

		for (Triangle t : getMesh()) {
			t.addPoints(positionSet);
			for (Vertex v : t.vert)
				positionSet.add(v.getPosition());
		}
	}

	@Override
	public ReversibleTransform createTransformer(TransformMatrix m)
	{
		if (!hasMesh())
			return null;

		PointListBackup backup = new PointListBackup();

		for (Triangle t : getMesh())
			for (Vertex v : t.vert)
				backup.addPoint(v.getPosition().getBackup());

		backup.addPoint(AABB.min.getBackup());
		backup.addPoint(AABB.max.getBackup());

		return backup;
	}

	@Override
	public void setSelected(boolean val)
	{ selected = val; }

	@Override
	public boolean isSelected()
	{ return selected; }

	/* required for tree operations */

	public abstract boolean allowsPopup();

	public abstract boolean allowsChildren();

	public abstract MapObjectNode<? extends MapObject> getNode();

	public boolean shouldIterate()
	{
		return hasMesh();
	}

	public static class DeleteComparator implements Comparator<MapObject>
	{
		@Override
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public int compare(MapObject o1, MapObject o2)
		{
			MapObjectNode node1 = o1.getNode();
			MapObjectNode node2 = o2.getNode();

			return node2.compareTo(node1);
		}
	}

	/**
	 * @param objs
	 * @return Set containing the list of MapObjects and all of their descendents.
	 */
	public static Set<MapObject> getSetWithDescendents(Iterable<MapObject> objs)
	{
		HashSet<MapObject> set = new HashSet<>();
		Stack<MapObjectNode<?>> nodes = new Stack<>();

		for (MapObject obj : objs) {
			if (obj.editorOnly())
				continue;

			nodes.push(obj.getNode());

			while (!nodes.isEmpty()) {
				MapObjectNode<?> node = nodes.pop();
				if (node.isRoot()) {
					for (int i = 0; i < node.getChildCount(); i++)
						nodes.push(node.getChildAt(i));
				}
				else if (!set.contains(node.getUserObject())) {
					set.add(node.getUserObject());

					for (int i = 0; i < node.getChildCount(); i++)
						nodes.push(node.getChildAt(i));
				}
			}
		}

		return set;
	}

	public static enum MapObjectType
	{
		// @formatter:off
		MODEL		("Model"),
		COLLIDER	("Collider"),
		ZONE		("Zone"),
		MARKER		("Marker"),
		EDITOR		("Editor");
		// @formatter:on

		private final String name;

		private MapObjectType(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static enum ShapeType
	{
		// @formatter:off
		ROOT		("Root"),
		MODEL		("Model"),
		GROUP		("Group"),
		SPECIAL		("Special");
		// @formatter:on

		private final String name;

		private ShapeType(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static enum HitType
	{
		ROOT, HIT, GROUP
	}

	public static final class SetObjectName extends AbstractCommand
	{
		private MapObject obj;
		private final String oldName;
		private final String newName;

		public SetObjectName(MapObject obj, String s)
		{
			super("Set " + obj.getObjectType() + " Name");
			this.obj = obj;
			oldName = obj.name;
			newName = s;
		}

		@Override
		public boolean shouldExec()
		{
			return !newName.isEmpty() && !newName.equals(oldName);
		}

		@Override
		public void exec()
		{
			super.exec();
			obj.setName(newName);
			SwingGUI.instance().reloadObjectInTree(obj);
		}

		@Override
		public void undo()
		{
			super.undo();
			obj.setName(oldName);
			SwingGUI.instance().reloadObjectInTree(obj);
		}
	}
}
