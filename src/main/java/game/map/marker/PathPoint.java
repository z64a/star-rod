package game.map.marker;

import java.util.List;

import game.map.editor.selection.SelectablePoint;

public class PathPoint extends SelectablePoint
{
	public final PathData path;
	public transient boolean degenerate;

	/** The name code uses for it, if it has one. */
	String name = null;

	/** The points it leads to, or null for the point after it. See PathData.successors. */
	List<PathPoint> next = null;

	public PathPoint(PathData path, int x, int y, int z)
	{
		super(3.0f);
		this.path = path;
		setX(x);
		setY(y);
		setZ(z);
	}

	public PathPoint(PathPoint other)
	{
		super(3.0f);
		this.path = other.path;
		setX(other.getX());
		setY(other.getY());
		setZ(other.getZ());
	}

	public PathPoint deepCopy()
	{
		return new PathPoint(this);
	}

	public String getName()
	{
		return name;
	}

	@Override
	public String toString()
	{
		String position = String.format("Position = (%d, %d, %d)", getX(), getY(), getZ());
		return (name == null) ? position : name + ": " + position;
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
