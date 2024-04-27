package game.map.editor.common;

import static org.lwjgl.opengl.GL11.*;

import java.awt.event.KeyEvent;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;

import game.map.editor.geometry.Vector3f;
import game.map.shape.TransformMatrix;
import renderer.GLUtils;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicTexturedShader;
import util.MathUtil;

public final class BasicCamera extends BaseCamera
{
	public final float defaultPosX;
	public final float defaultPosY;
	public final float panSpeedScale;

	public final float defaultZoom;
	public final float minZoom;
	public final float maxZoom;

	private float zoom;

	private final boolean isOrtho;
	private final boolean useRelativeZoom;

	float minW, minH;
	float maxW, maxH;

	public BasicCamera(
		float defaultX, float defaultY, float panSpeed,
		float defaultZoom, float minZoom, float maxZoom,
		boolean isOrtho, boolean useRelativeZoom)
	{
		this.panSpeedScale = panSpeed;
		this.isOrtho = isOrtho;
		this.useRelativeZoom = useRelativeZoom;

		defaultPosX = defaultX;
		pos.x = defaultX;

		defaultPosY = defaultY;
		pos.y = defaultY;

		this.defaultZoom = defaultZoom;
		this.minZoom = minZoom;
		this.maxZoom = maxZoom;
		zoom = defaultZoom;

		minW = -Float.MAX_VALUE;
		minH = -Float.MAX_VALUE;

		maxW = Float.MAX_VALUE;
		maxH = Float.MAX_VALUE;
	}

	public void handleInput(MouseInput mouse, KeyboardInput keyboard, double deltaTime, float canvasW, float canvasH)
	{
		float zdh = 0;
		float zdv = 0;

		if (isOrtho && useRelativeZoom) {
			zdh = (mouse.getPosX() - (canvasW / 2)) / canvasW;
			zdv = (mouse.getPosY() - (canvasH / 2)) / canvasH;
		}

		// zooming input
		float sdw = Math.signum(mouse.getFrameDW());
		float zh = 0;
		float zv = 0;

		// zooming in
		if (sdw > 0) {
			zoom /= 1.10;
			if (zoom < minZoom) {
				zoom = minZoom;
			}
			else {
				zh += zdh * zoom * 100;
				zv -= zdv * zoom * 100;
			}
		}

		// zooming out
		if (sdw < 0) {
			zoom *= 1.10;
			if (zoom > maxZoom)
				zoom = maxZoom;
		}

		// panning
		double panSpeed = panSpeedScale * zoom * canvasW;

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
			dv += (float) (deltaTime * panSpeed * (pv / panMag));
			dh += (float) (deltaTime * panSpeed * (ph / panMag));
		}

		pos.x += dh;
		pos.y -= dv;
		pos.z = 400.0f * zoom;

