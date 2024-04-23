package game.map.scripts.extract;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.PathComponent;
import game.map.marker.PathPoint;

public abstract class PathExtractor
{
	private static final Matcher PathMatcher = Pattern.compile(
		"(Vec3f \\S+\\[\\] = \\{\\n)" +
			"((?:\\s*\\{\\s*\\S+,\\s*\\S+,\\s*\\S+\\s*\\},?\\n)+)" +
			"(\\};)")
		.matcher("");

	private static final Matcher TweesterMatcher = Pattern.compile(
		"(TweesterPath \\S+ = \\{\\n)" +
			"((?:\\s*\\{\\s*\\S+,\\s*\\S+,\\s*\\S+\\s*\\},?\\n)+)" +
			"(\\s*\\S+)")
		.matcher("");

	protected static void findAndReplace(Extractor extractor)
	{
		findAndReplace(extractor, PathMatcher, true);
		findAndReplace(extractor, TweesterMatcher, false);
	}

	private static void findAndReplace(Extractor extractor, Matcher matcher, boolean showInterp)
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

			comp.fromLines(List.of(lines));

			PathPoint last = comp.path.points.get(comp.path.points.size() - 1);
			pathMarker.position.setPosition(last.getX(), last.getY(), last.getZ());

			comp.showInterp.set(showInterp);
			extractor.addMarker(pathMarker);

			String genName = extractor.getGenName(markerName);
			String replacement = String.format("%s    %s_PATH%n%s", matcher.group(1), genName, matcher.group(3));
			matcher.appendReplacement(out, replacement);
		}

		if (modified) {
			matcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
