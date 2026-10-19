package app;

import java.awt.Window;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

import org.apache.commons.text.WordUtils;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.IntelliJTheme;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import app.input.IOUtils;
import util.CaseInsensitiveMap;
import util.Logger;
import util.Priority;

public abstract class Themes
{
	public static final String STAR_ROD_THEME_SUFFIX = ".starrod-theme.json";

	private static final CaseInsensitiveMap<Theme> THEME_MAP = new CaseInsensitiveMap<>();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Theme DEFAULT_THEME;
	private static Theme currentTheme = null;

	enum ThemeSource
	{
		BUILT_IN,
		CUSTOM_JAR,
		CUSTOM_JSON,
		STAR_ROD,
	};

	private static class StarRodThemeData
	{
		private int version = 1;
		private String name;
		private String baseTheme;
		private Map<String, String> overrides = new LinkedHashMap<>();
	}

	public static class Theme
	{
		public final String key;
		public final String name;

		public final ThemeSource source;
		public final String className;
		public final String fileName;
		public final String jarEntry;
		public final String baseThemeKey;
		public final Map<String, String> overrides;

		private Theme(ThemeSource source, String key, String name, String className, String fileName, String jarEntry, String baseThemeKey,
			Map<String, String> overrides)
		{
			this.key = key;
			this.name = name;
			this.source = source;
			this.className = className;
			this.fileName = fileName;
			this.jarEntry = jarEntry;
			this.baseThemeKey = baseThemeKey;
			this.overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
		}

		public static Theme createBuiltIn(String name, String className)
		{
			String key = name.replaceAll("\\s+", "");
			return new Theme(ThemeSource.BUILT_IN, key, name, className, "", "", "", Collections.emptyMap());
		}

		public static Theme createFromJar(String fileName, String jarEntry)
		{
			String base = jarEntry.substring(0, jarEntry.length() - ".theme.json".length());
			String key = base.replaceAll("\\s+", "_");
			String name = WordUtils.capitalize(key.replaceAll("_", " "));

			return new Theme(ThemeSource.CUSTOM_JAR, key, name, "", fileName, jarEntry, "", Collections.emptyMap());
		}

		public static Theme createFromJson(File file)
		{
			String fileName = file.getName();
			String base = fileName.substring(0, fileName.length() - ".theme.json".length());
			String key = base.replaceAll("\\s+", "_");
			String name = WordUtils.capitalize(key.replaceAll("_", " "));

			return new Theme(ThemeSource.CUSTOM_JSON, key, name, "", file.getPath(), "", "", Collections.emptyMap());
		}

		private static Theme createStarRod(File file, StarRodThemeData data)
		{
			String fileName = file.getName();
			String key = fileName.substring(0, fileName.length() - STAR_ROD_THEME_SUFFIX.length()).replaceAll("\\s+", "_");
			return new Theme(ThemeSource.STAR_ROD, key, data.name, "", file.getPath(), "", data.baseTheme, data.overrides);
		}

		private static Theme createPreview(String name, Theme baseTheme, Map<String, String> overrides)
		{
			return new Theme(ThemeSource.STAR_ROD, "", name, "", "", "", baseTheme.key, overrides);
		}

		public boolean isBuiltIn()
		{
			return source == ThemeSource.BUILT_IN;
		}

