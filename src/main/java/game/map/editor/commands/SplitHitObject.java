package game.map.editor.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import common.commands.AbstractCommand;
import game.map.editor.MapEditor;
import game.map.hit.Collider;
import game.map.hit.HitObject;
import game.map.hit.Zone;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import util.identity.IdentityArrayList;
import util.identity.IdentityHashSet;

public abstract class SplitHitObject<T extends HitObject> extends AbstractCommand
{
	private final T newObject;
	private final IdentityHashSet<TriangleBatch> oldBatches;
	private final HashMap<TriangleBatch, IdentityArrayList<Triangle>> oldBatchLists;
	private final HashMap<TriangleBatch, IdentityArrayList<Triangle>> newBatchLists;

	public static class SplitCollider extends SplitHitObject<Collider>
	{
		public SplitCollider(List<Triangle> splitTriangles)
		{
			super(splitTriangles, "Split Collider");
		}

		@Override
		protected Collider createObject(TriangleBatch batch)
		{
			return Collider.create(batch, "Split Collider");
		}
	}

	public static class SplitZone extends SplitHitObject<Zone>
	{
		public SplitZone(List<Triangle> splitTriangles)
		{
			super(splitTriangles, "Split Zone");
		}

		@Override
		protected Zone createObject(TriangleBatch batch)
		{
			return Zone.create(batch, "Split Zone");
		}
	}

	public SplitHitObject(List<Triangle> splitTriangles, String name)
	{
		super(name);

		oldBatches = new IdentityHashSet<>(splitTriangles.size());
		oldBatchLists = new HashMap<>();
		newBatchLists = new HashMap<>();

		for (Triangle t : splitTriangles)
			oldBatches.add(t.parentBatch);

		for (TriangleBatch batch : oldBatches) {
			List<Triangle> newTriangles = new ArrayList<>(batch.triangles);
			newTriangles.removeAll(splitTriangles);

			oldBatchLists.put(batch, new IdentityArrayList<>(batch.triangles));
			newBatchLists.put(batch, new IdentityArrayList<>(newTriangles));
		}

		// copy the triangles
		IdentityArrayList<Triangle> newTriangles = new IdentityArrayList<>(splitTriangles.size());
		for (Triangle t : splitTriangles)
			newTriangles.add(t.deepCopy());

		// fuse identical vertices
		HashMap<Vertex, Vertex> vertexMap = new HashMap<>();
		for (Triangle t : newTriangles) {
			for (int i = 0; i < 3; i++)
				vertexMap.put(t.vert[i], t.vert[i]);
		}
		for (Triangle t : newTriangles) {
			for (int i = 0; i < 3; i++)
				t.vert[i] = vertexMap.get(t.vert[i]);
		}

		TriangleBatch batch = new TriangleBatch(null);
		batch.triangles = newTriangles;
		newObject = createObject(batch);
	}

	protected abstract T createObject(TriangleBatch batch);

	@Override
	public void exec()
	{
		super.exec();

		MapEditor editor = MapEditor.instance();
		editor.map.create(newObject);
		editor.selectionManager.createObject(newObject);

		for (TriangleBatch batch : oldBatches)
			batch.triangles = newBatchLists.get(batch);
		newObject.updateMeshHierarchy();
	}

	@Override
	public void undo()
	{
		super.undo();

		MapEditor editor = MapEditor.instance();
		editor.map.remove(newObject);
		editor.selectionManager.deleteObject(newObject);

		for (TriangleBatch batch : oldBatches) {
			batch.triangles = oldBatchLists.get(batch);
			batch.parentMesh.parentObject.dirtyAABB = true;
		}
	}
}
