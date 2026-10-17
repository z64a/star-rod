package common.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class ThreadSafeCommandManagerTest
{
	@Test
	void validationExecutionAndHistoryCallbacksShareTheRenderLockOnTheCallerThread() throws Exception
	{
		Object lock = new Object();
		AtomicInteger value = new AtomicInteger();
		ThreadSafeCommandManager manager = new ThreadSafeCommandManager(4, lock, () -> {
			assertTrue(Thread.holdsLock(lock));
			assertTrue(SwingUtilities.isEventDispatchThread());
		});
		AbstractCommand command = new AbstractCommand("locked edit", true) {
			@Override
			public boolean shouldExec()
			{
				assertTrue(Thread.holdsLock(lock), "Validation reads model state too");
				return true;
			}

			@Override
			public void exec()
			{
				assertTrue(Thread.holdsLock(lock));
				assertTrue(SwingUtilities.isEventDispatchThread());
				value.incrementAndGet();
			}

			@Override
			public void undo()
			{
				assertTrue(Thread.holdsLock(lock));
				assertTrue(SwingUtilities.isEventDispatchThread());
				value.decrementAndGet();
			}
		};
		SwingUtilities.invokeAndWait(() -> {
			manager.executeCommand(command);
			assertEquals(1, value.get(), "The edit finishes before execute returns");
			manager.undo();
			assertEquals(0, value.get());
			manager.redo();
			assertEquals(1, value.get());
		});
	}

	@Test
	void commandCannotEvenValidateWhileTheRendererOwnsTheLock() throws Exception
	{
		Object lock = new Object();
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch validated = new CountDownLatch(1);
		ThreadSafeCommandManager manager = new ThreadSafeCommandManager(4, lock, () -> {});
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> edit;
			synchronized (lock) {
				edit = executor.submit(() -> {
					started.countDown();
					manager.executeCommand(new AbstractCommand("concurrent edit", true) {
						@Override
						public boolean shouldExec()
						{
							validated.countDown();
							return true;
						}
					});
				});
				assertTrue(started.await(5, TimeUnit.SECONDS));
				assertFalse(validated.await(100, TimeUnit.MILLISECONDS));
			}
			edit.get(5, TimeUnit.SECONDS);
			assertEquals(0, validated.getCount());
		}
		finally {
			executor.shutdownNow();
		}
	}

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
