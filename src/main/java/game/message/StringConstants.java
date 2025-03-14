package game.message;

import static game.message.StringConstants.CharacterEncoding.*;

import java.util.HashMap;

import util.CaseInsensitiveMap;
import util.DualHashMap;

public abstract class StringConstants
{
	protected static final DualHashMap<Byte, Character> characterMap;
	protected static final DualHashMap<Byte, Character> creditsCharacterMap;

	public static final char COMMENT = '%';
	public static final char HASH = '#';
	public static final char OPEN_TAG = '[';
	public static final char CLOSE_TAG = ']';
	public static final char DELIMITER = ':';
	public static final char OPEN_FUNC = '[';
	public static final char CLOSE_FUNC = ']';
	public static final char ESCAPE = '\\';
	public static final char END_EFFECT_TAG = '/';
	public static final String END_EFFECT_WILDCARD = END_EFFECT_TAG + "fx";

	//protected static final String CONTINUE_LINE_TAG = "...";

	public static int getMaxStringVars()
	{
		return 3;
	}

	public static enum ControlCharacter
	{
		// @formatter:off
		ENDL 		( 0, 0xF0, "BR"),
		WAIT		( 0, 0xF1, "Wait"),
		PAUSE		( 1, 0xF2, "Pause"),
		VARIANT0	( 0, 0xF3, "Variant0"),
		VARIANT1	( 0, 0xF4, "Variant1"),
		VARIANT2	( 0, 0xF5, "Variant2"),
		VARIANT3	( 0, 0xF6, "Variant3"),
		// F7/F8/F9 are for spaces
		// FA
		NEXT		( 0, 0xFB, "Next"),
		STYLE		(-1, 0xFC, "Style"),
		END			(0, 0xFD, "End"),
		// FE unused afaik
		FUNC		(-1, 0xFF, "FUNC");
		// @formatter:on

		public final int args;
		public final int code;
		public final String name;

		private ControlCharacter(int args, int code, String name)
		{
			this.args = args;
			this.code = code;
			this.name = name;
		}

		public static final HashMap<Byte, ControlCharacter> decodeMap;
		public static final CaseInsensitiveMap<ControlCharacter> encodeMap;

		static {
			decodeMap = new HashMap<>();
			encodeMap = new CaseInsensitiveMap<>();
			for (ControlCharacter type : ControlCharacter.values()) {
				decodeMap.put((byte) type.code, type);
				encodeMap.put(type.name, type);
			}
		}
	}

	public static enum SpecialCharacter
	{
		// @formatter:off
		NOTE	( 0x00, "NOTE"),
		HEART	( 0x90, "HEART"),
		STAR	( 0x91, "STAR"),
		UP		( 0x92, "UP"),
		DOWN	( 0x93, "DOWN"),
		LEFT	( 0x94, "LEFT"),
		RIGHT	( 0x95, "RIGHT"),
		CIRCLE	( 0x96, "CIRCLE"),
		CROSS	( 0x97, "CROSS"),
		A		( 0x98, "~A"),
		B		( 0x99, "~B"),
		L		( 0x9A, "~L"),
		R		( 0x9B, "~R"),
		Z		( 0x9C, "~Z"),
		C_UP	( 0x9D, "~C-UP"),
		C_DOWN	( 0x9E, "~C-DOWN"),
		C_LEFT	( 0x9F, "~C-LEFT"),
		C_RIGHT	( 0xA0, "~C-RIGHT"),
		START	( 0xA1, "~START");
		// @formatter:on

		public final int code;
		public final String name;

		private SpecialCharacter(int code, String name)
		{
			this.code = code;
			this.name = name;
		}

		public static final HashMap<Byte, SpecialCharacter> decodeMap;
		public static final CaseInsensitiveMap<SpecialCharacter> encodeMap;

		static {
			decodeMap = new HashMap<>();
			encodeMap = new CaseInsensitiveMap<>();
			for (SpecialCharacter type : SpecialCharacter.values()) {
				decodeMap.put((byte) type.code, type);
				encodeMap.put(type.name, type);
			}
		}
	}

	public static enum StringFont
	{
		// @formatter:off
		NORMAL		(0x0, "Normal"),
		MENU		(0x1, "Menu"),
		TITLE		(0x3, "Title"),
		SUBTITLE	(0x4, "Subtitle");
		// @formatter:on

		public final int code;
		public final String name;

