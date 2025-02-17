package game.map.hit;

import static game.map.MapKey.*;

import org.w3c.dom.Element;

import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;
import common.commands.EditableField.StandardBoolName;
import game.map.MutablePoint;
import game.map.editor.MapEditor;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.render.Color4f;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.mesh.BasicMesh;
import game.map.shape.TriangleBatch;
import game.map.tree.MapObjectNode;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Zone extends HitObject implements XmlSerializable
{
	private int instanceVersion = latestVersion;
	private static final int latestVersion = 0;

	private MapObjectNode<Zone> node;

	public EditableField<Boolean> hasCameraData = EditableFieldFactory.create(false)
		.setCallback((obj) -> notifyListeners()).setName(new StandardBoolName("Camera")).build();

	public transient CameraZoneData camData = null;

	// used by compiler
	public transient int c_CameraOffset = -1;

	// for serialization purposes only!
	public Zone()
	{
		super(MapObjectType.COLLIDER, HitType.HIT);
	}

	// factory creation method for basic zone
	public static Zone create(TriangleBatch batch, String name)
	{
		Zone z = new Zone(HitType.HIT);
		z.setName(name);
		z.mesh.batch = batch;
		z.updateMeshHierarchy();
		z.dirtyAABB = true;
		return z;
	}

	// factory creation method for basic zone
	public static Zone create(BasicMesh mesh, String name)
	{
		Zone z = new Zone(HitType.HIT);
		z.setName(name);
		z.mesh = mesh;
		z.updateMeshHierarchy();
		z.dirtyAABB = true;
		return z;
	}

	// factory method for XML deserialization
	public static Zone read(XmlReader xmr, Element zoneElem)
	{
		Zone z = new Zone();
		z.fromXML(xmr, zoneElem);
		return z;
	}

	@Override
	public void fromXML(XmlReader xmr, Element zoneElem)
	{
		super.fromXML(xmr, zoneElem);

		xmr.requiresAttribute(zoneElem, ATTR_VERSION);
		instanceVersion = xmr.readInt(zoneElem, ATTR_VERSION);

		Element cameraElement = xmr.getUniqueTag(zoneElem, TAG_CAMERA);
		if (cameraElement != null) {
			hasCameraData.set(true);
			camData = CameraZoneData.read(xmr, cameraElement, this);
		}
		else {
			hasCameraData.set(false);
			camData = new CameraZoneData(this);
		}

		node = new MapObjectNode<>(this);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag zoneTag = xmw.createTag(TAG_ZONE, false);
		xmw.addInt(zoneTag, ATTR_VERSION, latestVersion);
		xmw.openTag(zoneTag);

		super.toXML(xmw);

		if (hasCameraData.get())
			camData.toXML(xmw);

		xmw.closeTag(zoneTag);
	}

	@Override
	public void initialize()
	{
		recalculateAABB();
	}

	@Override
	public MapObjectType getObjectType()
	{
		return MapObjectType.ZONE;
	}

	public Zone(HitType type)
	{
		super(MapObjectType.COLLIDER, type);
		this.instanceVersion = latestVersion;

		node = new MapObjectNode<>(this);
		camData = new CameraZoneData(this);
	}

	public static Zone createDefaultRoot()
	{
		Zone z = new Zone(HitType.ROOT);
		z.setName("Root");
		return z;
	}

	@Override
	public Zone deepCopy()
	{
		Zone z = new Zone(getType());
		z.AABB = AABB.deepCopy();
		z.dirtyAABB = dirtyAABB;

		z.setName(getName());

		if (z.hasMesh()) {
			z.mesh = mesh.deepCopy();
			z.updateMeshHierarchy();
		}

		z.hasCameraData.copy(hasCameraData);
		z.camData = new CameraZoneData(z, camData.getData());

		return z;
	}

	@Override
	public MapObjectNode<Zone> getNode()
	{
		return node;
	}

	private static final Color4f[] COLORS = {
			new Color4f(1.0f, 1.0f, 0.0f, 0.50f), // selected
			new Color4f(1.0f, 0.0f, 1.0f, 0.20f), // not selected, single sided
			new Color4f(1.0f, 0.0f, 0.4f, 0.20f), // not selected, double sided
			new Color4f(0.6f, 0.0f, 0.1f, 0.20f), // not selected, ignore camera
	};

	@Override
	public Color4f[] getColors(boolean useColoring)
	{
		Color4f[] colors = new Color4f[3];
		colors[0] = COLORS[0]; // selected
		colors[1] = COLORS[1]; // not selected, single sided
		colors[2] = COLORS[2]; // not selected, double sided

		//	if(hasCameraData && cam.ignoreInPreview)
		//		colors[1] = COLORS[3];

		return colors;
	}

	/**
	 * Camera Controller
	 */

	@Override
	public boolean hasCameraControlData()
	{
		return hasCameraData.get();
	}

	@Override
	public CameraZoneData getCameraControlData()
	{
		return camData;
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positionSet)
	{
		super.addPoints(positionSet);

		if (hasCameraData.get()) {
			camData.posA.addPoints(positionSet);
			camData.posB.addPoints(positionSet);
			camData.posC.addPoints(positionSet);
		}
	}

	@Override
	public void renderPoints(RenderingOptions opts, Renderer renderer, MapEditViewport view)
	{
		if (hasCameraData.get()) {
			MapEditor editor = MapEditor.instance();
			editor.dummyCameraController.update(camData, editor.cursor3D.getPosition(),
				editor.cursor3D.allowVerticalCameraMovement(), editor.getDeltaTime());
			camData.drawHelpers(renderer, editor.dummyCameraController, true);
		}
	}
}
