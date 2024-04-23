package game;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.input.InvalidInputException;

@Deprecated
public enum ScriptVariable
{
	// @formatter:off
	Literal		("LITERAL",		"Literal", 0),
	// Unknown	("UNKNOWN",		"Unknown", 250000000), 	// always -250000000, does not print with debug, and has no code in GetVariable, is this a type?
	FixedReal	("EVT_FLOAT",	"Fixed", 230000000),
	FlagArray	("ArrayFlag",  	"FlagArray", 210000000),
	Array		("ArrayVar",  	"Array", 190000000, 0x4000), // size is just heuristic
	GameFlag	("GameFlag", 	"GameFlag", 130000000, 0x800),
	GameByte	("GameByte",  	"GameByte", 170000000, 0x200),
	AreaFlag	("AreaFlag", 	"AreaFlag", 110000000, 0x100),
	AreaByte	("AreaByte",  	"AreaByte", 150000000, 0x10),
	MapFlag		("MapFlag",   	"MapFlag", 	90000000, 0x60),
	MapVar		("MapVar",   	"MapVar", 	50000000, 0x10),
	Flag		("LocalFlag",   "Flag", 	70000000, 0x60),
	Var			("LocalVar",   	"Var", 		30000000, 0x10),
	Temp		("TEMP", "Temp", 30000000 - 0x100, 0x100),
	Dynamic		("DYNAMIC", "Dynamic", 30000000 - 0x200, 0x400),
	Debug		("DEBUG", "Debug", 30000000 - 0x600, 0x4),
	ModFlag		("MSWF", "ModFlag", 130000000 - 0x1000, 0x8000),
	ModByte		("MSW",  "ModByte", 170000000 - 0x1000, 0x1000);
	// @formatter:on

	public static final int EXTENDED_INDEX_OFFSET = 0x1000;

	private final String decompName;
	private final String name;
	private final int offset;
	private final int max;
	private final boolean hasMax;

	private static final HashMap<String, String> shorthandVarNames = new HashMap<>();

	static {
		for (int i = 0; i < Var.getMaxIndex(); i++)
			shorthandVarNames.put(
				String.format("LocalVar(%d)", i),
				String.format("LVar%X", i)
			);
	}

	private ScriptVariable(String internalName, String name, int offset)
	{
		this.decompName = internalName;
		this.name = name;
		this.offset = offset;
		hasMax = false;
		this.max = 0;
	}

	private ScriptVariable(String internalName, String name, int offset, int max)
	{
		this.decompName = internalName;
		this.name = name;
		this.offset = offset;
		hasMax = true;
		this.max = max;
	}

	private static boolean optRoundFixed = false;

	private static final HashMap<String, ScriptVariable> nameMap;

	static {
		nameMap = new HashMap<>();
		for (ScriptVariable v : ScriptVariable.values()) {
			if (v != Literal) {
				nameMap.put(v.name, v);
				nameMap.put(v.decompName, v);
			}
		}
	}

	public String getTypeName()
	{ return name; }

	public int getMaxIndex()
	{ return max; }

	public int getOffset()
	{ return offset; }

	private static final Pattern VarOffsetPattern = Pattern.compile("\\*(\\w+)\\[([\\-\\+]?[\\.0-9A-Fa-f]+['`]?)\\]");
	private static final Matcher VarOffsetMatcher = VarOffsetPattern.matcher("");

	private static final Pattern VarNamePattern = Pattern.compile("\\*[A-Za-z][\\w:]*(?:\\[\\S+\\])?");
	private static final Matcher VarNameMatcher = VarNamePattern.matcher("");

	public static boolean isValidName(String name)
	{
		VarNameMatcher.reset(name);
		return VarNameMatcher.matches();
	}

	public static String getString(ScriptVariable type, int offset)
	{
		switch (type) {
			//case Unknown:
			case FlagArray:
			case Array:
				return String.format("*%s[%X]", type.name, offset);
			case ModByte:
			case ModFlag:
				return String.format("*%s[%04X]", type.name, offset);
			case GameByte:
			case GameFlag:
				return String.format("*%s[%03X]", type.name, offset);
			case AreaFlag:
				return String.format("*%s[%02X]", type.name, offset);
			case MapFlag:
			case Flag:
				return String.format("*%s[%02X]", type.name, offset);
			case AreaByte:
			case MapVar:
			case Var:
			case Debug:
				return String.format("*%s[%1X]", type.name, offset);
			case Temp:
			case Dynamic:
				return String.format("*%s[%02X]", type.name, offset);
			case Literal:
			case FixedReal:
			default:
				throw new UnsupportedOperationException("Cannot use index with ScriptVariable type " + type);
		}
	}

	public static int getReference(ScriptVariable type, int index)
	{
		return index - type.offset;
	}

