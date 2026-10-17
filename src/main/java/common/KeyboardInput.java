package common;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeyboardInput implements KeyListener
{
	public static class KeyInputEvent
	{
		public final int code;
		public final int modifiers;

		private KeyInputEvent(KeyEvent event)
		{
			this(event.getKeyCode(), event.getModifiersEx());
		}

		public KeyInputEvent(int keyCode)
		{
			this(keyCode, 0);
		}

		public KeyInputEvent(int keyCode, int modifiers)
		{
			this.code = keyCode;
			this.modifiers = KeyBinding.normalizeModifiers(modifiers);
		}
	}

	public static interface KeyboardInputListener
	{
		public default void keyPress(KeyInputEvent evt)
		{}

		public default void keyRelease(KeyInputEvent evt)
		{}
	}

	private final Set<Integer> keysDown = new HashSet<>();
	private final Set<Integer> deliveredKeys = new HashSet<>();
	private final ArrayDeque<KeyEvent> events = new ArrayDeque<>();
	private final KeyboardInputConfig config;

	public KeyboardInput(Component comp)
	{
		this(comp, null);
	}

	public KeyboardInput(Component comp, KeyboardInputConfig config)
	{
		this.config = config;
		comp.addKeyListener(this);
	}

	public synchronized void reset()
	{
		keysDown.clear();
		deliveredKeys.clear();
		events.clear();
	}

	@Override
	public void keyTyped(KeyEvent e)
	{}

	@Override
	public synchronized void keyPressed(KeyEvent e)
	{
		if (keysDown.add(e.getKeyCode()))
			events.add(e);
	}

	@Override
	public synchronized void keyReleased(KeyEvent e)
	{
		if (keysDown.remove(e.getKeyCode()))
			events.add(e);
	}

	public boolean isCtrlDown()
	{
		return isKeyDown(KeyEvent.VK_CONTROL);
	}

	public boolean isShiftDown()
	{
		return isKeyDown(KeyEvent.VK_SHIFT);
	}

	public boolean isAltDown()
	{
		return isKeyDown(KeyEvent.VK_ALT);
	}

	public synchronized boolean isKeyDown(int keycode)
	{
		return keysDown.contains(keycode);
	}

	public synchronized boolean isDown(KeyInput input)
	{
		KeyBinding binding = config == null ? input.getDefaultBinding() : config.getBinding(input);
		if (!binding.isBound() || !keysDown.contains(binding.keyCode))
			return false;
		int modifiers = 0;
		if (isCtrlDown())
			modifiers |= InputEvent.CTRL_DOWN_MASK;
		if (isShiftDown())
			modifiers |= InputEvent.SHIFT_DOWN_MASK;
		if (isAltDown())
			modifiers |= InputEvent.ALT_DOWN_MASK;
		if (isKeyDown(KeyEvent.VK_META))
			modifiers |= InputEvent.META_DOWN_MASK;
		if ((modifiers & binding.modifiers) != binding.modifiers)
			return false;
		// A more specific binding takes precedence over an unmodified held input.
		KeyInput exact = config == null ? null : config.resolve(binding.keyCode, modifiers);
		return exact == null || exact == input;
	}

	public void update(KeyboardInputListener listener, boolean hasFocus)
	{
		if (!hasFocus) {
			reset(listener);
			return;
		}
		List<KeyEvent> pending;
		synchronized (this) {
			pending = new ArrayList<>(events);
			events.clear();
		}
		for (KeyEvent event : pending) {
			KeyInputEvent input = new KeyInputEvent(event);
			if (event.getID() == KeyEvent.KEY_PRESSED) {
				synchronized (this) {
					deliveredKeys.add(input.code);
				}
				listener.keyPress(input);
			}
			else {
				synchronized (this) {
					deliveredKeys.remove(input.code);
				}
				listener.keyRelease(input);
			}
		}
	}

	public void reset(KeyboardInputListener listener)
	{
		Set<Integer> releases;
		synchronized (this) {
			releases = new HashSet<>(deliveredKeys);
			releases.addAll(keysDown);
			reset();
		}
		for (int keyCode : releases)
			listener.keyRelease(new KeyInputEvent(keyCode));
	}
}
