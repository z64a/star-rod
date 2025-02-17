package game.map.editor.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import common.commands.AbstractCommand;
import game.map.editor.MapEditor;
import game.map.mesh.TexturedMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import util.identity.IdentityArrayList;
import util.identity.IdentityHashSet;

public class SplitModel extends AbstractCommand
{
	private final Model newModel;
	private final IdentityHashSet<TriangleBatch> oldBatches;
	private final HashMap<TriangleBatch, IdentityArrayList<Triangle>> oldBatchLists;
	private final HashMap<TriangleBatch, IdentityArrayList<Triangle>> newBatchLists;

	public SplitModel(List<Triangle> splitTriangles)
	{
		super("Split Model");

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

		newModel = Model.createBareModel();
		newModel.setName("Split Model");
		newModel.getMesh().displayListModel.addElement(batch);

		newModel.updateMeshHierarchy();
		newModel.dirtyAABB = true;
		newModel.updateTransformHierarchy();
	}

	@Override
	public void exec()
	{
		super.exec();

		MapEditor editor = MapEditor.instance();
		editor.map.create(newModel);
		editor.selectionManager.createObject(newModel);

		for (TriangleBatch batch : oldBatches) {
			batch.triangles = newBatchLists.get(batch);
			TexturedMesh mesh = (TexturedMesh) batch.parentMesh;
			mesh.displayListModel.setDirty();
			mesh.parentObject.dirtyAABB = true;
		}
	}

	@Override
	public void undo()
	{
		super.undo();

		MapEditor editor = MapEditor.instance();
		editor.map.remove(newModel);
		editor.selectionManager.deleteObject(newModel);

		for (TriangleBatch batch : oldBatches) {
			batch.triangles = oldBatchLists.get(batch);
			TexturedMesh mesh = (TexturedMesh) batch.parentMesh;
			mesh.displayListModel.setDirty();
			mesh.parentObject.dirtyAABB = true;
		}
	}
}
