package game.message;

import static game.message.StringConstants.*;
import static game.message.StringConstants.ControlCharacter.STYLE;

import java.nio.ByteBuffer;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.StarRodException;
import app.input.IOUtils;
import game.message.StringConstants.ControlCharacter;
import game.message.StringConstants.SpecialCharacter;
import game.message.StringConstants.StringEffect;
import game.message.StringConstants.StringFont;
import game.message.StringConstants.StringFunction;
import game.message.StringConstants.StringStyle;
import game.message.StringConstants.StringVoice;
import util.DualHashMap;

public class StringDecoder
{
	// patterns for smart choices
	private static final Matcher CancelMatcher = Pattern.compile("\\[SetCancel (\\d+)\\]").matcher("");

	private static class DecodedMessageBuilder
	{
		private final StringBuilder text;
		private final StringBuilder markup;
		private final Stack<StringEffect> effects;

		private boolean choiceMessage = false;

		// decoding state
		private boolean creditsEncoding = false;

		private DecodedMessageBuilder(boolean plaintext)
		{
			effects = new Stack<>();
			text = new StringBuilder();
			if (plaintext)
				markup = new StringBuilder();
			else
				markup = text;
		}

		@Override
		public String toString()
		{
			return text.toString();
		}

		public DecodedMessageBuilder appendText(String s)
		{
			text.append(s);
			return this;
		}

		public DecodedMessageBuilder appendMarkup(String s)
		{
			markup.append(s);
			return this;
		}

		public DecodedMessageBuilder appendMarkup(String fmt, Object ... args)
		{
			markup.append(String.format(fmt, args));
			return this;
		}

		public DecodedMessageBuilder appendMarkup(char c)
		{
			markup.append(c);
			return this;
		}
	}

	public static String toASCII(byte[] message)
	{
		DecodedMessageBuilder msg = new DecodedMessageBuilder(true);
		ByteBuffer buffer = IOUtils.getDirectBuffer(message);
		byte b = 0x00;

		while (buffer.hasRemaining() && b != (byte) 0xFD) {
			b = buffer.get();
			if (ControlCharacter.decodeMap.containsKey(b)) {
				ControlCharacter tag = ControlCharacter.decodeMap.get(b);
				switch (tag) {
					case ENDL:
						msg.appendText(" ");
						break;
					case PAUSE:
						decodePause(msg, buffer);
						break;
					case STYLE:
						decodeStyle(msg, buffer);
						break;
					case FUNC:
						decodeFunction(msg, buffer);
						break;
					default:
						appendTag(msg, tag.name);
						break;
				}
			}
			else if (!msg.creditsEncoding && SpecialCharacter.decodeMap.containsKey(b))
				appendTag(msg, SpecialCharacter.decodeMap.get(b).name);
			else
				decodeChar(msg, b);
		}

		return msg
			.toString()
			.replaceAll("\\s+", " ") // squash contiguous spaces into one
			.replaceAll("$\\s+", ""); // strip leading spaces
	}

