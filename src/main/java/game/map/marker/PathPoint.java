package game.map.marker;

import game.map.editor.selection.SelectablePoint;

public class PathPoint extends SelectablePoint
{
	public final PathData path;
	public transient boolean degenerate;

	public PathPoint(PathData path, int x, int y, int z)
	{
		super(2.0f);
		this.path = path;
		setX(x);
		setY(y);
		setZ(z);
	}

	public PathPoint(PathPoint other)
	{
		super(2.0f);
		this.path = other.path;
		setX(other.getX());
		setY(other.getY());
		setZ(other.getZ());
	}

	public PathPoint deepCopy()
	{
		return new PathPoint(this);
	}

	@Override
	public String toString()
	{
		return String.format("Position = (%d, %d, %d)", getX(), getY(), getZ());
	}

	// find list position based on reference equality
	public int getListIndex()
	{
		int i = 0;
		for (PathPoint wp : path.points) {
			if (wp == this)
				return i;
			i++;
		}
		return -1;
	}
}
