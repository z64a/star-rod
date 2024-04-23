package app.input;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.CaseInsensitiveMap;

public class PatchFileParser
{
	private static final Pattern DeclarationPattern = Pattern.compile("\\s*[@#](.+)"); // matches non-escaped comment character
	private static final Pattern WhitespacePattern = Pattern.compile("\\s+");

	public static class PatchUnit
	{
		public final AbstractSource source;
		public final boolean parsedAsString;
		public final Line declaration;
		public final List<Line> body;

		public int startLineNum;
		public int endLineNum;

		public boolean parsed;

		private PatchUnit(Line declaration, boolean string)
		{
			this.source = declaration.source;
			this.parsedAsString = string;
			this.declaration = declaration;
			this.startLineNum = declaration.lineNum;
			body = new LinkedList<>();
		}
	}

	public static List<PatchUnit> parse(List<Line> lines)
	{
		return parse(lines, new CaseInsensitiveMap<String>());
	}

	public static List<PatchUnit> parse(List<Line> lines, CaseInsensitiveMap<String> rules)
	{
		PatchFileParser parser = new PatchFileParser(lines, rules);
		return parser.getResults();
	}

	private static enum ParseMode
	{
		NONE, DECLARATION, BODY
	}

	private static enum ScanState
	{
		NORMAL, SINGLE_COMMENT, MULTI_COMMENT, STRING_LITERAL
	}

	// state
	private List<PatchUnit> units = new LinkedList<>();
	private PatchUnit currentUnit = null;
	private boolean stringMode = false;

	// overall file-reading state
	ParseMode parseMode = ParseMode.NONE;
	int parenDepth = 0;

	private List<PatchUnit> getResults()
	{ return units; }

	private PatchFileParser(List<Line> lines)
	{
		this(lines, new CaseInsensitiveMap<String>());
	}

	private PatchFileParser(List<Line> lines, CaseInsensitiveMap<String> rules)
	{
		lines = removeComments(lines);
		lines = doPreprocessor(lines, rules);

		//	for(Line currentLine : lines)
		//		System.out.println("  ~~" + currentLine.str);

		// character state
		ScanState scanState = ScanState.NORMAL;
		boolean escaping = false;

		Line mostRecentLine = null;
		for (Line currentLine : lines) {
			mostRecentLine = currentLine;
			StringBuilder lineBuilder = new StringBuilder();
			boolean braceLine = false;

			//	System.out.println(currentLine.str);

			String sourceText = currentLine.str;
			for (int i = 0; i < sourceText.length(); i++) {
				char c = sourceText.charAt(i);

				//	System.out.printf("%c : %s %s%n", c, parseMode, scanState);

				if (escaping) {
					lineBuilder.append(c);
					escaping = false;
				}
				else if (c == '\\') {
					if (parseMode == ParseMode.BODY && stringMode)
						lineBuilder.append(c);
					escaping = true;
				}
				else {
					switch (scanState) {
						case NORMAL:
							switch (c) {
								case '#':
								case '@':
									if (parseMode == ParseMode.NONE)
										parseMode = ParseMode.DECLARATION;
									lineBuilder.append(c);
									break;
								case '"':
									if (!stringMode)
										scanState = ScanState.STRING_LITERAL;
									lineBuilder.append(c);
									break;
								case '{':
									if (parenDepth == 0 && parseMode != ParseMode.DECLARATION)
										throw new InputFileException(currentLine, "Open brace encountered without declaration.");

									if (lineBuilder.length() > 0)
										addLine(currentLine, lineBuilder.toString());

									lineBuilder = new StringBuilder();
									braceLine = true;

									parenDepth++;
									if (parenDepth > 1)
										addLine(currentLine, "{");
									else
										parseMode = ParseMode.BODY;
									break;
								case '}':
									if (currentUnit == null)
										throw new InputFileException(currentLine, "Closing brace encountered without declaration.");

									if (lineBuilder.length() > 0)
										addLine(currentLine, lineBuilder.toString());

									lineBuilder = new StringBuilder();
									braceLine = true;

									if (parenDepth > 1) {
										addLine(currentLine, "}");
									}
									else {
										parseMode = ParseMode.NONE;
										currentUnit.endLineNum = currentLine.lineNum;
										currentUnit = null;
									}
									parenDepth--;
									if (parenDepth < 0)
										throw new InputFileException(currentLine, "Encountered unmatched closing brace.");
									break;
								default:
									lineBuilder.append(c);
									break;
							}
							break;

						case STRING_LITERAL:
							if (c == '"')
								scanState = ScanState.NORMAL;
							lineBuilder.append(c);
							break;

						case MULTI_COMMENT:
						case SINGLE_COMMENT:
							throw new IllegalStateException();
					}
				}
			}

			if (!braceLine || lineBuilder.length() > 0)
				addLine(currentLine, lineBuilder.toString());
		}

		if (parseMode == ParseMode.BODY)
			throw new InputFileException(mostRecentLine, "Missing } -- brace was not closed by end of file!");

		if (scanState == ScanState.MULTI_COMMENT)
			throw new InputFileException(mostRecentLine, "Missing %/ -- comment was not closed by end of file!");

		if (scanState == ScanState.STRING_LITERAL)
			throw new InputFileException(mostRecentLine, "Missing \" -- string was not closed by end of file!");
	}

