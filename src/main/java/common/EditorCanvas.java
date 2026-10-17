package common;

import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.GL_VERSION;
import static org.lwjgl.opengl.GL11.glGetString;

import java.awt.GraphicsConfiguration;
import java.awt.geom.AffineTransform;

import org.lwjgl.Version;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import renderer.shaders.RenderState;
import util.Logger;

public class EditorCanvas extends AWTGLCanvas
{
	private static GLData getConfiguration()
	{
		GLData data = new GLData();
		data.samples = 4;
		data.swapInterval = 0;
		data.majorVersion = 3;
		data.minorVersion = 3;
		data.profile = GLData.Profile.CORE;
		data.stencilSize = 8;
		return data;
	}

	private final GLEditor editor;
	private double framebufferScaleX = Double.NaN;
	private double framebufferScaleY = Double.NaN;

	public EditorCanvas(GLEditor editor)
	{
		super(getConfiguration());
		this.editor = editor;
	}

	@Override
	public void initGL()
	{
		Logger.log("LWJGL Version: " + Version.getVersion());
		Logger.logf("Initializing OpenGL %d.%d (%s)",
			effective.majorVersion,
			effective.minorVersion,
			effective.profile == null ? "null" : effective.profile.toString().toLowerCase());

		createCapabilities();

		Logger.logf("Using driver: %s", glGetString(GL_VERSION));

		updateFramebufferScale();
		RenderState.init();
		editor.glInit();
	}

	@Override
	public void paintGL()
	{
		updateFramebufferScale();
		editor.glDraw();
		swapBuffers();
		repaint();
	}

	private void updateFramebufferScale()
	{
		GraphicsConfiguration config = getGraphicsConfiguration();
		if (config == null) {
			RenderState.setDefaultFramebufferScale(1.0, 1.0);
			return;
		}

		// AWT reports logical dimensions, while OpenGL renders to device pixels.
		AffineTransform transform = config.getDefaultTransform();
		double scaleX = transform.getScaleX();
		double scaleY = transform.getScaleY();
		RenderState.setDefaultFramebufferScale(scaleX, scaleY);

		if (scaleX != framebufferScaleX || scaleY != framebufferScaleY) {
			Logger.logf("Using framebuffer scale: %.2f x %.2f", scaleX, scaleY);
			framebufferScaleX = scaleX;
			framebufferScaleY = scaleY;
		}
	}
}
