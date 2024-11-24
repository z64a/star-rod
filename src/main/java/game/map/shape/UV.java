package game.map.shape;

import java.util.Objects;

import common.Vector3f;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.MutablePoint;
import game.map.PointListBackup;
import game.map.ReversibleTransform;
import game.map.editor.selection.Selectable;
import util.identity.IdentityHashSet;

public class UV implements Selectable
{
	private MutablePoint texCoordinate;
	public transient boolean selected;

	public UV(float u, float v)
	{
		texCoordinate = new MutablePoint(u, v, 0);
		selected = false;
	}

	public UV(int u, int v)
	{
		texCoordinate = new MutablePoint(u, v, 0);
		selected = false;
	}

	public short getU()
	{
		return (short) texCoordinate.getX();
	}

	public short getV()
	{
		return (short) texCoordinate.getY();
	}

	public Vector3f toVector()
	{
		return texCoordinate.getVector();
	}

	public void setPosition(int u, int v)
	{
		if (texCoordinate.isTransforming())
			texCoordinate.setTempPosition(u, v, 0);
	}

	/**
	 * Rounds this UV coordinate to the nearest grid interval.
	 * @param dg
	 */
	public void round(int dg)
	{
		texCoordinate.roundTemp(dg);
	}

	@Override
	public void addTo(BoundingBox aabb)
	{
		aabb.encompass((short) texCoordinate.getX(), (short) texCoordinate.getY(), 0);
	}

	@Override
	public boolean transforms()
	{
		return true;
	}

	@Override
	public boolean isTransforming()
	{
		return texCoordinate.isTransforming();
	}

	@Override
	public void startTransformation()
	{
		texCoordinate.startTransform();
	}

	@Override
	public void endTransformation()
	{
		texCoordinate.endTransform();
	}

	@Override
	public void recalculateAABB()
	{}

	@Override
	public boolean allowRotation(Axis axis)
	{
		return true;
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		positions.add(texCoordinate);
	}

	@Override
	public void setSelected(boolean val)
	{
		selected = val;
	}

	@Override
	public boolean isSelected()
	{
		return selected;
	}

	public UV deepCopy()
	{
		return new UV(getU(), getV());
	}

	@Override
	public ReversibleTransform createTransformer(TransformMatrix m)
	{
		return new PointListBackup(texCoordinate.getBackup());
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(texCoordinate);
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
		UV other = (UV) obj;
		if (!Objects.equals(texCoordinate, other.texCoordinate)) {
			return false;
		}
		return true;
	}
}
