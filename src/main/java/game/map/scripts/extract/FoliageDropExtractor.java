package game.map.scripts.nextract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.nextract.NewExtractor.MarkerExtractionGroup;

public class FoliageDropExtractor
{
	private static final Matcher FoliageDropListMatcher = Pattern.compile(
		"(FoliageDropList\\s+N\\((\\w+)_Drops\\)\\s*=\\s*\\{[\\s\\S]*?" +
			"\\.count\\s*=\\s*(\\d+)\\s*,[\\s\\S]*?" +
			"\\.drops\\s*=\\s*\\{)" +
			"([\\s\\S]*?)" +
			"(\\}\\s*\\}\\s*;)"
	).matcher("");

	private static final Matcher DropMatcher = Pattern.compile(
		"(\\{[\\s\\S]*?" +
			"\\.itemID\\s*=\\s*ITEM_(\\w+)\\s*,[\\s\\S]*?" +
			"\\.pos\\s*=\\s*\\{\\s*([-\\d.]+)(?:f)?\\s*,\\s*([-\\d.]+)(?:f)?\\s*,\\s*([-\\d.]+)(?:f)?\\s*\\}[\\s\\S]*?" +
			"\\})"
	).matcher("");

	protected static void findAndReplace(NewExtractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		FoliageDropListMatcher.reset(workingText);

		boolean modified = false;
		while (FoliageDropListMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String fullDeclStart = FoliageDropListMatcher.group(1);
			String ownerName = FoliageDropListMatcher.group(2);
			int count = Integer.parseInt(FoliageDropListMatcher.group(3));
			String dropsBody = FoliageDropListMatcher.group(4);
			String fullDeclEnd = FoliageDropListMatcher.group(5);

			StringBuilder rewrittenDrops = new StringBuilder();
			DropMatcher.reset(dropsBody);

			int dropIndex = 0;
			int lastEnd = 0;

			while (DropMatcher.find()) {
				rewrittenDrops.append(dropsBody, lastEnd, DropMatcher.start());

				String fullDrop = DropMatcher.group(1);
				String itemName = DropMatcher.group(2);

				int x = (int) Float.parseFloat(DropMatcher.group(3));
				int y = (int) Float.parseFloat(DropMatcher.group(4));
				int z = (int) Float.parseFloat(DropMatcher.group(5));

				String itemSuffix = toPascalCase(itemName);

				String markerName;
				if (count == 1)
					markerName = ownerName + "_Drop_" + itemSuffix;
				else
					markerName = ownerName + "_Drop" + (dropIndex + 1) + "_" + itemSuffix;

				Marker m = new Marker(markerName, MarkerType.Position, x, y, z, 0.0f);
				extractor.addMarker(m, MarkerExtractionGroup.FOLIAGE);

				extractor.saveVectorName(ownerName, x, y, z, markerName);

				String genName = extractor.getGenName(markerName);

				String replacedDrop = fullDrop.replaceFirst(
					"\\.pos\\s*=\\s*\\{\\s*[-\\d.]+(?:f)?\\s*,\\s*[-\\d.]+(?:f)?\\s*,\\s*[-\\d.]+(?:f)?\\s*\\}",
					".pos = { " + genName + "_VEC }"
				);

				rewrittenDrops.append(replacedDrop);

				lastEnd = DropMatcher.end();
				dropIndex++;
			}

			rewrittenDrops.append(dropsBody.substring(lastEnd));

			StringBuilder replacement = new StringBuilder();
			replacement.append(fullDeclStart);
			replacement.append(rewrittenDrops);
			replacement.append(fullDeclEnd);

			FoliageDropListMatcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
		}

		if (modified) {
			FoliageDropListMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}

	private static String toPascalCase(String s)
	{
		StringBuilder sb = new StringBuilder();
		for (String part : s.split("_")) {
			if (part.isEmpty())
				continue;

			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1)
				sb.append(part.substring(1).toLowerCase());
		}
		return sb.toString();
	}
}
