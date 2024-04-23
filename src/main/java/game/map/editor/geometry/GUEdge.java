package game.map.editor.geometry;

public class GUEdge implements Comparable<GUEdge>
{
	public GUVertex v1, v2;

	public boolean ignore; // used for delaunay triangulation
	public boolean intersecting;

	public GUEdge(GUVertex v1, GUVertex v2)
	{
		this.v1 = v1;
		this.v2 = v2;
	}

	public double getLength()
	{
		int dx = (v1.x - v2.x);
		int dz = (v1.z - v2.z);
		return Math.sqrt(dx * dx + dz * dz);
	}

	public void flip()
	{
		GUVertex temp = v1;
		v1 = v2;
		v2 = temp;
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
		GUEdge other = (GUEdge) obj;

		if (v1.equals(other.v1) && v2.equals(other.v2))
			return true;

		if (v1.equals(other.v2) && v2.equals(other.v1))
			return true;

		return false;
	}

	// order from largest to smallest
	@Override
	public int compareTo(GUEdge other)
	{
		return (int) (other.getLength() - getLength());
	}
}
