package game.map.editor;

import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.*;

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

		glEnable(GL_POINT_SMOOTH);
		glHint(GL_POINT_SMOOTH, GL_NICEST);

		glEnable(GL_LINE_SMOOTH);
		glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);

		RenderState.init();
		editor.glInit();
	}

	@Override
	public void paintGL()
	{
		editor.glDraw();
		swapBuffers();
		repaint();
	}
}
