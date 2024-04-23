package game.map;

import static game.map.MapKey.*;

import game.map.editor.geometry.Vector3f;
import org.w3c.dom.Element;

import game.map.editor.geometry.GUVertex;
import game.map.editor.selection.SelectablePoint;
import game.map.mesh.AbstractMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TransformMatrix;
import renderer.buffers.DeferredLineRenderer;
import renderer.buffers.LineBatch;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class BoundingBox implements XmlSerializable
{
	public MutablePoint min;
	public MutablePoint max;
	private boolean empty = true;

	public transient long lastRecalculated = -1;

	public static BoundingBox read(XmlReader xmr, Element aabbElem)
	{
		BoundingBox aabb = new BoundingBox();
		aabb.fromXML(xmr, aabbElem);
		return aabb;
	}

	@Override
	public void fromXML(XmlReader xmr, Element aabbElem)
	{
		empty = xmr.readBoolean(aabbElem, ATTR_AABB_EMPTY);
		int[] xyz;

		xyz = xmr.readIntArray(aabbElem, ATTR_AABB_MIN, 3);
		min = new MutablePoint(xyz[0], xyz[1], xyz[2]);

		xyz = xmr.readIntArray(aabbElem, ATTR_AABB_MAX, 3);
		max = new MutablePoint(xyz[0], xyz[1], xyz[2]);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag aabbTag = xmw.createTag(TAG_BOUNDING_BOX, true);
		xmw.addBoolean(aabbTag, ATTR_AABB_EMPTY, empty);
		xmw.addIntArray(aabbTag, ATTR_AABB_MIN, min.getX(), min.getY(), min.getZ());
		xmw.addIntArray(aabbTag, ATTR_AABB_MAX, max.getX(), max.getY(), max.getZ());
		xmw.printTag(aabbTag);
	}

	public BoundingBox()
	{
		min = new MutablePoint(0, 0, 0);
		max = new MutablePoint(0, 0, 0);
	}

	public BoundingBox(Vector3f min, Vector3f max)
	{
		this.min = new MutablePoint(min.x, min.y, min.z);
		this.max = new MutablePoint(max.x, max.y, max.z);

		empty = false;
	}

	public BoundingBox deepCopy()
	{
		BoundingBox bb = new BoundingBox();
		bb.empty = empty;
		if (!empty) {
			bb.min = min.deepCopy();
			bb.max = max.deepCopy();
		}
		return bb;
	}

	@Override
	public String toString()
	{
		return "(" + min.getX() + "," + min.getY() + "," + min.getZ() + ") to (" + max.getX() + "," + max.getY() + "," + max.getZ() + ")";
	}

	public void clear()
	{
		empty = true;
	}

	public boolean isEmpty()
	{ return empty; }

	public Vector3f getMin()
	{ return new Vector3f(min.getX(), min.getY(), min.getZ()); }

	public Vector3f getMax()
	{ return new Vector3f(max.getX(), max.getY(), max.getZ()); }

	public Vector3f getCenter()
	{ return new Vector3f(
		(max.getX() + min.getX()) / 2,
		(max.getY() + min.getY()) / 2,
		(max.getZ() + min.getZ()) / 2); }

	public Vector3f getSize()
	{ return new Vector3f(
		(max.getX() - min.getX()),
		(max.getY() - min.getY()),
		(max.getZ() - min.getZ())); }

	public boolean contains(int x, int y, int z)
	{
		if (y < min.getY() || y > max.getY())
			return false;
		if (x < min.getX() || x > max.getX())
			return false;
		if (z < min.getZ() || z > max.getZ())
			return false;
		return true;
	}

	public boolean contains(Vertex v)
	{
		return contains(v.getCurrentX(), v.getCurrentY(), v.getCurrentZ());
	}

	public boolean contains(SelectablePoint p)
	{
		return contains(p.getX(), p.getY(), p.getZ());
	}

	public boolean contains(Vector3f vec)
	{
		return contains((int) vec.x, (int) vec.y, (int) vec.z);
	}

	public void encompass(int x, int y, int z)
	{
		if (empty) {
			min.setPosition(x, y, z);
			max.setPosition(x, y, z);
			empty = false;
		}
		else {
			if (x < min.getX())
				min.setX(x);
			else if (x > max.getX())
				max.setX(x);
			if (y < min.getY())
				min.setY(y);
			else if (y > max.getY())
				max.setY(y);
			if (z < min.getZ())
				min.setZ(z);
			else if (z > max.getZ())
				max.setZ(z);
		}
	}

	public void encompass(Vector3f v)
	{
		encompass((int) v.x, (int) v.y, (int) v.z);
	}

	public void encompass(Vertex v)
	{
		encompass(v.getCurrentX(), v.getCurrentY(), v.getCurrentZ());
	}

	public void encompass(GUVertex v)
	{
		encompass(v.x, v.y, v.z);
	}

	public void encompass(BoundingBox bb)
	{
		if (bb.empty)
			return;

		if (empty) {
			min.setPosition(bb.min);
			max.setPosition(bb.max);
			empty = false;
		}
		else {
			if (bb.min.getX() < min.getX())
				min.setX(bb.min.getX());
			if (bb.max.getX() > max.getX())
				max.setX(bb.max.getX());
			if (bb.min.getY() < min.getY())
				min.setY(bb.min.getY());
			if (bb.max.getY() > max.getY())
				max.setY(bb.max.getY());
			if (bb.min.getZ() < min.getZ())
				min.setZ(bb.min.getZ());
			if (bb.max.getZ() > max.getZ())
				max.setZ(bb.max.getZ());
		}
	}

	public void encompass(AbstractMesh mesh)
	{
		for (Triangle t : mesh)
			for (Vertex v : t.vert)
				encompass(v);
	}

	public void render()
	{
		renderDeferred(1.0f, 1.0f, 1.0f, 1.0f);
	}

	public void render(float width)
	{
		renderDeferred(1.0f, 1.0f, 1.0f, width);
	}

	public void render(float r, float g, float b)
	{
		renderDeferred(r, g, b, 1.0f);
	}

	public void renderNow(TransformMatrix mtx, float r, float g, float b, float width)
	{
		if (empty)
			return;

		int minx = min.getX();
		int miny = min.getY();
		int minz = min.getZ();

		int maxx = max.getX();
		int maxy = max.getY();
		int maxz = max.getZ();

		RenderState.setColor(r, g, b);
		RenderState.setLineWidth(width);

		int mmm = LineRenderQueue.addVertex().setPosition(minx, miny, minz).setUV(1.0f, 1.0f).getIndex();
		int Mmm = LineRenderQueue.addVertex().setPosition(maxx, miny, minz).getIndex();
		int mMm = LineRenderQueue.addVertex().setPosition(minx, maxy, minz).getIndex();
		int MMm = LineRenderQueue.addVertex().setPosition(maxx, maxy, minz).getIndex();
		int mmM = LineRenderQueue.addVertex().setPosition(minx, miny, maxz).getIndex();
		int MmM = LineRenderQueue.addVertex().setPosition(maxx, miny, maxz).getIndex();
		int mMM = LineRenderQueue.addVertex().setPosition(minx, maxy, maxz).getIndex();
		int MMM = LineRenderQueue.addVertex().setPosition(maxx, maxy, maxz).getIndex();

		LineRenderQueue.addLine(mmm, Mmm, MMm, mMm, mmm);
		LineRenderQueue.addLine(mmM, MmM, MMM, mMM, mmM);
		LineRenderQueue.addLine(mmm, mmM);
		LineRenderQueue.addLine(Mmm, MmM);
		LineRenderQueue.addLine(mMm, mMM);
		LineRenderQueue.addLine(MMm, MMM);

		LineRenderQueue.renderWithTransform(mtx, true);
	}

	public void renderDeferred(float r, float g, float b, float width)
	{
		if (empty)
			return;

		int minx = min.getX();
		int miny = min.getY();
		int minz = min.getZ();

		int maxx = max.getX();
		int maxy = max.getY();
		int maxz = max.getZ();

		RenderState.setColor(r, g, b);
		RenderState.setLineWidth(width);

		LineBatch batch = DeferredLineRenderer.addLineBatch(true).setLineWidth(width);
		int mmm = batch.addVertex().setPosition(minx, miny, minz).getIndex();
		int Mmm = batch.addVertex().setPosition(maxx, miny, minz).getIndex();
		int mMm = batch.addVertex().setPosition(minx, maxy, minz).getIndex();
		int MMm = batch.addVertex().setPosition(maxx, maxy, minz).getIndex();
		int mmM = batch.addVertex().setPosition(minx, miny, maxz).getIndex();
		int MmM = batch.addVertex().setPosition(maxx, miny, maxz).getIndex();
		int mMM = batch.addVertex().setPosition(minx, maxy, maxz).getIndex();
		int MMM = batch.addVertex().setPosition(maxx, maxy, maxz).getIndex();
		batch.add(mmm, Mmm, MMm, mMm, mmm);
		batch.add(mmM, MmM, MMM, mMM, mmM);
		batch.add(mmm, mmM);
		batch.add(Mmm, MmM);
		batch.add(mMm, mMM);
		batch.add(MMm, MMM);
	}

	public boolean overlaps(BoundingBox other)
	{
		return (min.getX() <= other.max.getX() && other.min.getX() <= max.getY() &&
			min.getY() <= other.max.getY() && other.min.getY() <= max.getY() &&
			min.getZ() <= other.max.getZ() && other.min.getZ() <= max.getZ());
	}
}
