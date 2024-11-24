package game.map.editor;

import common.Vector3f;
import game.map.BoundingBox;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.ReversibleTransform;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.shape.TransformMatrix;
import util.identity.IdentityHashSet;

public abstract class PointObject extends EditorObject
{
	public MutablePoint position;

	public PointObject(Vector3f initialPosition, float radius)
	{
		position = new MutablePoint(initialPosition);
	}

	public Vector3f getPosition()
	{
		return position.getVector();
	}

	public abstract float getRadius();

	public static final class SetPosition extends AbstractCommand
	{
		private PointObject obj;
		private final Vector3f oldPos;
		private final Vector3f newPos;

		public SetPosition(PointObject obj, Vector3f pos)
		{
			super("Set Position");
			this.obj = obj;
			oldPos = obj.position.getVector();
			newPos = pos;
		}

		@Override
		public boolean shouldExec()
		{
			return !newPos.equals(oldPos);
		}

		@Override
		public void exec()
		{
			super.exec();
			obj.position.setX((int) newPos.x);
			obj.position.setY((int) newPos.y);
			obj.position.setZ((int) newPos.z);
			obj.recalculateAABB();
			editor.selectionManager.currentSelection.updateAABB();
		}

		@Override
		public void undo()
		{
			super.undo();
			obj.position.setX((int) oldPos.x);
			obj.position.setY((int) oldPos.y);
			obj.position.setZ((int) oldPos.z);
			obj.recalculateAABB();
			editor.selectionManager.currentSelection.updateAABB();
		}
	}

	@Override
	public void addTo(BoundingBox aabb)
	{
		aabb.encompass(AABB);
	}

	@Override
	public boolean isTransforming()
	{
		return position.isTransforming();
	}

	@Override
	public void startTransformation()
	{
		position.startTransform();
	}

	@Override
	public void endTransformation()
	{
		position.endTransform();
		recalculateAABB();
	}

	@Override
	public void recalculateAABB()
	{
		float radius = getRadius();
		AABB.clear();

		AABB.encompass(
			position.getX() - (int) Math.ceil(radius),
			position.getY() - (int) Math.ceil(radius),
			position.getZ() - (int) Math.ceil(radius));

		AABB.encompass(
			position.getX() + (int) Math.ceil(radius),
			position.getY() + (int) Math.ceil(radius),
			position.getZ() + (int) Math.ceil(radius));
	}

	@Override
	public ReversibleTransform createTransformer(TransformMatrix m)
	{
		PointBackup backup = position.getBackup();

		return new ReversibleTransform() {
			@Override
			public void transform()
			{
				backup.pos.setPosition(backup.newx, backup.newy, backup.newz);
				recalculateAABB();
			}

			@Override
			public void revert()
			{
				backup.pos.setPosition(backup.oldx, backup.oldy, backup.oldz);
				recalculateAABB();
			}
		};
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		positions.add(position);
	}

	// ==================================================
	// picking
	// --------------------------------------------------

	@Override
	public PickHit tryPick(PickRay ray)
	{
		PickHit hit = PickRay.getSphereIntersection(ray, position.getX(), position.getY(), position.getZ(), getRadius());
		hit.obj = this;
		return hit;
	}
}
