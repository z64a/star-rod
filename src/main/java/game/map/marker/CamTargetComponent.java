package game.map.marker;

import static game.map.MapKey.*;

import java.util.List;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import game.map.Map;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.editor.MapEditor;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.commands.fields.EditableField.StandardBoolName;
import game.map.editor.geometry.Vector3f;
import game.map.editor.render.PresetColor;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.Channel;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.selection.SelectablePoint;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.hit.CameraZoneData;
import game.map.hit.Zone;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class CamTargetComponent extends BaseMarkerComponent
{
	private final Consumer<Object> notifyCallback = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_GeneralTab);
	};

	public EditableField<Boolean> useZone = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Camera Settings From Zone")).build();

	public EditableField<Boolean> overrideDist = EditableFieldFactory.create(true)
		.setCallback(notifyCallback).setName(new StandardBoolName("Override Boom Length")).build();

	public EditableField<Float> boomLength = EditableFieldFactory.create(450.0f)
		.setCallback(notifyCallback).setName("Set Camera Distance").build();

	public EditableField<Boolean> overrideAngles = EditableFieldFactory.create(true)
		.setCallback(notifyCallback).setName(new StandardBoolName("Override Boom Angles")).build();

	public EditableField<Float> boomPitch = EditableFieldFactory.create(15.0f)
		.setCallback(notifyCallback).setName("Set Boom Pitch").build();

	public EditableField<Float> viewPitch = EditableFieldFactory.create(-6.0f)
		.setCallback(notifyCallback).setName("Set View Pitch").build();

	public EditableField<Boolean> generatePan = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Generate Camera Pan")).build();

	public EditableField<Float> moveSpeed = EditableFieldFactory.create(1.0f)
		.setCallback(notifyCallback).setName("Move Speed").build();

	//	public SelectablePoint samplePoint = new SelectablePoint(2.0f);
	public CameraZoneData controlData;
	public CameraZoneData sampledControlData;
	public Vector3f sampleHitPos = null;
	public Zone sampleZone = null;

	public CamTargetComponent(Marker parent)
	{
		super(parent);

		controlData = new CameraZoneData(parent);
		sampledControlData = new CameraZoneData(parent);
	}

	public CameraZoneData getCurrentData()
	{
		if (useZone.get())
			return sampledControlData;
		else
			return controlData;
	}

	@Override
	public CamTargetComponent deepCopy(Marker copyParent)
	{
		CamTargetComponent copy = new CamTargetComponent(copyParent);

		copy.useZone.set(useZone.get());
		copy.overrideDist.set(overrideDist.get());
		copy.boomLength.set(boomLength.get());
		copy.overrideAngles.set(overrideAngles.get());
		copy.boomPitch.set(boomPitch.get());
		copy.viewPitch.set(viewPitch.get());
		copy.generatePan.set(generatePan.get());
		copy.moveSpeed.set(moveSpeed.get());

		return copy;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag compTag = xmw.createTag(TAG_CAM_TARGET, useZone.get());
		xmw.addBoolean(compTag, ATTR_CAM_USE_SAMPLE, useZone.get());
		xmw.addBoolean(compTag, ATTR_CAM_GENERATE_PAN, generatePan.get());
		xmw.addFloat(compTag, ATTR_CAM_PAN_SPEED, moveSpeed.get());

		if (useZone.get()) {
			if (overrideDist.get())
				xmw.addFloat(compTag, ATTR_CAM_OVERRIDE_LENGTH, boomLength.get());

			if (overrideAngles.get())
				xmw.addFloatArray(compTag, ATTR_CAM_OVERRIDE_ANGLES, boomPitch.get(), viewPitch.get());

			xmw.printTag(compTag);
		}
		else {
			xmw.openTag(compTag);
			controlData.toXML(xmw);
			xmw.closeTag(compTag);
		}
	}

	@Override
	public void fromXML(XmlReader xmr, Element markerElem)
	{
		Element compElem = xmr.getUniqueRequiredTag(markerElem, TAG_CAM_TARGET);

		if (!xmr.hasAttribute(compElem, ATTR_CAM_USE_SAMPLE))
			xmr.complain("Camera target data is mising attribute: " + ATTR_CAM_USE_SAMPLE);

		if (xmr.hasAttribute(compElem, ATTR_CAM_GENERATE_PAN))
			generatePan.set(xmr.readBoolean(compElem, ATTR_CAM_GENERATE_PAN));

		if (xmr.hasAttribute(compElem, ATTR_CAM_PAN_SPEED))
			moveSpeed.set(xmr.readFloat(compElem, ATTR_CAM_PAN_SPEED));

		useZone.set(xmr.readBoolean(compElem, ATTR_CAM_USE_SAMPLE));
		if (useZone.get()) {
			if (xmr.hasAttribute(compElem, ATTR_CAM_OVERRIDE_LENGTH))
				boomLength.set(xmr.readFloat(compElem, ATTR_CAM_OVERRIDE_LENGTH));

			if (xmr.hasAttribute(compElem, ATTR_CAM_OVERRIDE_ANGLES)) {
				float[] angles = xmr.readFloatArray(compElem, ATTR_CAM_OVERRIDE_ANGLES, 2);
				boomPitch.set(angles[0]);
				viewPitch.set(angles[1]);
			}
		}
		else {
			Element camElem = xmr.getUniqueRequiredTag(compElem, TAG_CAMERA);
			controlData.fromXML(xmr, camElem);
		}
	}

	@Override
	public void tick(double deltaTime)
	{
		Map collisionMap = MapEditor.instance().getCollisionMap();

		if (useZone.get()) {
			PickRay sampleRay = new PickRay(Channel.COLLISION, parentMarker.position.getVector(), PickRay.DOWN, false);
			PickHit sampleHit = Map.pickObjectFromSet(sampleRay, collisionMap.zoneTree, false);

			sampleHitPos = null;
			sampleZone = null;

			if (!sampleHit.missed()) {
				sampleHitPos = sampleHit.point;
				sampleZone = (Zone) sampleHit.obj;
			}

			if (sampleZone != null && sampleZone.hasCameraControlData()) {
				// re-generate this every frame
				sampledControlData.copy(sampleZone.getCameraControlData());
				if (overrideDist.get())
					sampledControlData.boomLength.copy(boomLength);
				if (overrideAngles.get()) {
					sampledControlData.boomPitch.copy(boomPitch);
					sampledControlData.viewPitch.copy(viewPitch);
				}
			}
		}
	}

	@Override
	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		super.render(opts, view, renderer);

		if (!parentMarker.selected)
			return;

		boolean editPointsMode = (MapEditor.instance().selectionManager.getSelectionMode() == SelectionMode.POINT);

		if (!useZone.get()) {
			MapEditor editor = MapEditor.instance();
			controlData.drawHelpers(renderer, editor.dummyCameraController, true);
		}
		else {
			RenderState.setColor(PresetColor.RED);
			Vector3f startPos = parentMarker.position.getVector();
			Vector3f endPos;

			RenderState.setPointSize(editPointsMode ? 12.0f : 8.0f);
			PointRenderQueue.addPoint().setPosition(startPos.x, startPos.y, startPos.z);

			RenderState.setPointSize(4.0f);

			if (sampleHitPos != null)
				PointRenderQueue.addPoint().setPosition(sampleHitPos.x, sampleHitPos.y, sampleHitPos.z);

			RenderState.setLineWidth(3.0f);

			if (sampleHitPos == null) {
				endPos = new Vector3f(startPos.x, startPos.y - 500.0f, startPos.z);
				Renderer.queueStipple(startPos, endPos, 10.0f);
			}
			else {
				endPos = new Vector3f(sampleHitPos.x, sampleHitPos.y, sampleHitPos.z);
				LineRenderQueue.addLine(
					LineRenderQueue.addVertex().setPosition(startPos.x, startPos.y, startPos.z).getIndex(),
					LineRenderQueue.addVertex().setPosition(endPos.x, endPos.y, endPos.z).getIndex());
			}

			LineRenderQueue.render(true);
			PointRenderQueue.render(true);
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
		if (!useZone.get())
			points.addAll(controlData.getPoints());
	}

	@Override
	public void startTransformation()
	{
		if (!useZone.get())
			for (SelectablePoint sp : controlData.getPoints())
				sp.point.startTransform();
	}

	@Override
	public void endTransformation()
	{
		if (!useZone.get())
			for (SelectablePoint sp : controlData.getPoints())
				sp.point.endTransform();
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		if (!useZone.get())
			for (SelectablePoint sp : controlData.getPoints())
				positions.add(sp.point);
	}

	@Override
	public void addToBackup(IdentityHashSet<PointBackup> backupList)
	{
		if (!useZone.get())
			for (SelectablePoint sp : controlData.getPoints())
				backupList.add(sp.point.getBackup());
	}
}
