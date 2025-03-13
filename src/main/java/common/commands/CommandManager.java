package common.commands;

import java.util.Stack;

import util.EvictingStack;
import util.Logger;

public abstract class CommandManager
{
	private EvictingStack<AbstractCommand> undoStack;
	private Stack<AbstractCommand> redoStack;

	public CommandManager(int undoLimit)
	{
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

		cmd.exec();
		undoStack.push(cmd);
		redoStack.clear();

		if (cmd.modifiesData())
			onModified();
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

	public abstract void onModified();
}
