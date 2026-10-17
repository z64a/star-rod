package game.map.editor.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import game.map.editor.selection.SelectionManager;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;

public class FuseVertices extends ModifyVertices
{
	private static final class FusionKey
	{
		private final TriangleBatch batch;
		private final int x;
		private final int y;
		private final int z;
		private final int u;
		private final int v;
		private final int r;
		private final int g;
		private final int b;
		private final int a;
		private final boolean useLocal;

		private FusionKey(TriangleBatch batch, Vertex vertex)
		{
			this.batch = batch;
			x = vertex.getCurrentX();
			y = vertex.getCurrentY();
			z = vertex.getCurrentZ();
			u = vertex.uv.getU();
			v = vertex.uv.getV();
			r = vertex.r;
			g = vertex.g;
			b = vertex.b;
			a = vertex.a;
			useLocal = vertex.useLocal;
		}

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = System.identityHashCode(batch);
			result = prime * result + x;
			result = prime * result + y;
			result = prime * result + z;
			result = prime * result + u;
			result = prime * result + v;
			result = prime * result + r;
			result = prime * result + g;
			result = prime * result + b;
			result = prime * result + a;
			result = prime * result + (useLocal ? 1 : 0);
			return result;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof FusionKey other))
				return false;

			return batch == other.batch
				&& x == other.x
				&& y == other.y
				&& z == other.z
				&& u == other.u
				&& v == other.v
				&& r == other.r
				&& g == other.g
				&& b == other.b
				&& a == other.a
				&& useLocal == other.useLocal;
		}
	}

	public FuseVertices(SelectionManager selectionManager)
	{
		super("Fuse Vertices", selectionManager);
		List<Vertex> newVertices = new ArrayList<>(triangles.size() * 3);

		HashMap<FusionKey, Vertex> vertexMap = new HashMap<>();

		for (Triangle t : triangles) {
			for (Vertex v : t.vert) {
				if (!isVertexSelected(v)) {
					newVertices.add(v);
					continue;
				}

				FusionKey key = new FusionKey(t.parentBatch, v);
				Vertex fused = vertexMap.putIfAbsent(key, v);
				if (fused == null)
					fused = v;
				newVertices.add(fused);
			}
		}

		setNewVertices(newVertices);
	}
}
