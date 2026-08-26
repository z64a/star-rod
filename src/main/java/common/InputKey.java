package common;

import java.awt.event.KeyEvent;
import java.util.HashMap;

import util.Logger;

public enum InputKey
{
	CAMERA_W (KeyEvent.VK_W),
	CAMERA_A (KeyEvent.VK_A),
	CAMERA_S (KeyEvent.VK_S),
	CAMERA_D (KeyEvent.VK_D);

	private int event;

	private InputKey(int event)
	{
		this.event = event;
	}

	public int get()
	{
		return event;
	}

	public void bind(int event)
	{
		this.event = event;
	}

	public static void rebind(String name, String event)
	{
		if (!InputKeyMap.containsKey(name)) {
			Logger.logError("Could not bind unknown Star Rod key: " + event);
			return;
		}

		if (!KeyEventMap.containsKey(event)) {
			Logger.logError("Could not bind " + name + " to unknown Java key: " + event);
			return;
		}

		InputKeyMap.get(name).event = KeyEventMap.get(event);
	}

	private static final HashMap<String, InputKey> InputKeyMap;
	private static final HashMap<String, Integer> KeyEventMap;

	static {
		InputKeyMap = new HashMap<>();
		for (InputKey key : InputKey.values())
			InputKeyMap.put(key.name(), key);

		KeyEventMap = new HashMap<>();
		KeyEventMap.put("VK_ENTER", KeyEvent.VK_ENTER);
		KeyEventMap.put("VK_BACK_SPACE", KeyEvent.VK_BACK_SPACE);
		KeyEventMap.put("VK_TAB", KeyEvent.VK_TAB);
		KeyEventMap.put("VK_CANCEL", KeyEvent.VK_CANCEL);
		KeyEventMap.put("VK_CLEAR", KeyEvent.VK_CLEAR);
		KeyEventMap.put("VK_SHIFT", KeyEvent.VK_SHIFT);
		KeyEventMap.put("VK_CONTROL", KeyEvent.VK_CONTROL);
		KeyEventMap.put("VK_ALT", KeyEvent.VK_ALT);
		KeyEventMap.put("VK_PAUSE", KeyEvent.VK_PAUSE);
		KeyEventMap.put("VK_CAPS_LOCK", KeyEvent.VK_CAPS_LOCK);
		KeyEventMap.put("VK_ESCAPE", KeyEvent.VK_ESCAPE);
		KeyEventMap.put("VK_SPACE", KeyEvent.VK_SPACE);
		KeyEventMap.put("VK_PAGE_UP", KeyEvent.VK_PAGE_UP);
		KeyEventMap.put("VK_PAGE_DOWN", KeyEvent.VK_PAGE_DOWN);
		KeyEventMap.put("VK_END", KeyEvent.VK_END);
		KeyEventMap.put("VK_HOME", KeyEvent.VK_HOME);
		KeyEventMap.put("VK_LEFT", KeyEvent.VK_LEFT);
		KeyEventMap.put("VK_UP", KeyEvent.VK_UP);
		KeyEventMap.put("VK_RIGHT", KeyEvent.VK_RIGHT);
		KeyEventMap.put("VK_DOWN", KeyEvent.VK_DOWN);
		KeyEventMap.put("VK_COMMA", KeyEvent.VK_COMMA);
		KeyEventMap.put("VK_MINUS", KeyEvent.VK_MINUS);
		KeyEventMap.put("VK_PERIOD", KeyEvent.VK_PERIOD);
		KeyEventMap.put("VK_SLASH", KeyEvent.VK_SLASH);
		KeyEventMap.put("VK_0", KeyEvent.VK_0);
		KeyEventMap.put("VK_1", KeyEvent.VK_1);
		KeyEventMap.put("VK_2", KeyEvent.VK_2);
		KeyEventMap.put("VK_3", KeyEvent.VK_3);
		KeyEventMap.put("VK_4", KeyEvent.VK_4);
		KeyEventMap.put("VK_5", KeyEvent.VK_5);
		KeyEventMap.put("VK_6", KeyEvent.VK_6);
		KeyEventMap.put("VK_7", KeyEvent.VK_7);
		KeyEventMap.put("VK_8", KeyEvent.VK_8);
		KeyEventMap.put("VK_9", KeyEvent.VK_9);
		KeyEventMap.put("VK_SEMICOLON", KeyEvent.VK_SEMICOLON);
		KeyEventMap.put("VK_EQUALS", KeyEvent.VK_EQUALS);
		KeyEventMap.put("VK_A", KeyEvent.VK_A);
		KeyEventMap.put("VK_B", KeyEvent.VK_B);
		KeyEventMap.put("VK_C", KeyEvent.VK_C);
		KeyEventMap.put("VK_D", KeyEvent.VK_D);
		KeyEventMap.put("VK_E", KeyEvent.VK_E);
		KeyEventMap.put("VK_F", KeyEvent.VK_F);
		KeyEventMap.put("VK_G", KeyEvent.VK_G);
		KeyEventMap.put("VK_H", KeyEvent.VK_H);
		KeyEventMap.put("VK_I", KeyEvent.VK_I);
		KeyEventMap.put("VK_J", KeyEvent.VK_J);
		KeyEventMap.put("VK_K", KeyEvent.VK_K);
		KeyEventMap.put("VK_L", KeyEvent.VK_L);
		KeyEventMap.put("VK_M", KeyEvent.VK_M);
		KeyEventMap.put("VK_N", KeyEvent.VK_N);
		KeyEventMap.put("VK_O", KeyEvent.VK_O);
		KeyEventMap.put("VK_P", KeyEvent.VK_P);
		KeyEventMap.put("VK_Q", KeyEvent.VK_Q);
		KeyEventMap.put("VK_R", KeyEvent.VK_R);
		KeyEventMap.put("VK_S", KeyEvent.VK_S);
		KeyEventMap.put("VK_T", KeyEvent.VK_T);
		KeyEventMap.put("VK_U", KeyEvent.VK_U);
		KeyEventMap.put("VK_V", KeyEvent.VK_V);
		KeyEventMap.put("VK_W", KeyEvent.VK_W);
		KeyEventMap.put("VK_X", KeyEvent.VK_X);
		KeyEventMap.put("VK_Y", KeyEvent.VK_Y);
		KeyEventMap.put("VK_Z", KeyEvent.VK_Z);
		KeyEventMap.put("VK_OPEN_BRACKET", KeyEvent.VK_OPEN_BRACKET);
		KeyEventMap.put("VK_BACK_SLASH", KeyEvent.VK_BACK_SLASH);
		KeyEventMap.put("VK_CLOSE_BRACKET", KeyEvent.VK_CLOSE_BRACKET);
		KeyEventMap.put("VK_NUMPAD0", KeyEvent.VK_NUMPAD0);
		KeyEventMap.put("VK_NUMPAD1", KeyEvent.VK_NUMPAD1);
		KeyEventMap.put("VK_NUMPAD2", KeyEvent.VK_NUMPAD2);
		KeyEventMap.put("VK_NUMPAD3", KeyEvent.VK_NUMPAD3);
		KeyEventMap.put("VK_NUMPAD4", KeyEvent.VK_NUMPAD4);
		KeyEventMap.put("VK_NUMPAD5", KeyEvent.VK_NUMPAD5);
		KeyEventMap.put("VK_NUMPAD6", KeyEvent.VK_NUMPAD6);
		KeyEventMap.put("VK_NUMPAD7", KeyEvent.VK_NUMPAD7);
		KeyEventMap.put("VK_NUMPAD8", KeyEvent.VK_NUMPAD8);
		KeyEventMap.put("VK_NUMPAD9", KeyEvent.VK_NUMPAD9);
		KeyEventMap.put("VK_MULTIPLY", KeyEvent.VK_MULTIPLY);
		KeyEventMap.put("VK_ADD", KeyEvent.VK_ADD);
		KeyEventMap.put("VK_SEPARATER", KeyEvent.VK_SEPARATER);
		KeyEventMap.put("VK_SUBTRACT", KeyEvent.VK_SUBTRACT);
		KeyEventMap.put("VK_DECIMAL", KeyEvent.VK_DECIMAL);
		KeyEventMap.put("VK_DIVIDE", KeyEvent.VK_DIVIDE);
		KeyEventMap.put("VK_DELETE", KeyEvent.VK_DELETE);
		KeyEventMap.put("VK_NUM_LOCK", KeyEvent.VK_NUM_LOCK);
		KeyEventMap.put("VK_SCROLL_LOCK", KeyEvent.VK_SCROLL_LOCK);
		KeyEventMap.put("VK_F1", KeyEvent.VK_F1);
		KeyEventMap.put("VK_F2", KeyEvent.VK_F2);
		KeyEventMap.put("VK_F3", KeyEvent.VK_F3);
		KeyEventMap.put("VK_F4", KeyEvent.VK_F4);
		KeyEventMap.put("VK_F5", KeyEvent.VK_F5);
		KeyEventMap.put("VK_F6", KeyEvent.VK_F6);
		KeyEventMap.put("VK_F7", KeyEvent.VK_F7);
		KeyEventMap.put("VK_F8", KeyEvent.VK_F8);
		KeyEventMap.put("VK_F9", KeyEvent.VK_F9);
		KeyEventMap.put("VK_F10", KeyEvent.VK_F10);
		KeyEventMap.put("VK_F11", KeyEvent.VK_F11);
		KeyEventMap.put("VK_F12", KeyEvent.VK_F12);
		KeyEventMap.put("VK_F13", KeyEvent.VK_F13);
		KeyEventMap.put("VK_F14", KeyEvent.VK_F14);
		KeyEventMap.put("VK_F15", KeyEvent.VK_F15);
		KeyEventMap.put("VK_F16", KeyEvent.VK_F16);
		KeyEventMap.put("VK_F17", KeyEvent.VK_F17);
		KeyEventMap.put("VK_F18", KeyEvent.VK_F18);
		KeyEventMap.put("VK_F19", KeyEvent.VK_F19);
		KeyEventMap.put("VK_F20", KeyEvent.VK_F20);
		KeyEventMap.put("VK_F21", KeyEvent.VK_F21);
		KeyEventMap.put("VK_F22", KeyEvent.VK_F22);
		KeyEventMap.put("VK_F23", KeyEvent.VK_F23);
		KeyEventMap.put("VK_F24", KeyEvent.VK_F24);
		KeyEventMap.put("VK_PRINTSCREEN", KeyEvent.VK_PRINTSCREEN);
		KeyEventMap.put("VK_INSERT", KeyEvent.VK_INSERT);
		KeyEventMap.put("VK_HELP", KeyEvent.VK_HELP);
		KeyEventMap.put("VK_META", KeyEvent.VK_META);
		KeyEventMap.put("VK_BACK_QUOTE", KeyEvent.VK_BACK_QUOTE);
		KeyEventMap.put("VK_QUOTE", KeyEvent.VK_QUOTE);
		KeyEventMap.put("VK_KP_UP", KeyEvent.VK_KP_UP);
		KeyEventMap.put("VK_KP_DOWN", KeyEvent.VK_KP_DOWN);
		KeyEventMap.put("VK_KP_LEFT", KeyEvent.VK_KP_LEFT);
		KeyEventMap.put("VK_KP_RIGHT", KeyEvent.VK_KP_RIGHT);
		KeyEventMap.put("VK_DEAD_GRAVE", KeyEvent.VK_DEAD_GRAVE);
		KeyEventMap.put("VK_DEAD_ACUTE", KeyEvent.VK_DEAD_ACUTE);
		KeyEventMap.put("VK_DEAD_CIRCUMFLEX", KeyEvent.VK_DEAD_CIRCUMFLEX);
		KeyEventMap.put("VK_DEAD_TILDE", KeyEvent.VK_DEAD_TILDE);
		KeyEventMap.put("VK_DEAD_MACRON", KeyEvent.VK_DEAD_MACRON);
		KeyEventMap.put("VK_DEAD_BREVE", KeyEvent.VK_DEAD_BREVE);
		KeyEventMap.put("VK_DEAD_ABOVEDOT", KeyEvent.VK_DEAD_ABOVEDOT);
		KeyEventMap.put("VK_DEAD_DIAERESIS", KeyEvent.VK_DEAD_DIAERESIS);
		KeyEventMap.put("VK_DEAD_ABOVERING", KeyEvent.VK_DEAD_ABOVERING);
		KeyEventMap.put("VK_DEAD_DOUBLEACUTE", KeyEvent.VK_DEAD_DOUBLEACUTE);
		KeyEventMap.put("VK_DEAD_CARON", KeyEvent.VK_DEAD_CARON);
		KeyEventMap.put("VK_DEAD_CEDILLA", KeyEvent.VK_DEAD_CEDILLA);
		KeyEventMap.put("VK_DEAD_OGONEK", KeyEvent.VK_DEAD_OGONEK);
		KeyEventMap.put("VK_DEAD_IOTA", KeyEvent.VK_DEAD_IOTA);
		KeyEventMap.put("VK_DEAD_VOICED_SOUND", KeyEvent.VK_DEAD_VOICED_SOUND);
		KeyEventMap.put("VK_DEAD_SEMIVOICED_SOUND", KeyEvent.VK_DEAD_SEMIVOICED_SOUND);
		KeyEventMap.put("VK_AMPERSAND", KeyEvent.VK_AMPERSAND);
		KeyEventMap.put("VK_ASTERISK", KeyEvent.VK_ASTERISK);
		KeyEventMap.put("VK_QUOTEDBL", KeyEvent.VK_QUOTEDBL);
		KeyEventMap.put("VK_LESS", KeyEvent.VK_LESS);
		KeyEventMap.put("VK_GREATER", KeyEvent.VK_GREATER);
		KeyEventMap.put("VK_BRACELEFT", KeyEvent.VK_BRACELEFT);
		KeyEventMap.put("VK_BRACERIGHT", KeyEvent.VK_BRACERIGHT);
		KeyEventMap.put("VK_AT", KeyEvent.VK_AT);
		KeyEventMap.put("VK_COLON", KeyEvent.VK_COLON);
		KeyEventMap.put("VK_CIRCUMFLEX", KeyEvent.VK_CIRCUMFLEX);
		KeyEventMap.put("VK_DOLLAR", KeyEvent.VK_DOLLAR);
		KeyEventMap.put("VK_EURO_SIGN", KeyEvent.VK_EURO_SIGN);
		KeyEventMap.put("VK_EXCLAMATION_MARK", KeyEvent.VK_EXCLAMATION_MARK);
		KeyEventMap.put("VK_INVERTED_EXCLAMATION_MARK", KeyEvent.VK_INVERTED_EXCLAMATION_MARK);
		KeyEventMap.put("VK_LEFT_PARENTHESIS", KeyEvent.VK_LEFT_PARENTHESIS);
		KeyEventMap.put("VK_NUMBER_SIGN", KeyEvent.VK_NUMBER_SIGN);
		KeyEventMap.put("VK_PLUS", KeyEvent.VK_PLUS);
		KeyEventMap.put("VK_RIGHT_PARENTHESIS", KeyEvent.VK_RIGHT_PARENTHESIS);
		KeyEventMap.put("VK_UNDERSCORE", KeyEvent.VK_UNDERSCORE);
	}
}
