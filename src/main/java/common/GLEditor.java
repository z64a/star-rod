package common;

import java.awt.Window;

import util.Logger;

public abstract class GLEditor
{
	public final Object modifyLock = new Object();

	protected final EditorCanvas glCanvas;

	protected abstract void glInit();

	protected abstract void glDraw();

	protected GLEditor()
	{
		glCanvas = new EditorCanvas(this);
	}

	protected void runInContext(Runnable runnable)
	{
		glCanvas.runInContext(runnable);
	}

	public static void setFullScreenEnabled(Window frame, boolean b)
	{
		try {
			Class<? extends Object> fsu = Class.forName("com.apple.eawt.FullScreenUtilities");
			fsu.getMethod("setWindowCanFullScreen", Window.class, Boolean.TYPE).invoke(null, frame, b);
		}
		catch (Exception e) {
			Logger.printStackTrace(e);
		}
	}
}
