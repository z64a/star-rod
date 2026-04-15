package game.map.scripts.nextract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.Map;

public class CamSetupExtractor
{
	private static final Matcher MainScriptMatcher = Pattern.compile(
		"(EvtScript\\s+N\\(EVS_Main\\)\\s*=\\s*\\{)([\\s\\S]*?)(\\n\\};)"
	).matcher("");

	private static final Pattern LinePattern = Pattern.compile("(\\n[ \\t]*)([^\\n]+)");
	private static final Pattern PerspectivePattern = Pattern.compile(
		"Call\\(SetCamPerspective,\\s*CAM_DEFAULT,\\s*CAM_UPDATE_FROM_ZONE,\\s*(\\S+),\\s*(\\S+),\\s*(\\S+)\\)"
	);
	private static final Pattern BgColorPattern = Pattern.compile(
		"Call\\(SetCamBGColor,\\s*CAM_DEFAULT,\\s*(\\S+),\\s*(\\S+),\\s*(\\S+)\\)"
	);
	private static final Pattern EnabledPattern = Pattern.compile(
		"Call\\(SetCamEnabled,\\s*CAM_DEFAULT,\\s*(true|false)\\)"
	);
	private static final Pattern LeadPlayerPattern = Pattern.compile(
		"Call\\(SetCamLeadPlayer,\\s*CAM_DEFAULT,\\s*(true|false)\\)"
	);

	private static class CamSetup
	{
		String indent;

		int vfov;
		int nearClip;
		int farClip;

		int bgR;
		int bgG;
		int bgB;

		boolean enabled = true;
		boolean leadPlayer = true;
		boolean camEnabledLast = false;

		boolean foundPerspective;
		boolean foundBgColor;
		boolean foundEnabled;
		boolean foundLeadPlayer;
		boolean minimalOnly;

		int replaceStart;
		int replaceEnd;
	}

	protected static void findAndReplace(Map map, NewExtractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		MainScriptMatcher.reset(workingText);

		boolean modified = false;
		while (MainScriptMatcher.find()) {
			String scriptStart = MainScriptMatcher.group(1);
			String scriptBody = MainScriptMatcher.group(2);
			String scriptEnd = MainScriptMatcher.group(3);

			CamSetup setup = scanCameraSetup(map.getName(), scriptBody);
			if (setup == null) {
				if (out == null)
					out = new StringBuilder(workingText.length());
				MainScriptMatcher.appendReplacement(out, Matcher.quoteReplacement(MainScriptMatcher.group()));
				continue;
			}

			if (out == null)
				out = new StringBuilder(workingText.length());

			// save extracted values
			map.features.camVfov.set(setup.vfov);
			map.features.camNearClip.set(setup.nearClip);
			map.features.camFarClip.set(setup.farClip);

			if (setup.foundBgColor) {
				map.features.bgColorR.set(setup.bgR);
				map.features.bgColorG.set(setup.bgG);
				map.features.bgColorB.set(setup.bgB);
			}

			map.features.camLeadsPlayer.set(setup.leadPlayer);
			map.features.camEnabledLast = setup.camEnabledLast;

			String rewrittenBody;
			if (setup.minimalOnly) {
				modified = true;
				rewrittenBody = new StringBuilder(scriptBody.length())
					.append(scriptBody, 0, setup.replaceStart)
					.append(setup.indent)
					.append("Call(SetCamPerspective, CAM_DEFAULT, CAM_UPDATE_FROM_ZONE, GEN_CAM_VFOV, GEN_CAM_NEAR_CLIP, GEN_CAM_FAR_CLIP)")
					.append(scriptBody.substring(setup.replaceEnd))
					.toString();
			}
			else {
				modified = true;
				rewrittenBody = new StringBuilder(scriptBody.length())
					.append(scriptBody, 0, setup.replaceStart)
					.append(setup.indent)
					.append("GEN_SETUP_CAMERA()")
					.append(scriptBody.substring(setup.replaceEnd))
					.toString();
			}

			String replacement = scriptStart + rewrittenBody + scriptEnd;
			MainScriptMatcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}

		if (modified) {
			MainScriptMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}

	private static CamSetup scanCameraSetup(String mapName, String scriptBody)
	{
		Matcher lineMatcher = LinePattern.matcher(scriptBody);

		boolean foundPerspective = false;
		CamSetup setup = null;

		while (lineMatcher.find()) {
			String indent = lineMatcher.group(1);
			String line = lineMatcher.group(2).trim();

			Matcher perspectiveMatcher = PerspectivePattern.matcher(line);
			if (!foundPerspective) {
				if (!perspectiveMatcher.matches())
					continue;

				setup = new CamSetup();
				setup.indent = indent;
				setup.replaceStart = lineMatcher.start();
				setup.replaceEnd = lineMatcher.end();

				setup.vfov = Integer.parseInt(perspectiveMatcher.group(1));
				setup.nearClip = Integer.parseInt(perspectiveMatcher.group(2));
				setup.farClip = Integer.parseInt(perspectiveMatcher.group(3));

				setup.foundPerspective = true;
				foundPerspective = true;
				continue;
			}

			Matcher bgMatcher = BgColorPattern.matcher(line);
			if (bgMatcher.matches()) {
				if (setup.foundBgColor)
					throw new RuntimeException("DUPLICATE BG_COLOR IN " + mapName);

				setup.bgR = Integer.parseInt(bgMatcher.group(1));
				setup.bgG = Integer.parseInt(bgMatcher.group(2));
				setup.bgB = Integer.parseInt(bgMatcher.group(3));
				setup.foundBgColor = true;
				setup.replaceEnd = lineMatcher.end();
				continue;
			}

			Matcher enabledMatcher = EnabledPattern.matcher(line);
			if (enabledMatcher.matches()) {
				if (setup.foundEnabled)
					throw new RuntimeException("DUPLICATE ENABLED IN " + mapName);

				setup.enabled = Boolean.parseBoolean(enabledMatcher.group(1));
				setup.foundEnabled = true;
				setup.camEnabledLast = true;
				setup.replaceEnd = lineMatcher.end();
				continue;
			}

			Matcher leadMatcher = LeadPlayerPattern.matcher(line);
			if (leadMatcher.matches()) {
				if (setup.foundLeadPlayer)
					throw new RuntimeException("DUPLICATE LEAD_PLAYER IN " + mapName);

				boolean leadPlayer = Boolean.parseBoolean(leadMatcher.group(1));
				setup.leadPlayer = leadPlayer;
				setup.foundLeadPlayer = true;
				setup.camEnabledLast = false;
				setup.replaceEnd = lineMatcher.end();
				continue;
			}

			break;
		}

		if (setup == null)
			return null;

		if (!setup.foundLeadPlayer)
			setup.leadPlayer = true;

		if (!setup.foundBgColor) {
			setup.bgR = 0;
			setup.bgG = 0;
			setup.bgB = 0;
		}

		setup.minimalOnly = setup.foundPerspective
			&& !setup.foundBgColor
			&& !setup.foundEnabled
			&& !setup.foundLeadPlayer;

		return setup;
	}
}