		private StringFont(int code, String name)
		{
			this.code = code;
			this.name = name;
		}

		public static final HashMap<Byte, StringFont> decodeMap;
		public static final CaseInsensitiveMap<StringFont> encodeMap;

		static {
			decodeMap = new HashMap<>();
			encodeMap = new CaseInsensitiveMap<>();
			for (StringFont type : StringFont.values()) {
				decodeMap.put((byte) type.code, type);
				encodeMap.put(type.name, type);
			}
		}
	}

	public static enum StringVoice
	{
		// @formatter:off
		NORMAL		(0x0, "Normal"),
		BOWSER		(0x1, "Bowser"),
		STAR		(0x2, "Star");
		// @formatter:on

		public final int code;
		public final String name;

		private StringVoice(int code, String name)
		{
			this.code = code;
			this.name = name;
		}

		public static final HashMap<Byte, StringVoice> decodeMap;
		public static final CaseInsensitiveMap<StringVoice> encodeMap;

		static {
			decodeMap = new HashMap<>();
			encodeMap = new CaseInsensitiveMap<>();
			for (StringVoice type : StringVoice.values()) {
				decodeMap.put((byte) type.code, type);
				encodeMap.put(type.name, type);
			}
			encodeMap.put("Spirit", StringVoice.STAR);
		}
	}

	// switch at [801247F4]
	public static enum StringStyle
	{
		// @formatter:off
	//	STYLE_0	(0, 0x0,  0, "???"),
		RIGHT	(0, 0x1, -4, "Right"),		// standard speech bubble connected to NPC like /
		LEFT	(0, 0x2, -4, "Left"),		// standard speech bubble connected to NPC like \
		CENTER	(0, 0x3, -4, "Center"),		// standard speech bubble connected to NPC vertically |
		TATTLE	(0, 0x4, -4, "Tattle"),
		CHOICE	(4, 0x5,  0, "Choice"),
		INSPECT	(0, 0x6, -4, "Inspect"),	// silent. grey, scrolling pattern in bg. usually used when inspecting things.
		SIGN	(0, 0x7, -4, "Sign"),
		LAMPPOST(1, 0x8, -4, "Lamppost"),	// arg is height of text box. note: 0x48 = 72, 0x50 = 80
		POSTCARD(1, 0x9,  0, "Postcard"),	// arg: which image to display
		POPUP	(0, 0xA,  0, "Popup"),		// only used with popup for "you got kooper's shell!"
		STYLE_B	(0, 0xB,  0, "STYLE_B"),
		UPGRADE (4, 0xC,  0, "Upgrade"),	// used with upgrade blocks. silent. grey semitransparent scrolling bg. rounded frame.
		NARRATE (0, 0xD, -4, "Narrate"),	// "you got X!", "Y joined your party!"
		EPILOGUE(0, 0xE, -4, "Epilogue"),	// end of chapter text. silent. centered with no bg.
		STYLE_F	(0, 0xF,  0, "STYLE_F");
		// @formatter:on

		public final int args;
		public final int code;
		public final int lineOffset;
		public final String name;

		private StringStyle(int args, int code, int lineOffset, String name)
		{
			this.args = args;
			this.code = code;
			this.lineOffset = lineOffset;
			this.name = name;
		}

		public static final HashMap<Byte, StringStyle> decodeMap;
		public static final CaseInsensitiveMap<StringStyle> encodeMap;

		static {
			decodeMap = new HashMap<>();
			encodeMap = new CaseInsensitiveMap<>();
			for (StringStyle type : StringStyle.values()) {
				decodeMap.put((byte) type.code, type);
				encodeMap.put(type.name, type);
			}
		}
	}

