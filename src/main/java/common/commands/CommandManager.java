package common.commands;

import java.util.Stack;

import util.EvictingStack;
import util.Logger;

public class CommandManager
{
	private EvictingStack<AbstractCommand> undoStack;
	private Stack<AbstractCommand> redoStack;

	private final Runnable modifyCallback;

	public CommandManager(int undoLimit, Runnable modifyCallback)
	{
		undoStack = new EvictingStack<>(undoLimit);
		redoStack = new Stack<>();

		this.modifyCallback = modifyCallback;
	}

	public void setUndoLimit(int undoLimit)
	{
		undoStack.setCapacity(undoLimit);
	}

	public void executeCommand(AbstractCommand cmd)
	{
		if (!cmd.shouldExec())
			return;

		cmd.exec();
		undoStack.push(cmd);
		redoStack.clear();

		if (cmd.modifiesData())
			modifyCallback.run();
	}

	public void action_Undo()
	{
		if (undoStack.size() > 0) {
			AbstractCommand cmd = undoStack.pop();
			cmd.undo();
			redoStack.push(cmd);
		}
		else {
			Logger.log("Can't undo any more.");
		}
	}

	public void action_Redo()
	{
		if (redoStack.size() > 0) {
			AbstractCommand cmd = redoStack.pop();
			cmd.exec();
			undoStack.push(cmd);
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
