package game.map.editor.common;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class KeyboardInput implements KeyListener
{
	public static class KeyInputEvent
	{
		public final int code;

		private KeyInputEvent(KeyEvent evt)
		{
			code = evt.getKeyCode();
		}

		public KeyInputEvent(int keyCode)
		{
			code = keyCode;
		}
	}

	public static interface KeyboardInputListener
	{
		public default void keyPress(KeyInputEvent evt)
		{}

		public default void keyRelease(KeyInputEvent evt)
		{}
	}

	private HashSet<Integer> isKeyDown = new HashSet<>();

	private BlockingQueue<KeyEvent> pressed = new LinkedBlockingQueue<>();
	private BlockingQueue<KeyEvent> released = new LinkedBlockingQueue<>();

	public KeyboardInput(Component comp)
	{
		comp.addKeyListener(this);
	}

	public void reset()
	{
		isKeyDown = new HashSet<>();
		pressed = new LinkedBlockingQueue<>();
		released = new LinkedBlockingQueue<>();
	}

	@Override
	public void keyTyped(KeyEvent e)
	{}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (!isKeyDown.contains(e.getKeyCode())) {
			pressed.add(e);
			isKeyDown.add(e.getKeyCode());
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if (isKeyDown.contains(e.getKeyCode())) {
			released.add(e);
			isKeyDown.remove(e.getKeyCode());
		}
	}

	public boolean isCtrlDown()
	{ return isKeyDown.contains(KeyEvent.VK_CONTROL); }

	public boolean isShiftDown()
	{ return isKeyDown.contains(KeyEvent.VK_SHIFT); }

	public boolean isAltDown()
	{ return isKeyDown.contains(KeyEvent.VK_ALT); }

	public boolean isKeyDown(int keycode)
	{
		return isKeyDown.contains(keycode);
	}

	public void update(KeyboardInputListener listener, boolean hasFocus)
	{
		if (hasFocus) {
			while (!pressed.isEmpty())
				listener.keyPress(new KeyInputEvent(pressed.poll()));

			while (!released.isEmpty())
				listener.keyRelease(new KeyInputEvent(released.poll()));
		}
		else if (!isKeyDown.isEmpty()) {
			reset(listener);
		}
	}

	public void reset(KeyboardInputListener listener)
	{
		for (int keyCode : isKeyDown)
			listener.keyRelease(new KeyInputEvent(keyCode));
		isKeyDown.clear();
		pressed.clear();
		released.clear();
	}
}
