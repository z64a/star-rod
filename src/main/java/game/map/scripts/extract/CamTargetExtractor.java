package game.map.scripts.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.hit.CameraZoneData;
import game.map.hit.ControlType;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.extract.MapExtractor.MarkerExtractionGroup;
import util.Logger;

public abstract class CamTargetExtractor
{
	private static final Matcher ScriptMatcher = Pattern.compile(
		"(EvtScript\\s+N\\((\\w+)\\)\\s*=\\s*\\{)([\\s\\S]*?)(\\n\\};)"
	).matcher("");

	private static final Pattern LinePattern = Pattern.compile("(\\n[ \\t]*)([^\\n]+)");
	private static final Pattern CallPattern = Pattern.compile(
		"Call\\((\\w+),\\s*(?:CAM_DEFAULT|\\.Cam:Default)(?:,\\s*(.*))?\\)"
	);

	private static class TargetData
	{
		String name;
		String scriptName;
		String indent;

		int replaceStart;
		int replaceEnd;

		boolean useZone;
		boolean hasUseSettingsFrom;
		boolean hasSetPanTarget;
		boolean hasSetCamType;
		boolean hasSetCamPitch;
		boolean hasSetCamDistance;
		boolean hasSetCamPosA;
		boolean hasSetCamPosB;
		boolean hasSetCamPosC;

		int type;
		boolean flag;

		float boomLength;
		float boomPitch;
		float viewPitch;
		float moveSpeed = 1.0f;

		int[] samplePos;
		int[] targetPos;
		int[] posA;
		int[] posB;
		int[] posC;

		boolean generatePan;
	}

	protected static void findAndReplace(MapExtractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = null;
		boolean modified = false;

		ScriptMatcher.reset(workingText);
		while (ScriptMatcher.find()) {
			String scriptStart = ScriptMatcher.group(1);
			String scriptName = ScriptMatcher.group(2);
			String scriptBody = ScriptMatcher.group(3);
			String scriptEnd = ScriptMatcher.group(4);

			TargetData target = scanTargetScript(extractor, scriptName, scriptBody);
			if (target == null) {
				if (out == null)
					out = new StringBuilder(workingText.length());
				ScriptMatcher.appendReplacement(out, Matcher.quoteReplacement(ScriptMatcher.group()));
				continue;
			}

			if (out == null)
				out = new StringBuilder(workingText.length());

			addCameraMarker(extractor, target);

			String rewrittenBody = new StringBuilder(scriptBody.length())
				.append(scriptBody, 0, target.replaceStart)
				.append(target.indent)
				.append("GEN_CAM_TARGET(")
				.append(target.name)
				.append(")")
				.append(scriptBody.substring(target.replaceEnd))
				.toString();

			String replacement = scriptStart + rewrittenBody + scriptEnd;
			ScriptMatcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
			modified = true;
		}

		if (modified) {
			ScriptMatcher.appendTail(out);
			extractor.setFileText(out.toString());
		}
	}

