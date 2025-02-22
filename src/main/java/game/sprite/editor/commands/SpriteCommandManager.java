package game.sprite.editor.commands;

import java.util.Stack;

import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import util.EvictingStack;
import util.Logger;

public class SpriteCommandManager
{
	private final SpriteEditor editor;

	private EvictingStack<AbstractCommand> undoStack;
	private Stack<AbstractCommand> redoStack;

	public SpriteCommandManager(SpriteEditor editor, int undoLimit)
	{
		this.editor = editor;

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

		synchronized (editor.modifyLock) {
			cmd.exec();

			undoStack.push(cmd);
			redoStack.clear();

			if (cmd.modifiesData())
				onModified();
		}
	}

	public void action_Undo()
	{
		if (undoStack.size() > 0) {
			synchronized (editor.modifyLock) {
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
			synchronized (editor.modifyLock) {
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

	public void onModified()
	{
		editor.modified = true;
	}
}
