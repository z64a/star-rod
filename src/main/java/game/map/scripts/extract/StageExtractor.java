package game.map.scripts.extract;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.Environment;
import app.LoadingBar;
import app.input.IOUtils;
import assets.AssetManager;
import game.map.Map;
import util.Logger;
import util.NameUtils;

public class StageExtractor
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		LoadingBar.show("Extracting Stage Data");

		File f = Directories.PROJ_SRC_STAGE.toFile();
		File[] areaDirs = f.listFiles();
		Arrays.sort(areaDirs);

		for (File areaDir : areaDirs) {
			if (areaDir.isDirectory() && areaDir.getName().matches("area_\\w+")) {
				String areaName = areaDir.getName().substring(5);
				File[] stageFiles = areaDir.listFiles();
				Arrays.sort(stageFiles);

				for (File stageFile : stageFiles) {
					if (stageFile.getName().startsWith(areaName)) {
						new StageExtractor(stageFile);
					}
				}
			}
		}

		LoadingBar.dismiss();
		Environment.exit();
	}

	private static final Matcher StageNameMatcher = Pattern.compile("(\\w+)_bt(\\d+)([a-z]+)?").matcher("");

	private final String stageName;
	private String fileText;
	public boolean fileModified = false;
	public boolean remakeHeader = false;

	public StageExtractor(File stageFile) throws IOException
	{
		stageName = FilenameUtils.getBaseName(stageFile.getName());

		StageNameMatcher.reset(stageName);
		if (!StageNameMatcher.matches()) {
			Logger.logWarning("Couldn't parse stage name: " + stageName);
			return;
		}

		// remove the "bt" separating area name and map number
		String baseName = StageNameMatcher.group(1) + "_" + StageNameMatcher.group(2);
		String suffix = StageNameMatcher.group(3); // a,b,c,...
		if (suffix == null)
			suffix = "";

		File mapFile = AssetManager.getMap(baseName);
		if (!mapFile.exists()) {
			Logger.logWarning("Couldn't find map file for stage: " + baseName);
			return;
		}

		Map map = Map.loadMap(mapFile);

		// look for things to extract

		fileText = Files.readString(stageFile.toPath());
		fileModified = false;

		//TODO

		if (fileModified) {
			fileText = tryInjectHeader(baseName, fileText);
			Files.writeString(stageFile.toPath(), fileText);
			remakeHeader = true;
		}

		// generate the header

		String headerName = baseName + ".gen.h";
		tryInjectHeader(headerName, fileText);

		if (suffix.isEmpty()) {
			File genHeader = new File(stageFile.getParent(), headerName);

			try (PrintWriter pw = IOUtils.getBufferedPrintWriter(genHeader)) {
				pw.println("#include \"star_rod_macros.h\"");
				pw.println();

				TexPannerExtractor.print(pw, map);
			}
		}

		// save the map file

		try {
			map.saveMap();
		}
		catch (Exception e) {
			Logger.printStackTrace(e);
			Logger.logError("Failed to save " + stageName + "!");
		}
	}

	public String getFileText()
	{
		return fileText;
	}

	public void setFileText(String newText)
	{
		fileText = newText;
		fileModified = true;
	}

	private static final String INC_BTL = "#include \"battle/battle.h\"";

	private String tryInjectHeader(String headerName, String fileText) throws IOException
	{
		String include = "#include \"" + headerName + "\"";

		if (fileText.contains(include))
			return fileText;

		// try injecting on the line after "battle/battle.h", else append to the start
		if (fileText.contains(INC_BTL))
			fileText = fileText.replace(INC_BTL, INC_BTL + "\n" + include);
		else
			fileText = include + "\n" + fileText;

		return fileText;
	}

	public String getGenName(String name)
	{
		return NameUtils.toEnumStyle("GEN_" + name);
	}
}
