package common;

import static game.map.editor.EditorShortcut.*;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

import common.KeyboardInput.KeyInputEvent;
import common.KeyboardInput.KeyboardInputListener;

import game.map.editor.MapKeyConfig;
import game.map.editor.MapKeyInput;

class KeyboardInputTest
{
	private final JPanel source = new JPanel();

	@Test
	void fastPressReleaseSequencesRetainOrderAndModifierSnapshots()
	{
		KeyboardInput keyboard = new KeyboardInput(source);
		List<String> delivered = new ArrayList<>();
		KeyboardInputListener listener = new KeyboardInputListener() {
			@Override
			public void keyPress(KeyInputEvent event)
			{
				delivered.add("press:" + event.modifiers);
			}

			@Override
			public void keyRelease(KeyInputEvent event)
			{
				delivered.add("release:" + event.modifiers);
			}
		};
		keyboard.keyPressed(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK));
		keyboard.keyReleased(event(KeyEvent.KEY_RELEASED, KeyEvent.VK_J, 0));
		keyboard.keyPressed(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_J, InputEvent.SHIFT_DOWN_MASK));
		keyboard.keyReleased(event(KeyEvent.KEY_RELEASED, KeyEvent.VK_J, 0));
		keyboard.update(listener, true);
		assertEquals(List.of("press:128", "release:0", "press:64", "release:0"), delivered);
	}

	@Test
	void losingFocusReleasesActiveInputsEvenIfPhysicalReleaseIsAlreadyQueued()
	{
		MapKeyConfig config = new MapKeyConfig();
		KeyboardInput keyboard = new KeyboardInput(source, config);
		List<Boolean> delivered = new ArrayList<>();
		MapKeyInput listener = new MapKeyInput(config, () -> true, (input, press) -> delivered.add(press));
		keyboard.keyPressed(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_J, 0));
		keyboard.update(listener, true);
		keyboard.keyReleased(event(KeyEvent.KEY_RELEASED, KeyEvent.VK_J, 0));
		keyboard.update(listener, false);
		keyboard.update(listener, true);
		assertEquals(List.of(true, false), delivered);
	}

	@Test
	void focusLossDiscardsUnprocessedPressesAndAutoRepeatDoesNotDuplicateActions()
	{
		MapKeyConfig config = new MapKeyConfig();
		KeyboardInput keyboard = new KeyboardInput(source, config);
		List<Boolean> delivered = new ArrayList<>();
		MapKeyInput listener = new MapKeyInput(config, () -> true, (input, press) -> delivered.add(press));
		keyboard.keyPressed(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_J, 0));
		keyboard.keyReleased(event(KeyEvent.KEY_RELEASED, KeyEvent.VK_J, 0));
		keyboard.update(listener, false);
		keyboard.update(listener, true);
		assertTrue(delivered.isEmpty());
		keyboard.keyPressed(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_J, 0));
		keyboard.keyPressed(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_J, 0));
		keyboard.update(listener, true);
		assertEquals(List.of(true), delivered);
	}

	@Test
	void heldInputsRequireTheirModifiersAndRespectMoreSpecificBindings()
	{
		MapKeyConfig config = new MapKeyConfig();
		KeyboardInput keyboard = new KeyboardInput(source, config);
		var bindings = config.copyBindings();
		bindings.put(MOVE_FORWARD, new KeyBinding(KeyEvent.VK_F12, InputEvent.ALT_DOWN_MASK));
		bindings.put(SAVE, new KeyBinding(KeyEvent.VK_F12, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
		config.setBindings(bindings);
		keyboard.keyPressed(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_F12, 0));
		assertFalse(keyboard.isDown(MOVE_FORWARD));
		keyboard.keyPressed(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_ALT, InputEvent.ALT_DOWN_MASK));
		assertTrue(keyboard.isDown(MOVE_FORWARD));
		keyboard.keyPressed(event(KeyEvent.KEY_PRESSED, KeyEvent.VK_CONTROL, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
		assertFalse(keyboard.isDown(MOVE_FORWARD));
		assertTrue(keyboard.isDown(SAVE));
	}

	private KeyEvent event(int id, int key, int modifiers)
	{
		return new KeyEvent(source, id, 0, modifiers, key, KeyEvent.CHAR_UNDEFINED);
	}
}
