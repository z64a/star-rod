package renderer.buffers;

public final class BufferLine
{
	protected int i, j;

	protected BufferLine(BufferVertex vi, BufferVertex vj)
	{
		this.i = vi.getIndex();
		this.j = vj.getIndex();
	}

	@Override
	public String toString()
	{
		return String.format("%3d --> %-3d", i, j);
	}
}
