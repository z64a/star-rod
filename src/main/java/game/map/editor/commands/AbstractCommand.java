package game.map.editor.commands;

import javax.swing.SwingUtilities;

import game.map.editor.MapEditor;
import util.Logger;

public abstract class AbstractCommand
{
	public static enum STATE
	{
		READY, EXECUTED, UNDONE
	}

	private STATE state;
	private final String name;
	private boolean silent;

	protected final MapEditor editor;

	public AbstractCommand(String name)
	{
		this(name, false);
	}

	public AbstractCommand(String name, boolean silent)
	{
		editor = MapEditor.instance();
		state = STATE.READY;
		this.name = name;
		this.silent = silent;
	}

	public void silence()
	{
		silent = true;
	}

	// some commands can determine during initialization that they do not need to be executed
	public boolean shouldExec()
	{
		return true;
	}

	public boolean modifiesMap()
	{
		return true;
	}

	public void exec()
	{
		if (SwingUtilities.isEventDispatchThread())
			throw new RuntimeException("Invalid state. Tried to exec command from EDT.");

		state = STATE.EXECUTED;
		if (!silent)
			Logger.log("Exec: " + name);
	}

	public void undo()
	{
		state = STATE.UNDONE;
		if (!silent)
			Logger.log("Undo: " + name);
	}

	public STATE getState()
	{
		return state;
	}
}