	private static TargetData scanTargetScript(MapExtractor extractor, String scriptName, String scriptBody)
	{
		Matcher lineMatcher = LinePattern.matcher(scriptBody);
		TargetData target = null;

		while (lineMatcher.find()) {
			String indent = lineMatcher.group(1);
			String line = stripComment(lineMatcher.group(2).trim());

			Matcher callMatcher = CallPattern.matcher(line);
			if (!callMatcher.matches()) {
				if (target != null)
					break;
				continue;
			}

			String callName = callMatcher.group(1);
			List<String> args = splitArgs(callMatcher.group(2));
			if (!isCameraTargetCall(callName)) {
				if (target != null)
					break;
				continue;
			}

			if (target == null) {
				target = new TargetData();
				target.scriptName = scriptName;
				target.indent = indent;
				target.replaceStart = lineMatcher.start();
			}

			target.replaceEnd = lineMatcher.end();

			switch (callName) {
				case "UseSettingsFrom":
					if (args.size() != 3)
						return null;
					target.useZone = true;
					target.hasUseSettingsFrom = true;
					target.samplePos = parseVec(args);
					break;

				case "SetCamType":
					if (args.size() != 2)
						return null;
					target.hasSetCamType = true;
					target.type = parseCameraType(args.get(0));
					target.flag = parseBool(args.get(1));
					break;

				case "SetCamPitch":
					if (args.size() != 2)
						return null;
					target.hasSetCamPitch = true;
					target.boomPitch = parseFloatArg(args.get(0));
					target.viewPitch = parseFloatArg(args.get(1));
					break;

				case "SetCamDistance":
					if (args.size() != 1)
						return null;
					target.hasSetCamDistance = true;
					target.boomLength = parseFloatArg(args.get(0));
					break;

				case "SetCamPosA":
					if (args.size() != 2)
						return null;
					target.hasSetCamPosA = true;
					target.posA = parseVec(args);
					break;

				case "SetCamPosB":
					if (args.size() != 2)
						return null;
					target.hasSetCamPosB = true;
					target.posB = parseVec(args);
					break;

				case "SetCamPosC":
					if (args.size() != 2)
						return null;
					target.hasSetCamPosC = true;
					target.posC = parseVec(args);
					break;

				case "SetPanTarget":
					if (args.size() != 3)
						return null;
					target.hasSetPanTarget = true;
					target.targetPos = parseVec(args);
					break;

				case "SetCamSpeed":
					if (args.size() != 1)
						return null;
					target.moveSpeed = parseFloatArg(args.get(0));
					break;

				case "PanToTarget":
				case "WaitForCam":
					target.generatePan = true;
					break;
			}
		}

		if (target == null || !target.hasSetPanTarget)
			return null;

		boolean completeZoneTarget = target.useZone && target.hasUseSettingsFrom;
		boolean completeManualTarget = !target.useZone
			&& target.hasSetCamType
			&& target.hasSetCamPitch
			&& target.hasSetCamDistance
			&& target.hasSetCamPosA
			&& target.hasSetCamPosB
			&& target.hasSetCamPosC;

		if (!completeZoneTarget && !completeManualTarget)
			return null;

		target.name = extractor.getNextName(getBaseName(scriptName, target.generatePan), -1);
		return target;
	}

	private static void addCameraMarker(MapExtractor extractor, TargetData target)
	{
		Marker marker = new Marker(target.name, MarkerType.CamTarget,
			target.targetPos[0], target.targetPos[1], target.targetPos[2], 0);

		marker.cameraComponent.generatePan.set(target.generatePan);
		marker.cameraComponent.moveSpeed.set(target.moveSpeed);
		marker.cameraComponent.useZone.set(target.useZone);

		if (target.useZone) {
			if (!samePosition(target.samplePos, target.targetPos)) {
				Logger.logfWarning(
					"%s uses different camera sample and target positions; using SetPanTarget for marker position.",
					target.scriptName
				);
			}

			marker.cameraComponent.overrideAngles.set(target.hasSetCamPitch);
			if (target.hasSetCamPitch) {
				marker.cameraComponent.boomPitch.set(target.boomPitch);
				marker.cameraComponent.viewPitch.set(target.viewPitch);
			}

			marker.cameraComponent.overrideDist.set(target.hasSetCamDistance);
			if (target.hasSetCamDistance)
				marker.cameraComponent.boomLength.set(target.boomLength);
		}
		else {
			marker.cameraComponent.controlData = new CameraZoneData(marker, getCameraData(target));
		}

		extractor.addMarker(marker, MarkerExtractionGroup.CAMERA);
	}

	private static int[] getCameraData(TargetData target)
	{
		if (ControlType.getType(target.type) == ControlType.TYPE_4) {
			return new int[] {
					target.type,
					Float.floatToIntBits(target.boomLength),
					Float.floatToIntBits(target.boomPitch),
					Float.floatToIntBits(target.posA[0]),
					Float.floatToIntBits(target.posB[0]),
					Float.floatToIntBits(target.posA[1]),
					Float.floatToIntBits(target.posC[0]),
					Float.floatToIntBits(target.posB[1]),
					Float.floatToIntBits(target.posC[1]),
					Float.floatToIntBits(target.viewPitch),
					target.flag ? 1 : 0
			};
		}
		else {
			return new int[] {
					target.type,
					Float.floatToIntBits(target.boomLength),
					Float.floatToIntBits(target.boomPitch),
					Float.floatToIntBits(target.posA[0]),
					Float.floatToIntBits(target.posA[1]),
					Float.floatToIntBits(target.posB[0]),
					Float.floatToIntBits(target.posB[1]),
					Float.floatToIntBits(target.posC[0]),
					Float.floatToIntBits(target.posC[1]),
					Float.floatToIntBits(target.viewPitch),
					target.flag ? 1 : 0
			};
		}
	}