	private String toString(int v)
	{
		switch (this) {
			case Literal:
				return v + "";
			case FixedReal:
				float f = (v + 230000000) / 1024.0f;

				// if fixed is close to a number with only one or two decimal digits,
				// it should print as that number.
				// ie, *Fixed[0.80078125] --> *Fixed[0.8]
				if (optRoundFixed) {
					float g = f * 100.0f;
					float whole = Math.round(g);
					if (Math.abs(g - whole) <= 100.0f / 1024.0f)
						f = whole / 100.0f;
				}

				return String.format("%s(%s)", decompName, f);
			//case Unknown:
			case FlagArray:
			case Array:
				return String.format("%s(%d)", decompName, v + offset);
			case GameByte:
			case GameFlag:
			case AreaFlag:
				return String.format("%s(%d)", decompName, v + offset);
			case MapFlag:
			case Flag:
				return String.format("%s(%d)", decompName, v + offset);
			case AreaByte:
			case MapVar:
			case Var:
			case Debug:
				return String.format("%s(%d)", decompName, v + offset);
			case Temp:
			case Dynamic:
				return String.format("%s[%02X]", decompName, v + offset);
			case ModByte:
			case ModFlag:
				throw new IllegalStateException("Encountered mod variable while decoding!");
		}

		throw new IllegalStateException(String.format("Unknown variable type for argument: %08X", v));
	}

	public static ScriptVariable getType(int v)
	{
		if (v > -250000000) {
			//if(v <= -250000000)		return Unknown;
			if (v <= -220000000)
				return FixedReal;
			else if (v <= -200000000)
				return FlagArray;
			else if (v <= -180000000)
				return Array;
			else if (v <= -160000000)
				return GameByte;
			else if (v <= -140000000)
				return AreaByte;
			else if (v <= -120000000)
				return GameFlag;
			else if (v <= -100000000)
				return AreaFlag;
			else if (v <= -80000000)
				return MapFlag;
			else if (v <= -60000000)
				return Flag;
			else if (v <= -40000000)
				return MapVar;
			else if (v <= -20000000)
				return Var;
		}
		return Literal;
	}

	public static boolean isScriptVariable(int v)
	{
		if (v <= -250000000 || v > -20000000)
			return false;

		ScriptVariable type = getType(v);
		if (type == Literal)
			return false;

		if (!type.hasMax)
			return true;

		int index = v + type.offset;
		if (index < 0 || index > type.max)
			return false;

		return true;
	}

	public static String getScriptVariable(int v)
	{
		ScriptVariable type = getType(v);

		if (type == Var)
			return String.format("LVar%X", (v + Var.offset));

		return getType(v).toString(v);
	}

	public static String parseScriptVariable(String s) throws InvalidInputException
	{
		return String.format("%08X", getScriptVariableReference(s));
	}

	public static ScriptVariable getTypeOf(String s)
	{
		if (shorthandVarNames.containsKey(s))
			s = shorthandVarNames.get(s);

		VarOffsetMatcher.reset(s);
		if (!VarOffsetMatcher.matches())
			return null;

		return nameMap.get(VarOffsetMatcher.group(1));
	}

	public static int getScriptVariableReference(String s) throws InvalidInputException
	{
		if (s.isEmpty())
			throw new InvalidInputException("Script variable is missing!", s);

		if (shorthandVarNames.containsKey(s))
			s = shorthandVarNames.get(s);

		VarOffsetMatcher.reset(s);
		if (!VarOffsetMatcher.matches())
			throw new InvalidInputException("Could not parse script variable: %s", s);

		String variableType = VarOffsetMatcher.group(1);
		String index = VarOffsetMatcher.group(2);

		ScriptVariable type = nameMap.get(variableType);

		if (type == null)
			throw new InvalidInputException("Unrecognized script variable: %s", s);

		if (type == Literal)
			throw new InvalidInputException("Invalid script variable: %s", s);

		switch (type) {
			case FixedReal:
				float f = Float.parseFloat(index) * 1024.0f;
				return Math.round(f) - FixedReal.offset;
			default:
				int u = DataUtils.parseIntString(index);
				if (type.hasMax && u >= type.max)
					throw new InvalidInputException("Maximum index for %s is 0x%X, you used %s", type.toString(), type.max, s);
				return u - type.offset;
		}
	}

	public static int getScriptVariableIndex(String s) throws InvalidInputException
	{
		if (shorthandVarNames.containsKey(s))
			s = shorthandVarNames.get(s);

		VarOffsetMatcher.reset(s);
		if (!VarOffsetMatcher.matches())
			throw new InvalidInputException("Could not parse script variable: %s", s);

		String variableType = VarOffsetMatcher.group(1);
		String index = VarOffsetMatcher.group(2);

		ScriptVariable type = nameMap.get(variableType);

		if (type == null)
			throw new InvalidInputException("Unrecognized script variable: %s", s);

		if (type == Literal)
			throw new InvalidInputException("Invalid script variable: %s", s);

		switch (type) {
			case FixedReal:
				throw new InvalidInputException("Cannot resolve index for float variable: %s", s);
			default:
				int u = DataUtils.parseIntString(index);
				if (type.hasMax && u >= type.max)
					throw new InvalidInputException("Maximum index for %s is 0x%X, you used %s", type.toString(), type.max, s);
				if (type == ModFlag || type == ModByte)
					return u + EXTENDED_INDEX_OFFSET;
				else
					return u;
		}
	}
}
