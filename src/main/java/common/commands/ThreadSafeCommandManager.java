package common.commands;

import java.util.Stack;

import util.EvictingStack;
import util.Logger;

/**
 * Manages undo and redo operations for editor commands in a thread-safe manner.
 * <p>
 * This class schedules command execution outside the critical section of an editor's main loop.
 * It ensures that any undoable editor state remains immutable during the main loop by using
 * a lock to synchronize access.
 */
public class ThreadSafeCommandManager
{
	private EvictingStack<AbstractCommand> undoStack;
	private Stack<AbstractCommand> redoStack;

	private final Object modifyLock;

	private final Runnable modifyCallback;

	/**
	 * Creates a new {@code ThreadSafeCommandManager} with a specified undo limit.
	 *
	 * @param undoLimit      the maximum number of commands to keep in the undo stack
	 * @param modifyLock     the lock object to synchronize command execution
	 * @param modifyCallback the callback to notify when a command modifies the editor state
	 */
	public ThreadSafeCommandManager(int undoLimit, Object modifyLock, Runnable modifyCallback)
	{
		this.modifyLock = modifyLock;
		this.modifyCallback = modifyCallback;

		undoStack = new EvictingStack<>(undoLimit);
		redoStack = new Stack<>();
	}

	/**
	* Sets the maximum number of commands that can be stored in the undo stack.
	* When the limit is exceeded, the oldest commands are discarded.
	*
	* @param undoLimit the new maximum size of the undo stack
	*/
	public void setUndoLimit(int undoLimit)
	{
		undoStack.setCapacity(undoLimit);
	}

	/**
	 * Executes a command and adds it to the undo stack.
	 * <p>
	 * If the command modifies the editor state, the modify callback is triggered.
	 * The redo stack is cleared upon execution.
	 *
	 * @param cmd the command to execute
	 */
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

	/**
	* Pushes a command onto the undo stack without executing it.
	* <p>
	* This is useful for commands that are undoable but do not require an initial execution.
	* The redo stack is cleared when a new command is pushed.
	*
	* @param cmd the command to push onto the undo stack
	*/
	public void pushCommand(AbstractCommand cmd)
	{
		synchronized (modifyLock) {
			undoStack.push(cmd);
			redoStack.clear();
		}
	}

	/**
	 * Undoes the last executed command.
	 * <p>
	 * The command will then be available to redo.
	 */
	public void undo()
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

	/**
	 * Redoes the last undone command.
	 * <p>
	 * The command will then be available to undo.
	 */
	public void redo()
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

	/**
	 * Clears both the undo and redo stacks.
	 * <p>
	 * After calling this method, no commands will be available to undo or redo.
	 */
	public void flush()
	{
		undoStack.clear();
		redoStack.clear();
	}
}