	public static String toMarkup(byte[] message)
	{
		DecodedMessageBuilder msg = new DecodedMessageBuilder(false);

		ByteBuffer buffer = IOUtils.getDirectBuffer(message);
		byte b = 0x00;

		while (buffer.hasRemaining() && b != (byte) ControlCharacter.END.code) {
			b = buffer.get();

			if (ControlCharacter.decodeMap.containsKey(b)) {
				ControlCharacter tag = ControlCharacter.decodeMap.get(b);
				switch (tag) {
					case ENDL:
						appendTag(msg, tag.name);
						msg.appendText(System.lineSeparator());
						break;
					case END:
						appendTag(msg, tag.name);
						msg.appendText(System.lineSeparator());
						break;
					case NEXT:
						appendTag(msg, tag.name);
						msg.appendText(System.lineSeparator());
						break;
					case STYLE:
						decodeStyle(msg, buffer);
						msg.appendText(System.lineSeparator());
						break;
					case PAUSE:
						decodePause(msg, buffer);
						if (msg.text.length() == 0 || msg.text.charAt(msg.text.length() - 1) == '\n')
							msg.appendText(System.lineSeparator());
						break;
					case FUNC:
						decodeFunction(msg, buffer);
						break;
					case WAIT:
					case VARIANT0:
					case VARIANT1:
					case VARIANT2:
					case VARIANT3:
						appendTag(msg, tag.name);
						break;
				}
			}
			else if (!msg.creditsEncoding && SpecialCharacter.decodeMap.containsKey(b))
				appendTag(msg, SpecialCharacter.decodeMap.get(b).name);
			else
				decodeChar(msg, b);
		}

		//auto-choices
		String text = msg.toString();
		if (msg.choiceMessage) {
			int cancelOption = Integer.MAX_VALUE;
			CancelMatcher.reset(text);
			while (CancelMatcher.find())
				cancelOption = (byte) Integer.parseInt(CancelMatcher.group(1));

			text = text.replaceFirst("\\[DelayOff\\]", "[StartChoice]" + System.lineSeparator());
			text = text.replaceAll("\\[(DelayOn|Cursor \\d+|SetCancel \\d+|Option 255)\\]", "");

			if (cancelOption == Integer.MAX_VALUE)
				text = text.replaceAll("\\[EndChoice \\d+\\]", "[EndChoice]");
			else
				text = text.replaceAll("\\[EndChoice \\d+\\]", "[EndChoice cancel=" + cancelOption + "]");
		}

		// auto button colors
		text = text.replaceAll("(?i)\\[SaveColor\\]\\[Color 0x10\\]\\[~A\\]\\[RestoreColor\\]", "[A]");
		text = text.replaceAll("(?i)\\[SaveColor\\]\\[Color 0x11\\]\\[~B\\]\\[RestoreColor\\]", "[B]");
		text = text.replaceAll("(?i)\\[SaveColor\\]\\[Color 0x12\\]\\[~START\\]\\[RestoreColor\\]", "[START]");
		text = text.replaceAll("(?i)\\[SaveColor\\]\\[Color 0x13\\]\\[~C-(up|down|left|right)\\]\\[RestoreColor\\]", "[C-$1]");
		text = text.replaceAll("(?i)\\[SaveColor\\]\\[Color 0x14\\]\\[~Z\\]\\[RestoreColor\\]", "[Z]");
		return text.replaceAll("(?i)\\[SaveColor\\]\\[Color 0x15\\]\\[~(L|R)\\]\\[RestoreColor\\]", "[$1]");
	}

	private static void decodeChar(DecodedMessageBuilder msg, byte b)
	{
		DualHashMap<Byte, Character> map = msg.creditsEncoding ? creditsCharacterMap : characterMap;

		if (map.contains(b)) {
			char c = map.get(b);
			switch (c) {
				case '%':
				case '[':
				case ']':
				case '{':
				case '}':
				case '\\':
					msg.text.append(ESCAPE);
			}
			msg.text.append(c);
		}
		else
			throw new StarRodException("Could not decode byte: %02X" + b);
	}

	private static DecodedMessageBuilder appendTag(DecodedMessageBuilder msg, String s)
	{
		msg.appendMarkup(OPEN_TAG);
		msg.appendMarkup(s);
		msg.appendMarkup(CLOSE_TAG);
		return msg;
	}

	private static DecodedMessageBuilder decodePause(DecodedMessageBuilder msg, ByteBuffer buffer)
	{
		msg.appendMarkup(OPEN_TAG);
		msg.appendMarkup(ControlCharacter.PAUSE.name);
		msg.appendMarkup(" %d", buffer.get());
		msg.appendMarkup(CLOSE_TAG);
		return msg;
	}

