package game.map.editor.geometry;

public class Vector3f
{
	public float x, y, z;

	public Vector3f()
	{}

	public Vector3f(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vector3f(Vector3f other)
	{
		set(other);
	}

	public void set(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vector3f set(Vector3f other)
	{
		this.x = other.x;
		this.y = other.y;
		this.z = other.z;
		return this;
	}

	public Vector3f add(Vector3f other)
	{
		x += other.x;
		y += other.y;
		z += other.z;
		return this;
	}

	public Vector3f sub(Vector3f other)
	{
		x -= other.x;
		y -= other.y;
		z -= other.z;
		return this;
	}

	public static Vector3f add(Vector3f a, Vector3f b)
	{
		return new Vector3f(
			a.x + b.x,
			a.y + b.y,
			a.z + b.z
		);
	}

	public static Vector3f sub(Vector3f a, Vector3f b)
	{
		return new Vector3f(
			a.x - b.x,
			a.y - b.y,
			a.z - b.z
		);
	}

	public static Vector3f cross(Vector3f a, Vector3f b)
	{
		return new Vector3f(
			(a.y * b.z) - (a.z * b.y),
			(b.x * a.z) - (b.z * a.x),
			(a.x * b.y) - (a.y * b.x)
		);
	}

	public static float dot(Vector3f a, Vector3f b)
	{
		return (a.x * b.x) + (a.y * b.y) + (a.z * b.z);
	}

	public float length()
	{
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	public static Vector3f getNormalized(Vector3f vec)
	{
		return new Vector3f(vec).normalize();
	}

	public Vector3f normalize()
	{
		float len = length();
		if (len == 0) {
			x = 0;
			y = 0;
			z = 0;
		}
		else {
			x /= len;
			y /= len;
			z /= len;
		}
		return this;
	}

	/**
	 * @param vec
	 * @param s
	 * @return new product vector
	 */
	public static Vector3f getScaled(Vector3f vec, float s)
	{
		return new Vector3f(vec.x * s, vec.y * s, vec.z * s);
	}

	/**
	 * Apply a scaling factor to this vector
	 * @param s - scaling factor
	 */
	public Vector3f scale(float s)
	{
		x *= s;
		y *= s;
		z *= s;
		return this;
	}

	/**
	 * Negate this vector
	 */
	public Vector3f negate()
	{
		x = -x;
		y = -y;
		z = -z;
		return this;
	}

	@Override
	public String toString()
	{
		return "(" + x + ", " + y + ", " + z + ")";
	}
}