	private static boolean isCameraTargetCall(String callName)
	{
		switch (callName) {
			case "UseSettingsFrom":
			case "SetCamType":
			case "SetCamPitch":
			case "SetCamDistance":
			case "SetCamPosA":
			case "SetCamPosB":
			case "SetCamPosC":
			case "SetPanTarget":
			case "SetCamSpeed":
			case "PanToTarget":
			case "WaitForCam":
				return true;
		}

		return false;
	}

	private static String stripComment(String line)
	{
		int index = line.indexOf("//");
		if (index >= 0)
			return line.substring(0, index).trim();
		else
			return line;
	}

	private static List<String> splitArgs(String args)
	{
		List<String> list = new ArrayList<>();
		if (args == null || args.isBlank())
			return list;

		int depth = 0;
		int start = 0;
		for (int i = 0; i < args.length(); i++) {
			char c = args.charAt(i);
			switch (c) {
				case '(':
				case '[':
				case '{':
					depth++;
					break;
				case ')':
				case ']':
				case '}':
					depth--;
					break;
				case ',':
					if (depth == 0) {
						list.add(args.substring(start, i).trim());
						start = i + 1;
					}
					break;
			}
		}

		list.add(args.substring(start).trim());
		return list;
	}

	private static int[] parseVec(List<String> args)
	{
		int[] vec = new int[args.size()];
		for (int i = 0; i < args.size(); i++)
			vec[i] = Math.round(parseFloatArg(args.get(i)));
		return vec;
	}

	private static float parseFloatArg(String arg)
	{
		arg = normalizeToken(arg);
		if (arg.matches("[0-9A-Fa-f]{8}")) {
			return Integer.parseUnsignedInt(arg, 16);
		}
		return Float.parseFloat(arg);
	}

	private static int parseCameraType(String arg)
	{
		arg = normalizeToken(arg);

		Matcher typeMatcher = Pattern.compile("(?:TYPE_|CAMERA_TYPE_|CAM_TYPE_)(\\d+)").matcher(arg);
		if (typeMatcher.find())
			return Integer.parseInt(typeMatcher.group(1));

		if (arg.matches("[0-9A-Fa-f]{8}"))
			return Integer.parseUnsignedInt(arg, 16);

		return Integer.decode(arg);
	}

	private static boolean parseBool(String arg)
	{
		arg = normalizeToken(arg);
		return "true".equalsIgnoreCase(arg) || ".True".equals(arg) || "TRUE".equals(arg);
	}

	private static String normalizeToken(String arg)
	{
		arg = arg.trim();

		if (arg.startsWith("Float(") && arg.endsWith(")"))
			arg = arg.substring("Float(".length(), arg.length() - 1);
		else if (arg.startsWith("Fixed(") && arg.endsWith(")"))
			arg = arg.substring("Fixed(".length(), arg.length() - 1);
		else if (arg.startsWith("*Fixed[") && arg.endsWith("]"))
			arg = arg.substring("*Fixed[".length(), arg.length() - 1);

		if (arg.endsWith("`") || arg.endsWith("'") || arg.endsWith("f") || arg.endsWith("F"))
			arg = arg.substring(0, arg.length() - 1);

		return arg;
	}

	private static boolean samePosition(int[] a, int[] b)
	{
		if (a == null || b == null || a.length != b.length)
			return false;

		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i])
				return false;
		}

		return true;
	}

	private static String getBaseName(String scriptName, boolean generatePan)
	{
		String[] prefixes = {
				"EVS_SetCamera_",
				"EVS_SetCam_",
				"EVS_CamTarget_",
				"EVS_Camera_",
				"EVS_PanCamera_",
				"EVS_PanCam_"
		};

		for (String prefix : prefixes) {
			if (scriptName.startsWith(prefix) && scriptName.length() > prefix.length())
				return scriptName.substring(prefix.length());
		}

		return generatePan ? "PanCamera" : "CamTarget";
	}
}
