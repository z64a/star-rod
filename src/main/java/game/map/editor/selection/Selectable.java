package game.map.editor.selection;

import game.map.Axis;
import game.map.BoundingBox;
import game.map.MutableAngle;
import game.map.MutablePoint;
import game.map.ReversibleTransform;
import game.map.shape.TransformMatrix;
import util.identity.IdentityHashSet;

public interface Selectable
{
	// how should this object modify the selection box?
	public void addTo(BoundingBox selectionAABB);

	public boolean transforms();

	public boolean isTransforming();

	public void startTransformation();

	public void endTransformation();

	public void recalculateAABB();

	public boolean allowRotation(Axis axis);

	public void addPoints(IdentityHashSet<MutablePoint> positions);

	public default void addAngles(IdentityHashSet<MutableAngle> angles)
	{}

	public ReversibleTransform createTransformer(TransformMatrix m);

	public void setSelected(boolean val);

	public boolean isSelected();
}
