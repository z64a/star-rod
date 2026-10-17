package game.map.editor.commands;

import java.util.ArrayList;
import java.util.List;

import common.commands.AbstractCommand;

import game.map.editor.selection.SelectionManager;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import util.identity.IdentityHashSet;

abstract class ModifyVertices extends AbstractCommand
{
	protected final List<Triangle> triangles;
	private final List<Vertex> oldVertices;
	private List<Vertex> newVertices;
	private final SelectionManager selectionManager;
	private final IdentityHashSet<Vertex> selectedVertices;
	private AbstractCommand selectionChange;
	private boolean hasChanges;

	protected ModifyVertices(String name, SelectionManager selectionManager)
	{
		super(name);
		this.selectionManager = selectionManager;
		triangles = selectionManager.getTrianglesForVertexOperation();
		oldVertices = new ArrayList<>(triangles.size() * 3);
		for (Triangle t : triangles)
			for (Vertex v : t.vert)
				oldVertices.add(v);

		if (selectionManager.getSelectionMode() == SelectionMode.VERTEX) {
			selectedVertices = new IdentityHashSet<>();
			for (Vertex v : selectionManager.getVerticesFromSelection())
				selectedVertices.add(v);
		}
		else
			selectedVertices = null;
	}

	protected boolean isVertexSelected(Vertex v)
	{
		return selectedVertices == null || selectedVertices.contains(v);
	}

	protected void setNewVertices(List<Vertex> vertices)
	{
		newVertices = vertices;
		for (int i = 0; i < oldVertices.size(); i++)
			hasChanges |= oldVertices.get(i) != newVertices.get(i);

		if (hasChanges) {
			selectionChange = selectionManager.getVertexSelectionUpdate(triangles, oldVertices, newVertices);
			selectionChange.silence();
		}
	}

	@Override
	public boolean shouldExec()
	{
		return hasChanges;
	}

	@Override
	public void exec()
	{
		super.exec();
		apply(newVertices);
		selectionChange.exec();
	}

	@Override
	public void undo()
	{
		super.undo();
		apply(oldVertices);
		selectionChange.undo();
	}

	private void apply(List<Vertex> vertices)
	{
		for (int i = 0; i < triangles.size(); i++)
			for (int j = 0; j < 3; j++)
				triangles.get(i).vert[j] = vertices.get(3 * i + j);
	}
}
