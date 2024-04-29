package game.map.editor.common;

import app.config.Options.Scope;

public class BaseEditorSettings
{
	public boolean fullscreen;
	public boolean resizeable;
	public boolean hasMenuBar;
	public boolean glWindowGrabsMouse;
	public boolean glWindowHaltsForDialogs;

	public int sizeX = 800;
	public int sizeY = 600;
	public int targetFPS = 60;

	public String title = null;

	public boolean hasLog = false;
	public String logName;

	public boolean hasConfig = false;
	public String configFileName = null;
	public Scope configScope = null;

	public static BaseEditorSettings create()
	{
		// required stuff could be passed in here
		return new BaseEditorSettings();
	}

	private BaseEditorSettings()
	{}

	public BaseEditorSettings setTitle(String title)
	{
		this.title = title;
		return this;
	}

	public BaseEditorSettings setSize(int X, int Y)
	{
		sizeX = X;
		sizeY = Y;
		return this;
	}

	public BaseEditorSettings setFramerate(int fps)
	{
		targetFPS = fps;
		return this;
	}

	public BaseEditorSettings setLog(String name)
	{
		if (name == null || name.isEmpty())
			return this;

		hasLog = true;
		logName = name;
		return this;
	}

	public BaseEditorSettings setConfig(Scope scope, String name)
	{
		if (scope == null || name == null || name.isEmpty())
			return this;

		hasConfig = true;
		configFileName = name;
		configScope = scope;
		return this;
	}

	public BaseEditorSettings setFullscreen(boolean value)
	{
		fullscreen = value;
		return this;
	}

	public BaseEditorSettings setResizeable(boolean value)
	{
		resizeable = value;
		return this;
	}

	public BaseEditorSettings hasMenuBar(boolean value)
	{
		hasMenuBar = value;
		return this;
	}

	public BaseEditorSettings setGrabsMouse(boolean value)
	{
		glWindowGrabsMouse = value;
		return this;
	}

	public BaseEditorSettings setWaitsForDialogs(boolean value)
	{
		glWindowHaltsForDialogs = value;
		return this;
	}
}
