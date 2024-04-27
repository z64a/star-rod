package game.message.editor;

import static game.message.MessageBoxes.Graphic.Letter_BG;
import static game.message.MessageBoxes.WindowPart.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import game.message.MessageBoxes;
import game.message.MessageBoxes.WindowPalette;
import game.message.StringConstants.ControlCharacter;
import game.message.StringConstants.StringEffect;
import game.message.StringConstants.StringFunction;
import game.message.StringConstants.StringStyle;
import game.message.StringEncoder;
import game.message.editor.MessageUtil.StringProperties;
import game.message.editor.MessageTokenizer.Sequence;
import util.MathUtil;

public class MessagePrinter
{
	private MessageEditor editor;

	private final Animation anim = new Animation();

	public static final int MAX_LENGTH = 1024;
	public int compiledLength;

	public ArrayList<Sequence> sequences = new ArrayList<>();
	public ArrayList<Page> pages = new ArrayList<>();
	public Page currentPage = null;

	private ByteBuffer pageBuffer = ByteBuffer.allocate(0);
	public ByteBuffer drawBuffer = ByteBuffer.allocate(0);
	public int printPos; // position in the string

	private int pauseCounter;

	private float scrollTarget;
	public float scrollAmount;

	private int pageLines;

	public boolean donePrinting;

	public boolean hasPrintDelay;
	public int printChunkSize;
	public int printDelay;

	private int charsToPrint;
	private int printCounter;

	// window and style related fields

	public int stringWidth;
	private int stringNumLines;

	public StringStyle style;

	private int letterIndex;

	public int bubbleStraightWidth = 239;
	public int bubbleCurveWidth = 239;
	public int bubbleHeight = 239;

	public int windowBasePosX = 0;
	public int windowBasePosY = 0;

	public int windowTextStartX = 0;
	public int windowTextStartY = 0;

	public int windowSizeX = 296;
	public int windowSizeY = 68;

	public int rewindArrowX = 296;
	public int rewindArrowY = 68;

	// initial window position, typically from the screen space position of an NPC.
	// these example values are taken from the defaults [80125778] in init_printer
	public int openStartPosX = 160;
	public int openStartPosY = 40;

	public int clipMinX = 0;
	public int clipMinY = 0;
	public int clipMaxX = 319;
	public int clipMaxY = 239;

