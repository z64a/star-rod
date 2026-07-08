package game.map.scripts.extract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;

public class BombPosExtractor
{
	/**
		BombTrigger N(BombPos_Wall) = {
		    .pos = { 300.0f, 0.0f, 88.0f },
		    .diameter = 0.0f
		};
	*/

	private static final Matcher BombPosMatcher = Pattern.compile(
		"(BombTrigger \\S+ = \\{\\n)" +
			"\\s+\\.pos = \\{ (\\S+), (\\S+), (\\S+) }," +
			"\\s+\\.diameter = (\\S+),?"
	).matcher("");

	protected static void findAndReplace(Extractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		BombPosMatcher.reset(workingText);

		boolean modified = false;
		while (BombPosMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String declaration = BombPosMatcher.group(1);
			float x = Float.parseFloat(BombPosMatcher.group(2));
			float y = Float.parseFloat(BombPosMatcher.group(3));
			float z = Float.parseFloat(BombPosMatcher.group(4));
			float r = Float.parseFloat(BombPosMatcher.group(5)) / 2.0f;

			String markerName = extractor.getNextName("BombPos");
			Marker m = new Marker(markerName, MarkerType.Sphere, x, y, z, 0);
			m.volumeComponent.radius.set(r);
			extractor.addMarker(m);

			String genName = extractor.getGenName(markerName);

			StringBuilder newList = new StringBuilder();
			newList.append(declaration);
			newList.append(String.format("    .pos = { %s_VEC },%n", genName));
			newList.append(String.format("    .diameter = 2.0f * %s_RAD,", genName));

			BombPosMatcher.appendReplacement(out, newList.toString());
		}

		if (modified) {
			BombPosMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
