package game.map.editor.geometry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import common.Vector3f;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.editor.MapEditor;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.Logger;
import util.Priority;

public class GeometryUtils
{
	public static TriangleBatch getConvexProjectedTriangles(Iterable<Triangle> triangles, Axis axis)
	{
		if (triangles == null)
			return null;

		PointCloud projectedPoints = getProjectedPointSet(triangles, axis, 0);
		if (projectedPoints.list.size() < 3)
			return null;

		TriangleBatch batch = getDelaunayBatch(projectedPoints.list, axis, true);

		assignPlanarUVs(batch, axis);

		return batch;
	}

	public static TriangleBatch getConcaveProjectedTriangles(Iterable<Triangle> triangles, Axis axis)
	{
		if (triangles == null)
			return null;

		PointCloud projectedPoints = getProjectedPointSet(triangles, axis, 10);
		if (projectedPoints.list.size() < 3)
			return null;

		TriangleBatch batch = null;

		try {
			batch = GeometryUtils.getPolygonBatch(projectedPoints.list, axis, true);
		}
		catch (Exception e) {
			// happens almost every time because verticies aren't sorted!
		}

		return batch;
	}

	private static class PointCloud
	{
		public List<Vector3f> list = new ArrayList<>();
		public BoundingBox box = new BoundingBox();
	}

	/**
	 * Takes a list of {@code Triangle} and reduces them to a {@code PointCloud}
	 * projected along {@code axis} to the minimum vertex position along {@code axis}.
	 * @param i
	 */
	private static PointCloud getProjectedPointSet(Iterable<Triangle> triangles, Axis axis, int interp)
	{
		List<Vector3f> pointList = new ArrayList<>();
		BoundingBox pointBB = new BoundingBox();

		for (Triangle t : triangles) {
			for (Vertex v : t.vert) {
				Vector3f point = v.getCurrentPos();
				pointList.add(point);
				pointBB.encompass(point);
			}

			if (interp > 0) {
				//	addInterpPoints(pointList, pointBB, t.vert[0], t.vert[1], interp);
				//	addInterpPoints(pointList, pointBB, t.vert[1], t.vert[2], interp);
				//	addInterpPoints(pointList, pointBB, t.vert[2], t.vert[0], interp);
			}
		}

		Vector3f min = pointBB.min.getVector();
		List<Vector3f> projectedPoints = new ArrayList<>();

		for (Vector3f point : pointList) {
			Vector3f projected = null;

			switch (axis) {
				case X:
					projected = new Vector3f(min.x, point.y, point.z);
					break;
				case Y:
					projected = new Vector3f(point.x, min.y, point.z);
					break;
				case Z:
					projected = new Vector3f(point.x, point.y, min.z);
					break;
				default:
					throw new IllegalStateException("Invalid projection axis: " + axis);
			}

			projectedPoints.add(projected);
		}

		PointCloud points = new PointCloud();

		for (Vector3f p : projectedPoints) {
			if (!points.list.contains(p)) {
				points.list.add(p);
				points.box.encompass(p);
			}
		}

		return points;
	}

	// doesnt work
	private static void addInterpPoints(List<Vector3f> pointList, BoundingBox pointBB, Vertex v1, Vertex v2, int interp)
	{
		float x0 = v1.getCurrentX();
		float y0 = v1.getCurrentY();
		float z0 = v1.getCurrentZ();
		float lx = v2.getCurrentX() - x0;
		float ly = v2.getCurrentX() - y0;
		float lz = v2.getCurrentX() - z0;

		float dx = lx / (interp + 1);
		float dy = ly / (interp + 1);
		float dz = lz / (interp + 1);

		for (int i = 0; i < interp; i++) {
			Vector3f point = new Vector3f(x0 + i * dx, y0 + i * dy, z0 + i * dz);
			pointList.add(point);
			pointBB.encompass(point);
		}
	}

