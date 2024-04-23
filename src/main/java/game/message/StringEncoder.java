package game.message;

import static game.message.StringConstants.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.StarRodException;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import app.input.PatchFileParser;
import app.input.PatchFileParser.PatchUnit;
import game.DataUtils;
import game.ProjectDatabase;
import game.message.StringConstants.ControlCharacter;
import game.message.StringConstants.SpecialCharacter;
import game.message.StringConstants.StringEffect;
import game.message.StringConstants.StringFont;
import game.message.StringConstants.StringFunction;
import game.message.StringConstants.StringStyle;
import game.message.StringConstants.StringVoice;
import game.message.editor.MessageGroup;
import game.message.editor.StringTokenizer;
import game.message.editor.StringTokenizer.Sequence;
import util.CaseInsensitiveMap;
import util.DualHashMap;
import util.MathUtil;

public class StringEncoder
{
	private static final Matcher EndStringMatcher = Pattern.compile(
		"\\[" + ControlCharacter.END.name + "\\]").matcher("");

	// pattern for TAG_NAME:ARG:ARG:ARG
	private static final Matcher ClassicTagMatcher = Pattern.compile("(?i)[~\\w]+(?::[\\w`]+)*").matcher("");

	private static final String REGEX_1_INT = "-?(?:0x[0-9A-Fa-f]+|[0-9]+)";
	private static final String REGEX_2_INT = REGEX_1_INT + "," + REGEX_1_INT;

	private static final Matcher KVMatcher = Pattern.compile("(?i)(\\w+)=([\\w,]+)").matcher("");

	// temp buffer for encoding tags, prefer using helper methods to add to this
	private final ArrayList<Byte> tagBytes;

	private boolean tagPageBreak;

	// prevent a rare corner case in the editor: dont allow changing font in vars
	private boolean allowFontChange = true;

	// if vars are set
	private ByteBuffer[] vars;

	private final Stack<StringEffect> effects;

	private boolean doingSmartChoice = false;
	private int smartChoiceIndex = 0;

	// encoding state
	private boolean creditsEncoding = false;
	private boolean encounteredEnd = false;

	private StringEncoder()
	{
		tagBytes = new ArrayList<>(16);
		effects = new Stack<>();
	}

	private void addToTag(int i)
	{
		tagBytes.add((byte) i);
	}

	private void addToTag(int ... bytes)
	{
		for (int i : bytes)
			tagBytes.add((byte) i);
	}

	private void addToTag(ByteBuffer bb)
	{
		bb.rewind();
		while (bb.hasRemaining())
			tagBytes.add(bb.get());
	}

	private void addArgU8(String name, int i)
	{
		tagBytes.add((byte) checkRangeU8(name, i));
	}

	private void addArgU16(String name, int i)
	{
		int v = checkRangeU16(name, i);
		tagBytes.add((byte) (v >> 8));
		tagBytes.add((byte) v);
	}

	private void addArgsU8(String name, int ... bytes)
	{
		for (int i : bytes)
			addArgU8(name, i);
	}

	public static List<Message> parseMessages(MessageGroup group) throws IOException
	{
		List<Message> messages = new ArrayList<>();

		List<Line> lines = IOUtils.readPlainInputFile(group.asset);
		List<PatchUnit> units = PatchFileParser.parse(lines);

		for (PatchUnit unit : units) {
			// parse declarator
			if (!unit.parsedAsString)
				continue;

			Line declareLine = unit.declaration;
			if (!declareLine.str.startsWith("#message:") && !declareLine.str.startsWith("#string:"))
				throw new InputFileException(declareLine, "Invalid message declaration: %n%s", declareLine.trimmedInput());

			unit.declaration.tokenize(":");

			int section = 0xFFFF;
			int index = 0xFFFF;
			String name = "";

			if (unit.declaration.numTokens() == 2) {
				if (!declareLine.getString(1).matches("\\S+"))
					throw new InputFileException(declareLine, "String name could not be parsed: %n%s", declareLine.trimmedInput());

				name = declareLine.getString(1);
			}
			// LEGACY SUPPORT: #message | #string : section : index : (name)
			else if (unit.declaration.numTokens() == 4) {
				if (!declareLine.getString(3).matches("\\(.+\\)"))
					throw new InputFileException(declareLine, "String name could not be parsed: %n%s", declareLine.trimmedInput());

				name = declareLine.getString(3);
				name = name.substring(1, name.length() - 1);
			}
			// LEGACY SUPPORT: #message | #string : section : index | (name)
			else if (unit.declaration.numTokens() == 3) {
				if (!declareLine.getString(2).matches("\\(.+\\)"))
					throw new InputFileException(declareLine, "String ID could not be parsed: %n%s", declareLine.trimmedInput());

				name = declareLine.getString(2);
				name = name.substring(1, name.length() - 1);
			}
			else
				throw new InputFileException(declareLine, "String declaration could not be parsed: %n%s", declareLine.trimmedInput());

			// parse lines

			for (Line line : unit.body) {
				EndStringMatcher.reset(line.str);
				if (EndStringMatcher.find()) {
					if (line.str.substring(EndStringMatcher.end()).contains("\\S"))
						throw new InputFileException(line, "String %s has text after [END]: %n%s", name, line.trimmedInput());

					line.str = line.str.substring(0, EndStringMatcher.end());
				}
			}

			messages.add(new Message(group, unit, section, index, name));
		}

		return messages;
	}