		if (pos.x > maxW)
			pos.x = maxW;
		if (pos.x < minW)
			pos.x = minW;
		if (pos.y > maxH)
			pos.y = maxH;
		if (pos.y < minH)
			pos.y = minH;
	}

	public void setMinPos(int minW, int minH)
	{
		this.minW = minW;
		this.minH = minH;
	}

	public void setMaxPos(int maxW, int maxH)
	{
		this.maxW = maxW;
		this.maxH = maxH;
	}

	public void setPerspView()
	{
		projMatrix.perspective(60, glViewSizeX / glViewSizeY, 1, 0x2000);
		glLoadProjection();
	}

	public void setOrthoView()
	{
		float halfW = zoom * glViewSizeX / 2.0f;
		float halfH = zoom * glViewSizeY / 2.0f;
		projMatrix.ortho(-halfW, halfW, -halfH, halfH, 1, Float.MAX_VALUE);
		glLoadProjection();
	}

	public void reset()
	{
		pos.x = defaultPosX;
		pos.y = defaultPosY;
		zoom = defaultZoom;
	}

	public float getZoom()
	{
		return zoom;
	}

	public float getDist()
	{
		return 400.0f * zoom;
	}

	public float getPosX()
	{
		return pos.x;
	}

	public float getPosY()
	{
		return pos.y;
	}

	/**
	 * Returns a pick ray based on current mouse position.
	 * @return
	 */
	public BasicTraceRay getTraceRay(int mouseX, int mouseY)
	{
		MousePixelRead mousePixel = getMousePosition(mouseX, mouseY, false, false);
		Vector3f pickPoint = new Vector3f(mousePixel.worldPos);

		if (isOrtho)
			pickPoint.z = 0;

		Vector3f dir = Vector3f.sub(pickPoint, pos).normalize();

		return new BasicTraceRay(new Vector3f(pos), dir, mousePixel);
	}

	// Finds the 3D coordinate of the mouse position in this camera using gluUnProject.
	@Override
	public MousePixelRead getMousePosition(int mouseX, int mouseY, boolean useDepth, boolean useStencil)
	{
		IntBuffer viewport = BufferUtils.createIntBuffer(16);
		viewport.put(glViewMinX);
		viewport.put(glViewMinY);
		viewport.put(glViewSizeX);
		viewport.put(glViewSizeY);
		viewport.rewind();

		float winX = mouseX;
		float winY = mouseY;
		float winZ = 0;

		if (useDepth) {
			// this is the expensive part, reading z from the depth buffer
			FloatBuffer fb = BufferUtils.createFloatBuffer(1);
			glReadPixels(mouseX, mouseY, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, fb);
			winZ = fb.get();
		}

		FloatBuffer position = BufferUtils.createFloatBuffer(3);
		GLUtils.gluUnProject(winX, winY, winZ, viewMatrix.toFloatBuffer(), projMatrix.toFloatBuffer(), viewport, position);

		// read stencil buffer

		int stencilValue = 0;
		if (useStencil) {
			IntBuffer ib = BufferUtils.createIntBuffer(1);
			glReadPixels(mouseX, mouseY, 1, 1, GL_STENCIL_INDEX, GL_UNSIGNED_INT, ib);
			stencilValue = ib.get();
		}

		return new MousePixelRead(position.get(), position.get(), position.get(), stencilValue);
	}

	public static class BasicTraceRay
	{
		public final Vector3f origin;
		public final Vector3f direction;
		public final MousePixelRead pixelData;

		@Override
		public String toString()
		{
			return origin.toString() + " --> " + direction.toString();
		}

		public BasicTraceRay(Vector3f start, Vector3f direction, MousePixelRead pixelData)
		{
			this.origin = start;
			this.direction = direction;
			this.pixelData = pixelData;
		}

		public static boolean intersects(BasicTraceRay ray, Vector3f min, Vector3f max)
		{
			return !getIntersection(ray, min, max).missed();
		}

		public static BasicTraceHit getIntersection(BasicTraceRay ray, Vector3f min, Vector3f max)
		{
			Vector3f invDir = new Vector3f(1f / ray.direction.x, 1f / ray.direction.y, 1f / ray.direction.z);
			boolean signDirX = invDir.x < 0;
			boolean signDirY = invDir.y < 0;
			boolean signDirZ = invDir.z < 0;

			Vector3f bbox = signDirX ? max : min;
			float tmin = (bbox.x - ray.origin.x) * invDir.x;
			bbox = signDirX ? min : max;
			float tmax = (bbox.x - ray.origin.x) * invDir.x;
			bbox = signDirY ? max : min;
			float tymin = (bbox.y - ray.origin.y) * invDir.y;
			bbox = signDirY ? min : max;
			float tymax = (bbox.y - ray.origin.y) * invDir.y;

			if ((tmin > tymax) || (tymin > tmax))
				return new BasicTraceHit(ray, Float.MAX_VALUE);
			if (tymin > tmin)
				tmin = tymin;
			if (tymax < tmax)
				tmax = tymax;

			bbox = signDirZ ? max : min;
			float tzmin = (bbox.z - ray.origin.z) * invDir.z;
			bbox = signDirZ ? min : max;
			float tzmax = (bbox.z - ray.origin.z) * invDir.z;

			if ((tmin > tzmax) || (tzmin > tmax))
				return new BasicTraceHit(ray, Float.MAX_VALUE);
			if (tzmin > tmin)
				tmin = tzmin;
			if (tzmax < tmax)
				tmax = tzmax;

			return new BasicTraceHit(ray, tmin);
		}
	}

	public static class BasicTraceHit
	{
		private final float dist;
		public final Vector3f point;

		public BasicTraceHit(BasicTraceRay ray)
		{
			this(ray, Float.MAX_VALUE);
		}

		public BasicTraceHit(BasicTraceRay ray, float dist)
		{
			this.dist = dist;

			if (dist < Float.MAX_VALUE) {
				float hx = ray.origin.x + dist * ray.direction.x;
				float hy = ray.origin.y + dist * ray.direction.y;
				float hz = ray.origin.z + dist * ray.direction.z;
				point = new Vector3f(hx, hy, hz);
			}
			else
				point = null;
		}

		public boolean missed()
		{
			return dist == Float.MAX_VALUE;
		}
	}

	public void centerOn(
		int canvasW, int canvasH,
		float x, float y, float z,
		float sx, float sy, float sz)
	{
		if (!isOrtho)
			throw new IllegalStateException("Only ortho camera can center on.");

		pos.x = x;
		pos.y = y;

		float zoomX = (sx + 16) / canvasW;
		float zoomY = (sy + 16) / canvasH;

		// zoom out if necessary
		zoom = Math.max(Math.max(zoomX, zoomY), defaultZoom * 0.75f);
	}

	public void drawBackground(int canvasW, int canvasH, int bgTexID)
	{
		float scrollX = pos.x / 200;
		float scrollY = pos.y / 200;

		float scaleU = 4.0f;
		float scaleV = scaleU * canvasH / canvasW;

		TransformMatrix mtx = TransformMatrix.identity();
		mtx.ortho(0.0f, 1.0f, 0.0f, 1.0f, 1.0f, -1.0f);
		RenderState.setProjectionMatrix(mtx);
		RenderState.setViewMatrix(null);

		BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
		shader.texture.bind(bgTexID);

		shader.setXYQuadCoords(0, 0, 1, 1, -1);
		shader.setQuadTexCoords(scrollX, scrollY, scrollX + scaleU, scrollY + scaleV);

		RenderState.setPolygonMode(PolygonMode.FILL);
		RenderState.setDepthWrite(false);
		shader.renderQuad();
		RenderState.setDepthWrite(true);
	}
}