	private void setStyle(StringStyle style, ByteBuffer buffer)
	{
		this.style = style;

		windowBasePosX = 0;
		windowBasePosY = 0;

		windowTextStartX = 0;
		windowTextStartY = 0;

		windowSizeX = 296;
		windowSizeY = 68;

		clipMinX = 0;
		clipMinY = 0;
		clipMaxX = 319;
		clipMaxY = 239;

		switch (style) {
			case RIGHT: // 0x1
			case LEFT: // 0x2
			case CENTER: // 0x3
				windowSizeX = 296;
				windowSizeY = 68;
				windowBasePosX = 22;
				windowBasePosY = 13;
				windowTextStartX = 26;
				windowTextStartY = 6;

				clipMinX = 20;
				clipMaxX = 300;
				clipMinY = windowBasePosY + windowTextStartY;
				clipMaxY = clipMinY + windowSizeY - 16;

				rewindArrowX = 276;
				rewindArrowY = 57;

				bubbleStraightWidth = 218;
				bubbleCurveWidth = 32;
				bubbleHeight = 68;
				break;

			case TATTLE: // 0x4
				switch (stringNumLines) {
					case 1:
						bubbleCurveWidth = 24;
						windowTextStartX = 18;
						windowTextStartY = 10;
						break;
					case 2:
						bubbleCurveWidth = 28;
						windowTextStartX = 22;
						windowTextStartY = 6;
						break;
					case 3:
						bubbleCurveWidth = 32;
						windowTextStartX = 26;
						windowTextStartY = 6;
						break;
					default:
						bubbleCurveWidth = 32;
						windowTextStartX = 26;
						windowTextStartY = 8;
						break;
				}

				bubbleStraightWidth = MathUtil.clamp(stringWidth, 70, 256) - 12;
				windowSizeX = bubbleCurveWidth + bubbleStraightWidth + bubbleCurveWidth;
				windowSizeY = MathUtil.clamp(stringNumLines * 14 + 16, 36, 68);

				bubbleHeight = windowSizeY;

				windowBasePosX = openStartPosX - windowSizeX / 2;
				if (windowBasePosX < 18)
					windowBasePosX = 18;
				if (windowBasePosX + windowSizeX > 302)
					windowBasePosX = 302 - windowSizeX;

				windowBasePosY = openStartPosY - (windowSizeY + 38);
				if (windowBasePosY < 20)
					windowBasePosY = 20;
				if (windowBasePosY + windowSizeY > 170)
					windowBasePosY = 170 - windowSizeY;

				rewindArrowX = windowBasePosX + windowSizeX - 30;
				rewindArrowY = windowBasePosY + windowSizeY - 18;

				clipMinX = 20;
				clipMaxX = 300;
				clipMinY = windowBasePosY + windowTextStartY;
				clipMaxY = clipMinY + windowSizeY - 16;
				break;

			case CHOICE: // 0x5
				windowBasePosX = (buffer.get() & 0xFF);
				windowBasePosY = (buffer.get() & 0xFF);
				windowSizeX = (buffer.get() & 0xFF);
				windowSizeY = (buffer.get() & 0xFF);
				windowTextStartX = 12;
				windowTextStartY = 6;

				clipMinX = windowBasePosX + 2;
				clipMinY = windowBasePosY + 2;
				clipMaxX = windowBasePosX + windowSizeX - 2;
				clipMaxY = windowBasePosY + windowSizeY - 2;
				break;

			case SIGN: // 0x7
			case LAMPPOST: // 0x8
				windowBasePosX = 20;
				windowBasePosY = 28;

				windowTextStartX = 18;
				windowTextStartY = 11;

				windowSizeX = 280;
				if (style == StringStyle.SIGN)
					windowSizeY = 72;
				else
					windowSizeY = (buffer.get() & 0xFF);

				clipMinX = 34;
				clipMinY = 40;
				clipMaxX = 283;
				clipMaxY = windowSizeY + 17;

				rewindArrowX = clipMaxX - 16;
				rewindArrowY = clipMaxY - 9;
				break;

			case POSTCARD: // 0x9
				letterIndex = (buffer.get() & 0xFF);
				windowBasePosX = 40;
				windowBasePosY = 28;

				windowSizeX = 240;
				windowSizeY = 58;

				windowTextStartX = 12;
				windowTextStartY = 5;

				clipMinX = 45;
				clipMinY = 32;
				clipMaxX = 272;
				clipMaxY = 81;

				rewindArrowX = clipMaxX - 21;
				rewindArrowY = clipMaxY - 20;
				break;

			case POPUP: // 0xA
			case STYLE_B: // 0xB
				windowSizeX = stringWidth + 32;
				windowSizeY = 40;

				windowBasePosX = 160 - windowSizeX / 2;
				windowBasePosY = 56;

				windowTextStartX = 16;
				windowTextStartY = 4;

				clipMinX = 0;
				clipMinY = 0;
				clipMaxX = 319;
				clipMaxY = 239;
				break;

			case UPGRADE: // 0xC
				windowBasePosX = (buffer.get() & 0xFF);
				windowBasePosY = (buffer.get() & 0xFF);
				windowSizeX = (buffer.get() & 0xFF);
				windowSizeY = (buffer.get() & 0xFF);
				// intentional fall-through
			case INSPECT: // 0x6
			case NARRATE: // 0xD
			case STYLE_F: // 0xF
				if (style != StringStyle.UPGRADE) {
					windowBasePosX = 20;
					windowBasePosY = 28;
					windowSizeX = 280;
					windowSizeY = 58;
				}

				windowTextStartX = 16;
				windowTextStartY = 3;

				clipMinX = windowBasePosX + 5;
				clipMinY = windowBasePosY + 4;
				clipMaxX = windowBasePosX + windowSizeX - 8;
				clipMaxY = windowBasePosY + windowSizeY - 5;

				rewindArrowX = clipMaxX - 17;
				rewindArrowY = clipMaxY - 17;
				break;

			case EPILOGUE: // 0xE
				windowBasePosX = 60;
				windowBasePosY = 110;
				windowSizeX = 200;
				windowSizeY = 50;
				windowTextStartX = 0;
				windowTextStartY = -2;

				clipMinX = windowBasePosX;
				clipMinY = windowBasePosY;
				clipMaxX = windowBasePosX + windowSizeX;
				clipMaxY = windowBasePosY + windowSizeY;
				rewindArrowX = windowBasePosX + windowSizeX - 10;
				rewindArrowY = windowBasePosY + windowSizeY - 10;
				break;
		}
	}