	private void addLine(Line currentLine, String line)
	{
		switch (parseMode) {
			case BODY:
				if (stringMode) {
					// just add strings, leave their whitespace/empty lines alone
					currentUnit.body.add(currentLine.createLine(line));
				}
				else {
					if (line.isEmpty() || WhitespacePattern.matcher(line).matches())
						return; // empty line in non-string body --> skip

					// remove excess whitespace from non-string body
					line = WhitespacePattern.matcher(line).replaceAll(" ").trim();
					currentUnit.body.add(currentLine.createLine(line));
				}
				break;

			case DECLARATION:
				// serial declarations
			case NONE:
				if (line.isEmpty() || WhitespacePattern.matcher(line).matches())
					return; // empty line between outside body, fine

				if (DeclarationPattern.matcher(line).matches()) {
					String trimmed = line.trim();
					stringMode = trimmed.matches("(#(string|message)|(@|#new:|#export:)(String|Message)).+");
					currentUnit = new PatchUnit(currentLine.createLine(line.trim()), stringMode);
					units.add(currentUnit);
				}
				else {
					if (line.length() > 50)
						line = line.substring(0, 50) + " ...";
					throw new InputFileException(currentLine, "Expected declaration: " + line);
				}
				break;
		}
	}

	/**
	 * @param in unmodified lines from source file
	 * @return lines with EOL comments removed and multiline comments replaced by a single space
	 */
	public List<Line> removeComments(List<Line> in)
	{
		List<Line> out = new ArrayList<>(in.size());

		// distinction due to lines 'joined' by /% ... %/
		Line currentOutLine = null;

		boolean beginNextLine = true;
		StringBuilder lineBuilder = new StringBuilder();

		ScanState state = ScanState.NORMAL;
		boolean escaping = false;

		final char multilineCommentChar = '*';
		final char cantLookaheadChar = '?'; // dummy character that satisfies (c != '*' && c != '%')

		for (Line currentParseLine : in) {
			if (beginNextLine) {
				currentOutLine = currentParseLine;
				lineBuilder = new StringBuilder();
				beginNextLine = false;
			}

			// non-strings:		%	\%	/%	%/  \"	"
			// strings:			%	\%	/%	%/
			String sourceText = currentParseLine.str;
			scan_chars:
			for (int i = 0; i < sourceText.length(); i++) {
				char c = sourceText.charAt(i);
				char lookahead = (sourceText.length() > i + 1) ? sourceText.charAt(i + 1) : cantLookaheadChar;

				assert (c != '\r' && c != '\n');

				switch (state) {
					case NORMAL:
						// multiline comment
						if ((c == '/') && lookahead == multilineCommentChar) {
							state = ScanState.MULTI_COMMENT;
							lineBuilder.append(" "); // treat multi-line comments as whitespace
							i++;
							break;
						}
						else if (lookahead == '/') {
							// can't escape c-style comments
							state = ScanState.SINGLE_COMMENT;
							break scan_chars;
						}

						if (escaping) {
							escaping = false;
							lineBuilder.append(c);
						}
						else {
							switch (c) {
								case '%':
									state = ScanState.SINGLE_COMMENT;
									break scan_chars;
								case '"':
									if (!stringMode)
										state = ScanState.STRING_LITERAL;
									lineBuilder.append(c);
									break;
								case '\\':
									// do not consume escape chars at this stage
									lineBuilder.append(c);
									escaping = true;
									break;
								default:
									lineBuilder.append(c);
									break;
							}
						}
						break;

					case STRING_LITERAL:
						if (c == '"' && !escaping) {
							state = ScanState.NORMAL;
							lineBuilder.append(c);
							break;
						}
						if (escaping)
							escaping = false;
						if (c == '\\')
							escaping = true;
						lineBuilder.append(c);
						break;

					case MULTI_COMMENT:
						// can't be escaped
						if ((sourceText.length() > i + 1) && (c == multilineCommentChar) && (sourceText.charAt(i + 1) == '/')) {
							state = ScanState.NORMAL;
							i++;
							break;
						}
						break;

					case SINGLE_COMMENT:
						throw new IllegalStateException();
				}
			}

			// EOL reached
			switch (state) {
				case NORMAL:
				case STRING_LITERAL:
					beginNextLine = !escaping;
					break;
				case SINGLE_COMMENT:
					state = ScanState.NORMAL;
					beginNextLine = !escaping;
					break;
				case MULTI_COMMENT:
					break;
			}

			if (beginNextLine && lineBuilder.length() > 0)
				out.add(currentOutLine.createLine(lineBuilder.toString()));
		}

		return out;
	}