	private static DecodedMessageBuilder decodeStyle(DecodedMessageBuilder msg, ByteBuffer buffer)
	{
		byte b = buffer.get();
		StringStyle style = StringStyle.decodeMap.get(b);

		if (style == null)
			throw new StarRodException("Unknown style: %02X", b);

		msg.appendMarkup(OPEN_TAG);
		msg.appendMarkup(STYLE.name);
		msg.appendMarkup(" ");
		msg.appendMarkup(style.name);

		switch (style) {
			case CHOICE:
				msg.choiceMessage = true;
			case UPGRADE:
				int posX = (buffer.get() & 0xFF);
				int posY = (buffer.get() & 0xFF);
				int sizeX = (buffer.get() & 0xFF);
				int sizeY = (buffer.get() & 0xFF);
				msg.appendMarkup(" pos=%d,%d size=%d,%d", posX, posY, sizeX, sizeY);
				break;

			case LAMPPOST:
				int height = (buffer.get() & 0xFF);
				msg.appendMarkup(" height=%d", height);
				break;

			case POSTCARD:
				int index = (buffer.get() & 0xFF);
				msg.appendMarkup(" index=%d", index);
				break;

			default:
		}

		msg.appendMarkup(CLOSE_TAG);

		return msg;
	}

	private static DecodedMessageBuilder decodeFunction(DecodedMessageBuilder msg, ByteBuffer buffer)
	{
		byte b = buffer.get();

		if (StringFunction.decodeMap.containsKey(b)) {
			StringFunction func = StringFunction.decodeMap.get(b);

			if (func == StringFunction.ANIM_DELAY) {
				buffer.position(buffer.position() - 2);
				msg.appendText(System.lineSeparator());
				msg.appendMarkup(OPEN_FUNC);
				msg.appendMarkup((new MessageAnim(buffer)).getTag());
				msg.appendMarkup(CLOSE_FUNC);
				msg.appendText(System.lineSeparator());
				return msg;
			}

			msg.appendMarkup(OPEN_FUNC);

			switch (func) {
				case START_FX:
					decodeEffect(msg, buffer, true);
					break;
				case END_FX:
					decodeEffect(msg, buffer, false);
					break;

				case COLOR:
					msg.appendMarkup(func.name);
					for (int i = 0; i < func.args; i++)
						msg.appendMarkup(" 0x%02X", buffer.get());
					break;

				case SIZE:
					msg.appendMarkup(func.name);
					int sizeX = buffer.get() & 0xFF;
					int sizeY = buffer.get() & 0xFF;
					if (sizeX == sizeY)
						msg.appendMarkup(" %d", sizeX);
					else
						msg.appendMarkup(" %d,%d", sizeX, sizeY);
					break;

				case SPEED:
					msg.appendMarkup(func.name);
					msg.appendMarkup(" delay=%d chars=%d", buffer.get(), buffer.get());
					break;

				case SET_X:
					msg.appendMarkup(func.name);
					msg.appendMarkup(" %d", buffer.getShort() & 0xFFFF);
					break;

				case SETVOICE:
					msg.appendMarkup(func.name);
					msg.appendMarkup(" soundIDs=%08X,%08X", buffer.getInt(), buffer.getInt());
					break;

				case FONT:
					msg.appendMarkup(func.name);
					decodeFont(msg, buffer);
					break;

				case VOICE:
					msg.appendMarkup(func.name);
					decodeVoice(msg, buffer);
					break;

				case VOLUME:
					msg.appendMarkup(func.name);
					msg.appendMarkup(" percent=%d", buffer.get());
					break;

				case ANIM_SPRITE:
				case ANIM_DELAY:
				case ANIM_LOOP:
				case ANIM_DONE:
					msg.appendMarkup(func.name);
					for (int i = 0; i < func.args; i++)
						msg.appendMarkup(":02X", buffer.get() & 0xFF);
					break;

				case ITEM_ICON:
					msg.appendMarkup(func.name);
					msg.appendMarkup(" itemID=0x%X", buffer.getShort() & 0xFFFF);
					break;

				case INLINE_IMAGE:
					msg.appendMarkup(func.name);
					msg.appendMarkup(" index=%d", buffer.get() & 0xFF);
					break;

				case IMAGE:
					msg.appendMarkup(func.name);
					msg.appendMarkup(" index=%d pos=%d,%d hasBorder=%d alpha=%d fadeAmount=%d",
						buffer.get() & 0xFF,
						buffer.getShort() & 0xFFFF,
						buffer.get() & 0xFF,
						buffer.get() & 0xFF,
						buffer.get() & 0xFF,
						buffer.get() & 0xFF);
					break;

				case HIDE_IMAGE:
					msg.appendMarkup(func.name);
					msg.appendMarkup(" fadeAmount=%d", buffer.get() & 0xFF);
					break;

				case SET_REWIND:
					int v = buffer.get();
					if (v == 0)
						msg.appendMarkup("RewindOff");
					else
						msg.appendMarkup("RewindOn");
					break;

				default:
					msg.appendMarkup(func.name);
					for (int i = 0; i < func.args; i++)
						msg.appendMarkup(" %d", buffer.get() & 0xFF);
					break;
			}

			msg.appendMarkup(CLOSE_FUNC);
		}
		else
			throw new StarRodException("Unknown function type: %02X", b);

		return msg;
	}