		public boolean isStarRodTheme()
		{
			return source == ThemeSource.STAR_ROD;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static Theme getCurrentTheme()
	{
		return currentTheme;
	}

	public static Collection<Theme> getThemes()
	{
		return THEME_MAP.values();
	}

	public static List<Theme> getBaseThemes()
	{
		List<Theme> themes = new ArrayList<>();
		for (Theme theme : THEME_MAP.values()) {
			if (theme.isBuiltIn())
				themes.add(theme);
		}
		return themes;
	}

	public static Theme getBaseTheme(Theme theme)
	{
		if (theme != null && theme.isStarRodTheme()) {
			Theme baseTheme = THEME_MAP.get(theme.baseThemeKey);
			if (baseTheme != null && baseTheme.isBuiltIn())
				return baseTheme;
		}
		return DEFAULT_THEME;
	}

	public static void refreshUI()
	{
		FlatLaf.updateUILater();
	}

	public static boolean setTheme(Theme theme)
	{
		return setTheme(theme, null);
	}

	private static boolean setTheme(Theme theme, Window excludedWindow)
	{
		if (theme == null)
			theme = DEFAULT_THEME;

		if (theme == currentTheme)
			return true;

		Theme previousTheme = currentTheme;
		if (installTheme(theme)) {
			currentTheme = theme;
			refreshUI(excludedWindow);
			return true;
		}

		Theme rollbackTheme = (previousTheme == null) ? DEFAULT_THEME : previousTheme;
		if (rollbackTheme != theme && installTheme(rollbackTheme)) {
			currentTheme = rollbackTheme;
			refreshUI(excludedWindow);
		}

		return false;
	}

	public static boolean previewTheme(String name, Theme baseTheme, Map<String, String> overrides, Window previewWindow)
	{
		if (baseTheme == null || !baseTheme.isBuiltIn())
			baseTheme = DEFAULT_THEME;
		return setTheme(Theme.createPreview(name, baseTheme, overrides), previewWindow);
	}

	private static void refreshUI(Window excludedWindow)
	{
		if (excludedWindow == null) {
			refreshUI();
			return;
		}

		SwingUtilities.invokeLater(() -> {
			for (Window window : Window.getWindows()) {
				if (window != excludedWindow)
					SwingUtilities.updateComponentTreeUI(window);
			}
		});
	}

	private static boolean installTheme(Theme theme)
	{
		Theme lookAndFeelTheme = theme;
		if (theme.isStarRodTheme()) {
			lookAndFeelTheme = THEME_MAP.get(theme.baseThemeKey);
			if (lookAndFeelTheme == null || !lookAndFeelTheme.isBuiltIn()) {
				Logger.logError("Could not find base theme for " + theme.name + ": " + theme.baseThemeKey);
				return false;
			}

			FlatLaf.setGlobalExtraDefaults(new LinkedHashMap<>(theme.overrides));
		}
		else {
			FlatLaf.setGlobalExtraDefaults(null);
		}

		switch (lookAndFeelTheme.source) {
			case CUSTOM_JSON:
				try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(lookAndFeelTheme.fileName))) {
					if (IntelliJTheme.setup(stream))
						return true;
					Logger.logError("Error loading theme " + lookAndFeelTheme.name);
				}
				catch (IOException e) {
					Logger.logError("Could not read file for theme: " + lookAndFeelTheme.name);
				}
				return false;
			case CUSTOM_JAR:
				try (JarFile jar = new JarFile(lookAndFeelTheme.fileName)) {
					JarEntry entry = jar.getJarEntry(lookAndFeelTheme.jarEntry);
					if (entry == null)
						throw new IOException("Could not find theme entry " + lookAndFeelTheme.jarEntry);
					if (IntelliJTheme.setup(new BufferedInputStream(jar.getInputStream(entry))))
						return true;
					Logger.logError("Error loading theme " + lookAndFeelTheme.name);
				}
				catch (IOException e) {
					Logger.logError("Error loading theme " + lookAndFeelTheme.name);
					Logger.logError(e.getMessage());
				}
				return false;
			case BUILT_IN:
				break;
			case STAR_ROD:
				return false;
		}

		try {
			UIManager.setLookAndFeel(lookAndFeelTheme.className);
			if (theme.isStarRodTheme())
				applyThemeOverrides(theme.overrides);
			return true;
		}
		catch (Exception e) {
			// many types of exceptions are possible here
			Logger.log("Could not set UI to " + lookAndFeelTheme.key, Priority.ERROR);
			Logger.logError(e.getMessage());
			return false;
		}
	}

