package game.map.editor.selection;

import java.util.ArrayList;
import java.util.LinkedList;

import common.Vector3f;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.MutableAngle;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.editor.MapEditor;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.camera.OrthographicViewport;
import game.map.editor.camera.ViewType;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.CommandBatch;
import game.map.editor.commands.TransformSelection;
import game.map.editor.render.PresetColor;
import game.map.editor.render.Renderer;
import game.map.editor.selection.PickRay.PickHit;
import game.map.mesh.Vertex;
import game.map.shape.SynchronizedUV;
import game.map.shape.TransformMatrix;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.LineShader;
import util.Logger;
import util.Priority;
import util.identity.IdentityHashSet;

/**
 * Selections are a group of objects that can be operated upon by transformations.
 */
public final class Selection<T extends Selectable>
{
	private final MapEditor editor;

	// current type of transformation
	public static enum TransformState
	{
		IDLE, TRANSLATE, ROTATE, SCALE, SUBSTITUTE
	}

	private TransformState transformState;

	private static class Drag
	{
		private Vector3f referencePosition; // vertex snap
		private Vector3f accumulatedTranslation = new Vector3f();
		private Vector3f currentTranslation = new Vector3f();
		private float accumulatedRaw = 0;
		private double dragTime = 0;
		private int dragFrames = 0;
		public boolean immediate;
	}

	private static class Scale
	{
		private Vector3f startPoint; // click position corresponding to scale = (1.0, 1.0, 1.0)
		private Vector3f originalSize;
		private Vector3f currentScale;
		private boolean uniformScaling;
	}

	private static class Rotation
	{
		private Axis axis;
		private double angle;

		private Vector3f startPoint;
		private Vector3f endPoint;
		private double startAngle;
		private double endAngle;
	}

	private Vector3f transformOrigin;
	private AxisConstraint axisConstraint;
	private TransformGizmo transformGizmo;
	private PickHit gizmoHit;

	private Drag drag = null;
	private Scale scale = null;
	private Rotation rotation = null;

	private MapEditViewport transformViewport;
	private String transformMessage;

	public ArrayList<T> selectableList;
	public BoundingBox aabb;

	private IdentityHashSet<MutablePoint> transformPoints;
	private IdentityHashSet<MutableAngle> transformAngles;
	private IdentityHashSet<SynchronizedUV> transformUVs; //TODO

	/**
	 * Keep track of operations we preform during transformations. After the transformation
	 * is complete, these commands are concatenated and executed as a single batch which is
	 * amenable to undo/redo.
	 */
	public CommandBatch transformCommandBatch;

	public Selection(Class<T> type, MapEditor editor)
	{
		this.editor = editor;
		selectableList = new ArrayList<>();
		aabb = new BoundingBox();
		transformGizmo = null;
		transformState = TransformState.IDLE;
		transformCommandBatch = new CommandBatch();
	}

	public TransformState getTransformState()
	{
		return transformState;
	}

	public Axis getRotatationAxis()
	{
		return (transformState == TransformState.ROTATE) ? rotation.axis : null;
	}

	public float getRotatationAngle()
	{
		return (transformState == TransformState.ROTATE) ? (float) rotation.angle : 0.0f;
	}

	public boolean transforming()
	{
		return transformState != TransformState.IDLE;
	}

	public void postTransformUpdateToViewport()
	{
		if (transformViewport != null)
			transformViewport.setTextLL(transformMessage, true);
	}

	private void startTransformation()
	{
		transformMessage = "";
		transformOrigin = getCenter();

		aabb.min.startTransform();
		aabb.max.startTransform();
		transformGizmo.startTransformation();
		transformPoints = new IdentityHashSet<>();
		transformAngles = new IdentityHashSet<>();

		for (T item : selectableList)
			item.startTransformation();
	}

	private void endTransformation(TransformMatrix m, boolean changed)
	{
		if (!selectableList.isEmpty()) {
			if (changed) {
				transformCommandBatch.addCommand(new TransformSelection<>(this, m));
				MapEditor.execute(transformCommandBatch);
			}
			else {
				transformCommandBatch.undo();
			}
		}
		transformCommandBatch = new CommandBatch();

		aabb.min.endTransform();
		aabb.max.endTransform();
		transformGizmo.endTransformation();
		transformPoints.clear();
		transformAngles.clear();

		for (T item : selectableList)
			item.recalculateAABB();

		updateAABB();

		transformState = TransformState.IDLE;
		transformMessage = "";
	}

	public void testTransformGizmo(PickRay mouseRay, MapEditViewport mouseViewport)
	{
		if (!isEmpty() && editor.showGizmo) {
			float scaleFactor = mouseViewport.getScaleFactor(
				transformGizmo.origin.getX(),
				transformGizmo.origin.getY(),
				transformGizmo.origin.getZ());
			transformGizmo.test(mouseRay, mouseViewport, scaleFactor);
		}
	}

	/**
	 * Tests whether a pick selects part of the transform handle.
	 * If so, translation constraints are set.
	 * @param clickRay
	 * @param clickedViewport
	 */
	public PickHit pickTransformGizmo(PickRay clickRay, MapEditViewport clickedViewport)
	{
		axisConstraint = null;
		if (!isEmpty() && editor.showGizmo) {
			float scaleFactor = clickedViewport.getScaleFactor(
				transformGizmo.origin.getX(),
				transformGizmo.origin.getY(),
				transformGizmo.origin.getZ());
			gizmoHit = transformGizmo.pick(clickRay, clickedViewport, scaleFactor);
			axisConstraint = gizmoHit.missed() ? null : (AxisConstraint) gizmoHit.obj;
			return gizmoHit;
		}
		return new PickHit(clickRay);
	}

