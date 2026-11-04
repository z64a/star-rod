package game.map.editor;

import java.util.ArrayList;
import java.util.List;

import common.Vector3f;
import common.commands.CommandBatch;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.render.PresetColor;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.selection.SelectablePoint;
import game.map.editor.selection.SelectionManager;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.PathComponent;
import game.map.marker.PathData;
import game.map.marker.PathData.AddNextPathPoint;
import game.map.marker.PathData.DeletePathPoint;
import game.map.marker.PathData.InsertPathPoint;
import game.map.marker.PathData.RouteThroughPoint;
import game.map.marker.PathData.SetConnected;
import game.map.marker.PathPoint;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;

/**
 * What the mouse does to the paths of Path markers in Modify mode, which shows every one of them:
 * <ul>
 * <li>Clicking a point selects it, and its marker.</li>
 * <li>Clicking a connection selects its marker.</li>
 * <li>Dragging from a point to another point of its path connects them, or disconnects them if it already leads
 * there.</li>
 * <li>Dragging from a point to anywhere else on the map adds a point there, that it leads to.</li>
 * <li>Dragging from a connection adds a point where the drag ends, or routes the connection through the point of its
 * path the drag ends on.</li>
 * <li>Drags snap to the points of their path near the pointer.</li>
 * <li>Right-clicking a point deletes it, and right-clicking a connection removes it.</li>
 * </ul>
 */
public class PathTool
{
	private static final float GHOST_ALPHA = 0.5f;
	/** How much farther than a click a drag reaches to snap to a point. */
	private static final float SNAP_REACH = 2.5f;

	private final MapEditor editor;

	private PathPoint dragFrom = null;
	/** The connection a drag splits with a new point. */
	private PathPoint splitFrom = null;
	private PathPoint splitTo = null;
	private boolean dragging = false;
	private PathPoint dragToPoint = null;
	private Vector3f dragTo = null;
	private PathPoint hoverPoint = null;
	private Vector3f hoverInsert = null;

	public PathTool(MapEditor editor)
	{
		this.editor = editor;
	}

	/** Handles a left click, and returns whether it was on a point or connection, which a drag can start from. */
	public boolean press(PickRay ray)
	{
		reset();

		PickHit hit = pickPoint(ray, null);
		if (!hit.missed()) {
			dragFrom = (PathPoint) hit.obj;
			select(dragFrom.path, dragFrom, editor.keyboard.isCtrlDown());
			return true;
		}

		PathPoint[] connection = new PathPoint[2];
		hit = pickConnection(ray, connection);
		if (!hit.missed()) {
			splitFrom = connection[0];
			splitTo = connection[1];
			select(splitFrom.path, null, false);
			return true;
		}

		return false;
	}

	/** Deletes the point a right click is on, or else removes the connection it's on. */
	public void rightClick(PickRay ray)
	{
		PickHit hit = pickPoint(ray, null);
		if (!hit.missed()) {
			MapEditor.execute(new DeletePathPoint((PathPoint) hit.obj));
			return;
		}

		PathPoint[] connection = new PathPoint[2];
		hit = pickConnection(ray, connection);
		if (!hit.missed())
			MapEditor.execute(new SetConnected(connection[0], connection[1], false));
	}

	/** Starts dragging from the point or connection the left click was on, and returns whether it was on one. */
	public boolean startDrag()
	{
		dragging = (dragFrom != null || splitFrom != null);
		return dragging;
	}

