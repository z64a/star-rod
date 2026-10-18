package game;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import game.CIntegerExpression.Value;
import util.CaseInsensitiveMap;

// Reads top-level enum declarations, not general C. Includes are intentionally ignored,
// as they were with CDT's empty include provider. Undefined preprocessor names are zero.
final class DecompEnumParser
{
	record Token(String text, int line, int pos)
	{
		boolean identifier()
		{
			return !text.isEmpty() && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_');
		}
	}

	private record Macro(List<Token> body, boolean function)
	{}

	private record Constant(Value value, String problem)
	{}

	private static class Branch
	{
		final boolean parent;
		boolean active;
		boolean taken;
		boolean seenElse;

		Branch(boolean parent, boolean condition)
		{
			this.parent = parent;
			active = parent && condition;
			taken = active;
		}
	}

	private final String sourceName;
	private final String source;
	private final Map<String, Macro> macros = new HashMap<>();
	private final Map<String, Constant> constants = new HashMap<>();
	private List<Token> tokens;
	private int pos;

	DecompEnumParser(String sourceName, String source)
	{
		this.sourceName = sourceName;
		this.source = source;
	}

	CaseInsensitiveMap<DecompEnum> parse(Set<String> required) throws IOException
	{
		tokens = preprocess(lex());
		CaseInsensitiveMap<DecompEnum> enums = new CaseInsensitiveMap<>();
		int depth = 0;
		while (pos < tokens.size()) {
			String text = tokens.get(pos).text;
			if (depth == 0 && text.equals("enum")) {
				parseEnum(enums, required);
				continue;
			}
			if (text.equals("{"))
				depth++;
			else if (text.equals("}"))
				depth--;
			pos++;
		}
		for (String name : required) {
			if (!enums.containsKey(name))
				throw new IOException(sourceName + ": Missing required enum '" + name + "'");
		}
		return enums;
	}

	private void parseEnum(CaseInsensitiveMap<DecompEnum> enums, Set<String> required) throws IOException
	{
		Token start = tokens.get(pos++);
		String name = pos < tokens.size() && tokens.get(pos).identifier() ? tokens.get(pos++).text : "";
		if (pos == tokens.size() || !tokens.get(pos).text.equals("{"))
			return; // Forward declaration or use of an enum type.
		pos++;
		boolean selected = !name.isEmpty() && (required.isEmpty() || required.stream().anyMatch(name::equalsIgnoreCase));
		DecompEnum result = new DecompEnum();
		Constant previous = new Constant(new Value(-1), null);
		while (pos < tokens.size() && !tokens.get(pos).text.equals("}")) {
			int begin = pos;
			int depth = 0;
			while (pos < tokens.size()) {
				String text = tokens.get(pos).text;
				if (depth == 0 && (text.equals(",") || text.equals("}")))
					break;
				if (text.equals("(") || text.equals("[") || text.equals("{"))
					depth++;
				else if (text.equals(")") || text.equals("]") || text.equals("}"))
					depth--;
				pos++;
			}
			List<Token> entry = tokens.subList(begin, pos);
			if (entry.isEmpty() || !entry.get(0).identifier())
				throw error(start, "Expected an enumerator in '" + name + "'");
			Token key = entry.get(0);
			try {
				Value value;
				if (entry.size() == 1) {
					if (previous.value == null)
						throw error(key, "Cannot infer a value after an unresolved enumerator: " + previous.problem);
					long next = previous.value.number() + 1;
					value = new Value(next, 32, next > Integer.MAX_VALUE);
				}
				else {
					if (!entry.get(1).text.equals("="))
						throw error(key, "Expected '=' after '" + key.text + "'");
					value = new CIntegerExpression(sourceName, entry.subList(2, entry.size()), token -> {
						Constant constant = constants.get(token.text);
						if (constant == null)
							throw error(token, "Unknown integer constant '" + token.text + "' (includes and function-like macros are not expanded)");
						if (constant.value == null)
							throw error(token, "Cannot resolve '" + token.text + "': " + constant.problem);
						return constant.value;
					}).evaluate();
				}
				long number = value.number();
				if (value.bits() == 64 && (value.unsigned() ? Long.compareUnsigned(number, 0xFFFFFFFFL) > 0
					: number < Integer.MIN_VALUE || number > 0xFFFFFFFFL))
					throw error(key, "Enum value is outside the 32-bit range");
				// Enumerators use int when representable, even if their initializer used a wider type.
				previous = new Constant(new Value(number, 32, number > Integer.MAX_VALUE), null);
				result.add(key.text, (int) number);
			}
			catch (IOException e) {
				if (selected)
					throw new IOException(e.getMessage() + " (enum " + name + ", entry " + key.text + ")", e);
				// Unused enums may depend on excluded headers. Preserve the failure if a required enum refers to them.
				previous = new Constant(null, e.getMessage());
			}
			constants.put(key.text, previous);
			if (pos < tokens.size() && tokens.get(pos).text.equals(","))
				pos++;
		}
		if (pos == tokens.size())
			throw error(start, "Unterminated enum '" + name + "'");
		pos++;
		if (selected)
			enums.put(name, result);
	}

