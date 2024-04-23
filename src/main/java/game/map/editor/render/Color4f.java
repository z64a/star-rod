package game.map.editor.render;

public class Color4f
{
	public float r, g, b, a;

	public Color4f()
	{}

	public Color4f(float r, float g, float b, float a)
	{
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
	}

	public Color4f(Color4f other)
	{
		this.r = other.r;
		this.g = other.g;
		this.b = other.b;
		this.a = other.a;
	}
}
