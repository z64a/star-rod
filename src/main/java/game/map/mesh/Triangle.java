package game.map.mesh;

import static game.map.MapKey.*;

import java.util.Objects;

import org.w3c.dom.Element;

import game.map.Axis;
import game.map.BoundingBox;
import game.map.MutablePoint;
import game.map.PointListBackup;
import game.map.ReversibleTransform;
import game.map.editor.geometry.GeometryUtils;
import game.map.editor.geometry.Vector3f;
import game.map.editor.selection.Selectable;
import game.map.shape.TransformMatrix;
import game.map.shape.TriangleBatch;
import util.MathUtil;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Triangle implements XmlSerializable, Selectable
{
	private int instanceVersion = latestVersion;
	private static final int latestVersion = 1;

	public Vertex[] vert;
	public boolean doubleSided = false;

	public transient TriangleBatch parentBatch;
	public transient boolean selected = false;

	public transient int[] ijk = new int[3];

	//TODO
	public void setVertex(int i, Vertex v)
	{
		vert[i] = v;
		// update half edges ...
	}

	/**
	 * For serialization purposes only!
	 */
	public Triangle()
	{}

	public static Triangle read(XmlReader xmr, Element triangleElement, Vertex[] vertexTable)
	{
		Triangle tri = new Triangle();
		tri.fromXML(xmr, triangleElement);

		tri.vert = new Vertex[3];
		for (int i = 0; i < 3; i++) {
			int vi = tri.ijk[i];
			if (vi >= vertexTable.length)
				xmr.complain("Vertex index is out of bounds: " + vi);

			tri.vert[i] = vertexTable[vi];
		}

		return tri;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		xmr.requiresAttribute(elem, ATTR_TRI_IJK);
		ijk = xmr.readIntArray(elem, ATTR_TRI_IJK, 3);

		if (xmr.hasAttribute(elem, ATTR_TRI_TWOSIDE))
			doubleSided = xmr.readBoolean(elem, ATTR_TRI_TWOSIDE);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag triTag = xmw.createTag(TAG_TRIANGLE, true);

		xmw.addIntArray(triTag, ATTR_TRI_IJK, ijk);

		if (doubleSided)
			xmw.addBoolean(triTag, ATTR_TRI_TWOSIDE, doubleSided);

		xmw.printTag(triTag);
	}

	public Triangle(Vertex v1, Vertex v2, Vertex v3)
	{
		this.instanceVersion = latestVersion;
		vert = new Vertex[3];
		vert[0] = v1;
		vert[1] = v2;
		vert[2] = v3;
	}

	public void setParent(TriangleBatch b)
	{
		parentBatch = b;
	}

	/**
	 * @return Deep copy of this Triangle. No hierarchical relationships are copied.
	 */
	public Triangle deepCopy()
	{
		return new Triangle(vert[0].deepCopy(), vert[1].deepCopy(), vert[2].deepCopy());
	}

	public void flipNormal()
	{
		Vertex temp = vert[1];
		vert[1] = vert[2];
		vert[2] = temp;
	}

	public float getRadius()
	{
		Vector3f center = getCenter();
		float d0 = GeometryUtils.dist3D(vert[0].getCurrentPos(), center);
		float d1 = GeometryUtils.dist3D(vert[1].getCurrentPos(), center);
		float d2 = GeometryUtils.dist3D(vert[2].getCurrentPos(), center);
		return Math.max(d0, Math.max(d1, d2));
	}

	public Vector3f getCenter()
	{
		float x = (vert[0].getCurrentX() + vert[1].getCurrentX() + vert[2].getCurrentX()) / 3.0f;
		float y = (vert[0].getCurrentY() + vert[1].getCurrentY() + vert[2].getCurrentY()) / 3.0f;
		float z = (vert[0].getCurrentZ() + vert[1].getCurrentZ() + vert[2].getCurrentZ()) / 3.0f;

		return new Vector3f(x, y, z);
	}

	public Vector3f getNormal()
	{
		Vector3f norm = new Vector3f();

		float Ax = vert[1].getCurrentX() - vert[0].getCurrentX();
		float Ay = vert[1].getCurrentY() - vert[0].getCurrentY();
		float Az = vert[1].getCurrentZ() - vert[0].getCurrentZ();

		float Bx = vert[2].getCurrentX() - vert[0].getCurrentX();
		float By = vert[2].getCurrentY() - vert[0].getCurrentY();
		float Bz = vert[2].getCurrentZ() - vert[0].getCurrentZ();

		norm.x = Ay * Bz - Az * By;
		norm.y = Az * Bx - Ax * Bz;
		norm.z = Ax * By - Ay * Bx;

		double mag = norm.length();
		if (Math.abs(mag) < MathUtil.SMALL_NUMBER) // colinear
			return null;

		norm.x /= mag;
		norm.y /= mag;
		norm.z /= mag;

		return norm;
	}

	public Vector3f getNormalSafe()
	{
		Vector3f norm = getNormal();
		if (norm == null)
			return new Vector3f(0, 0, 0);
		else
			return norm;
	}

	public float getArea()
	{
		float Ax = vert[1].getCurrentX() - vert[0].getCurrentX();
		float Ay = vert[1].getCurrentY() - vert[0].getCurrentY();
		float Az = vert[1].getCurrentZ() - vert[0].getCurrentZ();

		float Bx = vert[2].getCurrentX() - vert[0].getCurrentX();
		float By = vert[2].getCurrentY() - vert[0].getCurrentY();
		float Bz = vert[2].getCurrentZ() - vert[0].getCurrentZ();

		float[] cross = new float[3];
		cross[0] = Ay * Bz - Az * By;
		cross[1] = Az * Bx - Ax * Bz;
		cross[2] = Ax * By - Ay * Bx;

		double mag = Math.sqrt(cross[0] * cross[0] + cross[1] * cross[1] + cross[2] * cross[2]);
		return (float) (mag / 2.0);
	}

	@Override
	public void addTo(BoundingBox aabb)
	{
		aabb.encompass(vert[0]);
		aabb.encompass(vert[1]);
		aabb.encompass(vert[2]);
	}

	@Override
	public boolean transforms()
	{
		return true;
	}

	@Override
	public boolean isTransforming()
	{
		return vert[0].getPosition().isTransforming();
	}

	@Override
	public void startTransformation()
	{
		vert[0].getPosition().startTransform();
		vert[1].getPosition().startTransform();
		vert[2].getPosition().startTransform();
	}

	@Override
	public void endTransformation()
	{
		vert[0].getPosition().endTransform();
		vert[1].getPosition().endTransform();
		vert[2].getPosition().endTransform();
	}

	@Override
	public void recalculateAABB()
	{
		parentBatch.parentMesh.parentObject.dirtyAABB = true;
	}

	@Override
	public boolean allowRotation(Axis axis)
	{
		return true;
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		positions.add(vert[0].getPosition());
		positions.add(vert[1].getPosition());
		positions.add(vert[2].getPosition());
	}

	@Override
	public void setSelected(boolean val)
	{
		selected = val;
	}

	@Override
	public boolean isSelected()
	{
		return selected;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((vert[0] == null) ? 0 : vert[0].hashCode());
		result = prime * result + ((vert[1] == null) ? 0 : vert[1].hashCode());
		return prime * result + ((vert[2] == null) ? 0 : vert[2].hashCode());
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Triangle other = (Triangle) obj;
		if (!Objects.equals(vert[0], other.vert[0])) {
			return false;
		}
		if (!Objects.equals(vert[1], other.vert[1])) {
			return false;
		}
		if (!Objects.equals(vert[2], other.vert[2])) {
			return false;
		}
		return true;
	}

	@Override
	public ReversibleTransform createTransformer(TransformMatrix m)
	{
		PointListBackup backups = new PointListBackup();
		backups.addPoint(vert[0].getPosition().getBackup());
		backups.addPoint(vert[1].getPosition().getBackup());
		backups.addPoint(vert[2].getPosition().getBackup());
		return backups;
	}

	@Override
	public String toString()
	{
		return String.format("%s %s %s (%s)",
			vert[0].getCurrentPos().toString(),
			vert[1].getCurrentPos().toString(),
			vert[2].getCurrentPos().toString(),
			getNormalSafe());
	}
}
