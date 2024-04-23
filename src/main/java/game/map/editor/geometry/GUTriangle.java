package game.map.editor.geometry;

import game.map.editor.geometry.Vector3f;

public class GUTriangle
{
	public final GUVertex[] vert = new GUVertex[3];

	public GUHalfEdge halfEdge;

	public GUTriangle(GUVertex v1, GUVertex v2, GUVertex v3)
	{
		this.vert[0] = v1;
		this.vert[1] = v2;
		this.vert[2] = v3;
	}

	public GUTriangle(Vector3f a, Vector3f b, Vector3f c)
	{
		this.vert[0] = new GUVertex(a);
		this.vert[1] = new GUVertex(b);
		this.vert[2] = new GUVertex(c);
	}

	public GUTriangle(GUHalfEdge halfEdge)
	{
		this.halfEdge = halfEdge;
	}

	public void flip()
	{
		GUVertex temp = vert[0];
		vert[0] = vert[1];
		vert[1] = temp;
	}

	public void makeCCW()
	{
		if ((vert[2].x - vert[0].x) * (vert[1].z - vert[0].z) > (vert[1].x - vert[0].x) * (vert[2].z - vert[0].z)) {
			GUVertex temp = vert[0];
			vert[0] = vert[2];
			vert[2] = temp;
		}
	}

	public boolean circumcircleContains(GUVertex other)
	{
		int m11 = vert[0].x - other.x;
		int m12 = vert[1].x - other.x;
		int m13 = vert[2].x - other.x;
		int m21 = vert[0].z - other.z;
		int m22 = vert[1].z - other.z;
		int m23 = vert[2].z - other.z;
		long m31 = (vert[0].x * vert[0].x - other.x * other.x) + (vert[0].z * vert[0].z - other.z * other.z);
		long m32 = (vert[1].x * vert[1].x - other.x * other.x) + (vert[1].z * vert[1].z - other.z * other.z);
		long m33 = (vert[2].x * vert[2].x - other.x * other.x) + (vert[2].z * vert[2].z - other.z * other.z);

		long c1 = m11 * (m22 * m33 - m23 * m32);
		long c2 = m21 * (m12 * m33 - m32 * m13);
		long c3 = m31 * (m12 * m23 - m13 * m22);

		return (c1 - c2 + c3) > 0;
	}

	public boolean contains(int px, int pz)
	{
		float denominator = (vert[1].z - vert[2].z) * (vert[0].x - vert[2].x) + (vert[2].x - vert[1].x) * (vert[0].z - vert[2].z);
		float a = ((vert[1].z - vert[2].z) * (px - vert[2].x) + (vert[2].x - vert[1].x) * (pz - vert[2].z)) / denominator;
		float b = ((vert[2].z - vert[0].z) * (px - vert[2].x) + (vert[0].x - vert[2].x) * (pz - vert[2].z)) / denominator;
		float c = 1.0f - a - b;
		return (a > 0.0f && a < 1.0f && b > 0.0f && b < 1.0f && c > 0.0f && c < 1.0f);
	}

	public boolean isClockwise()
	{
		return 0.0f >= (vert[0].x * vert[1].z) + (vert[2].x * vert[0].z) + (vert[1].x * vert[2].z) - (vert[0].x * vert[2].z) - (vert[2].x * vert[1].z)
			- (vert[1].x * vert[0].z);
	}
}
