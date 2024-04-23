package game.map.editor.selection;

import game.map.BoundingBox;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.geometry.Vector3f;
import game.map.mesh.Triangle;
import game.map.shape.UV;

public class PickRay
{
	public static final Vector3f UP = new Vector3f(0, 1, 0);
	public static final Vector3f DOWN = new Vector3f(0, -1, 0);
	public static final Vector3f PosX = new Vector3f(1, 0, 0);
	public static final Vector3f NegX = new Vector3f(-1, 0, 0);
	public static final Vector3f PosZ = new Vector3f(0, 0, 1);
	public static final Vector3f NegZ = new Vector3f(0, 0, -1);

	public static enum Channel
	{
		SELECTION,
		COLLISION
	}

	public final Channel channel;
	public final Vector3f origin;
	public final Vector3f direction;
	private final MapEditViewport pickView;
	private final boolean twoSided;

	public boolean preventSelectionChange;

	public static class PickHit
	{
		public final float dist;
		public final Vector3f point;
		public Vector3f norm = null;
		public Object obj = null;

		public PickHit(PickRay ray)
		{
			this(ray, Float.MAX_VALUE);
		}

		public PickHit(PickRay ray, float dist)
		{
			this.dist = dist;

			if (dist < Float.MAX_VALUE) {
				float hx = ray.origin.x + dist * ray.direction.x;
				float hy = ray.origin.y + dist * ray.direction.y;
				float hz = ray.origin.z + dist * ray.direction.z;
				point = new Vector3f(hx, hy, hz);
			}
			else
				point = null;
		}

		public PickHit(PickRay ray, float dist, Vector3f norm)
		{
			this(ray, dist);
			this.norm = new Vector3f(norm);
		}

		public boolean missed()
		{
			return dist == Float.MAX_VALUE;
		}
	}

	public PickRay(Channel channel, Vector3f start, Vector3f direction)
	{
		this(channel, start, direction, null, true);
	}

	public PickRay(Channel channel, Vector3f start, Vector3f direction, MapEditViewport pickView)
	{
		this(channel, start, direction, pickView, true);
	}

	public PickRay(Channel channel, Vector3f start, Vector3f direction, boolean twoSided)
	{
		this(channel, start, direction, null, twoSided);
	}

	public PickRay(Channel channel, Vector3f start, Vector3f direction, MapEditViewport pickView, boolean twoSided)
	{
		this.channel = channel;
		this.origin = start;
		this.direction = direction;
		this.pickView = pickView;
		this.twoSided = twoSided;
	}

	public static boolean intersects(PickRay ray, BoundingBox aabb)
	{
		return !getIntersection(ray, aabb).missed();
	}

	public static PickHit getIntersection(PickRay ray, BoundingBox aabb)
	{
		if (aabb.isEmpty())
			return new PickHit(ray, Float.MAX_VALUE);

		Vector3f invDir = new Vector3f(1f / ray.direction.x, 1f / ray.direction.y, 1f / ray.direction.z);
		boolean signDirX = invDir.x < 0;
		boolean signDirY = invDir.y < 0;
		boolean signDirZ = invDir.z < 0;
		Vector3f min = aabb.getMin();
		Vector3f max = aabb.getMax();
		Vector3f bbox = signDirX ? max : min;
		float tmin = (bbox.x - ray.origin.x) * invDir.x;
		bbox = signDirX ? min : max;
		float tmax = (bbox.x - ray.origin.x) * invDir.x;
		bbox = signDirY ? max : min;
		float tymin = (bbox.y - ray.origin.y) * invDir.y;
		bbox = signDirY ? min : max;
		float tymax = (bbox.y - ray.origin.y) * invDir.y;

		if ((tmin > tymax) || (tymin > tmax))
			return new PickHit(ray, Float.MAX_VALUE);
		if (tymin > tmin)
			tmin = tymin;
		if (tymax < tmax)
			tmax = tymax;

		bbox = signDirZ ? max : min;
		float tzmin = (bbox.z - ray.origin.z) * invDir.z;
		bbox = signDirZ ? min : max;
		float tzmax = (bbox.z - ray.origin.z) * invDir.z;

		if ((tmin > tzmax) || (tzmin > tmax))
			return new PickHit(ray, Float.MAX_VALUE);
		if (tzmin > tmin)
			tmin = tzmin;
		if (tzmax < tmax)
			tmax = tzmax;
		//	if ((tmin < maxDir) && (tmax > minDir)) {

		return new PickHit(ray, tmin);

		//		return ray.getPointAtDistance(tmin);
		//		}
		//	return null;
	}

