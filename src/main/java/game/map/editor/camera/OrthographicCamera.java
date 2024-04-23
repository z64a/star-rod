package game.map.editor.camera;

import java.awt.event.KeyEvent;

import game.map.editor.geometry.Vector3f;

import game.map.Axis;
import game.map.BoundingBox;
import game.map.editor.common.KeyboardInput;
import game.map.editor.common.MouseInput;
import game.map.editor.common.MousePixelRead;
import game.map.editor.render.PresetColor;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.Channel;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;
import util.MathUtil;

public class OrthographicCamera extends MapEditCamera
{
	private static final float SPEED_SCALE = 0.6f;
	private static final float MINIMUM_ZOOM = 0.07f;
	private static final float MAXIMUM_ZOOM = 200.0f;
	private float zoomLevel = 1.00f;

	private final ViewType type;

	public OrthographicCamera(MapEditViewport view, ViewType type)
	{
		this(new Vector3f(0.0f, 0.0f, 0.0f), view, type);
	}

	public OrthographicCamera(Vector3f pos, MapEditViewport view, ViewType type)
	{
		super(view);
		this.type = type;
		reset();

		switch (type) {
			case TOP:
				pitch = 90.0f;
				yaw = 0.0f;
				break;
			case SIDE:
				pitch = 0.0f;
				yaw = -90.0f;
				break;
			case FRONT:
				pitch = 0.0f;
				yaw = 0.0f;
				break;
			default:
				break;
		}
	}

	public float getZoomLevel()
	{ return zoomLevel; }

	@Override
	public void resize()
	{
		recalculateProjectionMatrix();
	}

	@Override
	public void recalculateProjectionMatrix()
	{
		float halfW = (view.sizeX / 2) * zoomLevel;
		float halfH = (view.sizeY / 2) * zoomLevel;

		projMatrix.ortho(
			-halfW, halfW,
			-halfH, halfH,
			NEAR_CLIP, 0x20000);
	}

	@Override
	public void reset()
	{
		switch (type) {
			case TOP:
				setPosition(new Vector3f(0.0f, 0x10000, 0.0f));
				break;
			case SIDE:
				setPosition(new Vector3f(0x10000, 0.0f, 0.0f));
				break;
			case FRONT:
				setPosition(new Vector3f(0.0f, 0.0f, 0x10000));
				break;
			default:
				break;
		}
		zoomLevel = 1.00f;
		recalculateProjectionMatrix();
	}

	@Override
	public void setPosition(Vector3f newPos)
	{
		switch (type) {
			case TOP:
				super.setPosition(new Vector3f(newPos.x, 0x10000, newPos.z));
				break;
			case SIDE:
				super.setPosition(new Vector3f(0x10000, newPos.y, newPos.z));
				break;
			case FRONT:
				super.setPosition(new Vector3f(newPos.x, newPos.y, 0x10000));
				break;
			default:
				break;
		}
	}

	/*
	@Override
	public void setRotation(Vector3f pos)
	{}
	 */

	@Override
	public void centerOn(BoundingBox aabb)
	{
		Vector3f min = aabb.getMin();
		Vector3f max = aabb.getMax();
		float sx = (float) Math.max(max.x - min.x, 100.0);
		float sy = (float) Math.max(max.y - min.y, 100.0);
		float sz = (float) Math.max(max.z - min.z, 100.0);

		Vector3f center = aabb.getCenter();

		int vx = view.maxX - view.minX;
		int vy = view.maxY - view.minY;

		// calculate zoom level
		float zoomX = 100.0f;
		float zoomY = 100.0f;
		switch (type) {
			case TOP:
				setPosition(new Vector3f(center.x, 0x10000, center.z));
				setRotation(new Vector3f(90.0f, 0.0f, 0.0f));
				zoomX = (sx * 1.2f) / vx;
				zoomY = (sz * 1.2f) / vy;
				break;
			case SIDE:
				setPosition(new Vector3f(0x10000, center.y, center.z));
				setRotation(new Vector3f(0.0f, -90.0f, 0.0f));
				zoomX = (sy * 1.2f) / vy;
				zoomY = (sz * 1.2f) / vx;
				break;
			case FRONT:
				setPosition(new Vector3f(center.x, center.y, 0x10000));
				setRotation(new Vector3f(0.0f, 0.0f, 0.0f));
				zoomX = (sx * 1.2f) / vx;
				zoomY = (sy * 1.2f) / vy;
				break;
			default:
				break;
		}

		if (zoomX >= zoomY)
			setZoom(zoomX);
		else
			setZoom(zoomY);
	}

