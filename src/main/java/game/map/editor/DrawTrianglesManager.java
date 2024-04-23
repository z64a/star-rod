package game.map.editor;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import game.map.Axis;
import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.editor.MapEditor.EditorMode;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.camera.OrthographicViewport;
import game.map.editor.commands.CreateObject;
import game.map.editor.geometry.GeometryUtils;
import game.map.editor.geometry.Vector3f;
import game.map.editor.render.PreviewDrawMode;
import game.map.editor.render.PreviewGeometry;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import util.Logger;
import util.MathUtil;

public class DrawTrianglesManager
{
	private static enum DrawMode
	{
		Inactive, Triangles, Edges, Polygon, Cut
	}

	private DrawMode drawMode;

	private final MapEditor editor;
	private final PreviewGeometry preview;

	private OrthographicViewport workingViewport;
	private MapObjectType currentType;

	private Vector3f anchorPos;
	private List<Vector3f> pointList;

	private boolean done;
	private boolean success;

	public DrawTrianglesManager(MapEditor editor, PreviewGeometry preview)
	{
		this.editor = editor;

		preview.drawMode = PreviewDrawMode.FILLED;
		preview.useDepth = false;
		this.preview = preview;

		pointList = new ArrayList<>(32);
		reset();
	}

	public boolean isActive()
	{ return drawMode != DrawMode.Inactive; }

	private void reset()
	{
		drawMode = DrawMode.Inactive;
		workingViewport = null;
		currentType = MapObjectType.MODEL;
		preview.clear();
		pointList.clear();
		anchorPos = null;
		done = false;
		success = false;
	}

	public void tryAddVertex(Vector3f pickOrigin, MapEditViewport activeView)
	{
		if (!(activeView instanceof OrthographicViewport))
			return;

		if (workingViewport == null) {
			// we are drawing the first point
			workingViewport = (OrthographicViewport) activeView;
			initializeDrawing();
		}

		if (activeView != workingViewport)
			return;

		Vector3f newPoint = new Vector3f(pickOrigin);

		// project
		switch (activeView.type) {
			case FRONT:
				newPoint.z = anchorPos.z;
				break;
			case SIDE:
				newPoint.x = anchorPos.x;
				break;
			case TOP:
				newPoint.y = anchorPos.y;
				break;
			case PERSPECTIVE:
				throw new IllegalStateException();
		}

		if (editor.gridEnabled) {
			// round to nearest gridline
			int spacing = editor.grid.getSpacing();
			newPoint.x = spacing * (int) Math.round((double) newPoint.x / spacing);
			newPoint.y = spacing * (int) Math.round((double) newPoint.y / spacing);
			newPoint.z = spacing * (int) Math.round((double) newPoint.z / spacing);
		}

		if (canAddPoint(newPoint))
			pointList.add(newPoint);

		if (drawMode == DrawMode.Triangles) {
			Axis viewportAxis = workingViewport.camera.getRotationAxis();
			TriangleBatch createBatch = GeometryUtils.getDelaunayBatch(pointList, viewportAxis, currentType == MapObjectType.MODEL);
			if (createBatch != null)
				preview.batch = createBatch;
			preview.points = pointList;
		}
		else if (drawMode == DrawMode.Edges) {
			preview.edges = pointList;
			preview.points = pointList;
		}
		else if (drawMode == DrawMode.Polygon) {
			preview.edges = pointList;
			preview.points = pointList;
		}
		else if (drawMode == DrawMode.Cut) {
			preview.edges = pointList;
			preview.points = pointList;

			if (pointList.size() > 2) {
				done = true;
				success = true;

				Vector3f AB = Vector3f.sub(pointList.get(1), pointList.get(0));
				Vector3f AC = Vector3f.sub(pointList.get(2), pointList.get(0));

				Vector3f uAB = Vector3f.getNormalized(AB);
				Vector3f proj = Vector3f.getScaled(uAB, Vector3f.dot(uAB, AC));

				Vector3f delta = Vector3f.sub(AC, proj);

				if (delta.length() > MathUtil.SMALL_NUMBER) {
					new TriangleCutter(pointList.get(0), delta.normalize(), editor.selectionManager.getSelectedObjects());
				}
			}
		}
	}

	private boolean canAddPoint(Vector3f newPoint)
	{
		// loop closure
		if ((drawMode == DrawMode.Edges || drawMode == DrawMode.Polygon) && pointList.size() > 2) {
			if (newPoint.equals(pointList.get(0))) {
				done = true;
				return true;
			}
		}

		// don't allow duplicates
		if (pointList.size() > 0) {
			for (Vector3f v : pointList) {
				if (newPoint.equals(v))
					return false;
			}
		}

		// no intersections
		if (drawMode == DrawMode.Polygon && pointList.size() > 1) {
			Axis viewportAxis = workingViewport.camera.getRotationAxis();
			List<Vector3f> pointsXZ = GeometryUtils.pointsToXZ(pointList, viewportAxis);
			Vector3f newPointXZ = GeometryUtils.pointToXZ(newPoint, viewportAxis);

			Vector3f last = pointsXZ.get(pointsXZ.size() - 1);
			for (int i = 0; i < pointsXZ.size() - 1; i++) {
				Vector3f a = pointsXZ.get(i);
				Vector3f b = pointsXZ.get(i + 1);

				if (GeometryUtils.doLineSegmentsIntersectXZ(last, newPointXZ, a, b))
					return false;
			}
		}

		// survived the tests
		return true;
	}

	private void postStatusToViewport(MapEditViewport activeView, String text)
	{
		if (workingViewport != null) {
			workingViewport.setTextLL(text, true);
			return;
		}

		if (activeView == null || !(activeView instanceof OrthographicViewport))
			return;

		activeView.setTextLL(text, true);
	}