	// http://gamedev.stackexchange.com/questions/12360/how-do-you-determine-which-object-surface-the-users-pointing-at-with-lwjgl/12370#12370
	public static PickHit getIntersection(PickRay ray, Triangle t)
	{
		Vector3f vertex1 = t.vert[0].getPosition().getVector();
		Vector3f vertex2 = t.vert[1].getPosition().getVector();
		Vector3f vertex3 = t.vert[2].getPosition().getVector();

		// check the normal first
		Vector3f triNorm = t.getNormalSafe();

		if (!ray.twoSided && !t.doubleSided && Vector3f.dot(ray.direction, triNorm) > 0.0)
			return new PickHit(ray, Float.MAX_VALUE);

		// Compute vectors along two edges of the triangle.
		Vector3f edge1 = Vector3f.sub(vertex2, vertex1);
		Vector3f edge2 = Vector3f.sub(vertex3, vertex1);

		// Compute the determinant.
		Vector3f directionCrossEdge2 = Vector3f.cross(ray.direction, edge2);

		float determinant = Vector3f.dot(directionCrossEdge2, edge1);

		// If the ray and triangle are parallel, there is no collision.
		if (determinant > -.0000001f && determinant < .0000001f)
			return new PickHit(ray, Float.MAX_VALUE);

		float inverseDeterminant = 1.0f / determinant;

		// Calculate the U parameter of the intersection point.
		Vector3f distanceVector = Vector3f.sub(ray.origin, vertex1);

		float triangleU = Vector3f.dot(directionCrossEdge2, distanceVector);
		triangleU *= inverseDeterminant;

		// Make sure the U is inside the triangle.
		if (triangleU < 0 || triangleU > 1)
			return new PickHit(ray, Float.MAX_VALUE);

		// Calculate the V parameter of the intersection point.
		Vector3f distanceCrossEdge1 = Vector3f.cross(distanceVector, edge1);

		float triangleV = Vector3f.dot(ray.direction, distanceCrossEdge1);
		triangleV *= inverseDeterminant;

		// Make sure the V is inside the triangle.
		if (triangleV < 0 || triangleU + triangleV > 1)
			return new PickHit(ray, Float.MAX_VALUE);

		// Get the distance to the face from our ray origin
		float rayDistance = Vector3f.dot(distanceCrossEdge1, edge2);
		rayDistance *= inverseDeterminant;

		// Is the triangle behind us?
		if (rayDistance < 0) {
			rayDistance *= -1;
			return new PickHit(ray, Float.MAX_VALUE);
		}

		return new PickHit(ray, rayDistance, triNorm);
	}

	public static PickHit getIntersection(PickRay ray, Vector3f vertex1, Vector3f vertex2, Vector3f vertex3)
	{
		// Compute vectors along two edges of the triangle.
		Vector3f edge1 = Vector3f.sub(vertex2, vertex1);
		Vector3f edge2 = Vector3f.sub(vertex3, vertex1);

		// Compute the determinant.
		Vector3f directionCrossEdge2 = Vector3f.cross(ray.direction, edge2);

		float determinant = Vector3f.dot(directionCrossEdge2, edge1);
		// If the ray and triangle are parallel, there is no collision.
		if (determinant > -.0000001f && determinant < .0000001f)
			return new PickHit(ray, Float.MAX_VALUE);

		float inverseDeterminant = 1.0f / determinant;

		// Calculate the U parameter of the intersection point.
		Vector3f distanceVector = Vector3f.sub(ray.origin, vertex1);

		float triangleU = Vector3f.dot(directionCrossEdge2, distanceVector);
		triangleU *= inverseDeterminant;

		// Make sure the U is inside the triangle.
		if (triangleU < 0 || triangleU > 1)
			return new PickHit(ray, Float.MAX_VALUE);

		// Calculate the V parameter of the intersection point.
		Vector3f distanceCrossEdge1 = Vector3f.cross(distanceVector, edge1);

		float triangleV = Vector3f.dot(ray.direction, distanceCrossEdge1);
		triangleV *= inverseDeterminant;

		// Make sure the V is inside the triangle.
		if (triangleV < 0 || triangleU + triangleV > 1)
			return new PickHit(ray, Float.MAX_VALUE);

		// Get the distance to the face from our ray origin
		float rayDistance = Vector3f.dot(distanceCrossEdge1, edge2);
		rayDistance *= inverseDeterminant;

		// Is the triangle behind us?
		if (rayDistance < 0)
			return new PickHit(ray, Float.MAX_VALUE);

		Vector3f triNorm = getNormal(vertex1, vertex2, vertex3);
		return new PickHit(ray, rayDistance, triNorm);
	}

