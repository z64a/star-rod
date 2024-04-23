package game.map.editor.render;

public class Color4d
{
	public int r, g, b, a;

	public Color4d()
	{}

	public Color4d(int r, int g, int b, int a)
	{
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
	}

	public Color4d(Color4d other)
	{
		this.r = other.r;
		this.g = other.g;
		this.b = other.b;
		this.a = other.a;
	}
}
