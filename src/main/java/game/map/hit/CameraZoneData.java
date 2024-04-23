package game.map.hit;

import static game.map.MapKey.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import game.map.MapObject;
import game.map.MutablePoint;
import game.map.editor.MapEditor;
import game.map.editor.UpdateProvider;
import game.map.editor.camera.CameraController;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.geometry.GeometryUtils;
import game.map.editor.geometry.Vector3f;
import game.map.editor.render.PresetColor;
import game.map.editor.render.Renderer;
import game.map.editor.selection.SelectablePoint;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;
import util.MathUtil;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class CameraZoneData extends UpdateProvider implements XmlSerializable
{
	public final MapObject parent;

	private ControlType type;
	private boolean flag; // breaks o863 from kzn_20, which erroneously uses -100000 (FFFE7960) for the flag

	public EditableField<Float> boomLength = EditableFieldFactory.create(0.0f)
		.setCallback((obj) -> notifyListeners()).setName("Set Boom Length").build();

	public EditableField<Float> boomPitch = EditableFieldFactory.create(0.0f)
		.setCallback((obj) -> notifyListeners()).setName("Set Boom Pitch").build();

	public EditableField<Float> viewPitch = EditableFieldFactory.create(0.0f)
		.setCallback((obj) -> notifyListeners()).setName("Set View Pitch").build();

	private Vector3f drawSamplePos = new Vector3f();
	private Vector3f drawTargetPos = new Vector3f();

	//TODO is there a problem representing these values with selectable points?
	// remember, these are actually FLOAT values. is it sufficient to store positions as integers?
	public SelectablePoint posA;
	public SelectablePoint posB;
	public SelectablePoint posC; // used by modes 2/5

	private final List<SelectablePoint> points;

	private static final int[] DEFAULT_CAMERA = {
			0,
			Float.floatToIntBits(450.0f),
			Float.floatToIntBits(16.0f),
			Float.floatToIntBits(0.0f),
			Float.floatToIntBits(10.0f),
			Float.floatToIntBits(50.0f),
			Float.floatToIntBits(0.0f),
			Float.floatToIntBits(10.0f),
			Float.floatToIntBits(-50.0f),
			Float.floatToIntBits(-6.0f),
			0
	};

	private static float helpPointSize = 16.0f;
	private static float projectionPointSize = 8.0f;

	private static final float HELP_LINE_SIZE = 3.0f;
	private static final float PROJECT_LINE_SIZE = 2.0f;

	public CameraZoneData(MapObject parent)
	{
		this(parent, DEFAULT_CAMERA);
	}

	public CameraZoneData(MapObject parent, int[] cameraData)
	{
		this.parent = parent;
		boomLength.set(Float.intBitsToFloat(cameraData[1]));
		boomPitch.set(Float.intBitsToFloat(cameraData[2]));
		float[] pos = {
				Float.intBitsToFloat(cameraData[3]),
				Float.intBitsToFloat(cameraData[4]),
				Float.intBitsToFloat(cameraData[5]),
				Float.intBitsToFloat(cameraData[6]),
				Float.intBitsToFloat(cameraData[7]),
				Float.intBitsToFloat(cameraData[8])
		};
		viewPitch.set(Float.intBitsToFloat(cameraData[9]));
		setFlag((cameraData[10] != 0));

		points = new ArrayList<>(4);

		// -1000000.0f is used as a placeholder
		for (int i = 0; i < 6; i++) {
			if (pos[i] < Short.MIN_VALUE)
				pos[i] = 0;
		}

		// hack to fix a couple egde cases (mim_09, center)
		// real solution is to use float for selectable points
		//XXX mutable point should be float!
		posA = new SelectablePoint(new MutablePoint(
			MathUtil.roundAwayFromZero(pos[0]),
			MathUtil.roundAwayFromZero(pos[1]),
			MathUtil.roundAwayFromZero(pos[2])), 2.0f);
		posB = new SelectablePoint(new MutablePoint(
			MathUtil.roundAwayFromZero(pos[3]),
			MathUtil.roundAwayFromZero(pos[4]),
			MathUtil.roundAwayFromZero(pos[5])), 2.0f);
		posC = new SelectablePoint(new MutablePoint(
			MathUtil.roundAwayFromZero(pos[1]),
			0,
			MathUtil.roundAwayFromZero(pos[4])), 2.0f);
		points.add(posA);
		points.add(posB);
		points.add(posC);

		setType(ControlType.getType(cameraData[0]));
		if (type == ControlType.TYPE_4)
			posA.setY(posB.getY());
	}

	public int[] getData()
	{
		if (type == ControlType.TYPE_4)
			return new int[] {
					type.index,
					Float.floatToIntBits(boomLength.get()),
					Float.floatToIntBits(boomPitch.get()),
					Float.floatToIntBits(posA.getX()),
					Float.floatToIntBits(posA.getY()),
					Float.floatToIntBits(posA.getZ()),
					Float.floatToIntBits(posB.getX()),
					Float.floatToIntBits(posB.getY()),
					Float.floatToIntBits(posB.getZ()),
					Float.floatToIntBits(viewPitch.get()),
					flag ? 1 : 0 };
		else
			return new int[] {
					type.index,
					Float.floatToIntBits(boomLength.get()),
					Float.floatToIntBits(boomPitch.get()),
					Float.floatToIntBits(posA.getX()),
					Float.floatToIntBits(posC.getX()),
					Float.floatToIntBits(posA.getZ()),
					Float.floatToIntBits(posB.getX()),
					Float.floatToIntBits(posC.getZ()),
					Float.floatToIntBits(posB.getZ()),
					Float.floatToIntBits(viewPitch.get()),
					flag ? 1 : 0 };
	}

	public void copy(CameraZoneData other)
	{
		this.type = other.type;
		this.flag = other.flag;
		this.boomLength.copy(other.boomLength);
		this.boomPitch.copy(other.boomPitch);
		this.viewPitch.copy(other.viewPitch);
		this.posA.point.setPosition(other.posA.point);
		this.posB.point.setPosition(other.posB.point);
		this.posC.point.setPosition(other.posC.point);
	}

	public static CameraZoneData read(XmlReader xmr, Element cameraElement, MapObject parent)
	{
		CameraZoneData controller = new CameraZoneData(parent);
		controller.fromXML(xmr, cameraElement);
		return controller;
	}

	@Override
	public void fromXML(XmlReader xmr, Element cameraElement)
	{
		setType(xmr.readEnum(cameraElement, ATTR_CAM_TYPE, ControlType.class));
		setFlag(xmr.readBoolean(cameraElement, ATTR_CAM_FLAG));

		boomLength.set(xmr.readFloat(cameraElement, ATTR_CAM_BOOM_LEN));
		boomPitch.set(xmr.readFloat(cameraElement, ATTR_CAM_BOOM_PITCH));
		viewPitch.set(xmr.readFloat(cameraElement, ATTR_CAM_VIEW_PITCH));

		int[] a = xmr.readIntArray(cameraElement, ATTR_CAM_POS_A, 3);
		int[] b = xmr.readIntArray(cameraElement, ATTR_CAM_POS_B, 3);
		int[] c = xmr.readIntArray(cameraElement, ATTR_CAM_POS_C, 3);

		posA = new SelectablePoint(new MutablePoint(a[0], a[1], a[2]), 2.0f);
		posB = new SelectablePoint(new MutablePoint(b[0], b[1], b[2]), 2.0f);
		posC = new SelectablePoint(new MutablePoint(c[0], c[1], c[2]), 2.0f);
		points.add(posA);
		points.add(posB);
		points.add(posC);

		setType(type);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag camTag = xmw.createTag(TAG_CAMERA, true);

		xmw.addEnum(camTag, ATTR_CAM_TYPE, type);
		xmw.addBoolean(camTag, ATTR_CAM_FLAG, flag);

		xmw.addFloat(camTag, ATTR_CAM_BOOM_LEN, boomLength.get());
		xmw.addFloat(camTag, ATTR_CAM_BOOM_PITCH, boomPitch.get());
		xmw.addFloat(camTag, ATTR_CAM_VIEW_PITCH, viewPitch.get());

		xmw.addIntArray(camTag, ATTR_CAM_POS_A, posA.getX(), posA.getY(), posA.getZ());
		xmw.addIntArray(camTag, ATTR_CAM_POS_B, posB.getX(), posB.getY(), posB.getZ());
		xmw.addIntArray(camTag, ATTR_CAM_POS_C, posC.getX(), posC.getY(), posC.getZ());

		xmw.printTag(camTag);
	}

	public List<SelectablePoint> getPoints()
	{ return points; }

	public void drawHelpers(Renderer renderer, CameraController controller, boolean editPointsMode)
	{
		boolean pieMode = MapEditor.instance().isPlayInEditorMode();

		drawSamplePos = controller.getSamplePosition();
		drawTargetPos = controller.getTargetPosition();

		helpPointSize = editPointsMode ? 12.0f : 8.0f;
		projectionPointSize = 8.0f;

		RenderState.setLineWidth(HELP_LINE_SIZE);

		float h = parent.AABB.getCenter().y + 50;

		switch (type) {
			case TYPE_0:
				drawHelpers0(renderer, pieMode, h);
				break;
			case TYPE_1:
				drawHelpers1(renderer, pieMode, h);
				break;
			case TYPE_2:
				drawHelpers2(renderer, pieMode, h);
				break;
			case TYPE_3:
				break; // no control points
			case TYPE_4:
				drawHelpers4(renderer, pieMode, h);
				break;
			case TYPE_5:
				drawHelpers5(renderer, pieMode, h);
				break;
			case TYPE_6:
				drawHelpers6(renderer, pieMode, h);
				break;
			default:
				throw new IllegalStateException("Invalid camera control type.");
		}

		if (pieMode && GeometryUtils.dist3D(drawSamplePos, drawTargetPos) > 1) {
			float colorAmount = Renderer.interpColor(0.0f, 1.0f);

			PointRenderQueue.addPoint(helpPointSize)
				.setColor(colorAmount, 1.0f, colorAmount)
				.setPosition(drawTargetPos.x, drawTargetPos.y, drawTargetPos.z);

			PointRenderQueue.addPoint(helpPointSize)
				.setColor(1.0f, 0.0f, colorAmount)
				.setPosition(drawSamplePos.x, drawSamplePos.y, drawSamplePos.z);

			LineRenderQueue.addLine( //TODO stipple 00FF
				LineRenderQueue.addVertex()
					.setPosition(drawTargetPos.x, drawTargetPos.y, drawTargetPos.z)
					.setColor(colorAmount, 1.0f, colorAmount).getIndex(),
				LineRenderQueue.addVertex()
					.setPosition(drawSamplePos.x, drawSamplePos.y, drawSamplePos.z)
					.setColor(1.0f, 0.0f, colorAmount).getIndex());
		}

		RenderState.setModelMatrix(null);
		LineRenderQueue.render(true);
		PointRenderQueue.render(true);
	}

	// show A/B as 2D points, tether A to B
	private void drawHelpers0(Renderer renderer, boolean previewMode, float h)
	{
		LineRenderQueue.addLine(lineVertexA(h), lineVertexB(h));

		projectA(h);
		projectB(h);

		if (previewMode && flag) {
			RenderState.setColor(PresetColor.BLUE);
			LineRenderQueue.addLine( //TODO stipple 00FF
				LineRenderQueue.addVertex().setPosition(posB.getX(), h, posB.getZ()).getIndex(),
				LineRenderQueue.addVertex().setPosition(drawTargetPos.x, h, drawTargetPos.z).getIndex());
		}
	}

	private void drawHelpers1(Renderer renderer, boolean previewMode, float h)
	{
		if (flag) {
			float dx = posB.getX() - posA.getX();
			float dz = posB.getZ() - posA.getZ();
			float r = (float) Math.sqrt(dx * dx + dz * dz);

			int n = 32 + Math.round((float) Math.sqrt(r));
			if (n % 2 == 1)
				n++;

			double dA = 2 * Math.PI / n;

			int[] indices = new int[n + 1];

			for (int i = 0; i <= n; i++) {
				float x = r * (float) Math.sin(i * dA);
				float z = r * (float) Math.cos(i * dA);
				indices[i] = LineRenderQueue.addVertex().setPosition(posA.getX() + x, h, posA.getZ() + z).getIndex();
			}

			for (int i = 0; i < n; i++)
				LineRenderQueue.addLine(indices[i], indices[i + 1]);

			LineRenderQueue.addLine(lineVertexA(h), lineVertexB(h));
			projectB(h);
		}

		projectA(h);
	}

	// show A/B/C as 2D points, tether B to C
	private void drawHelpers2(Renderer renderer, boolean previewMode, float h)
	{
		if (flag)
			return;

		if (previewMode) {
			Vector3f vBT = Vector3f.sub(drawTargetPos, posB.getPosition());
			Vector3f vCT = Vector3f.sub(drawTargetPos, posC.getPosition());
			vBT.y = 0;
			vCT.y = 0;

			RenderState.setLineWidth(1.0f);

			if (vBT.length() < vCT.length()) {
				RenderState.setColor(PresetColor.BLUE);
				LineRenderQueue.addLine( //TODO stipple 00FF
					LineRenderQueue.addVertex().setPosition(posB.getX(), h, posB.getZ()).getIndex(),
					LineRenderQueue.addVertex().setPosition(drawTargetPos.x, h, drawTargetPos.z).getIndex());
			}
			else {
				RenderState.setColor(PresetColor.GREEN);
				LineRenderQueue.addLine( //TODO stipple 00FF
					LineRenderQueue.addVertex().setPosition(posC.getX(), h, posC.getZ()).getIndex(),
					LineRenderQueue.addVertex().setPosition(drawTargetPos.x, h, drawTargetPos.z).getIndex());
			}
		}

		LineRenderQueue.addLine(lineVertexB(h), lineVertexC(h));
		LineRenderQueue.addLine(lineVertexA(h), lineVertexB(h));

		Vector3f vBA = Vector3f.sub(posA.getPosition(), posB.getPosition());
		vBA.y = 0;

		if (vBA.length() > MathUtil.SMALL_NUMBER) {
			Vector3f endPoint = Vector3f.cross(new Vector3f(0.0f, 64.0f, 0.0f), vBA.normalize());

			float colorAmount = Renderer.interpColor(0.0f, 1.0f);
			LineRenderQueue.addLine(lineVertexB(h),
				LineRenderQueue.addVertex().setColor(0.5f + colorAmount / 2, 0.5f + colorAmount / 2, 1.0f)
					.setPosition(posB.getX() + endPoint.x, h, posB.getZ() + endPoint.z).getIndex());
		}

		projectA(h);
		projectB(h);
		projectC(h);
	}

	// show A as a 2D point and B as a 3D point, tether A to B
	private void drawHelpers4(Renderer renderer, boolean previewMode, float h)
	{
		LineRenderQueue.addLine(lineVertexA(h), lineVertexB(h));
		projectA(h);
		pointB(posB.getY());
	}

	// show A/B/C as 2D points, tether A to B and target to C
	private void drawHelpers5(Renderer renderer, boolean previewMode, float h)
	{
		if (!flag) {
			if (previewMode) {
				Vector3f vBT = Vector3f.sub(drawTargetPos, posB.getPosition());
				Vector3f vCT = Vector3f.sub(drawTargetPos, posC.getPosition());
				vBT.y = 0;
				vCT.y = 0;

				int startVtx = (vBT.length() < vCT.length()) ? lineVertexB(h) : lineVertexC(h);
				LineRenderQueue.addLine(startVtx, //TODO stipple 00FF
					LineRenderQueue.addVertex().setColor(PresetColor.WHITE)
						.setPosition(drawTargetPos.x, drawTargetPos.y, drawTargetPos.z).getIndex());

				LineRenderQueue.addLine(lineVertexA(h), //TODO stipple 00FF
					LineRenderQueue.addVertex().setColor(PresetColor.WHITE)
						.setPosition(drawTargetPos.x, drawTargetPos.y, drawTargetPos.z).getIndex());
			}

			LineRenderQueue.addLine(lineVertexB(h), lineVertexC(h));
			projectA(h);
			projectC(h);

		}
		projectB(h);
	}

	// show A/B as 2D points, tether A to B
	private void drawHelpers6(Renderer renderer, boolean previewMode, float h)
	{
		if (previewMode && !flag) {
			Vector3f vBA = Vector3f.sub(posA.getPosition(), posB.getPosition());
			vBA.y = 0;

			if (vBA.length() > MathUtil.SMALL_NUMBER) {
				Vector3f unit = Vector3f.cross(new Vector3f(0.0f, 1.0f, 0.0f), vBA.normalize());

				Vector3f vBT = Vector3f.sub(drawTargetPos, posB.getPosition());
				vBT.y = 0;

				float len = Vector3f.dot(vBT, unit);

				LineRenderQueue.addLine(lineVertexB(h), //TODO stipple 00FF
					LineRenderQueue.addVertex().setColor(PresetColor.WHITE)
						.setPosition(posB.getX() + unit.x * len, h, posB.getZ() + unit.z * len).getIndex());

				LineRenderQueue.addLine(lineVertexA(h), //TODO stipple 00FF
					LineRenderQueue.addVertex().setColor(PresetColor.WHITE)
						.setPosition(posA.getX() + unit.x * len, h, posA.getZ() + unit.z * len).getIndex());
			}
		}

		LineRenderQueue.addLine(lineVertexA(h), lineVertexB(h));
		projectA(h);
		projectB(h);
	}

	private void projectA(float h)
	{
		RenderState.setColor(PresetColor.RED);
		project(posA, h);
	}

	private void projectB(float h)
	{
		RenderState.setColor(PresetColor.BLUE);
		project(posB, h);
	}

	private void projectC(float h)
	{
		RenderState.setColor(PresetColor.GREEN);
		project(posC, h);
	}

	private void project(SelectablePoint p, float h)
	{
		// draw projection of the selectable point onto the reference plane
		if (p.getY() != h) {
			// draw the projected point
			PointRenderQueue.addPoint(projectionPointSize)
				.setPosition(p.getX(), h, p.getZ());

			RenderState.setLineWidth(PROJECT_LINE_SIZE);
			Renderer.queueStipple(
				p.getX(), p.getY(), p.getZ(),
				p.getX(), h, p.getZ(),
				5.0f);
		}

		// draw the point
		PointRenderQueue.addPoint(helpPointSize)
			.setPosition(p.getX(), p.getY(), p.getZ());
	}

	private int lineVertexA(float h)
	{
		RenderState.setColor(PresetColor.RED);
		return LineRenderQueue.addVertex().setPosition(posA.getX(), h, posA.getZ()).getIndex();
	}

	private int lineVertexB(float h)
	{
		RenderState.setColor(PresetColor.BLUE);
		return LineRenderQueue.addVertex().setPosition(posB.getX(), h, posB.getZ()).getIndex();
	}

	private int lineVertexC(float h)
	{
		RenderState.setColor(PresetColor.GREEN);
		return LineRenderQueue.addVertex().setPosition(posC.getX(), h, posC.getZ()).getIndex();
	}

	private void pointA(float h)
	{
		PointRenderQueue.addPoint(helpPointSize)
			.setColor(PresetColor.RED)
			.setPosition(posA.getX(), h, posA.getZ());
	}

	private void pointB(float h)
	{
		PointRenderQueue.addPoint(helpPointSize)
			.setColor(PresetColor.BLUE)
			.setPosition(posB.getX(), h, posB.getZ());
	}

	private void pointC(float h)
	{
		PointRenderQueue.addPoint(helpPointSize)
			.setColor(PresetColor.GREEN)
			.setPosition(posC.getX(), h, posC.getZ());
	}

	/**
	 * @param controller
	 * @param controlType
	 * @return A list containing the set of points available to edit for a camera controller
	 * of a certain type/flag combination.
	 */
	private static List<SelectablePoint> getPointSet(CameraZoneData controller, ControlType controlType, boolean flag)
	{
		List<SelectablePoint> pointList = new ArrayList<>(3);

		switch (controlType) {
			case TYPE_0:
				// Uses A/B as 2D points
				pointList.add(controller.posA);
				pointList.add(controller.posB);
				break;

			case TYPE_1:
				// Uses A/B as 2D points
				pointList.add(controller.posA);

				if (flag)
					pointList.add(controller.posB);
				break;

			case TYPE_2:
				// Uses A/B/C as 2D points
				if (!flag) {
					pointList.add(controller.posA);
					pointList.add(controller.posB);
					pointList.add(controller.posC);
				}
				break;

			case TYPE_3:
				// Uses no control points
				break;

			case TYPE_4:
				// Uses A as a 2D point and B as a 3D point
				pointList.add(controller.posA);
				pointList.add(controller.posB);
				break;

			case TYPE_5:
				// Uses A/B/C as 2D points
				pointList.add(controller.posB);
				if (!flag) {
					pointList.add(controller.posA);
					pointList.add(controller.posC);
				}
				break;

			case TYPE_6:
				// Uses A/B as 2D points
				pointList.add(controller.posA);
				pointList.add(controller.posB);
				break;

			default:
				throw new IllegalArgumentException("Control type " + controlType + " is invalid.");
		}

		return pointList;
	}

	public ControlType getType()
	{ return type; }

	private void setType(ControlType value)
	{
		type = value;

		for (SelectablePoint p : points)
			p.hidden = true;

		List<SelectablePoint> newPoints = getPointSet(this, type, flag);

		for (SelectablePoint p : newPoints)
			p.hidden = false;

		notifyListeners();
	}

	public boolean getFlag()
	{ return flag; }

	private void setFlag(boolean value)
	{
		flag = value;
		notifyListeners();
	}

	public static final class SetCameraType extends AbstractCommand
	{
		private final CameraZoneData cam;
		private final ControlType oldType;
		private final ControlType newType;

		private final AbstractCommand selectionModCommand;

		public SetCameraType(CameraZoneData controller, ControlType type)
		{
			super("Set Camera Type");
			this.cam = controller;
			oldType = controller.type;
			newType = type;

			List<SelectablePoint> newPoints = getPointSet(controller, type, controller.flag);
			List<SelectablePoint> deselectList = new LinkedList<>();

			for (SelectablePoint p : controller.points) {
				if (p.isSelected() && !newPoints.contains(p))
					deselectList.add(p);
			}

			selectionModCommand = editor.selectionManager.getModifyPoints(null, deselectList);
		}

		@Override
		public boolean shouldExec()
		{
			return oldType != newType;
		}

		@Override
		public void exec()
		{
			selectionModCommand.exec();

			super.exec();
			cam.setType(newType);
		}

		@Override
		public void undo()
		{
			super.undo();
			cam.setType(oldType);

			selectionModCommand.undo();
		}
	}

	public static class SetCameraPos extends AbstractCommand
	{
		private CameraZoneData controller;
		private final int oldValue;
		private final int newValue;

		private final int axis;
		private final SelectablePoint point;

		public SetCameraPos(CameraZoneData controller, char letter, int axis, float val)
		{
			this(controller, letter, axis, (int) val);
		}

		public SetCameraPos(CameraZoneData controller, char letter, int axis, int val)
		{
			super("Set Camera Position");

			this.controller = controller;

			switch (letter) {
				case 'A':
					point = controller.posA;
					break;
				case 'B':
					point = controller.posB;
					break;
				case 'C':
					point = controller.posC;
					break;
				default:
					throw new IllegalStateException("Unknown camera controller point in SetCameraPos: " + letter);
			}

			this.axis = axis;

			switch (axis) {
				case 0:
					oldValue = point.getX();
					break;
				case 1:
					oldValue = point.getY();
					break;
				case 2:
					oldValue = point.getZ();
					break;
				default:
					throw new IllegalStateException("Invalid axis value " + axis);
			}

			newValue = val;
		}

		@Override
		public boolean shouldExec()
		{
			return newValue != oldValue;
		}

		@Override
		public void exec()
		{
			super.exec();

			switch (axis) {
				case 0:
					point.setX(newValue);
					break;
				case 1:
					point.setY(newValue);
					break;
				case 2:
					point.setZ(newValue);
					break;
			}

			controller.notifyListeners();
		}

		@Override
		public void undo()
		{
			super.undo();

			switch (axis) {
				case 0:
					point.setX(oldValue);
					break;
				case 1:
					point.setY(oldValue);
					break;
				case 2:
					point.setZ(oldValue);
					break;
			}

			controller.notifyListeners();
		}
	}

	public static final class SetCameraFlag extends AbstractCommand
	{
		private final CameraZoneData cam;
		private final boolean oldValue;
		private final boolean newValue;

		private final AbstractCommand selectionModCommand;

		public SetCameraFlag(CameraZoneData controller, boolean val)
		{
			super("Set Camera Constraint Flag");
			this.cam = controller;
			oldValue = controller.flag;
			newValue = val;

			List<SelectablePoint> newPoints = getPointSet(controller, controller.type, newValue);
			List<SelectablePoint> deselectList = new LinkedList<>();

			for (SelectablePoint p : controller.points) {
				if (p.isSelected() && !newPoints.contains(p))
					deselectList.add(p);
			}

			selectionModCommand = editor.selectionManager.getModifyPoints(null, deselectList);
		}

		@Override
		public boolean shouldExec()
		{
			return newValue != oldValue;
		}

		@Override
		public void exec()
		{
			selectionModCommand.exec();

			super.exec();

			cam.setFlag(newValue);
			cam.setType(cam.type);
		}

		@Override
		public void undo()
		{
			super.undo();

			cam.setFlag(oldValue);
			cam.setType(cam.type);

			selectionModCommand.undo();
		}
	}
}
