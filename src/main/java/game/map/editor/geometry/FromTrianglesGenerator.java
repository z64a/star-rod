package game.map.editor.geometry;

import java.util.List;

import game.map.Axis;
import game.map.mesh.Triangle;
import game.map.shape.TriangleBatch;

public class FromTrianglesGenerator
{
	public enum GeneratorType
	{
		// @formatter:off
		MESH			("Copy Mesh",		"Mesh"),
		CONVEX_HULL		("Convex Hull",		"Hull"),
		CONCAVE_HULL	("Concave Hull",	"Hull"),
		PROJECTION		("Projection",		"Projection"),
		FLOOR			("Copy Floor",		"Floor"),
		WALLS			("Copy Walls",		"Wall");
		// @formatter:on

		private final String displayName;
		private final String objectName;

		private GeneratorType(String displayName, String objectName)
		{
			this.displayName = displayName;
			this.objectName = objectName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}

		public String objectName()
		{
			return objectName;
		}
	}

	public static TriangleBatch getMesh(Iterable<Triangle> triangles)
	{
		TriangleBatch batch = new TriangleBatch(null);

		for (Triangle t : triangles) {
			Triangle copy = t.deepCopy();
			batch.triangles.add(copy);
			copy.parentBatch = batch;
		}

		return batch;
	}

	public static TriangleBatch getFloor(Iterable<Triangle> triangles)
	{
		TriangleBatch batch = new TriangleBatch(null);
		Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
		double threshold = Math.sqrt(3.0) / 2.0; // 30 degrees

		for (Triangle t : triangles) {
			Vector3f normal = t.getNormal();
			if (normal == null)
				continue;

			if (Vector3f.dot(normal, up) > threshold) {
				Triangle copy = t.deepCopy();
				batch.triangles.add(copy);
				copy.parentBatch = batch;
			}
		}

		return batch;
	}

	public static TriangleBatch getWall(Iterable<Triangle> triangles)
	{
		TriangleBatch batch = new TriangleBatch(null);
		Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
		double threshold = 1 / 2.0; // 60 degrees

		for (Triangle t : triangles) {
			Vector3f normal = t.getNormal();
			if (normal == null)
				continue;

			float inner = Vector3f.dot(normal, up);
			if (inner < threshold && inner > -threshold) {
				Triangle copy = t.deepCopy();
				batch.triangles.add(copy);
				copy.parentBatch = batch;
			}
		}

		return batch;
	}

	public static TriangleBatch getProjected(List<Triangle> triangles, Axis axis)
	{
		return GeometryUtils.getConvexProjectedTriangles(triangles, axis);
	}

	public static TriangleBatch getConvexHull(List<Triangle> triangles, int height)
	{
		return GeometryUtils.getConvexHullBatch(triangles, height, true);
	}

	public static TriangleBatch getConcaveHull(List<Triangle> triangles, int threshold, int height)
	{
		return GeometryUtils.getConcaveHullBatch(triangles, threshold, height, true);
	}
}
