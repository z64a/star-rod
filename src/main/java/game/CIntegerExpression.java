package game;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.DecompEnumParser.Token;

// Integer constant expressions for the game's 32-bit C target, including unsigned flags.
final class CIntegerExpression
{
	interface Resolver
	{
		Value resolve(Token token) throws IOException;
	}

	record Value(long number, int bits, boolean unsigned)
	{
		Value
		{
			if (bits == 32)
				number = unsigned ? number & 0xFFFFFFFFL : (int) number;
		}

		Value(long number)
		{
			this(number, 32, false);
		}

		boolean truth()
		{
			return number != 0;
		}
	}

	private static final Pattern LITERAL = Pattern.compile("(?i)(0x[0-9a-f]+|0b[01]+|0[0-7]*|[1-9][0-9]*)(u(?:ll|l)?|(?:ll|l)u?)?");
	private final String source;
	private final List<Token> tokens;
	private final Resolver resolver;
	private final boolean preprocessor;
	private int pos;

	CIntegerExpression(String source, List<Token> tokens, Resolver resolver)
	{
		this(source, tokens, resolver, false);
	}

	CIntegerExpression(String source, List<Token> tokens, Resolver resolver, boolean preprocessor)
	{
		this.source = source;
		this.tokens = tokens;
		this.resolver = resolver;
		this.preprocessor = preprocessor;
	}

	Value evaluate() throws IOException
	{
		if (tokens.size() > 512)
			throw error("Integer expression is too complex");
		Value value = conditional(true);
		if (pos != tokens.size())
			throw error("Unsupported expression near '" + tokens.get(pos).text() + "'");
		return value;
	}

	private Value conditional(boolean evaluate) throws IOException
	{
		Value condition = binary(1, evaluate);
		if (!take("?"))
			return condition;
		Value yes = conditional(evaluate && condition.truth());
		expect(":");
		Value no = conditional(evaluate && !condition.truth());
		Value type = common(yes, no);
		return new Value(condition.truth() ? yes.number : no.number, type.bits, type.unsigned);
	}

	private Value binary(int minimum, boolean evaluate) throws IOException
	{
		Value left = unary(evaluate);
		while (pos < tokens.size()) {
			String op = tokens.get(pos).text();
			int priority = precedence(op);
			if (priority < minimum)
				break;
			pos++;
			boolean rightNeeded = evaluate && !(op.equals("&&") && !left.truth()) && !(op.equals("||") && left.truth());
			Value right = binary(priority + 1, rightNeeded);
			left = apply(op, left, right, evaluate);
		}
		return left;
	}

	private Value unary(boolean evaluate) throws IOException
	{
		if (pos == tokens.size())
			throw error("Expected an integer expression");
		Token token = tokens.get(pos++);
		String text = token.text();
		if (text.equals("(")) {
			Value value = conditional(evaluate);
			expect(")");
			return value;
		}
		if (text.equals("+") || text.equals("-") || text.equals("~") || text.equals("!")) {
			Value value = unary(evaluate);
			switch (text) {
				case "+":
					return value;
				case "-":
					return new Value(-value.number, value.bits, value.unsigned);
				case "~":
					return new Value(~value.number, value.bits, value.unsigned);
				default:
					return logical(!value.truth());
			}
		}
		if (token.identifier())
			return resolver.resolve(token);
		Matcher matcher = LITERAL.matcher(text);
		if (!matcher.matches())
			throw error("Unsupported integer literal '" + text + "'");
		String digits = matcher.group(1);
		String suffix = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase();
		int radix = 10;
		if (digits.startsWith("0x") || digits.startsWith("0X")) {
			radix = 16;
			digits = digits.substring(2);
		}
		else if (digits.startsWith("0b") || digits.startsWith("0B")) {
			radix = 2;
			digits = digits.substring(2);
		}
		else if (digits.startsWith("0"))
			radix = 8;
		try {
			long number = Long.parseUnsignedLong(digits, radix);
			boolean unsigned = suffix.contains("u");
			// C preprocessors evaluate integer constants as intmax_t/uintmax_t.
			int bits = preprocessor || suffix.contains("ll") ? 64 : 32;
			if (bits == 32 && Long.compareUnsigned(number, unsigned || radix != 10 ? 0xFFFFFFFFL : Integer.MAX_VALUE) > 0)
				bits = 64;
			if (radix != 10 && (bits == 32 ? number > Integer.MAX_VALUE : number < 0))
				unsigned = true;
			if (number < 0 && !unsigned)
				throw error("Integer literal is outside the signed 64-bit range: " + text);
			return new Value(number, bits, unsigned);
		}
		catch (NumberFormatException e) {
			throw error("Integer literal is outside the 64-bit range: " + text);
		}
	}