	public static enum StringFunction
	{
		// @formatter:off
		FONT		(1, 0x00, 0x0, "Font"),			// set primary font style
		VARIANT		(1, 0x01, 0x1, "Variant"),		// set secondary font style
		// 02
		FUNC_03		(0, 0x03, 0x17, "Func_03"), 	// causes disaply list at 8014C500 to be appended
		YIELD		(0, 0x04, "Yield"), 			// end without closing message box: set flags 100040, clear 180
		COLOR		(1, 0x05, 0x4, "Color"),		// args = (color)
		SKIP_OFF	(0, 0x06, "NoSkip"),
		INPUT_OFF	(0, 0x07, "InputOff"),
		INPUT_ON	(0, 0x08, "InputOn"),
		DELAY_OFF	(0, 0x09, "DelayOff"),
		DELAY_ON	(0, 0x0A, "DelayOn"),
		SPACING		(1, 0x0B, 0x5, "Spacing"),		// char width override. forces all chars to have width = arg
		SCROLL		(1, 0x0C, 0xFA, "Scroll"),		// prints just FA; not FF,FA
		SIZE		(2, 0x0D, 0x6, "Size"),
		SIZE_RESET	(0, 0x0E, 0x7, "SizeReset"),
		SPEED		(2, 0x0F, "Speed"),
		SET_X		(2, 0x10, 0x8, "SetPosX"),		// args = (upper posX, lower posX)
		SET_Y		(1, 0x11, 0x9, "SetPosY"),		// args = (posY)
		RIGHT		(1, 0x12, 0xA, "Right"),
		DOWN		(1, 0x13, 0xB, "Down"),
		UP			(1, 0x14, 0xC, "Up"),
		INLINE_IMAGE(1, 0x15, 0xE, "InlineImage"),	// args = (index) for small inline images printed at caret pos
		ANIM_SPRITE	(3, 0x16, 0xF, "AnimSprite"),
		ITEM_ICON	(2, 0x17, 0x10, "ItemIcon"),	// args = (itemID upper, itemID lower)	-- DEV ERROR in getproperties assumes this has only one arg!
		IMAGE		(7, 0x18, "Image"),				// args = (index, posX upper, posX lower, posY, hasBorder (can also be 2?), alphaFinal, alphaStep)
		HIDE_IMAGE	(1, 0x19, "HideImage"),			// args = (fade amount per frame, 0 = instant)
		ANIM_DELAY	(3, 0x1A, 0x11, "AnimDelay"),
		ANIM_LOOP	(2, 0x1B, 0x12, "AnimLoop"),
		ANIM_DONE	(1, 0x1C, 0x13, "AnimDone"),
		SET_CURSOR	(3, 0x1D, "SetCursorPos"),		// args = (index, cursor[i] x, cursor[i] y)
		CURSOR		(1, 0x1E, 0x14, "Cursor"),		// position in text where finger cursor for choice N
		END_CHOICE	(1, 0x1F, "EndChoice"),			// end choices
		SET_CANCEL	(1, 0x20, "SetCancel"),			// sets the return value of the cancel button (B)
		OPTION		(1, 0x21, 0x15, "Option"),		// denotes text to highlight for each option
		PUSH_POS	(0, 0x22, 0x18, "SavePos"),
		POP_POS		(0, 0x23, 0x19, "RestorePos"),
		PUSH_COLOR	(0, 0x24, 0x1A, "SaveColor"),
		POP_COLOR	(0, 0x25, 0x1B, "RestoreColor"),
		START_FX	(-1, 0x26, 0x1C, "StartFX"),
		END_FX		(-1, 0x27, 0x1D, "EndFX"),
		VAR			(1, 0x28,  "Var"),
		CENTER_X	(1, 0x29, 0x1E, "CenterX"),
		SET_REWIND	(1, 0x2A, "SetRewind"),			// arg = set or clear print->stateFlags | 0x40000;
		ENABLE_CDOWN(0, 0x2B, "EnableCDownNext"), 	// allows C-Down to advance to next page
		SETVOICE	(8, 0x2C, "CustomVoice"),		// args = soundA=(AA BB CC DD) soundB=(EE FF GG HH)
		// 2D
		VOLUME		(1, 0x2E, "Volume"),
		VOICE		(1, 0x2F, "Voice");
		// @formatter:on

		public final int args;
		public final int code;
		public final int printCode;
		public final String name;

		private StringFunction(int args, int code, String name)
		{
			this(args, code, -1, name);
		}

		private StringFunction(int args, int code, int printCode, String name)
		{
			this.args = args;
			this.code = code;
			this.name = name;
			this.printCode = printCode;
		}

		public static final HashMap<Byte, StringFunction> decodeMap;
		public static final CaseInsensitiveMap<StringFunction> encodeMap;

		static {
			decodeMap = new HashMap<>();
			encodeMap = new CaseInsensitiveMap<>();
			for (StringFunction type : StringFunction.values()) {
				decodeMap.put((byte) type.code, type);
				encodeMap.put(type.name, type);
			}
		}
	}

