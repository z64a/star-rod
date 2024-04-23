package renderer.buffers;

import java.util.ArrayList;

public class LineBatch implements Comparable<LineBatch>
{
	protected ArrayList<BufferVertex> verts = new ArrayList<>();
	protected ArrayList<BufferLine> lines = new ArrayList<>();
	protected float lineWidth;
	protected int priority;
	protected int startIndex;

	public BufferVertex addVertex()
	{
		BufferVertex vtx = new BufferVertex(verts.size());
		verts.add(vtx);
		return vtx;
	}

	public void add(int i, int j)
	{
		lines.add(new BufferLine(verts.get(i), verts.get(j)));
	}

	public void add(int ... indices)
	{
		for (int i = 1; i < indices.length; i++)
			lines.add(new BufferLine(verts.get(indices[i - 1]), verts.get(indices[i])));
	}

	public void addLoop(int ... indices)
	{
		for (int i = 1; i < indices.length; i++)
			lines.add(new BufferLine(verts.get(indices[i - 1]), verts.get(indices[i])));
		if (indices.length > 2)
			lines.add(new BufferLine(verts.get(indices[indices.length - 1]), verts.get(indices[0])));
	}

	public LineBatch setPriority(int priority)
	{
		this.priority = priority;
		return this;
	}

	public LineBatch setLineWidth(float lineWidth)
	{
		this.lineWidth = lineWidth;
		return this;
	}

	public int getVertexCount()
	{ return verts.size(); }

	public int getLineCount()
	{ return lines.size(); }

	@Override
	public int compareTo(LineBatch o)
	{
		if (o.priority != this.priority)
			return o.priority - this.priority;
		else
			return Float.compare(o.lineWidth, lineWidth);
	}
}
