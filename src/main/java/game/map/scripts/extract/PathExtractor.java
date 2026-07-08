package game.map.scripts.nextract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.PathComponent;
import game.map.marker.PathPoint;

public abstract class PathExtractor
{
	// accept any integer or integer-valued float
	private static final String WHOLE_FLOAT = "-?\\d+(?:\\.0+)?f?";

	private static final Matcher PathMatcher = Pattern.compile(
		"(Vec3f \\S+\\[\\] = \\{\\n)" +
			"((?:\\s*\\{" +
			"\\s*" + WHOLE_FLOAT + "\\s*," +
			"\\s*" + WHOLE_FLOAT + "\\s*," +
			"\\s*" + WHOLE_FLOAT + "\\s*,?" +
			"\\s*\\}\\s*,?\\n)+)" +
			"(\\};)")
		.matcher("");

	private static final Matcher TweesterMatcher = Pattern.compile(
		"(TweesterPath \\S+ = \\{\\n)" +
			"((?:\\s*" +
			WHOLE_FLOAT + "\\s*," +
			"\\s*" + WHOLE_FLOAT + "\\s*," +
			"\\s*" + WHOLE_FLOAT + "\\s*,\\n)+)" +
			"(\\s*TWEESTER_PATH_LOOP\\s*\\n\\};)"
	).matcher("");

	protected static void findAndReplace(NewExtractor extractor)
	{
		findAndReplace(extractor, PathMatcher, true, "_PATH");
		findAndReplace(extractor, TweesterMatcher, false, "_PATH_FLAT");
	}

	private static void findAndReplace(NewExtractor extractor, Matcher matcher, boolean showInterp, String suffix)
	{
		StringBuilder out = null;
		matcher.reset(extractor.getFileText());

		boolean modified = false;
		while (matcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String markerName = extractor.getNextName("Path");
			Marker pathMarker = new Marker(markerName, MarkerType.Path, 0, 0, 0, 0);
			PathComponent comp = pathMarker.pathComponent;

			String listText = matcher.group(2).replaceAll("[\t ]+", "");
			String[] lines = listText.split("\r?\n");

			for (String line : lines) {
				if (line.isBlank())
					continue;

				line = line.trim();

				// normalize both formats:
				// { x, y, z }
				// x, y, z,
				if (line.startsWith("{")) {
					line = line.substring(line.indexOf("{") + 1, line.indexOf("}"));
				}
				else {
					line = line.replaceAll(",$", "");
				}

				String[] coords = line.split(",");
				if (coords.length != 3)
					continue;

				float x = Float.parseFloat(coords[0]);
				float y = Float.parseFloat(coords[1]);
				float z = Float.parseFloat(coords[2]);

				comp.path.points.addElement(
					new PathPoint(comp.path, Math.round(x), Math.round(y), Math.round(z))
				);
			}

			PathPoint last = comp.path.points.get(comp.path.points.size() - 1);
			pathMarker.position.setPosition(last.getX(), last.getY(), last.getZ());

			comp.showInterp.set(showInterp);
			extractor.addMarker(pathMarker);

			String genName = extractor.getGenName(markerName);
			String replacement = String.format("%s    %s%s%n%s", matcher.group(1), genName, suffix, matcher.group(3));
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}

		if (modified) {
			matcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
