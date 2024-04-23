package game.map.shape;

import game.map.editor.geometry.GeometryUtils;
import game.map.editor.geometry.Vector3f;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import util.MathUtil;

//TODO
public class SynchronizedUV
{
	private final UV uv;
	private final Vertex v;
	private final Triangle t;
	private Vector3f triangleNormal;

	public SynchronizedUV(Vertex v, Triangle t)
	{
		this.uv = v.uv;
		this.v = v;
		this.t = t;

		triangleNormal = t.getNormalSafe();

		int i = 0;
		for (Vertex u : t.vert) {
			if (u == v)
				break;
			i++;
		}

		Vertex prev = t.vert[Math.floorMod(i - 1, 3)];
		Vertex next = t.vert[(i + 1) % 3];

		float dAB = GeometryUtils.dist3D(t.vert[i], prev);
		float dAC = GeometryUtils.dist3D(t.vert[i], next);

		float uAB = GeometryUtils.dist3D(t.vert[i].uv.toVector(), prev.uv.toVector());
		float uAC = GeometryUtils.dist3D(t.vert[i].uv.toVector(), next.uv.toVector());

		if (MathUtil.nearlyZero(dAB) || MathUtil.nearlyZero(uAB)) {

		}
	}

}
