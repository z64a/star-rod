package game.map.editor;

import static game.map.editor.EditorShortcut.*;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import common.KeyBinding;
import common.KeyboardInput;
import common.KeyboardInput.KeyInputEvent;

class MapKeyBindingsTest
{
	@TempDir
	Path temporary;

	@Test
	void defaultsKeepThisBranchsControls()
	{
		MapKeyConfig config = new MapKeyConfig();
		assertEquals(PLAY_IN_EDITOR_SPIN, config.resolve(KeyEvent.VK_R, 0, true));
		assertEquals(TOGGLE_TWO_SIDED, config.resolve(KeyEvent.VK_L, 0, false));
		assertEquals(UNDO, config.resolve(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK, false));
		assertEquals(KeyEvent.VK_SHIFT, config.getBinding(PLAY_IN_EDITOR_RUN).keyCode);
	}

	@Test
	void rebindClearAndResetChangeBothDispatchAndHeldInput()
	{
		MapKeyConfig config = new MapKeyConfig();
		JPanel source = new JPanel();
		KeyboardInput keyboard = new KeyboardInput(source, config);
		var bindings = config.copyBindings();
		bindings.put(MOVE_FORWARD, new KeyBinding(KeyEvent.VK_UP, 0));
		bindings.put(NUDGE_UP, KeyBinding.NONE);
		config.setBindings(bindings);
		assertNull(config.resolve(KeyEvent.VK_W, 0, false));
		assertEquals(MOVE_FORWARD, config.resolve(KeyEvent.VK_UP, 0, false));
		keyboard.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
		assertTrue(keyboard.isDown(MOVE_FORWARD));
		assertFalse(keyboard.isDown(NUDGE_UP));
		config.resetAll();
		assertEquals(MOVE_FORWARD, config.resolve(KeyEvent.VK_W, 0, false));
		assertFalse(keyboard.isDown(MOVE_FORWARD));
		assertTrue(keyboard.isDown(NUDGE_UP));
	}

	@Test
	void conflictsAreRejectedWithoutPartiallyChangingTheConfiguration()
	{
		MapKeyConfig config = new MapKeyConfig();
		var original = config.copyBindings();
		var draft = config.copyBindings();
		draft.put(PLAY_IN_EDITOR_SPIN, config.getBinding(PLAY_IN_EDITOR_JUMP));
		assertEquals(PLAY_IN_EDITOR_JUMP, config.findConflict(draft, PLAY_IN_EDITOR_SPIN, draft.get(PLAY_IN_EDITOR_SPIN)));
		assertThrows(IllegalArgumentException.class, () -> config.setBindings(draft));
		assertEquals(original, config.copyBindings());
	}

	@Test
	void modifiedJumpAndSpinWorkWhileRunningButExplicitChordsTakePrecedence()
	{
		MapKeyConfig config = new MapKeyConfig();
		var bindings = config.copyBindings();
		bindings.put(PLAY_IN_EDITOR_JUMP, new KeyBinding(KeyEvent.VK_SPACE, InputEvent.ALT_DOWN_MASK));
		config.setBindings(bindings);
		int runningJump = InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
		assertEquals(PLAY_IN_EDITOR_JUMP, config.resolve(KeyEvent.VK_SPACE, runningJump, true));
		assertNull(config.resolve(KeyEvent.VK_SPACE, runningJump, false));
		assertNull(config.resolve(KeyEvent.VK_SPACE, 0, true));
		bindings.put(SAVE, new KeyBinding(KeyEvent.VK_SPACE, runningJump));
		config.setBindings(bindings);
		assertEquals(SAVE, config.resolve(KeyEvent.VK_SPACE, runningJump, true));
	}

	@Test
	void releaseUsesThePressedActionAfterModifiersOrBindingsChange()
	{
		MapKeyConfig config = new MapKeyConfig();
		List<String> events = new ArrayList<>();
		MapKeyInput input = new MapKeyInput(config, () -> true, (action, press) -> events.add(action + ":" + press));
		input.keyPress(new KeyInputEvent(KeyEvent.VK_J, InputEvent.SHIFT_DOWN_MASK));
		var draft = config.copyBindings();
		draft.put(PLAY_IN_EDITOR_JUMP, new KeyBinding(KeyEvent.VK_SPACE, 0));
		config.setBindings(draft);
		input.keyRelease(new KeyInputEvent(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK));
		assertEquals(List.of("PLAY_IN_EDITOR_JUMP:true", "PLAY_IN_EDITOR_JUMP:false"), events);
	}

	@Test
	void overridesRoundTripIncludingUnboundActionsAndCombinedModifiers() throws IOException
	{
		MapKeyConfig config = new MapKeyConfig();
		var draft = config.copyBindings();
		draft.put(SAVE, new KeyBinding(KeyEvent.VK_F12, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
		draft.put(PLAY_IN_EDITOR_SPIN, KeyBinding.NONE);
		Path file = temporary.resolve("keys.cfg");
		MapKeyBindingsConfig.save(file.toFile(), draft);
		assertFalse(Files.readString(file).contains("MOVE_FORWARD"), "Unchanged defaults should not be written");
		MapKeyConfig restored = new MapKeyConfig();
		MapKeyBindingsConfig.load(file.toFile(), restored);
		assertEquals(draft, restored.copyBindings());
	}

	@Test
	void invalidEntriesAndDuplicatesDoNotPreventLoadingValidOverrides() throws IOException
	{
		Path file = temporary.resolve("keys.cfg");
		Files.writeString(file, "% Comment\nINVALID = pressed X\nSAVE = nonsense\nUNDO = released Z\n"
			+ "REDO = ctrl pressed Z\nMOVE_FORWARD = pressed F12\nPLAY_IN_EDITOR_JUMP = pressed CONTROL\n");
		MapKeyConfig config = new MapKeyConfig();
		MapKeyBindingsConfig.load(file.toFile(), config);
		assertEquals(UNDO, config.resolve(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK, false));
		assertEquals(KeyBinding.NONE, config.getBinding(REDO));
		assertEquals(SAVE.getDefaultBinding(), config.getBinding(SAVE));
		assertEquals(PLAY_IN_EDITOR_JUMP.getDefaultBinding(), config.getBinding(PLAY_IN_EDITOR_JUMP));
		assertEquals(MOVE_FORWARD, config.resolve(KeyEvent.VK_F12, 0, false));
	}

	@Test
	void failedSaveLeavesBindingsAndExistingFilesUntouched() throws IOException
	{
		MapKeyConfig config = new MapKeyConfig();
		Path file = temporary.resolve("keys.cfg");
		Files.writeString(file, "original");
		var draft = config.copyBindings();
		draft.put(SAVE, config.getBinding(UNDO));
		assertThrows(IllegalArgumentException.class, () -> MapKeyBindingsConfig.save(file.toFile(), draft));
		assertEquals("original", Files.readString(file));
		assertThrows(IOException.class, () -> MapKeyBindingsConfig.save(temporary.resolve("missing/keys.cfg").toFile(), config.copyBindings()));
		assertEquals(SAVE.getDefaultBinding(), config.getBinding(SAVE));
	}
}
