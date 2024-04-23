package renderer.buffers;

import renderer.shaders.RenderState;

public final class BufferLine
{
	protected final float thickness;
	protected int i, j;

	protected BufferLine(BufferVertex vi, BufferVertex vj)
	{
		this.thickness = RenderState.getLineWidth();
		this.i = vi.getIndex();
		this.j = vj.getIndex();
	}

	@Override
	public String toString()
	{
		return String.format("%3d --> %-3d  %f", i, j, thickness);
	}
}
