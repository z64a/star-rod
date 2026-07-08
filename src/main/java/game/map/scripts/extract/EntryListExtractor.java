package game.map.scripts.extract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;

public abstract class EntryListExtractor
{
	/**
		[kmr_11_ENTRY_0]    { -925.0,    0.0,  -53.0,   90.0 },
		[kmr_11_ENTRY_1]    {  770.0,    0.0, -525.0,  225.0 },
	*/

	private static final Matcher EntryListMatcher = Pattern.compile(
		"(EntryList \\S+ = \\{\\n)" +
			"((?:\\s*\\S+\\s*\\{\\s*\\S+,\\s*\\S+,\\s*\\S+,\\s*\\S+\\s*\\},?\\n)+)" +
			"(\\};)"
	).matcher("");

	protected static void findAndReplace(MapExtractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		EntryListMatcher.reset(workingText);

		boolean modified = false;
		while (EntryListMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String listText = EntryListMatcher.group(2).replaceAll("[\t ]+", "");
			String[] lines = listText.split("\r?\n");

			for (String line : lines) {
				int open = line.indexOf("{");
				int close = line.indexOf("}");
				if (open < 0 || close < 0 || close <= open)
					throw new IllegalStateException("Malformed EntryList row: " + line);

				line = line.substring(open + 1, close);

				String[] coords = line.split(",");
				if (coords.length != 4)
					throw new IllegalStateException("Malformed EntryList coords: " + line);

				float x = Float.parseFloat(coords[0].trim());
				float y = Float.parseFloat(coords[1].trim());
				float z = Float.parseFloat(coords[2].trim());
				float a = Float.parseFloat(coords[3].trim());

				String markerName = extractor.getNextName("Entry", 0);
				extractor.addMarker(new Marker(markerName, MarkerType.Entry, x, y, z, a));
			}

			String replacement = String.format(
				"%s    GEN_ENTRY_LIST%n%s",
				EntryListMatcher.group(1),
				EntryListMatcher.group(3)
			);
			EntryListMatcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}

		if (modified) {
			EntryListMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
