package game.map.editor.selection;

import game.map.editor.geometry.Vector3f;

import game.map.Axis;
import game.map.BoundingBox;
import game.map.MutablePoint;
import game.map.PointListBackup;
import game.map.ReversibleTransform;
import game.map.editor.commands.AbstractCommand;
import game.map.shape.TransformMatrix;
import util.identity.IdentityHashSet;

/**
 * Wraps a {@link MutablePoint} as a {@link Selectable} object.
 */
public class SelectablePoint implements Selectable
{
	public final MutablePoint point;
	private boolean selected;
	public boolean hidden;

	public final float sizeScale;

	public SelectablePoint(float sizeScale)
	{
		this(new MutablePoint(0, 0, 0), sizeScale);
	}

	public SelectablePoint(MutablePoint point, float sizeScale)
	{
		this.sizeScale = sizeScale;
		this.point = point;
		selected = false;
	}

	public Vector3f getPosition()
	{ return new Vector3f(point.getX(), point.getY(), point.getZ()); }

	public void set(int axis, int val)
	{
		point.set(axis, val);
	}

	public void setX(int val)
	{
		point.setX(val);
	}

	public void setY(int val)
	{
		point.setY(val);
	}

	public void setZ(int val)
	{
		point.setZ(val);
	}

	public int get(int axis)
	{
		return point.get(axis);
	}

	public int getX()
	{ return point.getX(); }

	public int getY()
	{ return point.getY(); }

	public int getZ()
	{ return point.getZ(); }

	public void round(int spacing)
	{
		point.roundTemp(spacing);
	}

	@Override
	public void addTo(BoundingBox selectionAABB)
	{
		selectionAABB.encompass(point.getX(), point.getY(), point.getZ());
	}

	@Override
	public boolean transforms()
	{
		return true;
	}

	@Override
	public boolean isTransforming()
	{ return point.isTransforming(); }

	@Override
	public void startTransformation()
	{
		point.startTransform();
	}

	@Override
	public void endTransformation()
	{
		point.endTransform();
	}

	@Override
	public void recalculateAABB()
	{
		// no bounding box
	}

	@Override
	public boolean allowRotation(Axis axis)
	{
		return true;
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		positions.add(point);
	}

	@Override
	public ReversibleTransform createTransformer(TransformMatrix m)
	{
		return new PointListBackup(point.getBackup());
	}

	@Override
	public void setSelected(boolean val)
	{ selected = val; }

	@Override
	public boolean isSelected()
	{ return selected; }

	@Override
	public int hashCode()
	{
		return point.hashCode();
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
		SelectablePoint other = (SelectablePoint) obj;
		return point.equals(other.point);
	}

	public static class SetPointCoord extends AbstractCommand
	{
		private final int oldValue;
		private final int newValue;

		private final SelectablePoint point;
		private final int axis;

		public SetPointCoord(String name, SelectablePoint point, int axis, int val)
		{
			super(name);

			this.point = point;
			this.axis = axis;

			switch (axis) {
				case 0:
					oldValue = point.getX();
					break;
				case 1:
					oldValue = point.getY();
					break;
				case 2:
					oldValue = point.getZ();
					break;
				default:
					throw new IllegalStateException("Invalid axis value " + axis);
			}

			newValue = val;
		}

		@Override
		public boolean shouldExec()
		{
			return newValue != oldValue;
		}

		@Override
		public void exec()
		{
			super.exec();

			switch (axis) {
				case 0:
					point.setX(newValue);
					break;
				case 1:
					point.setY(newValue);
					break;
				case 2:
					point.setZ(newValue);
					break;
			}
		}

		@Override
		public void undo()
		{
			super.undo();

			switch (axis) {
				case 0:
					point.setX(oldValue);
					break;
				case 1:
					point.setY(oldValue);
					break;
				case 2:
					point.setZ(oldValue);
					break;
			}
		}
	}

	public static class SetPointPosition extends AbstractCommand
	{
		private final int oldX, oldY, oldZ;
		private final int newX, newY, newZ;

		private final SelectablePoint point;

		public SetPointPosition(String name, SelectablePoint point, int x, int y, int z)
		{
			super(name);

			this.point = point;
			oldX = point.getX();
			oldY = point.getY();
			oldZ = point.getZ();
			newX = x;
			newY = y;
			newZ = z;
		}

		@Override
		public boolean shouldExec()
		{
			return (newX != oldX) || (newY != oldY) || (newZ != oldZ);
		}

		@Override
		public void exec()
		{
			super.exec();

			point.setX(newX);
			point.setY(newY);
			point.setZ(newZ);
		}

		@Override
		public void undo()
		{
			super.undo();

			point.setX(oldX);
			point.setY(oldY);
			point.setZ(oldZ);
		}
	}
}