	public void drawMessageBox()
	{
		switch (style) {
			case RIGHT: // 0x1
			case LEFT: // 0x2
			case CENTER: // 0x3
			case TATTLE:
				Speech_L.drawBasicQuad(
					windowBasePosX + 1,
					windowBasePosX + bubbleCurveWidth,
					windowBasePosY,
					windowBasePosY + bubbleHeight);
				Speech_M.drawBasicQuad(
					windowBasePosX + bubbleCurveWidth,
					windowBasePosX + bubbleCurveWidth + bubbleStraightWidth,
					windowBasePosY,
					windowBasePosY + bubbleHeight);
				Speech_R.drawBasicQuad(
					windowBasePosX + bubbleCurveWidth + bubbleStraightWidth,
					windowBasePosX + bubbleCurveWidth + bubbleStraightWidth + bubbleCurveWidth,
					windowBasePosY,
					windowBasePosY + bubbleHeight);
				break;

			case CHOICE:
				// draw sample speech bubble as well
				Speech_L.drawBasicQuad(22 + 1, 22 + 32, 13, 13 + 68);
				Speech_M.drawBasicQuad(22 + 32, 22 + 32 + 218, 13, 13 + 68);
				Speech_R.drawBasicQuad(22 + 32 + 218, 22 + 32 + 218 + 32, 13, 13 + 68);

				MessageBoxes.drawBorder(MessageBoxes.FrameA,
					windowBasePosX, windowBasePosX + windowSizeX,
					windowBasePosY, windowBasePosY + windowSizeY,
					WindowPalette.Standard_0.getColors()[4], 0); // speech palette 0, color #4 at 802EC3F8 (5th one in palette)
				break;

			case SIGN:
			case LAMPPOST:
				WindowPalette pal = (style == StringStyle.SIGN) ? WindowPalette.Sign : WindowPalette.LampPost;

				// draw pieces in this order so they bug correctly at small windowSizeY
				MessageBoxes.Sign[1][1].drawBasicQuad(pal, 20 + 16, 284, 28 + 16, 28 + windowSizeY - 16);
				MessageBoxes.Sign[1][0].drawBasicQuad(pal, 20, 20 + 16, 28 + 16, 28 + windowSizeY - 16);
				MessageBoxes.Sign[1][2].drawBasicQuad(pal, 284, 284 + 16, 28 + 16, 28 + windowSizeY - 16);
				MessageBoxes.Sign[0][1].drawBasicQuad(pal, 20 + 16, 284, 28, 28 + 16);
				MessageBoxes.Sign[2][1].drawBasicQuad(pal, 20 + 16, 284, 28 + windowSizeY - 16, 28 + windowSizeY);
				MessageBoxes.Sign[0][0].drawBasicQuad(pal, 20, 28);
				MessageBoxes.Sign[0][2].drawBasicQuad(pal, 284, 28);
				MessageBoxes.Sign[2][0].drawBasicQuad(pal, 20, 28 + windowSizeY - 16);
				MessageBoxes.Sign[2][2].drawBasicQuad(pal, 284, 28 + windowSizeY - 16);
				break;

			case POSTCARD:
				Letter_BG.drawBasicQuad(85, 97);
				if (letterIndex > 0 && letterIndex < MessageBoxes.Letters.length)
					MessageBoxes.Letters[letterIndex].drawBasicQuad(160, 102);

				MessageBoxes.drawBorder(MessageBoxes.FrameB,
					windowBasePosX, windowBasePosX + windowSizeX,
					windowBasePosY, windowBasePosY + windowSizeY,
					null, editor.getFrameCount());
				break;

			case INSPECT:
			case UPGRADE:
			case NARRATE:
			case STYLE_F:
				MessageBoxes.drawBorder(MessageBoxes.FrameB,
					windowBasePosX, windowBasePosX + windowSizeX,
					windowBasePosY, windowBasePosY + windowSizeY,
					null, editor.getFrameCount());
				break;

			case POPUP:
			case STYLE_B:
				//TODO not really supported yet -- these use draw_box, so that whole system would
				// have to be integrated somehow
				MessageBoxes.drawBorder(MessageBoxes.FrameA,
					windowBasePosX, windowBasePosX + windowSizeX,
					windowBasePosY, windowBasePosY + windowSizeY,
					WindowPalette.Standard_0.getColors()[4], 0); // speech palette 0, color #4 at 802EC3F8 (5th one in palette)
				break;

			case EPILOGUE:
				break; // no graphics
		}
	}

