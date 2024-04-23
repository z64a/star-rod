package game.map.scripts.extract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;

public class DripVolumeExtractor
{
	private static final Matcher DripVolumeMatcher = Pattern.compile(
		"( +)\\.minPos\\s*=\\s*\\{\\s*(\\S+),\\s*(\\S+)\\s*\\}," +
			"\\s+\\.maxPos\\s*=\\s*\\{\\s*(\\S+),\\s*(\\S+)\\s*\\}," +
			"\\s+\\.startY\\s*=\\s*(\\S+)," +
			"\\s+\\.endY\\s*=\\s*(\\S+),")
		.matcher("");

	protected static void findAndReplace(Extractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		DripVolumeMatcher.reset(workingText);

		boolean modified = false;
		while (DripVolumeMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String indent = DripVolumeMatcher.group(1);
			float minX = Float.parseFloat(DripVolumeMatcher.group(2));
			float minZ = Float.parseFloat(DripVolumeMatcher.group(3));
			float maxX = Float.parseFloat(DripVolumeMatcher.group(4));
			float maxZ = Float.parseFloat(DripVolumeMatcher.group(5));
			float maxY = Float.parseFloat(DripVolumeMatcher.group(6));
			float minY = Float.parseFloat(DripVolumeMatcher.group(7));

			String markerName = extractor.getNextName("DripVolume");
			Marker m = new Marker(markerName, MarkerType.Volume, (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2, 0);
			m.volumeComponent.minPos.point.setPosition(minX, minY, minZ);
			m.volumeComponent.maxPos.point.setPosition(maxX, maxY, maxZ);
			extractor.addMarker(m);

			String genName = extractor.getGenName(markerName);

			StringBuilder newList = new StringBuilder();
			newList.append(String.format("%s.minPos = { %s_MIN_XZ },%n", indent, genName));
			newList.append(String.format("%s.maxPos = { %s_MAX_XZ },%n", indent, genName));
			newList.append(String.format("%s.startY = %s_MAX_Y,%n", indent, genName));
			newList.append(String.format("%s.endY   = %s_MIN_Y,", indent, genName));

			DripVolumeMatcher.appendReplacement(out, newList.toString());
		}

		if (modified) {
			DripVolumeMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
