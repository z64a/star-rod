package game.map.shape;

import java.util.Collection;

import game.map.Axis;
import game.map.BoundingBox;
import game.map.editor.MapEditor;
import game.map.editor.geometry.Vector3f;
import game.map.editor.selection.Selection;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import util.MathUtil;
import util.identity.IdentityHashSet;

public class UVGenerator
{
	public static enum ProjectionAxis
	{
		X, Y, Z, NORMAL
	}

	public static enum Projection
	{
		PLANAR, POLAR, CYLINDRICAL, SPHERICAL, ONLYZ
	}//, UNWRAP}

	private final UVGenerator.Projection projection;
	private final ProjectionAxis axis;

	private final float uScale;
	private final float vScale;

	public UVGenerator(Projection projection, Axis axis)
	{
		this.projection = projection;

		this.uScale = MapEditor.instance().getDefaultUVScale();
		this.vScale = MapEditor.instance().getDefaultUVScale();

		switch (axis) {
			case X:
				this.axis = ProjectionAxis.X;
				break;
			case Y:
				this.axis = ProjectionAxis.Y;
				break;
			case Z:
				this.axis = ProjectionAxis.Z;
				break;
			default:
				throw new IllegalStateException("Invalid projection axis: " + axis);
		}
	}

	public UVGenerator(Projection projection, ProjectionAxis axis, float uvScale)
	{
		this.projection = projection;
		this.axis = axis;

		this.uScale = uvScale;
		this.vScale = uvScale;
	}

	public void generateUVs(MapEditor editor, Collection<Triangle> triangles)
	{
		Vector3f normal;

		switch (axis) {
			case X:
				normal = new Vector3f(1.0f, 0.0f, 0.0f);
				break;
			default:
			case Y:
				normal = new Vector3f(0.0f, 1.0f, 0.0f);
				break;
			case Z:
				normal = new Vector3f(0.0f, 0.0f, 1.0f);
				break;
			case NORMAL:
				normal = new Vector3f();
				int count = 0;
				for (Triangle t : triangles) {
					Vector3f tnorm = t.getNormal();
					if (tnorm != null) {
						normal.add(tnorm);
						count++;
					}
				}
				if (count > 0) {
					normal.x /= count;
					normal.y /= count;
					normal.z /= count;

					if (normal.length() < MathUtil.SMALL_NUMBER)
						normal = new Vector3f(0.0f, 1.0f, 0.0f);
					else
						normal.normalize();
				}
				else
					normal = new Vector3f(0.0f, 1.0f, 0.0f);
				break;
		}

		normal.negate();

		Vector3f worldUp = new Vector3f(0.0f, 1.0f, 0.0f);
		if (Math.abs(Vector3f.dot(normal, worldUp)) > 0.99)
			worldUp = new Vector3f(1.0f, 0.0f, 0.0f);

		Vector3f right = Vector3f.cross(worldUp, normal);
		Vector3f up = Vector3f.cross(normal, right);

		switch (projection) {
			default:
			case PLANAR:
				generatePlanarUVs(editor, triangles, up, right);
				break;
			case ONLYZ:
				generateOnlyZUVs(editor, triangles);
				break;
			case POLAR:
				generatePolarUVs(editor, triangles, up, right);
				break;
			case CYLINDRICAL:
				generateCylindicalUVs(editor, triangles, normal, up, right);
				break;
			case SPHERICAL:
				generateSphericalUVs(editor, triangles, normal, up, right);
				break;
			//	case UNWRAP:
			//		new UVUnwrapper(triangles);
			//		break;
		}
	}

	private void generatePlanarUVs(MapEditor editor, Iterable<Triangle> triangles, Vector3f up, Vector3f right)
	{
		IdentityHashSet<Vertex> vertexSet = new IdentityHashSet<>();
		for (Triangle t : triangles) {
			for (Vertex v : t.vert)
				vertexSet.add(v);
		}

		Selection<UV> uvList = new Selection<>(UV.class, editor);
		BoundingBox aabb = new BoundingBox();
		for (Vertex v : vertexSet) {
			uvList.addWithoutSelecting(v.uv);
			aabb.encompass(v);
		}

		Vector3f center = aabb.getCenter();

		uvList.startDirectTransformation();
		for (Vertex vert : vertexSet) {
			Vector3f relative = Vector3f.sub(vert.getCurrentPos(), center);
			float u = Vector3f.dot(relative, right);
			float v = Vector3f.dot(relative, up);
			vert.uv.setPosition((int) (uScale * u), (int) (vScale * v));
		}
		uvList.endDirectTransformation();
	}

