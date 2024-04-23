package game.map.editor.selection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.SwingUtilities;

import game.map.BoundingBox;
import game.map.Map;
import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.EditorMode;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.camera.ViewType;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.CloneObjects;
import game.map.editor.commands.CloneTriangles;
import game.map.editor.commands.CommandBatch;
import game.map.editor.commands.DeleteObjects;
import game.map.editor.commands.DeleteTriangles;
import game.map.editor.selection.PickRay.PickHit;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.mesh.AbstractMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import game.map.shape.commands.DisplayCommand;
import game.map.shape.commands.DisplayCommand.CmdType;
import game.texture.ModelTexture;
import util.identity.IdentityArrayList;
import util.identity.IdentityHashSet;

public class SelectionManager
{
	public final MapEditor editor;

	public static enum SelectionMode
	{
		// @formatter:off
		OBJECT		("Objects"),
		TRIANGLE	("Triangles"),
		VERTEX		("Vertices"),
		POINT		("Points");
		// @formatter:on

		private final String name;

		private SelectionMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private SelectionMode selectionMode;

	private MapObjectType currentType;

	public Selection<?> currentSelection;

	// changes to one of these two selections must be reflected in the other
	private final Selection<MapObject> objectSelection;
	private final Selection<Triangle> triangleSelection;

	private final Selection<Vertex> vertexSelection;
	private List<Vertex> vertexWorkingSet = new ArrayList<>();

	public final Selection<UV> uvSelection;
	private List<UV> uvWorkingSet = new ArrayList<>();

	// no working set required since it syncs with the object selection
	public final Selection<SelectablePoint> pointSelection;

	private IdentityHashSet<DisplayCommand> selectedCommands = new IdentityHashSet<>();

	public ModelTexture selectedTexture;

	// objects used to synchronize selected objects with their counterparts in GUI
	// these track changes to the selection that occur between calls to syncGUI()
	private EditorUpdateRecord<MapObject> editorObjectUpdates = new EditorUpdateRecord<>();
	private GuiUpdateRecord<MapObject> objectTreeUpdates = new GuiUpdateRecord<>();
	private GuiUpdateRecord<SelectablePoint> pointsUpdates = new GuiUpdateRecord<>();
	private GuiUpdateRecord<DisplayCommand> displayListUpdates = new GuiUpdateRecord<>();

	// require the MapObjectPanel to have these methods available
	public static interface GUISelectionInterface
	{
		public void setObjectsSelected(Iterable<MapObject> objs);

		public void setObjectsDeselected(Iterable<MapObject> objs);

		public void setObjectsDeleted(Iterable<MapObject> objs);

		public void setObjectsCreated(Iterable<MapObject> undeletedSet);

		public void finishUpdate();
	}

	public SelectionManager(MapEditor editor)
	{
		this.editor = editor;

		objectSelection = new Selection<>(MapObject.class, editor);
		triangleSelection = new Selection<>(Triangle.class, editor);
		vertexSelection = new Selection<>(Vertex.class, editor);
		pointSelection = new Selection<>(SelectablePoint.class, editor);
		uvSelection = new Selection<>(UV.class, editor);

		selectedTexture = null;

		selectionMode = SelectionMode.OBJECT;
		currentSelection = objectSelection;
		currentType = MapObjectType.MODEL;
	}

	public SelectionMode getSelectionMode()
	{ return selectionMode; }

	public void setSelectionMode(SelectionMode newSelectionMode)
	{
		if (newSelectionMode != selectionMode)
			MapEditor.execute(new SetSelectionMode(newSelectionMode));
	}

	public class SetSelectionMode extends AbstractCommand
	{
		private final SelectionMode oldMode;
		private final SelectionMode newMode;

		private ArrayList<AbstractCommand> additionalCommand;

		private ArrayList<Vertex> oldVertexList;
		private ArrayList<Vertex> newVertexList;

		public SetSelectionMode(SelectionMode newSelectionMode)
		{
			super("Set Selection Mode");
			oldMode = selectionMode;
			newMode = newSelectionMode;

			additionalCommand = new ArrayList<>(2);

			switch (oldMode) {
				case OBJECT:
					break;
				case TRIANGLE:
					break;
				case VERTEX:
					oldVertexList = new ArrayList<>(vertexWorkingSet);
					break;
				case POINT:
					break;
			}

			switch (newSelectionMode) {
				case OBJECT:
					additionalCommand.add(getClearVertices());
					additionalCommand.add(getClearPoints());

					if (oldMode == SelectionMode.TRIANGLE) {
						IdentityHashSet<MapObject> objects = new IdentityHashSet<>();
						for (Triangle t : triangleSelection.selectableList)
							objects.add(t.parentBatch.parentMesh.parentObject);

						additionalCommand.add(new ResetSelection());
						additionalCommand.add(getAddObjects(objects));
					}
					break;

				case TRIANGLE:
					additionalCommand.add(getClearVertices());
					additionalCommand.add(getClearPoints());

					// choose new selection
					if (oldMode == SelectionMode.OBJECT) {
						IdentityHashSet<Triangle> addedTriangles = new IdentityHashSet<>();
						for (MapObject obj : objectSelection.selectableList) {
							if (!obj.hasMesh())
								return;

							for (Triangle t : obj.getMesh())
								addedTriangles.add(t);
						}

						additionalCommand.add(new ResetSelection());
						additionalCommand.add(getAddTriangles(addedTriangles));
					}
					break;

				case VERTEX:
					IdentityHashSet<Vertex> workingSet = new IdentityHashSet<>();
					newVertexList = new ArrayList<>();
					for (Triangle t : getTrianglesFromSelection()) {
						for (Vertex v : t.vert) {
							if (!workingSet.contains(v)) {
								newVertexList.add(v);
								workingSet.add(v);
							}
						}
					}
					additionalCommand.add(getClearPoints());
					break;

				case POINT:
					additionalCommand.add(getClearVertices());
					break;

			} // ----- end switch -----

			for (AbstractCommand cmd : additionalCommand)
				cmd.silence();
		}

		@Override
		public void exec()
		{
			super.exec();

			for (AbstractCommand cmd : additionalCommand)
				cmd.exec();

			switch (newMode) {
				case OBJECT:
					currentSelection = objectSelection;
					objectSelection.updateAABB();
					break;
				case TRIANGLE:
					currentSelection = triangleSelection;
					break;
				case VERTEX:
					currentSelection = vertexSelection;
					vertexWorkingSet = newVertexList;
					break;
				case POINT:
					currentSelection = pointSelection;
					break;
			}

			selectionMode = newMode;
			editor.gui.setSelectionMode(newMode);
		}

