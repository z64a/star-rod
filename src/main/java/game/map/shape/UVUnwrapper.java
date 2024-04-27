package game.map.shape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import game.map.editor.MapEditor;
import game.map.editor.geometry.Vector3f;
import game.map.editor.selection.Selection;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;

public class UVUnwrapper
{
	List<UnwrapFace> faces = new ArrayList<>();
	HashMap<UnwrapEdge, UnwrapEdge> edges = new HashMap<>();
	HashMap<UnwrapVert, UnwrapVert> verts = new HashMap<>();

	public UVUnwrapper(MapEditor editor, Iterable<Triangle> triangles)
	{
		Vector3f center = new Vector3f();
		for (Triangle t : triangles) {
			faces.add(new UnwrapFace(t));
			center.add(t.getCenter());
		}

		for (UnwrapVert v : verts.keySet()) {
			double dx = v.meshVertex.getCurrentX() - center.x;
			double dy = v.meshVertex.getCurrentY() - center.y;
			double dz = v.meshVertex.getCurrentZ() - center.z;

			double radius = Math.sqrt(dx * dx + dz * dz);
			double azimuth = 180 + Math.toDegrees(Math.atan2(dz, dx));
			double polar = 180 + Math.toDegrees(Math.atan2(radius, dy));

			//	v.v = 8 * azimuth;
			//	v.u = 8 * polar;

			v.u = v.meshVertex.uv.getU() / 32.0;
			v.v = v.meshVertex.uv.getV() / 32.0;
		}

		float kE = 0.01f;
		float kA = 0.00001f;

		int maxIterations = 10000;

		double maxForce;
		double forceTolerance = 1e-9;

		for (int i = 0; i < maxIterations; i++) {
			for (UnwrapVert v : verts.keySet()) {
				v.force[0] = 0;
				v.force[1] = 0;
			}
			maxForce = 0.0f;

			for (UnwrapFace f : faces)
				f.addForces(kA);

			for (UnwrapEdge e : edges.keySet())
				e.addForces(kE);

			for (UnwrapVert v : verts.keySet()) {
				v.u += v.force[0];
				v.v += v.force[1];
				double mag = Math.sqrt(v.force[0] * v.force[0] + v.force[1] * v.force[1]);
				if (mag > maxForce)
					maxForce = mag;
			}

			System.out.printf("Step %d: Max force = %e\r\n", i, maxForce);
			if (maxForce < forceTolerance)
				break;
		}

		Selection<UV> uvList = new Selection<>(UV.class, editor);
		for (UnwrapVert v : verts.keySet())
			uvList.addWithoutSelecting(v.meshVertex.uv);
		uvList.startDirectTransformation();
		for (UnwrapVert v : verts.keySet())
			v.meshVertex.uv.setPosition((short) (16.0 * v.u), (short) (16.0 * v.v));
		uvList.endDirectTransformation();

		/*
		
		ArrayList<Vertex> vertexList = getUniqueVertices(triangleList);
		ArrayList<float[]> forceList = new ArrayList<>(vertexList.size());
		for(int i = 0; i < vertexList.size(); i++)
			forceList.add(new float[] {0, 0});
		
		//
		Selection<UV> uvList = new Selection<>();
		BoundingBox aabb = new BoundingBox();
		for(Vertex v : vertexList)
		{
			uvList.addWithoutSelecting(v.uv);
			aabb.encompass(v);
		}
		Vector3f center = aabb.getCenter();
		uvList.startDirectTransformation();
		
		// initial state from spherical projection
		int[] coords = new int[3];
		
		for(Vertex v : vertexList)
		{
			coords[0] = v.getPosition().getX() - (int)center.x;
			coords[1] = v.getPosition().getY() - (int)center.y;
			coords[2] = v.getPosition().getZ() - (int)center.z;
		
			int radius = (int)Math.sqrt(coords[0]*coords[0] + coords[1]*coords[1]);
			int azimuth = 180 + (int)Math.toDegrees(Math.atan2(coords[1], coords[0]));
			int polar = 180 + (int)Math.toDegrees(Math.atan2(radius, coords[2]));
		
			v.uv.setPosition(8 * azimuth, 8 * polar);
		}
		
		float kE = 1.0f;
		float kA = 1.0f;
		
		int maxIterations = 100;
		
		float maxForce = 0.0f;
		float forceTolerance = 0.0f;
		
		for(int i = 0; i < maxIterations; i++)
		{
			for(UV uv : uvList.list)
			{
				uv.force[0] = 0;
				uv.force[1] = 0;
			}
		
			for(UnwrapEdge e : edgeList)
				e.addForces(kE);
		
			for(UnwrapFace f : faceList)
				f.addForces(kA);
		
			for(UV uv : uvList.list)
			{
				uv.setPosition(uv.getU() + uv.force[0], uv.getV() + uv.force[1]);
			}
		
			if(maxForce < forceTolerance)
				break;
		}
		
		uvList.endDirectTransformation();
		*/
	}

