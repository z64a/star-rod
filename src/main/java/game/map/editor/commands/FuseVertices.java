package game.map.editor.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;

public class FuseVertices extends AbstractCommand
{
	private List<Triangle> triangles;
	private List<Vertex> oldVertices;
	private List<Vertex> newVertices;

	private static class FusionWrapper
	{
		private final Vertex v;

		public FusionWrapper(Vertex vertex)
		{
			this.v = vertex;
		}

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + v.getCurrentX();
			result = prime * result + v.getCurrentY();
			return prime * result + v.getCurrentZ();
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null)
				return false;
			if (getClass() != o.getClass())
				return false;
			FusionWrapper other = (FusionWrapper) o;

			return v.getCurrentPos().equals(other.v.getCurrentPos());
		}
	}

	public FuseVertices(List<Triangle> triangles)
	{
		super("Fuse Vertices");

		this.triangles = triangles;
		oldVertices = new ArrayList<>(triangles.size() * 3);
		newVertices = new ArrayList<>(triangles.size() * 3);

		HashMap<FusionWrapper, FusionWrapper> vertexMap = new HashMap<>();
		HashMap<Vertex, TriangleBatch> batchMap = new HashMap<>();

		for (Triangle t : triangles) {
			for (int i = 0; i < 3; i++) {
				vertexMap.put(new FusionWrapper(t.vert[i]), new FusionWrapper(t.vert[i]));
				batchMap.put(t.vert[i], t.parentBatch);
				oldVertices.add(t.vert[i]);
			}
		}

		for (Triangle t : triangles) {
			for (Vertex v : t.vert) {
				// retrieve vertex with the same hashcode
				Vertex v2 = vertexMap.get(new FusionWrapper(v)).v;

				// only fuse vertices among the same triangle batch
				if (t.parentBatch == batchMap.get(v))
					newVertices.add(v2);
				else
					newVertices.add(v);
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
