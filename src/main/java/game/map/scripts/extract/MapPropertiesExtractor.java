package game.map.scripts.extract;

import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.Map;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public class MapPropertiesExtractor
{
	private static final Matcher ShadingMatcher = Pattern.compile(
		"(\\s*)Call\\(SetSpriteShading,\\s*(\\w+)\\)\\s*").matcher("");

	private static final Matcher LocationMatcher = Pattern.compile(
		"Set\\(GB_WorldLocation,\\s*(?!GEN_)(\\w+)\\)").matcher("");

	private static final Matcher SetMusicMatcher = Pattern.compile(
		"(Call\\(SetMusicTrack,\\s*\\S+,\\s*)(\\w+)(,.+)").matcher("");

	protected static void findAndReplace(Map map, Extractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		boolean modified = false;

		LocationMatcher.reset(workingText);
		if (LocationMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());

			String locationName = LocationMatcher.group(1);
			map.scripts.locationName.set(locationName);

			LocationMatcher.appendReplacement(out, "Set(GB_WorldLocation, GEN_MAP_LOCATION)");
			LocationMatcher.appendTail(out);

			workingText = out.toString();
			modified = true;
		}

		/*
		
		ShadingMatcher.reset(workingText);
		if (ShadingMatcher.find()) {
			modified = true;
			if (out == null)
				out = new StringBuilder(extractor.getFileText().length());
		
			String profileName = ShadingMatcher.group(1);
			map.scripts.hasSpriteShading.set(!"SHADING_NONE".equals(profileName));
		
			map.scripts.shadingProfile.set(null);
			//TODO
		
			ShadingMatcher.appendReplacement(out, "Call(SetSpriteShading, MAP_SPRITE_SHADING)");
		
			if (modifiedLine) {
				modified = true;
			}
			else {
				out.append(line).append("\n");
			}
		}
		*/

		if (modified) {
			extractor.setFileText(out.toString());
		}
	}

	public static void print(PrintWriter pw, Map map)
	{
		HeaderEntry h = new HeaderEntry("MapProperties");
		h.addDefine("MAP_LOCATION", map.scripts.locationName.get());
		h.print(pw);
	}

	public static void parse(HeaderEntry h, Map map) throws HeaderParseException
	{
		String location = h.getDefine("MAP_LOCATION");
		map.scripts.locationName.set(location);
	}
}