	public MessagePrinter(MessageEditor editor)
	{
		this.editor = editor;
		setStyle(StringStyle.RIGHT, null); // initialize parameters for default window style
	}

	public void setSequences(ArrayList<Sequence> sequences)
	{
		this.sequences = sequences;
		this.pages = paginate(sequences);

		anim.reset();
		setStyle(StringStyle.RIGHT, null); // initialize parameters for default window style

		ByteBuffer srcBuffer = StringEncoder.getBuffer(sequences, false);
		srcBuffer.rewind();
		compiledLength = srcBuffer.capacity();

		StringProperties properties = MessageUtil.getStringProperties(srcBuffer);
		stringWidth = properties.maxLineWidth;
		stringNumLines = properties.maxNonblankLinesPerPage;

		drawBuffer = ByteBuffer.allocateDirect(5 + srcBuffer.capacity()); // over-allocate for FE and 4x FA

		for (int i = 0; i < drawBuffer.capacity(); i++)
			drawBuffer.put((byte) BUFFER_FILL); // game uses FB for this after converting all FB->FA
		drawBuffer.rewind();
	}

	public static final int PAGE_END_MARK = 0xFE;
	public static final int BUFFER_FILL = 0xFA; // game uses FB for this after converting all FB->FA

	public void setCurrentPage(int caretPos)
	{
		if (pages.size() == 0) {
			currentPage = null;
			return;
		}

		assert (pages.size() > 0);

		// find page with cursor
		Page lastPageBeforeCursor = null;
		for (Page page : pages) {
			if (page.srcStart <= caretPos)
				lastPageBeforeCursor = page;
		}

		assert (lastPageBeforeCursor != null);

		ArrayList<Page> skipPages = new ArrayList<>();
		for (Page page : pages) {
			if (page == lastPageBeforeCursor)
				break;
			skipPages.add(page);
		}

		if (currentPage == lastPageBeforeCursor)
			return; // page didnt change

		// set the new current page
		currentPage = lastPageBeforeCursor;

		reset();

		drawBuffer.clear();
		for (Page pg : skipPages) {
			ByteBuffer skipBuffer = StringEncoder.getBuffer(pg.sequences, false);
			skipBuffer.rewind();

			while (skipBuffer.hasRemaining())
				update(skipBuffer);
		}

		drawBuffer.put((byte) PAGE_END_MARK);
		printPos = drawBuffer.position();
		donePrinting = false;

		for (int i = printPos; i < drawBuffer.capacity(); i++)
			drawBuffer.put((byte) BUFFER_FILL); // game uses FB for this after converting all FB->FA
		drawBuffer.position(printPos);

		pageBuffer = StringEncoder.getBuffer(currentPage.sequences, false);
		pageBuffer.rewind();
	}