	private static void applyThemeOverrides(Map<String, String> overrides)
	{
		UIDefaults defaults = UIManager.getLookAndFeelDefaults();
		for (Map.Entry<String, String> override : overrides.entrySet()) {
			String key = override.getKey();
			if (key.startsWith("@"))
				continue;

			if (key.startsWith("*.")) {
				String suffix = key.substring(1);
				for (Object defaultKey : new ArrayList<>(defaults.keySet())) {
					if (defaultKey instanceof String && ((String) defaultKey).endsWith(suffix))
						defaults.put(defaultKey, FlatLaf.parseDefaultsValue((String) defaultKey, override.getValue(), null));
				}
			}
			else {
				defaults.put(key, FlatLaf.parseDefaultsValue(key, override.getValue(), null));
			}
		}
	}

	public static void setThemeByKey(String themeKey)
	{
		if (themeKey == null || themeKey.isEmpty())
			themeKey = DEFAULT_THEME.key;

		if (currentTheme != null && themeKey.equalsIgnoreCase(currentTheme.key))
			return;

		Theme newTheme = THEME_MAP.get(themeKey);
		if (newTheme == null && themeKey.endsWith(STAR_ROD_THEME_SUFFIX))
			newTheme = addStarRodTheme(new File(themeKey));
		setTheme(newTheme);
	}

	public static String getConfigKey(Theme theme)
	{
		// Save As may select a location outside the automatically scanned theme folder.
		return theme.isStarRodTheme() ? new File(theme.fileName).getAbsolutePath() : theme.key;
	}

	public static Theme saveStarRodTheme(File file, String name, Theme baseTheme, Map<String, String> overrides) throws IOException
	{
		if (file == null || !file.getName().endsWith(STAR_ROD_THEME_SUFFIX))
			throw new IOException("Theme filename must end with " + STAR_ROD_THEME_SUFFIX);
		if (name == null || name.trim().isEmpty())
			throw new IOException("Theme name is required.");
		if (baseTheme == null || !baseTheme.isBuiltIn())
			throw new IOException("A built-in base theme is required.");
		if (overrides == null)
			throw new IOException("Theme overrides are required.");

		StarRodThemeData data = new StarRodThemeData();
		data.name = name.trim();
		data.baseTheme = baseTheme.key;
		data.overrides.putAll(overrides);
		Theme theme = Theme.createStarRod(file, data);
		Theme existingTheme = THEME_MAP.get(theme.key);
		if (existingTheme != null && !existingTheme.isStarRodTheme())
			throw new IOException("Theme name conflicts with an existing theme: " + theme.key);
		if (existingTheme != null && !file.getCanonicalFile().equals(new File(existingTheme.fileName).getCanonicalFile()))
			throw new IOException("Another theme already uses this filename. Choose a different filename.");

		if (file.getParentFile() != null)
			Files.createDirectories(file.getParentFile().toPath());
		try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			GSON.toJson(data, writer);
		}