	/*
		#import Blah.txt Namespace
		{
			optName=optVal
			...
			optName=.Constant
			optName=##[SomeOpt]NotPartOfTheName
		}
	
		conditional compiles:
		##[IF:optName:Value]
			...
		##[ENDIF]
	
		##[IF:optName:value1]
			...
		##[ELSEIF:optName:value2]
			...
		##[ELSE]
			...
		##[ENDIF]
	
		replacement:
		##[VALUE:optName]
	 */

	private static class If
	{
		public final Line declaringLine;
		public final String declaration;

		public If(Line declaringLine, String declaration)
		{
			this.declaringLine = declaringLine;
			this.declaration = declaration;
		}

		boolean foundElse = false;
	}

	private static final Matcher PPDirectiveMatcher = Pattern.compile("##\\[([\\w:]+)\\]").matcher("");

	private static List<Line> doPreprocessor(List<Line> in, CaseInsensitiveMap<String> rules)
	{
		if (rules == null)
			throw new IllegalStateException("Empty rule definitions provided to pre-processor!");

		List<Line> out = new ArrayList<>(in.size());
		Stack<If> stack = new Stack<>();

		boolean skipping = false;

		for (Line line : in) {
			StringBuffer sbuf = new StringBuffer(line.str.length());

			PPDirectiveMatcher.reset(line.str);
			while (PPDirectiveMatcher.find()) {
				String directive = PPDirectiveMatcher.group(1);
				String[] tokens = directive.split(":");
				String value;

				switch (tokens[0].toUpperCase()) {
					case "IF":
					case "ELSEIF":
						if (!tokens[0].startsWith("ELSE")) {
							If newIf = new If(line, directive);
							stack.push(newIf);
						}
						else if (stack.size() < 1)
							throw new InputFileException(line, "ElseIf found without initial If: " + directive);
						if (skipping)
							continue;
						if (tokens.length != 2 && tokens.length != 3)
							throw new InputFileException(line, "Invalid If directive: " + directive);
						value = rules.get(tokens[1]);

						if (tokens.length == 3)
							skipping = !tokens[2].equalsIgnoreCase(value);
						else
							skipping = (value == null);

						PPDirectiveMatcher.appendReplacement(sbuf, "");
						break;

					case "IFNOT":
					case "ELSEIFNOT":
						if (!tokens[0].startsWith("ELSE")) {
							If newIf = new If(line, directive);
							stack.push(newIf);
						}
						else if (stack.size() < 1)
							throw new InputFileException(line, "ElseIf found without initial If: " + directive);
						if (skipping)
							continue;
						if (tokens.length != 2 && tokens.length != 3)
							throw new InputFileException(line, "Invalid If directive: " + directive);
						value = rules.get(tokens[1]);

						if (tokens.length == 3)
							skipping = tokens[2].equalsIgnoreCase(value);
						else
							skipping = (value != null);

						PPDirectiveMatcher.appendReplacement(sbuf, "");
						break;

					case "ELSE":
						if (tokens.length != 1)
							throw new InputFileException(line, "Invalid Else directive: " + directive);

						if (stack.size() < 1)
							throw new InputFileException(line, "Else found without initial If: " + directive);

						if (stack.peek().foundElse)
							throw new InputFileException(line, "Duplicate Else found for If: " + directive);

						stack.peek().foundElse = true;
						skipping = !skipping;
						PPDirectiveMatcher.appendReplacement(sbuf, "");
						break;

					case "ENDIF":
						if (tokens.length != 1)
							throw new InputFileException(line, "Invalid EndIf directive: " + directive);

						if (stack.size() < 1)
							throw new InputFileException(line, "EndIf directive with no matching If: " + directive);
						stack.pop();

						skipping = false;
						PPDirectiveMatcher.appendReplacement(sbuf, "");
						break;

					case "VALUE":
						if (skipping)
							continue;
						if (tokens.length != 2)
							throw new InputFileException(line, "Invalid Value directive: " + directive);
						value = rules.get(tokens[1]);
						if (value == null)
							throw new InputFileException(line, "Identifier not defined: " + tokens[1]);

						PPDirectiveMatcher.appendReplacement(sbuf, Matcher.quoteReplacement(value));
						break;
				}
			}

			if (!skipping) {
				PPDirectiveMatcher.appendTail(sbuf);
				for (String newline : sbuf.toString().split("\r?\n"))
					out.add(line.createLine(newline));
				//	out.add(line.createLine(sbuf.toString()));
			}
		}

		if (stack.size() > 0)
			throw new InputFileException(stack.peek().declaringLine,
				"If directive is not closed: " + stack.peek().declaration);

		return out;
	}
}