	public static ByteBuffer encodeString(String s)
	{
		return encodeString(s, false);
	}

	/**
	 * Generic string encoding method for basic conversion of ASCII --> PM.
	 * Does not handle any tags or special characters.
	 * Does not know text source.
	 */
	public static ByteBuffer encodeString(String s, boolean appendEndChar)
	{
		ByteBuffer bb = ByteBuffer.allocate(s.length() + 1);
		for (char c : s.toCharArray()) {
			if (characterMap.containsInverse(c))
				bb.put(characterMap.getInverse(c));
			else
				throw new StarRodException("Unknown character: %c%nCould not encode string:%s ", c, s);
		}
		if (appendEndChar)
			bb.put((byte) ControlCharacter.END.code);

		bb.flip();
		return bb;
	}

	// for string editor
	public static ArrayList<Sequence> encodeText(String text, ByteBuffer[] vars)
	{
		StringEncoder builder = new StringEncoder();
		builder.vars = vars;
		ArrayList<Sequence> sequences = StringTokenizer.tokenize(text);
		encode(builder, sequences, false);
		return sequences;
	}

	// for string editor
	public static ByteBuffer encodeVar(String text, boolean throwsExceptions)
	{
		StringEncoder builder = new StringEncoder();
		builder.allowFontChange = false;
		ArrayList<Sequence> sequences = StringTokenizer.tokenize(text);
		encode(builder, sequences, throwsExceptions);
		return getBuffer(sequences, false);
	}

	// for validation
	public static ByteBuffer encode(String text)
	{
		StringEncoder builder = new StringEncoder();
		ArrayList<Sequence> sequences = StringTokenizer.tokenize(text);
		encode(builder, sequences, true);
		return getBuffer(sequences, false);
	}

	// for mod compilation
	public static ByteBuffer encodeLines(List<Line> lines)
	{
		StringEncoder builder = new StringEncoder();
		ArrayList<Sequence> sequences = StringTokenizer.tokenize(lines);
		encode(builder, sequences, true);
		return getBuffer(sequences, true);
	}

	public static ByteBuffer getBuffer(ArrayList<Sequence> sequences, boolean addPadding)
	{
		int bufferSize = 0;
		for (Sequence seq : sequences)
			bufferSize += seq.bytes.size();

		if (addPadding)
			bufferSize = (bufferSize + 3) & -4;

		ByteBuffer bb = ByteBuffer.allocateDirect(bufferSize);
		for (Sequence seq : sequences)
			for (byte b : seq.bytes)
				bb.put(b);
		bb.rewind();
		return bb;
	}

	private static void encode(StringEncoder builder, ArrayList<Sequence> sequences, boolean throwExceptions)
	{
		for (Sequence seq : sequences) {
			if (seq.hasError() && throwExceptions) // parse error
				throw new InputFileException(seq.srcLine, seq.getErrorMessage());

			try {
				if (seq.tag) {
					encodeTag(builder, seq);
				}
				else {
					encodeChars(builder, seq);
				}
			}
			catch (TagEncodingException e) {
				seq.addError(e.getMessage());
			}

			if (seq.hasError() && throwExceptions) // compile error
				throw new InputFileException(seq.srcLine, seq.getErrorMessage());

			if (builder.encounteredEnd)
				break; // dont look at anything after [END]
		}
	}

	private static void encodeChars(StringEncoder builder, Sequence seq)
	{
		DualHashMap<Byte, Character> map = builder.creditsEncoding ? creditsCharacterMap : characterMap;

		for (char chr : seq.srcText.toCharArray()) {
			if (map.containsInverse(chr))
				seq.bytes.add(map.getInverse(chr));
			else
				seq.addError("Could not encode character: %c", chr);
		}
	}

	private static void encodeTag(StringEncoder builder, Sequence seq)
	{
		String tag = seq.srcText;

		if (tag.startsWith("[") && tag.endsWith("]"))
			tag = tag.substring(1, tag.length() - 1);

		ClassicTagMatcher.reset(tag);
		builder.tagBytes.clear();
		builder.tagPageBreak = false;

		String[] fields;
		boolean classic;

		if (ClassicTagMatcher.matches()) {
			classic = true;
			fields = tag.split(Character.toString(DELIMITER));
		}
		else {
			classic = false;
			tag = tag.replaceAll("\\s+", " ");
			fields = tag.split(" ");
		}

		boolean closingTag = !fields[0].isEmpty() && (fields[0].charAt(0) == StringConstants.END_EFFECT_TAG);
		String tagName = closingTag ? fields[0].substring(1) : fields[0];

		if (PseudoTag.encodeMap.containsKey(tagName))
			encodePseudoTag(builder, seq, fields, classic);
		else if (SpecialCharacter.encodeMap.containsKey(tagName) && fields.length == 1)
			encodeSpecialCharacter(builder, fields);
		else if (ControlCharacter.encodeMap.containsKey(tagName))
			encodeControlCharacter(builder, fields, classic);
		else if (StringEffect.encodeMap.containsKey(tagName))
			encodeEffect(builder, fields, classic, !closingTag);
		else if (fields[0].equalsIgnoreCase(StringConstants.END_EFFECT_WILDCARD))
			encodeEffect(builder, fields, classic, !closingTag);
		else if (StringFunction.encodeMap.containsKey(tagName))
			encodeFunction(builder, fields, classic);
		else if (tagName.equalsIgnoreCase("RAW"))
			encodeRaw(builder, fields, classic);
		else
			seq.addError("Unknown tag: " + fields[0]);

		if (!seq.hasError()) {
			seq.bytes.addAll(builder.tagBytes);
			seq.pageBreak = builder.tagPageBreak;
		}
	}

