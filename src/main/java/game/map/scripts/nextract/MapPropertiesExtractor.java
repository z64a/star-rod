package game.map.scripts.nextract;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.Map;

public class MapPropertiesExtractor
{
	private static final Matcher ShadingMatcher = Pattern.compile(
		"Call\\(SetSpriteShading,\\s*(\\w+)\\)").matcher("");

	private static final Matcher LocationMatcher = Pattern.compile(
		"Set\\(GB_WorldLocation,\\s*(?!GEN_)(\\w+)\\)").matcher("");

	private static final Matcher WorldFogColorMatcher = Pattern.compile(
		"set_world_fog_color\\((\\d+), (\\d+), (\\d+), (\\d+)\\)").matcher("");

	private static final Matcher EntityFogColorMatcher = Pattern.compile(
		"set_entity_fog_color\\((\\d+), (\\d+), (\\d+), (\\d+)\\)").matcher("");

	private static final Matcher WorldFogDistMatcher = Pattern.compile(
		"set_world_fog_dist\\((\\d+), (\\d+)\\)").matcher("");

	private static final Matcher EntityFogDistMatcher = Pattern.compile(
		"set_entity_fog_dist\\((\\d+), (\\d+)\\)").matcher("");

	private static final Matcher CamBackColorMatcher = Pattern.compile(
		"gCameras\\[CAM_DEFAULT\\]\\.bgColor\\[(\\d)\\] = (\\d+)").matcher("");

	protected static void findAndReplace(Map map, NewExtractor extractor)
	{
		String workingText = extractor.getFileText();
		MatchResult matchResult = null;
		boolean modified = false;

		// get location name, but do not replace it with GEN token
		LocationMatcher.reset(workingText);
		if (LocationMatcher.find()) {
			// StringBuilder out = new StringBuilder(workingText.length());

			String locationName = LocationMatcher.group(1);
			map.features.locationName.set(locationName);

			// LocationMatcher.appendReplacement(out, "Set(GB_WorldLocation, GEN_MAP_LOCATION)");
			// LocationMatcher.appendTail(out);

			// workingText = out.toString();
			// modified = true;
		}

		ShadingMatcher.reset(workingText);
		if (ShadingMatcher.find()) {
			StringBuilder out = new StringBuilder(workingText.length());

			String profileName = ShadingMatcher.group(1);

			if (profileName.startsWith("SHADING_"))
				profileName = profileName.substring("SHADING_".length()).toLowerCase();

			map.features.shadingProfileName.set(profileName);
			map.features.hasSpriteShading.set(!profileName.equals("none"));

			ShadingMatcher.appendReplacement(out, "Call(SetSpriteShading, GEN_SPRITE_SHADING)");
			ShadingMatcher.appendTail(out);

			workingText = out.toString();
			modified = true;
		}

		WorldFogColorMatcher.reset(workingText);
		if (WorldFogColorMatcher.find()) {
			StringBuilder out = new StringBuilder(workingText.length());

			do {
				int r = Integer.parseInt(WorldFogColorMatcher.group(1));
				int g = Integer.parseInt(WorldFogColorMatcher.group(2));
				int b = Integer.parseInt(WorldFogColorMatcher.group(3));
				int a = Integer.parseInt(WorldFogColorMatcher.group(4));

				map.features.worldFog.enabled.set(true);
				map.features.worldFog.R.set(r);
				map.features.worldFog.G.set(g);
				map.features.worldFog.B.set(b);
				map.features.worldFog.A.set(a);

				WorldFogColorMatcher.appendReplacement(out,
					Matcher.quoteReplacement("set_world_fog_color(GEN_WORLD_FOG_RGBA)"));
			}
			while (WorldFogColorMatcher.find());

			WorldFogColorMatcher.appendTail(out);
			workingText = out.toString();
			modified = true;
		}

		EntityFogColorMatcher.reset(workingText);
		if (EntityFogColorMatcher.find()) {
			StringBuilder out = new StringBuilder(workingText.length());

			do {
				int r = Integer.parseInt(EntityFogColorMatcher.group(1));
				int g = Integer.parseInt(EntityFogColorMatcher.group(2));
				int b = Integer.parseInt(EntityFogColorMatcher.group(3));
				int a = Integer.parseInt(EntityFogColorMatcher.group(4));

				map.features.entityFog.enabled.set(true);
				map.features.entityFog.R.set(r);
				map.features.entityFog.G.set(g);
				map.features.entityFog.B.set(b);
				map.features.entityFog.A.set(a);

				EntityFogColorMatcher.appendReplacement(out,
					Matcher.quoteReplacement("set_entity_fog_color(GEN_ENTITY_FOG_RGBA)")
				);
			}
			while (EntityFogColorMatcher.find());

			EntityFogColorMatcher.appendTail(out);
			workingText = out.toString();
			modified = true;
		}

		CamBackColorMatcher.reset(workingText);
		if (CamBackColorMatcher.find()) {
			StringBuilder out = new StringBuilder(workingText.length());

			do {
				int i = Integer.parseInt(CamBackColorMatcher.group(1));
				int v = Integer.parseInt(CamBackColorMatcher.group(2));

				String color = "X";
				switch (i) {
					case 0:
						color = "R";
						map.features.bgColorR.set(v);
						break;
					case 1:
						color = "G";
						map.features.bgColorG.set(v);
						break;
					case 2:
						color = "B";
						map.features.bgColorB.set(v);
						break;
				}

				String newline = String.format("gCameras[CAM_DEFAULT].bgColor[%d] = GEN_CAM_BG_%s", i, color);
				CamBackColorMatcher.appendReplacement(out, Matcher.quoteReplacement(newline));
			}
			while (CamBackColorMatcher.find());

			CamBackColorMatcher.appendTail(out);
			workingText = out.toString();
			modified = true;
		}

		// replace only LAST occurance
		WorldFogDistMatcher.reset(workingText);
		matchResult = null;
		while (WorldFogDistMatcher.find()) {
			matchResult = WorldFogDistMatcher.toMatchResult();
		}

		if (matchResult != null) {
			int start = Integer.parseInt(matchResult.group(1));
			int end = Integer.parseInt(matchResult.group(2));

			map.features.worldFog.enabled.set(true);
			map.features.worldFog.start.set(start);
			map.features.worldFog.end.set(end);

			workingText = workingText.substring(0, matchResult.start()) +
				"set_world_fog_dist(GEN_WORLD_FOG_DIST)" +
				workingText.substring(matchResult.end());

			modified = true;
		}

		// replace only LAST occurance
		EntityFogDistMatcher.reset(workingText);
		matchResult = null;
		while (EntityFogDistMatcher.find()) {
			matchResult = EntityFogDistMatcher.toMatchResult();
		}

		if (matchResult != null) {
			int start = Integer.parseInt(matchResult.group(1));
			int end = Integer.parseInt(matchResult.group(2));

			map.features.entityFog.enabled.set(true);
			map.features.entityFog.start.set(start);
			map.features.entityFog.end.set(end);

			workingText = workingText.substring(0, matchResult.start()) +
				"set_entity_fog_dist(GEN_ENTITY_FOG_DIST)" +
				workingText.substring(matchResult.end());

			modified = true;
		}

		if (modified) {
			extractor.setFileText(workingText);
		}
	}
}
