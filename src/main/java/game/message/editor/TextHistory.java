package game.message.editor;

import java.util.Stack;

import util.EvictingStack;

public class TextHistory
{
	private boolean hasCurrent;
	private String current;
	private EvictingStack<String> undoStack;
	private Stack<String> redoStack;

	public TextHistory(int undoLimit)
	{
		undoStack = new EvictingStack<>(undoLimit);
		redoStack = new Stack<>();
	}

	public void setUndoLimit(int undoLimit)
	{
		undoStack.setCapacity(undoLimit);
	}

	public void set(String txt, boolean replace)
	{
		if (!replace) {
			if (hasCurrent)
				undoStack.push(current);
			redoStack.clear();
		}
		current = txt;
		hasCurrent = true;
	}

	public void clear()
	{
		undoStack.clear();
		redoStack.clear();
		hasCurrent = false;
	}

	public boolean canUndo()
	{
		return (undoStack.size() > 0);
	}

	public boolean canRedo()
	{
		return (redoStack.size() > 0);
	}

	public String undo()
	{
		redoStack.push(current);
		current = undoStack.pop();
		return current;
	}

	public String redo()
	{
		undoStack.push(current);
		current = redoStack.pop();
		return current;
	}
}