	//	[8012A71C] values index switch table at 80150878
	public static enum StringEffect
	{
		// @formatter:off
		SHAKE			(0, 0x00,   0x01, "Shake"),
		WAVE			(0, 0x01,   0x02, "Wave"),
		NOISE_OUTLINE	(0, 0x02,   0x04, "NoiseOutline"),
		STATIC			(1, 0x03,0x10000, "Static"),		// StaticBlend (amount=d|percent=f)
		UNUSED			(0, 0x04,      0, "???"),			// there is no code for this
		BLUR			(1, 0x05,   0x20, "Blur"),			// Blur (direction|dir)=(x|y|both)
		RAINBOW			(0, 0x06,   0x40, "Rainbow"),
		DITHER_FADE		(1, 0x07,   0x80, "DitherFade"),	// DitherFade (amount=d|percent=f)
		GLOBAL_WAVE		(0, 0x08,  0x200, "GlobalWave"),
		GLOBAL_RAINBOW	(0, 0x09,  0x400, "GlobalRainbow"),
		RISE_PRINT		(0, 0x0A,  0x800, "PrintRising"),
		GROW_PRINT		(0, 0x0B, 0x1000, "PrintGrowing"),
		SIZE_JITTER		(0, 0x0C, 0x2000, "SizeJitter"),
		SIZE_WAVE		(0, 0x0D, 0x4000, "SizeWave"),
		DROP_SHADOW		(0, 0x0E, 0x8000, "DropShadow");
		// @formatter:on

		public final int args;
		public final int code;
		public final int flag;
		public final String name;

		private StringEffect(int args, int code, int flag, String name)
		{
			this.args = args;
			this.code = code;
			this.flag = flag;
			this.name = name;
		}

		public static final HashMap<Byte, StringEffect> decodeMap;
		public static final CaseInsensitiveMap<StringEffect> encodeMap;

		static {
			decodeMap = new HashMap<>();
			encodeMap = new CaseInsensitiveMap<>();
			for (StringEffect type : StringEffect.values()) {
				decodeMap.put((byte) type.code, type);
				encodeMap.put(type.name, type);
			}
		}
	}

	public static enum CharacterEncoding
	{
		NORMAL, ESCAPED, TAG
	}