	// results in overlapping triangles
	public static TriangleBatch getNaiveProjectedTriangles(Iterable<Triangle> triangles, Axis axis)
	{
		if (triangles == null)
			return null;

		List<GUTriangle> converted = convertTriangles(triangles);

		BoundingBox bb = new BoundingBox();
		for (GUTriangle t : converted) {
			bb.encompass(t.vert[0]);
			bb.encompass(t.vert[1]);
			bb.encompass(t.vert[2]);
		}

		// project along axis
		Vector3f min = bb.min.getVector();
		for (GUTriangle t : converted) {
			for (GUVertex v : t.vert) {
				switch (axis) {
					case X:
						v.x = Math.round(min.x);
						break;
					case Y:
						v.y = Math.round(min.y);
						break;
					case Z:
						v.z = Math.round(min.z);
						break;
					default:
						throw new IllegalStateException("Invalid projection axis: " + axis);
				}
			}
		}

		// fuse verticies
		HashMap<GUVertex, GUVertex> fuseVertexSet = new HashMap<>();
		HashMap<GUVertex, Vertex> projectedVertexSet = new HashMap<>();
		for (GUTriangle t : converted)
			for (int i = 0; i < 3; i++) {
				GUVertex v = t.vert[i];

				if (!projectedVertexSet.containsKey(v)) {
					projectedVertexSet.put(v, v.getVertex());
					fuseVertexSet.put(v, v);
				}
				else
					t.vert[i] = fuseVertexSet.get(v);
			}

		// copy non-degenerate triangles
		TriangleBatch batch = new TriangleBatch(null);
		for (GUTriangle t : converted) {
			if (t.vert[0] == t.vert[1] || t.vert[0] == t.vert[2] || t.vert[1] == t.vert[2])
				continue;

			batch.triangles.add(new Triangle(
				projectedVertexSet.get(t.vert[0]),
				projectedVertexSet.get(t.vert[1]),
				projectedVertexSet.get(t.vert[2])));
		}

		return batch;
	}

	// Fuses a set of Triangles and converts them to GUTriangles
	private static List<GUTriangle> convertTriangles(Iterable<Triangle> triangles)
	{
		HashMap<Vertex, GUVertex> vertexMap = new HashMap<>();

		int count = 0;
		for (Triangle t : triangles) {
			for (Vertex v : t.vert) {
				if (!vertexMap.containsKey(v))
					vertexMap.put(v, new GUVertex(v));
			}
			count++;
		}

		List<GUTriangle> converted = new ArrayList<>(count);

		for (Triangle t : triangles) {
			converted.add(new GUTriangle(
				vertexMap.get(t.vert[0]),
				vertexMap.get(t.vert[1]),
				vertexMap.get(t.vert[2])));
		}

		return converted;
	}

	/**
	 * @param triangleSet
	 * @param height
	 * @return {@code TriangleBatch} corresponding to the convex hull enclosing {@code triangleSet}
	 */
	public static TriangleBatch getConvexHullBatch(List<Triangle> triangleSet, int height, boolean calculateUVs)
	{
		if (triangleSet.isEmpty())
			return null;

		PointCloud uniqueVertexSet = getProjectedPointSet(triangleSet, Axis.Y, 0);
		if (uniqueVertexSet == null)
			return null;

		List<GUVertex> vertexList = new ArrayList<>(uniqueVertexSet.list.size());
		for (Vector3f p : uniqueVertexSet.list)
			vertexList.add(new GUVertex(p));

		ArrayList<GUVertex> hull = getConvexHull(vertexList);
		if (hull == null)
			return null;

		return getGUStripBatch(hull, Axis.Y, height, calculateUVs);
	}

	/**
	 * @return {@code TriangleBatch} corresponding to the concave hull enclosing {@code triangleSet}
	 */
	public static TriangleBatch getConcaveHullBatch(List<Triangle> triangleSet, int threshold, int height, boolean calculateUVs)
	{
		if (triangleSet.isEmpty())
			return null;

		PointCloud uniqueVertexSet = getProjectedPointSet(triangleSet, Axis.Y, 10);
		if (uniqueVertexSet == null)
			return null;

		List<GUVertex> vertexList = new ArrayList<>(uniqueVertexSet.list.size());
		for (Vector3f p : uniqueVertexSet.list)
			vertexList.add(new GUVertex(p));

		ArrayList<GUVertex> hull = getConcaveHull(vertexList, threshold);
		if (hull == null)
			return null;

		return getGUStripBatch(hull, Axis.Y, height, calculateUVs);
	}

	/**
	 * @return {@code TriangleBatch} corresponding to strip of co-planar points
	 */
	public static TriangleBatch getStripBatch(Iterable<Vector3f> hull, Axis axis, int minLength, boolean calculateUVs)
	{
		ArrayList<GUVertex> vertices = new ArrayList<>();
		for (Vector3f v : hull)
			vertices.add(new GUVertex(v));
		return getGUStripBatch(vertices, axis, minLength, calculateUVs);
	}