	private void setZoom(float newLevel)
	{
		if (newLevel < MINIMUM_ZOOM)
			zoomLevel = MINIMUM_ZOOM;
		else if (newLevel > MAXIMUM_ZOOM)
			zoomLevel = MAXIMUM_ZOOM;
		else
			zoomLevel = newLevel;

		recalculateProjectionMatrix();
	}

	@Override
	public void handleMovementInput(MouseInput mouse, KeyboardInput keyboard, float deltaTime)
	{
		// zooming input
		float zdh = (mouse.getPosX() - (view.minX + view.sizeX / 2)) / (float) view.sizeX;
		float zdv = (mouse.getPosY() - (view.minY + view.sizeY / 2)) / (float) view.sizeY;

		float sdw = Math.signum(mouse.getFrameDW());
		float zh = 0;
		float zv = 0;

		// zooming in
		if (sdw > 0) {
			zoomLevel /= 1.10;
			if (zoomLevel < MINIMUM_ZOOM) {
				zoomLevel = MINIMUM_ZOOM;
			}
			else {
				zh += zdh * zoomLevel * 100;
				zv -= zdv * zoomLevel * 100;
			}
		}

		// zooming out
		if (sdw < 0) {
			zoomLevel *= 1.10;
			if (zoomLevel > MAXIMUM_ZOOM)
				zoomLevel = MAXIMUM_ZOOM;
		}

		if (sdw != 0)
			recalculateProjectionMatrix();

		// panning
		double panSpeed = SPEED_SCALE * zoomLevel * (view.maxX - view.minX);
		double maxSpeed = SPEED_SCALE * MAXIMUM_ZOOM * (view.maxX - view.minX);
		panSpeed = maxSpeed * Math.atan2(panSpeed, maxSpeed); // decrease slightly at max

		int pv = 0;
		int ph = 0;
		if (keyboard.isKeyDown(KeyEvent.VK_W))
			pv -= 1;
		if (keyboard.isKeyDown(KeyEvent.VK_S))
			pv += 1;
		if (keyboard.isKeyDown(KeyEvent.VK_A))
			ph -= 1;
		if (keyboard.isKeyDown(KeyEvent.VK_D))
			ph += 1;

		float dv = zv;
		float dh = zh;

		double panMag = Math.sqrt(pv * pv + ph * ph);
		if (!MathUtil.nearlyZero(panMag)) {
			dv = zv + (float) (deltaTime * panSpeed * (pv / panMag));
			dh = zh + (float) (deltaTime * panSpeed * (ph / panMag));
		}

		// handle different directions
		switch (type) {
			case TOP:
				pos.x += dh;
				if (pos.x > Short.MAX_VALUE)
					pos.x = Short.MAX_VALUE;
				if (pos.x < Short.MIN_VALUE)
					pos.x = Short.MIN_VALUE;
				pos.z += dv;
				if (pos.z > Short.MAX_VALUE)
					pos.z = Short.MAX_VALUE;
				if (pos.z < Short.MIN_VALUE)
					pos.z = Short.MIN_VALUE;
				break;
			case SIDE:
				pos.z -= dh;
				if (pos.z > Short.MAX_VALUE)
					pos.z = Short.MAX_VALUE;
				if (pos.z < Short.MIN_VALUE)
					pos.z = Short.MIN_VALUE;
				pos.y -= dv;
				if (pos.y > Short.MAX_VALUE)
					pos.y = Short.MAX_VALUE;
				if (pos.y < Short.MIN_VALUE)
					pos.y = Short.MIN_VALUE;
				break;
			case FRONT:
				pos.x += dh;
				if (pos.x > Short.MAX_VALUE)
					pos.x = Short.MAX_VALUE;
				if (pos.x < Short.MIN_VALUE)
					pos.x = Short.MIN_VALUE;
				pos.y -= dv;
				if (pos.y > Short.MAX_VALUE)
					pos.y = Short.MAX_VALUE;
				if (pos.y < Short.MIN_VALUE)
					pos.y = Short.MIN_VALUE;
				break;
			default:
				break;
		}
	}

