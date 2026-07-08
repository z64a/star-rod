package game.map.scripts.nextract;

import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.nextract.NewExtractor.MarkerExtractionGroup;

public class EffectPosExtractor
{
	/**
		PlayEffect(EFFECT_FLAME, FX_FLAME_RED, -503, -323, -44
	**/

	private static final Matcher EffectPosMatcher = Pattern.compile(
		"EFFECT_(\\w+),\\s*(\\w+),\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+),"
	).matcher("");

	private record EffectKey(String type, int x, int y, int z)
	{}

	public static String cleanName(String effectName)
	{
		try {
			// check if the effect name is an integer
			Integer.parseInt(effectName, 16);

			// unnamed (numeric) effect names are not cleaned up
			return "Effect" + effectName;
		}
		catch (NumberFormatException e) {
			// convert THIS_CASE to ThisCase
			return Arrays.stream(effectName.toLowerCase().split("_"))
				.filter(s -> !s.isEmpty())
				.map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
				.reduce("", String::concat);
		}
	}

	protected static void findAndReplace(NewExtractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		EffectPosMatcher.reset(workingText);

		HashMap<EffectKey, String> markerNamesByKey = new HashMap<>();

		boolean modified = false;
		while (EffectPosMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String type = EffectPosMatcher.group(1);
			String subtype = EffectPosMatcher.group(2);

			int x = Integer.parseInt(EffectPosMatcher.group(3));
			int y = Integer.parseInt(EffectPosMatcher.group(4));
			int z = Integer.parseInt(EffectPosMatcher.group(5));

			EffectKey key = new EffectKey(type, x, y, z);

			String genName = markerNamesByKey.get(key);
			if (genName == null) {
				String markerName = extractor.getNextName(cleanName(type) + "Pos");
				Marker m = new Marker(markerName, MarkerType.Position, x, y, z, 0);
				extractor.addMarker(m, MarkerExtractionGroup.EFFECT);

				genName = extractor.getGenName(markerName);
				markerNamesByKey.put(key, genName);
			}

			String newLine = String.format("EFFECT_%s, %s, %s_VEC,", type, subtype, genName);
			EffectPosMatcher.appendReplacement(out, newLine);
		}

		if (modified) {
			EffectPosMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
