package game.map.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import game.map.editor.GeometryTestSupport;
import game.map.editor.selection.SelectionManager;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;

class VertexSelectionTest
{
	@Test
	void fuseInVertexModeUpdatesVertexAndUvSelectionsAndUndo()
	{
		Vertex a = new Vertex(0, 0, 0), b = new Vertex(0, 0, 0), unselected = new Vertex(0, 0, 0);
		Triangle t = new Triangle(a, b, unselected);
		GeometryTestSupport.batch(new Model(), t);
		SelectionManager manager = GeometryTestSupport.selectVertices(List.of(a, b, unselected), a, b);
		manager.uvSelection.addAndSelect(List.of(a.uv, b.uv));
		FuseVertices command = new FuseVertices(manager);
		assertTrue(command.shouldExec());
		command.exec();
		assertSame(a, t.vert[1]);
		assertSame(unselected, t.vert[2]);
		assertEquals(List.of(a), manager.getVerticesFromSelection());
		assertEquals(List.of(a.uv), manager.uvSelection.selectableList);
		assertFalse(b.selected);
		command.undo();
		assertSame(b, t.vert[1]);
		assertEquals(2, manager.getVerticesFromSelection().size());
		assertEquals(List.of(a.uv, b.uv), manager.uvSelection.selectableList);
		command.exec();
		assertSame(a, t.vert[1]);
	}

	@Test
	void separateOnlySelectedVerticesAndRestoreSharingOnUndo()
	{
		Vertex shared = new Vertex(0, 0, 0), untouched = new Vertex(10, 0, 0);
		Triangle a = new Triangle(shared, untouched, new Vertex(0, 10, 0));
		Triangle b = new Triangle(shared, untouched, new Vertex(0, -10, 0));
		GeometryTestSupport.batch(new Model(), a, b);
		SelectionManager manager = GeometryTestSupport.selectVertices(List.of(shared, untouched), shared);
		manager.uvSelection.addAndSelect(shared.uv);
		SeparateVertices command = new SeparateVertices(manager);
		command.exec();
		assertNotSame(a.vert[0], b.vert[0]);
		assertSame(untouched, a.vert[1]);
		assertSame(untouched, b.vert[1]);
		assertEquals(2, manager.getVerticesFromSelection().size());
		assertEquals(2, manager.uvSelection.selectableList.size());
		assertFalse(manager.getVertices().stream().anyMatch(v -> v == shared));
		command.undo();
		assertSame(shared, a.vert[0]);
		assertSame(shared, b.vert[0]);
		assertEquals(List.of(shared), manager.getVerticesFromSelection());
	}

	@Test
	void separatingOneTriangleRetainsSharedVertexUsedElsewhere()
	{
		Vertex shared = new Vertex(0, 0, 0);
		Triangle a = new Triangle(shared, new Vertex(10, 0, 0), new Vertex(0, 10, 0));
		Triangle b = new Triangle(shared, new Vertex(-10, 0, 0), new Vertex(0, -10, 0));
		GeometryTestSupport.batch(new Model(), a, b);
		SelectionManager manager = GeometryTestSupport.selectTriangles(List.of(a));
		manager.uvSelection.addAndSelect(shared.uv);
		new SeparateVertices(manager).exec();
		assertSame(shared, b.vert[0]);
		assertNotSame(shared, a.vert[0]);
		assertTrue(manager.uvSelection.selectableList.stream().anyMatch(uv -> uv == shared.uv));
		assertTrue(manager.uvSelection.selectableList.stream().anyMatch(uv -> uv == a.vert[0].uv));
	}
}
