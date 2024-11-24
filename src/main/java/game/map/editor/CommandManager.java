package game.map.editor;

import java.util.Stack;

import game.map.editor.commands.AbstractCommand;
import util.EvictingStack;
import util.Logger;

public class CommandManager
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

		if (cmd.modifiesMap())
			MapEditor.instance().map.modified = true;
	}

	/**
	 * Certain actions only use commands for undo/redo maintainence, and not for the
	 * initial execution. Those commands are sent here. However, this practice should
	 * be avoided wherever possible because it may lead to subtle errors involving
	 * undo/redo state integrity.
	 */
	public void pushCommand(AbstractCommand cmd)
	{
		undoStack.push(cmd);
		redoStack.clear();
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