		@Override
		public void undo()
		{
			super.undo();

			selectionMode = oldMode;
			editor.gui.setSelectionMode(oldMode);

			switch (oldMode) {
				case OBJECT:
					currentSelection = objectSelection;
					objectSelection.updateAABB();
					break;
				case TRIANGLE:
					currentSelection = triangleSelection;
					break;
				case VERTEX:
					currentSelection = vertexSelection;
					vertexWorkingSet = oldVertexList;
					break;
				case POINT:
					currentSelection = pointSelection;
					break;
			}

			for (AbstractCommand cmd : additionalCommand)
				cmd.undo();
		}
	}

	public void setObjectType(MapObjectType type)
	{ currentType = type; }

	public MapObjectType getObjectType()
	{ return currentType; }

	/**
	 * @return Unfiltered list of all selected MapObjects.
	 */
	public List<MapObject> getSelectedObjects()
	{ return new ArrayList<>(objectSelection.selectableList); }

	/**
	 * @param filterClass
	 * @return Filtered list of all selected MapObjects.
	 */
	public <T extends MapObject> List<T> getSelectedObjects(Class<T> filterClass)
	{
		ArrayList<T> list = new ArrayList<>();

		for (MapObject obj : objectSelection.selectableList) {
			if (filterClass.isAssignableFrom(obj.getClass()))
				list.add(filterClass.cast(obj));
		}

		return list;
	}

	/**
	 * @return Unfiltered list of all selected triangles.
	 */
	public List<Triangle> getTrianglesFromSelection()
	{ return getTrianglesFromSelection(MapObject.class); }

	/**
	 * @param filterClass
	 * @return Filtered list of all selected triangles.
	 */
	public <T extends MapObject> List<Triangle> getTrianglesFromSelection(Class<T> filterClass)
	{
		List<Triangle> workingSet = new ArrayList<>();
		switch (selectionMode) {
			case OBJECT:
				for (MapObject obj : objectSelection.selectableList) {
					if (filterClass.isAssignableFrom(obj.getClass())) {
						if (obj.hasMesh())
							for (Triangle t : obj.getMesh())
								workingSet.add(t);
					}
				}
				break;
			case TRIANGLE:
				for (Triangle t : triangleSelection.selectableList) {
					if (filterClass.isAssignableFrom(t.parentBatch.parentMesh.parentObject.getClass()))
						workingSet.add(t);
				}
				break;
			default:
				break;
		}
		return workingSet;
	}

	/**
	 * @return Unfiltered list of all selected vertices.
	 */
	public List<Vertex> getVerticesFromSelection()
	{ return getVerticesFromSelection(MapObject.class); }

	/**
	 * @param filterClass
	 * @return Filtered list of all selected vertices.
	 */
	public <T extends MapObject> List<Vertex> getVerticesFromSelection(Class<T> filterClass)
	{
		IdentityHashSet<Vertex> vertexSet = new IdentityHashSet<>();
		switch (selectionMode) {
			case OBJECT:
				for (MapObject obj : objectSelection.selectableList) {
					if (filterClass.isAssignableFrom(obj.getClass())) {
						if (obj.hasMesh())
							for (Triangle t : obj.getMesh())
								for (Vertex v : t.vert)
									vertexSet.add(v);
					}
				}
				break;
			case TRIANGLE:
				for (Triangle t : triangleSelection.selectableList) {
					if (filterClass.isAssignableFrom(t.parentBatch.parentMesh.parentObject.getClass()))
						for (Vertex v : t.vert)
							vertexSet.add(v);
				}
				break;
			case VERTEX:
				for (Vertex v : vertexSelection.selectableList) {
					if (filterClass.isAssignableFrom(v.parentMesh.parentObject.getClass()))
						vertexSet.add(v);
				}
				break;
			case POINT:
				break;
		}

		ArrayList<Vertex> vertexList = new ArrayList<>(vertexSet.size());
		for (Vertex v : vertexSet)
			vertexList.add(v);
		return vertexList;
	}

	public List<Vertex> getVertices()
	{ return vertexWorkingSet; }

	public void generateUVListFromSelectedModel()
	{
		Model mostRecentModel = getMostRecentModel();

		if (mostRecentModel == null)
			return;

		IdentityHashSet<Vertex> vertexSet = new IdentityHashSet<>();

		for (Triangle t : mostRecentModel.getMesh()) {
			vertexSet.add(t.vert[0]);
			vertexSet.add(t.vert[1]);
			vertexSet.add(t.vert[2]);
		}

		uvWorkingSet.clear();
		for (Vertex v : vertexSet) {
			uvWorkingSet.add(v.uv);
		}
	}

	public void generateUVList(Collection<Triangle> triangles)
	{
		uvWorkingSet.clear();

		if (triangles.size() == 0)
			return;

		IdentityHashSet<Vertex> vertexSet = new IdentityHashSet<>();

		for (Triangle t : triangles) {
			vertexSet.add(t.vert[0]);
			vertexSet.add(t.vert[1]);
			vertexSet.add(t.vert[2]);
		}

		for (Vertex v : vertexSet)
			uvWorkingSet.add(v.uv);
	}

	public void generateUVListFromWorkingSet()
	{
		IdentityHashSet<Vertex> vertexSet = new IdentityHashSet<>();

		switch (selectionMode) {
			case OBJECT:
				for (MapObject obj : objectSelection.selectableList) {
					for (Triangle t : obj.getMesh()) {
						vertexSet.add(t.vert[0]);
						vertexSet.add(t.vert[1]);
						vertexSet.add(t.vert[2]);
					}
				}
				break;
			case TRIANGLE:
				for (Triangle t : triangleSelection.selectableList) {
					vertexSet.add(t.vert[0]);
					vertexSet.add(t.vert[1]);
					vertexSet.add(t.vert[2]);
				}
				break;
			case VERTEX:
				for (Vertex v : vertexSelection.selectableList) {
					vertexSet.add(v);
				}
				break;
			case POINT:
				break;
		}

		uvWorkingSet.clear();
		for (Vertex v : vertexSet) {
			uvWorkingSet.add(v.uv);
		}
	}

	public void clearUVSet()
	{
		uvSelection.clear();
		uvWorkingSet.clear();
	}

	public MapObject getMostRecentObject()
	{
		int selectedObjects = objectSelection.selectableList.size();
		if (selectedObjects == 0)
			return null;

		return objectSelection.selectableList.get(selectedObjects - 1);
	}

	public MapObject getMostRecentObjectWithMesh()
	{
		MapObject mostRecent = null;

		for (int i = objectSelection.selectableList.size() - 1; i >= 0; i--) {
			MapObject obj = objectSelection.selectableList.get(i);
			if (obj instanceof Marker || !obj.hasMesh())
				continue;

			mostRecent = obj;
			break;
		}

		return mostRecent;
	}

	public <T extends MapObject> T getMostRecent(Class<T> typeClass)
	{
		T mostRecent = null;

		for (int i = objectSelection.selectableList.size() - 1; i >= 0; i--) {
			MapObject obj = objectSelection.selectableList.get(i);
			if (typeClass.isAssignableFrom(obj.getClass())) {
				mostRecent = typeClass.cast(obj);
				break;
			}
		}

		return mostRecent;
	}

