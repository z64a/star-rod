package game.map.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import game.map.editor.GeometryTestSupport;
import game.map.editor.selection.Selection;
import game.map.mesh.TexturedMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;

class GeometryCopyTest
{
	@Test
	void copyMeshPreservesSharedVerticesAcrossBatchesAndDoubleSidedFlag()
	{
		Model model = new Model();
		Vertex shared = new Vertex(1, 2, 3);
		Triangle a = new Triangle(shared, new Vertex(10, 0, 0), new Vertex(0, 10, 0));
		Triangle b = new Triangle(shared, new Vertex(-10, 0, 0), new Vertex(0, -10, 0));
		a.doubleSided = true;
		GeometryTestSupport.batch(model, a);
		GeometryTestSupport.batch(model, b);
		TexturedMesh copy = model.getMesh().deepCopy();
		Triangle ca = copy.getBatches().get(0).triangles.get(0);
		Triangle cb = copy.getBatches().get(1).triangles.get(0);
		assertSame(ca.vert[0], cb.vert[0]);
		assertNotSame(shared, ca.vert[0]);
		assertTrue(ca.doubleSided);
		assertSame(copy, ca.vert[0].parentMesh);
		assertSame(model.getMesh(), shared.parentMesh);
	}

	@Test
	void cloneSelectedTrianglesKeepsSharedEdgesAndUndoRestoresOriginals()
	{
		Vertex shared = new Vertex(0, 0, 0);
		Triangle a = new Triangle(shared, new Vertex(10, 0, 0), new Vertex(0, 10, 0));
		Triangle b = new Triangle(shared, a.vert[1], new Vertex(0, -10, 0));
		a.doubleSided = true;
		TriangleBatch batch = GeometryTestSupport.batch(new Model(), a, b);
		Selection<Triangle> selection = new Selection<>(Triangle.class, null);
		selection.addAndSelect(List.of(a, b));
		CloneTriangles command = new CloneTriangles(selection);
		command.exec();
		Triangle ca = selection.selectableList.get(0), cb = selection.selectableList.get(1);
		assertSame(ca.vert[0], cb.vert[0]);
		assertSame(ca.vert[1], cb.vert[1]);
		assertNotSame(shared, ca.vert[0]);
		assertTrue(ca.doubleSided);
		assertEquals(4, batch.triangles.size());
		command.undo();
		assertEquals(List.of(a, b), selection.selectableList);
		assertEquals(2, batch.triangles.size());
	}
}
