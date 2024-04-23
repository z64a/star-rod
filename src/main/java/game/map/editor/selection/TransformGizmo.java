package game.map.editor.selection;

import static game.map.editor.selection.Selection.TransformState.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import game.map.Axis;
import game.map.BoundingBox;
import game.map.MutablePoint;
import game.map.PointListBackup;
import game.map.editor.MapEditor;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.geometry.Vector3f;
import game.map.editor.render.UtilityModel;
import game.map.editor.render.UtilityModel.UtilityTriangle;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.selection.Selection.TransformState;
import game.map.shape.TransformMatrix;
import renderer.buffers.TriangleRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicSolidShader;
import util.identity.IdentityHashSet;

@SuppressWarnings("unchecked")
public class TransformGizmo implements Selectable
{
	public transient boolean selected = false;

	private static final Vector3f GIZMO_RED = new Vector3f(0.9f, 0.2f, 0.2f);
	private static final Vector3f GIZMO_GREEN = new Vector3f(0.2f, 0.9f, 0.2f);
	private static final Vector3f GIZMO_BLUE = new Vector3f(0.2f, 0.2f, 0.9f);
	private static final Vector3f GIZMO_YELLOW = new Vector3f(0.95f, 0.95f, 0.3f);
	private static final Vector3f GIZMO_BASE = new Vector3f(0.8f, 0.8f, 0.8f);

	private static final int HALF_WIDTH = 2;
	private static final int HEAD_LENGTH = 8;
	private static final int ARM_LENGTH = 40;
	private static final int PLANE_LENGTH = 12;

	private static List<UtilityTriangle>[] axis;
	private static List<UtilityTriangle>[] axisOutline;

	private static List<UtilityTriangle>[] cone;
	private static List<UtilityTriangle>[] coneOutline;

	private static List<UtilityTriangle>[] cube;
	private static List<UtilityTriangle>[] cubeOutline;

	public transient MutablePoint origin;
	public transient BoundingBox aabb;
	public transient BoundingBox xbox;
	public transient BoundingBox ybox;
	public transient BoundingBox zbox;
	public transient BoundingBox xybox;
	public transient BoundingBox yzbox;
	public transient BoundingBox xzbox;

	public int frameCount = 0;
	public boolean highlightX = false;
	public boolean highlightY = false;
	public boolean highlightZ = false;
	private AxisConstraint lastPickAxis = null;

	private final Selection<?> selection;

