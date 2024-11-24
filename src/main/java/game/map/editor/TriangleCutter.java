package game.map.editor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import common.Vector3f;
import game.map.MapObject;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.CommandBatch;
import game.map.editor.commands.CreateObject;
import game.map.editor.geometry.GeometryUtils;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.mesh.AbstractMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.MathUtil;
import util.identity.IdentityArrayList;

public class TriangleCutter
{
	private final Vector3f planeNormal;
	private final Vector3f planePoint;

	public TriangleCutter(Vector3f planePoint, Vector3f planeNormal, List<MapObject> selectedObjects)
	{
		this.planeNormal = planeNormal;
		this.planePoint = planePoint;

		CommandBatch commands = new CommandBatch("Cut Triangles");

		for (MapObject obj : selectedObjects) {
			if (obj.hasMesh()) {
				AbstractMesh mesh = obj.getMesh();

				for (TriangleBatch batch : mesh.getBatches())
					cutBatch(obj, commands, batch);
			}
		}

		MapEditor.execute(commands);
	}

	private void cutBatch(MapObject obj, CommandBatch commands, TriangleBatch batch)
	{
		IdentityArrayList<Triangle> positive = new IdentityArrayList<>();
		IdentityArrayList<Triangle> negative = new IdentityArrayList<>();

		for (Triangle t : batch.triangles) {
			IntersectionResult result = intersectsPlane(t);
			List<Triangle> cutTris = new LinkedList<>();

			if (result.positive.size() == 3 || result.negative.size() == 3 || result.zero.size() > 1) {
				cutTris.add(t); // no need to cut
			}
			else if (result.zero.isEmpty()) {
				cutTris.addAll(cutInto3(result));
			}
			else if (result.zero.size() == 1) {
				if (result.positive.size() == 2 || result.negative.size() == 2)
					cutTris.add(t); // no need to cut
				else
					cutTris.addAll(cutInto2(result));
			}
			else
				throw new IllegalStateException(String.format(
					"Cut resulted in triangle with pos: %d, neg: %d, zero: %d",
					result.positive.size(), result.negative.size(), result.zero.size()));

			// fix normals
			Vector3f triNorm = t.getNormalSafe();
			for (Triangle cut : cutTris) {
				Vector3f cutNorm = cut.getNormalSafe();
				if (Vector3f.dot(triNorm, cutNorm) < 0)
					cut.flipNormal();

				float cutSign = Math.signum(distFromPlane(cut.getCenter()));
				if (cutSign > 0.0f)
					positive.add(cut);
				else
					negative.add(cut);
			}
		}

		for (Triangle t : negative)
			t.parentBatch = batch;

		TriangleBatch cutBatch = new TriangleBatch(null);

		for (Triangle t : positive) {
			cutBatch.triangles.add(t.deepCopy());
			t.parentBatch = cutBatch;
		}

		// nothing was cut
		if (negative.size() < 1)
			return;

		MapObject cutObject = getCutObject(obj, cutBatch);

		commands.addCommand(new CreateObject(cutObject));
		commands.addCommand(new ReplaceTriangles(batch, negative));
	}

	private List<Triangle> cutInto2(IntersectionResult intResult)
	{
		List<Triangle> cutTris = new ArrayList<>(2);

		assert (intResult.positive.size() == 1);
		assert (intResult.negative.size() == 1);
		assert (intResult.zero.size() == 1);

		Vertex A = intResult.zero.get(0);
		Vertex B = intResult.positive.get(0);
		Vertex C = intResult.negative.get(0);

		Vector3f intBC = intersection(C.getCurrentPos(), B.getCurrentPos());

		if (intBC == null) {
			System.out.println("cut2 NULL");
			return cutTris;
		}

		Vertex X = new Vertex(intBC);

		interpVertexColor(X, C, B);
		interpTexCoords(X, C, B);

		cutTris.add(new Triangle(A, B, X));
		cutTris.add(new Triangle(A, C, X));

		return cutTris;
	}

	private List<Triangle> cutInto3(IntersectionResult intResult)
	{
		List<Vertex> isolatedList;
		List<Vertex> pairedList;
		List<Triangle> cutTris = new ArrayList<>(3);

		if (intResult.positive.size() > intResult.negative.size()) {
			isolatedList = intResult.negative;
			pairedList = intResult.positive;
		}
		else {
			isolatedList = intResult.positive;
			pairedList = intResult.negative;
		}

		assert (isolatedList.size() == 1);
		assert (pairedList.size() == 2);

		Vertex A = isolatedList.get(0);
		Vertex B = pairedList.get(0);
		Vertex C = pairedList.get(1);

		if (Math.signum(distFromPlane(A.getCurrentPos())) > 0.0f) {
			Vector3f intAB = intersection(B.getCurrentPos(), A.getCurrentPos());
			Vector3f intAC = intersection(C.getCurrentPos(), A.getCurrentPos());

			if (intAB == null || intAC == null) {
				System.out.println("cut3 NULL > 0");
				return cutTris;
			}

			Vertex X = new Vertex(intAB);
			Vertex Y = new Vertex(intAC);
			interpVertexColor(X, B, A);
			interpVertexColor(Y, C, A);
			interpTexCoords(X, B, A);
			interpTexCoords(Y, C, A);

			cutTris.add(new Triangle(A, X, Y));
			cutTris.add(new Triangle(Y, X, B));
			cutTris.add(new Triangle(B, C, Y));
		}
		else {
			Vector3f intAB = intersection(A.getCurrentPos(), B.getCurrentPos());
			Vector3f intAC = intersection(A.getCurrentPos(), C.getCurrentPos());

			if (intAB == null || intAC == null) {
				System.out.println("cut3 NULL <= 0");
				return cutTris;
			}

			Vertex X = new Vertex(intAB);
			Vertex Y = new Vertex(intAC);
			interpVertexColor(X, B, A);
			interpVertexColor(Y, C, A);
			interpTexCoords(X, B, A);
			interpTexCoords(Y, C, A);

			cutTris.add(new Triangle(A, X, Y));
			cutTris.add(new Triangle(Y, X, B));
			cutTris.add(new Triangle(B, C, Y));
		}

		return cutTris;
	}

