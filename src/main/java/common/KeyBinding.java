package common;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

/**
 * A keyboard key and its required Ctrl, Shift, Alt, and Meta modifiers.
 */
public final class KeyBinding
{
	public static final KeyBinding NONE = new KeyBinding(KeyEvent.VK_UNDEFINED, 0);

	private static final int SUPPORTED_MODIFIERS = InputEvent.CTRL_DOWN_MASK
		| InputEvent.SHIFT_DOWN_MASK
		| InputEvent.ALT_DOWN_MASK
		| InputEvent.META_DOWN_MASK;

	public final int keyCode;
	public final int modifiers;

	public KeyBinding(int keyCode, int modifiers)
	{
		this.keyCode = keyCode;
		this.modifiers = normalizeModifiers(modifiers) & ~modifierMask(keyCode);
	}

	public boolean isBound()
	{
		return keyCode != KeyEvent.VK_UNDEFINED;
	}

	public KeyStroke toKeyStroke()
	{
		if (!isBound())
			return null;
		return KeyStroke.getKeyStroke(keyCode, modifiers);
	}

	public String getDisplayText()
	{
		if (!isBound())
			return "Unbound";

		String modifierText = InputEvent.getModifiersExText(modifiers);
		String keyText = KeyEvent.getKeyText(keyCode);
		if (modifierText.isEmpty())
			return keyText;
		return modifierText + "+" + keyText;
	}

	public String serialize()
	{
		if (!isBound())
			return "NONE";
		return toKeyStroke().toString();
	}

	public static KeyBinding parse(String text)
	{
		if (text == null || text.trim().isEmpty() || text.trim().equalsIgnoreCase("NONE"))
			return NONE;

		KeyStroke stroke = KeyStroke.getKeyStroke(text.trim());
		if (stroke == null || stroke.getKeyCode() == KeyEvent.VK_UNDEFINED || stroke.isOnKeyRelease())
			throw new IllegalArgumentException("Invalid key binding: " + text);

		return new KeyBinding(stroke.getKeyCode(), stroke.getModifiers());
	}

	public static int normalizeModifiers(int modifiers)
	{
		return modifiers & SUPPORTED_MODIFIERS;
	}

	public static int modifierMask(int keyCode)
	{
		return switch (keyCode) {
			case KeyEvent.VK_CONTROL -> InputEvent.CTRL_DOWN_MASK;
			case KeyEvent.VK_SHIFT -> InputEvent.SHIFT_DOWN_MASK;
			case KeyEvent.VK_ALT -> InputEvent.ALT_DOWN_MASK;
			case KeyEvent.VK_META -> InputEvent.META_DOWN_MASK;
			default -> 0;
		};
	}

	public static boolean isModifierKey(int keyCode)
	{
		switch (keyCode) {
			case KeyEvent.VK_CONTROL:
			case KeyEvent.VK_SHIFT:
			case KeyEvent.VK_ALT:
			case KeyEvent.VK_META:
			case KeyEvent.VK_ALT_GRAPH:
				return true;
			default:
				return false;
		}
	}

	@Override
	public int hashCode()
	{
		return 31 * keyCode + modifiers;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!(obj instanceof KeyBinding))
			return false;

		KeyBinding other = (KeyBinding) obj;
		return keyCode == other.keyCode && modifiers == other.modifiers;
	}

	@Override
	public String toString()
	{
		return getDisplayText();
	}
}
