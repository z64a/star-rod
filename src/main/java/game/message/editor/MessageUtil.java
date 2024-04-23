package game.message.editor;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import game.message.StringConstants.ControlCharacter;
import game.message.StringConstants.StringEffect;
import game.message.StringConstants.StringFunction;
import game.message.StringConstants.StringStyle;
import game.message.font.FontType;

public class MessageUtil
{
	private static class Line
	{
		int width = 0;
		int charCount = 0;
	}

	// based on func_80125F68 (msg_get_properties)
	public static StringProperties getStringProperties(ByteBuffer buffer)
	{
		//TODO there might be a limit of 32 lines for this

		FontType font = FontType.Normal;
		int fontVariant = 0;

		float stringScaleX = 1.0f;

		int charWidthOverride = 0;
		int spaceCount = 0;

		boolean emptyLine = true;
		ArrayList<ArrayList<Line>> pages = new ArrayList<>();

		ArrayList<Line> currentPage = new ArrayList<>();
		pages.add(currentPage);

		Line currentLine = new Line();

		read_buf:
		while (buffer.hasRemaining()) {
			byte charByte = buffer.get();
			int charInt = charByte & 0xFF;

			if (charInt < 0xF0 || charInt == 0xF7 || charInt == 0xF8 || charInt == 0xF9) {
				if (emptyLine)
					emptyLine = false;
				currentLine.width += getCharWidth(font, fontVariant, charInt, stringScaleX, charWidthOverride, 0);
				currentLine.charCount++;

				if (charInt == 0xF7 || charInt == 0xF8 || charInt == 0xF9)
					spaceCount++;
			}
			else {
				switch (ControlCharacter.decodeMap.get(charByte)) {
					case ENDL: // 0xF0
						currentPage.add(currentLine);
						currentLine = new Line();
						emptyLine = true;
						break;

					case WAIT: // 0xF1
					case NEXT: // 0xFB
						if (currentLine.charCount > 0)
							currentPage.add(currentLine);
						currentPage = new ArrayList<>();
						pages.add(currentPage);
						currentLine = new Line();
						break;

					case PAUSE: // 0xF2
						buffer.get();
						break;

					case VARIANT0: // 0xF3
					case VARIANT1: // 0xF4
					case VARIANT2: // 0xF5
					case VARIANT3: // 0xF6
						fontVariant = (charByte & 0xFF) - 0xF3;
						break;

					case END: // 0xFD
						break read_buf;

					case STYLE: // 0xFC
						switch (StringStyle.decodeMap.get(buffer.get())) {
							case LAMPPOST:
							case POSTCARD:
								buffer.get();
								break;

							case CHOICE:
							case UPGRADE:
								buffer.get();
								buffer.get();
								buffer.get();
								buffer.get();
								break;

							default:
								break;
						}
						break;

					// 0xFE = unused afaik
					case FUNC: // 0xFF
						StringFunction func = StringFunction.decodeMap.get(buffer.get());
						switch (func) {
							case FONT:
								int fontType = (buffer.get() & 0xFF);
								switch (fontType) {
									case 0:
										font = FontType.Normal;
										break;
									case 1:
										font = FontType.Menus;
										break;
									case 2:
										font = FontType.Menus;
										break;
									case 3:
										font = FontType.Title;
										break;
									case 4:
										font = FontType.Subtitle;
										break;
								}
								break;

							case SIZE:
								stringScaleX = (buffer.get() & 0xFF) / 16.0f;
								buffer.get();
								break;
							case SIZE_RESET:
								stringScaleX = 1.0f;
								break;

							case START_FX:
								byte fxID = buffer.get();
								StringEffect fx = StringEffect.decodeMap.get(fxID);
								for (int i = 0; i < fx.args; i++)
									buffer.get();
								break;
							case END_FX:
								buffer.get();
								break;

							default:
								for (int i = 0; i < func.args; i++)
									buffer.get(); // copy function args
						}
						break;
				}
			}
		}

		if (currentLine.charCount > 0)
			currentPage.add(currentLine);

		StringProperties properties = new StringProperties();
		int numLines = 0;
		for (ArrayList<Line> lines : pages) {
			int nonBlankLines = 0;
			for (Line line : lines) {
				numLines++;
				if (line.width > properties.maxLineWidth)
					properties.maxLineWidth = line.width;
				if (line.charCount > properties.maxLineCharCount)
					properties.maxLineCharCount = line.charCount;
				if (line.charCount > 0)
					nonBlankLines++;
			}

			if (nonBlankLines > properties.maxNonblankLinesPerPage)
				properties.maxNonblankLinesPerPage = nonBlankLines;
		}
		properties.height = numLines * font.lineHeight;
		properties.spaces = spaceCount;
		properties.numLines = numLines;
		/*
		System.out.println("PROPERITES:");
		System.out.println(properties.numLines);
		System.out.println(properties.maxLineWidth);
		System.out.println(properties.maxLineCharCount);
		System.out.println(properties.maxNonblankLinesPerPage);
		*/
		return properties;
	}

	public static class StringProperties
	{
		public int maxNonblankLinesPerPage;
		public int maxLineCharCount;
		public int maxLineWidth;
		public int height;
		public int numLines;
		public int spaces;
	}

	// based on func_80125DF4 (msg_get_char_width)
	public static int getCharWidth(FontType font, int subfont, int index, float stringScale, int width, int flags)
	{
		if (subfont >= font.numVariants || subfont < 0)
			subfont = 0;

		index = translate(index);
		if (index < 0xf8) {
			if (width == 0) {
				//ALSO: if(((flags & 0x100) == 0)
				if ((index == 0xf5) || (index == 0xf6) || (index == 0xf7))
					width = font.fullspaceWidth[subfont];
				else
					width = font.chars.widths[index];
			}

			if (index == 0xf5) // char F7
				return (int) ((width * stringScale) * 0.6);
			if (index == 0xf6) // char F8
				return (int) (width * stringScale);
			if (index == 0xf7) // char F9
				return (int) ((width * stringScale) * 0.5);
			if (index > 0xef)
				return 0; // other control chars
			return (int) (width * stringScale);
		}
		return 0;
	}

	private static int translate(int c)
	{
		switch (c) {
			//case 0xF1: // skip
			//case 0xF2: // skip, maybe FB
			case 0xF7:
				return 0xF5;
			case 0xF8:
				return 0xF6;
			case 0xF9:
				return 0xF7;
			case 0xFA:
				return 0xF9;
			case 0xFB:
				return 0xFA;
			// new FB character -> end of currently-printed
			case 0xFC:
				return 0xF8;
			case 0xFD:
				return 0xFB;
			//case 0xFF: // skip
			default:
				return c;
		}
	}
}
