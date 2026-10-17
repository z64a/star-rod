package game.map.editor.commands;

import java.util.ArrayList;
import java.util.List;

import game.map.editor.selection.SelectionManager;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;

public class SeparateVertices extends ModifyVertices
{
	public SeparateVertices(SelectionManager selectionManager)
	{
		super("Separate Vertices", selectionManager);
		List<Vertex> newVertices = new ArrayList<>(triangles.size() * 3);

		for (Triangle t : triangles) {
			for (Vertex v : t.vert) {
				if (!isVertexSelected(v)) {
					newVertices.add(v);
					continue;
				}
				Vertex v2 = v.deepCopy();
				v2.parentMesh = t.parentBatch.parentMesh;
				newVertices.add(v2);
			}
		}
		setNewVertices(newVertices);
	}
}
