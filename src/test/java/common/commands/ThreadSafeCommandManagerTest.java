package common.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ThreadSafeCommandManagerTest
{
	@Test
	void dataChangesNotifyOnExecuteUndoAndRedo()
	{
		AtomicInteger modifications = new AtomicInteger();
		ThreadSafeCommandManager manager = new ThreadSafeCommandManager(4, new Object(), modifications::incrementAndGet);

		manager.executeCommand(new TestCommand(true));
		manager.undo();
		manager.redo();

		assertEquals(3, modifications.get());
	}

	@Test
	void editorOnlyCommandsNeverNotify()
	{
		AtomicInteger modifications = new AtomicInteger();
		ThreadSafeCommandManager manager = new ThreadSafeCommandManager(4, new Object(), modifications::incrementAndGet);

		manager.executeCommand(new TestCommand(false));
		manager.undo();
		manager.redo();

		assertEquals(0, modifications.get());
	}

	@Test
	void mapCommandManagerNotifiesOnExecuteUndoAndRedo()
	{
		AtomicInteger modifications = new AtomicInteger();
		CommandManager manager = new CommandManager(4, modifications::incrementAndGet);

		manager.executeCommand(new TestCommand(true));
		manager.action_Undo();
		manager.action_Redo();

		assertEquals(3, modifications.get());
	}

	@Test
	void mapCommandManagerIgnoresEditorOnlyCommands()
	{
		AtomicInteger modifications = new AtomicInteger();
		CommandManager manager = new CommandManager(4, modifications::incrementAndGet);

		manager.executeCommand(new TestCommand(false));
		manager.action_Undo();
		manager.action_Redo();

		assertEquals(0, modifications.get());
	}

	private static final class TestCommand extends AbstractCommand
	{
		private final boolean modifiesData;

		private TestCommand(boolean modifiesData)
		{
			super("test", true);
			this.modifiesData = modifiesData;
		}

		@Override
		public boolean modifiesData()
		{
			return modifiesData;
		}
	}
}
