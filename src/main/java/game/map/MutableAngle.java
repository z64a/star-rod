package game.map;

public class MutableAngle
{
	private double angle;
	public final Axis axis;
	public final boolean clockwise;
	private transient double temp;
	private transient boolean transforming = false;

	public transient long lastModified = -1;

	public MutableAngle(double a, Axis axis, boolean clockwise)
	{
		this.clockwise = clockwise;
		this.axis = axis;
		setAngle(a);
	}

	public MutableAngle(int a, Axis axis, boolean clockwise)
	{
		this.clockwise = clockwise;
		this.axis = axis;
		setAngle((double) a);
	}

	public void setAngle(double a)
	{
		angle = a;
	}

	public void setAngle(int a)
	{
		setAngle((double) a);
	}

	public void setTempAngle(double a)
	{
		temp = a;
	}

	public void startTransform()
	{
		transforming = true;
		temp = angle;
	}

	public void endTransform()
	{
		transforming = false;
	}

	public boolean isTransforming()
	{
		return transforming;
	}

	public double getAngle()
	{
		return transforming ? temp : angle;
	}

	public double getBaseAngle()
	{
		return angle;
	}

	public double getTempAngle()
	{
		return temp;
	}

	public MutableAngle deepCopy()
	{
		return new MutableAngle(angle, axis, clockwise);
	}

	public AngleBackup getBackup()
	{
		return new AngleBackup(this);
	}

	public static class AngleBackup
	{
		public final MutableAngle angle;
		public final double oldAngle;
		public final double newAngle;

		private AngleBackup(MutableAngle a)
		{
			angle = a;
			oldAngle = a.angle;
			newAngle = a.temp;
		}
	}
}
