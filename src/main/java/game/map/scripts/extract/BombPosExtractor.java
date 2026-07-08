package game.map.scripts.nextract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.nextract.NewExtractor.MarkerExtractionGroup;

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

	protected static void findAndReplace(NewExtractor extractor)
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

			String[] tokens = declaration.split(" ");
			String name = tokens[1];
			boolean isFoliage = false;

			if (name.matches("N\\(\\S+\\)"))
				name = name.substring(2, name.length() - 1);

			if (name.startsWith("BombPos_"))
				name = name.substring("BombPos_".length());

			if (name.startsWith("Tree"))
				isFoliage = true;

			String markerName = "BombPos_" + name;
			Marker m = new Marker(markerName, MarkerType.Sphere, x, y, z, 0);
			m.volumeComponent.radius.set(r);

			if (isFoliage)
				extractor.addMarker(m, MarkerExtractionGroup.FOLIAGE);
			else
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
