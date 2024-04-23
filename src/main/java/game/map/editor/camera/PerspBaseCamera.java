package game.map.editor.camera;

import game.map.editor.geometry.Vector3f;

import game.map.Axis;
import game.map.editor.render.TextureManager;
import game.map.scripts.ScriptData;
import game.map.shape.TransformMatrix;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicTexturedShader;

public abstract class PerspBaseCamera extends MapEditCamera
{
	private float vfov;
	private float hfov;
	private float aspectRatio;
	public static final float DEFAULT_HFOV = 105;
	public static final float DEFAULT_VFOV = 70;

	public PerspBaseCamera(MapEditViewport view)
	{
		super(view);
		hfov = DEFAULT_HFOV;
		vfov = DEFAULT_VFOV;
	}

	@Override
	public final void release()
	{
		editor.mouse.setGrabbed(false);
	}

	@Override
	public final void resize()
	{
		aspectRatio = (float) view.sizeX / view.sizeY;
		vfov = (float) Math.toDegrees((2 * Math.atan(Math.tan(Math.toRadians(hfov / 2)) / aspectRatio)));

		recalculateProjectionMatrix();
	}

	@Override
	public final void glSetViewport(int minX, int minY, int sizeX, int sizeY)
	{
		if (!editor.usingInGameAspectRatio()) {
			super.glSetViewport(minX, minY, sizeX, sizeY);
			return;
		}

		float goalRatio = 320.0f / 240.0f;
		float viewRatio = (float) sizeX / sizeY;

		// sizeX = sizeXX + padX
		// sizeX = sizeXX + padX
		// such that sizeXX and sizeYY are the largest rectangle with aspect ratio = goalRatio
		int sizeXX = 0;
		int sizeYY = 0;
		int padX = 0;
		int padY = 0;

		if (viewRatio > goalRatio) // too wide
		{
			sizeYY = sizeY;
			sizeXX = Math.round(goalRatio * sizeY);
			padX = (sizeX - sizeXX) / 2;
			assert (padX >= 0);
		}
		else if (viewRatio < goalRatio) // too tall
		{
			sizeXX = sizeX;
			sizeYY = Math.round(sizeX / goalRatio);
			padY = (sizeY - sizeYY) / 2;
			assert (padY >= 0);
		}
		else // exactly right
		{
			sizeYY = sizeY;
			sizeXX = sizeX;
		}

		int sx = padX + (int) Math.round((12.0 / 320.0) * sizeXX);
		int ssx = (int) Math.round((296.0 / 320.0) * sizeXX);

		int sy = padY + (int) Math.round((20.0 / 240.0) * sizeYY);
		int ssy = (int) Math.round((200.0 / 240.0) * sizeYY);

		RenderState.setViewport(minX + sx, minY + sy, ssx, ssy);
		glViewMinX = minX + sx;
		glViewMinY = minY + sy;
		glViewSizeX = ssx;
		glViewSizeY = ssy;
		aspectRatio = (float) ssx / ssy;
	}

	@Override
	public final void recalculateProjectionMatrix()
	{
		float currentAspectRatio = aspectRatio;
		float currentVfov = vfov;
		float currentZnear = NEAR_CLIP;
		float currentZfar = FAR_CLIP;

		if (editor.usingInGameCameraProperties()) {
			ScriptData scriptData = editor.map.scripts;
			currentAspectRatio = 298.0f / 200.0f;
			currentVfov = scriptData.camVfov.get();
			currentZnear = scriptData.camNearClip.get();
			currentZfar = scriptData.camFarClip.get();
		}

		projMatrix.perspective(currentVfov, currentAspectRatio, currentZnear, currentZfar);
	}

	@Override
	public final Vector3f getForward(float length)
	{
		float sinP = (float) Math.sin(Math.toRadians(pitch));
		float cosP = (float) Math.cos(Math.toRadians(pitch));
		float sinY = (float) Math.sin(Math.toRadians(yaw));
		float cosY = (float) Math.cos(Math.toRadians(yaw));

		return new Vector3f(length * cosP * sinY, length * -sinP, length * -cosP * cosY);
	}

	@Override
	public final Vector3f getUp(float length)
	{
		float sinP = -(float) Math.sin(Math.toRadians(pitch));
		float cosP = (float) Math.cos(Math.toRadians(pitch));
		float sinY = (float) Math.sin(Math.toRadians(yaw));
		float cosY = (float) Math.cos(Math.toRadians(yaw));

		return new Vector3f(length * sinP * sinY, length * cosP, length * -sinP * cosY);
	}

	@Override
	public final Vector3f getRight(float length)
	{
		float sinY = (float) Math.sin(Math.toRadians(yaw));
		float cosY = (float) Math.cos(Math.toRadians(yaw));

		return new Vector3f(length * cosY, 0, length * sinY);
	}

	@Override
	public final Vector3f getTranslationVector(int dx, int dy)
	{
		float sinP = (float) Math.sin(Math.toRadians(pitch));
		float cosP = (float) Math.cos(Math.toRadians(pitch));
		float sinY = (float) Math.sin(Math.toRadians(yaw));
		float cosY = (float) Math.cos(Math.toRadians(yaw));

		//  UP*dy + RIGHT*dx
		return new Vector3f(
			dx * cosY + sinY * sinP * dy,
			dy * cosP,
			dx * sinY - cosY * sinP * dy);
	}

	@Override
	public Axis getRotationAxis()
	{
		// rotations in perspective view are disabled
		// if ever implemented, perhaps this should be the look vector
		return null;
	}

	@Override
	public void tick(double deltaTime)
	{
		recalculateProjectionMatrix();
	}

	// parallax mode takes into account yaw and fov
	@Override
	public final void drawBackground()
	{
		int bgTexID = (editor.map.hasBackground) ? editor.map.glBackgroundTexID : TextureManager.glNoBackgoundTexID;

		float left = yaw / 360;
		float right = (yaw + hfov + 360) / 360;

		TransformMatrix mtx = TransformMatrix.identity();
		mtx.ortho(0.0f, 1.0f, 0.0f, 1.0f, 1.0f, -1.0f);
		RenderState.setProjectionMatrix(mtx);
		RenderState.setViewMatrix(null);

		BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
		shader.texture.bind(bgTexID);

		shader.setXYQuadCoords(0, 0, 1, 1, -1);
		shader.setQuadTexCoords(left, 0, right, 1);

		RenderState.setPolygonMode(PolygonMode.FILL);
		RenderState.setDepthWrite(false);
		shader.renderQuad();
		RenderState.setDepthWrite(true);
	}
}
