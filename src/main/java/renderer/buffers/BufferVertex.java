package renderer.buffers;

import game.map.editor.render.Color4f;
import game.map.editor.render.PresetColor;
import renderer.shaders.RenderState;

public final class BufferVertex
{
	private int index;
	protected float x;
	protected float y;
	protected float z;
	protected float u, v;
	protected float r, g, b, a;
	protected float aux0, aux1;

	protected BufferVertex(int index)
	{
		this.index = index;

		Color4f color = RenderState.getColor();
		r = color.r;
		g = color.g;
		b = color.b;
		a = color.a;
	}

	public int getIndex()
	{ return index; }

	protected void setIndex(int newIndex)
	{ index = newIndex; }

	public BufferVertex setPosition(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}

	public BufferVertex setUV(float u, float v)
	{
		this.u = u;
		this.v = v;
		return this;
	}

	public BufferVertex setAux(int index, float value)
	{
		if (index == 0)
			this.aux0 = value;
		else if (index == 1)
			this.aux1 = value;
		return this;
	}

	protected BufferVertex setPointSize(float size)
	{
		aux0 = size;
		return this;
	}

	public BufferVertex setColor(int r, int g, int b)
	{
		return setColor(r / 255.0f, g / 255.0f, b / 255.0f, 1.0f);
	}

	public BufferVertex setColor(int r, int g, int b, int a)
	{
		return setColor(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
	}

	public BufferVertex setColor(float r, float g, float b)
	{
		return setColor(r, g, b, 1.0f);
	}

	public BufferVertex setColor(PresetColor color)
	{
		return setColor(color.r, color.g, color.b, 1.0f);
	}

	public BufferVertex setColor(float r, float g, float b, float a)
	{
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
		return this;
	}

	@Override
	public String toString()
	{
		String indexString = String.format("[%X]", index);
		return String.format("%6s %5.1f %5.1f %5.1f : %5.4f %5.4f : %5.1f %5.1f %5.1f %5.1f : %5f %5f", indexString, x, y, z, u, v, r, g, b, a, aux0,
			aux1);
	}
}
