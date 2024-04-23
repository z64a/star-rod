package game.map.shape;

// 16.16 fixed point numbers and operations on them
// they go from -32768.0 to 32767.99998474121 in increments of 1.52587890625E-5
public final class Fixed implements Comparable<Fixed>
{
	//	private static final DecimalFormat FORMAT = new DecimalFormat("0.0#####");
	// 0.0000152587890625

	public static final Fixed MIN = new Fixed(0x80000000);
	public static final Fixed MAX = new Fixed(0x7FFFFFFF);
	public static final Fixed STEP = new Fixed(0x00000001);
	private final int v;

	public static void main(String[] args)
	{
		Fixed a = new Fixed((short) 0, (short) 1);
		//	Fixed b = new Fixed((short)0, (short)2);
		Fixed b = new Fixed(0x7FFF0000);
		Fixed c = new Fixed(0x7FFFFFFF);
		Fixed d = new Fixed(0x80000000);
		Fixed e = new Fixed(0x8000FFFF);
		System.out.println(a.toDouble());
		System.out.println(b.toDouble());
		System.out.println(c.toDouble());
		System.out.println(d.toDouble());
		System.out.println(e.toDouble());
	}

	private Fixed(int v)
	{
		this.v = v;
	}

	public Fixed(float f)
	{
		v = (int) (f * 65536.0f);
	}

	public Fixed(double d)
	{
		v = (int) (d * 65536.0);
	}

	public Fixed(short whole, short frac)
	{
		v = (whole << 16) | (frac & 0xFFFF);
	}

	public short getWholePart()
	{ return (short) (v >> 16); }

	public short getFracPart()
	{ return (short) v; }

	@Override
	public String toString()
	{
		return String.format("%08X", v);
		//return FORMAT.format(toDouble());
	}

	public float toFloat()
	{
		return v / 65536.0f;
	}

	public double toDouble()
	{
		return v / 65536.0;
	}

	public static Fixed[][] fromMatrix(double[][] mat)
	{
		Fixed[][] fmat = new Fixed[mat.length][];
		for (int i = 0; i < mat.length; i++) {
			fmat[i] = new Fixed[mat[i].length];
			for (int j = 0; j < mat[i].length; j++)
				fmat[i][j] = new Fixed(mat[i][j]);
		}
		return fmat;
	}

	public static double[][] toMatrix(Fixed[][] fmat)
	{
		double[][] mat = new double[fmat.length][];
		for (int i = 0; i < fmat.length; i++) {
			mat[i] = new double[fmat[i].length];
			for (int j = 0; j < fmat[i].length; j++)
				mat[i][j] = fmat[i][j].toDouble();
		}
		return mat;
	}

	public static Fixed neg(Fixed a)
	{
		return new Fixed(-a.v);
	}

	// round towards inf
	public static Fixed round(Fixed a)
	{
		int u = a.v;
		if ((u & 0x80000000) != 0)
			u -= 0x8000;
		else
			u += 0x8000;

		int trunc = (u >> 16) << 16;

		return new Fixed(trunc);
	}

	// round toward -inf
	public static Fixed floor(Fixed a)
	{
		return new Fixed((a.v >> 16) << 16);
	}

	// round toward +inf
	public static Fixed ceil(Fixed a)
	{
		int u = a.v + 0xFFFF;
		int trunc = (u >> 16) << 16;
		return new Fixed(trunc);
	}

	public static Fixed add(Fixed a, Fixed b)
	{
		return new Fixed(a.v + b.v);
	}

	public static Fixed sub(Fixed a, Fixed b)
	{
		return new Fixed(a.v - b.v);
	}

	public static Fixed mul(Fixed a, Fixed b)
	{
		long prod = (long) a.v * (long) b.v;
		return new Fixed((int) (prod >> 16));
	}

	public static Fixed div(Fixed a, Fixed b)
	{
		long num = (long) a.v << 16L;
		long div = b.v;
		return new Fixed((int) (num / div));
	}

	public static Fixed sqrt(Fixed a)
	{
		return new Fixed(Math.sqrt(a.toDouble()));
	}

	public static Fixed sin(Fixed a)
	{
		return new Fixed(Math.sin(a.toDouble()));
	}

	public static Fixed sinDeg(Fixed a)
	{
		return new Fixed(Math.sin(Math.toRadians(a.toDouble())));
	}

	public static Fixed cos(Fixed a)
	{
		return new Fixed(Math.cos(a.toDouble()));
	}

	public static Fixed cosDeg(Fixed a)
	{
		return new Fixed(Math.cos(Math.toRadians(a.toDouble())));
	}

	@Override
	public int compareTo(Fixed other)
	{
		return v - other.v;
	}

	@Override
	public int hashCode()
	{
		return v;
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
		Fixed other = (Fixed) obj;
		return v == other.v;
	}
}