	public static enum StandardCharacter
	{
		// @formatter:off
		c00	( 0x00,	"NOTE",	TAG),
		c01	( 0x01,	"!",	NORMAL),
		c02	( 0x02,	"\"",	NORMAL),
		c03	( 0x03,	"#",	NORMAL),
		c04	( 0x04,	"$",	NORMAL),
		c05	( 0x05,	"%",	ESCAPED),
		c06	( 0x06,	"&",	NORMAL),
		c07	( 0x07,	"\'",	NORMAL),
		c08	( 0x08,	"( ",	ESCAPED),
		c09	( 0x09,	")",	ESCAPED),
		c0A	( 0x0A,	"*",	NORMAL),
		c0B	( 0x0B,	"+",	NORMAL),
		c0C	( 0x0C,	",",	NORMAL),
		c0D	( 0x0D,	"-",	NORMAL),
		c0E	( 0x0E,	".",	NORMAL),
		c0F	( 0x0F,	"/",	NORMAL),
		c10	( 0x10,	"0",	NORMAL),
		c11	( 0x11,	"1",	NORMAL),
		c12	( 0x12,	"2",	NORMAL),
		c13	( 0x13,	"3",	NORMAL),
		c14	( 0x14,	"4",	NORMAL),
		c15	( 0x15,	"5",	NORMAL),
		c16	( 0x16,	"6",	NORMAL),
		c17	( 0x17,	"7",	NORMAL),
		c18	( 0x18,	"8",	NORMAL),
		c19	( 0x19,	"9",	NORMAL),
		c1A	( 0x1A,	":",	NORMAL),
		c1B	( 0x1B,	";",	NORMAL),
		c1C	( 0x1C,	"<",	NORMAL),
		c1D	( 0x1D,	"=",	NORMAL),
		c1E	( 0x1E,	">",	NORMAL),
		c1F	( 0x1F,	"?",	NORMAL),
		c20	( 0x20,	"@",	NORMAL),
		c21	( 0x21,	"A",	NORMAL),
		c22	( 0x22,	"B",	NORMAL),
		c23	( 0x23,	"C",	NORMAL),
		c24	( 0x24,	"D",	NORMAL),
		c25	( 0x25,	"E",	NORMAL),
		c26	( 0x26,	"F",	NORMAL),
		c27	( 0x27,	"G",	NORMAL),
		c28	( 0x28,	"H",	NORMAL),
		c29	( 0x29,	"I",	NORMAL),
		c2A	( 0x2A,	"J",	NORMAL),
		c2B	( 0x2B,	"K",	NORMAL),
		c2C	( 0x2C,	"L",	NORMAL),
		c2D	( 0x2D,	"M",	NORMAL),
		c2E	( 0x2E,	"N",	NORMAL),
		c2F	( 0x2F,	"O",	NORMAL),
		c30	( 0x30,	"P",	NORMAL),
		c31	( 0x31,	"Q",	NORMAL),
		c32	( 0x32,	"R",	NORMAL),
		c33	( 0x33,	"S",	NORMAL),
		c34	( 0x34,	"T",	NORMAL),
		c35	( 0x35,	"U",	NORMAL),
		c36	( 0x36,	"V",	NORMAL),
		c37	( 0x37,	"W",	NORMAL),
		c38	( 0x38,	"X",	NORMAL),
		c39	( 0x39,	"Y",	NORMAL),
		c3A	( 0x3A,	"Z",	NORMAL),
		c3B	( 0x3B,	"[",	ESCAPED),
		c3C	( 0x3C,	"\u00A5",	NORMAL),
		c3D	( 0x3D,	"]",	ESCAPED),
		c3E	( 0x3E,	"^",	NORMAL),
		c3F	( 0x3F,	"_",	NORMAL),
		c40	( 0x40,	"`",	NORMAL),
		c41	( 0x41,	"a",	NORMAL),
		c42	( 0x42,	"b",	NORMAL),
		c43	( 0x43,	"c",	NORMAL),
		c44	( 0x44,	"d",	NORMAL),
		c45	( 0x45,	"e",	NORMAL),
		c46	( 0x46,	"f",	NORMAL),
		c47	( 0x47,	"g",	NORMAL),
		c48	( 0x48,	"h",	NORMAL),
		c49	( 0x49,	"i",	NORMAL),
		c4A	( 0x4A,	"j",	NORMAL),
		c4B	( 0x4B,	"k",	NORMAL),
		c4C	( 0x4C,	"l",	NORMAL),
		c4D	( 0x4D,	"m",	NORMAL),
		c4E	( 0x4E,	"n",	NORMAL),
		c4F	( 0x4F,	"o",	NORMAL),
		c50	( 0x50,	"p",	NORMAL),
		c51	( 0x51,	"q",	NORMAL),
		c52	( 0x52,	"r",	NORMAL),
		c53	( 0x53,	"s",	NORMAL),
		c54	( 0x54,	"t",	NORMAL),
		c55	( 0x55,	"u",	NORMAL),
		c56	( 0x56,	"v",	NORMAL),
		c57	( 0x57,	"w",	NORMAL),
		c58	( 0x58,	"x",	NORMAL),
		c59	( 0x59,	"y",	NORMAL),
		c5A	( 0x5A,	"z",	NORMAL),
		c5B	( 0x5B,	"{",	ESCAPED),
		c5C	( 0x5C,	"|",	NORMAL),
		c5D	( 0x5D,	"}",	ESCAPED),
		c5E	( 0x5E,	"~",	NORMAL),
		c5F	( 0x5F,	"\u00B0",	NORMAL),
		c60	( 0x60,	"\u00C0",	NORMAL),
		c61	( 0x61,	"\u00C1",	NORMAL),
		c62	( 0x62,	"\u00C2",	NORMAL),
		c63	( 0x63,	"\u00C4",	NORMAL),
		c64	( 0x64,	"\u00C7",	NORMAL),
		c65	( 0x65,	"\u00C8",	NORMAL),
		c66	( 0x66,	"\u00C9",	NORMAL),
		c67	( 0x67,	"\u00CA",	NORMAL),
		c68	( 0x68,	"\u00CB",	NORMAL),
		c69	( 0x69,	"\u00CC",	NORMAL),
		c6A	( 0x6A,	"\u00CD",	NORMAL),
		c6B	( 0x6B,	"\u00CE",	NORMAL),
		c6C	( 0x6C,	"\u00CF",	NORMAL),
		c6D	( 0x6D,	"\u00D1",	NORMAL),
		c6E	( 0x6E,	"\u00D2",	NORMAL),
		c6F	( 0x6F,	"\u00D3",	NORMAL),
		c70	( 0x70,	"\u00D4",	NORMAL),
		c71	( 0x71,	"\u00D6",	NORMAL),
		c72	( 0x72,	"\u00D9",	NORMAL),
		c73	( 0x73,	"\u00DA",	NORMAL),
		c74	( 0x74,	"\u00DB",	NORMAL),
		c75	( 0x75,	"\u00DC",	NORMAL),
		c76	( 0x76,	"\u00DF",	NORMAL),
		c77	( 0x77,	"\u00E0",	NORMAL),
		c78	( 0x78,	"\u00E1",	NORMAL),
		c79	( 0x79,	"\u00E2",	NORMAL),
		c7A	( 0x7A,	"\u00E4",	NORMAL),
		c7B	( 0x7B,	"\u00E7",	NORMAL),
		c7C	( 0x7C,	"\u00E8",	NORMAL),
		c7D	( 0x7D,	"\u00E9",	NORMAL),
		c7E	( 0x7E,	"\u00EA",	NORMAL),
		c7F	( 0x7F,	"\u00EB",	NORMAL),
		c80	( 0x80,	"\u00EC",	NORMAL),
		c81	( 0x81,	"\u00ED",	NORMAL),
		c82	( 0x82,	"\u00EE",	NORMAL),
		c83	( 0x83,	"\u00EF",	NORMAL),
		c84	( 0x84,	"\u00F1",	NORMAL),
		c85	( 0x85,	"\u00F2",	NORMAL),
		c86	( 0x86,	"\u00F3",	NORMAL),
		c87	( 0x87,	"\u00F4",	NORMAL),
		c88	( 0x88,	"\u00F6",	NORMAL),
		c89	( 0x89,	"\u00F9",	NORMAL),
		c8A	( 0x8A,	"\u00FA",	NORMAL),
		c8B	( 0x8B,	"\u00FB",	NORMAL),
		c8C	( 0x8C,	"\u00FC",	NORMAL),
		c8D	( 0x8D,	"\u00A1",	NORMAL),
		c8E	( 0x8E,	"\u00BF",	NORMAL),
		c8F	( 0x8F,	"\u00AA",	NORMAL),
		c90	( 0x90,	"HEART",	TAG),
		c91	( 0x91,	"STAR",		TAG),
		c92	( 0x92,	"UP",		TAG),
		c93	( 0x93,	"DOWN",		TAG),
		c94	( 0x94,	"LEFT",		TAG),
		c95	( 0x95,	"RIGHT",	TAG),
		c96	( 0x96,	"CIRCLE",	TAG),
		c97	( 0x97,	"CROSS",	TAG),
		c98	( 0x98,	"A",		TAG),
		c99	( 0x99,	"B",		TAG),
		c9A	( 0x9A,	"L",		TAG),
		c9B	( 0x9B,	"R",		TAG),
		c9C	( 0x9C,	"Z",		TAG),
		c9D	( 0x9D,	"C-UP",		TAG),
		c9E	( 0x9E,	"C-DOWN",	TAG),
		c9F	( 0x9F,	"C-LEFT",	TAG),
		cA0	( 0xA0,	"C-RIGHT",	TAG),
		cA1	( 0xA1,	"START",	TAG),
		cA2	( 0xA2,	"\u201C",	NORMAL),
		cA3	( 0xA3, "\u201D",	NORMAL),
		cA4	( 0xA4,	"\u2018",	NORMAL),
		cA5	( 0xA5,	"\u2019",	NORMAL),
	//	cF5	( 0xF8,	"SPACE+",	TAG),    // drawn at full width, unused
	//	cF6	( 0xF9,	"SPACE-",	TAG ),   // drawn at 50% width, unused
		cF7	( 0xF7,	" ",		NORMAL); // drawn at 60% width
		// @formatter:on