	private void reset()
	{
		hasPrintDelay = true;
		printChunkSize = 1;
		printDelay = 1;

		scrollTarget = 0;
		scrollAmount = 0;

		pauseCounter = 0;

		pageLines = 0;

		printPos = 0;
		donePrinting = false;

		anim.reset();
	}

	public void update()
	{
		update(pageBuffer);
	}

	public void update(ByteBuffer buffer)
	{
		if (currentPage == null)
			return;

		anim.update();

		boolean optEnablePrintDelay = editor.printDelayEnabled();

		if (scrollAmount < scrollTarget) {
			//	scrollAmount += 1 / 8.0;

			// this is just an interp designed to feel nice
			float diff = scrollTarget - scrollAmount;
			if (diff < 0.5)
				scrollAmount += 0.1;
			else
				scrollAmount += 0.2 * diff;

			if (scrollAmount > scrollTarget)
				scrollAmount = scrollTarget; // no overshoot
		}

		if (pauseCounter > 0) {
			pauseCounter--;
			return; // waiting for pause
		}

		if (hasPrintDelay && optEnablePrintDelay) {
			if (printCounter > 0)
				printCounter--;

			if (printCounter > 0)
				return; // waiting for print delay

			// time to continue printing
			printCounter = printDelay;
			charsToPrint = printChunkSize;
		}

		drawBuffer.position(printPos);
		read_buf:
		while (buffer.hasRemaining()) {
			byte charByte = buffer.get();
			int charInt = charByte & 0xFF;

			if (charInt < 0xF0 || charInt == 0xF7 || charInt == 0xF8 || charInt == 0xF9) {
				drawBuffer.put(charByte);
				charsToPrint--;

				if (charsToPrint == 0)
					break read_buf;
			}
			else {
				switch (ControlCharacter.decodeMap.get(charByte)) {
					case ENDL: // 0xF0
						drawBuffer.put(charByte);
						pageLines++;
						break;

					case VARIANT0: // 0xF3
					case VARIANT1: // 0xF4
					case VARIANT2: // 0xF5
					case VARIANT3: // 0xF6
						// printer ignores
						drawBuffer.put(charByte);
						break;

					case WAIT: // 0xF1
						//TODO support for input?
						break;

					case NEXT: // 0xFB
						//NOTE:	this spacing doesn't really occur in-game, but provides
						//		better visual separation of pages in the editor
						scrollTarget += (pageLines + 1);
						drawBuffer.put((byte) 0xF0);

						//	scrollTarget += pageLines;
						pageLines = 0;
						break;

					case PAUSE: // 0xF2
						pauseCounter = (buffer.get() & 0xFF);
						charsToPrint = 0;
						break read_buf;

					// 0xF7-0xF9 are spaces
					// 0xFA = unknown

					case END: // 0xFD
						drawBuffer.put(charByte);
						break read_buf;

					case STYLE: // 0xFC
						byte styleID = buffer.get();
						drawBuffer.put(charByte);
						drawBuffer.put(styleID);
						// strip args
						setStyle(StringStyle.decodeMap.get(styleID), buffer);
						break;

					// 0xFE = unused afaik
					case FUNC: // 0xFF
						byte funcID = buffer.get();
						StringFunction func = StringFunction.decodeMap.get(funcID);

						switch (func) {
							/*
							[Func_1A:00:00:05][Sprite:00:39:1F]
							[Func_1A:00:01:06][Sprite:00:39:20]
							[Func_1A:00:02:05][Sprite:00:39:1F]
							[Func_1A:00:03:06][Sprite:00:39:20]
							[Func_1B:00:00][Func_1C:00]
							 */
							case ANIM_SPRITE:
								anim.sprites.add(buffer.getShort() & 0xFFFF);
								anim.rasters.add(buffer.get() & 0xFF);
								break;
							case ANIM_DELAY:
								buffer.get();
								anim.indices.add(buffer.get() & 0xFF);
								anim.delays.add(buffer.get() & 0xFF);
								break;
							case ANIM_LOOP:
								buffer.get();
								buffer.get();
								break;
							case ANIM_DONE:
								drawBuffer.put(charByte);
								drawBuffer.put(funcID);
								buffer.get();
								anim.bake();
								break;

							case YIELD:
								break;

							case DELAY_ON:
								hasPrintDelay = true;
								break;
							case DELAY_OFF:
								hasPrintDelay = false;
								break;
							case SCROLL:
								scrollTarget += (buffer.get() & 0xFF);
								break;
							case SPEED:
								printDelay = (buffer.get() & 0xFF);
								printChunkSize = (buffer.get() & 0xFF);
								break;
							case START_FX:
								byte fxID = buffer.get();
								drawBuffer.put(charByte);
								drawBuffer.put(funcID); // copy function type
								drawBuffer.put(fxID); // copy fx type
								StringEffect fx = StringEffect.decodeMap.get(fxID);
								for (int i = 0; i < fx.args; i++)
									drawBuffer.put(buffer.get()); // copy fx args
								break;
							case END_FX:
								drawBuffer.put(charByte);
								drawBuffer.put(funcID); // copy function type
								drawBuffer.put(buffer.get()); // copy fx type
								break;

							case SETVOICE:
							case VOLUME:
							case VOICE:
								for (int i = 0; i < func.args; i++)
									buffer.get();
								break;

							default:
								drawBuffer.put(charByte);
								drawBuffer.put(funcID); // copy function type
								for (int i = 0; i < func.args; i++)
									drawBuffer.put(buffer.get()); // copy function args
						}
						break;
				}
			}
		}

		donePrinting = !buffer.hasRemaining();
		printPos = drawBuffer.position();
	}

