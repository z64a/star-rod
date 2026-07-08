package game.map.scripts.extract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;

public class FlowerVolumeExtractor
{
	private static final Matcher FlowerVolumeMatcher = Pattern.compile(
		"EVT_FLOWER_SPAWN_REGION\\(\\s*(\\S+),\\s*(\\S+),\\s*(\\S+),\\s*(\\S+),\\s*(\\S+)\\s*\\)").matcher("");

	protected static void findAndReplace(Extractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		FlowerVolumeMatcher.reset(workingText);

		boolean modified = false;
		while (FlowerVolumeMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			float minX = Float.parseFloat(FlowerVolumeMatcher.group(1));
			float minZ = Float.parseFloat(FlowerVolumeMatcher.group(2));
			float maxX = Float.parseFloat(FlowerVolumeMatcher.group(3));
			float maxZ = Float.parseFloat(FlowerVolumeMatcher.group(4));
			float minY = Float.parseFloat(FlowerVolumeMatcher.group(5));

			String markerName = extractor.getNextName("FlowerVolume");
			Marker m = new Marker(markerName, MarkerType.Volume, (minX + maxX) / 2, minY + 25, (minZ + maxZ) / 2, 0);
			m.volumeComponent.minPos.point.setPosition(minX, minY, minZ);
			m.volumeComponent.maxPos.point.setPosition(maxX, minY + 50, maxZ);
			extractor.addMarker(m);

			String genName = extractor.getGenName(markerName);

			StringBuilder newList = new StringBuilder();
			newList.append(String.format("GEN_FLOWER_SPAWN_REGION(%s_MIN_XZ, %s_MAX_XZ, %s_MIN_Y)", genName, genName, genName));
			FlowerVolumeMatcher.appendReplacement(out, newList.toString());
		}

		if (modified) {
			FlowerVolumeMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