	private void generateOnlyZUVs(MapEditor editor, Iterable<Triangle> triangles)
	{
		IdentityHashSet<Vertex> vertexSet = new IdentityHashSet<>();
		for (Triangle t : triangles) {
			for (Vertex v : t.vert)
				vertexSet.add(v);
		}

		Selection<UV> uvList = new Selection<>(UV.class, editor);
		BoundingBox aabb = new BoundingBox();
		for (Vertex v : vertexSet) {
			uvList.addWithoutSelecting(v.uv);
			aabb.encompass(v);
		}

		Vector3f center = aabb.getCenter();

		uvList.startDirectTransformation();
		for (Vertex vert : vertexSet) {
			Vector3f relative = Vector3f.sub(vert.getCurrentPos(), center);
			vert.uv.setPosition(vert.uv.getU(), (int) (vScale * relative.y));
		}
		uvList.endDirectTransformation();
	}

	private void generatePolarUVs(MapEditor editor, Iterable<Triangle> triangles, Vector3f up, Vector3f right)
	{
		IdentityHashSet<Vertex> vertexSet = new IdentityHashSet<>();
		for (Triangle t : triangles) {
			for (Vertex v : t.vert)
				vertexSet.add(v);
		}

		Selection<UV> uvList = new Selection<>(UV.class, editor);
		BoundingBox aabb = new BoundingBox();
		for (Vertex v : vertexSet) {
			uvList.addWithoutSelecting(v.uv);
			aabb.encompass(v);
		}

		Vector3f center = aabb.getCenter();

		uvList.startDirectTransformation();
		for (Vertex vert : vertexSet) {
			Vector3f relative = Vector3f.sub(vert.getCurrentPos(), center);
			float u = Vector3f.dot(relative, right);
			float v = Vector3f.dot(relative, up);

			double r = Math.sqrt(u * u + v * v);
			double phi = 180.0 + Math.toDegrees(Math.atan2(v, u));

			vert.uv.setPosition((int) (uScale * phi), (int) (vScale * r));
		}
		uvList.endDirectTransformation();
	}

	private void generateCylindicalUVs(MapEditor editor, Iterable<Triangle> triangles, Vector3f forward, Vector3f up, Vector3f right)
	{
		IdentityHashSet<Vertex> vertexSet = new IdentityHashSet<>();
		for (Triangle t : triangles) {
			for (Vertex v : t.vert)
				vertexSet.add(v);
		}

		Selection<UV> uvList = new Selection<>(UV.class, editor);
		BoundingBox aabb = new BoundingBox();
		for (Vertex v : vertexSet) {
			uvList.addWithoutSelecting(v.uv);
			aabb.encompass(v);
		}

		Vector3f center = aabb.getCenter();

		uvList.startDirectTransformation();
		for (Vertex vert : vertexSet) {
			Vector3f relative = Vector3f.sub(vert.getCurrentPos(), center);

			float x = Vector3f.dot(relative, right);
			float y = Vector3f.dot(relative, up);
			float z = Vector3f.dot(relative, forward);

			double azimuth = Math.toDegrees(Math.atan2(y, x));

			vert.uv.setPosition((int) (uScale * azimuth), (int) (vScale * z));
		}
		uvList.endDirectTransformation();
	}

	private void generateSphericalUVs(MapEditor editor, Iterable<Triangle> triangles, Vector3f forward, Vector3f up, Vector3f right)
	{
		IdentityHashSet<Vertex> vertexSet = new IdentityHashSet<>();
		for (Triangle t : triangles) {
			for (Vertex v : t.vert)
				vertexSet.add(v);
		}

		Selection<UV> uvList = new Selection<>(UV.class, editor);
		BoundingBox aabb = new BoundingBox();
		for (Vertex v : vertexSet) {
			uvList.addWithoutSelecting(v.uv);
			aabb.encompass(v);
		}

		Vector3f center = aabb.getCenter();

		uvList.startDirectTransformation();
		for (Vertex vert : vertexSet) {
			Vector3f relative = Vector3f.sub(vert.getCurrentPos(), center);

			float x = Vector3f.dot(relative, right);
			float y = Vector3f.dot(relative, up);
			float z = Vector3f.dot(relative, forward);

			double radius = Math.sqrt(x * x + y * y);
			double azimuth = 180 + Math.toDegrees(Math.atan2(y, x));
			double polar = 180 + Math.toDegrees(Math.atan2(radius, z));

			vert.uv.setPosition((int) (uScale * azimuth), (int) (vScale * polar));
		}
		uvList.endDirectTransformation();
	}
}