		public final int id;
		public final String name;
		public final CharacterEncoding type;

		private StandardCharacter(int id, String name, CharacterEncoding type)
		{
			this.id = id;
			this.name = name;
			this.type = type;
		}
	}

	private static HashMap<Character, StandardCharacter> standardCharacterCharMap;
	private static StandardCharacter[] standardCharacterTable;

	private static StandardCharacter getStandard(char c)
	{
		return standardCharacterCharMap.get(c);
	}

	public static StandardCharacter getStandard(int i)
	{
		return standardCharacterTable[i];
	}

	/**
	 * Get a MessageFont index for a given character
	 * @param chr the character
	 * @param useStandard select font: standard (true) or credits (false)
	 * @return
	 */
	public static int getIndex(char chr, boolean useStandard)
	{
		if (useStandard) {
			StandardCharacter sc = StringConstants.getStandard(chr);
			return (sc == null) ? -1 : sc.id;
		}

		Byte id = creditsCharacterMap.getInverse(chr);
		return (id == null) ? -1 : (int) id;
	}

	/**
	 * Get a MessageFont character name for a given index
	 * @param id the index
	 * @param useStandard select font: standard (true) or credits (false)
	 * @return
	 */
	public static String getName(int id, boolean useStandard)
	{
		if (useStandard) {
			StandardCharacter sc = StringConstants.getStandard(id);
			return (sc == null) ? null : sc.name;
		}

		Character c = creditsCharacterMap.get((byte) id);
		return (c == null) ? null : c.toString();
	}