	public void tick(MapEditViewport activeView)
	{
		if (editor.getEditorMode() == EditorMode.Modify) {
			boolean trisModeKey = editor.keyboard.isKeyDown(KeyEvent.VK_COMMA);
			boolean edgeModeKey = editor.keyboard.isKeyDown(KeyEvent.VK_SLASH);
			boolean polyModeKey = editor.keyboard.isKeyDown(KeyEvent.VK_PERIOD);
			boolean cutModeKey = editor.keyboard.isKeyDown(KeyEvent.VK_BACK_QUOTE);

			switch (drawMode) {
				case Inactive: {
					if (trisModeKey) {
						drawMode = DrawMode.Triangles;
						Logger.log("Started drawing convex.");
					}
					else if (edgeModeKey) {
						drawMode = DrawMode.Edges;
						Logger.log("Started drawing wall.");
					}
					else if (polyModeKey) {
						drawMode = DrawMode.Polygon;
						Logger.log("Started drawing concave.");
					}
					else if (cutModeKey) {
						drawMode = DrawMode.Cut;
						Logger.log("Started cut.");
					}
				}
					break;

				case Triangles: {
					postStatusToViewport(activeView, "Drawing Convex");
					preview.visible = true;
					if (!trisModeKey) {
						if (pointList.size() > 2) {
							Axis viewportAxis = workingViewport.camera.getRotationAxis();
							TriangleBatch createBatch = GeometryUtils.getDelaunayBatch(pointList, viewportAxis, currentType == MapObjectType.MODEL);
							for (Triangle t : createBatch.triangles)
								t.flipNormal(); // just live with it.
							createTriangles(createBatch);
						}

						if (success)
							Logger.log("Finished drawing convex.");
						else
							Logger.log("Drawing convex aborted.");

						reset();
						preview.clear();
					}
				}
					break;

				case Edges: {
					postStatusToViewport(activeView, "Drawing Wall");
					preview.visible = true;
					if (!edgeModeKey || done) {
						if (pointList.size() > 1) {
							Axis viewportAxis = workingViewport.camera.getRotationAxis();
							TriangleBatch createBatch = GeometryUtils.getStripBatch(pointList, viewportAxis, 64, currentType == MapObjectType.MODEL);
							createTriangles(createBatch);
						}

						if (success)
							Logger.log("Finished drawing wall.");
						else
							Logger.log("Drawing wall aborted.");

						reset();
						preview.clear();
					}
				}
					break;

				case Polygon: {
					postStatusToViewport(activeView, "Drawing Concave");
					preview.visible = true;
					if (!polyModeKey || done) {
						if (pointList.size() > 2) {
							Vector3f first = pointList.get(0);
							Vector3f last = pointList.get(pointList.size() - 1);
							if (!first.equals(last))
								pointList.add(first);

							Axis viewportAxis = workingViewport.camera.getRotationAxis();
							TriangleBatch createBatch = null;

							// the lazy way, just try it both ways and give up if neither works
							try {
								createBatch = GeometryUtils.getPolygonBatch(pointList, viewportAxis, currentType == MapObjectType.MODEL);
							}
							catch (Exception e1) {
								Collections.reverse(pointList);
								try {
									createBatch = GeometryUtils.getPolygonBatch(pointList, viewportAxis, currentType == MapObjectType.MODEL);
								}
								catch (Exception e2) {
									createBatch = null;
								}
							}

							if (createBatch != null)
								createTriangles(createBatch);
						}

						if (success)
							Logger.log("Finished drawing concave.");
						else
							Logger.log("Drawing concave aborted.");

						reset();
						preview.clear();
					}
				}
					break;

				case Cut: {
					if (pointList.size() < 2)
						postStatusToViewport(activeView, "Cutting Triangles - Choose Plane");
					else
						postStatusToViewport(activeView, "Cutting Triangles - Choose Side");

					preview.visible = true;
					if (!cutModeKey || done) {
						if (success)
							Logger.log("Finished cut.");
						else
							Logger.log("Aborted cut.");

						reset();
						preview.clear();
					}
				}
					break;
			}
		}
		else if (drawMode != DrawMode.Inactive) {
			reset();
			preview.clear();
		}
	}

	private void initializeDrawing()
	{
		currentType = editor.gui.getObjectTab();
		anchorPos = editor.getCursorPosition();

		List<Vertex> selectedVerts = editor.selectionManager.getVerticesFromSelection();
		if (selectedVerts.size() > 0) {
			int count = 0;

			for (Vertex v : selectedVerts) {
				anchorPos.x += v.getCurrentX();
				anchorPos.y += v.getCurrentY();
				anchorPos.z += v.getCurrentZ();
				count++;
			}

			anchorPos.x /= count;
			anchorPos.y /= count;
			anchorPos.z /= count;
		}
	}

	private void createTriangles(TriangleBatch batch)
	{
		if (batch == null)
			return;

		// create new object of current type
		MapObject newObj = getDrawnObject(batch, currentType);
		editor.executeNextFrame(new CreateObject(newObj));

		success = true;
	}

	private MapObject getDrawnObject(TriangleBatch batch, MapObjectType type)
	{
		switch (type) {
			default:
			case MODEL:
				Model mdl = Model.create(batch, "DrawnModel");
				mdl.getMesh().setTexture(editor.selectionManager.selectedTexture);
				return mdl;
			case COLLIDER:
				return Collider.create(batch, "DrawnCollider");
			case ZONE:
				return Zone.create(batch, "DrawnZone");
		}
	}
}