	private List<Token> preprocess(List<Token> input) throws IOException
	{
		List<Token> output = new ArrayList<>();
		Deque<Branch> branches = new ArrayDeque<>();
		for (int begin = 0; begin < input.size();) {
			int end = begin;
			while (end < input.size() && !input.get(end).text.equals("\n"))
				end++;
			List<Token> line = input.subList(begin, end);
			begin = end + 1;
			if (line.isEmpty())
				continue;
			boolean active = branches.isEmpty() || branches.peek().active;
			if (!line.get(0).text.equals("#")) {
				if (active)
					expand(line, output, new HashSet<>());
				continue;
			}
			if (line.size() == 1)
				continue;
			Token directive = line.get(1);
			List<Token> args = line.subList(2, line.size());
			switch (directive.text) {
				case "if":
					branches.push(new Branch(active, active && condition(args)));
					break;
				case "ifdef":
				case "ifndef":
					checkIdentifier(args, directive);
					boolean defined = macros.containsKey(args.get(0).text);
					branches.push(new Branch(active, directive.text.equals("ifdef") ? defined : !defined));
					break;
				case "elif":
				case "else":
					if (branches.isEmpty() || branches.peek().seenElse)
						throw error(directive, "Unexpected #" + directive.text);
					Branch branch = branches.peek();
					branch.seenElse = directive.text.equals("else");
					branch.active = branch.parent && !branch.taken && (branch.seenElse || condition(args));
					branch.taken |= branch.active;
					break;
				case "endif":
					if (branches.isEmpty())
						throw error(directive, "Unexpected #endif");
					branches.pop();
					break;
				default:
					if (active)
						directive(directive, args);
					break;
			}
		}
		if (!branches.isEmpty())
			throw new IOException(sourceName + ": Unterminated preprocessor conditional");
		return output;
	}

	private void directive(Token directive, List<Token> args) throws IOException
	{
		switch (directive.text) {
			case "define":
				if (args.isEmpty() || !args.get(0).identifier())
					throw error(directive, "Expected a macro name");
				Token name = args.get(0);
				boolean function = args.size() > 1 && args.get(1).text.equals("(") && args.get(1).pos == name.pos + name.text.length();
				macros.put(name.text, new Macro(List.copyOf(args.subList(1, args.size())), function));
				break;
			case "undef":
				checkIdentifier(args, directive);
				macros.remove(args.get(0).text);
				break;
			case "include":
			case "pragma":
			case "line":
				break;
			default:
				throw error(directive, "Unsupported active directive #" + directive.text + " "
					+ String.join(" ", args.stream().map(Token::text).toList()));
		}
	}

	private void checkIdentifier(List<Token> args, Token directive) throws IOException
	{
		if (args.size() != 1 || !args.get(0).identifier())
			throw error(directive, "Expected one identifier after #" + directive.text);
	}

