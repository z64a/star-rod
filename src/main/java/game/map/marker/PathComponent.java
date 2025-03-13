package game.map.marker;

import static game.map.MapKey.*;
import static org.lwjgl.opengl.GL11.GL_GREATER;
import static org.lwjgl.opengl.GL11.GL_LEQUAL;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import common.Vector3f;
import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;
import common.commands.EditableField.StandardBoolName;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.camera.ViewType;
import game.map.editor.render.PresetColor;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.selection.SelectablePoint;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.scripts.extract.HeaderEntry;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;
import util.Logger;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class PathComponent extends BaseMarkerComponent
{
	public final PathData path;

	public EditableField<Boolean> showInterp = EditableFieldFactory.create(true)
		.setName(new StandardBoolName("Show Interp")).build();

	private float[] interpLens;
	private Vector3f[] interpVecs;

	public PathComponent(Marker marker)
	{
		super(marker);
		path = new PathData(marker, MarkerInfoPanel.tag_GeneralTab);
	}

	@Override
	public PathComponent deepCopy(Marker copyParent)
	{
		PathComponent copy = new PathComponent(copyParent);
		for (PathPoint wp : path.points)
			copy.path.points.addElement(new PathPoint(copy.path, wp.getX(), wp.getY(), wp.getZ()));
		return copy;
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

		if (path.points.size() == 0 || !parentMarker.selected)
			return;

		if (showInterp.get() && path.points.size() > 2)
			computeInterpolation();

		boolean editPointsMode = true;
		boolean drawHiddenPaths = (view.type == ViewType.PERSPECTIVE);

		RenderState.setPointSize(editPointsMode ? 12.0f : 8.0f);
		RenderState.setLineWidth(3.0f);

		RenderState.setColor(PresetColor.YELLOW);
		for (SelectablePoint point : path.points)
			PointRenderQueue.addPoint().setPosition(point.getX(), point.getY(), point.getZ());

		/*
		RenderState.setColor(Colors.GREEN);
		int max = 1 + path.points.size() * 8;
		for (int i = 0; i <= max; i++) {
			Vector3f pos = getInterpPos((float) i / max);
			PointRenderQueue.addPoint().setPosition(pos.x, pos.y, pos.z);
		}
		*/

		PointRenderQueue.render(true);

		if (drawHiddenPaths)
			RenderState.setDepthFunc(GL_LEQUAL);

		RenderState.setColor(PresetColor.YELLOW);
		SelectablePoint last = null;
		for (SelectablePoint point : path.points) {
			if (last != null) {
				LineRenderQueue.addLine(
					LineRenderQueue.addVertex().setPosition(last.getX(), last.getY(), last.getZ()).getIndex(),
					LineRenderQueue.addVertex().setPosition(point.getX(), point.getY(), point.getZ()).getIndex());
			}
			last = point;
		}

		try {
			if (showInterp.get() && path.points.size() > 2) {
				RenderState.setColor(PresetColor.GREEN);
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
			RenderState.setColor(PresetColor.YELLOW);
			last = null;
			for (SelectablePoint point : path.points) {
				if (last != null) {
					Renderer.queueStipple(
						last.getX(), last.getY(), last.getZ(),
						point.getX(), point.getY(), point.getZ(),
						10.0f);
				}
				last = point;
			}

			// flush the line render queue with depth writing disabled
			RenderState.setDepthWrite(false);
			RenderState.setDepthFunc(GL_GREATER);
			LineRenderQueue.render(true);
			RenderState.setDepthWrite(true);

			RenderState.initDepthFunc();
		}
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

	public void addHeaderDefines(HeaderEntry h)
	{
		List<String> lines = new ArrayList<>();

		for (int i = 0; i < path.points.size(); i++) {
			PathPoint wp = path.points.get(i);
			lines.add(String.format("    { %4d, %4d, %4d },", wp.point.getX(), wp.point.getY(), wp.point.getZ()));
		}

		h.addDefine("PATH", lines);
	}

	public void fromLines(Iterable<String> lines)
	{
		path.points.clear();

		for (String line : lines) {
			// trim { and }, from each row
			line = line.substring(line.indexOf("{") + 1, line.indexOf("}"));
			String[] coords = line.split(",");
			float x = Float.parseFloat(coords[0]);
			float y = Float.parseFloat(coords[1]);
			float z = Float.parseFloat(coords[2]);

			path.points.addElement(new PathPoint(path, Math.round(x), Math.round(y), Math.round(z)));
		}
	}
}
