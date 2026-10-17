package game.map.editor.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;

class FuseVerticesTest
{
	@Test
	void fusesMatchingVerticesWithinOneBatchAndCanUndo()
	{
		TriangleBatch batch = new TriangleBatch(null);
		Vertex first = new Vertex(1, 2, 3);
		Vertex duplicate = new Vertex(1, 2, 3);
		Triangle firstTriangle = triangle(batch, first, new Vertex(10, 0, 0), new Vertex(11, 0, 0));
		Triangle secondTriangle = triangle(batch, duplicate, new Vertex(12, 0, 0), new Vertex(13, 0, 0));

		FuseVertices command = new FuseVertices(List.of(firstTriangle, secondTriangle));

		assertTrue(command.shouldExec());
		command.exec();
		assertSame(first, secondTriangle.vert[0]);

		command.undo();
		assertSame(duplicate, secondTriangle.vert[0]);
	}

	@Test
	void preservesVerticesWithDifferentRenderAttributes()
	{
		TriangleBatch batch = new TriangleBatch(null);
		List<Vertex> candidates = new ArrayList<>();
		candidates.add(new Vertex(1, 2, 3));

		Vertex differentU = new Vertex(1, 2, 3);
		differentU.uv = new UV(1, 0);
		candidates.add(differentU);

		Vertex differentR = new Vertex(1, 2, 3);
		differentR.r = 1;
		candidates.add(differentR);

		Vertex differentG = new Vertex(1, 2, 3);
		differentG.g = 1;
		candidates.add(differentG);

		Vertex differentB = new Vertex(1, 2, 3);
		differentB.b = 1;
		candidates.add(differentB);

		Vertex differentA = new Vertex(1, 2, 3);
		differentA.a = 1;
		candidates.add(differentA);

		Vertex differentLocalMode = new Vertex(1, 2, 3);
		differentLocalMode.useLocal = true;
		candidates.add(differentLocalMode);

		List<Triangle> triangles = new ArrayList<>();
		for (int i = 0; i < candidates.size(); i++) {
			triangles.add(triangle(batch, candidates.get(i),
				new Vertex(100 + 2 * i, 0, 0), new Vertex(101 + 2 * i, 0, 0)));
		}

		FuseVertices command = new FuseVertices(triangles);

		assertFalse(command.shouldExec());
	}

	@Test
	void doesNotFuseAcrossTriangleBatches()
	{
		TriangleBatch firstBatch = new TriangleBatch(null);
		TriangleBatch secondBatch = new TriangleBatch(null);
		Triangle firstTriangle = triangle(firstBatch, new Vertex(1, 2, 3),
			new Vertex(10, 0, 0), new Vertex(11, 0, 0));
		Triangle secondTriangle = triangle(secondBatch, new Vertex(1, 2, 3),
			new Vertex(12, 0, 0), new Vertex(13, 0, 0));

		FuseVertices command = new FuseVertices(List.of(firstTriangle, secondTriangle));

		assertFalse(command.shouldExec());
	}

	private static Triangle triangle(TriangleBatch batch, Vertex... vertices)
	{
		Triangle triangle = new Triangle(vertices[0], vertices[1], vertices[2]);
		triangle.setParent(batch);
		return triangle;
	}
}
