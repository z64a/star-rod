package game.map.editor.selection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import common.commands.ThreadSafeCommandManager;

import game.map.MapObject;
import game.map.shape.Model;

class SelectionThreadingTest
{
	@Test
	void selectUndoAndRedoCompleteOnTheEdtBeforeTheNextFrame() throws Exception
	{
		SelectionManager selection = new SelectionManager(null);
		ThreadSafeCommandManager commands = new ThreadSafeCommandManager(8, new Object(), () -> fail("Selection must not dirty the map"));
		Model model = new Model();
		RecordingGUI gui = new RecordingGUI();
		SwingUtilities.invokeAndWait(() -> {
			commands.executeCommand(selection.getAddObject(model));
			assertTrue(model.selected);
			selection.syncGUI(gui);
			assertEquals(List.of(model), gui.selected);
			commands.undo();
			assertFalse(model.selected);
			selection.syncGUI(gui);
			assertEquals(List.of(model), gui.deselected);
			commands.redo();
			assertTrue(model.selected);
			selection.syncGUI(gui);
			assertEquals(3, gui.updates);
			selection.syncGUI(gui);
			assertEquals(3, gui.updates, "An idle frame does not replay selection changes");
		});
	}

	@Test
	void changesReportedDuringGuiRefreshAreNotLost() throws Exception
	{
		SelectionManager selection = new SelectionManager(null);
		Model first = new Model();
		Model second = new Model();
		RecordingGUI gui = new RecordingGUI() {
			@Override
			public void finishUpdate()
			{
				super.finishUpdate();
				if (updates == 1)
					selection.selectObject(second);
			}
		};
		SwingUtilities.invokeAndWait(() -> {
			selection.selectObject(first);
			selection.syncGUI(gui);
			assertEquals(List.of(first), gui.selected);
			selection.syncGUI(gui);
			assertEquals(List.of(first, second), gui.selected);
		});
	}

	@Test
	void createDeleteUndoBeforeGuiRefreshUsesOnlyFinalSelection() throws Exception
	{
		SelectionManager selection = new SelectionManager(null);
		Model model = new Model();
		RecordingGUI gui = new RecordingGUI();
		SwingUtilities.invokeAndWait(() -> {
			selection.createObject(model);
			selection.deleteObject(model);
			selection.createObject(model);
			selection.syncGUI(gui);
			assertEquals(List.of(model), gui.created);
			assertTrue(gui.deleted.isEmpty());
		});
	}

	private static class RecordingGUI implements SelectionManager.GUISelectionInterface
	{
		final List<MapObject> selected = new ArrayList<>();
		final List<MapObject> deselected = new ArrayList<>();
		final List<MapObject> created = new ArrayList<>();
		final List<MapObject> deleted = new ArrayList<>();
		int updates;

		@Override
		public void setObjectsSelected(Iterable<MapObject> objects)
		{
			objects.forEach(selected::add);
		}

		@Override
		public void setObjectsDeselected(Iterable<MapObject> objects)
		{
			objects.forEach(deselected::add);
		}

		@Override
		public void setObjectsCreated(Iterable<MapObject> objects)
		{
			objects.forEach(created::add);
		}

		@Override
		public void setObjectsDeleted(Iterable<MapObject> objects)
		{
			objects.forEach(deleted::add);
		}

		@Override
		public void finishUpdate()
		{
			assertTrue(SwingUtilities.isEventDispatchThread());
			updates++;
		}
	}
}