	private static void encodeSpecialCharacter(StringEncoder builder, String[] fields)
	{
		SpecialCharacter tag = SpecialCharacter.encodeMap.get(fields[0].toUpperCase());
		if (tag == null)
			throw new TagEncodingException("Invalid special character: %s", fields[0]);

		if (fields.length != 1)
			throw new TagEncodingException("Special character %s can't have args.", fields[0]);

		if (builder.creditsEncoding)
			throw new TagEncodingException("Special characters can only be used with the %s character set.", StringFont.NORMAL.name);

		builder.addArgU8(fields[0], tag.code);
	}

	private static void encodeControlCharacter(StringEncoder builder, String[] fields, boolean classic)
	{
		ControlCharacter tag = ControlCharacter.encodeMap.get(fields[0].toUpperCase());
		if (tag == null)
			throw new TagEncodingException("Invalid control character: %s", fields[0]);

		builder.addToTag(tag.code);

		try {
			switch (tag) {
				case FUNC:
					throw new TagEncodingException("Invalid tag %s: use names for functions.", fields[0]);
				case STYLE:
					if (fields.length < 2)
						throw new TagEncodingException("%s does not have enough args.", fields[0], fields.length - 1);
					encodeBoxStyle(builder, fields, classic);
					break;
				case PAUSE: //	[pause time]   or   [PAUSE:TIME]
					if (fields.length != 2)
						throw new TagEncodingException("%s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					builder.addArgU8(fields[0], parseIntArg(fields[1], classic));
					break;
				case WAIT:
					builder.tagPageBreak = true;
				default:
					if (fields.length > 1)
						throw new TagEncodingException("Control character %s can't have args.", fields[0]);
			}
		}
		catch (InvalidInputException e) {
			throw new TagEncodingException("Unable to parse arg for %s: %n%s", fields[0], e.getMessage());
		}
	}

	private static void encodeBoxStyle(StringEncoder builder, String[] fields, boolean classic)
	{
		StringStyle type = StringStyle.encodeMap.get(fields[1].toUpperCase());
		if (type == null)
			throw new TagEncodingException("Invalid name for style: %s", fields[1]);

		builder.addToTag(type.code);

		try {
			switch (type) {
				case UPGRADE:
				case CHOICE:
					if (!classic) // [style choice pos=x,y size=w,h]
					{
						if (fields.length != 4)
							throw new TagEncodingException("Style %s has incorrect arg count: %d (expected 2).", fields[1], fields.length - 2);

						Integer[] pos = findIntArg(fields, "pos", 2, true);
						Integer[] size = findIntArg(fields, "size", 2, true);
						builder.addArgsU8(fields[1], pos[0], pos[1], size[0], size[1]);
					}
					else //	[STYLE:CHOICE:XX:YY:WW:HH]
					{
						if (fields.length != 6)
							throw new TagEncodingException("Style %s has incorrect arg count: %d (expected 4).", fields[1], fields.length - 2);
						builder.addArgsU8(fields[1],
							parseClassicIntArg(fields[2]), parseClassicIntArg(fields[3]),
							parseClassicIntArg(fields[4]), parseClassicIntArg(fields[5]));
					}
					break;

				case LAMPPOST:
					if (fields.length != 3)
						throw new TagEncodingException("Style %s has incorrect arg count: %d (expected 1).", fields[1], fields.length - 2);
					if (!classic) // [style lamppost height=%d]
					{
						Integer[] values = findIntArg(fields, "height", 1, true);
						builder.addArgU8(fields[1], values[0]);
					}
					else // [STYLE:LAMPPOST:HH]
						builder.addArgU8(fields[1], parseClassicIntArg(fields[2]));
					break;

				case POSTCARD:
					if (fields.length != 3)
						throw new TagEncodingException("Style %s has incorrect arg count: %d (expected 4).", fields[1], fields.length - 2);
					if (!classic) // [style postcard index=%d]
					{
						Integer[] values = findIntArg(fields, "index", 1, true);
						builder.addArgU8(fields[1], values[0]);
					}
					else // [STYLE:POSTCARD:INDEX]
						builder.addArgU8(fields[1], parseClassicIntArg(fields[2]));
					break;

				default: // [style %s]   or   [STYLE:TYPE]
			}
		}
		catch (InvalidInputException e) {
			throw new TagEncodingException("Unable to parse arg for style %s: %n%s", fields[1], e.getMessage());
		}
	}

	private static void encodeFunction(StringEncoder builder, String[] fields, boolean classic)
	{
		StringFunction func = StringFunction.encodeMap.get(fields[0].toUpperCase());
		if (func == null)
			throw new TagEncodingException("Invalid name for function: " + fields[0]);

		if (builder.creditsEncoding && func != StringFunction.FONT)
			throw new TagEncodingException("Invalid function: %s%n" +
				"Only the %s function may be called when using non-standard character sets.", fields[0], StringFunction.FONT.name);

		try {
			switch (func) {
				case VAR: {
					if (fields.length != 2) // [Func:arg0]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);

					int varIndex = checkRange(fields[0], parseIntArg(fields[1], classic), 0, StringConstants.getMaxStringVars() - 1);

					// if vars are supplied, replace tag bytes with them
					if (builder.vars == null || builder.vars[varIndex] == null)
						builder.addToTag(ControlCharacter.FUNC.code, func.code, varIndex);
					else
						builder.addToTag(builder.vars[varIndex]);
				}
					break;

				case FONT: // [font name]   or   [FONT:NAME]   with NAME = (normal|menu|title|subtitle)
				{
					if (!builder.allowFontChange)
						throw new TagEncodingException("Message Editor does not support setting font in Vars.");

					if (fields.length != 2)
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);

					StringFont font = StringFont.encodeMap.get(fields[1]);
					if (font == null)
						throw new TagEncodingException("Invalid font name: %s (expected '%s', '%s', '%s', or '%s').", fields[1],
							StringFont.NORMAL, StringFont.MENU, StringFont.TITLE, StringFont.SUBTITLE);

					builder.addToTag(ControlCharacter.FUNC.code, func.code, font.code);
					builder.creditsEncoding = (font != StringFont.NORMAL);
				}
					break;

				case VOICE: {
					if (fields.length != 2)
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);

					StringVoice voice = StringVoice.encodeMap.get(fields[1]);
					if (voice == null)
						throw new TagEncodingException("Invalid voice name: %s (expected '%s', '%s', or '%s').", fields[1],
							StringVoice.NORMAL.name, StringVoice.BOWSER.name, StringVoice.STAR.name);

					builder.addToTag(ControlCharacter.FUNC.code, func.code, voice.code);
				}
					break;

				case OPTION:
					if (fields.length != 2) // [Func arg0]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					int optIndex = parseIntArg(fields[1], classic);
					if (builder.doingSmartChoice) {
						builder.addToTag(ControlCharacter.FUNC.code, StringFunction.CURSOR.code, optIndex);
						builder.addToTag(ControlCharacter.FUNC.code, StringFunction.OPTION.code, optIndex);
						builder.smartChoiceIndex++;
					}
					else {
						if (optIndex != 0xFF)
							checkRange(fields[0], optIndex, 0, 5);
						builder.addToTag(ControlCharacter.FUNC.code, func.code, optIndex);
					}
					break;

				case END_CHOICE:
					if (builder.doingSmartChoice) {
						builder.addToTag(ControlCharacter.FUNC.code, StringFunction.OPTION.code, 0xFF);
						builder.addToTag(ControlCharacter.FUNC.code, StringFunction.DELAY_ON.code);

						if (fields.length == 2) {
							int cancelOption = findIntArg(fields, "cancel", 1, true)[0];
							builder.addToTag(ControlCharacter.FUNC.code, StringFunction.SET_CANCEL.code, cancelOption);
						}
						else if (fields.length != 1)
							throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 0 or 1).", fields[0],
								fields.length - 1);

						builder.addToTag(ControlCharacter.FUNC.code, StringFunction.END_CHOICE.code, builder.smartChoiceIndex);
					}
					else {
						if (fields.length != 2) // [Func arg0]
							throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
						builder.addToTag(ControlCharacter.FUNC.code, func.code);
						builder.addToTag(checkRange(fields[0], parseIntArg(fields[1], classic), 0, 5));
					}
					break;

