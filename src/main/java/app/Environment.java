package app;

import java.awt.Frame;
import java.awt.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.yaml.snakeyaml.Yaml;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import app.Resource.ResourceType;
import app.config.Config;
import app.config.Options;
import app.config.Options.Scope;
import app.input.IOUtils;
import assets.AssetExtractor;
import assets.ExpectedAsset;
import game.ProjectDatabase;
import game.entity.EntityExtractor;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.DirChooser;
import game.message.font.FontManager;
import util.Logger;
import util.Priority;

public abstract class Environment
{
	private static final String FN_MAIN_CONFIG = "main.cfg";
	private static final String FN_PROJ_CONFIG = "star_rod.cfg";
	private static final String FN_BASEROM = "ver/us/baserom.z64";
	private static final String FN_SPLAT = "splat.yaml";

	public static ImageIcon ICON_DEFAULT = loadIconResource(ResourceType.Icon, "icon.png");
	public static ImageIcon ICON_ERROR = null;

	private static enum OSFamily
	{
		Windows,
		Mac,
		Linux,
		Unknown
	}

	private static OSFamily osFamily = OSFamily.Unknown;

	private static boolean commandLine = false;
	private static boolean fromJar = false;
	private static File codeSource;

	public static Config mainConfig = null;
	public static Config projectConfig = null;

	private static DirChooser projectChooser;
	private static File projectDirectory = null;

	private static String gameVersion = "";

	private static File usBaseRom;
	private static ByteBuffer romBytes;

	public static List<File> assetDirectories;

	private static boolean initialized = false;

	private static boolean isDeluxe = false;

	private static String versionString;
	private static String gitBuildBranch;
	private static String gitBuildCommit;
	private static String gitBuildTag;

	public static boolean isDeluxe()
	{
		return isDeluxe;
	}

	public static String getVersionString()
	{
		return versionString;
	}

	public static String decorateTitle(String title)
	{
		StringBuilder sb = new StringBuilder();

		if (isDeluxe)
			sb.append("Deluxe ");
		sb.append(title);

		sb.append(" (v").append(versionString).append(")");

		if (fromJar && (gitBuildTag == null || !gitBuildTag.startsWith("v")) && gitBuildCommit != null)
			sb.append(" (").append(gitBuildCommit.substring(0, 8)).append(")");

		return sb.toString();
	}

	public static void initialize()
	{
		initialize(false);
	}