	public Model getMostRecentModel()
	{
		Model mostRecentModel = null;

		for (int i = objectSelection.selectableList.size() - 1; i >= 0; i--) {
			MapObject obj = objectSelection.selectableList.get(i);
			if (obj instanceof Model mdl) {
				mostRecentModel = mdl;
				break;
			}
		}

		return mostRecentModel;
	}

	public void recalculateBoundingBox()
	{
		if (!currentSelection.isEmpty() && !currentSelection.transforming())
			currentSelection.updateAABB();
	}

	public void selectAll(Map map)
	{
		switch (selectionMode) {
			case OBJECT:
				selectAllObjects(map);
				break;
			case TRIANGLE:
				selectAllTriangles(map);
				break;
			case VERTEX:
				vertexSelection.clear();
				HashSet<Vertex> addedVertices = new HashSet<>();
				for (MapObject obj : objectSelection.selectableList) {
					if (obj.hasMesh()) {
						for (Triangle t : obj.getMesh()) {
							for (Vertex v : t.vert)
								if (!addedVertices.contains(v)) {
									vertexSelection.addAndSelect(v);
									addedVertices.add(v);
								}
						}
					}
				}
				break;
			case POINT:
				pointSelection.clear();
				for (MapObject obj : objectSelection.selectableList) {
					if (obj.hasSelectablePoints()) {
						for (SelectablePoint p : obj.getSelectablePoints())
							pointSelection.addAndSelect(p);
					}
				}
				break;
		}
	}

	private void selectAllObjects(Map map)
	{
		List<MapObject> added = new LinkedList<>();

		switch (currentType) {
			case MODEL:
				for (Model mdl : map.modelTree)
					if (!mdl.hidden && !mdl.selected)
						added.add(mdl);
				break;

			case COLLIDER:
				for (Collider c : map.colliderTree)
					if (!c.hidden && !c.selected)
						added.add(c);
				break;

			case ZONE:
				for (Zone z : map.zoneTree)
					if (!z.hidden && !z.selected)
						added.add(z);
				break;

			case MARKER:
				for (Marker m : map.markerTree)
					if (!m.hidden && !m.selected)
						added.add(m);
				break;
			case EDITOR:
				throw new IllegalStateException("MapObjectType cannot be EDITOR");
		}

		for (MapObject obj : editor.getEditorObjects()) {
			if (!obj.selected)
				added.add(obj);
		}

		if (added.size() != 0)
			MapEditor.execute(getAddObjects(added));
	}

	private void selectAllTriangles(Map map)
	{
		List<Triangle> added = new LinkedList<>();

		for (Model mdl : map.modelTree)
			if (!mdl.hidden)
				for (Triangle t : mdl.getMesh())
					if (!t.selected)
						added.add(t);

		for (Collider c : map.colliderTree)
			if (!c.hidden)
				for (Triangle t : c.getMesh())
					if (!t.selected)
						added.add(t);

		for (Zone z : map.zoneTree)
			if (!z.hidden)
				for (Triangle t : z.getMesh())
					if (!t.selected)
						added.add(t);

		if (added.size() != 0)
			MapEditor.execute(getAddTriangles(added));
	}

	public void selectAllWithinBox(Map map, BoundingBox selectionBox)
	{
		switch (selectionMode) {
			case OBJECT: {
				List<MapObject> toAdd = new LinkedList<>();
				List<MapObject> toRemove = new LinkedList<>();
				for (MapObject obj : map.getObjectsWithinRegion(selectionBox, editor.getEditorObjects())) {
					if (obj.selected)
						toRemove.add(obj);
					else
						toAdd.add(obj);
				}
				if (toAdd.size() != 0 || toRemove.size() != 0)
					MapEditor.execute(getModifyObjects(toAdd, toRemove));
			}
				break;

			case TRIANGLE: {
				List<Triangle> toAdd = new LinkedList<>();
				List<Triangle> toRemove = new LinkedList<>();
				for (Triangle t : map.getTrianglesWithinRegion(selectionBox)) {
					if (t.selected)
						toRemove.add(t);
					else
						toAdd.add(t);
				}
				if (toAdd.size() != 0 || toRemove.size() != 0)
					MapEditor.execute(getModifyTriangles(toAdd, toRemove, true));
			}
				break;

			case VERTEX: {
				List<Vertex> toAdd = new LinkedList<>();
				List<Vertex> toRemove = new LinkedList<>();
				for (Vertex v : vertexWorkingSet) {
					if (selectionBox.contains(v)) {
						if (v.selected)
							toRemove.add(v);
						else
							toAdd.add(v);
					}
				}
				if (toAdd.size() != 0 || toRemove.size() != 0)
					MapEditor.execute(getModifyVertices(toAdd, toRemove));
			}

			case POINT: {
				List<SelectablePoint> toAdd = new LinkedList<>();
				List<SelectablePoint> toRemove = new LinkedList<>();

				List<SelectablePoint> pointWorkingSet = getPointWorkingSet();
				for (SelectablePoint point : pointWorkingSet) {
					if (point.hidden)
						continue;

					if (selectionBox.contains(point)) {
						if (point.isSelected())
							toRemove.add(point);
						else
							toAdd.add(point);
					}
				}
				if (toAdd.size() != 0 || toRemove.size() != 0)
					MapEditor.execute(getModifyPoints(toAdd, toRemove));
			}
				break;
		}
	}

	public void selectUVsWithinBox(BoundingBox selectionBox)
	{
		List<UV> toAdd = new LinkedList<>();
		List<UV> toRemove = new LinkedList<>();

		for (UV uv : uvWorkingSet) {
			if (selectionBox.contains(uv.getU(), uv.getV(), 0)) {
				if (uv.selected)
					toRemove.add(uv);
				else
					toAdd.add(uv);
			}
		}

		if (toAdd.size() != 0 || toRemove.size() != 0)
			MapEditor.execute(getModifyUVs(toAdd, toRemove));
	}

	public void cloneSelection()
	{
		switch (selectionMode) {
			case OBJECT:
				if (!objectSelection.isEmpty()) {
					CloneObjects cloneCommand = new CloneObjects(objectSelection);
					if (cloneCommand.shouldExec()) {
						objectSelection.transformCommandBatch.addCommand(cloneCommand);
						cloneCommand.exec(); // not added to redo/undo stacks
					}
				}
				break;
			case TRIANGLE:
				if (!triangleSelection.isEmpty()) {
					CloneTriangles cloneCommand = new CloneTriangles(triangleSelection);
					triangleSelection.transformCommandBatch.addCommand(cloneCommand);
					cloneCommand.exec(); // not added to redo/undo stacks
				}
				break;
			case VERTEX:
				break;
			case POINT:
				break;
		}
	}

