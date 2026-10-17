package game.map.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import game.map.editor.GeometryTestSupport;
import game.map.editor.ui.info.DisplayListPanel.AddTriangles;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;

class MoveTrianglesTest
{
	@Test
	void transferPreservesWorldPositionSourceSharingAndOriginalOrderOnUndo()
	{
		Model source = Model.createBareModel(), target = Model.createBareModel();
		Vertex shared = new Vertex(30, 0, 0);
		Triangle first = triangle(shared), moving = triangle(shared), last = triangle(shared);
		TriangleBatch from = GeometryTestSupport.batch(source, first, moving, last);
		Triangle existing = triangle(new Vertex(0, 0, 0));
		TriangleBatch to = GeometryTestSupport.batch(target, existing);
		target.hasTransformMatrix.set(true);
		target.cumulativeTransformMatrix = game.map.shape.TransformMatrix.identity();
		target.cumulativeTransformMatrix.translate(10, 0, 0);
		MoveTriangles command = new MoveTriangles(to, GeometryTestSupport.selectTriangles(List.of(moving)));
		assertTrue(command.shouldExec());
		command.exec();
		assertSame(to, moving.parentBatch);
		assertNotSame(shared, moving.vert[0]);
		assertEquals(30, moving.vert[0].getCurrentX());
		assertEquals(20, moving.vert[0].getLocalPosition().getX());
		assertSame(source.getMesh(), shared.parentMesh);
		assertSame(shared, first.vert[0]);
		command.undo();
		assertSame(moving, from.triangles.get(1));
		assertSame(last, from.triangles.get(2));
		assertSame(shared, moving.vert[0]);
		assertSame(from, moving.parentBatch);
		assertEquals(1, to.triangles.size());
		assertSame(existing, to.triangles.get(0));
		command.exec();
		assertEquals(2, to.triangles.size());
	}

	@Test
	void nonInvertibleDestinationRejectsMoveWithoutMutation()
	{
		Model target = Model.createBareModel();
		target.hasTransformMatrix.set(true);
		target.cumulativeTransformMatrix = game.map.shape.TransformMatrix.identity();
		target.cumulativeTransformMatrix.scale(0, 1, 1);
		Triangle triangle = triangle(new Vertex(30, 0, 0));
		TriangleBatch from = GeometryTestSupport.batch(Model.createBareModel(), triangle);
		TriangleBatch to = GeometryTestSupport.batch(target);
		MoveTriangles command = new MoveTriangles(to, GeometryTestSupport.selectTriangles(List.of(triangle)));
		assertFalse(command.shouldExec());
		assertSame(from, triangle.parentBatch);
		assertTrue(to.triangles.isEmpty());
	}

	@Test
	void addingPrimitiveToModelAssignsVerticesToDestinationAndUndoRestoresParents()
	{
		Model target = Model.createBareModel();
		Triangle triangle = triangle(new Vertex(30, 0, 0));
		Vertex original = triangle.vert[0];
		TriangleBatch to = GeometryTestSupport.batch(target);
		AddTriangles transfer = new AddTriangles(to, List.of(triangle));
		assertTrue(transfer.shouldExec());
		transfer.exec();
		assertEquals(1, to.triangles.size());
		assertSame(target.getMesh(), triangle.vert[0].parentMesh);
		assertTrue(triangle.vert[0].useLocal);
		assertEquals(30, triangle.vert[0].getCurrentX());
		transfer.undo();
		assertTrue(to.triangles.isEmpty());
		assertNull(triangle.parentBatch);
		assertSame(original, triangle.vert[0]);
	}

	@Test
	void cleanupMergeRestoresTriangleParentsOnUndo()
	{
		Model model = Model.createBareModel();
		Triangle triangle = triangle(new Vertex(30, 0, 0));
		TriangleBatch from = GeometryTestSupport.batch(model, triangle);
		TriangleBatch to = GeometryTestSupport.batch(model);
		Vertex vertex = triangle.vert[0];
		AddTriangles command = new AddTriangles(to, from.triangles);
		command.exec();
		assertSame(to, triangle.parentBatch);
		assertSame(vertex, triangle.vert[0]);
		command.undo();
		assertSame(from, triangle.parentBatch);
		assertTrue(to.triangles.isEmpty());
		assertSame(triangle, from.triangles.get(0));
	}

	private static Triangle triangle(Vertex shared)
	{
		return new Triangle(shared, new Vertex(40, 0, 0), new Vertex(30, 10, 0));
	}
}