	private class UnwrapFace
	{
		private UnwrapVert v1, v2, v3;
		private final double Aeq;

		private UnwrapFace(Triangle t)
		{
			v1 = new UnwrapVert(t.vert[0]);
			if (verts.containsKey(v1))
				v1 = verts.get(v1);
			else
				verts.put(v1, v1);

			v2 = new UnwrapVert(t.vert[1]);
			if (verts.containsKey(v2))
				v2 = verts.get(v2);
			else
				verts.put(v2, v2);

			v3 = new UnwrapVert(t.vert[2]);
			if (verts.containsKey(v3))
				v3 = verts.get(v3);
			else
				verts.put(v3, v3);

			UnwrapEdge e1 = new UnwrapEdge(v1, v2);
			if (edges.containsKey(e1))
				e1 = edges.get(e1);
			else
				edges.put(e1, e1);

			UnwrapEdge e2 = new UnwrapEdge(v2, v3);
			if (edges.containsKey(e2))
				e2 = edges.get(e2);
			else
				edges.put(e2, e2);

			UnwrapEdge e3 = new UnwrapEdge(v3, v1);
			if (edges.containsKey(e3))
				e3 = edges.get(e3);
			else
				edges.put(e3, e3);

			Aeq = t.getArea();
		}

		public double getProjectedArea()
		{
			double Au = v2.u - v1.u;
			double Av = v2.v - v1.v;

			double Bu = v3.u - v1.u;
			double Bv = v3.v - v1.v;

			double mag = Math.abs(Au * Bv - Av * Bu);
			return mag / 2.0;
		}

		public void addForces(double kA)
		{
			double A = getProjectedArea();
			if (A == 0)
				A = 1;
			double mag = -kA * (A - Aeq);

			double cu = (v1.u + v2.u + v3.u) / 3.0;
			double cv = (v1.v + v2.v + v3.v) / 3.0;

			double du, dv, dl;

			du = (v1.u - cu);
			dv = (v1.v - cv);
			dl = Math.sqrt(du * du + dv * dv);
			v1.force[0] += mag * du / dl;
			v1.force[1] += mag * dv / dl;

			du = (v2.u - cu);
			dv = (v2.v - cv);
			dl = Math.sqrt(du * du + dv * dv);
			v2.force[0] += mag * du / dl;
			v2.force[1] += mag * dv / dl;

			du = (v3.u - cu);
			dv = (v3.v - cv);
			dl = Math.sqrt(du * du + dv * dv);
			v3.force[0] += mag * du / dl;
			v3.force[1] += mag * dv / dl;

			//	System.out.printf("%s Aeq = %f, A = %f, F = %s\r\n", this, Aeq, A, mag);
		}

		@Override
		public String toString()
		{
			return String.format("%s : %s : %s", v1, v2, v3);
		}
	}

	private class UnwrapEdge
	{
		private final UnwrapVert v1, v2;
		private final double Leq;

		private UnwrapEdge(UnwrapVert v1, UnwrapVert v2)
		{
			this.v1 = v1;
			this.v2 = v2;

			Vector3f rel = Vector3f.sub(v1.meshVertex.getCurrentPos(), v2.meshVertex.getCurrentPos());
			Leq = rel.length();
		}

		public void addForces(double kE)
		{
			double du = v2.u - v1.u;
			double dv = v2.v - v1.v;
			double L = Math.sqrt(du * du + dv * dv);
			if (L == 0)
				L = 1;
			double mag = kE * (1 - Leq / L);
			v1.force[0] += du * mag;
			v1.force[1] += dv * mag;
			v2.force[0] += -du * mag;
			v2.force[1] += -dv * mag;
			//	System.out.printf("%s Leq = %f, L = %f, F = (%s, %s)\r\n", this, Leq, L, du * mag, dv * mag);
		}

		@Override
		public int hashCode()
		{
			return v1.hashCode() + v2.hashCode();
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
			UnwrapEdge other = (UnwrapEdge) obj;
			if (v1 == other.v1 && v2 == other.v2)
				return true;
			if (v1 == other.v2 && v2 == other.v1)
				return true;
			return true;
		}

		@Override
		public String toString()
		{
			return String.format("%s --> %s", v1, v2);
		}
	}

	private class UnwrapVert
	{
		final Vertex meshVertex; // corresponding vertex from mesh
		double u, v;
		double[] force;

		private UnwrapVert(Vertex v)
		{
			meshVertex = v;
			force = new double[2];
		}

		@Override
		public int hashCode()
		{
			return meshVertex.hashCode();
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
			UnwrapVert other = (UnwrapVert) obj;
			return meshVertex == other.meshVertex;
		}

		@Override
		public String toString()
		{
			return String.format("(%f, %f)", u, v);
		}
	}
}