	private boolean condition(List<Token> args) throws IOException
	{
		List<Token> replaced = new ArrayList<>();
		for (int i = 0; i < args.size(); i++) {
			Token token = args.get(i);
			if (!token.text.equals("defined")) {
				replaced.add(token);
				continue;
			}
			boolean parens = i + 1 < args.size() && args.get(i + 1).text.equals("(");
			i += parens ? 2 : 1;
			if (i >= args.size() || !args.get(i).identifier())
				throw error(token, "Expected an identifier after defined");
			replaced.add(new Token(macros.containsKey(args.get(i).text) ? "1" : "0", token.line, token.pos));
			if (parens && (++i >= args.size() || !args.get(i).text.equals(")")))
				throw error(token, "Expected ')' after defined");
		}
		List<Token> expanded = new ArrayList<>();
		expand(replaced, expanded, new HashSet<>());
		return new CIntegerExpression(sourceName, expanded, token -> new Value(0, 64, false), true).evaluate().truth();
	}

	private void expand(List<Token> input, List<Token> output, Set<String> expanding) throws IOException
	{
		for (Token token : input) {
			if (expanding.size() > 64 || output.size() > 1000000)
				throw error(token, "Macro expansion is too large");
			Macro macro = macros.get(token.text);
			if (macro == null || macro.function || !expanding.add(token.text)) {
				output.add(token);
				continue;
			}
			List<Token> body = macro.body.stream().map(t -> new Token(t.text, token.line, token.pos)).toList();
			expand(body, output, expanding);
			expanding.remove(token.text);
		}
	}

	private List<Token> lex() throws IOException
	{
		// Splice continued lines before recognizing comments, retaining physical line numbers for errors.
		String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
		StringBuilder joined = new StringBuilder();
		List<Integer> lines = new ArrayList<>();
		int line = 1;
		for (int i = 0; i < normalized.length(); i++) {
			char c = normalized.charAt(i);
			if (c == '\\' && i + 1 < normalized.length() && normalized.charAt(i + 1) == '\n') {
				i++;
				line++;
				continue;
			}
			joined.append(c);
			lines.add(line);
			if (c == '\n')
				line++;
		}
		String text = joined.toString();
		List<Token> result = new ArrayList<>();
		Set<String> operators = Set.of("<<", ">>", "&&", "||", "==", "!=", "<=", ">=", "##", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "->");
		for (int i = 0; i < text.length();) {
			int begin = i;
			char c = text.charAt(i++);
			Token location = new Token("", lines.get(begin), begin);
			if (c != '\n' && (Character.isWhitespace(c) || c == '\uFEFF'))
				continue;
			if (text.startsWith("//", begin)) {
				while (i < text.length() && text.charAt(i) != '\n')
					i++;
				continue;
			}
			if (text.startsWith("/*", begin)) {
				int end = text.indexOf("*/", begin + 2);
				if (end < 0)
					throw error(location, "Unterminated comment");
				// A block comment is one space, even inside a preprocessor directive.
				i = end + 2;
				continue;
			}
			if (c == '\'' || c == '"') {
				while (i < text.length() && text.charAt(i) != c && text.charAt(i) != '\n') {
					if (text.charAt(i) == '\\')
						i++;
					i++;
				}
				if (i >= text.length() || text.charAt(i) != c)
					throw error(location, "Unterminated quoted literal");
				i++;
			}
			else if (Character.isDigit(c)) {
				// Keep preprocessing numbers intact so floats and malformed suffixes cannot become valid integer expressions.
				while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_' || text.charAt(i) == '.'
					|| ((text.charAt(i) == '+' || text.charAt(i) == '-') && "eEpP".indexOf(text.charAt(i - 1)) >= 0)))
					i++;
			}
			else if (Character.isLetter(c) || c == '_') {
				while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_'))
					i++;
			}
			else if (i < text.length() && operators.contains(text.substring(begin, i + 1)))
				i++;
			result.add(new Token(text.substring(begin, i), location.line, begin));
		}
		return result;
	}

	private IOException error(Token token, String message)
	{
		return new IOException(sourceName + ":" + token.line + ": " + message);
	}
}
