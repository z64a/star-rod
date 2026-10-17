package game.map.editor.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.junit.jupiter.api.Test;

import common.commands.ThreadSafeCommandManager;

class TreeReorderThreadingTest
{
	@Test
	void successiveReordersAndUndoSeeTheCompletedTreeWithoutWaitingForSwing() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
			DefaultMutableTreeNode a = new DefaultMutableTreeNode("a");
			DefaultMutableTreeNode b = new DefaultMutableTreeNode("b");
			DefaultMutableTreeNode c = new DefaultMutableTreeNode("c");
			root.add(a);
			root.add(b);
			root.add(c);
			SimpleEditableJTree tree = new SimpleEditableJTree(new DefaultTreeModel(root));
			Object lock = new Object();
			AtomicInteger changes = new AtomicInteger();
			ThreadSafeCommandManager commands = new ThreadSafeCommandManager(8, lock, changes::incrementAndGet);
			Runnable checkThread = () -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				assertTrue(Thread.holdsLock(lock));
			};
			commands.executeCommand(tree.new MoveNode(tree, checkThread, checkThread, c, root, 0));
			assertSame(c, root.getChildAt(0));
			commands.executeCommand(tree.new MoveNode(tree, checkThread, checkThread, a, root, -1));
			assertSame(a, root.getChildAt(2));
			commands.undo();
			assertSame(c, root.getChildAt(0));
			assertSame(a, root.getChildAt(1));
			commands.undo();
			assertSame(a, root.getChildAt(0));
			assertSame(c, root.getChildAt(2));
			commands.redo();
			commands.redo();
			assertSame(c, root.getChildAt(0));
			assertSame(a, root.getChildAt(2));
			assertEquals(6, changes.get());
		});
	}
}