	public static class Page
	{
		public final ArrayList<Sequence> sequences = new ArrayList<>();
		public final int srcStart;
		public int srcEnd;

		public Page(int startPos)
		{
			srcStart = startPos;
			srcEnd = startPos;
		}
	}

	private static ArrayList<Page> paginate(List<Sequence> sequences)
	{
		ArrayList<Page> pages = new ArrayList<>();
		Page currentPage = new Page(0);

		for (Sequence seq : sequences) {
			currentPage.sequences.add(seq);
			currentPage.srcEnd = seq.srcEnd;

			if (seq.pageBreak) {
				pages.add(currentPage);
				currentPage = new Page(seq.srcEnd);
			}
		}

		if (!currentPage.sequences.isEmpty())
			pages.add(currentPage);

		return pages;
	}

	private static class Animation
	{
		private final ArrayList<Integer> sprites;
		private final ArrayList<Integer> rasters;
		private final ArrayList<Integer> indices;
		private final ArrayList<Integer> delays;

		public int delayCounter;
		public int currentIndex;
		public int currentRaster;

		public boolean baked;

		private Animation()
		{
			sprites = new ArrayList<>();
			rasters = new ArrayList<>();
			indices = new ArrayList<>();
			delays = new ArrayList<>();
			reset();
		}

		private void reset()
		{
			sprites.clear();
			rasters.clear();
			indices.clear();
			delays.clear();

			baked = false;
			delayCounter = 0;
			currentIndex = 0;
			currentRaster = 0;
		}

		private void update()
		{
			if (baked) {
				delayCounter--;
				if (delayCounter == 0) {
					currentIndex++;
					if (currentIndex == delays.size())
						currentIndex = 0;
					delayCounter = delays.get(currentIndex);
					currentRaster = rasters.get(currentIndex);
				}
			}
		}

		private void bake()
		{
			// sanity checks
			if (rasters.size() == 0)
				return;
			if (rasters.size() != delays.size())
				return;
			for (int i = 0; i < rasters.size(); i++) {
				if ((int) indices.get(i) != i)
					return;
				if ((int) sprites.get(i) != (int) sprites.get(0))
					return;
			}

			baked = true;
			delayCounter = delays.get(0);
			currentRaster = rasters.get(0);
			currentIndex = 0;
		}
	}

	public boolean hasAnim()
	{
		return anim.baked;
	}

	public int getAnimSprite()
	{
		return anim.sprites.get(0);
	}

	public int getAnimRaster()
	{
		return anim.currentRaster;
	}
}