	public void deleteSelection()
	{
		switch (selectionMode) {
			case OBJECT:
				if (!objectSelection.isEmpty())
					MapEditor.execute(new DeleteObjects(objectSelection));
				break;
			case TRIANGLE:
				if (!triangleSelection.isEmpty())
					MapEditor.execute(new DeleteTriangles(triangleSelection));
				break;
			case VERTEX:
				break;
			case POINT:
				break;
		}

		currentSelection.clear();
	}

	public void testGizmoMouseover(EditorMode editorMode, PickRay mouseRay, MapEditViewport mouseViewport)
	{
		Selection<?> selection = null;
		if (editorMode == EditorMode.Modify || editorMode == EditorMode.Scripts)
			selection = currentSelection;
		else if (editorMode == EditorMode.EditUVs)
			selection = uvSelection;

		if (selection == null)
			return;

		selection.testTransformGizmo(mouseRay, mouseViewport);
	}

	/**
	 * Picks a point from the visible world, disregarding EditorObjects
	 */
	public PickHit pickWorld(Map map, PickRay pickRay, MapEditViewport pickViewport, boolean modelsOnly)
	{
		/*
		if(pickViewport.type != ViewType.PERSPECTIVE)
		{
			// pick from selection
			PickHit hitSelection = editor.map.pickObjectFromSet(pickRay, objectSelection.selectableList);
			if(!hitSelection.missed())
				return hitSelection;
		}
		*/

		// pick from all
		PickHit hitObject = new PickHit(pickRay);
		if (modelsOnly)
			hitObject = Map.pickObjectFromSet(pickRay, map.modelTree);
		else
			hitObject = map.pickNearestObject(pickRay, currentType, new LinkedList<>());

		return hitObject;
	}

	public PickHit pickCurrentSelection(Map map, PickRay pickRay, MapEditViewport pickViewport, boolean modelsOnly, boolean modifySelection)
	{
		PickHit hitHandle = currentSelection.pickTransformGizmo(pickRay, pickViewport);
		if (!hitHandle.missed())
			return hitHandle;

		PickHit hit = null;
		switch (selectionMode) {
			case OBJECT:
				hit = pickObject(map, pickRay, pickViewport, modifySelection, modelsOnly);
				break;

			case TRIANGLE:
				hit = pickTriangle(map, pickRay, pickViewport, modifySelection, modelsOnly);
				break;

			case VERTEX:
				hit = pickVertex(map, pickRay, pickViewport, modifySelection, modelsOnly);
				break;

			case POINT:
				hit = pickPoint(pickRay, pickViewport, modifySelection);
				break;
		}
		return hit;
	}

	private PickHit pickObject(Map map, PickRay pickRay, MapEditViewport pickViewport, boolean modifySelection, boolean modelsOnly)
	{
		boolean additive = editor.keyboard.isCtrlDown();
		if (!additive && pickViewport.type != ViewType.PERSPECTIVE) {
			// pick from selection
			PickHit hitSelection = Map.pickObjectFromSet(pickRay, objectSelection.selectableList);
			if (!hitSelection.missed())
				return hitSelection;
		}

		// pick from all
		PickHit hitObject = new PickHit(pickRay);
		if (modelsOnly)
			hitObject = Map.pickObjectFromSet(pickRay, map.modelTree);
		else
			hitObject = map.pickNearestObject(pickRay, currentType, editor.getEditorObjects());

		if (!modifySelection || pickRay.preventSelectionChange)
			return hitObject;

		List<MapObject> added = new LinkedList<>();
		List<MapObject> removed = new LinkedList<>();

		if (!hitObject.missed()) {
			MapObject selectedObject = (MapObject) hitObject.obj;

			// multiple selection mode
			if (additive) {
				if (!selectedObject.selected)
					added.add(selectedObject);
				else
					removed.add(selectedObject);

				// single selection mode
			}
			else {
				removed.addAll(objectSelection.selectableList);
				added.add(selectedObject);
			}

			// click nothing
		}
		else if (!additive && !editor.keyboard.isShiftDown()) {
			removed.addAll(objectSelection.selectableList);
		}

		if (added.size() != 0 || removed.size() != 0)
			MapEditor.execute(getModifyObjects(added, removed));

		return hitObject;
	}

	private PickHit pickTriangle(Map map, PickRay pickRay, MapEditViewport pickViewport, boolean modifySelection, boolean modelsOnly)
	{
		boolean additive = editor.keyboard.isCtrlDown();
		if (!additive && pickViewport.type != ViewType.PERSPECTIVE) {
			// pick from selection
			PickHit hitSelection = Map.pickTriangleFromList(pickRay, triangleSelection.selectableList);
			if (!hitSelection.missed())
				return hitSelection;
		}

		// pick from all
		PickHit hitTriangle = new PickHit(pickRay);
		if (modelsOnly)
			hitTriangle = Map.pickTriangleFromObjectList(pickRay, map.modelTree);
		else
			hitTriangle = map.pickNearestTriangle(pickRay);

		if (!modifySelection)
			return hitTriangle;

		List<Triangle> added = new LinkedList<>();
		List<Triangle> removed = new LinkedList<>();

		if (!hitTriangle.missed()) {
			Triangle selectedTriangle = (Triangle) hitTriangle.obj;

			// multiple selection mode
			if (additive) {
				if (!selectedTriangle.selected)
					added.add(selectedTriangle);
				else
					removed.add(selectedTriangle);

				// single selection mode
			}
			else {
				if (!selectedTriangle.selected) {
					removed.addAll(triangleSelection.selectableList);
					added.add(selectedTriangle);
				}
			}
		}
		else if (!additive && !editor.keyboard.isShiftDown()) {
			removed.addAll(triangleSelection.selectableList);
		}

		if (added.size() != 0 || removed.size() != 0)
			MapEditor.execute(getModifyTriangles(added, removed, true));

		return hitTriangle;
	}

	private PickHit pickVertex(Map map, PickRay pickRay, MapEditViewport pickViewport, boolean modifySelection, boolean modelsOnly)
	{
		boolean additive = editor.keyboard.isCtrlDown();
		if (!additive && pickViewport.type != ViewType.PERSPECTIVE) {
			// pick from selection
			PickHit hitSelection = Map.pickVertexFromList(pickRay, vertexSelection.selectableList);
			if (!hitSelection.missed())
				return hitSelection;
		}

		PickHit hitVertex = Map.pickVertexFromList(pickRay, vertexWorkingSet);

		if (!modifySelection)
			return hitVertex;

		List<Vertex> added = new LinkedList<>();
		List<Vertex> removed = new LinkedList<>();

		if (!hitVertex.missed()) {
			Vertex selectedVertex = (Vertex) hitVertex.obj;

			// multiple selection mode
			if (additive) {
				if (!selectedVertex.selected)
					added.add(selectedVertex);
				else
					removed.add(selectedVertex);

				// single selection mode
			}
			else {
				if (!selectedVertex.selected) {
					removed.addAll(vertexSelection.selectableList);
					added.add(selectedVertex);
				}
			}
		}
		else if (!additive && !editor.keyboard.isShiftDown()) {
			removed.addAll(vertexSelection.selectableList);
		}

		if (added.size() != 0 || removed.size() != 0)
			MapEditor.execute(getModifyVertices(added, removed));

		return hitVertex;
	}

