package game.map.editor.common;

import java.util.Stack;

import util.EvictingStack;
import util.Logger;

public class BasicCommandManager
{
	private EvictingStack<BasicEditorCommand> undoStack;
	private Stack<BasicEditorCommand> redoStack;

	public BasicCommandManager(int undoLimit)
	{
		undoStack = new EvictingStack<>(undoLimit);
		redoStack = new Stack<>();
	}

	public void setUndoLimit(int undoLimit)
	{
		undoStack.setCapacity(undoLimit);
	}

	public void pushCommand(BasicEditorCommand cmd)
	{
		undoStack.push(cmd);
		redoStack.clear();
	}

	public void undo()
	{
		if (undoStack.size() > 0) {
			BasicEditorCommand cmd = undoStack.pop();
			cmd.undo();
			redoStack.push(cmd);
		}
		else {
			Logger.log("Can't undo any more.");
		}
	}

	public void redo()
	{
		if (redoStack.size() > 0) {
			BasicEditorCommand cmd = redoStack.pop();
			cmd.redo();
			undoStack.push(cmd);
		}
		else {
			Logger.log("Can't redo anything.");
		}
	}
}
