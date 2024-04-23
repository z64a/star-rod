package game.map.scripts.extract;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public abstract class EntryListExtractor
{
	/**
		[kmr_11_ENTRY_0]    { -925.0,    0.0,  -53.0,   90.0 },
		[kmr_11_ENTRY_1]    {  770.0,    0.0, -525.0,  225.0 },
	*/

	private static final Matcher EntryListMatcher = Pattern.compile(
		"(EntryList \\S+ = \\{\\n)" +
			"((?:\\s*\\S+\\s*\\{\\s*\\S+,\\s*\\S+,\\s*\\S+,\\s*\\S+\\s*\\},?\\n)+)" +
			"(\\};)")
		.matcher("");

	protected static void findAndReplace(Extractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		EntryListMatcher.reset(workingText);

		boolean modified = false;
		while (EntryListMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			HeaderEntry h = new HeaderEntry("EntryList");
			extractor.addHeaderEntry(h);

			String listText = EntryListMatcher.group(2).replaceAll("[\t ]+", "");
			String[] lines = listText.split("\r?\n");
			h.addDefine("ENTRY_LIST", List.of(lines));

			String replacement = String.format("%s    %s%n%s", EntryListMatcher.group(1), "GEN_ENTRY_LIST", EntryListMatcher.group(3));
			EntryListMatcher.appendReplacement(out, replacement);
		}

		if (modified) {
			EntryListMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}

	public static void parse(Extractor extractor, HeaderEntry h) throws HeaderParseException
	{
		for (String line : h.getBlockDefine("*")) {
			// trim { and }, from each row
			line = line.substring(line.indexOf("{") + 1, line.indexOf("}"));
			String[] coords = line.split(",");
			float x = Float.parseFloat(coords[0]);
			float y = Float.parseFloat(coords[1]);
			float z = Float.parseFloat(coords[2]);
			float a = Float.parseFloat(coords[3]);

			String markerName = extractor.getNextName("Entry", 0);
			extractor.addMarker(new Marker(markerName, MarkerType.Entry, x, y, z, a));
		}
	}

	public static void print(PrintWriter pw, Iterable<Marker> markers) throws IOException
	{
		boolean hasEntryList = false;

		for (Marker m : markers) {
			if (m.type == MarkerType.Entry) {
				hasEntryList = true;
			}
		}

		if (!hasEntryList)
			return;

		HeaderEntry h = new HeaderEntry("EntryList");
		List<String> entryList = new ArrayList<>();
		for (Marker m : markers) {
			if (m.type == MarkerType.Entry) {
				entryList.add(String.format("    { %6.1f, %6.1f, %6.1f, %6.1f },",
					(float) m.position.getX(), (float) m.position.getY(), (float) m.position.getZ(), m.yaw.getAngle()));
			}
		}
		h.addDefine("ENTRY_LIST", entryList);
		h.print(pw);
	}
}