		THEME_MAP.put(theme.key, theme);
		return theme;
	}

	private static Theme addStarRodTheme(File file)
	{
		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			StarRodThemeData data = GSON.fromJson(reader, StarRodThemeData.class);
			if (data == null || data.version != 1)
				throw new IOException("Unsupported or missing theme version.");
			if (data.name == null || data.name.trim().isEmpty())
				throw new IOException("Theme name is required.");
			if (data.baseTheme == null || data.baseTheme.isEmpty())
				throw new IOException("Base theme is required.");
			data.name = data.name.trim();
			data.baseTheme = data.baseTheme.trim();
			Theme baseTheme = THEME_MAP.get(data.baseTheme);
			if (baseTheme == null || !baseTheme.isBuiltIn())
				throw new IOException("Unknown base theme: " + data.baseTheme);
			if (data.overrides == null)
				data.overrides = new LinkedHashMap<>();
			Theme theme = Theme.createStarRod(file, data);
			Theme existingTheme = THEME_MAP.get(theme.key);
			if (existingTheme != null && (!existingTheme.isStarRodTheme()
				|| !file.getCanonicalFile().equals(new File(existingTheme.fileName).getCanonicalFile())))
				throw new IOException("Theme name conflicts with an existing theme: " + theme.key);
			THEME_MAP.put(theme.key, theme);
			return theme;
		}
		catch (IOException | RuntimeException e) {
			Logger.logError("Error loading " + file.getName());
			Logger.logError(e.getMessage());
		}
		return null;
	}

	private static void addCustomJarTheme(File file)
	{
		try (JarFile jar = new JarFile(file)) {
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.getName().endsWith(".theme.json")) {
					addTheme(Theme.createFromJar(jar.getName(), entry.getName()));
					return;
				}
			}
		}
		catch (IOException e) {
			Logger.logError("Error loading " + file.getName());
			Logger.logError(e.getMessage());
		}
	}

	private static void addCustomJsonTheme(File file)
	{
		addTheme(Theme.createFromJson(file));
	}

	private static void addTheme(Theme t)
	{
		if (THEME_MAP.containsKey(t.key)) {
			Logger.log("Skipping duplicate theme: " + t.key);
			return;
		}

		THEME_MAP.put(t.key, t);
	}

	static {
		if (!Environment.isCommandLine()) {
			// @formatter:off
			DEFAULT_THEME = Theme.createBuiltIn("Flat Dark",			     "com.formdev.flatlaf.FlatDarkLaf");
			addTheme(DEFAULT_THEME);

            addTheme(Theme.createBuiltIn("Flat Light",               	   "com.formdev.flatlaf.FlatLightLaf"));
			addTheme(Theme.createBuiltIn("Arc Light",                      "com.formdev.flatlaf.intellijthemes.FlatArcIJTheme"));
			addTheme(Theme.createBuiltIn("Arc Light Orange",               "com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme"));
			addTheme(Theme.createBuiltIn("Arc Dark",                       "com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme"));
			addTheme(Theme.createBuiltIn("Arc Dark Orange",                "com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme"));
			addTheme(Theme.createBuiltIn("Arc Dark (Material)",            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatArcDarkIJTheme"));
			addTheme(Theme.createBuiltIn("Atom One Dark (Material)",       "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneDarkIJTheme"));
			addTheme(Theme.createBuiltIn("Atom One Light (Material)",      "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneLightIJTheme"));
			addTheme(Theme.createBuiltIn("Carbon",                         "com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme"));
			addTheme(Theme.createBuiltIn("Cobalt 2",                       "com.formdev.flatlaf.intellijthemes.FlatCobalt2IJTheme"));
			addTheme(Theme.createBuiltIn("Cyan Light",                     "com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme"));
			addTheme(Theme.createBuiltIn("Dark Flat",                      "com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme"));
			addTheme(Theme.createBuiltIn("Dark Purple",                    "com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme"));
			addTheme(Theme.createBuiltIn("Dracula (Material)",             "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatDraculaIJTheme"));
			addTheme(Theme.createBuiltIn("Dracula",                        "com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme"));
			addTheme(Theme.createBuiltIn("GitHub (Material)",              "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatGitHubIJTheme"));
			addTheme(Theme.createBuiltIn("GitHub Dark (Material)",         "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatGitHubDarkIJTheme"));
			addTheme(Theme.createBuiltIn("Gradianto Dark Fuchsia",         "com.formdev.flatlaf.intellijthemes.FlatGradiantoDarkFuchsiaIJTheme"));
			addTheme(Theme.createBuiltIn("Gradianto Deep Ocean",           "com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme"));
			addTheme(Theme.createBuiltIn("Gradianto Midnight Blue",        "com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme"));
			addTheme(Theme.createBuiltIn("Gradianto Nature Green",         "com.formdev.flatlaf.intellijthemes.FlatGradiantoNatureGreenIJTheme"));
			addTheme(Theme.createBuiltIn("Gray",                           "com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme"));
			addTheme(Theme.createBuiltIn("Gruvbox Dark Hard",              "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme"));
			addTheme(Theme.createBuiltIn("Gruvbox Dark Medium",            "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkMediumIJTheme"));
			addTheme(Theme.createBuiltIn("Gruvbox Dark Soft",              "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkSoftIJTheme"));
			addTheme(Theme.createBuiltIn("Hiberbee Dark",                  "com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme"));
			addTheme(Theme.createBuiltIn("High Contrast",                  "com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme"));
			addTheme(Theme.createBuiltIn("Light Flat",                     "com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme"));
			addTheme(Theme.createBuiltIn("Light Owl (Material)",           "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatLightOwlIJTheme"));
			addTheme(Theme.createBuiltIn("Material Darker (Material)",     "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialDarkerIJTheme"));
			addTheme(Theme.createBuiltIn("Material Deep Ocean (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialDeepOceanIJTheme"));
			addTheme(Theme.createBuiltIn("Material Design Dark",           "com.formdev.flatlaf.intellijthemes.FlatMaterialDesignDarkIJTheme"));
			addTheme(Theme.createBuiltIn("Material Lighter (Material)",    "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialLighterIJTheme"));
			addTheme(Theme.createBuiltIn("Material Oceanic (Material)",    "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialOceanicIJTheme"));
			addTheme(Theme.createBuiltIn("Material Palenight (Material)",  "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialPalenightIJTheme"));
			addTheme(Theme.createBuiltIn("Monocai",                        "com.formdev.flatlaf.intellijthemes.FlatMonocaiIJTheme"));
			addTheme(Theme.createBuiltIn("Monokai Pro (Material)",         "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMonokaiProIJTheme"));
			addTheme(Theme.createBuiltIn("Monokai Pro",                    "com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme"));
			addTheme(Theme.createBuiltIn("Moonlight (Material)",           "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMoonlightIJTheme"));
			addTheme(Theme.createBuiltIn("Night Owl (Material)",           "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatNightOwlIJTheme"));
			addTheme(Theme.createBuiltIn("Nord",                           "com.formdev.flatlaf.intellijthemes.FlatNordIJTheme"));
			addTheme(Theme.createBuiltIn("One Dark",                       "com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme"));
			addTheme(Theme.createBuiltIn("Solarized Dark (Material)",      "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatSolarizedDarkIJTheme"));
			addTheme(Theme.createBuiltIn("Solarized Dark",                 "com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme"));
			addTheme(Theme.createBuiltIn("Solarized Light (Material)",     "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatSolarizedLightIJTheme"));
			addTheme(Theme.createBuiltIn("Solarized Light",                "com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme"));
			addTheme(Theme.createBuiltIn("Spacegray",                      "com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme"));
			addTheme(Theme.createBuiltIn("Vuesion",                        "com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme"));
			addTheme(Theme.createBuiltIn("Xcode-Dark",                     "com.formdev.flatlaf.intellijthemes.FlatXcodeDarkIJTheme"));
			// @formatter:on

			try {
				for (File f : IOUtils.getFilesWithExtension(Directories.DATABASE_THEMES.toFile(), "jar", true)) {
					addCustomJarTheme(f);
				}

				for (File f : IOUtils.getFilesWithExtension(Directories.DATABASE_THEMES.toFile(), ".theme.json", true)) {
					addCustomJsonTheme(f);
				}

				for (File f : IOUtils.getFilesWithExtension(Directories.DATABASE_THEMES.toFile(), STAR_ROD_THEME_SUFFIX, true)) {
					addStarRodTheme(f);
				}
			}
			catch (IOException e) {
				Logger.logError("IOException while loading custom themes: " + e.getMessage());
			}

			Logger.log("Loaded " + THEME_MAP.size() + " themes.");
		}
	}
}