	public PickHit pickUV(Map map, PickRay pickRay, MapEditViewport pickViewport, boolean modifySelection)
	{
		PickHit hitHandle = uvSelection.pickTransformGizmo(pickRay, pickViewport);
		if (!hitHandle.missed())
			return hitHandle;

		boolean additive = editor.keyboard.isCtrlDown();
		if (!additive && pickViewport.type != ViewType.PERSPECTIVE) {
			// pick from selection
			PickHit hitSelection = Map.pickUVFromList(pickRay, uvSelection.selectableList);
			if (!hitSelection.missed())
				return hitSelection;
		}

		PickHit hitUV = Map.pickUVFromList(pickRay, uvWorkingSet);

		if (!modifySelection)
			return hitUV;

		List<UV> added = new LinkedList<>();
		List<UV> removed = new LinkedList<>();

		// found something
		if (!hitUV.missed()) {
			UV selectedUV = (UV) hitUV.obj;

			// multiple selection mode
			if (additive) {
				if (!selectedUV.selected)
					added.add(selectedUV);
				else
					removed.add(selectedUV);

				// single selection mode
			}
			else {
				if (!selectedUV.selected) {
					removed.addAll(uvSelection.selectableList);
					added.add(selectedUV);
				}
			}
		}
		else if (!additive && !editor.keyboard.isShiftDown()) {
			removed.addAll(uvSelection.selectableList);
		}

		if (added.size() != 0 || removed.size() != 0)
			MapEditor.execute(getModifyUVs(added, removed));

		return hitUV;
	}

	private List<SelectablePoint> getPointWorkingSet()
	{
		List<SelectablePoint> pointWorkingSet = new IdentityArrayList<>();
		for (MapObject obj : objectSelection.selectableList) {
			if (obj.hasSelectablePoints())
				obj.addSelectablePoints(pointWorkingSet);
		}

		return pointWorkingSet;
	}

	public PickHit pickPoint(PickRay pickRay, MapEditViewport pickViewport, boolean modifySelection)
	{
		PickHit hitHandle = pointSelection.pickTransformGizmo(pickRay, pickViewport);
		if (!hitHandle.missed())
			return hitHandle;

		boolean additive = editor.keyboard.isCtrlDown();
		if (!additive && pickViewport.type != ViewType.PERSPECTIVE) {
			// pick from selection
			PickHit hitSelection = pickPointFromList(pickRay, pointSelection.selectableList);
			if (!hitSelection.missed())
				return hitSelection;
		}

		// recalculate this on pick
		List<SelectablePoint> pointWorkingSet = getPointWorkingSet();
		PickHit hitPoint = pickPointFromList(pickRay, pointWorkingSet);

		if (!modifySelection)
			return hitPoint;

		List<SelectablePoint> added = new LinkedList<>();
		List<SelectablePoint> removed = new LinkedList<>();

		if (!hitPoint.missed()) {
			SelectablePoint selectedPoint = (SelectablePoint) hitPoint.obj;

			// multiple selection mode
			if (additive) {
				if (!selectedPoint.isSelected())
					added.add(selectedPoint);
				else
					removed.add(selectedPoint);

				// single selection mode
			}
			else {
				if (!selectedPoint.isSelected()) {
					removed.addAll(pointSelection.selectableList);
					added.add(selectedPoint);
				}
			}
		}
		else if (!additive) {
			removed.addAll(pointSelection.selectableList);
		}

		if (added.size() != 0 || removed.size() != 0)
			MapEditor.execute(getModifyPoints(added, removed));

		return hitPoint;
	}

	private PickHit pickPointFromList(PickRay pickRay, Iterable<SelectablePoint> candidates)
	{
		PickHit closestHit = new PickHit(pickRay, Float.MAX_VALUE);
		for (SelectablePoint point : candidates) {
			if (point.hidden)
				continue;

			PickHit hit = PickRay.getPointIntersection(pickRay, point.getX(), point.getY(), point.getZ(), point.sizeScale);
			if (hit.dist < closestHit.dist) {
				closestHit = hit;
				closestHit.obj = point;
			}
		}
		return closestHit;
	}

	/**
	 * These methods should be used by commands to modify the object and triangle selection.
	 * These will ensure synchronization with the GUI.
	 * However, the GUI should NOT use these methods.
	 */

	public void clearObjectSelection()
	{
		if (SwingUtilities.isEventDispatchThread())
			throw new RuntimeException("Invalid state. Tried to clear object selection from EDT.");

		editorObjectUpdates.clear(objectSelection.selectableList);
		for (MapObject obj : objectSelection.selectableList) {
			if (obj.hasMesh())
				obj.getMesh().selectedTriangleCount = 0;
		}
		objectSelection.clear();
		//XXX	triangleSelection.clear();
	}

	public void selectObject(MapObject obj)
	{
		if (SwingUtilities.isEventDispatchThread())
			throw new RuntimeException("Invalid state. Tried to select object from EDT.");

		objectSelection.addAndSelect(obj);
		editorObjectUpdates.select(obj);
	}

	public void deselectObject(MapObject obj)
	{
		if (SwingUtilities.isEventDispatchThread())
			throw new RuntimeException("Invalid state. Tried to deselect object from EDT.");

		objectSelection.removeAndDeselect(obj);
		editorObjectUpdates.deselect(obj);
	}

	public void deleteObject(MapObject obj)
	{
		if (SwingUtilities.isEventDispatchThread())
			throw new RuntimeException("Invalid state. Tried to delete object from EDT.");

		objectSelection.removeAndDeselect(obj);
		editorObjectUpdates.delete(obj);

		//XXX
		if (obj.hasMesh()) {
			AbstractMesh mesh = obj.getMesh();
			for (Triangle t : mesh) {
				if (t.selected) {
					triangleSelection.removeAndDeselect(t);
					mesh.selectedTriangleCount--;
				}
			}
		}
	}

	public void createObject(MapObject obj)
	{
		if (SwingUtilities.isEventDispatchThread())
			throw new RuntimeException("Invalid state. Tried to create object from EDT.");

		objectSelection.addAndSelect(obj);
		editorObjectUpdates.create(obj);

		//XXX
		if (obj.hasMesh()) {
			AbstractMesh mesh = obj.getMesh();
			for (Triangle t : mesh) {
				if (!t.selected) {
					triangleSelection.addAndSelect(t);
					mesh.selectedTriangleCount++;
				}
			}
		}
	}