	/**
	 * Immediately applies an arbitrary matrix transformation to the selection.
	 * @param m
	 */
	public void applyMatrixTransformation(TransformMatrix m)
	{
		if (transformState != TransformState.IDLE)
			return;

		transformGizmo.startTransformation();
		transformPoints = new IdentityHashSet<>();
		transformAngles = new IdentityHashSet<>();
		transformPoints.add(aabb.min);
		transformPoints.add(aabb.max);

		for (T item : selectableList) {
			item.startTransformation();
			item.addPoints(transformPoints);
			item.addAngles(transformAngles);
		}

		transformState = TransformState.SUBSTITUTE;

		for (MutablePoint p : transformPoints)
			m.applyTransform(p);

		for (MutableAngle a : transformAngles)
			m.applyTransform(a);

		endTransformation(m, true);
	}

	/**
	 * Direct transformations allow us to programmitcally SET the position of each item
	 * in a selection while maintaining the ability to redo/undo the transformation once
	 * it has been applied.
	 */
	public void startDirectTransformation()
	{
		if (transformState != TransformState.IDLE) {
			Logger.log("Cannot initiate transformation when selection is already being transformed!", Priority.WARNING);
			return;
		}

		aabb.min.startTransform();
		aabb.max.startTransform();
		transformGizmo.startTransformation();
		transformPoints = new IdentityHashSet<>();
		transformAngles = new IdentityHashSet<>();

		for (T item : selectableList) {
			item.startTransformation();
			item.addPoints(transformPoints);
			item.addAngles(transformAngles);
		}

		transformState = TransformState.SUBSTITUTE;
	}

	/**
	 * Direct transformations allow us to programmitcally SET the position of each item
	 * in a selection while maintaining the ability to redo/undo the transformation once
	 * it has been applied.
	 */
	public void endDirectTransformation()
	{
		MapEditor.execute(new DirectTransformSelection<>(editor, this));
		aabb.min.endTransform();
		aabb.max.endTransform();
		transformGizmo.endTransformation();
		transformPoints.clear();
		transformAngles.clear();

		transformState = TransformState.IDLE;
	}

	/**
	 * Nudges the current selection to the nearest grid increment.
	 * @param direction component-wise nudge direction (only signs matter)
	 */
	public void nudgeAlong(Vector3f direction)
	{
		startTranslation(null, true);
		direction.normalize();

		if (editor.gridEnabled && editor.grid.power > 0 && editor.snapTranslation) {
			int snapx = 0;
			int snapy = 0;
			int snapz = 0;

			int spacing = editor.grid.getSpacing();

			if (direction.x < 0) {
				snapx = spacing * ((int) Math.ceil((double) aabb.min.getBaseX() / spacing) - 1);
				snapx -= aabb.min.getBaseX();
			}
			else if (direction.x > 0) {
				snapx = spacing * ((int) Math.floor((double) aabb.max.getBaseX() / spacing) + 1);
				snapx -= aabb.max.getBaseX();
			}

			if (direction.y < 0) {
				snapy = spacing * ((int) Math.ceil((double) aabb.min.getBaseY() / spacing) - 1);
				snapy -= aabb.min.getBaseY();
			}
			else if (direction.y > 0) {
				snapy = spacing * ((int) Math.floor((double) aabb.max.getBaseY() / spacing) + 1);
				snapy -= aabb.max.getBaseY();
			}

			if (direction.z < 0) {
				snapz = spacing * ((int) Math.ceil((double) aabb.min.getBaseZ() / spacing) - 1);
				snapz -= aabb.min.getBaseZ();
			}
			else if (direction.z > 0) {
				snapz = spacing * ((int) Math.floor((double) aabb.max.getBaseZ() / spacing) + 1);
				snapz -= aabb.max.getBaseZ();
			}

			drag.currentTranslation.x = snapx;
			drag.currentTranslation.y = snapy;
			drag.currentTranslation.z = snapz;

		}
		else {
			drag.currentTranslation.x = direction.x;
			drag.currentTranslation.y = direction.y;
			drag.currentTranslation.z = direction.z;
		}

		// apply the translation
		long currentFrame = editor.getFrame();
		for (MutablePoint p : transformPoints) {
			if (p.lastModified < currentFrame) {
				p.setTempTranslation(drag.currentTranslation);
				p.lastModified = currentFrame;
			}
		}
		aabb.min.setTempTranslation(drag.currentTranslation);
		aabb.max.setTempTranslation(drag.currentTranslation);

		endTransform();
	}

	public void startTranslation(Object obj, boolean immediate)
	{
		if (transformState != TransformState.IDLE) {
			Logger.log("Cannot initiate transformation when selection is already being transformed!", Priority.WARNING);
			return;
		}

		drag = new Drag();
		drag.immediate = immediate;

		if (obj != null && obj instanceof Vertex vtx)
			drag.referencePosition = vtx.getCurrentPos();
		else
			drag.referencePosition = null;

		startTransformation();

		for (T item : selectableList)
			item.addPoints(transformPoints);

		if (transformPoints.size() == 0) {
			endTransformation(null, false);
			return;
		}

		transformState = TransformState.TRANSLATE;
	}

