package game.map.editor.commands;

import java.util.ArrayList;
import java.util.List;

import common.commands.AbstractCommand;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;

public class SeparateVertices extends AbstractCommand
{
	private final List<Triangle> triangles;
	private final List<Vertex> oldVertices;
	private final List<Vertex> newVertices;

	public SeparateVertices(List<Triangle> triangles)
	{
		super("Separate Vertices");

		this.triangles = triangles;
		oldVertices = new ArrayList<>(triangles.size() * 3);
		newVertices = new ArrayList<>(triangles.size() * 3);

		for (Triangle t : triangles) {
			for (int i = 0; i < 3; i++) {
				oldVertices.add(t.vert[i]);
			}
		}

		for (Triangle t : triangles) {
			for (Vertex v : t.vert) {
				Vertex v2 = v.deepCopy();
				v2.parentMesh = t.parentBatch.parentMesh;
				newVertices.add(v2);
			}
		}
	}

	@Override
	public void exec()
	{
		super.exec();

		for (int i = 0; i < triangles.size(); i++) {
			Triangle t = triangles.get(i);

			for (int j = 0; j < 3; j++) {
				Vertex v = newVertices.get(3 * i + j);
				t.vert[j] = v;
			}
		}
	}

	@Override
	public void undo()
	{
		super.undo();

		for (int i = 0; i < triangles.size(); i++) {
			Triangle t = triangles.get(i);

			for (int j = 0; j < 3; j++) {
				Vertex v = oldVertices.get(3 * i + j);
				t.vert[j] = v;
			}
		}
	}
}