	private void selectTriangle(Triangle t)
	{
		if (SwingUtilities.isEventDispatchThread())
			throw new RuntimeException("Invalid state. Tried to select triangle from EDT.");

		triangleSelection.addAndSelect(t);

		//XXX these exist to make the GUI identify objects as we select triangles
		AbstractMesh mesh = t.parentBatch.parentMesh;
		if (mesh.selectedTriangleCount == 0) {
			MapObject obj = mesh.parentObject;
			objectSelection.addAndSelect(obj);
			editorObjectUpdates.select(obj);
		}
		mesh.selectedTriangleCount++;
	}

	private void deselectTriangle(Triangle t, boolean canDeselectMesh)
	{
		if (SwingUtilities.isEventDispatchThread())
			throw new RuntimeException("Invalid state. Tried to deselect triangle from EDT.");

		triangleSelection.removeAndDeselect(t);

		//XXX
		AbstractMesh mesh = t.parentBatch.parentMesh;
		mesh.selectedTriangleCount--;
		if (mesh.selectedTriangleCount == 0 && canDeselectMesh) {
			MapObject obj = mesh.parentObject;
			objectSelection.removeAndDeselect(obj);
			editorObjectUpdates.deselect(obj);
		}
	}

	// STORY TIME
	// In this method, updates from the Editor take precedence over changes from the GUI. Why is that?
	// If we allow bidirectional updates in the same frame, conflicts may occur. Here is an example.
	// Deleting a selected object will break undo/redo. We'll find something like this in the log:
	//	> Exec: Create Object
	//	> Undo: Create Object
	//	> Exec: Modify Object Selection
	//	> Can't redo anything.
	// Basically, the map.remove(...) method is responsible. When a selected item is removed from the JTree,
	// fireTreeNodesRemoved(...) is invoked, which fires a TreeSelectionEvent to the TreeSelectionListener in
	// MapObjectPanel, which asynchronously notifies SelectionManager that a selection change has occurred via
	// syncGUI(...). From there, a ModifySelection command is created and executed in the Editor thread.
	// Meanwhile, deleteObject(...) is invoked on the SelectionManager, which uses syncGUI(...) to notify the
	// JTree about the selection change at its earliest convenience via invokeLater(...).
	// Hence two events, both dispatched from syncGUI(...). The solution is to pre-empt this conflict by only
	// allowing ONE thread to update the other at a time. This has a small chance of creating a race condition
	// if the CreateObject command is executed from the EDT, so don't do that!
	// An alternative solution (aka uglier hack) is use a public flag in the MapObjectTreeModel that is set when
	// fireTreeNodesRemoved(...) is invoked. MapObjectPanel can check this flag when it handles a tree selection
	// event to determine whether or not it should notify SelectionManager.
	// Tight coupling is bad.

	/**
	 * Handles all communication with the the GUI since the last time this method was invoked.
	 */
	public void syncGUI(GUISelectionInterface guiSelect)
	{
		if (pointsUpdates.updated) {
			if (pointsUpdates.added.size() != 0 || pointsUpdates.removed.size() != 0) {
				CommandBatch batch = new CommandBatch("Select Points");

				if (selectionMode != SelectionMode.POINT)
					batch.addCommand(new SetSelectionMode(SelectionMode.POINT));

				batch.addCommand(getModifyPoints(pointsUpdates.added, pointsUpdates.removed));
				MapEditor.execute(batch);
			}
			pointsUpdates.reset();
		}

		if (displayListUpdates.updated) {
			List<Triangle> removedTriangles = new LinkedList<>();
			List<Triangle> addedTriangles = new LinkedList<>();

			AbstractMesh mesh = null;

			for (DisplayCommand cmd : displayListUpdates.removed) {
				if (cmd.getType() == CmdType.DrawTriangleBatch)
					removedTriangles.addAll(((TriangleBatch) cmd).triangles);
				mesh = cmd.parentMesh;
			}

			for (DisplayCommand cmd : displayListUpdates.added) {
				if (cmd.getType() == CmdType.DrawTriangleBatch)
					addedTriangles.addAll(((TriangleBatch) cmd).triangles);
				mesh = cmd.parentMesh;
			}

			if (mesh == null || !(mesh.parentObject instanceof Model))
				return;

			CommandBatch batch = new CommandBatch("Select Display Commands");
			batch.addCommand(new ModifyDisplayCommands((Model) mesh.parentObject, displayListUpdates.added, displayListUpdates.removed));

			if (addedTriangles.size() != 0 || removedTriangles.size() != 0) {
				if (selectionMode != SelectionMode.TRIANGLE)
					batch.addCommand(new SetSelectionMode(SelectionMode.TRIANGLE));

				batch.addCommand(getModifyTriangles(addedTriangles, removedTriangles, false));
			}

			MapEditor.execute(batch);

			displayListUpdates.reset();
		}

		if (editorObjectUpdates.updated) {
			// changes on the editor thread take precedence...
			objectTreeUpdates.reset();

			// ...but changes to the GUI need to be done on the EDT
			SwingUtilities.invokeLater(() -> {
				if (!editorObjectUpdates.removed.isEmpty())
					guiSelect.setObjectsDeselected(editorObjectUpdates.removed);

				if (!editorObjectUpdates.added.isEmpty())
					guiSelect.setObjectsSelected(editorObjectUpdates.added);

				if (!editorObjectUpdates.deleted.isEmpty())
					guiSelect.setObjectsDeleted(editorObjectUpdates.deleted);

				if (!editorObjectUpdates.created.isEmpty())
					guiSelect.setObjectsCreated(editorObjectUpdates.created);

				guiSelect.finishUpdate();
				editorObjectUpdates.reset();
			});
		}
		else if (objectTreeUpdates.updated) {
			// for readibility
			LinkedBlockingQueue<MapObject> added = objectTreeUpdates.added;
			LinkedBlockingQueue<MapObject> removed = objectTreeUpdates.removed;

			if (added.size() != 0 || removed.size() != 0) {
				//	System.out.println("GUI updated. (+" + added.size() + ", -" + removed.size() + ")");
				MapEditor.execute(getModifyObjects(added, removed));
			}

			objectTreeUpdates.reset();
		}
	}

	/**
	 * The GUI uses these methods to report changes in the command list selections.
	 */

	public void clearDisplayCommandsFromGUI()
	{
		displayListUpdates.deselect(selectedCommands);
	}

	public void selectDisplayCommandsFromGUI(Iterable<DisplayCommand> objs)
	{
		displayListUpdates.select(objs);
	}

	public void deselectDisplayCommandsFromGUI(Iterable<DisplayCommand> objs)
	{
		displayListUpdates.deselect(objs);
	}

