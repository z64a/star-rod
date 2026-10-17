package game.map.editor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import common.KeyboardInput.KeyInputEvent;
import common.KeyboardInput.KeyboardInputListener;

/** Releases the action chosen on key-down, even when modifiers or bindings have since changed. */
public final class MapKeyInput implements KeyboardInputListener
{
	private final MapKeyConfig config;
	private final BooleanSupplier playInEditor;
	private final BiConsumer<EditorShortcut, Boolean> handler;
	private final Map<Integer, EditorShortcut> pressed = new HashMap<>();

	public MapKeyInput(MapKeyConfig config, BooleanSupplier playInEditor, BiConsumer<EditorShortcut, Boolean> handler)
	{
		this.config = config;
		this.playInEditor = playInEditor;
		this.handler = handler;
	}

	@Override
	public void keyPress(KeyInputEvent event)
	{
		EditorShortcut input = config.resolve(event.code, event.modifiers, playInEditor.getAsBoolean());
		if (input != null && !pressed.containsKey(event.code)) {
			pressed.put(event.code, input);
			handler.accept(input, true);
		}
	}

	@Override
	public void keyRelease(KeyInputEvent event)
	{
		EditorShortcut input = pressed.remove(event.code);
		if (input != null)
			handler.accept(input, false);
	}
}