	/**
	 * Follows the pointer: while dragging, to the point of the path or the place on the map it's over, and otherwise, to
	 * the point it's over, or the place on a connection a drag would add one.
	 */
	public void update(PickRay ray, MapEditViewport view)
	{
		if (!dragging) {
			PickHit hit = pickPoint(ray, null);
			hoverPoint = hit.missed() ? null : (PathPoint) hit.obj;
			hoverInsert = null;
			if (hoverPoint == null) {
				PathPoint[] connection = new PathPoint[2];
				hit = pickConnection(ray, connection);
				if (!hit.missed())
					hoverInsert = closestOnSegment(hit.point, connection[0], connection[1]);
			}
			return;
		}

		if (splitFrom != null) {
			PickHit hit = pickPoint(ray, splitFrom.path, SNAP_REACH);
			dragToPoint = (hit.missed() || hit.obj == splitFrom || hit.obj == splitTo) ? null : (PathPoint) hit.obj;
			if (dragToPoint != null) {
				dragTo = position(dragToPoint);
				return;
			}
			hit = editor.selectionManager.pickWorld(editor.getGeometryMap(), ray, view, true);
			dragTo = hit.missed() ? null : hit.point;
			return;
		}

		// a drag that hasn't left the point it started from ends nowhere, so a slow click doesn't add a point
		PickHit hit = pickPoint(ray, dragFrom.path, SNAP_REACH);
		dragToPoint = (hit.missed() || hit.obj == dragFrom) ? null : (PathPoint) hit.obj;
		if (!hit.missed()) {
			dragTo = (dragToPoint == null) ? null : position(dragToPoint);
			return;
		}

		hit = editor.selectionManager.pickWorld(editor.getGeometryMap(), ray, view, true);
		dragTo = hit.missed() ? null : hit.point;
	}

	/** Finishes a click, or a drag, connecting or adding a point where the drag ended. */
	public void release()
	{
		if (dragging && splitFrom != null && dragToPoint != null)
			MapEditor.execute(new RouteThroughPoint(splitFrom, splitTo, dragToPoint));
		else if (dragging && splitFrom != null && dragTo != null) {
			MapEditor.execute(new InsertPathPoint(splitFrom, splitTo,
				Math.round(dragTo.x), Math.round(dragTo.y), Math.round(dragTo.z)));
		}
		else if (dragging && dragToPoint != null)
			MapEditor.execute(new SetConnected(dragFrom, dragToPoint, !leads(dragFrom, dragToPoint)));
		else if (dragging && dragTo != null) {
			MapEditor.execute(new AddNextPathPoint(dragFrom,
				Math.round(dragTo.x), Math.round(dragTo.y), Math.round(dragTo.z)));
		}
		reset();
	}

	private void reset()
	{
		dragFrom = null;
		splitFrom = null;
		splitTo = null;
		dragging = false;
		dragToPoint = null;
		dragTo = null;
	}

	private static boolean leads(PathPoint from, PathPoint to)
	{
		return from.path.successors(from).stream().anyMatch(point -> point == to);
	}