	private static DecodedMessageBuilder decodeEffect(DecodedMessageBuilder msg, ByteBuffer buffer, boolean starting)
	{
		byte b = buffer.get();

		if (StringEffect.decodeMap.containsKey(b)) {
			StringEffect type = StringEffect.decodeMap.get(b);

			if (starting) {
				msg.appendMarkup(type.name);
				switch (type) {
					case DITHER_FADE:
						msg.appendMarkup(String.format(" %d", buffer.get() & 0xFF));
						break;
					case STATIC:
						msg.appendMarkup(String.format(" %d", buffer.get() & 0xFF));
						break;
					case BLUR:
						msg.appendMarkup(" dir=");
						switch (buffer.get()) {
							case 0:
								msg.appendMarkup("x");
								break;
							case 1:
								msg.appendMarkup("y");
								break;
							case 2:
								msg.appendMarkup("xy");
								break;
						}
						break;
					default:
				}

				msg.effects.push(type);
			}
			else {
				if (!msg.effects.isEmpty() && msg.effects.peek() == type) {
					msg.effects.pop();
					msg.appendMarkup(StringConstants.END_EFFECT_WILDCARD);
				}
				else {
					msg.effects.clear();
					msg.appendMarkup(StringConstants.END_EFFECT_TAG);
					msg.appendMarkup(type.name);
				}
			}
		}
		else
			throw new StarRodException("Unknown effect type: %02X", b);

		return msg;
	}

	private static DecodedMessageBuilder decodeFont(DecodedMessageBuilder msg, ByteBuffer buffer)
	{
		msg.appendMarkup(" ");
		byte b = buffer.get();

		if (StringFont.decodeMap.containsKey(b)) {
			StringFont type = StringFont.decodeMap.get(b);
			msg.appendMarkup(type.name);
			msg.creditsEncoding = (type != StringFont.NORMAL);
		}
		else
			throw new StarRodException("Unknown font style: %02X ", b);

		return msg;
	}

	private static DecodedMessageBuilder decodeVoice(DecodedMessageBuilder msg, ByteBuffer buffer)
	{
		msg.appendMarkup(" ");
		byte b = buffer.get();

		if (StringVoice.decodeMap.containsKey(b)) {
			StringVoice type = StringVoice.decodeMap.get(b);
			msg.appendMarkup(type.name);
		}
		else
			throw new StarRodException("Unknown voice: %02X ", b);

		return msg;
	}
}
