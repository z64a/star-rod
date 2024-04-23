package assets.ui;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.Environment;
import assets.AssetHandle;
import assets.AssetManager;
import util.Logger;

public class MapAsset extends AssetHandle
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		long t0 = System.nanoTime();

		AssetManager.getMapSources();

		long t1 = System.nanoTime();
		double sec = (t1 - t0) / 1e9;
		Logger.logf("Loaded map info %.02f ms", sec * 1e3);

		Environment.exit();
	}

	private static final Matcher MapTagMatcher = Pattern.compile("\\s*<Map .+>\\s*").matcher("");
	private static final Matcher MapDescMatcher = Pattern.compile(".+desc=\"([^\"]+)\".+").matcher("");

	public String desc = "";

	public MapAsset(AssetHandle asset)
	{
		super(asset);

		// need to read Map tag quickly, do not parse whole XML file
		try (BufferedReader in = new BufferedReader(new FileReader(asset))) {
			String line;

			while (true) {
				line = in.readLine();
				if (line == null) {
					return; // encountered final line without finding Map tag
				}

				MapTagMatcher.reset(line);

				if (MapTagMatcher.matches())
					break;
			}

			MapDescMatcher.reset(line);
			if (MapDescMatcher.matches()) {
				desc = MapDescMatcher.group(1);
			}
		}
		catch (IOException e) {
			Logger.logError(e.getMessage());
			desc = "READ ERROR";
		}
	}
}
