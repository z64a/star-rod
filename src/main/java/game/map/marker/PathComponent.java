package game.map.marker;

import static game.map.MapKey.*;
import static org.lwjgl.opengl.GL11.GL_ALWAYS;
import static org.lwjgl.opengl.GL11.GL_GREATER;
import static org.lwjgl.opengl.GL11.GL_LEQUAL;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import common.Vector3f;
import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;
import common.commands.EditableField.StandardBoolName;

import game.map.JsonFeatures.JsonMarker;
import game.map.JsonFeatures.JsonPathComp;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.editor.MapEditor.EditorMode;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.camera.ViewType;
import game.map.editor.render.PresetColor;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.SelectablePoint;
import game.map.editor.ui.info.MarkerInfoPanel;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.buffers.TriangleRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicSolidShader;
import util.Logger;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class PathComponent extends BaseMarkerComponent
{
	public final PathData path;

	public EditableField<Boolean> showInterp = EditableFieldFactory.create(true)
		.setCallback((v) -> parentMarker.updateListeners(MarkerInfoPanel.TAG_PATH))
		.setName(new StandardBoolName("Show Interp")).build();

	/** The length of the arrows along connections, scaled by the view so they're the same size on screen. */
	private static final float ARROW_SIZE = 4.0f;
	/** About how far apart the arrows along a connection are. */
	private static final float ARROW_SPACING = 80.0f;
	/** How fast the arrows move along connections, in units per second. */
	private static final float ARROW_SPEED = 40.0f;
	/** The size of a point's color, inside its outline and shadow. */
	public static final float POINT_SIZE = 16.0f;
	/** How opaque the path of a marker that isn't selected is. */
	private static final float UNSELECTED_ALPHA = 0.4f;

	private float[] interpLens;
	private Vector3f[] interpVecs;

	public PathComponent(Marker marker)
	{
		super(marker);
		path = new PathData(marker, MarkerInfoPanel.TAG_PATH);
	}

	@Override
	public PathComponent deepCopy(Marker copyParent)
	{
		PathComponent copy = new PathComponent(copyParent);
		for (PathPoint wp : path.points)
			copy.path.points.addElement(new PathPoint(copy.path, wp.getX(), wp.getY(), wp.getZ()));
		copy.path.setGraph(path.getNames(), path.getEdges());

		copy.showInterp.set(showInterp.get());
		return copy;
	}

	@Override
	protected void fromJson(JsonMarker in)
	{
		if (in.pathComp == null)
			return;

		showInterp.set(in.pathComp.showInterp);

		if (in.pathComp.waypoints != null) {
			for (int i = 0; i < in.pathComp.waypoints.length; i++) {
				int[] wp = in.pathComp.waypoints[i];
				if (wp == null || wp.length != 3) {
					Logger.logError("PathComponent: waypoint " + i + " pos must have exactly 3 elements");
					continue;
				}
				path.points.addElement(new PathPoint(path, wp[0], wp[1], wp[2]));
			}
		}
		path.setGraph(in.pathComp.names, in.pathComp.edges);
	}

	@Override
	protected void toJson(JsonMarker out)
	{
		out.pathComp = new JsonPathComp();
		out.pathComp.showInterp = showInterp.get();
		out.pathComp.names = path.getNames();
		out.pathComp.edges = path.getEdges();

		out.pathComp.waypoints = new int[path.points.size()][];
		for (int i = 0; i < path.points.size(); i++) {
			PathPoint p = path.points.get(i);
			out.pathComp.waypoints[i] = new int[] { p.getX(), p.getY(), p.getZ() };
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag pathTag = xmw.createTag(TAG_MARKER_PATH, false);
		xmw.addBoolean(pathTag, ATTR_PATH_INTERP, showInterp.get());
		xmw.openTag(pathTag);
		for (SelectablePoint p : path.points) {
			XmlTag wpTag = xmw.createTag(TAG_MARKER_WP, true);
			xmw.addIntArray(wpTag, ATTR_MARKER_POS, p.getX(), p.getY(), p.getZ());
			xmw.printTag(wpTag);
		}
		xmw.closeTag(pathTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element markerElem)
	{
		Element pathElement = xmr.getUniqueRequiredTag(markerElem, TAG_MARKER_PATH);
		if (xmr.hasAttribute(pathElement, ATTR_PATH_INTERP)) {
			showInterp.set(xmr.readBoolean(pathElement, ATTR_PATH_INTERP));
		}

		List<Element> waypointElems = xmr.getTags(pathElement, TAG_MARKER_WP);

		for (Element waypointElem : waypointElems) {
			int[] pos = xmr.readIntArray(waypointElem, ATTR_MARKER_POS, 3);
			path.points.addElement(new PathPoint(path, pos[0], pos[1], pos[2]));
		}
	}

	@Override
	public boolean hasSelectablePoints()
	{
		return true;
	}

	@Override
	public void addSelectablePoints(List<SelectablePoint> points)
	{
		for (PathPoint wp : path.points)
			points.add(wp);
	}

	@Override
	public void addToBackup(IdentityHashSet<PointBackup> backupList)
	{
		for (PathPoint wp : path.points)
			backupList.add(wp.point.getBackup());
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		for (PathPoint wp : path.points)
			positions.add(wp.point);
	}

	@Override
	public void startTransformation()
	{
		for (PathPoint wp : path.points)
			wp.point.startTransform();
	}

	@Override
	public void endTransformation()
	{
		for (PathPoint wp : path.points)
			wp.point.endTransform();
	}

	@Override
	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		super.render(opts, view, renderer);

		if (!shown(opts))
			return;

		boolean drawInterp = showInterp.get() && path.points.size() > 2 && path.isLinear();
		if (drawInterp)
			computeInterpolation();

		boolean editPointsMode = true;
		boolean drawHiddenPaths = (view.type == ViewType.PERSPECTIVE);

		RenderState.setPointSize(editPointsMode ? 12.0f : 8.0f);
		RenderState.setLineWidth(3.0f);

		/*
		RenderState.setColor(Colors.GREEN);
		int max = 1 + path.points.size() * 8;
		for (int i = 0; i <= max; i++) {
			Vector3f pos = getInterpPos((float) i / max);
			PointRenderQueue.addPoint().setPosition(pos.x, pos.y, pos.z);
		}
		*/

		if (drawHiddenPaths)
			RenderState.setDepthFunc(GL_LEQUAL);

		setColor(PresetColor.YELLOW);
		for (PathPoint from : path.points) {
			for (PathPoint to : path.successors(from)) {
				addLine(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
			}
		}

		try {
			if (drawInterp) {
				setColor(PresetColor.GREEN);
				int max = 1 + path.points.size() * 8;

				Vector3f lastInterp = null;
				Vector3f nextInterp = null;
				for (int i = 0; i <= max + 1; i++) {
					if (i <= max)
						nextInterp = getInterpPos((float) i / max);

					if (lastInterp != null) {
						LineRenderQueue.addLine(
							LineRenderQueue.addVertex().setPosition(lastInterp.x, lastInterp.y, lastInterp.z).getIndex(),
							LineRenderQueue.addVertex().setPosition(nextInterp.x, nextInterp.y, nextInterp.z).getIndex());
					}
					lastInterp = nextInterp;
				}
			}
		}
		catch (Throwable t) {
			Logger.logError(t.getMessage());
		}

		LineRenderQueue.render(true);

		if (drawHiddenPaths) {
			setColor(PresetColor.YELLOW);
			for (PathPoint from : path.points) {
				for (PathPoint to : path.successors(from)) {
					Renderer.queueStipple(
						from.getX(), from.getY(), from.getZ(),
						to.getX(), to.getY(), to.getZ(),
						10.0f);
				}
			}

			// flush the line render queue with depth writing disabled
			RenderState.setDepthWrite(false);
			RenderState.setDepthFunc(GL_GREATER);
			LineRenderQueue.render(true);
			RenderState.setDepthWrite(true);

			RenderState.initDepthFunc();
		}
	}

	private boolean shown(RenderingOptions opts)
	{
		return !path.points.isEmpty() && (parentMarker.selected || opts.editorMode == EditorMode.Modify);
	}

	private float alpha()
	{
		return parentMarker.selected ? 1.0f : UNSELECTED_ALPHA;
	}

	/** Draws the points and arrows, which go after the rest of the map so they can be drawn over it. */
	public void renderPoints(RenderingOptions opts, MapEditViewport view)
	{
		if (!shown(opts))
			return;

		// they're drawn over everything, so they can be seen and clicked where they're hidden
		RenderState.setModelMatrix(null);
		RenderState.setDepthFunc(GL_ALWAYS);
		renderArrows(view);
		for (boolean selected : new boolean[] { false, true }) {
			List<Vector3f> positions = new ArrayList<>();
			for (SelectablePoint point : path.points) {
				if (point.isSelected() == selected)
					positions.add(new Vector3f(point.getX(), point.getY(), point.getZ()));
			}
			renderOutlinedPoints(positions, selected ? PresetColor.PINK : PresetColor.YELLOW, POINT_SIZE, alpha());
		}
		RenderState.initDepthFunc();
	}

	/** Draws points as a path's are drawn, with a black shadow and a white outline around `color`, `size` across. */
	public static void renderOutlinedPoints(List<Vector3f> positions, PresetColor color, float size, float alpha)
	{
		for (int layer = 0; layer < 3; layer++) {
			switch (layer) {
				case 0 -> RenderState.setColor(0.0f, 0.0f, 0.0f, alpha);
				case 1 -> RenderState.setColor(1.0f, 1.0f, 1.0f, alpha);
				default -> RenderState.setColor(color.r, color.g, color.b, alpha);
			}
			RenderState.setPointSize(size + 4 * (2 - layer));
			for (Vector3f position : positions)
				PointRenderQueue.addPoint().setPosition(position.x, position.y, position.z);
			PointRenderQueue.render(true);
		}
	}

	private void setColor(PresetColor color)
	{
		RenderState.setColor(color.r, color.g, color.b, alpha());
	}

	private static void addLine(float ax, float ay, float az, float bx, float by, float bz)
	{
		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(ax, ay, az).getIndex(),
			LineRenderQueue.addVertex().setPosition(bx, by, bz).getIndex());
	}

	/** Draws arrows that move along each connection in the direction it leads. */
	private void renderArrows(MapEditViewport view)
	{
		Vector3f forward = view.camera.getForward(1.0f);
		double travelled = (System.nanoTime() / 1e9) * ARROW_SPEED;

		for (PathPoint from : path.points) {
			for (PathPoint to : path.successors(from)) {
				Vector3f start = new Vector3f(from.getX(), from.getY(), from.getZ());
				Vector3f along = Vector3f.sub(new Vector3f(to.getX(), to.getY(), to.getZ()), start);
				float length = along.length();
				along.normalize();
				// arrows lie level across their connection, so they run along the floor with it, except on one that
				// goes straight up or down, where they face the view
				Vector3f side = Vector3f.cross(along, PickRay.UP);
				if (side.length() < 0.01f)
					side = Vector3f.cross(along, forward);
				if (length < 1.0f || side.length() < 0.01f)
					continue;
				side.normalize();

				int count = Math.max(1, Math.round(length / ARROW_SPACING));
				float gap = length / count;
				float offset = (float) (travelled % gap);
				for (int i = 0; i < count; i++) {
					float distance = offset + i * gap;
					Vector3f tip = Vector3f.add(start, Vector3f.getScaled(along, distance));
					float size = ARROW_SIZE * view.getScaleFactor(tip.x, tip.y, tip.z);
					Vector3f back = Vector3f.sub(tip, Vector3f.getScaled(along, size));
					Vector3f notch = Vector3f.sub(tip, Vector3f.getScaled(along, size * 0.6f));
					Vector3f left = Vector3f.add(back, Vector3f.getScaled(side, size * 0.6f));
					Vector3f right = Vector3f.sub(back, Vector3f.getScaled(side, size * 0.6f));
					int tipIndex = addVertex(tip);
					int notchIndex = addVertex(notch);
					TriangleRenderQueue.addTriangle(tipIndex, addVertex(left), notchIndex);
					TriangleRenderQueue.addTriangle(tipIndex, notchIndex, addVertex(right));
				}
			}
		}

		RenderState.setPolygonMode(PolygonMode.FILL);
		BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
		shader.multiplyVertexColor.set(false);
		shader.baseColor.set(PresetColor.YELLOW.r, PresetColor.YELLOW.g, PresetColor.YELLOW.b, alpha());
		TriangleRenderQueue.render(shader, true);
		shader.multiplyVertexColor.set(true);
		shader.baseColor.set(1.0f, 1.0f, 1.0f, 1.0f);
	}

	private static int addVertex(Vector3f position)
	{
		return TriangleRenderQueue.addVertex().setPosition(position.x, position.y, position.z).getIndex();
	}

	private void computeInterpolation()
	{
		int num = path.points.size();
		interpLens = new float[num];
		interpVecs = new Vector3f[num];

		Vector3f[] pathPositions = new Vector3f[num];
		for (int i = 0; i < num; i++) {
			pathPositions[i] = path.points.get(i).getPosition();
			interpVecs[i] = new Vector3f();
		}

		// compute the distance of each vector along the path and map to the range [0,1]
		interpLens[0] = 0.0f;
		for (int i = 1; i < num; i++) {
			float dx = pathPositions[i].x - pathPositions[i - 1].x;
			float dy = pathPositions[i].y - pathPositions[i - 1].y;
			float dz = pathPositions[i].z - pathPositions[i - 1].z;
			float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			interpLens[i] = interpLens[i - 1] + length;
		}
		for (int i = 1; i < num; i++) {
			interpLens[i] /= interpLens[num - 1];
		}

		float lenBuf[] = new float[num];
		Vector3f vecBuf[] = new Vector3f[num];
		for (int i = 0; i < num; i++) {
			vecBuf[i] = new Vector3f();
		}

		// end points
		interpVecs[0].x = 0.0f;
		interpVecs[0].y = 0.0f;
		interpVecs[0].z = 0.0f;
		interpVecs[num - 1].x = 0.0f;
		interpVecs[num - 1].y = 0.0f;
		interpVecs[num - 1].z = 0.0f;

		for (int i = 0; i < num - 1; i++) {
			lenBuf[i] = interpLens[i + 1] - interpLens[i];
			vecBuf[i + 1].x = (pathPositions[i + 1].x - pathPositions[i].x) / lenBuf[i];
			vecBuf[i + 1].y = (pathPositions[i + 1].y - pathPositions[i].y) / lenBuf[i];
			vecBuf[i + 1].z = (pathPositions[i + 1].z - pathPositions[i].z) / lenBuf[i];
		}

		// n = 1
		interpVecs[1].x = vecBuf[2].x - vecBuf[1].x;
		interpVecs[1].y = vecBuf[2].y - vecBuf[1].y;
		interpVecs[1].z = vecBuf[2].z - vecBuf[1].z;
		vecBuf[1].x = 2.0f * (interpLens[2] - interpLens[0]);
		vecBuf[1].y = 2.0f * (interpLens[2] - interpLens[0]);
		vecBuf[1].z = 2.0f * (interpLens[2] - interpLens[0]);

		// 1 < n < N - 2
		for (int i = 1; i < num - 2; i++) {
			float sx = lenBuf[i] / vecBuf[i].x;
			float sy = lenBuf[i] / vecBuf[i].y;
			float sz = lenBuf[i] / vecBuf[i].z;
			interpVecs[i + 1].x = (vecBuf[i + 2].x - vecBuf[i + 1].x) - interpVecs[i].x * sx;
			interpVecs[i + 1].y = (vecBuf[i + 2].y - vecBuf[i + 1].y) - interpVecs[i].y * sy;
			interpVecs[i + 1].z = (vecBuf[i + 2].z - vecBuf[i + 1].z) - interpVecs[i].z * sz;
			vecBuf[i + 1].x = 2.0f * (interpLens[i + 2] - interpLens[i]) - lenBuf[i] * sx;
			vecBuf[i + 1].y = 2.0f * (interpLens[i + 2] - interpLens[i]) - lenBuf[i] * sy;
			vecBuf[i + 1].z = 2.0f * (interpLens[i + 2] - interpLens[i]) - lenBuf[i] * sz;
		}

		// n = N - 2
		interpVecs[num - 2].x -= (lenBuf[num - 2] * interpVecs[num - 1].x);
		interpVecs[num - 2].y -= (lenBuf[num - 2] * interpVecs[num - 1].y);
		interpVecs[num - 2].z -= (lenBuf[num - 2] * interpVecs[num - 1].z);

		for (int i = num - 2; i > 0; i--) {
			interpVecs[i].x = (interpVecs[i].x - (lenBuf[i] * interpVecs[i + 1].x)) / vecBuf[i].x;
			interpVecs[i].y = (interpVecs[i].y - (lenBuf[i] * interpVecs[i + 1].y)) / vecBuf[i].y;
			interpVecs[i].z = (interpVecs[i].z - (lenBuf[i] * interpVecs[i + 1].z)) / vecBuf[i].z;
		}
	}

	private Vector3f getInterpPos(float alpha)
	{
		int num = path.points.size();

		Vector3f[] pathPoints = new Vector3f[num];
		for (int i = 0; i < num; i++) {
			pathPoints[i] = path.points.get(i).getPosition();
		}

		int limit = num - 1;
		int i;

		for (i = 0; i < limit;) {
			int avg = (i + limit) / 2;

			if (interpLens[avg] < alpha)
				i = avg + 1;
			else
				limit = avg;
		}

		if (i > 0) {
			i--;
		}

		Vector3f outPos = new Vector3f();
		float ax, ay, az, bx, by, bz, dx, dy, dz;

		float curLength = interpLens[i + 1] - interpLens[i];
		float curProgress = alpha - interpLens[i];

		dx = (pathPoints[i + 1].x - pathPoints[i].x) / curLength;
		ax = (((interpVecs[i + 1].x - interpVecs[i].x) * curProgress / curLength) + (3.0f * interpVecs[i].x)) * curProgress;
		bx = dx - (((2.0f * interpVecs[i].x) + interpVecs[i + 1].x) * curLength);
		outPos.x = ((ax + bx) * curProgress) + pathPoints[i].x;

		dy = (pathPoints[i + 1].y - pathPoints[i].y) / curLength;
		ay = (((interpVecs[i + 1].y - interpVecs[i].y) * curProgress / curLength) + (3.0f * interpVecs[i].y)) * curProgress;
		by = dy - (((2.0f * interpVecs[i].y) + interpVecs[i + 1].y) * curLength);
		outPos.y = ((ay + by) * curProgress) + pathPoints[i].y;

		dz = (pathPoints[i + 1].z - pathPoints[i].z) / curLength;
		az = (((interpVecs[i + 1].z - interpVecs[i].z) * curProgress / curLength) + (3.0f * interpVecs[i].z)) * curProgress;
		bz = dz - (((2.0f * interpVecs[i].z) + interpVecs[i + 1].z) * curLength);
		outPos.z = ((az + bz) * curProgress) + pathPoints[i].z;

		return outPos;
	}
}