	private static void calculateHullUVs(ArrayList<GUVertex> hull, List<Vertex> base, List<Vertex> extruded, float extrudeLength)
	{
		float uScale = MapEditor.instance().getDefaultUVScale();
		float vScale = MapEditor.instance().getDefaultUVScale();

		float utot = 0.0f;
		GUVertex prev = null;
		for (GUVertex guv : hull) {
			if (prev != null)
				utot += getDistance(prev, guv);

			prev = guv;
		}
		utot *= uScale;

		float uCompress = 1.0f;
		int maxSpan = (Short.MAX_VALUE - Short.MIN_VALUE) - 32;
		if (utot > maxSpan) {
			uCompress = maxSpan / utot;
			utot *= uCompress;
		}

		// add offset to really long strips to keep UVs from wrapping
		float uOffset = (utot > Short.MAX_VALUE / 2.0f) ? -utot / 2.0f : 0.0f;

		float u = 0.0f;
		prev = null;

		for (int i = 0; i < hull.size(); i++) {
			GUVertex guv = hull.get(i);

			if (prev != null)
				u += getDistance(prev, guv);

			base.get(i).uv = new UV(uOffset + u * uScale * uCompress, 0);
			extruded.get(i).uv = new UV(uOffset + u * uScale * uCompress, extrudeLength * vScale);

			prev = guv;
		}
	}

	private static TriangleBatch getGUStripBatch(ArrayList<GUVertex> hull, Axis axis, int minLength, boolean calculateUVs)
	{
		if (hull == null || hull.size() < 2)
			return null;

		List<Vertex> base = new ArrayList<>();
		List<Vertex> extruded = new ArrayList<>();

		BoundingBox bb = new BoundingBox();

		for (GUVertex v : hull)
			bb.encompass(v);

		Vector3f min = bb.getMin();
		Vector3f max = bb.getMax();

		float hmin;
		float hmax;

		switch (axis) {
			case X:
				hmin = min.x;
				hmax = ((max.x - min.x) < minLength) ? min.x + minLength : max.x;
				break;
			case Y:
				hmin = min.y;
				hmax = ((max.y - min.y) < minLength) ? min.y + minLength : max.y;
				break;
			case Z:
				hmin = min.z;
				hmax = ((max.z - min.z) < minLength) ? min.z + minLength : max.z;
				break;
			default:
				throw new IllegalArgumentException("Invalid axis for getGUStripBatch: " + axis);
		}

		switch (axis) {
			case X:
				for (GUVertex v : hull) {
					base.add(new Vertex(hmin, v.y, v.z));
					extruded.add(new Vertex(hmax, v.y, v.z));
				}
				break;
			case Y:
				for (GUVertex v : hull) {
					base.add(new Vertex(v.x, hmin, v.z));
					extruded.add(new Vertex(v.x, hmax, v.z));
				}
				break;
			case Z:
				for (GUVertex v : hull) {
					base.add(new Vertex(v.x, v.y, hmin));
					extruded.add(new Vertex(v.x, v.y, hmax));
				}
				break;
		}

		if (calculateUVs)
			calculateHullUVs(hull, base, extruded, (hmax - hmin));

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < hull.size() - 1; i++) {
			// ensure proper outward-facing normals
			Triangle t1 = new Triangle(extruded.get(i + 1), extruded.get(i), base.get(i));
			Triangle t2 = new Triangle(base.get(i), base.get(i + 1), extruded.get(i + 1));

			batch.triangles.add(t1);
			batch.triangles.add(t2);
		}