	// assumes x,A,B colinear with A <= x <= B. clamped to [0,1]
	private float getInterpAlpha(Vertex x, Vertex A, Vertex B)
	{
		float dAB = GeometryUtils.dist3D(A.getCurrentPos(), B.getCurrentPos());
		float dAx = GeometryUtils.dist3D(A.getCurrentPos(), x.getCurrentPos());
		if (dAB < 1e-4)
			return 0.0f;
		float alpha = dAx / dAB;
		assert (alpha >= 0.0f);
		return Math.min(Math.max(alpha, 0.0f), 1.0f);
	}

	private void interpTexCoords(Vertex x, Vertex A, Vertex B)
	{
		float alpha = getInterpAlpha(x, A, B);
		int u = Math.round((1.0f - alpha) * A.uv.getU() + alpha * B.uv.getU());
		int v = Math.round((1.0f - alpha) * A.uv.getV() + alpha * B.uv.getV());
		x.uv = new UV(u, v);
	}

	private void interpVertexColor(Vertex x, Vertex A, Vertex B)
	{
		float alpha = getInterpAlpha(x, A, B);
		x.r = Math.round((1.0f - alpha) * A.r + alpha * B.r);
		x.g = Math.round((1.0f - alpha) * A.g + alpha * B.g);
		x.b = Math.round((1.0f - alpha) * A.b + alpha * B.b);
		x.a = Math.round((1.0f - alpha) * A.a + alpha * B.a);
	}

	public static class ReplaceTriangles extends AbstractCommand
	{
		TriangleBatch batch;
		IdentityArrayList<Triangle> oldTriangles;
		IdentityArrayList<Triangle> newTriangles;

		public ReplaceTriangles(TriangleBatch batch, List<Triangle> triangles)
		{
			super("Replace Triangles");

			this.batch = batch;
			oldTriangles = new IdentityArrayList<>(batch.triangles);
			newTriangles = new IdentityArrayList<>(triangles);
		}

		@Override
		public void exec()
		{
			super.exec();
			batch.triangles = newTriangles;
		}

		@Override
		public void undo()
		{
			super.undo();
			batch.triangles = oldTriangles;
		}
	}

	/**
	 * @return The lone {@code Vertex} separated from the other two by the plane,
	 * if one exists. Otherwise, {@code null}.
	 */
	private IntersectionResult intersectsPlane(Triangle t)
	{
		float[] signs = new float[3];
		signs[0] = Math.signum(distFromPlane(t.vert[0].getCurrentPos()));
		signs[1] = Math.signum(distFromPlane(t.vert[1].getCurrentPos()));
		signs[2] = Math.signum(distFromPlane(t.vert[2].getCurrentPos()));

		IntersectionResult result = new IntersectionResult();
		for (int i = 0; i < 3; i++) {
			float sgn = signs[i];
			if (sgn > 0)
				result.positive.add(t.vert[i]);
			else if (sgn < 0)
				result.negative.add(t.vert[i]);
			else
				result.zero.add(t.vert[i]);
		}

		return result;
	}

	private static class IntersectionResult
	{
		List<Vertex> positive = new ArrayList<>(3);
		List<Vertex> negative = new ArrayList<>(3);
		List<Vertex> zero = new ArrayList<>(3);
	}

	private Vector3f intersection(Vector3f a, Vector3f b)
	{
		Vector3f AB = Vector3f.sub(b, a);

		if (AB.length() < MathUtil.SMALL_NUMBER)
			return null;

		Vector3f uAB = Vector3f.getNormalized(AB);

		float cos = Vector3f.dot(uAB, planeNormal);

		// parallel to plane
		if (Math.abs(cos) < MathUtil.SMALL_NUMBER)
			return null;

		Vector3f AP = Vector3f.sub(planePoint, a);

		float proj = Vector3f.dot(AP, planeNormal);

		// lies within the plane
		if (proj < 1e-4)
			return null;

		float d = (proj / cos);

		Vector3f result = new Vector3f();
		result.x = a.x + uAB.x * d;
		result.y = a.y + uAB.y * d;
		result.z = a.z + uAB.z * d;

		return result;
	}

	private float distFromPlane(Vector3f a)
	{
		Vector3f delta = Vector3f.sub(a, planePoint);
		return Vector3f.dot(delta, planeNormal);
	}

	private MapObject getCutObject(MapObject original, TriangleBatch batch)
	{
		switch (original.getObjectType()) {
			default:
			case MODEL:
				Model mdl = Model.create(batch, "DrawnModel");
				if (original.hasMesh()) // should never be false
					mdl.getMesh().setTexture(((Model) original).getMesh().texture);
				return mdl;
			case COLLIDER:
				return Collider.create(batch, "DrawnCollider");
			case ZONE:
				return Zone.create(batch, "DrawnZone");
		}
	}
}
