package app;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.swing.UIManager;

import org.apache.commons.text.WordUtils;

import com.formdev.flatlaf.IntelliJTheme;

import app.input.IOUtils;
import util.CaseInsensitiveMap;
import util.Logger;
import util.Priority;

public abstract class Themes
{
	private static final CaseInsensitiveMap<Theme> THEME_MAP = new CaseInsensitiveMap<>();

	private static Theme DEFAULT_THEME;
	private static Theme currentTheme = null;

	enum ThemeSource
	{
		BUILT_IN,
		CUSTOM_JAR,
		CUSTOM_JSON,
	};

	public static class Theme
	{
		public final String key;
		public final String name;

		public final ThemeSource source;
		public final String className;
		public final String fileName;
		public final String jarEntry;

		private Theme(ThemeSource source, String key, String name, String className, String fileName, String jarEntry)
		{
			this.key = key;
			this.name = name;
			this.source = source;
			this.className = className;
			this.fileName = fileName;
			this.jarEntry = jarEntry;
		}

		public static Theme createBuiltIn(String name, String className)
		{
			String key = name.replaceAll("\\s+", "");
			return new Theme(ThemeSource.BUILT_IN, key, name, className, "", "");
		}

		public static Theme createFromJar(String fileName, String jarEntry)
		{
			String base = jarEntry.substring(0, jarEntry.length() - ".theme.json".length());
			String key = base.replaceAll("\\s+", "_");
			String name = WordUtils.capitalize(key.replaceAll("_", " "));

			return new Theme(ThemeSource.CUSTOM_JAR, key, name, "", fileName, jarEntry);
		}

		public static Theme createFromJson(String fileName)
		{
			String base = fileName.substring(0, fileName.length() - ".theme.json".length());
			String key = base.replaceAll("\\s+", "_");
			String name = WordUtils.capitalize(key.replaceAll("_", " "));

			return new Theme(ThemeSource.CUSTOM_JSON, key, name, "", fileName, "");
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

	public static void setTheme(Theme theme)
	{
		if (theme == null)
			theme = DEFAULT_THEME;

		if (theme == currentTheme)
			return;

		switch (theme.source) {
			case CUSTOM_JSON:
				try {
					IntelliJTheme.setup(new BufferedInputStream(new FileInputStream(new File(theme.className))));
					currentTheme = theme;
					return;
				}
				catch (FileNotFoundException e) {
					Logger.logError("Could not find file for theme: " + theme.name);

					// reset to default
					if (currentTheme == null)
						theme = DEFAULT_THEME;
				}
				break;
			case CUSTOM_JAR:
				try (JarFile jar = new JarFile(theme.fileName)) {
					JarEntry entry = jar.getJarEntry(theme.jarEntry);
					IntelliJTheme.setup(new BufferedInputStream(jar.getInputStream(entry)));
					currentTheme = theme;
					return;
				}
				catch (IOException e) {
					Logger.logError("Error loading theme " + theme.name);
					Logger.logError(e.getMessage());

					// reset to default
					if (currentTheme == null)
						theme = DEFAULT_THEME;
				}
				break;
			case BUILT_IN:
				// handled after the switch
				break;
		}

		try {
			UIManager.setLookAndFeel(theme.className);
			currentTheme = theme;
		}
		catch (Exception e) {
			// many types of exceptions are possible here
			Logger.log("Could not set UI to " + theme.key, Priority.ERROR);
			Logger.logError(e.getMessage());
		}
	}

	public static void setThemeByKey(String themeKey)
	{
		if (themeKey == null || themeKey.isEmpty())
			themeKey = DEFAULT_THEME.key;

		if (currentTheme != null && themeKey.equalsIgnoreCase(currentTheme.key))
			return;

		Theme newTheme = THEME_MAP.get(themeKey);
		setTheme(newTheme);
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
		addTheme(Theme.createFromJson(file.getName()));
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
			DEFAULT_THEME = Theme.createBuiltIn("Flat Light",			     "com.formdev.flatlaf.FlatLightLaf");
			addTheme(DEFAULT_THEME);

            addTheme(Theme.createBuiltIn("Flat Dark",               	   "com.formdev.flatlaf.FlatDarkLaf"));
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
			}
			catch (IOException e) {
				Logger.logError("IOException while loading custom themes: " + e.getMessage());
			}

			Logger.log("Loaded " + THEME_MAP.size() + " themes.");
		}
	}
}