	/**
	 * The GUI uses these methods to report changes in the command list selections.
	 */

	public void clearPointsFromGUI()
	{
		pointsUpdates.deselect(pointSelection.selectableList);
	}

	public void selectPointsFromGUI(Iterable<? extends SelectablePoint> objs)
	{
		pointsUpdates.select(objs);
	}

	public void deselectPointsFromGUI(Iterable<? extends SelectablePoint> objs)
	{
		pointsUpdates.deselect(objs);
	}

	/**
	 * The GUI uses these methods to report changes in the object tree selections.
	 */

	public void clearObjectsFromGUI()
	{
		objectTreeUpdates.deselect(objectSelection.selectableList);
	}

	public void selectObjectsFromGUI(Iterable<MapObject> objs)
	{
		objectTreeUpdates.select(objs);
	}

	public void deselectObjectsFromGUI(Iterable<MapObject> objs)
	{
		objectTreeUpdates.deselect(objs);
	}

	/**
	 * The GUI uses these methods to report changes in the object tree selections.
	 */

	public void selectLightsFromGUI(MapObject obj)
	{
		editorObjectUpdates.select(obj);
	}

	public void deselectLightsFromGUI(Iterable<MapObject> objs)
	{
		editorObjectUpdates.clear(objs);
	}

	/**
	 * DisplayCommands are not stored as a full-fledged Selection, but changes in their selected
	 * set trigger changes in the triangle selection.
	 */
	private class ModifyDisplayCommands extends AbstractCommand
	{
		private final Model mdl;
		private final List<DisplayCommand> addList;
		private final List<DisplayCommand> removeList;

		public ModifyDisplayCommands(Model mdl, Iterable<DisplayCommand> addList, Iterable<DisplayCommand> removeList)
		{
			super("Modify Display Commands");
			this.mdl = mdl;

			this.addList = new ArrayList<>();
			if (addList != null)
				for (DisplayCommand cmd : addList)
					this.addList.add(cmd);

			this.removeList = new ArrayList<>();
			if (removeList != null)
				for (DisplayCommand cmd : removeList)
					this.removeList.add(cmd);
		}

		@Override
		public void exec()
		{
			super.exec();

			for (DisplayCommand cmd : removeList) {
				selectedCommands.remove(cmd);
				cmd.selected = false;
			}

			for (DisplayCommand cmd : addList) {
				selectedCommands.add(cmd);
				cmd.selected = true;
			}

			mdl.displayListChanged();
		}

		@Override
		public void undo()
		{
			super.undo();

			for (DisplayCommand cmd : addList) {
				selectedCommands.remove(cmd);
				cmd.selected = false;
			}

			for (DisplayCommand cmd : removeList) {
				selectedCommands.add(cmd);
				cmd.selected = true;
			}

			mdl.displayListChanged();
		}
	}

	/**
	 * The object selection must be modified with this command to ensure undo/redo
	 * compatibility and proper synchronization with the GUI.
	 */
	private class ModifyObjects extends AbstractCommand
	{
		private final SelectionMode mode;
		private final List<MapObject> addList = new ArrayList<>();
		private final List<MapObject> removeList = new ArrayList<>();

		private final List<Triangle> selectTris = new LinkedList<>();
		private final List<Triangle> deselectTris = new LinkedList<>();

		private final List<Vertex> addVerts = new LinkedList<>();
		private final List<Vertex> removeVerts = new LinkedList<>();
		private final List<Vertex> deselectVerts = new LinkedList<>();

		private final List<SelectablePoint> addPoints = new LinkedList<>();
		private final List<SelectablePoint> removePoints = new LinkedList<>();
		private final List<SelectablePoint> deselectPoints = new LinkedList<>();

		private AbstractCommand subordinateSelectionChange = null;

		public ModifyObjects(String msg, Iterable<? extends MapObject> addList, Iterable<? extends MapObject> removeList)
		{
			super(msg);

			mode = selectionMode;

			if (addList != null)
				for (MapObject obj : addList) {
					this.addList.add(obj);
					processAdd(obj);
				}

			if (removeList != null)
				for (MapObject obj : removeList) {
					this.removeList.add(obj);
					processRemove(obj);
				}

			switch (mode) {
				case OBJECT:
					break;
				case TRIANGLE:
					if (!deselectTris.isEmpty() || !selectTris.isEmpty())
						subordinateSelectionChange = getModifyTriangles(selectTris, deselectTris, false);
					break;
				case VERTEX:
					if (!deselectVerts.isEmpty())
						subordinateSelectionChange = getModifyVertices(null, deselectVerts);
					break;
				case POINT:
					if (!deselectPoints.isEmpty())
						subordinateSelectionChange = getModifyPoints(null, deselectPoints);
					break;
			}

			if (subordinateSelectionChange != null)
				subordinateSelectionChange.silence();
		}

		private void processAdd(MapObject obj)
		{
			switch (mode) {
				case OBJECT:
					break;
				case TRIANGLE:
					if (obj.hasMesh()) {
						for (Triangle t : obj.getMesh()) {
							if (!t.isSelected() && !selectTris.contains(t))
								selectTris.add(t);
						}
					}
					break;
				case VERTEX:
					if (obj.hasMesh()) {
						for (Triangle t : obj.getMesh()) {
							for (Vertex v : t.vert)
								if (!addVerts.contains(v))
									addVerts.add(v);
						}
					}
					break;
				case POINT:
					if (obj.hasSelectablePoints()) {
						for (SelectablePoint p : obj.getSelectablePoints())
							if (!addPoints.contains(p))
								addPoints.add(p);
					}
					break;
			}
		}

		private void processRemove(MapObject obj)
		{
			switch (mode) {
				case OBJECT:
					break;
				case TRIANGLE:
					if (obj.hasMesh()) {
						for (Triangle t : obj.getMesh()) {
							if (t.isSelected() && !deselectTris.contains(t))
								deselectTris.add(t);
						}
					}
					break;
				case VERTEX:
					if (obj.hasMesh()) {
						for (Triangle t : obj.getMesh()) {
							for (Vertex v : t.vert) {
								if (!removeVerts.contains(v))
									removeVerts.add(v);

								if (v.isSelected() && !deselectVerts.contains(v))
									deselectVerts.add(v);
							}
						}
					}
					break;
				case POINT:
					if (obj.hasSelectablePoints()) {
						for (SelectablePoint p : obj.getSelectablePoints()) {
							if (!removePoints.contains(p))
								removePoints.add(p);

							if (p.isSelected() && !deselectPoints.contains(p))
								deselectPoints.add(p);
						}
					}
					break;
			}
		}

		@Override
		public boolean modifiesMap()
		{
			return false;
		}

