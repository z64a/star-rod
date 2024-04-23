package game.map.hit;

import static game.map.MapKey.*;

import org.w3c.dom.Element;

import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.render.Color4f;
import game.map.mesh.BasicMesh;
import game.map.shape.TriangleBatch;
import game.map.tree.MapObjectNode;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Collider extends HitObject implements XmlSerializable
{
	private int instanceVersion = latestVersion;
	private static final int latestVersion = 0;

	public static final int IGNORE_SHELL_BIT = 0x8000;
	public static final int IGNORE_PLAYER_BIT = 0x10000;
	public static final int IGNORE_NPC_BIT = 0x20000;

	private MapObjectNode<Collider> node;

	public EditableField<Integer> flags = EditableFieldFactory.create(0)
		.setCallback((obj) -> notifyListeners()).setName("Set Collider Flags").build();

	public EditableField<Integer> surface = EditableFieldFactory.create(0)
		.setCallback((obj) -> notifyListeners()).setName("Set Collider Surface").build();

	// for serialization purposes only!
	public Collider()
	{
		super(MapObjectType.COLLIDER, HitType.HIT);
	}

	// factory creation method for basic collider
	public static Collider create(TriangleBatch batch, String name)
	{
		Collider c = new Collider(HitType.HIT);
		c.setName(name);
		c.mesh.batch = batch;
		c.updateMeshHierarchy();
		c.dirtyAABB = true;
		return c;
	}

	// factory creation method for basic collider
	public static Collider create(BasicMesh mesh, String name)
	{
		Collider c = new Collider(HitType.HIT);
		c.setName(name);
		c.mesh = mesh;
		c.updateMeshHierarchy();
		c.dirtyAABB = true;
		return c;
	}

	// factory method for XML deserialization
	public static Collider read(XmlReader xmr, Element colliderElement)
	{
		Collider c = new Collider();
		c.fromXML(xmr, colliderElement);
		return c;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		super.fromXML(xmr, elem);

		xmr.requiresAttribute(elem, ATTR_VERSION);
		instanceVersion = xmr.readInt(elem, ATTR_VERSION);

		xmr.requiresAttribute(elem, ATTR_COL_FLAGS);
		flags.set(xmr.readHex(elem, ATTR_COL_FLAGS));

		if (xmr.hasAttribute(elem, ATTR_COL_SURFACE))
			surface.set(xmr.readHex(elem, ATTR_COL_SURFACE));

		node = new MapObjectNode<>(this);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag colliderTag = xmw.createTag(TAG_COLLIDER, false);
		xmw.addInt(colliderTag, ATTR_VERSION, latestVersion);
		xmw.addHex(colliderTag, ATTR_COL_FLAGS, flags.get());
		xmw.addHex(colliderTag, ATTR_COL_SURFACE, surface.get());

		xmw.openTag(colliderTag);
		super.toXML(xmw);
		xmw.closeTag(colliderTag);
	}

	@Override
	public void initialize()
	{
		recalculateAABB();
	}

	@Override
	public MapObjectType getObjectType()
	{ return MapObjectType.COLLIDER; }

	public Collider(HitType type)
	{
		super(MapObjectType.COLLIDER, type);
		this.instanceVersion = latestVersion;

		node = new MapObjectNode<>(this);
	}

	public static Collider createDefaultRoot()
	{
		Collider c = new Collider(HitType.ROOT);
		c.setName("Root");
		return c;
	}

	@Override
	public Collider deepCopy()
	{
		Collider c = new Collider(getType());
		c.AABB = AABB.deepCopy();
		c.dirtyAABB = dirtyAABB;
		c.setName(getName());

		if (c.hasMesh()) {
			c.mesh = mesh.deepCopy();
			c.updateMeshHierarchy();
		}

		c.flags.set(flags.get());
		c.surface.set(surface.get());

		return c;
	}

	@Override
	public MapObjectNode<Collider> getNode()
	{ return node; }

	private static final Color4f[] COLORS = {
			new Color4f(0.0f, 1.0f, 0.0f, 0.50f), // selected
			new Color4f(0.0f, 1.0f, 1.0f, 0.20f), // not selected, single sided (CYAN)
			new Color4f(0.0f, 0.60f, 1.0f, 0.30f), // not selected, double sided (DARK BLUE)
			new Color4f(0.24f, 0.24f, 0.32f, 0.20f), // (GREY)
			new Color4f(0.5f, 0.0f, 1.0f, 0.20f), // not selected, special flags (PURPLE)
			new Color4f(1.0f, 0.0f, 0.0f, 0.20f), // invalid (RED)
	};

	public static int R = 0;
	public static int G = 0;
	public static int B = 0;
	public static int A = 255;

	@Override
	public Color4f[] getColors(boolean useColoring)
	{
		Color4f[] colors = new Color4f[3];
		colors[0] = COLORS[0]; // selected
		colors[1] = COLORS[1]; // not selected, single sided
		colors[2] = COLORS[2]; // not selected, double sided

		colors[1] = COLORS[1];
		if (useColoring) {
			int typeFlags = (flags.get() & (IGNORE_PLAYER_BIT | IGNORE_SHELL_BIT));
			if (typeFlags == IGNORE_PLAYER_BIT)
				colors[1] = COLORS[5]; // probable error
			if (typeFlags == (IGNORE_PLAYER_BIT | IGNORE_SHELL_BIT))
				colors[1] = COLORS[4]; // enemy blocker
			if (typeFlags == IGNORE_SHELL_BIT)
				colors[1] = COLORS[3]; // invisible wall
		}

		return colors;
	}
}