	private static Vector3f getNormal(Vector3f vertex1, Vector3f vertex2, Vector3f vertex3)
	{
		float[] normal = new float[3];

		float Ax = vertex2.x - vertex1.x;
		float Ay = vertex2.y - vertex1.y;
		float Az = vertex2.z - vertex1.z;

		float Bx = vertex3.x - vertex1.x;
		float By = vertex3.y - vertex1.y;
		float Bz = vertex3.z - vertex1.z;

		normal[0] = Ay * Bz - Az * By;
		normal[1] = Az * Bx - Ax * Bz;
		normal[2] = Ax * By - Ay * Bx;

		double mag = Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
		if (Math.abs(mag) < 1e-6) // colinear
			return null;

		normal[0] /= mag;
		normal[1] /= mag;
		normal[2] /= mag;

		return new Vector3f(normal[0], normal[1], normal[2]);
	}

	public static PickHit getPointIntersection(PickRay ray, int x, int y, int z, float pointScale)
	{
		if (ray.pickView == null)
			return getSphereIntersection(ray, x, y, z, pointScale * 0.5f);

		float radius = pointScale * (0.22f + (2.0f * ray.pickView.getScaleFactor(x, y, z)));
		float relx = ray.origin.x - x;
		float rely = ray.origin.y - y;
		float relz = ray.origin.z - z;

		switch (ray.pickView.type) {
			case FRONT: // XY
				if (Math.sqrt(relx * relx + rely * rely) < radius)
					return new PickHit(ray, z);
				else
					return new PickHit(ray);

			case TOP: // XZ
				if (Math.sqrt(relx * relx + relz * relz) < radius)
					return new PickHit(ray, y);
				else
					return new PickHit(ray);

			case SIDE: // YZ
				if (Math.sqrt(rely * rely + relz * relz) < radius)
					return new PickHit(ray, x);
				else
					return new PickHit(ray);

			default:
				return getSphereIntersection(
					ray, x, y, z, radius);
		}
	}

	public static PickHit getIntersection(PickRay ray, UV uv)
	{
		if (ray.pickView == null)
			return new PickHit(ray); // invalid

		float radius = 0.22f + (2.0f * ray.pickView.getScaleFactor(uv.getU(), uv.getV(), 2.0f));
		float relx = ray.origin.x - uv.getU();
		float rely = ray.origin.y - uv.getV();

		float dist = (float) Math.sqrt(relx * relx + rely * rely);

		if (dist < radius)
			return new PickHit(ray, dist);
		else
			return new PickHit(ray);
	}

	public static PickHit getSphereIntersection(PickRay ray, float x, float y, float z, float r)
	{
		Vector3f relative = new Vector3f();
		relative.x = ray.origin.x - x;
		relative.y = ray.origin.y - y;
		relative.z = ray.origin.z - z;

		float a = Vector3f.dot(ray.direction, ray.direction);
		float b = 2 * Vector3f.dot(ray.direction, relative);
		float c = Vector3f.dot(relative, relative) - (r * r);

		float discriminant = b * b - 4 * a * c;

		// no solution, ray missed the sphere
		if (discriminant < 0)
			return new PickHit(ray, Float.MAX_VALUE);

		// compute q as described above
		float distSqrt = (float) Math.sqrt(discriminant);
		float q;
		if (b < 0)
			q = (-b - distSqrt) / 2.0f;
		else
			q = (-b + distSqrt) / 2.0f;

		// compute t0 and t1
		float t0 = q / a;
		float t1 = c / q;

		// make sure t0 is smaller than t1
		if (t0 > t1) {
			// if t0 is bigger than t1 swap them around
			float temp = t0;
			t0 = t1;
			t1 = temp;
		}

		// if t1 is less than zero, the object is in the ray's negative direction
		// and consequently the ray misses the sphere
		if (t1 < 0)
			return new PickHit(ray, Float.MAX_VALUE);

		// if t0 is less than zero, the intersection point is at t1
		if (t0 < 0)
			return new PickHit(ray, t1);
		else
			return new PickHit(ray, t0); // else the intersection point is at t0
	}
}