		@Override
		public void exec()
		{
			super.exec();

			for (MapObject obj : removeList)
				deselectObject(obj);

			for (MapObject obj : addList)
				selectObject(obj);

			if (subordinateSelectionChange != null)
				subordinateSelectionChange.exec();

			// change working sets
			switch (mode) {
				case VERTEX:
					vertexWorkingSet.removeAll(removeVerts);
					vertexWorkingSet.addAll(addVerts);
					break;
				default:
			}
		}

		@Override
		public void undo()
		{
			super.undo();

			// undo changes to working sets
			switch (mode) {
				case VERTEX:
					vertexWorkingSet.addAll(removeVerts);
					vertexWorkingSet.removeAll(addVerts);
					break;
				default:
			}

			if (subordinateSelectionChange != null)
				subordinateSelectionChange.undo();

			for (MapObject obj : addList)
				deselectObject(obj);

			for (MapObject obj : removeList)
				selectObject(obj);
		}
	}

	public AbstractCommand getModifyObjects(Iterable<? extends MapObject> addList, Iterable<? extends MapObject> removeList)
	{
		return new ModifyObjects("Change Object Selection", addList, removeList);
	}

	public AbstractCommand getAddObjects(Iterable<? extends MapObject> addList)
	{
		return new ModifyObjects("Select Objects", addList, null);
	}

	public AbstractCommand getSetObjects(Iterable<? extends MapObject> addList)
	{
		return new ModifyObjects("Select Objects", addList, getSelectedObjects());
	}

	public AbstractCommand getAddObject(MapObject obj)
	{
		LinkedList<MapObject> dummyList = new LinkedList<>();
		dummyList.add(obj);
		return new ModifyObjects("Select " + obj.getName(), dummyList, null);
	}

	private class ResetSelection extends AbstractCommand
	{
		private final List<MapObject> objects;
		private final List<Triangle> triangles;
		//	private final List<Vertex> vertices;

		public ResetSelection()
		{
			super("Reset Selection");

			objects = new ArrayList<>(objectSelection.selectableList.size());
			for (MapObject obj : objectSelection.selectableList)
				objects.add(obj);

			triangles = new ArrayList<>(triangleSelection.selectableList.size());
			for (Triangle t : triangleSelection.selectableList)
				triangles.add(t);

			//	vertices = new ArrayList<>(vertexSelection.selectableList.size());
			//	for(Vertex v : vertexSelection.selectableList)
			//		vertices.add(v);
		}

		@Override
		public void exec()
		{
			super.exec();

			for (MapObject obj : objects)
				deselectObject(obj);

			for (Triangle t : triangles)
				deselectTriangle(t, false); //XXX what is 'canDeselectMesh'?
		}

		@Override
		public void undo()
		{
			super.undo();

			for (MapObject obj : objects)
				selectObject(obj);

			for (Triangle t : triangles)
				selectTriangle(t);
		}
	}

	private class ModifyTriangles extends AbstractCommand
	{
		private final List<Triangle> addList;
		private final List<Triangle> removeList;
		private boolean canDeselectMesh;

		public ModifyTriangles(String msg, Iterable<Triangle> addList, Iterable<Triangle> removeList, boolean canDeselectMesh)
		{
			super(msg);

			this.addList = new ArrayList<>();
			if (addList != null)
				for (Triangle t : addList)
					this.addList.add(t);

			this.removeList = new ArrayList<>();
			if (removeList != null)
				for (Triangle t : removeList)
					this.removeList.add(t);

			this.canDeselectMesh = canDeselectMesh;
		}

		@Override
		public boolean modifiesMap()
		{
			return false;
		}

		@Override
		public void exec()
		{
			super.exec();

			for (Triangle t : removeList)
				deselectTriangle(t, canDeselectMesh);

			for (Triangle t : addList)
				selectTriangle(t);
		}

		@Override
		public void undo()
		{
			super.undo();

			for (Triangle t : addList)
				deselectTriangle(t, canDeselectMesh);

			for (Triangle t : removeList)
				selectTriangle(t);
		}
	}

	public AbstractCommand getModifyTriangles(Iterable<Triangle> addList, Iterable<Triangle> removeList, boolean canDeselectMesh)
	{
		return new ModifyTriangles("Change Triangle Selection", addList, removeList, canDeselectMesh);
	}

	private AbstractCommand getAddTriangles(Iterable<Triangle> addList)
	{
		return new ModifyTriangles("Select Triangles", addList, null, false);
	}

	/**
	 * This is the general command for modifying selections. Use one of the convenience
	 * methods below to operate on specific selections.
	 */
	private static class ModifySelection<T extends Selectable> extends AbstractCommand
	{
		private final Selection<T> selection;
		private final List<T> addList;
		private final List<T> removeList;

		public ModifySelection(String msg, Selection<T> selection, Iterable<T> addList, Iterable<T> removeList)
		{
			super(msg);

			this.selection = selection;

			this.addList = new ArrayList<>();
			if (addList != null)
				for (T selectable : addList)
					this.addList.add(selectable);

			this.removeList = new ArrayList<>();
			if (removeList != null)
				for (T selectable : removeList)
					this.removeList.add(selectable);
		}

		@Override
		public boolean modifiesMap()
		{
			return false;
		}

		@Override
		public void exec()
		{
			super.exec();

			for (T selectable : removeList)
				selection.removeAndDeselect(selectable);

			for (T selectable : addList)
				selection.addAndSelect(selectable);
		}

		@Override
		public void undo()
		{
			super.undo();

			for (T selectable : addList)
				selection.removeAndDeselect(selectable);

			for (T selectable : removeList)
				selection.addAndSelect(selectable);
		}
	}

	private AbstractCommand getModifyVertices(Iterable<Vertex> addList, Iterable<Vertex> removeList)
	{
		return new ModifySelection<>("Change Vertex Selection",
			vertexSelection, addList, removeList);
	}

	/*
	private AbstractCommand getAddVertices(Iterable<Vertex> addList)
	{
		return new ModifySelection<Vertex>("Select Vertices",
				vertexSelection, addList, null);
	}
	*/

	private AbstractCommand getClearVertices()
	{ return new ModifySelection<>("Clear Vertex Selection",
		vertexSelection, null, vertexSelection.selectableList); }

	private AbstractCommand getModifyUVs(Iterable<UV> addList, Iterable<UV> removeList)
	{
		return new ModifySelection<>("Change UV Selection",
			uvSelection, addList, removeList);
	}

	public AbstractCommand getClearUVs()
	{ return new ModifySelection<>("Clear UV Selection",
		uvSelection, null, uvSelection.selectableList); }

	public AbstractCommand getModifyPoints(Iterable<SelectablePoint> addList, Iterable<SelectablePoint> removeList)
	{
		return new ModifySelection<>("Change Selection",
			pointSelection, addList, removeList);
	}

	public AbstractCommand getClearPoints()
	{ return new ModifySelection<>("Clear Point Selection",
		pointSelection, null, pointSelection.selectableList); }
}
