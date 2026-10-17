package game.map.tree;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.junit.jupiter.api.Test;

import common.commands.AbstractCommand;
import common.commands.ThreadSafeCommandManager;

import game.map.MapObject.ShapeType;
import game.map.shape.Model;

class TreeSelectionNotificationTest
{
	@Test
	void removingSelectedNodeDoesNotRecordASelectionEditAndDestroyRedo() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			ModelTreeModel model = new ModelTreeModel();
			JTree tree = new JTree(model);
			Model object = new Model(ShapeType.GROUP);
			AtomicInteger unwantedEdits = new AtomicInteger();
			ThreadSafeCommandManager commands = new ThreadSafeCommandManager(8, new Object(), () -> {});
			tree.addTreeSelectionListener(event -> {
				if (!model.ignoreSelectionChanges) {
					unwantedEdits.incrementAndGet();
					commands.executeCommand(new AbstractCommand("unexpected selection edit", true) {});
				}
			});
			commands.executeCommand(new AbstractCommand("create node", true) {
				@Override
				public void exec()
				{
					model.create(object);
				}

				@Override
				public void undo()
				{
					model.remove(object);
				}
			});
			model.ignoreSelectionChanges = true;
			tree.setSelectionPath(new TreePath(object.getNode().getPath()));
			model.ignoreSelectionChanges = false;
			commands.undo();
			assertEquals(0, unwantedEdits.get());
			assertEquals(0, model.getRoot().getChildCount());
			commands.redo();
			assertEquals(1, model.getRoot().getChildCount());
			assertFalse(model.ignoreSelectionChanges, "Notification suppression must be scoped");
		});
	}
}
