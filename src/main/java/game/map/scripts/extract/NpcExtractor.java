package game.map.scripts.extract;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.NpcComponent;
import game.map.marker.NpcComponent.MoveType;
import game.map.marker.PathPoint;
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

	protected static void findAndReplace(MapExtractor extractor)
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
				parseTerritory(npc, territory);

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

	private static void parseTerritory(NpcComponent npc, String territory)
	{
		String original = territory;
		territory = territory.replaceAll("\\s", "");

		assert (territory.startsWith("{"));
		assert (territory.endsWith("}}"));

		territory = territory.substring(1, territory.length() - 2);

		// determine territory type
		if (territory.startsWith(".patrol={"))
			npc.moveType.set(MoveType.Patrol);
		else if (territory.startsWith(".wander={"))
			npc.moveType.set(MoveType.Wander);
		else
			throw new StarRodException("Cannot parse NPC territory: " + original);

		territory = territory.substring(territory.indexOf("{") + 1);
		if (territory.endsWith(","))
			territory = territory.substring(0, territory.length() - 1);

		String[] fields = territory.split(",(?=\\.\\w+\\s*=)");

		for (String field : fields) {
			String[] kv = field.split("=");
			int[] coords;

			switch (kv[0]) {
				case ".isFlying":
					npc.flying.set("TRUE".equalsIgnoreCase(kv[1]));
					break;
				case ".moveSpeedOverride":
					if (!"NO_OVERRIDE_MOVEMENT_SPEED".equals(kv[1])) {
						assert (kv[1].matches("OVERRIDE_MOVEMENT_SPEED\\(\\S+\\)"));
						float speed = Float.parseFloat(kv[1].substring("OVERRIDE_MOVEMENT_SPEED(".length(), kv[1].length() - 1));
						npc.overrideMovementSpeed.set(true);
						npc.movementSpeedOverride.set(speed);
					}
					break;
				case ".wanderShape":
					npc.useWanderCircle.set("SHAPE_CYLINDER".equals(kv[1]));
					break;
				case ".centerPos":
					coords = getIntVec(kv[1], "centerPos", 3);
					npc.wanderCenter.point.setPosition(coords[0], coords[1], coords[2]);
					break;
				case ".wanderSize":
					if (npc.useWanderCircle.get()) {
						coords = getIntVec(kv[1], "wanderSize", -1);
						npc.wanderRadius.set(coords[0]);
					}
					else {
						coords = getIntVec(kv[1], "wanderSize", 2);
						npc.wanderSizeX.set(coords[0]);
						npc.wanderSizeZ.set(coords[1]);
					}
					break;
				case ".detectShape":
					npc.useDetectCircle.set("SHAPE_CYLINDER".equals(kv[1]));
					break;
				case ".detectPos":
					coords = getIntVec(kv[1], "detectPos", 3);
					npc.detectCenter.point.setPosition(coords[0], coords[1], coords[2]);
					break;
				case ".detectSize":
					if (npc.useDetectCircle.get()) {
						coords = getIntVec(kv[1], "detectSize", -1);
						npc.detectRadius.set(coords[0]);
						if (coords.length > 1 && coords[1] != 0) {
							npc.detectHeight.set(coords[1]);
						}
					}
					else {
						coords = getIntVec(kv[1], "detectSize", 2);
						npc.detectSizeX.set(coords[0]);
						npc.detectSizeZ.set(coords[1]);
					}
					break;
				case ".points":
					// examples:
					// {{200,0,75},{300,0,75},}
					// {{-450,0,-160},{-378,0,-81},{-590,0,-100},{-464,0,-46},{-495,0,-147},}
					assert (kv[1].matches("\\{\\{.+\\},\\}")) : kv[1];
					String listString = kv[1].substring(2, kv[1].length() - 3);
					String[] points = listString.split("\\},\\{");
					for (String p : points) {
						// have to add the {} so the function can strip them out
						int[] point = getIntVec("{" + p + "}", "point", 3);
						npc.patrolPath.points.addElement(new PathPoint(npc.patrolPath, point[0], point[1], point[2]));
					}
					break;
				case ".numPoints":
					break;
				default:
					throw new StarRodException(kv[0]);
			}
		}
	}

	private static int[] getIntVec(String s, String fieldName, int len)
	{
		assert (s.matches("\\{\\S+(,\\S+)*\\}")) : s;
		s = s.substring(1, s.length() - 1);
		String[] tokens = s.split(",");

		// check predefined position name
		if (tokens.length == 1 && "NPC_DISPOSE_LOCATION".equals(tokens[0]))
			return new int[] { 0, -1000, 0 };

		if (len > 0 && tokens.length != len)
			throw new StarRodException("Wrong length for %s vector: %s (expected %d)", fieldName, s, len);

		if (len < 0 && tokens.length != -len)
			Logger.logfError("Wrong length for %s vector: %s (expected %d)", fieldName, s, len);

		// convert the coords
		int[] coords = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			coords[i] = Integer.decode(tokens[i]);
		}
		return coords;
	}
}
