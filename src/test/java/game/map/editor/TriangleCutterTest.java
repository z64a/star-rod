package game.map.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import common.Vector3f;

import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;

class TriangleCutterTest
{
	@Test
	void cutPreservesSharedEdgesPropertiesAndUndo() throws Exception
	{
		Model model = square();
		model.pannerID.set(3);
		TriangleBatch original = model.getMesh().getBatches().get(0);
		Triangle first = original.triangles.get(0), second = original.triangles.get(1);
		first.doubleSided = true;
		TriangleCutter cutter = new TriangleCutter(new Vector3f(), new Vector3f(1, 0, 0));
		TriangleCutter.ObjectSplit split = cutter.buildObjectSplit(model);
		assertNotNull(split);
		assertEquals(3, ((Model) split.cutObject).pannerID.get());
		assertSame(first, original.triangles.get(0));
		assertSame(original, first.parentBatch);
		TriangleCutter.ReplaceBatches command = new TriangleCutter.ReplaceBatches(split.replacements);
		command.exec();
		TriangleBatch cut = split.cutObject.getMesh().getBatches().get(0);
		assertEquals(3, original.triangles.size());
		assertEquals(3, cut.triangles.size());
		assertEquals(400, area(original) + area(cut), 0.001);
		Vertex shared = centerVertex(original);
		assertTrue(original.triangles.stream().filter(t -> contains(t, shared)).count() > 1);
		assertNotSame(shared, centerVertex(cut));
		assertTrue(cut.triangles.stream().anyMatch(t -> t.doubleSided));
		for (Triangle t : cut.triangles) {
			assertSame(cut, t.parentBatch);
			for (Vertex v : t.vert)
				assertSame(cut.parentMesh, v.parentMesh);
		}
		command.undo();
		assertEquals(2, original.triangles.size());
		assertSame(first, original.triangles.get(0));
		assertSame(second, original.triangles.get(1));
		assertSame(original, second.parentBatch);
		command.exec();
		assertEquals(3, original.triangles.size());
	}

	@Test
	void planeOutsideObjectDoesNotChangeGeometryOrParents() throws Exception
	{
		Model model = square();
		TriangleBatch batch = model.getMesh().getBatches().get(0);
		Triangle triangle = batch.triangles.get(0);
		TriangleCutter cutter = new TriangleCutter(new Vector3f(-100, 0, 0), new Vector3f(1, 0, 0));
		assertNull(cutter.buildObjectSplit(model));
		assertEquals(2, batch.triangles.size());
		assertSame(batch, triangle.parentBatch);
		assertSame(triangle, batch.triangles.get(0));
	}

	private static Model square()
	{
		Model model = Model.createBareModel();
		model.setName("Square");
		Vertex a = new Vertex(-10, -10, 0), b = new Vertex(10, -10, 0);
		Vertex c = new Vertex(10, 10, 0), d = new Vertex(-10, 10, 0);
		GeometryTestSupport.batch(model, new Triangle(a, b, c), new Triangle(a, c, d));
		return model;
	}

	private static double area(TriangleBatch batch)
	{
		return batch.triangles.stream().mapToDouble(Triangle::getArea).sum();
	}

	private static Vertex centerVertex(TriangleBatch batch)
	{
		for (Triangle t : batch.triangles)
			for (Vertex v : t.vert)
				if (v.getCurrentX() == 0 && v.getCurrentY() == 0)
					return v;
		throw new AssertionError("Missing intersection on shared edge");
	}

	private static boolean contains(Triangle triangle, Vertex vertex)
	{
		return triangle.vert[0] == vertex || triangle.vert[1] == vertex || triangle.vert[2] == vertex;
	}
}