				default:
					builder.addToTag(ControlCharacter.FUNC.code, func.code);
					encodeFunctionArgs(builder, func, fields, classic);
					break;
			}
		}
		catch (InvalidInputException e) {
			throw new TagEncodingException("Unable to parse arg for function %s: %n%s", fields[0], e.getMessage());
		}
	}

	private static void encodeFunctionArgs(StringEncoder builder, StringFunction func, String[] fields, boolean classic) throws InvalidInputException
	{
		if (classic) {
			if (func.args + 1 != fields.length) // Func:args...
				throw new TagEncodingException("Function %s has incorrect arg count: %d (expected %d).", fields[0], fields.length - 1, func.args);

			for (int i = 1; i <= func.args; i++)
				builder.addArgU8(fields[0], parseClassicIntArg(fields[i]));
		}
		else {
			switch (func) {
				case VAR:
				case FONT:
				case START_FX:
				case END_FX:
				case OPTION:
				case END_CHOICE:
					throw new IllegalStateException("LOGIC ERROR: " + func.name + " in encodeFunctionArgs!");

				// no args:
				case YIELD:
				case FUNC_03:
				case SKIP_OFF:
				case INPUT_OFF:
				case INPUT_ON:
				case DELAY_OFF:
				case DELAY_ON:
				case SIZE_RESET:
				case PUSH_POS:
				case POP_POS:
				case PUSH_COLOR:
				case POP_COLOR:
				case ENABLE_CDOWN:
					if (fields.length != 1) // [Func]
						throw new TagEncodingException("Function %s can't have args.", fields[0]);
					break;

				case SET_X:
					if (fields.length != 2) // [Func arg0]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					int value = checkRange(fields[0], parseNewIntArg(fields[1]), 0, 320);
					builder.addToTag((value >> 8) & 0xFF);
					builder.addToTag(value & 0xFF);
					break;

				case VARIANT:
					if (fields.length != 2) // [Func arg0]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					int variant = parseNewIntArg(fields[1]);
					if (builder.creditsEncoding)
						checkRange(fields[0], variant, 0, 0);
					else
						checkRange(fields[0], variant, 0, 3);
					builder.addArgU8(fields[0], variant);
					break;

				// 1 arg
				case COLOR:
				case SPACING:
				case SCROLL:
				case SET_Y:
				case RIGHT:
				case DOWN:
				case UP:
				case CENTER_X:
					if (fields.length != 2) // [Func arg0]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					builder.addArgU8(fields[0], parseNewIntArg(fields[1]));
					break;

				case VOLUME:
					if (fields.length != 2) // [Func arg0]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					Float[] values = findFloatArg(fields, "percent", 1, false);
					if (values != null)
						builder.addArgsU8(fields[0], Math.round(MathUtil.clamp(values[0], 0.0f, 100.0f) * 255.0f / 100.0f));
					else
						builder.addArgsU8(fields[0], parseIntArg(fields[1], classic));
					break;

				case SET_REWIND:
					if (fields.length != 2) // [Func arg0]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					builder.addToTag(checkRange(fields[0], parseNewIntArg(fields[1]), 0, 1));
					break;

				case SET_CANCEL:
					if (builder.doingSmartChoice)
						throw new TagEncodingException("Can't use %s with smart choices.", fields[0], fields.length - 1);
					if (fields.length != 2) // [Func arg0]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					builder.addToTag(checkRange(fields[0], parseNewIntArg(fields[1]), 0, 6));
					break;

				case CURSOR:
					if (builder.doingSmartChoice)
						throw new TagEncodingException("Can't use %s with smart choices.", fields[0], fields.length - 1);
					if (fields.length != 2) // [Func arg0]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					builder.addToTag(checkRange(fields[0], parseNewIntArg(fields[1]), 0, 5));
					break;

				// N args
				case ANIM_SPRITE: {
					if (fields.length != 3) // [Func arg0 arg1]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 2).", fields[0], fields.length - 1);
					Integer[] spriteID = findIntArg(fields, "spriteID", 1, true);
					Integer[] raster = findIntArg(fields, "raster", 1, true);
					builder.addArgU16(fields[0], spriteID[0]);
					builder.addArgU8(fields[0], raster[0]);
				}
					break;

				case ANIM_DELAY: {
					if (fields.length != 3) // [Func arg0 arg1]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 2).", fields[0], fields.length - 1);
					Integer[] index = findIntArg(fields, "index", 1, true);
					Integer[] delay = findIntArg(fields, "delay", 1, true);
					builder.addArgsU8(fields[0], 0, index[0], delay[0]);
				}
					break;

				case ANIM_LOOP:
				case ANIM_DONE:
					if (fields.length != func.args + 1) // [Func]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 0).", fields[0], fields.length - 1);
					for (int i = 0; i < func.args; i++)
						builder.addArgU8(fields[0], 0);
					break;

				case SET_CURSOR: {
					if (fields.length != 2) // [size XX,YY]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					String[] sizes = fields[1].split(",");
					if (sizes.length != 2) // XX YY
						throw new TagEncodingException("Function %s has incorrect arg format: %s (expected XX,YY)", fields[0], fields[1]);
					builder.addArgsU8(fields[0], parseNewIntArg(sizes[0]), parseNewIntArg(sizes[1]));
				}
					break;

				case SIZE: {
					if (fields.length != 2) // [size XX,YY]
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					String[] sizes = fields[1].split(",");
					if (sizes.length == 1) // same size X/Y
						builder.addArgsU8(fields[0], parseNewIntArg(sizes[0]), parseNewIntArg(sizes[0]));
					else if (sizes.length == 2) // XX YY
						builder.addArgsU8(fields[0], parseNewIntArg(sizes[0]), parseNewIntArg(sizes[1]));
					else
						throw new TagEncodingException("Function %s has incorrect arg format: %s (expected XX,YY)", fields[0], fields[1]);
				}
					break;

				// labeled args
				case SPEED: { // [speed delay=%d chars=%d]
					if (fields.length != 3)
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 2).", fields[0], fields.length - 1);
					Integer[] delay = findIntArg(fields, "delay", 1, true);
					Integer[] chars = findIntArg(fields, "chars", 1, true);
					builder.addArgsU8(fields[0], delay[0], chars[0]);
				}
					break;

				case ITEM_ICON: { // [icon itemID=%d]   or   [icon itemName=%s]
					if (fields.length != 2)
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					int itemID;
					Integer[] id = findIntArg(fields, "itemID", 1, false);
					if (id == null) {
						String[] name = findStringArg(fields, "itemName", 1, false);
						if (name == null)
							throw new TagEncodingException("Function %s requires either itemID=XXX or itemName=NAME: %s", fields[0], fields[1]);
						itemID = ProjectDatabase.getItemID(name[0]);
					}
					else
						itemID = id[0];
					builder.addArgU16(fields[1], itemID);
				}
					break;

				case INLINE_IMAGE: {
					if (fields.length != 2)
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					int index = checkRange(fields[0], findIntArg(fields, "index", 1, true)[0], 0, 3);
					builder.addToTag(index);
				}
					break;

				case IMAGE: {
					int index = checkRange(fields[0], findIntArg(fields, "index", 1, true)[0], 0, 3);
					Integer[] pos = findIntArg(fields, "pos", 2, true);
					int border = checkRange(fields[0], findIntArg(fields, "hasBorder", 1, true)[0], 0, 1);
					Integer[] alpha = findIntArg(fields, "alpha", 1, true);
					Integer[] fadeAmount = findIntArg(fields, "fadeAmount", 1, true);
					builder.addToTag(index);
					builder.addArgU16(fields[0], pos[0]);
					builder.addArgU8(fields[0], pos[1]);
					builder.addToTag(border);
					builder.addArgU8(fields[0], alpha[0]);
					builder.addArgU8(fields[0], fadeAmount[0]);
				}
					break;

				case HIDE_IMAGE: { // [hideimage fadeAmount=%d]
					if (fields.length != 2)
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					Integer[] fade = findIntArg(fields, "fadeAmount", 1, true);
					builder.addArgU8(fields[0], fade[0]);
				}
					break;

				case VOICE: { // [voice name]   or   [VOICE:NAME]   with NAME = (normal|bowser|spirit)
					if (fields.length != 2)
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);

					StringVoice voice = StringVoice.encodeMap.get(fields[1].toUpperCase());
					if (voice == null)
						throw new TagEncodingException("Function %s has invalid option: %s (expected 'normal', 'bowser', or 'spirit')", fields[0],
							fields[1]);

					builder.addToTag(voice.code);
				}
					break;

				case SETVOICE:
					if (fields.length != 2)
						throw new TagEncodingException("Function %s has incorrect arg count: %d (expected 1).", fields[0], fields.length - 1);
					Integer[] sounds = findIntArg(fields, "soundIDs", 2, true);

					builder.addToTag((sounds[0] >> 24) & 0xFF);
					builder.addToTag((sounds[0] >> 16) & 0xFF);
					builder.addToTag((sounds[0] >> 8) & 0xFF);
					builder.addToTag(sounds[0] & 0xFF);

					builder.addToTag((sounds[1] >> 24) & 0xFF);
					builder.addToTag((sounds[1] >> 16) & 0xFF);
					builder.addToTag((sounds[1] >> 8) & 0xFF);
					builder.addToTag(sounds[1] & 0xFF);
					break;
			}
		}
	}

	private static void encodeEffect(StringEncoder builder, String[] fields, boolean classic, boolean starting)
	{
		String effectName;
		StringEffect effect;

		if (starting) {
			effectName = fields[0];
			builder.addToTag(ControlCharacter.FUNC.code, StringFunction.START_FX.code);
		}
		else {
			effectName = fields[0].substring(1);
			builder.addToTag(ControlCharacter.FUNC.code, StringFunction.END_FX.code);
		}

		if (fields[0].equalsIgnoreCase(StringConstants.END_EFFECT_WILDCARD)) {
			if (builder.effects.isEmpty())
				throw new TagEncodingException("No effect to disable: " + effectName);

			effect = builder.effects.pop();
		}
		else {
			effect = StringEffect.encodeMap.get(effectName);
			if (effect == null)
				throw new TagEncodingException("Invalid name for effect: " + effectName);

			if (starting)
				builder.effects.push(effect);
			else
				builder.effects.clear();
		}

		builder.addToTag(effect.code);

		try {
			if (starting) {
				switch (effect) {
					case DITHER_FADE:
					case STATIC:
						if (fields.length != 2)
							throw new TagEncodingException("Effect %s has incorrect arg count: %d (expected %d).", effectName, fields.length - 2, 1);
						// only ever 1 arg here, so classic is same as new style
						if (classic) {
							builder.addArgsU8(effectName, parseIntArg(fields[1], classic));
						}
						else {
							Float[] values = findFloatArg(fields, "percent", 1, false);
							if (values != null)
								builder.addArgsU8(effectName, Math.round(MathUtil.clamp(values[0], 0.0f, 100.0f) * 255.0f / 100.0f));
							else
								builder.addArgsU8(effectName, parseIntArg(fields[1], classic));
						}
						break;

					case BLUR:
						if (fields.length != 2)
							throw new TagEncodingException("Effect %s has incorrect arg count: %d (expected %d).", effectName, fields.length - 2, 1);
						if (classic) {
							builder.addArgsU8(effectName, parseIntArg(fields[1], classic));
						}
						else {
							String[] values = findStringArg(fields, "dir", 1, true);
							if (values[0].equalsIgnoreCase("x"))
								builder.addArgU8(values[0], 0);
							else if (values[0].equalsIgnoreCase("y"))
								builder.addArgU8(values[0], 1);
							else if (values[0].equalsIgnoreCase("xy"))
								builder.addArgU8(values[0], 2);
							else
								throw new TagEncodingException("Effect %s has incorrect arg: %s (expected x, y, or xy).", effectName, values[0]);
						}
						break;

					default:
						if (fields.length != 1)
							throw new TagEncodingException("Effect %s can't have args.", effectName);
				}
			}
		}
		catch (InvalidInputException e) {
			throw new TagEncodingException("Unable to parse arg for effect %s: %n%s", effectName, e.getMessage());
		}
	}

	private static void encodeRaw(StringEncoder builder, String[] fields, boolean classic)
	{
		for (int i = 1; i < fields.length; i++) {
			try {
				builder.addArgsU8(fields[0], parseIntArg(fields[i], classic));
			}
			catch (InvalidInputException e) {
				throw new TagEncodingException("Invalid raw value: %s", fields[i]);
			}
		}
	}

	private static enum PseudoTag
	{
		// @formatter:off
		ANIMATION	("Animation"),
		START_CHOICE("StartChoice"),
		REWIND_ON	("RewindOn"),
		REWIND_OFF	("RewindOff"),
		SET_POS		("SetPos"),
		START		("START"),
		A			("A"),
		B			("B"),
		L			("L"),
		R			("R"),
		Z			("Z"),
		C_UP		("C-up"),
		C_DOWN		("C-down"),
		C_LEFT		("C-left"),
		C_RIGHT		("C-right");
		// @formatter:on

		public final String name;

		private PseudoTag(String name)
		{
			this.name = name;
		}

		public static final CaseInsensitiveMap<PseudoTag> encodeMap;

		static {
			encodeMap = new CaseInsensitiveMap<>();
			for (PseudoTag type : PseudoTag.values())
				encodeMap.put(type.name, type);
		}
	}

	private static void encodePseudoTag(StringEncoder builder, Sequence seq, String[] fields, boolean classic)
	{
		PseudoTag pt = PseudoTag.encodeMap.get(fields[0]);

		try {
			switch (pt) {
				case ANIMATION: { // [Animation spriteID=%X rasterIDs=%X,%X,%X,%X delays=%d,%d,%d,%d]
					if (fields.length != 4)
						throw new TagEncodingException("Invalid arg count for " + fields[0]);

					Integer[] spriteID = findIntArg(fields, "spriteID", 1, true);
					Integer[] rasters = findIntArg(fields, "rasterIDs", -1, true);
					Integer[] delays = findIntArg(fields, "delays", -1, true);

					if (rasters.length != delays.length)
						throw new TagEncodingException("rasterID and delay args must have same length!");

					seq.anim = new MessageAnim(spriteID[0], rasters, delays);
					for (Byte b : seq.anim.getBytes())
						builder.tagBytes.add(b);
				}
					break;

				case START_CHOICE:
					builder.doingSmartChoice = true;
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.DELAY_OFF.code);
					break;

				case REWIND_ON:
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.SET_REWIND.code, 1);
					break;

				case REWIND_OFF:
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.SET_REWIND.code, 0);
					break;

				case SET_POS: {
					int x, y;

					if (classic) // [SetPos:XXXX:YY]
					{
						if (fields.length != 3)
							throw new TagEncodingException("Invalid arg count for %s", fields[0]);
						x = parseClassicIntArg(fields[1]);
						y = parseClassicIntArg(fields[2]);
					}
					else // [SetPos X,Y]
					{
						if (fields.length != 2 || !fields[1].matches(REGEX_2_INT))
							throw new TagEncodingException("Invalid arg format for %s", fields[0]);
						String[] args = fields[1].split(",");
						if (args.length != 2)
							throw new TagEncodingException("Invalid arg format for %s, expected X,Y", fields[0]);
						x = parseNewIntArg(args[0]);
						y = parseNewIntArg(args[1]);
					}

					if (x < 0 || x > 320)
						throw new TagEncodingException("X coordinate is out of range [0,320]: %d", x);

					if (y < -255 || y > 255)
						throw new TagEncodingException("Y coordinate is out of range [-255,255]: %d", y);

					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.SET_X.code, (x >> 8), x);
					if (y < 0) {
						builder.addToTag(ControlCharacter.FUNC.code, StringFunction.SET_Y.code, 0);
						builder.addToTag(ControlCharacter.FUNC.code, StringFunction.UP.code, -y);
					}
					else
						builder.addToTag(ControlCharacter.FUNC.code, StringFunction.SET_Y.code, y);
				}
					break;

				case A:
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.PUSH_COLOR.code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.COLOR.code, 0x10);
					builder.addToTag(SpecialCharacter.A.code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.POP_COLOR.code);
					break;

				case B:
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.PUSH_COLOR.code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.COLOR.code, 0x11);
					builder.addToTag(SpecialCharacter.B.code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.POP_COLOR.code);
					break;

				case START:
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.PUSH_COLOR.code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.COLOR.code, 0x12);
					builder.addToTag(SpecialCharacter.START.code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.POP_COLOR.code);
					break;

				case C_UP:
				case C_DOWN:
				case C_LEFT:
				case C_RIGHT:
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.PUSH_COLOR.code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.COLOR.code, 0x13);
					builder.addToTag(SpecialCharacter.encodeMap.get("~" + fields[0]).code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.POP_COLOR.code);
					break;

				case Z:
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.PUSH_COLOR.code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.COLOR.code, 0x14);
					builder.addToTag(SpecialCharacter.Z.code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.POP_COLOR.code);
					break;

				case L:
				case R:
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.PUSH_COLOR.code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.COLOR.code, 0x15);
					builder.addToTag(SpecialCharacter.encodeMap.get("~" + fields[0]).code);
					builder.addToTag(ControlCharacter.FUNC.code, StringFunction.POP_COLOR.code);
					break;
			}
		}
		catch (InvalidInputException e) {
			throw new TagEncodingException("Unable to parse arg for %s: %n%s", fields[0], e.getMessage());
		}
	}

	private static int checkRangeU8(String name, int value)
	{
		return checkRange(name, value, 0, 0xFF);
	}

	private static int checkRangeU16(String name, int value)
	{
		return checkRange(name, value, 0, 0xFFFF);
	}

	private static int checkRange(String name, int value, int min, int max)
	{
		if (value < min || value > max)
			throw new TagEncodingException("Value for %s is out of range: %d (expected %d to %d).", name, value, min, max);
		return value;
	}

	private static int parseIntArg(String arg, boolean classic) throws InvalidInputException
	{
		if (classic)
			return parseClassicIntArg(arg);
		else
			return parseNewIntArg(arg);
	}

	private static int parseClassicIntArg(String arg) throws InvalidInputException
	{
		return DataUtils.parseIntString(arg);
	}

	private static int parseNewIntArg(String arg) throws InvalidInputException
	{
		try {
			if (arg.toUpperCase().startsWith("0X"))
				return Integer.parseInt(arg.substring(2), 16);
			else
				return Integer.parseInt(arg);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException(e);
		}
	}

	private static Integer[] findIntArg(String[] fields, String string, int expectedSize, boolean required) throws InvalidInputException
	{
		for (String field : fields) {
			KVMatcher.reset(field);
			if (KVMatcher.matches()) {
				if (!KVMatcher.group(1).equalsIgnoreCase(string))
					continue;
				String[] svals = KVMatcher.group(2).split(",");
				if (expectedSize > 0 && svals.length != expectedSize)
					throw new InvalidInputException("Number of elements is incorrect for %s: %d (expected %d)", string, svals.length, expectedSize);
				Integer[] values = new Integer[svals.length];
				for (int j = 0; j < values.length; j++)
					values[j] = parseNewIntArg(svals[j]);
				return values;
			}
		}

		if (required)
			throw new InvalidInputException("Could not find required arg: " + string);
		else
			return null;
	}

	private static Float[] findFloatArg(String[] fields, String string, int expectedSize, boolean required) throws InvalidInputException
	{
		for (String field : fields) {
			KVMatcher.reset(field);
			if (KVMatcher.matches()) {
				if (!KVMatcher.group(1).equalsIgnoreCase(string))
					continue;
				String[] svals = KVMatcher.group(2).split(",");
				if (expectedSize > 0 && svals.length != expectedSize)
					throw new InvalidInputException("Number of elements is incorrect for %s: %d (expected %d)", string, svals.length, expectedSize);
				Float[] values = new Float[svals.length];
				for (int j = 0; j < values.length; j++) {
					try {
						values[j] = Float.parseFloat(svals[j]);
					}
					catch (NumberFormatException e) {
						throw new InvalidInputException("Could not parse float value: " + svals[j]);
					}
				}
				return values;
			}
		}

		if (required)
			throw new InvalidInputException("Could not find required arg: " + string);
		else
			return null;
	}

	private static String[] findStringArg(String[] fields, String string, int expectedSize, boolean required) throws InvalidInputException
	{
		for (String field : fields) {
			KVMatcher.reset(field);
			if (KVMatcher.matches()) {
				if (!KVMatcher.group(1).equalsIgnoreCase(string))
					continue;
				String[] svals = KVMatcher.group(2).split(",");
				if (expectedSize > 0 && svals.length != expectedSize)
					throw new InvalidInputException("Number of elements is incorrect for %s: %d (expected %d)", string, svals.length, expectedSize);
				return svals;
			}
		}

		if (required)
			throw new InvalidInputException("Could not find required arg: " + string);
		else
			return null;
	}
}
