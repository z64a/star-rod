package game.map.hit;

import static game.map.MapKey.*;

import org.w3c.dom.Element;

import game.map.MapObject;
import game.map.editor.render.Color4f;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.mesh.AbstractMesh;
import game.map.mesh.BasicMesh;
import game.map.mesh.Triangle;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public abstract class HitObject extends MapObject implements XmlSerializable
{
	// final for all intents and purposes aside from serialization
	private HitType hitType;

	public BasicMesh mesh;

	// used by compiler
	public transient int c_TriangleOffset;
	public transient int c_ChildIndex = -1;
	public transient int c_NextIndex = -1;

	public HitObject(MapObjectType objType, HitType hitType)
	{
		super(objType);
		this.hitType = hitType;
		mesh = new BasicMesh();
	}

	public HitType getType()
	{
		return hitType;
	}

	public abstract Color4f[] getColors(boolean useColoring);

	@Override
	public void fromXML(XmlReader xmr, Element childElem)
	{
		super.fromXML(xmr, childElem);

		Element hitElement = xmr.getUniqueRequiredTag(childElem, TAG_HIT_OBJECT);

		hitType = xmr.readEnum(hitElement, ATTR_HIT_TYPE, HitType.class);

		Element meshElement = xmr.getUniqueTag(childElem, TAG_BASIC_MESH);
		if (meshElement != null) {
			mesh = BasicMesh.read(xmr, meshElement);
			mesh.parentObject = this;
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		super.toXML(xmw);

		XmlTag hitTag = xmw.createTag(TAG_HIT_OBJECT, true);
		xmw.addEnum(hitTag, ATTR_HIT_TYPE, hitType);
		xmw.printTag(hitTag);

		if (hasMesh())
			mesh.toXML(xmw);
	}

	@Override
	public boolean transforms()
	{
		return hitType == HitType.HIT;
	}

	@Override
	public boolean shouldTryPick(PickRay ray)
	{
		return hasMesh() && PickRay.intersects(ray, AABB);
	}

	@Override
	public PickHit tryPick(PickRay ray)
	{
		PickHit nearestHit = new PickHit(ray, Float.MAX_VALUE);

		if (!PickRay.intersects(ray, AABB))
			return nearestHit;

		for (Triangle t : mesh) {
			PickHit hit = PickRay.getIntersection(ray, t);
			if (hit.dist < nearestHit.dist)
				nearestHit = hit;
		}

		nearestHit.obj = this;
		return nearestHit;
	}

	@Override
	public boolean shouldTryTrianglePick(PickRay ray)
	{
		return hasMesh();
	}

	@Override
	public boolean hasMesh()
	{
		return hitType == HitType.HIT;
	}

	@Override
	public boolean shouldIterate()
	{
		return hasMesh() && (hitType != HitType.ROOT);
	}

	@Override
	public AbstractMesh getMesh()
	{
		return mesh;
	}

	@Override
	public void updateMeshHierarchy()
	{
		mesh.parentObject = this;
		mesh.updateHierarchy();
	}

	@Override
	public boolean allowsPopup()
	{
		return hitType != HitType.HIT;
	}

	@Override
	public boolean allowsChildren()
	{
		return hitType != HitType.HIT;
	}

	@Override
	public String toString()
	{
		return getName();
	}
}