	private Value apply(String op, Value left, Value right, boolean evaluate) throws IOException
	{
		if (op.equals("&&"))
			return logical(left.truth() && right.truth());
		if (op.equals("||"))
			return logical(left.truth() || right.truth());
		if (op.equals("<<") || op.equals(">>")) {
			if (evaluate && (right.number < 0 || right.number >= left.bits))
				throw error("Shift count must be between 0 and " + (left.bits - 1));
			long shifted = !evaluate ? 0
				: op.equals("<<") ? left.number << right.number
					: left.unsigned ? left.number >>> right.number : left.number >> right.number;
			return new Value(shifted, left.bits, left.unsigned);
		}
		Value type = common(left, right);
		long a = new Value(left.number, type.bits, type.unsigned).number;
		long b = new Value(right.number, type.bits, type.unsigned).number;
		boolean unsigned64 = type.unsigned && type.bits == 64;
		int comparison = unsigned64 ? Long.compareUnsigned(a, b) : Long.compare(a, b);
		switch (op) {
			case "==":
				return logical(a == b);
			case "!=":
				return logical(a != b);
			case "<":
				return logical(comparison < 0);
			case "<=":
				return logical(comparison <= 0);
			case ">":
				return logical(comparison > 0);
			case ">=":
				return logical(comparison >= 0);
		}
		long value = 0;
		if (evaluate) {
			if ((op.equals("/") || op.equals("%")) && b == 0)
				throw error("Division by zero in integer expression");
			switch (op) {
				case "+":
					value = a + b;
					break;
				case "-":
					value = a - b;
					break;
				case "*":
					value = a * b;
					break;
				case "/":
					value = unsigned64 ? Long.divideUnsigned(a, b) : a / b;
					break;
				case "%":
					value = unsigned64 ? Long.remainderUnsigned(a, b) : a % b;
					break;
				case "&":
					value = a & b;
					break;
				case "|":
					value = a | b;
					break;
				case "^":
					value = a ^ b;
					break;
			}
		}
		return new Value(value, type.bits, type.unsigned);
	}

	private Value logical(boolean value)
	{
		return new Value(value ? 1 : 0, preprocessor ? 64 : 32, false);
	}

	private static Value common(Value left, Value right)
	{
		int bits = Math.max(left.bits, right.bits);
		boolean unsigned = left.bits == right.bits ? left.unsigned || right.unsigned
			: left.bits > right.bits ? left.unsigned : right.unsigned;
		return new Value(0, bits, unsigned);
	}

	private static int precedence(String op)
	{
		switch (op) {
			case "||":
				return 1;
			case "&&":
				return 2;
			case "|":
				return 3;
			case "^":
				return 4;
			case "&":
				return 5;
			case "==":
			case "!=":
				return 6;
			case "<":
			case "<=":
			case ">":
			case ">=":
				return 7;
			case "<<":
			case ">>":
				return 8;
			case "+":
			case "-":
				return 9;
			case "*":
			case "/":
			case "%":
				return 10;
			default:
				return 0;
		}
	}

	private boolean take(String text)
	{
		if (pos < tokens.size() && tokens.get(pos).text().equals(text)) {
			pos++;
			return true;
		}
		return false;
	}

	private void expect(String text) throws IOException
	{
		if (!take(text))
			throw error("Expected '" + text + "'");
	}

	private IOException error(String message)
	{
		int line = tokens.isEmpty() ? 1 : tokens.get(Math.min(pos, tokens.size() - 1)).line();
		return new IOException(source + ":" + line + ": " + message);
	}
}