	/**
	 * Draws what a click or drag would do: the point under the pointer, or the one a drag snaps to, larger; a ghost of the
	 * point a drag from a connection would add; and while dragging, a line to where it would end, red if it would
	 * disconnect, with a ghost of the point it would add, or the connection it's splitting, through a ghost of the point
	 * it would add.
	 */
	public void render()
	{
		RenderState.setModelMatrix(null);
		RenderState.enableDepthTest(false);

		if (dragging && splitFrom != null && dragTo != null) {
			RenderState.setLineWidth(3.0f);
			RenderState.setColor(PresetColor.WHITE);
			int ghost = LineRenderQueue.addVertex().setPosition(dragTo.x, dragTo.y, dragTo.z).getIndex();
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(splitFrom.getX(), splitFrom.getY(), splitFrom.getZ()).getIndex(), ghost,
				LineRenderQueue.addVertex().setPosition(splitTo.getX(), splitTo.getY(), splitTo.getZ()).getIndex());
			LineRenderQueue.render(true);
			if (dragToPoint == null)
				PathComponent.renderOutlinedPoints(List.of(dragTo), PresetColor.YELLOW, PathComponent.POINT_SIZE, GHOST_ALPHA);
		}
		else if (dragging && dragFrom != null && dragTo != null) {
			RenderState.setLineWidth(3.0f);
			boolean disconnects = (dragToPoint != null) && leads(dragFrom, dragToPoint);
			RenderState.setColor(disconnects ? PresetColor.RED : PresetColor.WHITE);
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(dragFrom.getX(), dragFrom.getY(), dragFrom.getZ()).getIndex(),
				LineRenderQueue.addVertex().setPosition(dragTo.x, dragTo.y, dragTo.z).getIndex());
			LineRenderQueue.render(true);
			if (dragToPoint == null)
				PathComponent.renderOutlinedPoints(List.of(dragTo), PresetColor.YELLOW, PathComponent.POINT_SIZE, GHOST_ALPHA);
		}

		PathPoint highlighted = dragging ? dragToPoint : hoverPoint;
		if (highlighted != null) {
			PathComponent.renderOutlinedPoints(List.of(position(highlighted)),
				highlighted.isSelected() ? PresetColor.PINK : PresetColor.YELLOW, PathComponent.POINT_SIZE + 6, 1.0f);
		}
		else if (!dragging && hoverInsert != null) {
			PathComponent.renderOutlinedPoints(List.of(hoverInsert), PresetColor.YELLOW, PathComponent.POINT_SIZE, GHOST_ALPHA);
		}

		RenderState.enableDepthTest(true);
	}

	/** The paths of the map's Path markers that aren't hidden. */
	private List<PathData> paths()
	{
		List<PathData> paths = new ArrayList<>();
		for (Marker marker : editor.map.markerTree) {
			if (marker.getType() == MarkerType.Path && !marker.hidden)
				paths.add(marker.pathComponent.path);
		}
		return paths;
	}

	/** The point closest along the ray, of `only` if it isn't null, or of any path, with the point as the hit's obj. */
	private PickHit pickPoint(PickRay ray, PathData only)
	{
		return pickPoint(ray, only, 1.0f);
	}

	private PickHit pickPoint(PickRay ray, PathData only, float reach)
	{
		return editor.selectionManager.pickPathPoint(editor.map, ray,
			marker -> only == null || marker.pathComponent.path == only, reach);
	}

	/** The connection closest along the ray, with the point it's from and the point it's to put in `connection`. */
	private PickHit pickConnection(PickRay ray, PathPoint[] connection)
	{
		PickHit closest = new PickHit(ray);
		for (PathData path : paths()) {
			for (PathPoint from : path.points) {
				for (PathPoint to : path.successors(from)) {
					PickHit hit = PickRay.getSegmentIntersection(ray, position(from), position(to), 1.0f);
					if (hit.dist < closest.dist) {
						closest = hit;
						connection[0] = from;
						connection[1] = to;
					}
				}
			}
		}
		return closest;
	}

	/**
	 * Selects the marker of `path` in point selection, with `point` in place of the selected points, or toggled among
	 * them if `additive`, or with no points if `point` is null.
	 */
	private void select(PathData path, PathPoint point, boolean additive)
	{
		SelectionManager selection = editor.selectionManager;
		CommandBatch batch = new CommandBatch("Select Path Point");
		batch.setModifiesData(false);

		if (selection.getSelectionMode() != SelectionMode.POINT)
			batch.addCommand(selection.new SetSelectionMode(SelectionMode.POINT));

		Marker marker = path.getMarker();
		List<?> objects = selection.getSelectedObjects();
		if (objects.size() != 1 || objects.get(0) != marker) {
			batch.addCommand(selection.getSetObjects(List.of(marker)));
			additive = false;
		}

		List<SelectablePoint> added = new ArrayList<>();
		List<SelectablePoint> removed = new ArrayList<>();
		if (point == null)
			removed.addAll(selection.pointSelection.selectableList);
		else if (additive) {
			if (point.isSelected())
				removed.add(point);
			else
				added.add(point);
		}
		else if (!point.isSelected()) {
			removed.addAll(selection.pointSelection.selectableList);
			added.add(point);
		}
		if (!added.isEmpty() || !removed.isEmpty())
			batch.addCommand(selection.getModifyPoints(added, removed));

		if (batch.shouldExec())
			MapEditor.execute(batch);
	}

	private static Vector3f position(PathPoint point)
	{
		return new Vector3f(point.getX(), point.getY(), point.getZ());
	}

	/** The point of the segment from `a` to `b` closest to `near`. */
	private static Vector3f closestOnSegment(Vector3f near, PathPoint a, PathPoint b)
	{
		Vector3f start = position(a);
		Vector3f along = Vector3f.sub(position(b), start);
		float lengthSquared = Vector3f.dot(along, along);
		float s = (lengthSquared == 0) ? 0 : Vector3f.dot(Vector3f.sub(near, start), along) / lengthSquared;
		s = Math.max(0.0f, Math.min(1.0f, s));
		return new Vector3f(start.x + s * along.x, start.y + s * along.y, start.z + s * along.z);
	}
}
