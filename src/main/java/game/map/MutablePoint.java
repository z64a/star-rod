package game.map;

import java.util.Objects;

import game.map.editor.geometry.Vector3f;

import game.map.editor.commands.AbstractCommand;

public class MutablePoint
{
	private int x, y, z;
	private transient int tempx, tempy, tempz;
	private transient boolean transforming = false;

	public transient long lastModified = -1;

	/**
	 * For serialization purposes only!
	 */
	public MutablePoint()
	{}

	public MutablePoint(float x, float y, float z)
	{
		setPosition((int) x, (int) y, (int) z);
	}

	public MutablePoint(int x, int y, int z)
	{
		setPosition(x, y, z);
	}

	public MutablePoint(Vector3f v)
	{
		setPosition(v.x, v.y, v.z);
	}

	public void setPosition(float x, float y, float z)
	{
		setPosition((int) x, (int) y, (int) z);
	}

	public void setPosition(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public void setPosition(Vector3f v)
	{
		setPosition(v.x, v.y, v.z);
	}

	public void setPosition(MutablePoint p)
	{
		setPosition(p.getX(), p.getY(), p.getZ());
	}

	public void startTransform()
	{
		transforming = true;
		tempx = x;
		tempy = y;
		tempz = z;
	}

	public void endTransform()
	{
		transforming = false;
		//	x = tempx;
		//	y = tempy;
		//	z = tempz;
	}

	public boolean isTransforming()
	{ return transforming; }

	public void roundTemp(int dg)
	{
		tempx = dg * Math.round(x / (float) dg);
		tempy = dg * Math.round(y / (float) dg);
		tempz = dg * Math.round(z / (float) dg);
	}

	public void setTempTranslation(Vector3f translation)
	{
		setTempTranslation((int) translation.x, (int) translation.y, (int) translation.z);
	}

	public void setTempTranslation(int dx, int dy, int dz)
	{
		tempx = x + dx;
		tempy = y + dy;
		tempz = z + dz;
	}

	public void setTempScale(Vector3f origin, Vector3f scale)
	{
		tempx = (int) (origin.x + scale.x * (x - origin.x));
		tempy = (int) (origin.y + scale.y * (y - origin.y));
		tempz = (int) (origin.z + scale.z * (z - origin.z));
	}

	public void setTempPosition(float x, float y, float z)
	{
		setTempPosition((int) x, (int) y, (int) z);
	}

	public void setTempPosition(int x, int y, int z)
	{
		tempx = x;
		tempy = y;
		tempz = z;
	}

	public int get(int axis)
	{
		switch (axis) {
			case 0:
				return getX();
			case 1:
				return getY();
			case 2:
				return getZ();
			default:
				throw new IllegalArgumentException("Invalid axis value " + axis);
		}
	}

	public int getX()
	{ return transforming ? tempx : x; }

	public int getY()
	{ return transforming ? tempy : y; }

	public int getZ()
	{ return transforming ? tempz : z; }

	public int getBaseX()
	{ return x; }

	public int getBaseY()
	{ return y; }

	public int getBaseZ()
	{ return z; }

	public int getTempX()
	{ return tempx; }

	public int getTempY()
	{ return tempy; }

	public int getTempZ()
	{ return tempz; }

	public void set(int axis, int val)
	{
		switch (axis) {
			case 0:
				setX(val);
				break;
			case 1:
				setY(val);
				break;
			case 2:
				setZ(val);
				break;
			default:
				throw new IllegalArgumentException("Invalid axis value " + axis);
		}
	}

	public void setX(int val)
	{ x = val; }

	public void setY(int val)
	{ y = val; }

	public void setZ(int val)
	{ z = val; }

	public void setTempX(int val)
	{ tempx = val; }

	public void setTempY(int val)
	{ tempy = val; }

	public void setTempZ(int val)
	{ tempz = val; }

	public Vector3f getVector()
	{
		if (transforming)
			return new Vector3f(tempx, tempy, tempz);
		else
			return new Vector3f(x, y, z);
	}

	public MutablePoint deepCopy()
	{
		return new MutablePoint(x, y, z);
	}

	public PointBackup getBackup()
	{ return new PointBackup(this); }

	public static class PointBackup
	{
		public final MutablePoint pos;
		public final int oldx, oldy, oldz;
		public final int newx, newy, newz;

		private PointBackup(MutablePoint p)
		{
			pos = p;
			oldx = p.x;
			oldy = p.y;
			oldz = p.z;
			newx = p.tempx;
			newy = p.tempy;
			newz = p.tempz;
		}
	}

	public static final class SetPosition extends AbstractCommand
	{
		private MutablePoint point;
		private final Vector3f oldPos;
		private final Vector3f newPos;

		public SetPosition(MutablePoint point, Vector3f pos)
		{
			super("Set Position");
			this.point = point;
			oldPos = point.getVector();
			newPos = pos;
		}

		@Override
		public boolean shouldExec()
		{
			return !newPos.equals(oldPos);
		}

		@Override
		public void exec()
		{
			super.exec();
			point.setX((int) newPos.x);
			point.setY((int) newPos.y);
			point.setZ((int) newPos.z);
		}

		@Override
		public void undo()
		{
			super.undo();
			point.setX((int) oldPos.x);
			point.setY((int) oldPos.y);
			point.setZ((int) oldPos.z);
		}
	}

	@Override
	public String toString()
	{
		return "(" + x + ", " + y + ", " + z + ")";
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
		MutablePoint other = (MutablePoint) obj;
		if (x != other.x)
			return false;
		if (y != other.y)
			return false;
		if (z != other.z)
			return false;
		return true;
	}
}
