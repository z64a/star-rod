package game.map.scripts.extract;

import static game.map.shape.TexturePanner.*;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.Map;
import game.map.MapObject.MapObjectType;
import game.map.marker.FormatStringList;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;
import game.map.shape.Model;
import game.map.shape.TexturePanner;
import game.map.shape.TexturePanner.PannerParams;
import util.IterableListModel;

public class TexPannerExtractor
{
	private static final Matcher TexPanMatcher = Pattern.compile(
		"(\\s*)TEX_PAN_PARAMS_(\\w+)\\((.+)\\)\\s*").matcher("");

	private static final Matcher SetPannerMatcher = Pattern.compile(
		"\\s*Call\\(SetTexPanner,\\s*MODEL_(\\w+),\\s*TEX_PANNER_(\\w+)\\)\\s*").matcher("");

	protected static void findAndReplace(Map map, Extractor extractor)
	{
		String workingText = extractor.getFileText();
		StringBuilder out = new StringBuilder(workingText.length());
		boolean modified = false;

		HashSet<Model> modelsAssignedPanners = new HashSet<>();
		List<String> pannerLines = new ArrayList<>();

		for (String line : workingText.split("\\r?\\n")) {
			boolean modifiedLine = false;
			TexPanMatcher.reset(line);
			SetPannerMatcher.reset(line);

			if (TexPanMatcher.matches()) {
				String[] args = TexPanMatcher.group(3).replaceAll("\\s", "").split(",");

				if (TexPanMatcher.group(2).equals("ID")) {
					if (args[0].startsWith("TEX_PANNER_"))
						args[0] = "0x" + args[0].substring("TEX_PANNER_".length());
					int curPanID = Integer.decode(args[0]);
					out.append(TexPanMatcher.group(1)); // indent
					out.append(String.format("GEN_TEX_PANNER_%X%n", curPanID));
				}

				pannerLines.add(line);
				modifiedLine = true;
			}
			else {
				if (!pannerLines.isEmpty()) {
					// flush lines
					parsePannerLines(map, pannerLines);
					pannerLines.clear();
				}
			}

			if (SetPannerMatcher.matches()) {
				String modelName = SetPannerMatcher.group(1);
				int pannerID = Integer.parseInt(SetPannerMatcher.group(2), 16);
				Model mdl = (Model) map.find(MapObjectType.MODEL, modelName);

				if (mdl != null && !modelsAssignedPanners.contains(mdl)) {
					mdl.pannerID.set(pannerID);
					modelsAssignedPanners.add(mdl);
				}
			}

			if (modifiedLine) {
				modified = true;
			}
			else {
				out.append(line).append("\n");
			}
		}

		if (modified) {
			extractor.setFileText(out.toString());
		}
	}

	private static void parsePannerLines(Map map, List<String> lines)
	{
		IterableListModel<TexturePanner> panners = map.scripts.texPanners;
		int panID = 0;

		for (String line : lines) {
			TexPanMatcher.reset(line);

			if (TexPanMatcher.matches()) {
				String[] args = TexPanMatcher.group(3).replaceAll("\\s", "").split(",");
				PannerParams panParams;

				switch (TexPanMatcher.group(2)) {
					case "ID":
						if (args[0].startsWith("TEX_PANNER_"))
							args[0] = "0x" + args[0].substring("TEX_PANNER_".length());
						panID = Integer.decode(args[0]);
						panParams = panners.get(panID).params;
						panParams.maxUV = DEFAULT_MAXIMUM;
						panParams.maxST = panners.get(panID).params.maxUV / TEXEL_RATIO;
						break;
					case "MAX":
						panParams = panners.get(panID).params;
						panParams.useTexels = true;
						panParams.maxUV = (int) (long) Long.decode(args[0]);
						panParams.maxST = panners.get(panID).params.maxUV / TEXEL_RATIO;
						break;
					case "FREQ":
						panParams = panners.get(panID).params;
						for (int i = 0; i < 4; i++) {
							panParams.freq[i] = Integer.decode(args[i]);
						}
						break;
					case "STEP":
						panParams = panners.get(panID).params;
						for (int i = 0; i < 4; i++) {
							int value = Integer.decode(args[i]);
							panParams.rate[i] = value;
							panParams.rate[i + MAIN_S] = value / TEXEL_RATIO;
							if ((value % TEXEL_RATIO) != 0)
								panParams.useTexels = false;
						}
						break;
					case "INIT":
						panParams = panners.get(panID).params;
						for (int i = 0; i < 4; i++) {
							int value = Integer.decode(args[i]);
							panParams.init[i] = value;
							panParams.init[i + MAIN_S] = value / TEXEL_RATIO;
							if ((value % TEXEL_RATIO) != 0)
								panParams.useTexels = false;
						}
						break;
				}
			}
		}
	}

	public static void print(PrintWriter pw, Map map)
	{
		for (TexturePanner panner : map.scripts.texPanners) {
			if (panner.params.generate || panner.isNonzero()) {
				PannerParams out = panner.params.getOutput();

				HeaderEntry h = new HeaderEntry("TexPanner");
				FormatStringList lines = new FormatStringList();

				lines.addf("    TEX_PAN_PARAMS_ID(TEX_PANNER_%X)", panner.panID);

				if (out.useTexels || out.maxUV != DEFAULT_MAXIMUM)
					lines.addf("    TEX_PAN_PARAMS_MAX(0x%X)", out.maxUV);

				lines.addf("    TEX_PAN_PARAMS_STEP(%6d,%6d,%6d,%6d)", out.rate[0], out.rate[1], out.rate[2], out.rate[3]);
				lines.addf("    TEX_PAN_PARAMS_FREQ(%6d,%6d,%6d,%6d)", out.freq[0], out.freq[1], out.freq[2], out.freq[3]);
				lines.addf("    TEX_PAN_PARAMS_INIT(%6d,%6d,%6d,%6d)", out.init[0], out.init[1], out.init[2], out.init[3]);

				h.addDefine(String.format("TEX_PANNER_%X", panner.panID), lines);

				h.print(pw);
			}
		}
	}

	public static void parse(HeaderEntry h, Map map) throws HeaderParseException
	{
		parsePannerLines(map, h.getBlockDefine("*"));
	}
}
