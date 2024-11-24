package game.map.editor.geometry;

import java.util.Objects;

import common.Vector3f;
import game.map.mesh.Vertex;

public class GUVertex
{
	public int x, y, z;

	public GUTriangle triangle;
	public GUHalfEdge halfEdge;
	public GUVertex prev, next;

	public boolean isReflex;
	public boolean isConvex;
	public boolean isEar;

	public GUVertex(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public GUVertex(Vector3f v)
	{
		this.x = Math.round(v.x);
		this.y = Math.round(v.y);
		this.z = Math.round(v.z);
	}

	public GUVertex(GUVertex sv)
	{
		this(sv.x, sv.y, sv.z);
	}

	public GUVertex(Vertex v)
	{
		this(v.getCurrentX(), v.getCurrentY(), v.getCurrentZ());
	}

	public Vertex getVertex()
	{
		return new Vertex(x, y, z);
	}

	@Override
	public String toString()
	{
		return x + " " + y + " " + z;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y, z);
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
		GUVertex other = (GUVertex) obj;
		if (x != other.x)
			return false;
		if (y != other.y)
			return false;
		if (z != other.z)
			return false;
		return true;
	}
}