	/**
	 * Draw the grid in the background.
	 */
	@Override
	public void drawBackground()
	{
		int hmin = 0, hmax = 0;
		int vmin = 0, vmax = 0;

		// get extents of screen in world space
		switch (type) {
			case TOP:
				hmin = (int) (pos.x - (view.sizeX / 2) * zoomLevel);
				hmax = (int) (pos.x + (view.sizeX / 2) * zoomLevel);
				vmin = (int) (pos.z - (view.sizeY / 2) * zoomLevel);
				vmax = (int) (pos.z + (view.sizeY / 2) * zoomLevel);
				break;

			case SIDE:
				hmin = (int) (pos.z - (view.sizeX / 2) * zoomLevel);
				hmax = (int) (pos.z + (view.sizeX / 2) * zoomLevel);
				vmin = (int) (pos.y - (view.sizeY / 2) * zoomLevel);
				vmax = (int) (pos.y + (view.sizeY / 2) * zoomLevel);
				break;

			case FRONT:
				hmin = (int) (pos.x - (view.sizeX / 2) * zoomLevel);
				hmax = (int) (pos.x + (view.sizeX / 2) * zoomLevel);
				vmin = (int) (pos.y - (view.sizeY / 2) * zoomLevel);
				vmax = (int) (pos.y + (view.sizeY / 2) * zoomLevel);
				break;

			default:
				break;
		}

		int spacing = editor.grid.getSpacing(hmax - hmin);

		if (hmax > Short.MAX_VALUE)
			hmax = Short.MAX_VALUE;
		if (hmin < Short.MIN_VALUE)
			hmin = Short.MIN_VALUE;
		if (vmax > Short.MAX_VALUE)
			vmax = Short.MAX_VALUE;
		if (vmin < Short.MIN_VALUE)
			vmin = Short.MIN_VALUE;

		// truncate to grid intervals
		hmax = spacing * (int) Math.floor((double) hmax / spacing);
		hmin = spacing * (int) Math.ceil((double) hmin / spacing);
		vmax = spacing * (int) Math.floor((double) vmax / spacing);
		vmin = spacing * (int) Math.ceil((double) vmin / spacing);

		RenderState.setLineWidth(1.0f);
		RenderState.setColor(0.15f, 0.15f, 0.15f, 1.0f);

		// draw vertical lines
		for (int i = hmin; i <= hmax; i += spacing) {
			if (i == 0 && editor.showAxes)
				continue;

			switch (type) {
				case TOP:
					LineRenderQueue.addLine(
						LineRenderQueue.addVertex().setPosition(i, 0.0f, Short.MIN_VALUE).getIndex(),
						LineRenderQueue.addVertex().setPosition(i, 0.0f, Short.MAX_VALUE).getIndex());
					break;

				case SIDE:
					LineRenderQueue.addLine(
						LineRenderQueue.addVertex().setPosition(0.0f, Short.MIN_VALUE, i).getIndex(),
						LineRenderQueue.addVertex().setPosition(0.0f, Short.MAX_VALUE, i).getIndex());
					break;

				case FRONT:
					LineRenderQueue.addLine(
						LineRenderQueue.addVertex().setPosition(i, Short.MIN_VALUE, 0.0f).getIndex(),
						LineRenderQueue.addVertex().setPosition(i, Short.MAX_VALUE, 0.0f).getIndex());
					break;

				default:
					break;
			}
		}

		// draw horizontal lines
		for (int i = vmin; i <= vmax; i += spacing) {
			if (i == 0 && editor.showAxes)
				continue;

			switch (type) {
				case TOP:
					LineRenderQueue.addLine(
						LineRenderQueue.addVertex().setPosition(Short.MIN_VALUE, 0.0f, i).getIndex(),
						LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, 0.0f, i).getIndex());
					break;

				case SIDE:
					LineRenderQueue.addLine(
						LineRenderQueue.addVertex().setPosition(0.0f, i, Short.MIN_VALUE).getIndex(),
						LineRenderQueue.addVertex().setPosition(0.0f, i, Short.MAX_VALUE).getIndex());
					break;

				case FRONT:
					LineRenderQueue.addLine(
						LineRenderQueue.addVertex().setPosition(Short.MIN_VALUE, i, 0.0f).getIndex(),
						LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, i, 0.0f).getIndex());
					break;

				default:
					break;
			}
		}

		if (editor.showAxes) {
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(Short.MIN_VALUE, 0, 0).setColor(PresetColor.RED).getIndex(),
				LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, 0, 0).setColor(PresetColor.RED).getIndex());

			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(0, Short.MIN_VALUE, 0).setColor(PresetColor.GREEN).getIndex(),
				LineRenderQueue.addVertex().setPosition(0, Short.MAX_VALUE, 0).setColor(PresetColor.GREEN).getIndex());

			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(0, 0, Short.MIN_VALUE).setColor(PresetColor.BLUE).getIndex(),
				LineRenderQueue.addVertex().setPosition(0, 0, Short.MAX_VALUE).setColor(PresetColor.BLUE).getIndex());
		}

		RenderState.setLineWidth(2.0f);
		RenderState.setColor(0.35f, 0.35f, 0.35f, 1.0f);
		int v1, v2, v3, v4;

		// draw coordinate boundaries
		switch (type) {
			case TOP:
				v1 = LineRenderQueue.addVertex().setPosition(Short.MIN_VALUE, 0.0f, Short.MAX_VALUE).getIndex();
				v2 = LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, 0.0f, Short.MAX_VALUE).getIndex();
				v3 = LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, 0.0f, Short.MIN_VALUE).getIndex();
				v4 = LineRenderQueue.addVertex().setPosition(Short.MIN_VALUE, 0.0f, Short.MIN_VALUE).getIndex();
				LineRenderQueue.addLine(v1, v2, v3, v4, v1);
				break;

			case SIDE:
				v1 = LineRenderQueue.addVertex().setPosition(0.0f, Short.MIN_VALUE, Short.MAX_VALUE).getIndex();
				v2 = LineRenderQueue.addVertex().setPosition(0.0f, Short.MAX_VALUE, Short.MAX_VALUE).getIndex();
				v3 = LineRenderQueue.addVertex().setPosition(0.0f, Short.MAX_VALUE, Short.MIN_VALUE).getIndex();
				v4 = LineRenderQueue.addVertex().setPosition(0.0f, Short.MIN_VALUE, Short.MIN_VALUE).getIndex();
				LineRenderQueue.addLine(v1, v2, v3, v4, v1);
				break;

			case FRONT:
				v1 = LineRenderQueue.addVertex().setPosition(Short.MIN_VALUE, Short.MAX_VALUE, 0.0f).getIndex();
				v2 = LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, Short.MAX_VALUE, 0.0f).getIndex();
				v3 = LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, Short.MIN_VALUE, 0.0f).getIndex();
				v4 = LineRenderQueue.addVertex().setPosition(Short.MIN_VALUE, Short.MIN_VALUE, 0.0f).getIndex();
				LineRenderQueue.addLine(v1, v2, v3, v4, v1);
				break;

			default:
				break;
		}

		RenderState.setDepthWrite(false);
		LineRenderQueue.render(true);
		RenderState.setDepthWrite(true);

	}

	@Override
	public PickRay getPickRay(int mouseX, int mouseY)
	{
		MousePixelRead pixelRead = getMousePosition(mouseX, mouseY, false, false);
		Vector3f clickPosition = pixelRead.worldPos;

		Vector3f origin = null;
		Vector3f direction = null;

		switch (type) {
			case TOP:
				origin = new Vector3f(clickPosition.x, pos.y, clickPosition.z);
				direction = new Vector3f(0.0f, -1.0f, 0.0f);
				break;
			case SIDE:
				origin = new Vector3f(pos.x, clickPosition.y, clickPosition.z);
				direction = new Vector3f(-1.0f, 0.0f, 0.0f);
				break;
			case FRONT:
				origin = new Vector3f(clickPosition.x, clickPosition.y, pos.z);
				direction = new Vector3f(0.0f, 0.0f, -1.0f);
				break;
			default:
				break;
		}

		return new PickRay(Channel.SELECTION, origin, direction, view);
	}

	@Override
	public Vector3f getForward(float length)
	{
		Vector3f vec = null;

		switch (type) {
			case FRONT:
				vec = new Vector3f(0, 0, -length);
				break;
			case SIDE:
				vec = new Vector3f(length, 0, 0);
				break;
			case TOP:
				vec = new Vector3f(0, length, 0);
				break;
			default:
				break;
		}

		return vec;
	}

	@Override
	public Vector3f getUp(float length)
	{
		Vector3f vec = null;

		switch (type) {
			case FRONT:
				vec = new Vector3f(0, length, 0);
				break;
			case SIDE:
				vec = new Vector3f(0, length, 0);
				break;
			case TOP:
				vec = new Vector3f(0, 0, -length);
				break;
			default:
				break;
		}

		return vec;
	}

	@Override
	public Vector3f getRight(float length)
	{
		Vector3f vec = null;

		switch (type) {
			case FRONT:
				vec = new Vector3f(length, 0, 0);
				break;
			case SIDE:
				vec = new Vector3f(0, 0, -length);
				break;
			case TOP:
				vec = new Vector3f(length, 0, 0);
				break;
			default:
				break;
		}

		return vec;
	}

	@Override
	public Vector3f getTranslationVector(int dx, int dy)
	{
		Vector3f vec = null;

		switch (type) {
			case FRONT:
				vec = new Vector3f(zoomLevel * dx, zoomLevel * dy, 0);
				break;
			case SIDE:
				vec = new Vector3f(0, zoomLevel * dy, -zoomLevel * dx);
				break;
			case TOP:
				vec = new Vector3f(zoomLevel * dx, 0, -zoomLevel * dy);
				break;
			default:
				break;
		}

		return vec;
	}

	@Override
	public Axis getRotationAxis()
	{
		switch (type) {
			case FRONT:
				return Axis.Z;
			case SIDE:
				return Axis.X;
			case TOP:
				return Axis.Y;
			default:
				return Axis.X;
		}
	}
}
