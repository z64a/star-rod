package game.map.scripts.nextract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.nextract.NewExtractor.MarkerExtractionGroup;

public class FoliageEffectExtractor
{
	private static final Matcher FoliageEffectListMatcher = Pattern.compile(
		"(FoliageVectorList\\s+N\\((\\w+)_Effects\\)\\s*=\\s*\\{[\\s\\S]*?" +
			"\\.count\\s*=\\s*(\\d+)\\s*,[\\s\\S]*?" +
			"\\.vectors\\s*=\\s*\\{)" +
			"([\\s\\S]*?)" +
			"(\\}\\s*\\}\\s*;)"
	).matcher("");

	private static final Matcher EffectVectorMatcher = Pattern.compile(
		"\\{\\s*([-\\d.]+)(?:f)?\\s*,\\s*([-\\d.]+)(?:f)?\\s*,\\s*([-\\d.]+)(?:f)?\\s*\\}"
	).matcher("");

	protected static void findAndReplace(NewExtractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		FoliageEffectListMatcher.reset(workingText);

		boolean modified = false;
		while (FoliageEffectListMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String fullDeclStart = FoliageEffectListMatcher.group(1);
			String ownerName = FoliageEffectListMatcher.group(2);
			int count = Integer.parseInt(FoliageEffectListMatcher.group(3));
			String vectorsBody = FoliageEffectListMatcher.group(4);
			String fullDeclEnd = FoliageEffectListMatcher.group(5);

			StringBuilder rewrittenVectors = new StringBuilder();
			EffectVectorMatcher.reset(vectorsBody);

			int effectIndex = 0;
			int lastEnd = 0;

			while (EffectVectorMatcher.find()) {
				rewrittenVectors.append(vectorsBody, lastEnd, EffectVectorMatcher.start());

				int x = (int) Float.parseFloat(EffectVectorMatcher.group(1));
				int y = (int) Float.parseFloat(EffectVectorMatcher.group(2));
				int z = (int) Float.parseFloat(EffectVectorMatcher.group(3));

				String markerName = extractor.findVectorName(ownerName, x, y, z);

				if (markerName == null) {
					if (count == 1)
						markerName = ownerName + "_Effect";
					else
						markerName = ownerName + "_Effect" + (effectIndex + 1);

					Marker m = new Marker(markerName, MarkerType.Position, x, y, z, 0.0f);
					extractor.addMarker(m, MarkerExtractionGroup.FOLIAGE);
				}

				String genName = extractor.getGenName(markerName);
				rewrittenVectors.append("{ ").append(genName).append("_VEC }");

				lastEnd = EffectVectorMatcher.end();
				effectIndex++;
			}

			rewrittenVectors.append(vectorsBody.substring(lastEnd));

			StringBuilder replacement = new StringBuilder();
			replacement.append(fullDeclStart);
			replacement.append(rewrittenVectors);
			replacement.append(fullDeclEnd);

			FoliageEffectListMatcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
		}

		if (modified) {
			FoliageEffectListMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
