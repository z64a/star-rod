package game.message;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.input.InputFileException;
import app.input.Line;
import app.input.PatchFileParser.PatchUnit;
import game.message.StringConstants.ControlCharacter;
import game.message.editor.MessageAsset;
import util.ui.FilterListable;

public class Message implements FilterListable
{
	public final MessageAsset source;
	public final PatchUnit unit;

	public int startLineNum = -1;
	public int endLineNum = -1;

	public String leadingTabs = "";

	public InputFileException parseException;
	private String errorMessage = "";
	private boolean hasError;

	public boolean modified;
	public boolean editorShouldSync;

	private String markup;
	private String previewText;
	public byte[] bytes;

	public int section;
	public int index;
	public String name;

	// loading from asset
	public Message(MessageAsset res, PatchUnit unit, int section, int index, String name)
	{
		this.source = res;
		this.unit = unit;
		startLineNum = unit.startLineNum;
		endLineNum = unit.endLineNum;

		this.section = section;
		this.index = index;
		this.name = name;

		loadLines(unit.body);
		tryCompile(unit.body);
	}

	// created in editor
	public Message(MessageAsset res)
	{
		this.source = res;
		this.unit = null;

		name = "NewMessage";
		markup = "[END]";
		previewText = "";
	}

	private static final Matcher TabStartMatcher = Pattern.compile("^(\t+).+").matcher("");

	private void loadLines(List<Line> lines)
	{
		// preserve tab indents
		if (lines.size() > 0 && lines.get(0).str.startsWith("\t")) {
			String firstLine = lines.get(0).str;
			TabStartMatcher.reset(firstLine);
			if (TabStartMatcher.matches())
				leadingTabs = TabStartMatcher.group(1);
		}

		StringBuilder sb = new StringBuilder();
		for (Line line : lines) {
			sb.append(line.str.replaceAll("[\t\r]", ""));
			sb.append("\n");
		}
		setMarkup(sb.toString());
	}

	public void setMarkup(String s)
	{
		markup = s;
		previewText = markup.replaceAll("(?i)\\[" + ControlCharacter.ENDL + "\\]", " ")
			.replaceAll("\\\\", "") // remove escape sequences
			.replaceAll("\\[[^]]+\\]", "") // remove other tags
			.replaceAll("\\s+", " ") // squash contiguous spaces into one
			.trim();
	}

	public String getMarkup()
	{
		return markup;
	}

	public void sanitize()
	{
		String unescapedControlChar = "[\\s\\S]*(?<!\\\\)[{}%][\\s\\S]*";
		String invalidEscapedChar = "[\\s\\S]*\\\\[^{}%\\[\\]][\\s\\S]*";
		if (markup.matches(unescapedControlChar) || markup.matches(invalidEscapedChar)) {
			editorShouldSync = true;
			String fixed = markup.replaceAll("(?<!\\\\)([{}%])", "\\\\$1");
			fixed = fixed.replaceAll("\\\\*([^{}%\\[\\]])", "$1");
			setMarkup(fixed);
		}
	}

	private void tryCompile(List<Line> lines)
	{
		try {
			ByteBuffer bb = StringEncoder.encodeLines(lines);
			bytes = new byte[bb.remaining()];
			bb.get(bytes);
			parseException = null;
		}
		catch (InputFileException e) {
			parseException = e;
			errorMessage = parseException.getMessage();
			bytes = null;
		}
	}

	public int getID()
	{
		return ((section & 0xFFFF) << 16) | (index & 0xFFFF);
	}

	public String getIDName()
	{
		return String.format("%02X-%03X", section, index);
	}

	@Override
	public String toString()
	{
		return previewText;
	}

	public String getIdentifier()
	{
		return name;
	}

	public void setModified()
	{
		assert (source != null);
		modified = true;
		source.hasModified = true;
	}

	public boolean isModified()
	{
		return modified;
	}

	public void setErrorMessage(String string)
	{
		if (string == null || string.isBlank()) {
			errorMessage = "";
			hasError = false;
		}
		else {
			errorMessage = string;
			hasError = true;
		}
	}

	public String getErrorMessage()
	{
		return errorMessage;
	}

	public boolean hasError()
	{
		return hasError;
	}

	@Override
	public String getFilterableString()
	{
		return name.replaceAll("_", " ") + " " + toString();
	}
}