	public static void initialize(boolean isCommandLine)
	{
		if (initialized)
			return;

		if (SystemUtils.IS_OS_WINDOWS)
			osFamily = OSFamily.Windows;
		else if (SystemUtils.IS_OS_LINUX)
			osFamily = OSFamily.Linux;
		else if (SystemUtils.IS_OS_MAC)
			osFamily = OSFamily.Mac;
		else
			osFamily = OSFamily.Unknown;

		commandLine = isCommandLine;

		isDeluxe = (Math.random() < 0.001);

		// running from a jar, we need to set the natives directory at runtime
		try {
			CodeSource src = StarRodMain.class.getProtectionDomain().getCodeSource();
			String sourceName = src.getLocation().toURI().toString();

			Matcher matcher = Pattern.compile("%[0-9A-Fa-f]{2}").matcher(sourceName);
			StringBuffer sb = new StringBuffer(sourceName.length());
			while (matcher.find()) {
				String encoded = matcher.group(0).substring(1);
				matcher.appendReplacement(sb, "" + (char) Integer.parseInt(encoded, 16));
			}
			matcher.appendTail(sb);
			sourceName = sb.toString();

			// WSL
			if (sourceName.startsWith("file:/"))
				sourceName = sourceName.substring(5);

			codeSource = new File(sourceName);
			fromJar = (sourceName.endsWith(".jar"));
			Logger.log("Executing from " + codeSource.getAbsolutePath());
		}
		catch (URISyntaxException e) {
			Logger.logError("Could not determine path to StarRod code source!");
			StarRodMain.handleEarlyCrash(e);
		}

		if (fromJar) {
			ClassLoader cl = Environment.class.getClassLoader();
			try {
				Manifest manifest = new Manifest(cl.getResourceAsStream("META-INF/MANIFEST.MF"));
				Attributes attr = manifest.getMainAttributes();
	
				versionString = attr.getValue("App-Version");
				gitBuildBranch = attr.getValue("Build-Branch");
				gitBuildCommit = attr.getValue("Build-Commit");
				gitBuildTag = attr.getValue("Build-Tag");

				// Git info not available when built with Nix; normalise empty strings to null
				if (gitBuildBranch != null && gitBuildBranch.isEmpty()) gitBuildBranch = null;
				if (gitBuildCommit != null && gitBuildCommit.isEmpty()) gitBuildCommit = null;
				if (gitBuildTag != null && gitBuildTag.isEmpty()) gitBuildTag = null;
	
				Logger.logf("Detected version %s (%s-%s)", versionString, gitBuildBranch, gitBuildCommit);
			}
			catch (IOException | IndexOutOfBoundsException e) {
				Logger.logError("Could not read MANIFEST.MF");
				Logger.printStackTrace(e);
			}
		}
		else {
			try {
				Properties prop = new Properties();
				prop.load(new FileInputStream(new File("./app.properties")));
				versionString = prop.getProperty("version");
				Logger.logf("Detected version %s (IDE)", versionString);
			}
			catch (IOException e) {
				Logger.logError("Could not read version properties file: " + e.getMessage());
			}
		}

		projectChooser = new DirChooser(codeSource.getParentFile(), "Select Project Directory");

		// Create user directories
		getUserConfigDir().mkdirs();
		getUserStateDir().mkdirs();

		try {
			checkForDependencies();
			File projDir = readMainConfig();

			if (projDir == null) {
				// User declined to select a project directory
				exit();
			}

			boolean logDetails = mainConfig.getBoolean(Options.LogDetails);
			Logger.setDefaultOuputPriority(logDetails ? Priority.DETAIL : Priority.STANDARD);

			if (!isCommandLine) {
				Themes.setThemeByKey(Environment.mainConfig.getString(Options.Theme));
				// UIManager.put("TabbedPane.tabWidthMode", "compact");
				// UIManager.put("TabbedPane.showTabSeparators", true);
				// UIManager.put("TabbedPane.tabSeparatorsFullHeight", true);

				if (fromJar && mainConfig.getBoolean(Options.CheckForUpdates))
					checkForUpdate();
			}

			LoadingBar.show("Loading Project", true);
			boolean validProject = loadProject(projDir);
			if (!validProject)
				exit();
		}
		catch (Throwable t) {
			StarRodMain.handleEarlyCrash(t);
		}
		finally {
			LoadingBar.dismiss();
		}

		initialized = true;
	}

	public static void exit()
	{
		exit(0);
	}

	public static void exit(int status)
	{
		System.exit(status);
	}

	public static boolean isCommandLine()
	{
		return commandLine;
	}

	public static File getWorkingDirectory()
	{
		if (fromJar)
			return codeSource.getParentFile();
		else
			return new File(".");
	}

	public static File getProjectDirectory()
	{
		return projectDirectory;
	}

	public static File getSourceDirectory()
	{
		return new File(projectDirectory, "/src/");
	}

	public static File getProjectFile(String relativePath)
	{
		return new File(projectDirectory, relativePath);
	}

