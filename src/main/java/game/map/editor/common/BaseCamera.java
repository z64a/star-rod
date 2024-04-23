package game.map.editor.common;

import static org.lwjgl.opengl.GL11.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;

import game.map.Axis;
import game.map.editor.geometry.Vector3f;
import game.map.shape.TransformMatrix;
import renderer.GLUtils;
import renderer.shaders.RenderState;

public abstract class BaseCamera
{
	public static final float NEAR_CLIP = 1;
	public static final float FAR_CLIP = 0xFFFF;

	// handle projection matrix manually, no GLU functions!
	public final TransformMatrix projMatrix;
	public final TransformMatrix viewMatrix;

	public final Vector3f pos = new Vector3f();

	// camera at zero pitch, zero yaw is oriented along the -z axis.
	// pitch is defined from the xz plane toward -y axis  (positive pitch is looking downward)
	// yaw is defined -z^ toward +x^
	public float pitch, yaw;

	public BaseCamera()
	{
		projMatrix = new TransformMatrix();
		viewMatrix = new TransformMatrix();
	}

	public void setPosition(Vector3f pos)
	{
		this.pos.set(pos);
	}

	public void setRotation(Vector3f rotation)
	{
		pitch = rotation.x;
		yaw = rotation.y;
	}

	public float getPitch()
	{ return pitch; }

	public float getYaw()
	{ return yaw; }

	public int glViewMinX = 0;
	public int glViewMinY = 0;
	public int glViewSizeX = 256;
	public int glViewSizeY = 256;
	protected float glAspectRatio = 1.0f;

	public void glSetViewport(int minX, int minY, int sizeX, int sizeY)
	{
		RenderState.setViewport(minX, minY, sizeX, sizeY);
		glViewMinX = minX;
		glViewMinY = minY;
		glViewSizeX = sizeX;
		glViewSizeY = sizeY;
		glAspectRatio = (float) glViewSizeX / glViewSizeY;
	}

	public final void glLoadProjection()
	{
		RenderState.setProjectionMatrix(projMatrix);
	}

	public final void glLoadTransform()
	{
		updateTransfrom();
		viewMatrix.setIdentity();
		viewMatrix.translate(new Vector3f(-pos.x, -pos.y, -pos.z));
		viewMatrix.rotate(Axis.Y, yaw);
		viewMatrix.rotate(Axis.X, pitch);

		RenderState.setViewMatrix(viewMatrix);
	}

	public void updateTransfrom()
	{}

	/**
	 * Finds the 3D coordinate of the mouse position in this camera using gluUnProject.
	 * @param mouseX
	 * @param mouseY
	 * @return
	 */
	public MousePixelRead getMousePosition(int mouseX, int mouseY, boolean useDepth, boolean readStencil)
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

		/*
		if(useDepth)
		{
			// this is the expensive part, reading z from the depth buffer
			FloatBuffer fb = BufferUtils.createFloatBuffer(1);
			glReadPixels(mouseX, mouseY, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, fb);
			winZ = fb.get();
		}
		*/

		IntBuffer stencilValue = BufferUtils.createIntBuffer(1);
		if (readStencil)
			glReadPixels(mouseX, mouseY, 1, 1, GL_STENCIL_INDEX, GL_UNSIGNED_INT, stencilValue);

		FloatBuffer position = BufferUtils.createFloatBuffer(3);
		GLUtils.gluUnProject(winX, winY, winZ, viewMatrix.toFloatBuffer(), projMatrix.toFloatBuffer(), viewport, position);

		if (readStencil)
			return new MousePixelRead(position.get(), position.get(), position.get(), stencilValue.get());
		else
			return new MousePixelRead(position.get(), position.get(), position.get(), -1);
	}

	public Vector3f getScreenCoords(Vector3f pos)
	{
		return getScreenCoords(pos.x, pos.y, pos.z);
	}

	public Vector3f getScreenCoords(float x, float y, float z)
	{
		IntBuffer viewport = BufferUtils.createIntBuffer(16);
		viewport.put(glViewMinX);
		viewport.put(glViewMinY);
		viewport.put(glViewSizeX);
		viewport.put(glViewSizeY);
		viewport.rewind();

		FloatBuffer position = BufferUtils.createFloatBuffer(3);
		GLUtils.gluProject(x, y, z, viewMatrix.toFloatBuffer(), projMatrix.toFloatBuffer(), viewport, position);

		return new Vector3f(position.get(), position.get(), position.get());
	}
}
