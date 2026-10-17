package game.map.editor.commands;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

import common.commands.AbstractCommand;

import game.map.editor.selection.SelectionManager;
import game.map.editor.ui.info.DisplayListPanel;
import game.map.mesh.AbstractMesh;
import game.map.mesh.Triangle;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import util.identity.IdentityArrayList;
import util.identity.IdentityHashSet;

public final class MoveTriangles extends AbstractCommand
{
	private final TriangleBatch newParent;

	private final List<Triangle> triangles;
	private final IdentityHashMap<TriangleBatch, IdentityArrayList<Triangle>> oldTriangles;
	private final IdentityHashSet<AbstractMesh> meshes;
	private final TriangleTransfer transfer;
	private final AbstractCommand selectionChange;

	public MoveTriangles(TriangleBatch newParent, SelectionManager selectionManager)
	{
		super("Moving Triangles");
		this.newParent = newParent;

		triangles = selectionManager.getTrianglesFromSelection(Model.class);
		meshes = new IdentityHashSet<>();
		meshes.add(newParent.parentMesh);

		Iterator<Triangle> iter = triangles.iterator();
		while (iter.hasNext()) {
			TriangleBatch parent = iter.next().parentBatch;
			if (parent == newParent)
				iter.remove();
		}

		oldTriangles = new IdentityHashMap<>();
		oldTriangles.put(newParent, new IdentityArrayList<>(newParent.triangles));
		for (int i = 0; i < triangles.size(); i++) {
			TriangleBatch parentBatch = triangles.get(i).parentBatch;
			oldTriangles.computeIfAbsent(parentBatch, batch -> new IdentityArrayList<>(batch.triangles));
			meshes.add(parentBatch.parentMesh);
		}
		transfer = new TriangleTransfer(newParent, triangles);
		if (transfer.isValid() && transfer.copiedVertices()) {
			selectionChange = transfer.selectionUpdate(selectionManager);
			selectionChange.silence();
		}
		else
			selectionChange = null;
	}

	@Override
	public boolean shouldExec()
	{
		return triangles.size() > 0 && transfer.isValid();
	}

	@Override
	public void exec()
	{
		super.exec();

		for (int i = 0; i < triangles.size(); i++) {
			Triangle t = triangles.get(i);
			t.parentBatch.triangles.remove(t);
			newParent.triangles.add(t);
		}
		transfer.exec();
		if (selectionChange != null)
			selectionChange.exec();

		for (AbstractMesh mesh : meshes)
			mesh.parentObject.dirtyAABB = true;

		// update the display command descriptions
		DisplayListPanel.repaintCommandList();
	}

	@Override
	public void undo()
	{
		super.undo();

		for (TriangleBatch batch : oldTriangles.keySet())
			batch.triangles = new IdentityArrayList<>(oldTriangles.get(batch));
		transfer.undo();
		if (selectionChange != null)
			selectionChange.undo();

		for (AbstractMesh mesh : meshes)
			mesh.parentObject.dirtyAABB = true;

		// update the display command descriptions
		DisplayListPanel.repaintCommandList();
	}
}