	private static final void snapToGrid(BoundingBox aabb, int spacing, Vector3f displacement, Vector3f accumulated, Vector3f snapped)
	{
		int snapx = 0;
		int snapy = 0;
		int snapz = 0;

		if (displacement.x <= 0) {
			snapx += (int) accumulated.x + aabb.min.getBaseX(); // world coordinate after proposed translation
			snapx = spacing * (int) Math.round((double) snapx / spacing); // round to nearest gridline
			snapx -= aabb.min.getBaseX(); // convert to accepted translation
		}
		else if (displacement.x > 0) {
			snapx += (int) accumulated.x + aabb.max.getBaseX();
			snapx = spacing * (int) Math.round((double) snapx / spacing);
			snapx -= aabb.max.getBaseX();
		}

		if (displacement.y <= 0) {
			snapy += (int) accumulated.y + aabb.min.getBaseY();
			snapy = spacing * (int) Math.round((double) snapy / spacing);
			snapy -= aabb.min.getBaseY();
		}
		else if (displacement.y > 0) {
			snapy += (int) accumulated.y + aabb.max.getBaseY();
			snapy = spacing * (int) Math.round((double) snapy / spacing);
			snapy -= aabb.max.getBaseY();
		}

		if (displacement.z <= 0) {
			snapz += (int) accumulated.z + aabb.min.getBaseZ();
			snapz = spacing * (int) Math.round((double) snapz / spacing);
			snapz -= aabb.min.getBaseZ();
		}
		else if (displacement.z > 0) {
			snapz += (int) accumulated.z + aabb.max.getBaseZ();
			snapz = spacing * (int) Math.round((double) snapz / spacing);
			snapz -= aabb.max.getBaseZ();
		}

		snapped.x = snapx;
		snapped.y = snapy;
		snapped.z = snapz;
	}

	private static void snapToVerts(
		Iterable<Vector3f> snapPositions,
		OrthographicViewport viewport,
		Vector3f referencePos,
		Vector3f accumulatedTranslation,
		Vector3f currentTranslation)
	{
		Vector3f proj = viewport.getProjectionVector();

		Vector3f newPos = Vector3f.add(referencePos, accumulatedTranslation);
		newPos.x *= proj.x;
		newPos.y *= proj.y;
		newPos.z *= proj.z;

		double minDist = Double.MAX_VALUE;
		Vector3f minPos = null;

		for (Vector3f target : snapPositions) {
			target.x *= proj.x;
			target.y *= proj.y;
			target.z *= proj.z;

			float dx = newPos.x - target.x;
			float dy = newPos.y - target.y;
			float dz = newPos.z - target.z;

			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist < minDist) {
				minDist = dist;
				minPos = target;
			}
		}

