package game.map.scripts.extract;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.Resource;
import app.Resource.ResourceType;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.NpcComponent;
import util.Logger;

public class NpcExtractor
{
	private static final Matcher NpcDataMatcher = Pattern.compile(
		"(\\n +)\\.id = (.+),"
			+ "\\1\\.pos = (.+),"
			+ "\\1\\.yaw = (.+),"
			+ "(?:\\1\\.territory = ([^;]+?\\1\\}),)?" // (optional) territory field
			+ "([^;]+?)" // any number of additional fields
			+ "\\.animations = (\\{[^;]+?\\}|\\w+),") // the animations field (for selecting a preview sprite)
		.matcher("");

	private static final Matcher AnimArrayMatcher = Pattern.compile(
		"\\{\\s+\\.idle\\s*=\\s*(\\w+),[\\s\\S]+").matcher("");

	private static HashMap<String, String> animDefs = null;

	public static void loadAnimDefs()
	{
		animDefs = new HashMap<>();

		for (String mapping : Resource.getText(ResourceType.Extract, "npc_anim_defs.txt")) {
			String[] tokens = mapping.split("\\s*=\\s*");
			animDefs.put(tokens[0], tokens[1]);
		}
	}

	protected static void findAndReplace(Extractor extractor)
	{
		if (animDefs == null)
			loadAnimDefs();

		String workingText = extractor.getFileText();
		StringBuilder out = null;
		NpcDataMatcher.reset(workingText);

		boolean modified = false;
		while (NpcDataMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String indent = NpcDataMatcher.group(1).replaceAll("[\r\n]", "");
			String ID = NpcDataMatcher.group(2); // .id field
			String pos = NpcDataMatcher.group(3);
			String dir = NpcDataMatcher.group(4);
			String territory = NpcDataMatcher.group(5);

			if (ID.startsWith("NPC_FireBar_")) {
				NpcDataMatcher.appendReplacement(out, NpcDataMatcher.group());
				continue;
			}

			// parse pos
			assert (pos.matches("\\{ (\\S+|.+,.+,.+) \\}")) : pos;
			pos = pos.substring(pos.indexOf("{") + 1, pos.indexOf("}")).replaceAll("\\s", "");

			float x = 0.0f;
			float y = -1000.0f;
			float z = 0.0f;
			if (!pos.equals("NPC_DISPOSE_LOCATION")) {
				String[] coords = pos.split(",");
				x = Float.parseFloat(coords[0]);
				if (!coords[1].equals("NPC_DISPOSE_POS_Y"))
					y = Float.parseFloat(coords[1]);
				z = Float.parseFloat(coords[2]);
			}

			// parse yaw
			float yaw = Integer.decode(dir);

			String markerName = extractor.getNextName(ID, -1);
			Marker m = new Marker(markerName, MarkerType.NPC, x, y, z, yaw);
			NpcComponent npc = m.npcComponent;

			// parse territory
			if (territory != null)
				npc.parseTerritory(territory);

			String animation = NpcDataMatcher.group(7);

			AnimArrayMatcher.reset(animation);
			if (AnimArrayMatcher.matches()) {
				npc.setAnimByName(AnimArrayMatcher.group(1));
			}
			else if (animDefs != null && animDefs.containsKey(animation)) {
				npc.setAnimByName(animDefs.get(animation));
			}
			else {
				Logger.logfWarning("Could not resolve NPC animation: " + animation);
			}

			extractor.addMarker(m);

			String genName = extractor.getGenName(markerName);
			StringBuilder replacement = new StringBuilder();
			replacement.append("\n");
			replacement.append(String.format("%s.id = %s,%n", indent, ID));
			replacement.append(String.format("%s.pos = { %s_VEC },%n", indent, genName));
			replacement.append(String.format("%s.yaw = %s_DIR,%n", indent, genName));
			replacement.append(String.format("%s.territory = %s_TERRITORY,", indent, genName));

			if (NpcDataMatcher.group(6) != null)
				replacement.append(NpcDataMatcher.group(6));

			replacement.append(String.format(".animations = %s,", NpcDataMatcher.group(7)));
			NpcDataMatcher.appendReplacement(out, replacement.toString());
		}

		if (modified) {
			NpcDataMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}
}
