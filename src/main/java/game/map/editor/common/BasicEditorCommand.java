package game.map.editor.common;

import util.Logger;

public abstract class BasicEditorCommand
{
	public static enum STATE
	{
		READY, EXECUTED, UNDONE
	}

	private STATE state;
	private final String name;
	private boolean silent;

	public BasicEditorCommand(String name)
	{
		this(name, false);
	}

	public BasicEditorCommand(String name, boolean silent)
	{
		state = STATE.READY;
		this.name = name;
		this.silent = silent;
	}

	public void silence()
	{
		silent = true;
	}

	public boolean shouldExec()
	{
		return true;
	}

	public boolean triggersModify()
	{
		return true;
	}

	public void undo()
	{
		state = STATE.UNDONE;
		if (!silent)
			Logger.log("Undo: " + name);
	}

	public void redo()
	{
		state = STATE.EXECUTED;
		if (!silent)
			Logger.log("Redo: " + name);
	}

	public STATE getState()
	{ return state; }
}