		currentTranslation.set(accumulatedTranslation);
		if (minDist < viewport.getViewWorldSizeX() / 40.0f) {
			if (proj.x != 0)
				currentTranslation.x = minPos.x - referencePos.x;
			if (proj.y != 0)
				currentTranslation.y = minPos.y - referencePos.y;
			if (proj.z != 0)
				currentTranslation.z = minPos.z - referencePos.z;
		}
	}

	public void updateTranslation(MapEditViewport activeView, Vector3f displacement, int rawDx, int rawDy, double dt)
	{
		updateTranslation(activeView, displacement, rawDx, rawDy, dt, null, null);
	}

	public void updateTranslation(MapEditViewport activeView, Vector3f displacement, int rawDx, int rawDy, double dt, OrthographicViewport viewport,
		Iterable<Vector3f> snapVertices)
	{
		if (transformState != TransformState.TRANSLATE)
			return;

		// add the update to the total translation since the transform began
		if (axisConstraint != null) {
			if (axisConstraint.allowX)
				drag.accumulatedTranslation.x += displacement.x;
			if (axisConstraint.allowY)
				drag.accumulatedTranslation.y += displacement.y;
			if (axisConstraint.allowZ)
				drag.accumulatedTranslation.z += displacement.z;
		}
		else {
			drag.accumulatedTranslation.add(displacement);
		}

		drag.accumulatedRaw += Math.sqrt(rawDx * rawDx + rawDy * rawDy);
		drag.dragTime += dt;
		drag.dragFrames++;

		if (drag.immediate || (drag.dragFrames > 10 && drag.accumulatedRaw > 25) || drag.dragFrames > 30) {
			// do translation snap
			if (editor.vertexSnap && drag.referencePosition != null && snapVertices != null)
				snapToVerts(snapVertices, viewport, drag.referencePosition, drag.accumulatedTranslation, drag.currentTranslation);
			else if (editor.gridEnabled && editor.grid.power > 0 && editor.snapTranslation)
				snapToGrid(aabb, editor.grid.getSpacing(), displacement, drag.accumulatedTranslation, drag.currentTranslation);
			else
				drag.currentTranslation.set(drag.accumulatedTranslation);
		}

		// no motion allowed along forbidden axes
		if (axisConstraint != null) {
			if (!axisConstraint.allowX)
				drag.currentTranslation.x = 0;
			if (!axisConstraint.allowY)
				drag.currentTranslation.y = 0;
			if (!axisConstraint.allowZ)
				drag.currentTranslation.z = 0;
		}

		// apply the translation
		long currentFrame = editor.getFrame();
		for (MutablePoint p : transformPoints) {
			if (p.lastModified < currentFrame) {
				p.setTempTranslation(drag.currentTranslation);
				p.lastModified = currentFrame;
			}
		}
		aabb.min.setTempTranslation(drag.currentTranslation);
		aabb.max.setTempTranslation(drag.currentTranslation);
		transformGizmo.setTransformDisplacement(drag.currentTranslation);

		transformViewport = activeView;
		transformMessage = String.format("Translate: %d, %d, %d",
			(int) drag.currentTranslation.x, (int) drag.currentTranslation.y, (int) drag.currentTranslation.z);
		editor.gui.setTransformInfo(transformMessage);
	}

	public void startScale(Vector3f clickPoint, boolean uniform)
	{
		if (transformState != TransformState.IDLE) {
			Logger.log("Cannot initiate transformation when selection is already being transformed!", Priority.WARNING);
			return;
		}

		drag = new Drag();
		scale = new Scale();

		scale.uniformScaling = uniform;
		startTransformation();

		for (T item : selectableList)
			item.addPoints(transformPoints);

		if (transformPoints.size() == 0 || clickPoint == null) {
			endTransformation(null, false);
			return;
		}

		scale.startPoint = new Vector3f(clickPoint);
		if (axisConstraint != null) {
			if (!axisConstraint.allowX)
				scale.startPoint.x = transformOrigin.x;
			if (!axisConstraint.allowY)
				scale.startPoint.y = transformOrigin.y;
			if (!axisConstraint.allowZ)
				scale.startPoint.z = transformOrigin.z;
		}

		int sizeX = aabb.max.getX() - aabb.min.getX();
		int sizeY = aabb.max.getY() - aabb.min.getY();
		int sizeZ = aabb.max.getZ() - aabb.min.getZ();
		scale.originalSize = new Vector3f(sizeX, sizeY, sizeZ);
		scale.currentScale = new Vector3f(1.0f, 1.0f, 1.0f);

		transformState = TransformState.SCALE;
	}

	public void updateScale(MapEditViewport activeView, Vector3f displacement)
	{
		if (transformState != TransformState.SCALE)
			return;

		// add the update to the total translation since the transform began
		if (axisConstraint != null) {
			if (axisConstraint.allowX)
				drag.accumulatedTranslation.x += displacement.x;
			if (axisConstraint.allowY)
				drag.accumulatedTranslation.y += displacement.y;
			if (axisConstraint.allowZ)
				drag.accumulatedTranslation.z += displacement.z;
		}
		else {
			drag.accumulatedTranslation.x += displacement.x;
			drag.accumulatedTranslation.y += displacement.y;
			drag.accumulatedTranslation.z += displacement.z;
		}

		drag.currentTranslation.x = drag.accumulatedTranslation.x;
		drag.currentTranslation.y = drag.accumulatedTranslation.y;
		drag.currentTranslation.z = drag.accumulatedTranslation.z;

		Vector3f scaleEndPoint = Vector3f.add(scale.startPoint, drag.currentTranslation);

		Vector3f dstart = Vector3f.sub(scale.startPoint, transformOrigin);
		Vector3f dend = Vector3f.sub(scaleEndPoint, transformOrigin);

		if (Math.abs(scale.startPoint.x) > 1e-5 && Math.abs(scale.originalSize.x) > 1e-5)
			scale.currentScale.x = dend.x / dstart.x;
		else
			scale.currentScale.x = 1.0f;

		if (Math.abs(scale.startPoint.y) > 1e-5 && Math.abs(scale.originalSize.y) > 1e-5)
			scale.currentScale.y = dend.y / dstart.y;
		else
			scale.currentScale.y = 1.0f;

		if (Math.abs(scale.startPoint.z) > 1e-5 && Math.abs(scale.originalSize.z) > 1e-5)
			scale.currentScale.z = dend.z / dstart.z;
		else
			scale.currentScale.z = 1.0f;

		boolean allowX = ((axisConstraint == null || axisConstraint.allowX) && Math.abs(scale.originalSize.x) > 1e-5);
		boolean allowY = ((axisConstraint == null || axisConstraint.allowY) && Math.abs(scale.originalSize.y) > 1e-5);
		boolean allowZ = ((axisConstraint == null || axisConstraint.allowZ) && Math.abs(scale.originalSize.z) > 1e-5);

		if (scale.uniformScaling) {
			float rs = dstart.length();
			float re = dend.length();
			float sgn = Math.signum(Vector3f.dot(dstart, dend));
			float uniformScale = sgn * (re / rs);

			scale.currentScale.x = uniformScale;
			scale.currentScale.y = uniformScale;
			scale.currentScale.z = uniformScale;
		}
		// no motion allowed along forbidden axes
		else if (axisConstraint != null) {
			if (!allowX)
				scale.currentScale.x = 1.0f;
			if (!allowY)
				scale.currentScale.y = 1.0f;
			if (!allowZ)
				scale.currentScale.z = 1.0f;
		}

		if (editor.snapScale) {
			if (editor.snapScaleToGrid) {
				float dg = editor.grid.getSpacing();
				double gx = dg / scale.originalSize.x;
				double gy = dg / scale.originalSize.y;
				double gz = dg / scale.originalSize.z;

				if (allowX && Math.abs(scale.originalSize.x) > 1e-5)
					scale.currentScale.x = (float) (gx * Math.round(scale.currentScale.x / gx));
				else
					scale.currentScale.x = 1.0f;

				if (allowY && Math.abs(scale.originalSize.y) > 1e-5)
					scale.currentScale.y = (float) (gy * Math.round(scale.currentScale.y / gy));
				else
					scale.currentScale.y = 1.0f;

				if (allowZ && Math.abs(scale.originalSize.z) > 1e-5)
					scale.currentScale.z = (float) (gz * Math.round(scale.currentScale.z / gz));
				else
					scale.currentScale.z = 1.0f;
			}
			else {
				if (allowX)
					scale.currentScale.x = Math.round(scale.currentScale.x * 10.0f) / 10.0f;
				if (allowY)
					scale.currentScale.y = Math.round(scale.currentScale.y * 10.0f) / 10.0f;
				if (allowZ)
					scale.currentScale.z = Math.round(scale.currentScale.z * 10.0f) / 10.0f;
			}
		}

		long currentFrame = editor.getFrame();
		for (MutablePoint p : transformPoints) {
			if (p.lastModified < currentFrame) {
				p.setTempScale(transformOrigin, scale.currentScale);
				p.lastModified = currentFrame;
			}
		}
		aabb.min.setTempScale(transformOrigin, scale.currentScale);
		aabb.max.setTempScale(transformOrigin, scale.currentScale);

		transformViewport = activeView;
		transformMessage = String.format("Scale: %s, %s, %s", scale.currentScale.x, scale.currentScale.y, scale.currentScale.z);
		editor.gui.setTransformInfo(transformMessage);
	}

	public void startRotation(Axis axis, Vector3f clickPoint)
	{
		if (transformState != TransformState.IDLE) {
			Logger.log("Cannot initiate transformation when selection is already being transformed!", Priority.WARNING);
			return;
		}

		startTransformation();

		for (T item : selectableList) {
			if (item.allowRotation(axis))
				item.addPoints(transformPoints);

			item.addAngles(transformAngles);
		}

		if (transformPoints.size() == 0 && transformAngles.size() == 0) {
			endTransformation(null, false);
			return;
		}

		rotation = new Rotation();
		rotation.axis = axis;
		rotation.startPoint = new Vector3f(clickPoint);

		// project start point onto rotation plane
		switch (rotation.axis) {
			case X:
				rotation.startPoint.x = transformOrigin.x;
				break;
			case Y:
				rotation.startPoint.y = transformOrigin.y;
				break;
			case Z:
				rotation.startPoint.z = transformOrigin.z;
				break;
		}

		rotation.endPoint = new Vector3f(rotation.startPoint);
		rotation.startAngle = getAngle(rotation.endPoint);
		rotation.angle = 0;

		transformState = TransformState.ROTATE;
	}

	public void updateRotation(MapEditViewport activeView, Vector3f vec)
	{
		if (transformState != TransformState.ROTATE)
			return;

		rotation.endPoint.x = vec.x;
		rotation.endPoint.y = vec.y;
		rotation.endPoint.z = vec.z;

		// project click point onto rotation plane
		switch (rotation.axis) {
			case X:
				rotation.endPoint.x = transformOrigin.x;
				break;
			case Y:
				rotation.endPoint.y = transformOrigin.y;
				break;
			case Z:
				rotation.endPoint.z = transformOrigin.z;
				break;
		}

		// calculate rotation angle
		rotation.endAngle = getAngle(rotation.endPoint);
		double angle = rotation.endAngle - rotation.startAngle;
		if (editor.snapRotation)
			rotation.angle = Math.round(angle / editor.getRotationSnap()) * editor.getRotationSnap();
		else
			rotation.angle = angle;

		long currentFrame = editor.getFrame();

		for (MutablePoint p : transformPoints)
			if (p.lastModified < currentFrame) {
				rotatePosition(rotation.axis, rotation.angle, p);
				p.lastModified = currentFrame;
			}

		for (MutableAngle a : transformAngles)
			if (a.lastModified < currentFrame) {
				rotateAngle(rotation.axis, rotation.angle, a);
				a.lastModified = currentFrame;
			}

		transformViewport = activeView;
		transformMessage = String.format("Rotate %s: %d degrees", rotation.axis, (int) rotation.angle);
		editor.gui.setTransformInfo(transformMessage);
	}

	private void rotateAngle(Axis axis, double angle, MutableAngle a)
	{
		if (axis != a.axis)
			return;

		// positive rotations are counter-clockwise in a right-hand coordinate
		// system, but we can allow for some angles to be defined clockwise.
		if (a.clockwise)
			a.setTempAngle(a.getBaseAngle() - angle);
		else
			a.setTempAngle(a.getBaseAngle() + angle);
	}

	private void rotatePosition(Axis axis, double angle, MutablePoint p)
	{
		Vector3f relative = new Vector3f(
			p.getBaseX() - transformOrigin.x,
			p.getBaseY() - transformOrigin.y,
			p.getBaseZ() - transformOrigin.z);

		angle = Math.toRadians(angle);
		double dx = 0;
		double dy = 0;
		double dz = 0;

		switch (axis) {
			case X:
				dy = (relative.y * Math.cos(angle) - relative.z * Math.sin(angle));
				dz = (relative.y * Math.sin(angle) + relative.z * Math.cos(angle));
				p.setTempPosition(
					p.getX(),
					(int) (transformOrigin.y + dy),
					(int) (transformOrigin.z + dz));
				break;
			case Y:
				angle = -angle;
				dx = (relative.x * Math.cos(angle) - relative.z * Math.sin(angle));
				dz = (relative.x * Math.sin(angle) + relative.z * Math.cos(angle));
				p.setTempPosition(
					(int) (transformOrigin.x + dx),
					p.getY(),
					(int) (transformOrigin.z + dz));
				break;
			case Z:
				dx = (relative.x * Math.cos(angle) - relative.y * Math.sin(angle));
				dy = (relative.x * Math.sin(angle) + relative.y * Math.cos(angle));
				p.setTempPosition(
					(int) (transformOrigin.x + dx),
					(int) (transformOrigin.y + dy),
					p.getZ());
				break;
		}
	}

	private float getAngle(Vector3f vec)
	{
		Vector3f relative = new Vector3f(
			vec.x - transformOrigin.x,
			vec.y - transformOrigin.y,
			vec.z - transformOrigin.z);

		// project click vector onto rotation plane
		switch (rotation.axis) {
			case X:
				relative.x = transformOrigin.x;
				break;
			case Y:
				relative.y = transformOrigin.y;
				break;
			case Z:
				relative.z = transformOrigin.z;
				break;
		}

		double angle = 0.0f;

		switch (rotation.axis) {
			case X:
				angle = (float) Math.atan2(relative.y, -relative.z);
				break;
			case Y:
				angle = (float) Math.atan2(-relative.z, relative.x);
				break;
			case Z:
				angle = (float) Math.atan2(relative.y, relative.x);
				break;
		}

		return (float) Math.toDegrees(angle);
	}

	public void endTransform()
	{
		if (transformState == TransformState.TRANSLATE) {
			boolean changed = (drag.currentTranslation.x != 0 || drag.currentTranslation.y != 0 || drag.currentTranslation.z != 0);

			TransformMatrix tx = new TransformMatrix();
			tx.setTranslation(drag.currentTranslation.x, drag.currentTranslation.y, drag.currentTranslation.z);

			endTransformation(tx, changed);
			axisConstraint = null;
		}
		else if (transformState == TransformState.ROTATE) {
			boolean changed = (Math.abs(rotation.angle) > 1E-12);

			TransformMatrix r = new TransformMatrix();
			r.setRotation(rotation.axis, rotation.angle);

			TransformMatrix tx = new TransformMatrix();
			tx.setTranslation(-transformOrigin.x, -transformOrigin.y, -transformOrigin.z);
			tx.concat(r);
			tx.translate(transformOrigin);

			endTransformation(tx, changed);
		}
		else if (transformState == TransformState.SCALE) {
			boolean changedX = Math.abs(scale.currentScale.x - 1.0f) > 1e-5;
			boolean changedY = Math.abs(scale.currentScale.y - 1.0f) > 1e-5;
			boolean changedZ = Math.abs(scale.currentScale.z - 1.0f) > 1e-5;
			boolean changed = changedX || changedY || changedZ;

			TransformMatrix tx = new TransformMatrix();
			tx.setScale(scale.currentScale.x, scale.currentScale.y, scale.currentScale.z);

			endTransformation(tx, changed);
			axisConstraint = null;
		}

		drag = null;
		scale = null;
		rotation = null;
		editor.gui.setTransformInfo("");
	}

	public void addWithoutSelecting(T item)
	{
		selectableList.add(item);
		item.addTo(aabb);
		centerTransformHandle();
	}

	/**
	 * Adds an item to the selection.
	 * @param item
	 */
	public void addAndSelect(T item)
	{
		if (item.isSelected())
			return;

		selectableList.add(item);
		item.setSelected(true);
		item.addTo(aabb);
		centerTransformHandle();
	}

	/**
	 * Adds a list of items to the selection.
	 * @param items
	 */
	public void addAndSelect(Iterable<T> items)
	{
		int count = 0;

		for (T item : items) {
			if (item.isSelected())
				continue;

			selectableList.add(item);
			item.setSelected(true);
			item.addTo(aabb);
			count++;
		}

		if (count > 0)
			centerTransformHandle();
	}

	/**
	 * Removes a particular item from the selection.
	 * @param item
	 */
	public void removeAndDeselect(T item)
	{
		if (!item.isSelected())
			return;

		selectableList.remove(item);
		item.setSelected(false);
		recalculateAABB();

		if (selectableList.isEmpty()) {
			transformGizmo = null;
		}
		else
			centerTransformHandle();
	}

	/**
	 * Removes a list of items from the selection.
	 * @param items
	 */
	public void removeAndDeselect(Iterable<T> items)
	{
		for (T item : items) {
			if (!item.isSelected())
				continue;

			selectableList.remove(item);
			item.setSelected(false);
		}
		recalculateAABB();

		if (selectableList.isEmpty())
			transformGizmo = null;
		else
			centerTransformHandle();
	}

	/**
	 * Clears the selection.
	 */
	public void clear()
	{
		for (T item : selectableList)
			item.setSelected(false);
		selectableList.clear();
		recalculateAABB();
		transformGizmo = null;
	}

	/**
	 * @return Whether the list is empty or not.
	 */
	public boolean isEmpty()
	{
		return selectableList.isEmpty();
	}

	/**
	 * @return Most recently added item to the selection.
	 */
	public T getMostRecent()
	{
		if (selectableList.isEmpty())
			return null;

		return selectableList.get(selectableList.size() - 1);
	}

	public void updateAABB()
	{
		recalculateAABB();

		if (transformGizmo != null)
			centerTransformHandle();
	}

	/**
	 * Clears and rebuilds the selection AABB.
	 */
	private void recalculateAABB()
	{
		aabb.clear();
		for (T item : selectableList)
			item.addTo(aabb);
	}

	/**
	 * @return Center of the selection AABB.
	 */
	public Vector3f getCenter()
	{
		int centerx = (aabb.max.getX() + aabb.min.getX()) / 2;
		int centery = (aabb.max.getY() + aabb.min.getY()) / 2;
		int centerz = (aabb.max.getZ() + aabb.min.getZ()) / 2;

		return new Vector3f(centerx, centery, centerz);
	}

	/**
	 * Moves the transform handle to the center of the selection,
	 * creating a new handle if necessary.
	 */
	private void centerTransformHandle()
	{
		if (transformGizmo == null)
			transformGizmo = new TransformGizmo(this, getCenter());
		else
			transformGizmo.setOrigin(getCenter());
	}

	public boolean render(Renderer renderer, MapEditViewport view)
	{
		if (transformGizmo != null && editor.showGizmo) {
			float scaleFactor = view.getScaleFactor(
				transformGizmo.origin.getX(),
				transformGizmo.origin.getY(),
				transformGizmo.origin.getZ());

			transformGizmo.render(editor, view.camera.pos, scaleFactor);
			renderTransform(view);
			renderAABB(renderer, view.type, scaleFactor);
			return true;
		}
		return false;
	}

	public void renderTransform(MapEditViewport view)
	{
		switch (transformState) {
			case TRANSLATE:
				renderTranslation();
				break;

			case ROTATE:
				if (view.type != ViewType.PERSPECTIVE)
					renderRotation(view);
				break;

			default:
				break;
		}
	}

	private void renderTranslation()
	{
		RenderState.setLineWidth(2.0f);
		RenderState.setPointSize(8.0f);
		RenderState.setColor(PresetColor.WHITE);

		PointRenderQueue.addPoint().setPosition(transformOrigin.x, transformOrigin.y, transformOrigin.z);
		PointRenderQueue.addPoint().setPosition(
			transformOrigin.x + drag.currentTranslation.x,
			transformOrigin.y + drag.currentTranslation.y,
			transformOrigin.z + drag.currentTranslation.z);

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(transformOrigin.x, transformOrigin.y, transformOrigin.z).getIndex(),
			LineRenderQueue.addVertex().setPosition(
				transformOrigin.x + drag.currentTranslation.x,
				transformOrigin.y + drag.currentTranslation.y,
				transformOrigin.z + drag.currentTranslation.z).getIndex());

		RenderState.enableDepthTest(false);
		PointRenderQueue.render(true);
		LineRenderQueue.render(true);
		RenderState.enableDepthTest(true);
	}

	private void renderRotation(MapEditViewport view)
	{
		RenderState.setLineWidth(2.0f);
		RenderState.setPointSize(8.0f);
		RenderState.setColor(PresetColor.WHITE);

		PointRenderQueue.addPoint().setPosition(transformOrigin.x, transformOrigin.y, transformOrigin.z);
		PointRenderQueue.addPoint().setPosition(rotation.startPoint.x, rotation.startPoint.y, rotation.startPoint.z);
		PointRenderQueue.addPoint().setPosition(rotation.endPoint.x, rotation.endPoint.y, rotation.endPoint.z);

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(transformOrigin.x, transformOrigin.y, transformOrigin.z).getIndex(),
			LineRenderQueue.addVertex().setPosition(rotation.startPoint.x, rotation.startPoint.y, rotation.startPoint.z).getIndex());

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(transformOrigin.x, transformOrigin.y, transformOrigin.z).getIndex(),
			LineRenderQueue.addVertex().setPosition(rotation.endPoint.x, rotation.endPoint.y, rotation.endPoint.z).getIndex());

		RenderState.enableDepthTest(false);
		PointRenderQueue.render(true);
		LineRenderQueue.render(true);
		RenderState.enableDepthTest(true);
	}

	public void renderAABB(Renderer renderer, ViewType type, float scaleFactor)
	{
		if (isEmpty() || aabb.isEmpty())
			return;

		if (type != ViewType.PERSPECTIVE)
			RenderState.enableDepthTest(false);

		float colorAmount = Renderer.interpColor(0.0f, 1.0f);

		RenderState.setColor(1.0f, colorAmount, colorAmount);
		RenderState.setPointSize(5.0f);
		enqueuePointsForRendering();

		RenderState.setDepthWrite(false);
		PointRenderQueue.render(true);
		RenderState.setDepthWrite(true);

		RenderState.setColor(1.0f, colorAmount, colorAmount);
		RenderState.setLineWidth(3.0f);
		enqueueLinesForRendering(type);

		LineShader shader = ShaderManager.use(LineShader.class);
		shader.dashRatio.set(0.5f);
		shader.dashSize.set(20.0f * (float) Math.sqrt(scaleFactor));
		shader.dashSpeedRate.set(0.5f);
		LineRenderQueue.render(shader, true);

		if (type != ViewType.PERSPECTIVE)
			RenderState.enableDepthTest(true);
	}

	private void enqueuePointsForRendering()
	{
		int minx = aabb.min.getX();
		int miny = aabb.min.getY();
		int minz = aabb.min.getZ();

		int maxx = aabb.max.getX();
		int maxy = aabb.max.getY();
		int maxz = aabb.max.getZ();

		PointRenderQueue.addPoint().setPosition(maxx, maxy, maxz);
		PointRenderQueue.addPoint().setPosition(maxx, maxy, minz);
		PointRenderQueue.addPoint().setPosition(maxx, miny, maxz);
		PointRenderQueue.addPoint().setPosition(maxx, miny, minz);
		PointRenderQueue.addPoint().setPosition(minx, maxy, maxz);
		PointRenderQueue.addPoint().setPosition(minx, maxy, minz);
		PointRenderQueue.addPoint().setPosition(minx, miny, maxz);
		PointRenderQueue.addPoint().setPosition(minx, miny, minz);
	}

	private void enqueueLinesForRendering(ViewType type)
	{
		if (aabb.isEmpty())
			return;

		int minx = aabb.min.getX();
		int miny = aabb.min.getY();
		int minz = aabb.min.getZ();

		int maxx = aabb.max.getX();
		int maxy = aabb.max.getY();
		int maxz = aabb.max.getZ();

		int mmm = LineRenderQueue.addVertex().setPosition(minx, miny, minz).getIndex();
		int Mmm = LineRenderQueue.addVertex().setPosition(maxx, miny, minz).getIndex();
		int mMm = LineRenderQueue.addVertex().setPosition(minx, maxy, minz).getIndex();
		int MMm = LineRenderQueue.addVertex().setPosition(maxx, maxy, minz).getIndex();
		int mmM = LineRenderQueue.addVertex().setPosition(minx, miny, maxz).getIndex();
		int MmM = LineRenderQueue.addVertex().setPosition(maxx, miny, maxz).getIndex();
		int mMM = LineRenderQueue.addVertex().setPosition(minx, maxy, maxz).getIndex();
		int MMM = LineRenderQueue.addVertex().setPosition(maxx, maxy, maxz).getIndex();

		switch (type) {
			case PERSPECTIVE:
				if (minx == maxx)
					LineRenderQueue.addLine(mmm, mMm, mMM, mmM, mmm);
				else if (miny == maxy)
					LineRenderQueue.addLine(mmm, Mmm, MmM, mmM, mmm);
				else if (minz == maxz)
					LineRenderQueue.addLine(mmm, Mmm, MMm, mMm, mmm);
				else {
					// full 3D
					LineRenderQueue.addLine(mmm, Mmm, MMm, mMm, mmm);
					LineRenderQueue.addLine(mmM, mMM, MMM, MmM, mmM);
					LineRenderQueue.addLine(mmm, mmM);
					LineRenderQueue.addLine(Mmm, MmM);
					LineRenderQueue.addLine(mMm, mMM);
					LineRenderQueue.addLine(MMm, MMM);
				}
				break;

			case SIDE: // YZ, X const
				if (miny == maxy)
					LineRenderQueue.addLine(mmm, mmM);
				else if (minz == maxz)
					LineRenderQueue.addLine(mmm, mMm);
				else
					LineRenderQueue.addLine(mmm, mMm, mMM, mmM, mmm);
				break;

			case TOP: // XZ, Y const
				if (minx == maxx)
					LineRenderQueue.addLine(mmm, mmM);
				else if (minz == maxz)
					LineRenderQueue.addLine(mmm, Mmm);
				else
					LineRenderQueue.addLine(mmm, mmM, MmM, Mmm, mmm);
				break;

			case FRONT: // XY, Z const
				if (minx == maxx)
					LineRenderQueue.addLine(mmm, mMm);
				else if (miny == maxy)
					LineRenderQueue.addLine(mmm, Mmm);
				else
					LineRenderQueue.addLine(mmm, Mmm, MMm, mMm, mmm);
				break;
		}
	}

	private static final class DirectTransformSelection<T extends Selectable> extends AbstractCommand
	{
		private LinkedList<PointBackup> backupList;
		private Selection<T> selection;

		public DirectTransformSelection(MapEditor editor, Selection<T> selection)
		{
			super("Direct Transform Selection");
			backupList = new LinkedList<>();
			this.selection = selection;

			IdentityHashSet<MutablePoint> pointList = new IdentityHashSet<>();

			for (T item : selection.selectableList) {
				item.addPoints(pointList);
				item.endTransformation();
			}

			selection.transformGizmo.addPoints(pointList);

			for (MutablePoint p : pointList) {
				backupList.add(p.getBackup());
			}
		}

		@Override
		public void exec()
		{
			super.exec();

			for (PointBackup b : backupList)
				b.pos.setPosition(b.newx, b.newy, b.newz);

			selection.updateAABB();
		}

		@Override
		public void undo()
		{
			super.undo();

			for (PointBackup b : backupList)
				b.pos.setPosition(b.oldx, b.oldy, b.oldz);

			selection.updateAABB();
		}
	}
}
