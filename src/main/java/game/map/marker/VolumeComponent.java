package game.map.marker;

import static game.map.MapKey.*;

import java.util.List;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.selection.SelectablePoint;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.marker.Marker.MarkerType;
import game.map.shape.TransformMatrix;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.LineShader;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class VolumeComponent extends BaseMarkerComponent
{
	private final Consumer<Object> notifyCallback = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_GeneralTab);
	};

	public EditableField<Float> radius = EditableFieldFactory.create(0.0f)
		.setCallback(notifyCallback).setName("Set Radius").build();

	public EditableField<Float> height = EditableFieldFactory.create(0.0f)
		.setCallback(notifyCallback).setName("Set Height").build();

	public SelectablePoint minPos;
	public SelectablePoint maxPos;

	public VolumeComponent(Marker parent)
	{
		super(parent);

		MutablePoint minPoint = new MutablePoint(
			parent.position.getX(),
			parent.position.getY(),
			parent.position.getZ());

		MutablePoint maxPoint = new MutablePoint(
			parent.position.getX(),
			parent.position.getY(),
			parent.position.getZ());

		minPos = new SelectablePoint(minPoint, 2.0f);
		maxPos = new SelectablePoint(maxPoint, 2.0f);
	}

	@Override
	public VolumeComponent deepCopy(Marker copyParent)
	{
		VolumeComponent copy = new VolumeComponent(copyParent);
		copy.radius.copy(radius);
		copy.height.copy(height);
		copy.minPos.point.setPosition(minPos.point);
		copy.maxPos.point.setPosition(maxPos.point);
		return copy;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag compTag = xmw.createTag(TAG_VOLUME, true);

		xmw.addFloat(compTag, ATTR_VOLUME_RADIUS, radius.get());
		xmw.addFloat(compTag, ATTR_VOLUME_HEIGHT, height.get());

		xmw.addFloatArray(compTag, ATTR_VOLUME_MIN, minPos.point.getX(), minPos.point.getY(), minPos.point.getZ());
		xmw.addFloatArray(compTag, ATTR_VOLUME_MAX, maxPos.point.getX(), maxPos.point.getY(), maxPos.point.getZ());

		xmw.printTag(compTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element markerElem)
	{
		Element volumeElem = xmr.getUniqueRequiredTag(markerElem, TAG_VOLUME);
		if (xmr.hasAttribute(volumeElem, ATTR_VOLUME_RADIUS))
			radius.set(xmr.readFloat(volumeElem, ATTR_VOLUME_RADIUS));

		if (xmr.hasAttribute(volumeElem, ATTR_VOLUME_HEIGHT))
			height.set(xmr.readFloat(volumeElem, ATTR_VOLUME_HEIGHT));

		if (xmr.hasAttribute(volumeElem, ATTR_VOLUME_MIN)) {
			float[] min = xmr.readFloatArray(volumeElem, ATTR_VOLUME_MIN, 3);
			minPos.point.setPosition(min[0], min[1], min[2]);
		}

		if (xmr.hasAttribute(volumeElem, ATTR_VOLUME_MAX)) {
			float[] max = xmr.readFloatArray(volumeElem, ATTR_VOLUME_MAX, 3);
			maxPos.point.setPosition(max[0], max[1], max[2]);
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
		if (parentMarker.type == MarkerType.Volume) {
			points.add(minPos);
			points.add(maxPos);
		}
	}

	@Override
	public void addToBackup(IdentityHashSet<PointBackup> backupList)
	{
		if (parentMarker.type == MarkerType.Volume) {
			backupList.add(minPos.point.getBackup());
			backupList.add(maxPos.point.getBackup());
		}
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		if (parentMarker.type == MarkerType.Volume) {
			positions.add(minPos.point);
			positions.add(maxPos.point);
		}
	}

	@Override
	public void startTransformation()
	{
		if (parentMarker.type == MarkerType.Volume) {
			minPos.point.startTransform();
			maxPos.point.startTransform();
		}
	}

	@Override
	public void endTransformation()
	{
		if (parentMarker.type == MarkerType.Volume) {
			minPos.point.endTransform();
			maxPos.point.endTransform();
		}
	}

	@Override
	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		super.render(opts, view, renderer);

		int R = (int) (float) radius.get();
		int H = (int) (float) height.get();

		float r = 0.0f;
		float g = 0.0f;
		float b = 0.0f;
		float a = 1.0f;

		if (parentMarker.selected)
			r = 1.0f;
		else
			g = 1.0f;
		RenderState.setLineWidth(2.0f);

		TransformMatrix mtx = TransformMatrix.identity();
		MutablePoint pos = parentMarker.position;

		switch (parentMarker.type) {
			case Sphere:
				if (R < 0)
					return;

				LineShader shader = ShaderManager.use(LineShader.class);
				shader.useVertexColor.set(false);
				shader.color.set(r, g, b, a);
				mtx.scale(R);
				mtx.translate(pos.getX(), pos.getY(), pos.getZ());
				Renderer.instance().renderLineSphere36(mtx);
				break;

			case Cylinder:
				if (R < 0)
					return;

				Renderer.instance().drawCircularVolume(
					pos.getX(), pos.getY(), pos.getZ(), R, H,
					r, g, b, a);
				break;

			case Volume:
				boolean editPointsMode = true;

				Renderer.instance().drawRectangularVolume(
					minPos.point.getX(), minPos.point.getY(), minPos.point.getZ(),
					maxPos.point.getX(), maxPos.point.getY(), maxPos.point.getZ(),
					r, g, b, a);

				if (parentMarker.selected) {
					RenderState.setColor(r, g, b, a);
					RenderState.setPointSize(editPointsMode ? 12.0f : 8.0f);

					PointRenderQueue.addPoint().setPosition(minPos.point.getX(), minPos.point.getY(), minPos.point.getZ());
					PointRenderQueue.addPoint().setPosition(maxPos.point.getX(), maxPos.point.getY(), maxPos.point.getZ());
					PointRenderQueue.render(true);
				}
				break;
			default:
				break;

		}
	}
}