	public static void checkForUpdate()
	{
		try {
			URL url = new URI("https://api.github.com/repos/z64a/star-rod/releases/latest").toURL();
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(1000);
			connection.setReadTimeout(1000);

			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
				JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();
				String latestVersion = jsonObject.get("tag_name").getAsString();

				if (!latestVersion.equals("v" + versionString)) {
					Logger.log("Detected newer remote version: " + latestVersion);

					SwingUtils.getWarningDialog()
						.setTitle("Update Available")
						.setMessage("A newer version is available!", "Please visit the GitHub repo to download it.")
						.show();
				}
			}
			else {
				Logger.logError("Update check failed (response code: " + responseCode + ")");
			}
		}
		catch (Exception e) {
			Logger.logError("IOException while checking for updates: " + e.getMessage());
			Logger.printStackTrace(e);
		}
	}

	private static final void checkForDependencies() throws IOException
	{
		File db = Directories.SEED_DATABASE.toFile();

		if (!db.exists() || !db.isDirectory()) {
			SwingUtils.getErrorDialog()
				.setTitle("Missing Directory")
				.setMessage("Could not find required directory: " + db.getName(),
					"It should be in the same directory as the jar.")
				.show();

			exit();
		}

		// Copy SEED_DATABASE to DATABASE if DATABASE does not exist
		// TODO: handle upgrades
		File writeDb = Directories.DATABASE.toFile();
		if (!writeDb.exists()) {
			writeDb.mkdirs();
			FileUtils.copyDirectory(db, writeDb);
		}
	}

	public static final File getUserConfigDir()
	{
		String userHome = System.getProperty("user.home");

		if (isWindows()) return new File(userHome, "/AppData/Local/StarRod/");

		String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
		String dotConfig = (xdgConfigHome != null && !xdgConfigHome.isEmpty())
			? xdgConfigHome
			: (userHome + "/.config");
		return new File(dotConfig, "/star-rod/");
	}

	public static final File getUserStateDir()
	{
		String userHome = System.getProperty("user.home");

		if (isWindows()) return new File(userHome, "/AppData/Local/StarRod/");

		String xdgStateHome = System.getenv("XDG_STATE_HOME");
		String dotState = (xdgStateHome != null && !xdgStateHome.isEmpty())
			? xdgStateHome
			: (userHome + "/.local/state");
		return new File(dotState, "/star-rod/");
	}

	private static final File readMainConfig() throws IOException
	{
		File configDir = getUserConfigDir();

		File configFile = new File(configDir, FN_MAIN_CONFIG);

		// backwards compatibility for Star Rod 0.9.2 and below: move old config to new location
		File oldConfigFile = new File(codeSource.getParent(), "cfg/main.cfg");
		if (oldConfigFile.exists()) {
			FileUtils.moveFile(oldConfigFile, configFile);
		}

		// we may need to create a new config file here
		if (!configFile.exists()) {
			mainConfig = makeConfig(configFile, Scope.Main);
		}
		else {
			// read existing config
			mainConfig = new Config(configFile, Scope.Main);
			mainConfig.readConfig();
		}

		// if current directory seems to be a decomp project, use it regardless of config
		File decompCfg = new File("./ver/us/", FN_SPLAT);
		if (decompCfg.exists()) {
			return new File(".");
		}

		// get project directory from config
		String directoryName = mainConfig.getString(Options.ProjPath);
		if (directoryName != null) {
			File dir;
			if (directoryName.startsWith("."))
				dir = new File(codeSource.getParent(), directoryName);
			else
				dir = new File(directoryName);

			if (dir.exists() && dir.isDirectory()) {
				return dir;
			}
		}

		// project directory is missing, prompt to select new one
		SwingUtils.getErrorDialog()
			.setTitle("Missing Project Directory")
			.setMessage("Could not find project directory!", "Please select a new one.")
			.show();

		return promptSelectProject();
	}

	public static void promptChangeProject() throws IOException
	{
		if (projectChooser.prompt() == ChooseDialogResult.APPROVE) {
			File dirChoice = projectChooser.getSelectedFile();
			loadProject(dirChoice);
		}
	}

	private static File promptSelectProject()
	{
		if (projectChooser.prompt() == ChooseDialogResult.APPROVE)
			return projectChooser.getSelectedFile();
		else
			return null;
	}

	private static void showErrorMessage(String title, String fmt, Object ... args)
	{
		String message = String.format(fmt, args);
		if (isCommandLine())
			Logger.logError(message);
		else
			SwingUtils.getErrorDialog()
				.setTitle(title)
				.setMessage(message)
				.show();
	}

	public static boolean loadProject(File projectDir) throws IOException
	{
		if (projectDir == null) {
			showErrorMessage("Invalid Decomp Project", "No project directory is set.");
			return false;
		}

		if (!projectDir.exists() || !projectDir.isDirectory()) {
			showErrorMessage("Invalid Decomp Project", "Not a valid directory: %n%s", projectDir.getAbsolutePath());
			return false;
		}

		// check version to get appropriate splat
		gameVersion = mainConfig.getString(Options.GameVersion);
		File versionDir = new File(projectDir, "ver/" + gameVersion);
		if (!versionDir.exists()) {
			showErrorMessage("Invalid Decomp Project",
				"Project does not have game version: %s", gameVersion);
			return false;
		}

		// get splat config
		File decompCfg = new File(versionDir, FN_SPLAT);
		if (!decompCfg.exists()) {
			showErrorMessage("Invalid Decomp Project",
				"Could not find splat file for directory: %n%s", decompCfg.getAbsolutePath());
			return false;
		}

		// resolve asset dirs
		try {
			assetDirectories = getAssetDirs(projectDir, decompCfg);
		}
		catch (IOException e) {
			Logger.printStackTrace(e);
			showErrorMessage("Splat Read Exception",
				"IOException while attempting to read splat file: %n%s %n%s", decompCfg.getAbsolutePath(),
				e.getMessage());
			return false;
		}

		// get US baserom
		usBaseRom = new File(projectDir, FN_BASEROM);
		if (!usBaseRom.exists()) {
			showErrorMessage("Missing US Base ROM",
				"Could not find US baserom for project. %n" +
					"Star Rod requries one for asset extraction.");
			return false;
		}

		// save project dir
		projectDirectory = projectDir;
		SwingUtilities.invokeLater(() -> {
			projectChooser.setCurrentDirectory(projectDir);
		});
		Directories.setProjectDirectory(projectDirectory.getAbsolutePath());

		mainConfig.setString(Options.ProjPath, projectDirectory.getAbsolutePath());
		mainConfig.saveConfigFile();

		readProjectConfig();
		reloadIcons();

		ProjectDatabase.initialize();

		// set dump dir
		File dumpDir = new File(usBaseRom.getParentFile(), "/dump/");
		Directories.setDumpDirectory(dumpDir.getAbsolutePath());

		// dump if missing
		if (!dumpDir.exists()) {
			LoadingBar.show("Extracting Baserom");
			Logger.log("Extracting assets from baserom");
			Directories.createDumpDirectories();
			EntityExtractor.extractAll();
			FontManager.dump();
		}

		AssetExtractor.extractAll();

		return true;
	}

	private static void readProjectConfig() throws IOException
	{
		File configFile = new File(projectDirectory, FN_PROJ_CONFIG);

		if (!configFile.exists()) {
			projectConfig = makeConfig(configFile, Scope.Project);
			projectConfig.saveConfigFile();
		}
		else {
			// config exists, read it
			projectConfig = new Config(configFile, Scope.Project);
			projectConfig.readConfig();
		}
	}

	private static Config makeConfig(File configFile, Scope scope) throws IOException
	{
		FileUtils.touch(configFile);
		Config cfg = new Config(configFile, scope);

		// set default values for options
		for (Options opt : Options.values()) {
			if (opt.scope == scope) {
				opt.setToDefault(cfg);
			}
		}

		return cfg;
	}

	public static ByteBuffer getBaseRomBuffer()
	{
		// lazy load
		if (romBytes != null)
			return romBytes;

		try {
			romBytes = IOUtils.getDirectBuffer(usBaseRom).asReadOnlyBuffer();
		}
		catch (IOException e) {
			Logger.printStackTrace(e);
			showErrorMessage("Base ROM Read Exception",
				"IOException while attempting to read baserom: %n%s %n%s", usBaseRom.getAbsolutePath());
			return null;
		}

		return romBytes;
	}

	private static List<File> getAssetDirs(File directory, File splatFile) throws IOException
	{
		Map<String, Object> topLevelMap = new Yaml().load(new FileInputStream(splatFile));

		@SuppressWarnings("unchecked")
		List<String> assetDirNames = (List<String>) topLevelMap.get("asset_stack");

		File assetsDir = new File(directory, "assets");

		List<File> assetDirectories = new ArrayList<>();
		for (String dirName : assetDirNames) {
			assetDirectories.add(new File(assetsDir, dirName));
		}
		return assetDirectories;
	}

	public static boolean isWindows()
	{
		return osFamily == OSFamily.Windows;
	}

	public static boolean isMacOS()
	{
		return osFamily == OSFamily.Mac;
	}

	public static boolean isLinux()
	{
		return osFamily == OSFamily.Linux;
	}

	public static void reloadIcons()
	{
		ICON_DEFAULT = loadIconAsset(ExpectedAsset.ICON_APP);
		if (ICON_DEFAULT == null)
			ICON_DEFAULT = loadIconResource(ResourceType.Icon, "icon.png");
		ICON_ERROR = loadIconAsset(ExpectedAsset.CRASH_GUY);

		for (Frame frame : Frame.getFrames()) {
			if (frame instanceof StarRodFrame srf) {
				srf.reloadIcon();
			}
		}
	}

	public static final Image getDefaultIconImage()
	{
		return (ICON_DEFAULT == null) ? null : ICON_DEFAULT.getImage();
	}

	public static final Image getErrorIconImage()
	{
		return (ICON_DEFAULT == null) ? null : ICON_DEFAULT.getImage();
	}

	private static ImageIcon loadIconAsset(ExpectedAsset asset)
	{
		File imgFile = asset.getFile();
		if (imgFile == null) {
			Logger.logError("Unable to find asset " + asset.getPath());
			return null;
		}

		try {
			return new ImageIcon(ImageIO.read(imgFile));
		}
		catch (IOException e) {
			Logger.logError("Exception while loading image " + asset.getPath());
			return null;
		}
	}

	private static ImageIcon loadIconResource(ResourceType type, String resourceName)
	{
		try {
			return new ImageIcon(ImageIO.read(Resource.getStream(type, resourceName)));
		}
		catch (IOException e) {
			Logger.logError("Exception while loading image " + resourceName);
			return null;
		}
	}
}
