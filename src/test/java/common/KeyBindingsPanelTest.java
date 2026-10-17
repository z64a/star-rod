package common;

import static game.map.editor.EditorShortcut.*;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import game.map.editor.MapKeyConfig;

class KeyBindingsPanelTest
{
	@Test
	void editingAndResettingStayInTheDraftUntilAccepted() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			MapKeyConfig config = new MapKeyConfig();
			KeyBindingsPanel panel = new KeyBindingsPanel(config);
			JTextField search = components(panel, JTextField.class).get(0);
			JTable table = components(panel, JTable.class).get(0);
			search.setText("Play in Editor Spin");
			assertEquals(1, table.getRowCount());
			table.setRowSelectionInterval(0, 0);
			button(panel, "Clear").doClick();
			assertEquals(KeyBinding.NONE, panel.getBindings().get(PLAY_IN_EDITOR_SPIN));
			assertEquals(PLAY_IN_EDITOR_SPIN.getDefaultBinding(), config.getBinding(PLAY_IN_EDITOR_SPIN));
			button(panel, "Reset All").doClick();
			assertEquals(config.copyBindings(), panel.getBindings());
			search.setText("[");
			assertEquals(0, table.getRowCount(), "Filter accepts literal punctuation");
		});
	}

	@Test
	void keyCaptureConsumesEscapeEnterTabAndCombinedModifiers() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			KeyBindingsPanel.KeyCaptureField field = new KeyBindingsPanel.KeyCaptureField(false);
			for (int key : new int[] { KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER, KeyEvent.VK_TAB, KeyEvent.VK_Z }) {
				int modifiers = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
				KeyEvent event = new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, modifiers, key, KeyEvent.CHAR_UNDEFINED);
				field.processKeyEvent(event);
				assertTrue(event.isConsumed());
				assertEquals(new KeyBinding(key, modifiers), field.getBinding());
			}
		});
	}

	@Test
	void runCanUseAModifierAloneWithoutDuplicatingItsName() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			KeyBindingsPanel.KeyCaptureField field = new KeyBindingsPanel.KeyCaptureField(true);
			field.processKeyEvent(new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED));
			field.processKeyEvent(new KeyEvent(field, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED));
			assertEquals("Shift", field.getBinding().getDisplayText());
			assertEquals(field.getBinding(), KeyBinding.parse(field.getBinding().serialize()));
		});
	}

	private static JButton button(Container parent, String text)
	{
		return components(parent, JButton.class).stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
	}

	private static <T> List<T> components(Container parent, Class<T> type)
	{
		List<T> result = new ArrayList<>();
		for (Component child : parent.getComponents()) {
			if (type.isInstance(child))
				result.add(type.cast(child));
			if (child instanceof Container container)
				result.addAll(components(container, type));
		}
		return result;
	}
}
