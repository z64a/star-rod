package common.commands;

import javax.swing.SwingUtilities;

import util.Logger;

public abstract class AbstractCommand
{
	public static enum ExecState
	{
		READY, EXECUTED, UNDONE
	}

	private ExecState state;
	private final String name;
	private boolean silent;

	public AbstractCommand(String name)
	{
		this(name, false);
	}

	public AbstractCommand(String name, boolean silent)
	{
		state = ExecState.READY;
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

	public boolean modifiesData()
	{
		return true;
	}

	public void exec()
	{
		if (SwingUtilities.isEventDispatchThread())
			throw new RuntimeException("Invalid state. Tried to exec command from EDT.");

		state = ExecState.EXECUTED;
		if (!silent)
			Logger.log("Exec: " + name);
	}

	public void undo()
	{
		state = ExecState.UNDONE;
		if (!silent)
			Logger.log("Undo: " + name);
	}

	public ExecState getState()
	{
		return state;
	}
}
