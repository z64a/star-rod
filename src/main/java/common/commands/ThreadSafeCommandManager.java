package common.commands;

import java.util.Stack;

import util.EvictingStack;
import util.Logger;

public class ThreadSafeCommandManager
{
	private EvictingStack<AbstractCommand> undoStack;
	private Stack<AbstractCommand> redoStack;

	private final Object modifyLock;
	private final Runnable modifyCallback;

	public ThreadSafeCommandManager(int undoLimit, Object modifyLock, Runnable modifyCallback)
	{
		this.modifyLock = modifyLock;
		this.modifyCallback = modifyCallback;

		undoStack = new EvictingStack<>(undoLimit);
		redoStack = new Stack<>();
	}

	public void setUndoLimit(int undoLimit)
	{
		undoStack.setCapacity(undoLimit);
	}

	public void executeCommand(AbstractCommand cmd)
	{
		if (!cmd.shouldExec())
			return;

		synchronized (modifyLock) {
			cmd.exec();

			undoStack.push(cmd);
			redoStack.clear();

			if (cmd.modifiesData())
				modifyCallback.run();
		}
	}

	public void action_Undo()
	{
		if (undoStack.size() > 0) {
			synchronized (modifyLock) {
				AbstractCommand cmd = undoStack.pop();
				cmd.undo();
				redoStack.push(cmd);
			}
		}
		else {
			Logger.log("Can't undo any more.");
		}
	}

	public void action_Redo()
	{
		if (redoStack.size() > 0) {
			synchronized (modifyLock) {
				AbstractCommand cmd = redoStack.pop();
				cmd.exec();
				undoStack.push(cmd);
			}
		}
		else {
			Logger.log("Can't redo anything.");
		}
	}

	public void flush()
	{
		undoStack.clear();
		redoStack.clear();
	}
}
