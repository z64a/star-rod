package game.texture.editor;

public class Pixel
{
	public int index, r, g, b, a;

	public static Pixel getColorIndexed(byte index)
	{
		Pixel px = new Pixel();
		px.index = index & 0xFF;
		return px;
	}

	public static Pixel getIntensity(byte I, byte A)
	{
		Pixel px = new Pixel();
		px.r = I & 0xFF;
		px.g = px.r;
		px.b = px.r;
		px.a = A & 0xFF;
		return px;
	}

	public static Pixel getRGBA(byte R, byte G, byte B, byte A)
	{
		Pixel px = new Pixel();
		px.r = R & 0xFF;
		px.g = G & 0xFF;
		px.b = B & 0xFF;
		px.a = A & 0xFF;
		return px;
	}

	public Pixel()
	{
		clear();
	}

	public Pixel(Pixel other)
	{
		sample(other);
	}

	public void clear()
	{
		index = 0;
		r = 0;
		g = 0;
		b = 0;
		a = 255;
	}

	public void sample(Pixel other)
	{
		this.index = other.index;
		this.r = other.r;
		this.g = other.g;
		this.b = other.b;
		this.a = other.a;
	}

	public int getARGB()
	{ return (a << 24) | (r << 16) | (g << 8) | b; }

	public boolean equals(Pixel other)
	{
		if (r != other.r)
			return false;
		if (g != other.g)
			return false;
		if (b != other.b)
			return false;
		if (a != other.a)
			return false;
		if (index != other.index)
			return false;
		return true;
	}
}
