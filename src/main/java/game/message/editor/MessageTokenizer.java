package game.message.editor;

import static game.message.StringConstants.*;

import java.util.ArrayList;
import java.util.List;

import app.input.Line;
import game.message.MessageAnim;

public class MessageTokenizer
{
	public static void main(String[] args)
	{
		String text = "%This [is] [[a] [] TE\\\\\\ST[b";
		(new MessageTokenizer(text)).print();
	}

	private void print()
	{
		for (Sequence seq : sequences)
			System.out.println(seq);
	}

	public ArrayList<Sequence> sequences = new ArrayList<>();
	private boolean readingTag = false;
	private boolean escaping = false;

	public static ArrayList<Sequence> tokenize(String text)
	{
		return new MessageTokenizer(text).sequences;
	}

	public static ArrayList<Sequence> tokenize(List<Line> lines)
	{
		return new MessageTokenizer(lines).sequences;
	}

	private MessageTokenizer(String text)
	{
		add(text, null);
	}

	private MessageTokenizer(List<Line> lines)
	{
		for (Line line : lines)
			add(line.str, line);
	}

	private void add(String text, Line line)
	{
		SequenceBuilder sb = new SequenceBuilder(line, 0, false);

		char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			char chr = chars[i];

			if (chr == '\r' || chr == '\t')
				continue; // ignore these

			if (escaping) {
				switch (chr) {
					case OPEN_TAG:
					case CLOSE_TAG:
					case '{':
					case '}':
					case COMMENT:
						sb.append(chr);
						break;
					default:
						// can't escape other characters
						sb.errors.add("Can't escape '" + chr + "', only escape these characters: []{}%");
						break;
				}

				escaping = false;
				continue;
			}

			if (readingTag) {
				if (chr == '\n') {
					sb.errors.add("Tag cannot include newline!");
					sequences.add(sb.build(i));

					readingTag = false;
					sb = new SequenceBuilder(line, i, false);
					sb.append(chr);
					continue;
				}

				if (chr == OPEN_TAG) {
					sb.errors.add("New tag cannot start within existing tag!");
					sequences.add(sb.build(i));

					readingTag = true;
					sb = new SequenceBuilder(line, i, true);
					sb.append(chr);
					continue;
				}

				if (chr == CLOSE_TAG) {
					sb.append(chr);
					if (sb.length() == 2)
						sb.errors.add("Cannot have empty tag!");

					sequences.add(sb.build(i + 1));
					readingTag = false;
					sb = new SequenceBuilder(line, i + 1, false);
					continue;
				}

				sb.append(chr);
			}
			else {
				if (chr == '\n')
					continue;

				if (chr == '\\') {
					escaping = true;
					continue;
				}

				if (chr == OPEN_TAG) {
					if (sb.length() > 0 || sb.errors.size() > 0)
						sequences.add(sb.build(i));

					readingTag = true;
					sb = new SequenceBuilder(line, i, true);
					sb.append(chr);
					continue;
				}

				switch (chr) {
					case CLOSE_TAG:
					case '{':
					case '}':
					case COMMENT:
						// these characters must be escaped
						sb.errors.add("'" + chr + "' must be escaped!");
						continue;
					default:
						sb.append(chr);
						continue;
				}
			}
		}

		if (escaping)
			sb.errors.add("String ends with incomplete escape sequence.");

		if (sb.length() > 0) {
			if (sb.tag)
				sb.errors.add("Tag is not closed!");

			sequences.add(sb.build(chars.length));
		}
	}

	public static class Sequence
	{
		public final boolean tag; // tags refer to AT MOST 1 printed char, but may be zero
		public MessageAnim anim = null;
		private ArrayList<String> errors;

		public boolean pageBreak;

		public final Line srcLine;
		public final String srcText;
		public final int srcStart; // in string or line
		public final int srcEnd;
		public final int srcLen;

		// compiling
		public final ArrayList<Byte> bytes;

		public Sequence(String text, boolean tag, ArrayList<String> errors, Line line, int start, int end)
		{
			this.bytes = new ArrayList<>(32);

			this.tag = tag;
			this.errors = errors;

			this.srcText = text;
			this.srcLine = line;
			this.srcStart = start;
			this.srcEnd = end;
			this.srcLen = end - start;
		}

		public void addError(String message)
		{
			errors.add(message);
		}

		public void addError(String fmt, Object ... args)
		{
			errors.add(String.format(fmt, args));
		}

		public boolean hasError()
		{
			return errors.size() > 0;
		}

		public String getErrorMessage()
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < errors.size(); i++) {
				sb.append(errors.get(i));
				if (i + 1 < errors.size())
					sb.append(System.lineSeparator());
			}
			return sb.toString();
		}

		@Override
		public String toString()
		{
			return String.format("%-3d %-3d %-6b %-12s %s", srcStart, srcEnd, tag, srcText, hasError() ? getErrorMessage() : "");
		}
	}

	private static class SequenceBuilder
	{
		private StringBuilder sb = new StringBuilder();
		private final boolean tag;
		private final Line line;
		private final int start;

		private ArrayList<String> errors;

		public SequenceBuilder(Line line, int startPos, boolean tag)
		{
			this.line = line;
			this.start = startPos;
			this.tag = tag;
			this.errors = new ArrayList<>();
		}

		public void append(char c)
		{
			sb.append(c);
		}

		public int length()
		{
			return sb.length();
		}

		public Sequence build(int end)
		{
			return new Sequence(sb.toString(), tag, errors, line, start, end);
		}
	}
}