	public TransformGizmo(Selection<?> selection, Vector3f vec)
	{
		this.selection = selection;
		origin = new MutablePoint(vec);

		aabb = new BoundingBox(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
		xbox = new BoundingBox(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
		ybox = new BoundingBox(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
		zbox = new BoundingBox(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
		xybox = new BoundingBox(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
		yzbox = new BoundingBox(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
		xzbox = new BoundingBox(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
	}

	public void setOrigin(Vector3f vec)
	{
		origin.setPosition(vec);
	}

	private static enum GizmoPart
	{
		BASE,
		AXIS_X,
		AXIS_Y,
		AXIS_Z,
		CAP_X,
		CAP_Y,
		CAP_Z,
	}

	private static class DepthSort implements Comparable<DepthSort>
	{
		public final double depth;
		public final GizmoPart part;

		public DepthSort(GizmoPart part, Vector3f cameraPos, int x, int y, int z)
		{
			double dx = x - cameraPos.x;
			double dy = y - cameraPos.y;
			double dz = z - cameraPos.z;
			this.depth = Math.sqrt(dx * dx + dy * dy + dz * dz);
			this.part = part;
		}

		@Override
		public int compareTo(DepthSort o)
		{
			return Double.compare(o.depth, depth);
		}
	}

	public void render(MapEditor editor, Vector3f cameraPos, float scaleFactor)
	{
		if (editor.debugModeEnabled()) {
			recalculateMainAABB(scaleFactor);
			recalculateAxisAABB(scaleFactor);
			aabb.render();
			xbox.render();
			ybox.render();
			zbox.render();
			xybox.render();
			yzbox.render();
			xzbox.render();
		}

		DepthSort[] sortedParts = new DepthSort[7];
		sortedParts[0] = new DepthSort(GizmoPart.BASE, cameraPos, origin.getX(), origin.getY(), origin.getZ());
		sortedParts[1] = new DepthSort(GizmoPart.AXIS_X, cameraPos, origin.getX() + 50, origin.getY(), origin.getZ());
		sortedParts[2] = new DepthSort(GizmoPart.AXIS_Y, cameraPos, origin.getX(), origin.getY() + 50, origin.getZ());
		sortedParts[3] = new DepthSort(GizmoPart.AXIS_Z, cameraPos, origin.getX(), origin.getY(), origin.getZ() + 50);
		sortedParts[4] = new DepthSort(GizmoPart.CAP_X, cameraPos, origin.getX() + 100, origin.getY(), origin.getZ());
		sortedParts[5] = new DepthSort(GizmoPart.CAP_Y, cameraPos, origin.getX(), origin.getY() + 100, origin.getZ());
		sortedParts[6] = new DepthSort(GizmoPart.CAP_Z, cameraPos, origin.getX(), origin.getY(), origin.getZ() + 100);
		Arrays.sort(sortedParts);

		TransformMatrix mtx = TransformMatrix.identity();
		mtx.scale(scaleFactor);
		if (selection.transforming() && selection.getTransformState() == TransformState.ROTATE)
			mtx.rotate(selection.getRotatationAxis(), selection.getRotatationAngle());
		mtx.translate(origin.getX(), origin.getY(), origin.getZ());

		boolean useScaleModel = editor.keyboard.isKeyDown(MapEditor.SCALE_KEY) || editor.rescaling;

		RenderState.setPolygonMode(PolygonMode.FILL);

		ShaderManager.use(BasicSolidShader.class);

		RenderState.enableDepthTest(false);
		renderGizmoModel(mtx, useScaleModel, sortedParts, true);

		RenderState.enableDepthTest(true);
		renderGizmoModel(mtx, useScaleModel, sortedParts, false);

		RenderState.setModelMatrix(null);
	}

	private void renderGizmoModel(TransformMatrix mtx, boolean useScaleModel, DepthSort[] sortedParts, boolean hidden)
	{
		boolean longEnough = origin.isTransforming() || frameCount > 3;

		Vector3f colorX, colorY, colorZ;
		colorX = longEnough && highlightX ? GIZMO_YELLOW : GIZMO_RED;
		colorY = longEnough && highlightY ? GIZMO_YELLOW : GIZMO_GREEN;
		colorZ = longEnough && highlightZ ? GIZMO_YELLOW : GIZMO_BLUE;

		float alpha = hidden ? 0.5f : 1.0f;

		Vector3f colorBase = longEnough && (highlightX || highlightY || highlightZ) ? GIZMO_YELLOW : GIZMO_BASE;

		// outline

		if (!hidden) {
			RenderState.setEnabledCullFace(true);
			RenderState.setColor(0.1f, 0.1f, 0.1f);

			for (UtilityTriangle t : cubeOutline[3])
				t.queueForRendering(true);

			for (UtilityTriangle t : axisOutline[0])
				t.queueForRendering(true);
			for (UtilityTriangle t : axisOutline[1])
				t.queueForRendering(true);
			for (UtilityTriangle t : axisOutline[2])
				t.queueForRendering(true);

			if (useScaleModel) {
				for (UtilityTriangle t : cubeOutline[0])
					t.queueForRendering(true);
				for (UtilityTriangle t : cubeOutline[1])
					t.queueForRendering(true);
				for (UtilityTriangle t : cubeOutline[2])
					t.queueForRendering(true);
			}
			else {
				for (UtilityTriangle t : coneOutline[0])
					t.queueForRendering(true);
				for (UtilityTriangle t : coneOutline[1])
					t.queueForRendering(true);
				for (UtilityTriangle t : coneOutline[2])
					t.queueForRendering(true);
			}

			RenderState.setEnabledCullFace(false);

			RenderState.setDepthWrite(false);
			TriangleRenderQueue.renderWithTransform(mtx, true);
			RenderState.setDepthWrite(true);
		}

		for (DepthSort sortedPart : sortedParts) {
			switch (sortedPart.part) {
				case BASE:
					RenderState.setColor(colorBase.x, colorBase.y, colorBase.z, alpha);
					for (UtilityTriangle t : cube[3])
						t.queueForRendering(false);
					break;
				case AXIS_X:
					RenderState.setColor(colorX.x, colorX.y, colorX.z, alpha);
					for (UtilityTriangle t : axis[0])
						t.queueForRendering(false);
					break;
				case AXIS_Y:
					RenderState.setColor(colorY.x, colorY.y, colorY.z, alpha);
					for (UtilityTriangle t : axis[1])
						t.queueForRendering(false);
					break;
				case AXIS_Z:
					RenderState.setColor(colorZ.x, colorZ.y, colorZ.z, alpha);
					for (UtilityTriangle t : axis[2])
						t.queueForRendering(false);
					break;
				case CAP_X:
					RenderState.setColor(colorX.x, colorX.y, colorX.z, alpha);
					for (UtilityTriangle t : useScaleModel ? cube[0] : cone[0])
						t.queueForRendering(false);
					break;
				case CAP_Y:
					RenderState.setColor(colorY.x, colorY.y, colorY.z, alpha);
					for (UtilityTriangle t : useScaleModel ? cube[1] : cone[1])
						t.queueForRendering(false);
					break;
				case CAP_Z:
					RenderState.setColor(colorZ.x, colorZ.y, colorZ.z, alpha);
					for (UtilityTriangle t : useScaleModel ? cube[2] : cone[2])
						t.queueForRendering(false);
					break;
				default:
					break;
			}
		}

		TriangleRenderQueue.renderWithTransform(mtx, true);
	}

	private static class GizmoTest
	{
		private final boolean x, y, z;
		private final PickHit hit;

		private GizmoTest(PickHit hit, boolean x, boolean y, boolean z)
		{
			this.hit = hit;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	public void test(PickRay mouseRay, MapEditViewport mouseViewport, float scaleFactor)
	{
		if (selection.transforming()) {
			TransformState state = selection.getTransformState();
			if (state == ROTATE) {
				Axis rotationAxis = selection.getRotatationAxis();
				frameCount = 6;
				highlightX = (rotationAxis == Axis.X);
				highlightY = (rotationAxis == Axis.Y);
				highlightZ = (rotationAxis == Axis.Z);
			}
			else if ((state == TRANSLATE || state == SCALE) && lastPickAxis != null) {
				frameCount = 6;
				highlightX = lastPickAxis.allowX;
				highlightY = lastPickAxis.allowY;
				highlightZ = lastPickAxis.allowZ;
			}
			else {
				frameCount = 0;
				highlightX = false;
				highlightY = false;
				highlightZ = false;
			}
			return;
		}

		if (frameCount < 0)
			frameCount = 0;
		if (frameCount > 6)
			frameCount = 6;

		recalculateMainAABB(scaleFactor);
		highlightX = false;
		highlightY = false;
		highlightZ = false;

		if (!PickRay.intersects(mouseRay, aabb)) {
			frameCount--;
			return;
		}

		recalculateAxisAABB(scaleFactor);

		boolean ignoreX = false;
		boolean ignoreY = false;
		boolean ignoreZ = false;

		switch (mouseViewport.type) {
			case FRONT:
				ignoreZ = true;
				break;
			case SIDE:
				ignoreX = true;
				break;
			case TOP:
				ignoreY = true;
				break;
			case PERSPECTIVE:
				break;
		}

		// select closest axis

		List<GizmoTest> tests = new ArrayList<>();
		if (!ignoreX)
			tests.add(new GizmoTest(PickRay.getIntersection(mouseRay, xbox), true, false, false));
		if (!ignoreY)
			tests.add(new GizmoTest(PickRay.getIntersection(mouseRay, ybox), false, true, false));
		if (!ignoreZ)
			tests.add(new GizmoTest(PickRay.getIntersection(mouseRay, zbox), false, false, true));

		tests.sort((a, b) -> Float.compare(a.hit.dist, b.hit.dist));
		if (!tests.isEmpty()) {
			GizmoTest test = tests.get(0);
			if (!test.hit.missed()) {
				highlightX = test.x;
				highlightY = test.y;
				highlightZ = test.z;
				frameCount++;
				return;
			}
		}

		// select closest plane

		tests.clear();
		if (!ignoreX && !ignoreY)
			tests.add(new GizmoTest(PickRay.getIntersection(mouseRay, xybox), true, true, false));
		if (!ignoreY && !ignoreZ)
			tests.add(new GizmoTest(PickRay.getIntersection(mouseRay, yzbox), false, true, true));
		if (!ignoreX && !ignoreZ)
			tests.add(new GizmoTest(PickRay.getIntersection(mouseRay, xzbox), true, false, true));

		tests.sort((a, b) -> Float.compare(a.hit.dist, b.hit.dist));
		if (!tests.isEmpty()) {
			GizmoTest test = tests.get(0);
			if (!test.hit.missed()) {
				highlightX = test.x;
				highlightY = test.y;
				highlightZ = test.z;
				frameCount++;
				return;
			}
		}

		frameCount--;
	}

	/**
	 * @param clickRay
	 * @param clickedViewport
	 * @param scaleFactor
	 * @return PickHit holding an AxisConstraint
	 */
	public PickHit pick(PickRay clickRay, MapEditViewport clickedViewport, float scaleFactor)
	{
		recalculateMainAABB(scaleFactor);

		if (!PickRay.intersects(clickRay, aabb))
			return new PickHit(clickRay);

		recalculateAxisAABB(scaleFactor);

		boolean ignoreX = false;
		boolean ignoreY = false;
		boolean ignoreZ = false;

		switch (clickedViewport.type) {
			case FRONT:
				ignoreZ = true;
				break;
			case SIDE:
				ignoreX = true;
				break;
			case TOP:
				ignoreY = true;
				break;
			case PERSPECTIVE:
				break;
		}

		PickHit closestHit = new PickHit(clickRay);
		highlightX = false;
		highlightY = false;
		highlightZ = false;

		// select closest axis

		List<GizmoTest> tests = new ArrayList<>();
		if (!ignoreX)
			tests.add(new GizmoTest(PickRay.getIntersection(clickRay, xbox), true, false, false));
		if (!ignoreY)
			tests.add(new GizmoTest(PickRay.getIntersection(clickRay, ybox), false, true, false));
		if (!ignoreZ)
			tests.add(new GizmoTest(PickRay.getIntersection(clickRay, zbox), false, false, true));

		tests.sort((a, b) -> Float.compare(a.hit.dist, b.hit.dist));
		if (!tests.isEmpty()) {
			GizmoTest test = tests.get(0);
			if (!test.hit.missed()) {
				highlightX = test.x;
				highlightY = test.y;
				highlightZ = test.z;
				lastPickAxis = new AxisConstraint(highlightX, highlightY, highlightZ);
				closestHit = test.hit;
				closestHit.obj = lastPickAxis;
			}
		}

		// select closest plane

		tests.clear();
		if (!ignoreX && !ignoreY)
			tests.add(new GizmoTest(PickRay.getIntersection(clickRay, xybox), true, true, false));
		if (!ignoreY && !ignoreZ)
			tests.add(new GizmoTest(PickRay.getIntersection(clickRay, yzbox), false, true, true));
		if (!ignoreX && !ignoreZ)
			tests.add(new GizmoTest(PickRay.getIntersection(clickRay, xzbox), true, false, true));

		tests.sort((a, b) -> Float.compare(a.hit.dist, b.hit.dist));
		if (!tests.isEmpty()) {
			GizmoTest test = tests.get(0);
			if (!test.hit.missed()) {
				highlightX = test.x;
				highlightY = test.y;
				highlightZ = test.z;
				lastPickAxis = new AxisConstraint(highlightX, highlightY, highlightZ);
				closestHit = test.hit;
				closestHit.obj = lastPickAxis;
			}
		}

		return closestHit;
	}

	private void recalculateMainAABB(float scaleFactor)
	{
		float length = ARM_LENGTH * scaleFactor;
		float halfWidth = HALF_WIDTH * scaleFactor;
		halfWidth = Math.max(halfWidth, 0.5f);

		aabb.min.setPosition(
			Math.round(origin.getX() - halfWidth),
			Math.round(origin.getY() - halfWidth),
			Math.round(origin.getZ() - halfWidth));
		aabb.max.setPosition(
			Math.round(origin.getX() + length),
			Math.round(origin.getY() + length),
			Math.round(origin.getZ() + length));
	}

	private void recalculateAxisAABB(float scaleFactor)
	{
		float armLength = ARM_LENGTH * scaleFactor;
		float planeLength = PLANE_LENGTH * scaleFactor;
		float halfWidth = HALF_WIDTH * scaleFactor;
		halfWidth = Math.max(halfWidth, 0.5f);

		xbox.min.setPosition(
			Math.round(origin.getX() + halfWidth),
			Math.round(origin.getY() - halfWidth),
			Math.round(origin.getZ() - halfWidth));
		xbox.max.setPosition(
			Math.round(origin.getX() + armLength),
			Math.round(origin.getY() + halfWidth),
			Math.round(origin.getZ() + halfWidth));

		ybox.min.setPosition(
			Math.round(origin.getX() - halfWidth),
			Math.round(origin.getY() + halfWidth),
			Math.round(origin.getZ() - halfWidth));
		ybox.max.setPosition(
			Math.round(origin.getX() + halfWidth),
			Math.round(origin.getY() + armLength),
			Math.round(origin.getZ() + halfWidth));

		zbox.min.setPosition(
			Math.round(origin.getX() - halfWidth),
			Math.round(origin.getY() - halfWidth),
			Math.round(origin.getZ() + halfWidth));
		zbox.max.setPosition(
			Math.round(origin.getX() + halfWidth),
			Math.round(origin.getY() + halfWidth),
			Math.round(origin.getZ() + armLength));

		xybox.min.setPosition(
			Math.round(origin.getX() + halfWidth),
			Math.round(origin.getY() + halfWidth),
			Math.round(origin.getZ() - halfWidth));
		xybox.max.setPosition(
			Math.round(origin.getX() + planeLength),
			Math.round(origin.getY() + planeLength),
			Math.round(origin.getZ() + halfWidth));

		yzbox.min.setPosition(
			Math.round(origin.getX() - halfWidth),
			Math.round(origin.getY() + halfWidth),
			Math.round(origin.getZ() + halfWidth));
		yzbox.max.setPosition(
			Math.round(origin.getX() + halfWidth),
			Math.round(origin.getY() + planeLength),
			Math.round(origin.getZ() + planeLength));

		xzbox.min.setPosition(
			Math.round(origin.getX() + halfWidth),
			Math.round(origin.getY() - halfWidth),
			Math.round(origin.getZ() + halfWidth));
		xzbox.max.setPosition(
			Math.round(origin.getX() + planeLength),
			Math.round(origin.getY() + halfWidth),
			Math.round(origin.getZ() + planeLength));
	}

	public void setTransformDisplacement(Vector3f translation)
	{
		origin.setTempTranslation(translation);
	}

	@Override
	public void addTo(BoundingBox aabb)
	{}

	@Override
	public void recalculateAABB()
	{}

	@Override
	public boolean allowRotation(Axis axis)
	{
		return false;
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		positions.add(origin);
	}

	@Override
	public void setSelected(boolean val)
	{ selected = val; }

	@Override
	public boolean isSelected()
	{ return selected; }

	@Override
	public boolean transforms()
	{
		return true;
	}

	@Override
	public boolean isTransforming()
	{ return origin.isTransforming(); }

	@Override
	public void startTransformation()
	{
		origin.startTransform();
	}

	@Override
	public void endTransformation()
	{
		origin.endTransform();
	}

	@Override
	public PointListBackup createTransformer(TransformMatrix m)
	{
		return new PointListBackup(origin.getBackup());
	}

	static {
		axis = new List[3];
		axis[2] = getAxis(16, 2.0f, 0.0f);
		axis[1] = ZtoY(axis[2]);
		axis[0] = ZtoX(axis[2]);

		axisOutline = new List[3];
		axisOutline[2] = getAxis(16, 2.0f, 0.5f);
		axisOutline[1] = ZtoY(axisOutline[2]);
		axisOutline[0] = ZtoX(axisOutline[2]);

		cone = new List[3];
		cone[2] = getConeHead(16, 4.5f, 0.0f);
		cone[1] = ZtoY(cone[2]);
		cone[0] = ZtoX(cone[2]);

		coneOutline = new List[3];
		coneOutline[2] = getConeHead(16, 4.5f, 0.5f);
		coneOutline[1] = ZtoY(coneOutline[2]);
		coneOutline[0] = ZtoX(coneOutline[2]);

		UtilityModel cubeModel = new UtilityModel("cube_sharp_bevel.obj");

		cube = new List[4];
		cube[3] = getCubeCenter(cubeModel.triangles, 0.0f);
		cube[2] = getCubeHead(cubeModel.triangles, 0.0f);
		cube[1] = ZtoY(cube[2]);
		cube[0] = ZtoX(cube[2]);

		cubeOutline = new List[4];
		cubeOutline[3] = getCubeCenter(cubeModel.triangles, 0.5f);
		cubeOutline[2] = getCubeHead(cubeModel.triangles, 0.5f);
		cubeOutline[1] = ZtoY(cubeOutline[2]);
		cubeOutline[0] = ZtoX(cubeOutline[2]);
	}

	private static List<UtilityTriangle> getAxis(int numFaces, float radius, float extrudeAmount)
	{
		float start = 2.0f + extrudeAmount * 0.1f;
		float end = (ARM_LENGTH - HEAD_LENGTH) - extrudeAmount * 0.1f;

		Vector3f[][] axesVerts = new Vector3f[numFaces + 1][2];

		float R = radius + extrudeAmount;

		for (int i = 0; i < numFaces; i++) {
			double angle = 2 * Math.PI * i / numFaces;
			float x = R * (float) Math.cos(angle);
			float y = R * (float) Math.sin(angle);

			axesVerts[i][0] = new Vector3f(x, y, start);
			axesVerts[i][1] = new Vector3f(x, y, end);
		}
		axesVerts[numFaces][0] = axesVerts[0][0];
		axesVerts[numFaces][1] = axesVerts[0][1];

		Vector3f startCenter = new Vector3f(0.0f, 0.0f, start);
		Vector3f endCenter = new Vector3f(0.0f, 0.0f, end);

		List<UtilityTriangle> triangles = new ArrayList<>(4 * numFaces);

		for (int i = 0; i < numFaces; i++) {
			triangles.add(new UtilityTriangle(axesVerts[i][0], axesVerts[i + 1][0], axesVerts[i][1]));
			triangles.add(new UtilityTriangle(axesVerts[i + 1][0], axesVerts[i + 1][1], axesVerts[i][1]));
		}

		for (int i = 0; i < numFaces; i++) {
			triangles.add(new UtilityTriangle(axesVerts[i + 1][0], axesVerts[i][0], startCenter));
			triangles.add(new UtilityTriangle(axesVerts[i][1], axesVerts[i + 1][1], endCenter));
		}

		return triangles;
	}

	private static List<UtilityTriangle> getConeHead(int numFaces, float radius, float extrudeAmount)
	{
		float start = ARM_LENGTH - HEAD_LENGTH;
		float end = ARM_LENGTH;

		Vector3f[] axesVerts = new Vector3f[numFaces + 1];

		float R = radius + (1.2f * extrudeAmount); // dont bother with perfect miter

		for (int i = 0; i < numFaces; i++) {
			double angle = 2 * Math.PI * i / numFaces;
			float x = R * (float) Math.cos(angle);
			float y = R * (float) Math.sin(angle);

			axesVerts[i] = new Vector3f(x, y, start - (0.5f * extrudeAmount));
		}
		axesVerts[numFaces] = axesVerts[0];

		Vector3f startCenter = new Vector3f(0.0f, 0.0f, start - extrudeAmount);
		Vector3f endCenter = new Vector3f(0.0f, 0.0f, end + (1.5f * extrudeAmount));

		List<UtilityTriangle> triangles = new ArrayList<>(4 * numFaces);

		for (int i = 0; i < numFaces; i++) {
			triangles.add(new UtilityTriangle(axesVerts[i + 1], axesVerts[i], startCenter));
			triangles.add(new UtilityTriangle(axesVerts[i], axesVerts[i + 1], endCenter));
		}

		return triangles;
	}

	private static List<UtilityTriangle> getCubeCenter(List<UtilityTriangle> triangles, float extrudeAmount)
	{
		float s = extrudeAmount + 3.0f;

		List<UtilityTriangle> newTriangles = new ArrayList<>(triangles.size());

		for (UtilityTriangle t : triangles) {
			Vector3f a = new Vector3f(s * t.a.x, s * t.a.y, s * t.a.z);
			Vector3f b = new Vector3f(s * t.b.x, s * t.b.y, s * t.b.z);
			Vector3f c = new Vector3f(s * t.c.x, s * t.c.y, s * t.c.z);
			newTriangles.add(new UtilityTriangle(a, b, c));
		}

		return newTriangles;
	}

	private static List<UtilityTriangle> getCubeHead(List<UtilityTriangle> triangles, float extrudeAmount)
	{
		float shift = ARM_LENGTH - (HEAD_LENGTH / 2.0f);
		float s = extrudeAmount + HEAD_LENGTH / 2.0f;

		List<UtilityTriangle> newTriangles = new ArrayList<>(triangles.size());

		for (UtilityTriangle t : triangles) {
			Vector3f a = new Vector3f(s * t.a.x, s * t.a.y, shift + s * t.a.z);
			Vector3f b = new Vector3f(s * t.b.x, s * t.b.y, shift + s * t.b.z);
			Vector3f c = new Vector3f(s * t.c.x, s * t.c.y, shift + s * t.c.z);
			newTriangles.add(new UtilityTriangle(a, b, c));
		}

		return newTriangles;
	}

	private static List<UtilityTriangle> ZtoX(List<UtilityTriangle> triangles)
	{
		List<UtilityTriangle> newTriangles = new ArrayList<>(triangles.size());

		for (UtilityTriangle t : triangles) {
			Vector3f a = new Vector3f(t.a.z, t.a.x, t.a.y);
			Vector3f b = new Vector3f(t.b.z, t.b.x, t.b.y);
			Vector3f c = new Vector3f(t.c.z, t.c.x, t.c.y);
			newTriangles.add(new UtilityTriangle(a, b, c));
		}

		return newTriangles;
	}

	private static List<UtilityTriangle> ZtoY(List<UtilityTriangle> triangles)
	{
		List<UtilityTriangle> newTriangles = new ArrayList<>(triangles.size());

		for (UtilityTriangle t : triangles) {
			Vector3f a = new Vector3f(t.a.y, t.a.z, t.a.x);
			Vector3f b = new Vector3f(t.b.y, t.b.z, t.b.x);
			Vector3f c = new Vector3f(t.c.y, t.c.z, t.c.x);
			newTriangles.add(new UtilityTriangle(a, b, c));
		}

		return newTriangles;
	}
}
