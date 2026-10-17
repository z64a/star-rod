package common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-editor keyboard bindings, published together so input sees a complete configuration. */
public class KeyboardInputConfig
{
	private final Map<KeyInput, KeyBinding> defaults = new LinkedHashMap<>();
	private volatile Map<KeyInput, KeyBinding> bindings = Map.of();
	private final List<Runnable> listeners = new ArrayList<>();

	protected final void addDefault(KeyInput input)
	{
		defaults.put(input, input.getDefaultBinding());
	}

	protected final void finishDefaults()
	{
		setBindings(defaults);
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public KeyBinding getBinding(KeyInput input)
	{
		return bindings.getOrDefault(input, KeyBinding.NONE);
	}

	public KeyBinding getDefaultBinding(KeyInput input)
	{
		return defaults.getOrDefault(input, KeyBinding.NONE);
	}

	public String getBindingText(KeyInput input)
	{
		return getBinding(input).getDisplayText();
	}

	public boolean isUserBindable(KeyInput input)
	{
		return defaults.containsKey(input);
	}

	public List<KeyInput> getUserBindableInputs()
	{
		return new ArrayList<>(defaults.keySet());
	}

	public Map<KeyInput, KeyBinding> copyBindings()
	{
		return new LinkedHashMap<>(bindings);
	}

	public KeyInput findConflict(Map<KeyInput, KeyBinding> candidate, KeyInput target, KeyBinding binding)
	{
		if (!binding.isBound())
			return null;
		for (KeyInput input : defaults.keySet())
			if (input != target && binding.equals(candidate.get(input)))
				return input;
		return null;
	}

	public String validateBinding(KeyInput input, KeyBinding binding)
	{
		if (binding.keyCode == java.awt.event.KeyEvent.VK_ALT_GRAPH)
			return "Alt Graph cannot be assigned as a shortcut.";
		if (KeyBinding.isModifierKey(binding.keyCode) && !input.allowsModifierKey())
			return "Use a key or a key combination for this action.";
		return null;
	}

	public void setBindings(Map<KeyInput, KeyBinding> candidate)
	{
		Map<KeyInput, KeyBinding> updated = new LinkedHashMap<>(defaults);
		for (KeyInput input : defaults.keySet()) {
			KeyBinding binding = candidate.getOrDefault(input, defaults.get(input));
			String error = validateBinding(input, binding);
			if (error != null)
				throw new IllegalArgumentException(error);
			updated.put(input, binding);
		}
		for (KeyInput input : defaults.keySet()) {
			KeyInput conflict = findConflict(updated, input, updated.get(input));
			if (conflict != null)
				throw new IllegalArgumentException("Duplicate binding for " + input + " and " + conflict);
		}
		bindings = Map.copyOf(updated);
		for (Runnable listener : listeners)
			listener.run();
	}

	public void resetAll()
	{
		setBindings(defaults);
	}

	public KeyInput resolve(int keyCode, int modifiers)
	{
		KeyBinding key = new KeyBinding(keyCode, modifiers);
		if (!key.isBound())
			return null;
		for (var entry : bindings.entrySet())
			if (key.equals(entry.getValue()))
				return entry.getKey();
		return null;
	}
}
