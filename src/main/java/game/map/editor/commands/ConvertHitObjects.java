package game.map.editor.commands;

import java.util.ArrayList;
import java.util.List;

import game.map.hit.Collider;
import game.map.hit.HitObject;
import game.map.hit.Zone;

public abstract class ConvertHitObjects<T extends HitObject, S extends HitObject> extends AbstractCommand
{
	private final List<T> oldObjects;
	private final List<S> newObjects;

	public static class ConvertColliders extends ConvertHitObjects<Collider, Zone>
	{
		public ConvertColliders(List<Collider> c)
		{
			super(c, "Convert Collider to Zone");
		}

		@Override
		protected Zone convert(Collider c)
		{
			return Zone.create(c.mesh.deepCopy(), c.getName());
		}
	}

	public static class ConvertZones extends ConvertHitObjects<Zone, Collider>
	{
		public ConvertZones(List<Zone> z)
		{
			super(z, "Convert Zone to Collider");
		}

		@Override
		protected Collider convert(Zone z)
		{
			return Collider.create(z.mesh.deepCopy(), z.getName());
		}
	}

	public ConvertHitObjects(List<T> objs, String name)
	{
		super(name);

		oldObjects = new ArrayList<>(objs.size());
		newObjects = new ArrayList<>(objs.size());

		for (T obj : objs) {
			if (obj.hasMesh()) {
				oldObjects.add(obj);
				newObjects.add(convert(obj));
			}
		}
	}

	protected abstract S convert(T obj);

	@Override
	public boolean shouldExec()
	{
		return !newObjects.isEmpty();
	}

	@Override
	public void exec()
	{
		super.exec();

		for (int i = oldObjects.size() - 1; i >= 0; i--) {
			HitObject obj = oldObjects.get(i);
			editor.map.remove(obj);
			editor.selectionManager.deleteObject(obj);
		}

		for (S obj : newObjects) {
			editor.map.create(obj);
			editor.selectionManager.createObject(obj);
		}
	}

	@Override
	public void undo()
	{
		super.undo();

		for (int i = newObjects.size() - 1; i >= 0; i--) {
			HitObject obj = newObjects.get(i);
			editor.map.remove(obj);
			editor.selectionManager.deleteObject(obj);
		}

		for (T obj : oldObjects) {
			editor.map.add(obj);
			editor.selectionManager.createObject(obj);
		}
	}
}