		return batch;
	}

	/**
	 * Implementation of the "Javis March" algorithm.
	 * @return ArrayList of SimpleVertex outlining the convex hull.
	 */
	private static ArrayList<GUVertex> getConvexHull(Iterable<GUVertex> uniqueVertexSet)
	{
		ArrayList<GUVertex> hull = new ArrayList<>();
		GUVertex startVertex = null;
		double relativeAngle = 0;

		for (GUVertex sv : uniqueVertexSet) {
			if (startVertex == null || startVertex.x > sv.x || (startVertex.x == sv.x && startVertex.z > sv.z))
				startVertex = sv;
		}

		GUVertex currentVertex = startVertex;
		hull.add(currentVertex);

		do {
			GUVertex nextVertex = null;
			double smallestAngle = Double.POSITIVE_INFINITY;
			double furthestLength = 0;

			for (GUVertex v : uniqueVertexSet) {
				if (v == currentVertex)
					continue;

				double angle = Math.toDegrees(Math.atan2(v.x - currentVertex.x, v.z - currentVertex.z));
				if (angle < 0)
					angle += 360;

				angle -= relativeAngle;
				if (angle < 0)
					angle += 360;

				if (angle < smallestAngle) {
					nextVertex = v;
					smallestAngle = angle;
					double dx = v.x - currentVertex.x;
					double dz = v.z - currentVertex.z;
					furthestLength = dx * dx + dz * dz;
				}
				else if (Math.abs(angle - smallestAngle) < 1e-6) {
					double dx = v.x - currentVertex.x;
					double dz = v.z - currentVertex.z;
					double length = dx * dx + dz * dz;
					if (length > furthestLength) {
						nextVertex = v;
						smallestAngle = angle;
						furthestLength = length;
					}
				}
			}

			hull.add(nextVertex);
			currentVertex = nextVertex;

			relativeAngle += smallestAngle;
			if (relativeAngle >= 360)
				relativeAngle -= 360;

			if (hull.size() >= 1024) {
				Logger.log("Failed to build convex hull, vertex overflow!", Priority.WARNING);
				return null;
			}
		}
		while (currentVertex != startVertex);

		return hull;
	}

	private static ArrayList<GUVertex> getConcaveHull(Iterable<GUVertex> uniqueVertexSet, int threshold)
	{
		List<GUVertex> convexHull = getConvexHull(uniqueVertexSet);

		Set<GUVertex> boundarySet = new HashSet<>(convexHull);
		LinkedList<GUEdge> edgeList = new LinkedList<>();

		// concave hull contains the sequence v1, v2, v3, ..., vn, v1
		for (int i = 0; i < convexHull.size() - 1; i++)
			edgeList.add(new GUEdge(convexHull.get(i), convexHull.get(i + 1)));
		edgeList.add(new GUEdge(convexHull.get(convexHull.size() - 1), convexHull.get(0)));

		PriorityQueue<GUEdge> sortedEdgeQueue = new PriorityQueue<>(edgeList);

		while (!sortedEdgeQueue.isEmpty()) {
			GUEdge edge = sortedEdgeQueue.poll();

			if (edge.getLength() >= threshold) {
				GUVertex bestVertex = null;
				double smallestAngle = 180.0;

				// angles are from the z axis (down) toward x (right)
				double angle12 = Math.toDegrees(Math.atan2(edge.v2.x - edge.v1.x, edge.v2.z - edge.v1.z));
				double angle21 = angle12 - 180;

				if (angle12 < 0)
					angle12 += 360;
				if (angle21 < 0)
					angle21 += 360;

				for (GUVertex v : uniqueVertexSet) {
					if (getDistance(v, edge.v1) >= edge.getLength() || getDistance(v, edge.v2) >= edge.getLength())
						continue;

					if (boundarySet.contains(v))
						continue;

					double angle1 = Math.toDegrees(Math.atan2(v.x - edge.v1.x, v.z - edge.v1.z));
					double angle2 = Math.toDegrees(Math.atan2(v.x - edge.v2.x, v.z - edge.v2.z));

					if (angle1 < 0)
						angle1 += 360;
					if (angle2 < 0)
						angle2 += 360;

					double rel1 = angle1 - angle12;
					double rel2 = angle2 - angle21;

					if (rel1 >= 360)
						rel1 -= 360;
					if (rel2 >= 360)
						rel2 -= 360;

					rel1 = Math.abs(rel1);
					rel2 = Math.abs(rel2);

					double largerAngle = (rel1 > rel2) ? rel1 : rel2;

					if (largerAngle > 90.1)
						continue;

					if (largerAngle < smallestAngle) {
						smallestAngle = largerAngle;
						bestVertex = v;
					}
				}

				// found a good vertex
				if (bestVertex != null) {
					GUEdge replacement1 = new GUEdge(edge.v1, bestVertex);
					GUEdge replacement2 = new GUEdge(bestVertex, edge.v2);

					boolean intersects = false;
					for (GUEdge e : edgeList) {
						if (e == edge)
							continue;

						if (doEdgesIntersect2D(e, replacement1) || doEdgesIntersect2D(e, replacement2))
							intersects = true;
					}

					if (intersects)
						continue;

					int i = edgeList.indexOf(edge);
					edgeList.remove(i);
					edgeList.add(i, replacement2);
					edgeList.add(i, replacement1);

					sortedEdgeQueue.add(replacement1);
					sortedEdgeQueue.add(replacement2);

					boundarySet.add(bestVertex);
				}
			}
		}

		ArrayList<GUVertex> concaveHull = new ArrayList<>();

		concaveHull.add(edgeList.get(0).v1);
		for (int i = 0; i < edgeList.size() - 1; i++)
			concaveHull.add(edgeList.get(i).v2);

		return concaveHull;
	}

	private static boolean doEdgesIntersect2D(GUEdge e1, GUEdge e2)
	{
		int dx = e2.v1.x - e1.v1.x;
		int dz = e2.v1.z - e1.v1.z;

		int rx = e1.v2.x - e1.v1.x;
		int rz = e1.v2.z - e1.v1.z;

		int sx = e2.v2.x - e2.v1.x;
		int sz = e2.v2.z - e2.v1.z;

		// 'cross products'
		double RcS = rx * sz - rz * sx;
		double DcR = dx * rz - dz * rx;
		double DcS = dx * sz - dz * sx;

		double epsilon = 1e-6;

		// parallel or co-linear
		if (Math.abs(RcS) <= 1e-5) {
			// co-linear, do they overlap?
			if (Math.abs(DcR) <= 1e-5) {
				double r2 = rx * rx + rz * rz;
				double t0 = (dx * rx + dz * rz) / r2;
				double t1 = t0 + (sx * rx + sz * rz) / r2;

				// does the interval [t0, t1] intersect [0,1]?
				if (t0 > epsilon && t0 < (1.0 - epsilon) || t1 > epsilon && t1 < (1.0 - epsilon))
					return true;
			}
		}
		// find intersection point
		else {
			double t = DcS / RcS;
			double u = DcR / RcS;

			return ((epsilon < t && t < (1.0 - epsilon)) && (epsilon < u && u < (1.0 - epsilon)));
		}

		return false;
	}

	public static TriangleBatch getDelaunayBatch(List<Vector3f> pointList, Axis axis, boolean generateUVs)
	{
		List<Vector3f> pointsXZ = pointsToXZ(pointList, axis);
		List<GUTriangle> triangulated = getDelaunayTriangulation(pointsXZ);

		if (triangulated == null)
			return null;

		TriangleBatch batch = getBatchFromTriangles(triangulated);
		batch = batchFromXZ(batch, axis);

		if (generateUVs)
			assignPlanarUVs(batch, axis);

		return batch;
	}

	private static List<GUTriangle> getDelaunayTriangulation(List<Vector3f> pointList)
	{
		if (pointList == null || pointList.isEmpty())
			return null;

		HashSet<Vector3f> uniquePointSet = new HashSet<>();
		List<GUVertex> uniqueVertexList = new LinkedList<>();

		for (Vector3f v : pointList) {
			if (!uniquePointSet.contains(v)) {
				uniquePointSet.add(v);
				uniqueVertexList.add(new GUVertex(v));
			}
		}

		if (uniqueVertexList.size() < 3)
			return null;

		if (uniqueVertexList.size() == 3) {
			List<GUTriangle> triangulation = new LinkedList<>();
			triangulation.add(new GUTriangle(uniqueVertexList.get(0), uniqueVertexList.get(1), uniqueVertexList.get(2)));
			return triangulation;
		}

		return getDelaunayTriangulation(uniqueVertexList);
	}

	private static TriangleBatch getBatchFromTriangles(List<GUTriangle> triangleList)
	{
		HashMap<GUVertex, Vertex> vertexMap = new HashMap<>();
		for (GUTriangle st : triangleList)
			for (GUVertex v : st.vert) {
				if (!vertexMap.containsKey(v))
					vertexMap.put(v, v.getVertex());
			}

		TriangleBatch batch = new TriangleBatch(null);
		for (GUTriangle st : triangleList) {
			Triangle t = new Triangle(vertexMap.get(st.vert[0]), vertexMap.get(st.vert[1]), vertexMap.get(st.vert[2]));
			batch.triangles.add(t);
		}
		return batch;
	}

	private static void assignPlanarUVs(TriangleBatch batch, Axis axis)
	{
		BoundingBox bb = new BoundingBox();
		for (Triangle t : batch.triangles)
			for (Vertex v : t.vert)
				bb.encompass(v);

		Vector3f center = bb.getCenter();

		float uScale = MapEditor.instance().getDefaultUVScale();
		float vScale = MapEditor.instance().getDefaultUVScale();

		switch (axis) {
			case X:
				for (Triangle t : batch.triangles)
					for (Vertex v : t.vert)
						v.uv = new UV(uScale * (v.getCurrentY() - center.y), -vScale * (v.getCurrentZ() - center.z));
				break;

			case Y:
				for (Triangle t : batch.triangles)
					for (Vertex v : t.vert)
						v.uv = new UV(uScale * (v.getCurrentX() - center.x), vScale * (v.getCurrentZ() - center.z));
				break;

			case Z:
				for (Triangle t : batch.triangles)
					for (Vertex v : t.vert)
						v.uv = new UV(uScale * (v.getCurrentY() - center.y), vScale * (v.getCurrentX() - center.x));
				break;

			default:
				throw new IllegalStateException("Invalid axis for assigning planar UVs: " + axis);
		}
	}

	public static Vector3f pointToXZ(Vector3f v, Axis axis)
	{
		switch (axis) {
			case X:
				return new Vector3f(v.y, v.x, -v.z);
			case Y:
				return v;
			case Z:
				return new Vector3f(v.y, v.z, v.x);
		}

		return v;
	}

	public static Vector3f pointFromXZ(Vector3f v, Axis axis)
	{
		switch (axis) {
			case X:
				return new Vector3f(v.y, v.x, -v.z);
			case Y:
				return v;
			case Z:
				return new Vector3f(v.z, v.x, v.y);
		}

		return v;
	}

	public static List<Vector3f> pointsToXZ(List<Vector3f> pointList, Axis axis)
	{
		List<Vector3f> rotatedIntoXZ = new LinkedList<>();

		for (Vector3f v : pointList)
			rotatedIntoXZ.add(pointToXZ(v, axis));

		return rotatedIntoXZ;
	}

	public static List<Vector3f> pointsFromXZ(List<Vector3f> xzPointList, Axis axis)
	{
		List<Vector3f> rotatedFromXZ = new LinkedList<>();

		for (Vector3f v : xzPointList)
			rotatedFromXZ.add(pointFromXZ(v, axis));

		return rotatedFromXZ;
	}

	private static TriangleBatch batchFromXZ(TriangleBatch batch, Axis axis)
	{
		HashMap<Vertex, Vertex> rotatedMap = new HashMap<>();

		for (Triangle t : batch.triangles)
			for (Vertex v : t.vert)
				rotatedMap.put(v, new Vertex(pointFromXZ(v.getCurrentPos(), axis)));

		TriangleBatch rotated = new TriangleBatch(null);
		if (axis != Axis.Y) {
			for (Triangle t : batch.triangles) {
				rotated.triangles.add(new Triangle(
					rotatedMap.get(t.vert[0]),
					rotatedMap.get(t.vert[1]),
					rotatedMap.get(t.vert[2])));
			}
		}
		else {
			for (Triangle st : batch.triangles) {
				rotated.triangles.add(st);
			}
		}

		return rotated;
	}

	private static List<GUTriangle> getDelaunayTriangulation(Iterable<GUVertex> uniqueVertexSet)
	{
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;

		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (GUVertex v : uniqueVertexSet) {
			if (v.x < minX)
				minX = v.x;
			if (v.x > maxX)
				maxX = v.x;
			if (v.z < minZ)
				minZ = v.z;
			if (v.z > maxZ)
				maxZ = v.z;
		}

		int sizeX = maxX - minX;
		int sizeZ = maxZ - minZ;

		if (sizeX < 16)
			sizeX = 16;
		if (sizeZ < 16)
			sizeZ = 16;

		int centerX = minX + sizeX / 2;
		int centerZ = minZ + sizeZ / 2;

		GUVertex enc1, enc2, enc3;

		if (sizeX > sizeZ) {
			enc1 = new GUVertex(centerX + sizeX, 0, centerZ - sizeZ);
			enc2 = new GUVertex(centerX - sizeX, 0, centerZ - sizeZ);
			enc3 = new GUVertex(centerX, 0, centerZ + 4 * sizeZ);
		}
		else {
			enc1 = new GUVertex(centerX - sizeX, 0, centerZ + sizeZ);
			enc2 = new GUVertex(centerX - sizeX, 0, centerZ - sizeZ);
			enc3 = new GUVertex(centerX + 4 * sizeX, 0, centerZ);
		}

		LinkedList<GUTriangle> triangleList = new LinkedList<>();

		GUTriangle enclosingTriangle = new GUTriangle(enc1, enc2, enc3);
		enclosingTriangle.makeCCW();
		triangleList.add(enclosingTriangle);

		for (GUVertex v : uniqueVertexSet)
			addVertexToDelaunayTriangulation(triangleList, v);

		Iterator<GUTriangle> i = triangleList.iterator();
		while (i.hasNext()) {
			GUTriangle t = i.next();

			if (t.vert[0] == enc1 || t.vert[0] == enc2 || t.vert[0] == enc3 ||
				t.vert[1] == enc1 || t.vert[1] == enc2 || t.vert[1] == enc3 ||
				t.vert[2] == enc1 || t.vert[2] == enc2 || t.vert[2] == enc3)
				i.remove();
		}

		return triangleList;
	}

	private static void addVertexToDelaunayTriangulation(Collection<GUTriangle> triangleList, GUVertex v)
	{
		HashMap<GUEdge, GUEdge> edgeMap = new HashMap<>();

		Iterator<GUTriangle> i = triangleList.iterator();
		while (i.hasNext()) {
			GUTriangle t = i.next();

			if (t.circumcircleContains(v)) {
				GUEdge e1 = new GUEdge(t.vert[0], t.vert[1]);
				GUEdge e2 = new GUEdge(t.vert[1], t.vert[2]);
				GUEdge e3 = new GUEdge(t.vert[2], t.vert[0]);

				if (edgeMap.containsKey(e1))
					edgeMap.get(e1).ignore = true;
				else
					edgeMap.put(e1, e1);

				if (edgeMap.containsKey(e2))
					edgeMap.get(e2).ignore = true;
				else
					edgeMap.put(e2, e2);

				if (edgeMap.containsKey(e3))
					edgeMap.get(e3).ignore = true;
				else
					edgeMap.put(e3, e3);

				i.remove();
			}
		}

		for (GUEdge e : edgeMap.keySet()) {
			if (!e.ignore) {
				GUTriangle t = new GUTriangle(v, e.v1, e.v2);
				t.makeCCW();
				triangleList.add(t);
			}
		}
	}

	private static double getDistance(Vertex v1, Vertex v2)
	{
		int dx = v1.getCurrentX() - v2.getCurrentX();
		int dy = v1.getCurrentY() - v2.getCurrentY();
		int dz = v1.getCurrentZ() - v2.getCurrentZ();

		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static double getDistance(GUVertex v1, GUVertex v2)
	{
		int dx = v1.x - v2.x;
		int dy = v1.y - v2.y;
		int dz = v1.z - v2.z;

		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static int wrapIndex(int index, int listSize)
	{
		return ((index % listSize) + listSize) % listSize;
	}

	public static TriangleBatch getPolygonBatch(List<Vector3f> points, Axis axis, boolean generateUVs)
	{
		List<Vector3f> pointsXZ = pointsToXZ(points, axis);
		List<GUTriangle> triangulated = triangulateConcavePolygon(pointsXZ);

		if (triangulated == null)
			return null;

		TriangleBatch batch = getBatchFromTriangles(triangulated);
		batch = batchFromXZ(batch, axis);

		if (generateUVs)
			assignPlanarUVs(batch, axis);

		return batch;
	}

	private static List<GUTriangle> triangulateConcavePolygon(List<Vector3f> points)
	{
		List<GUTriangle> triangles = new LinkedList<>();

		if (points.size() < 3)
			return null;

		if (points.size() == 3) {
			triangles.add(new GUTriangle(points.get(0), points.get(1), points.get(2)));
			return triangles;
		}

		List<GUVertex> vertices = new LinkedList<>();
		for (Vector3f p : points)
			vertices.add(new GUVertex(p));

		for (int i = 0; i < vertices.size(); i++) {
			GUVertex v = vertices.get(i);
			int prevIndex = wrapIndex(i - 1, vertices.size());
			int nextIndex = wrapIndex(i + 1, vertices.size());
			v.prev = vertices.get(prevIndex);
			v.next = vertices.get(nextIndex);
		}

		for (GUVertex v : vertices)
			assignReflexOrConvex(v);

		List<GUVertex> earVertices = new LinkedList<>();

		for (GUVertex v : vertices)
			checkVertexEar(v, vertices, earVertices);

		while (true) {
			if (vertices.size() == 3) {
				GUVertex v = vertices.get(0);
				triangles.add(new GUTriangle(v, v.prev, v.next));
				break;
			}

			GUVertex earVertex = earVertices.get(0);
			GUVertex earVertexPrev = earVertex.prev;
			GUVertex earVertexNext = earVertex.next;

			triangles.add(new GUTriangle(earVertex, earVertexPrev, earVertexNext));

			vertices.remove(earVertex);
			earVertices.remove(earVertex);
			earVertexPrev.next = earVertexNext;
			earVertexNext.prev = earVertexPrev;

			assignReflexOrConvex(earVertexPrev);
			assignReflexOrConvex(earVertexNext);

			earVertices.remove(earVertexPrev);
			earVertices.remove(earVertexNext);

			checkVertexEar(earVertexPrev, vertices, earVertices);
			checkVertexEar(earVertexNext, vertices, earVertices);
		}

		return triangles;
	}

	public static class PointXZ
	{
		public final int x;
		public final int z;

		public PointXZ(int x, int z)
		{
			this.x = x;
			this.z = z;
		}
	}

	private static PointXZ getXZ(GUVertex v)
	{
		return new PointXZ(v.x, v.z);
	}

	private static void assignReflexOrConvex(GUVertex v)
	{
		v.isReflex = false;
		v.isConvex = false;

		PointXZ a = getXZ(v.prev);
		PointXZ b = getXZ(v);
		PointXZ c = getXZ(v.next);

		if (isTriangleClockwise(a, b, c))
			v.isReflex = true;
		else
			v.isConvex = true;
	}

	private static void checkVertexEar(GUVertex v, List<GUVertex> vertices, List<GUVertex> earVertices)
	{
		if (v.isReflex)
			return;

		PointXZ a = getXZ(v.prev);
		PointXZ b = getXZ(v);
		PointXZ c = getXZ(v.next);

		boolean hasPointInside = false;

		for (GUVertex vi : vertices) {
			if (vi.isReflex) {
				PointXZ p = getXZ(vi);

				if (isPointInTriangle(a, b, c, p)) {
					hasPointInside = true;
					break;
				}
			}
		}

		if (!hasPointInside)
			earVertices.add(v);
	}

	private static boolean isTriangleClockwise(PointXZ a, PointXZ b, PointXZ c)
	{
		return 0.0f >= (a.x * b.z) + (c.x * a.z) + (b.x * c.z) - (a.x * c.z) - (c.x * b.z) - (b.x * a.z);
	}

	private static boolean isPointInTriangle(PointXZ p1, PointXZ p2, PointXZ p3, PointXZ p)
	{
		double denom = (p2.z - p3.z) * (p1.x - p3.x) + (p3.x - p2.x) * (p1.z - p3.z);
		double a = ((p2.z - p3.z) * (p.x - p3.x) + (p3.x - p2.x) * (p.z - p3.z)) / denom;
		double b = ((p3.z - p1.z) * (p.x - p3.x) + (p1.x - p3.x) * (p.z - p3.z)) / denom;
		double c = 1.0f - a - b;

		return (a > 0.0f && a < 1.0f && b > 0.0f && b < 1.0f && c > 0.0f && c < 1.0f);
	}

	public static boolean doLineSegmentsIntersectXZ(Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4)
	{
		if (doLinesOverlapXZ(p1, p2, p3, p4))
			return true;

		return (arePointsOnDifferentSides(p1, p2, p3, p4) && arePointsOnDifferentSides(p3, p4, p1, p2));
	}

	private static boolean doLinesOverlapXZ(Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4)
	{
		Vector3f seg1 = Vector3f.sub(p2, p1);
		Vector3f seg2 = Vector3f.sub(p4, p3);

		Vector3f norm1 = Vector3f.getNormalized(seg1);
		Vector3f norm2 = Vector3f.getNormalized(seg2);

		float dot = Vector3f.dot(norm1, norm2);
		if (Math.abs(1.0 - Math.abs(dot)) < 1e-4) {
			float a, b, c, d;
			if (Math.abs(p1.x - p2.x) > 1e-4) {
				// use x projection
				a = Math.min(p1.x, p2.x);
				b = Math.max(p1.x, p2.x);
				c = Math.min(p3.x, p4.x);
				d = Math.max(p3.x, p4.x);
			}
			else if (Math.abs(p1.z - p2.z) > 1e-4) {
				// use z projection
				a = Math.min(p1.z, p2.z);
				b = Math.max(p1.z, p2.z);
				c = Math.min(p3.z, p4.z);
				d = Math.max(p3.z, p4.z);
			}
			else
				return false;

			return (a < d) && (c < b);
		}

		return false;
	}

	private static boolean arePointsOnDifferentSides(Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4)
	{
		Vector3f lineDir = Vector3f.sub(p2, p1);
		Vector3f lineNormal = new Vector3f(-lineDir.z, lineDir.y, lineDir.x);

		Vector3f to3 = Vector3f.sub(p3, p1);
		Vector3f to4 = Vector3f.sub(p4, p1);

		float dot1 = Vector3f.dot(lineNormal, to3);
		float dot2 = Vector3f.dot(lineNormal, to4);
		return (dot1 * dot2 < 0.0f);
	}

	public static float dist3D(Vector3f a, Vector3f b)
	{
		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double dz = b.z - a.z;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public static float dist3D(Vertex a, Vertex b)
	{
		int dx = b.getCurrentX() - a.getCurrentX();
		int dy = b.getCurrentY() - a.getCurrentY();
		int dz = b.getCurrentZ() - a.getCurrentZ();
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * @return unit vector pointing from a to b
	 */
	public static Vector3f getUnitVector(Vector3f a, Vector3f b)
	{
		return Vector3f.sub(b, a).normalize();
	}
}