	static {
		standardCharacterCharMap = new HashMap<>();
		standardCharacterTable = new StandardCharacter[StandardCharacter.values().length];

		int i = 0;
		for (StandardCharacter sc : StandardCharacter.values()) {
			if (sc.type != TAG)
				standardCharacterCharMap.put(sc.name.charAt(0), sc);

			standardCharacterTable[i++] = sc;
		}

		characterMap = new DualHashMap<>();

		for (StandardCharacter sc : StandardCharacter.values()) {
			if (sc.type != TAG)
				characterMap.add((byte) sc.id, sc.name.charAt(0));
		}

		creditsCharacterMap = new DualHashMap<>();
		creditsCharacterMap.add((byte) 0x00, 'A');
		creditsCharacterMap.add((byte) 0x01, 'B');
		creditsCharacterMap.add((byte) 0x02, 'C');
		creditsCharacterMap.add((byte) 0x03, 'D');
		creditsCharacterMap.add((byte) 0x04, 'E');
		creditsCharacterMap.add((byte) 0x05, 'F');
		creditsCharacterMap.add((byte) 0x06, 'G');
		creditsCharacterMap.add((byte) 0x07, 'H');
		creditsCharacterMap.add((byte) 0x08, 'I');
		creditsCharacterMap.add((byte) 0x09, 'J');
		creditsCharacterMap.add((byte) 0x0A, 'K');
		creditsCharacterMap.add((byte) 0x0B, 'L');
		creditsCharacterMap.add((byte) 0x0C, 'M');
		creditsCharacterMap.add((byte) 0x0D, 'N');
		creditsCharacterMap.add((byte) 0x0E, 'O');
		creditsCharacterMap.add((byte) 0x0F, 'P');
		creditsCharacterMap.add((byte) 0x10, 'Q');
		creditsCharacterMap.add((byte) 0x11, 'R');
		creditsCharacterMap.add((byte) 0x12, 'S');
		creditsCharacterMap.add((byte) 0x13, 'T');
		creditsCharacterMap.add((byte) 0x14, 'U');
		creditsCharacterMap.add((byte) 0x15, 'V');
		creditsCharacterMap.add((byte) 0x16, 'W');
		creditsCharacterMap.add((byte) 0x17, 'X');
		creditsCharacterMap.add((byte) 0x18, 'Y');
		creditsCharacterMap.add((byte) 0x19, 'Z');
		creditsCharacterMap.add((byte) 0x1A, '\'');
		creditsCharacterMap.add((byte) 0x1B, '.');
		creditsCharacterMap.add((byte) 0x1C, ',');
		creditsCharacterMap.add((byte) 0x1D, '0');
		creditsCharacterMap.add((byte) 0x1E, '1');
		creditsCharacterMap.add((byte) 0x1F, '2');
		creditsCharacterMap.add((byte) 0x20, '3');
		creditsCharacterMap.add((byte) 0x21, '4');
		creditsCharacterMap.add((byte) 0x22, '5');
		creditsCharacterMap.add((byte) 0x23, '6');
		creditsCharacterMap.add((byte) 0x24, '7');
		creditsCharacterMap.add((byte) 0x25, '8');
		creditsCharacterMap.add((byte) 0x26, '9');
		creditsCharacterMap.add((byte) 0x27, '\u00A9');
		creditsCharacterMap.add((byte) 0x28, '&');
		creditsCharacterMap.add((byte) 0xF7, ' ');
	}
}
