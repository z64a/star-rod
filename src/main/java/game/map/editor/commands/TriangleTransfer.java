package game.map.editor.commands;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import common.commands.AbstractCommand;

import game.map.editor.selection.SelectionManager;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TransformMatrix;
import game.map.shape.TriangleBatch;
import util.Logger;

/** Preserves world positions and shared vertices when changing a triangle's mesh. */
public final class TriangleTransfer
{
	private final TriangleBatch batch;
	private final List<Triangle> triangles;
	private final List<TriangleBatch> oldParents = new ArrayList<>();
	private final List<Vertex> oldVertices = new ArrayList<>();
	private final List<Vertex> newVertices = new ArrayList<>();
	private boolean copiedVertices;
	private boolean valid = true;

	public TriangleTransfer(TriangleBatch batch, List<Triangle> triangles)
	{
		this.batch = batch;
		this.triangles = triangles;
		for (Triangle t : triangles) {
			oldParents.add(t.parentBatch);
			for (Vertex v : t.vert) {
				oldVertices.add(v);
				copiedVertices |= v.parentMesh != batch.parentMesh;
			}
		}
		if (!copiedVertices) {
			newVertices.addAll(oldVertices);
			return;
		}

		Model model = batch.parentMesh.parentObject instanceof Model ? (Model) batch.parentMesh.parentObject : null;
		boolean transformed = model != null && (model.hasTransformMatrix.get() || model.recievesTransform);
		TransformMatrix inverse = null;
		if (transformed) {
			inverse = model.cumulativeTransformMatrix.getInverse();
			TransformMatrix product = TransformMatrix.multiply(model.cumulativeTransformMatrix, inverse);
			for (int row = 0; row < 4; row++) {
				for (int col = 0; col < 4; col++) {
					double value = product.get(row, col);
					if (!Double.isFinite(value) || Math.abs(value - (row == col ? 1.0 : 0.0)) > 0.00001) {
						Logger.logWarning("Cannot transfer triangles: the destination transform could not be inverted.");
						valid = false;
						return;
					}
				}
			}
		}

		IdentityHashMap<Vertex, Vertex> vertexMap = new IdentityHashMap<>();
		for (Vertex v : oldVertices) {
			if (v.parentMesh == batch.parentMesh) {
				newVertices.add(v);
				continue;
			}
			Vertex replacement = vertexMap.get(v);
			if (replacement == null) {
				// Keep vertices still used by the source mesh independent of the destination.
				replacement = v.deepCopy();
				replacement.parentMesh = batch.parentMesh;
				replacement.useLocal = false;
				replacement.getPosition().setPosition(v.getCurrentPos());
				if (transformed) {
					inverse.forceTransform(replacement.getPosition(), replacement.getLocalPosition());
					replacement.forceTransform(model.cumulativeTransformMatrix);
				}
				else {
					replacement.copyWorldToLocal();
					replacement.useLocal = model != null;
				}
				vertexMap.put(v, replacement);
			}
			newVertices.add(replacement);
		}
	}

	public void exec()
	{
		for (int i = 0; i < triangles.size(); i++) {
			Triangle t = triangles.get(i);
			t.setParent(batch);
			for (int j = 0; j < 3; j++)
				t.vert[j] = newVertices.get(3 * i + j);
		}
	}

	public void undo()
	{
		for (int i = 0; i < triangles.size(); i++) {
			Triangle t = triangles.get(i);
			t.setParent(oldParents.get(i));
			for (int j = 0; j < 3; j++)
				t.vert[j] = oldVertices.get(3 * i + j);
		}
	}

	public boolean isValid()
	{
		return valid;
	}

	public boolean copiedVertices()
	{
		return copiedVertices;
	}

	public AbstractCommand selectionUpdate(SelectionManager manager)
	{
		return manager.getVertexSelectionUpdate(triangles, oldVertices, newVertices);
	}
}
