package game.map.editor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import game.map.editor.selection.Selection;
import game.map.editor.selection.SelectionManager;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;

public final class GeometryTestSupport
{
	private GeometryTestSupport()
	{}

	public static TriangleBatch batch(Model model, Triangle ... triangles)
	{
		TriangleBatch batch = new TriangleBatch(model.getMesh());
		model.getMesh().displayListModel.addElement(batch);
		for (Triangle triangle : triangles)
			batch.triangles.add(triangle);
		batch.setParent(model.getMesh());
		return batch;
	}

	public static SelectionManager selectTriangles(List<Triangle> triangles)
	{
		SelectionManager manager = new SelectionManager(null);
		Selection<Triangle> selection = field(manager, "triangleSelection");
		selection.addAndSelect(triangles);
		setField(manager, "selectionMode", SelectionMode.TRIANGLE);
		manager.currentSelection = selection;
		return manager;
	}

	public static SelectionManager selectVertices(List<Vertex> working, Vertex ... selected)
	{
		SelectionManager manager = new SelectionManager(null);
		Selection<Vertex> selection = field(manager, "vertexSelection");
		selection.addAndSelect(List.of(selected));
		setField(manager, "vertexWorkingSet", new ArrayList<>(working));
		setField(manager, "uvWorkingSet", new ArrayList<>(working.stream().map(v -> v.uv).toList()));
		setField(manager, "selectionMode", SelectionMode.VERTEX);
		manager.currentSelection = selection;
		return manager;
	}

	@SuppressWarnings("unchecked")
	public static <T> T field(Object object, String name)
	{
		try {
			Field field = object.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return (T) field.get(object);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	public static void setField(Object object, String name, Object value)
	{
		try {
			Field field = object.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(object, value);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}
}
