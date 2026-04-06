package game.map.scripts.nextract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;

public class OLDFlameEffectPosExtractor
{
	/**
		PlayEffect(EFFECT_FLAME, FX_FLAME_RED, -503, -323, -44
	**/

	private static final Matcher EffectPosMatcher = Pattern.compile(
		"EFFECT_FLAME,\\s*FX_FLAME_RED,\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+),"
	).matcher("");

	protected static void findAndReplace(NewExtractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		EffectPosMatcher.reset(workingText);

		boolean modified = false;
		while (EffectPosMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			float x = Float.parseFloat(EffectPosMatcher.group(1));
			float y = Float.parseFloat(EffectPosMatcher.group(2));
			float z = Float.parseFloat(EffectPosMatcher.group(3));

			String markerName = extractor.getNextName("FlamePos");
			Marker m = new Marker(markerName, MarkerType.Position, x, y, z, 0);
			extractor.addMarker(m);

			String genName = extractor.getGenName(markerName);
			String newLine = String.format("EFFECT_FLAME, FX_FLAME_RED, %s_VEC,", genName);
			EffectPosMatcher.appendReplacement(out, newLine);
		}

		if (modified) {
			EffectPosMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
