package game.map.editor.commands;

import java.util.List;

import common.commands.AbstractCommand;
import game.map.editor.MapEditor;
import game.map.hit.Collider;
import game.map.hit.HitObject;
import game.map.hit.Zone;
import game.map.shape.TriangleBatch;

public abstract class JoinHitObjects<T extends HitObject> extends AbstractCommand
{
	private List<T> objs;
	private final TriangleBatch oldBatch;
	private final TriangleBatch newBatch;

	public static class JoinColliders extends JoinHitObjects<Collider>
	{
		public JoinColliders(List<Collider> colliders)
		{
			super(colliders, "Join Colliders");
		}
	}

	public static class JoinZones extends JoinHitObjects<Zone>
	{
		public JoinZones(List<Zone> colliders)
		{
			super(colliders, "Join Zones");
		}
	}

	private JoinHitObjects(List<T> objs, String name)
	{
		super(name);
		this.objs = objs;

		oldBatch = objs.get(0).mesh.batch;
		newBatch = new TriangleBatch(null);

		for (HitObject hit : objs)
			newBatch.triangles.addAll(hit.mesh.batch.triangles);
	}

	@Override
	public void exec()
	{
		super.exec();

		MapEditor editor = MapEditor.instance();

		HitObject first = objs.get(0);
		first.mesh.batch = newBatch;
		first.dirtyAABB = true;

		for (int i = 1; i < objs.size(); i++) {
			HitObject obj = objs.get(i);
			editor.map.remove(obj);
			editor.selectionManager.deleteObject(obj);
		}

		first.updateMeshHierarchy();
	}

	@Override
	public void undo()
	{
		super.undo();

		MapEditor editor = MapEditor.instance();

		HitObject first = objs.get(0);
		first.mesh.batch = oldBatch;
		first.updateMeshHierarchy();
		first.dirtyAABB = true;

		for (int i = objs.size() - 1; i >= 1; i--) {
			HitObject obj = objs.get(i);
			editor.map.add(obj);
			editor.selectionManager.createObject(obj);
			obj.updateMeshHierarchy();
		}
	}
}
