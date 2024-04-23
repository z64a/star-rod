package game.map.scripts.extract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;

public class LavaResetExtractor
{
	private static final Matcher LavaResetMatcher = Pattern.compile(
		"(LavaReset \\S+\\[\\] = \\{\\n)" +
			"((?:\\s*\\{.+\\},?\\n)+)" +
			"(\\};)")
		.matcher("");

	private static final Matcher LineMatcher = Pattern.compile(
		"\\{\\.colliderID=COLLIDER_(\\w+),\\.pos=\\{(\\S+),(\\S+),(\\S+)\\}\\},")
		.matcher("");

	protected static void findAndReplace(Extractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		LavaResetMatcher.reset(workingText);

		boolean modified = false;
		while (LavaResetMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String listText = LavaResetMatcher.group(2);//.replaceAll("[\t ]+", "");
			String indent = LavaResetMatcher.group(2).substring(0, LavaResetMatcher.group(2).indexOf("{"));

			StringBuilder newList = new StringBuilder();
			String[] lines = listText.split("\r?\n");
			for (String line : lines) {
				String cleanLine = line.replaceAll("[\t ]+", "");
				LineMatcher.reset(cleanLine);
				if (LineMatcher.matches()) {
					String colliderName = LineMatcher.group(1);
					float x = Float.parseFloat(LineMatcher.group(2));
					float y = Float.parseFloat(LineMatcher.group(3));
					float z = Float.parseFloat(LineMatcher.group(4));

					String markerName = extractor.getNextName("LavaReset_" + colliderName, -1);
					extractor.addMarker(new Marker(markerName, MarkerType.Position, x, y, z, 0));

					String genName = extractor.getGenName(markerName);
					newList.append(String.format("%s{ .colliderID = COLLIDER_%s, .pos = { %s_VEC }},%n", indent, colliderName, genName));
				}
				else {
					newList.append(line).append("\n");
				}
			}

			LavaResetMatcher.appendReplacement(out, LavaResetMatcher.group(1) + newList.toString() + LavaResetMatcher.group(3));
		}

		if (modified) {
			LavaResetMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
