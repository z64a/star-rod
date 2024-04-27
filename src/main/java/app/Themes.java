package app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.UIManager;

import com.formdev.flatlaf.IntelliJTheme;

import app.input.IOUtils;
import util.Logger;
import util.Priority;

public abstract class Themes
{
	private static final List<Theme> THEMES = new ArrayList<>();
	private static Theme SYSTEM_THEME;
	private static Theme currentTheme = null;

	public static class Theme
	{
		public final String key;
		public final String name;
		public final String className;
		public final boolean custom;

		public Theme(String name, String className)
		{
			this(name, className, false);
		}

		public Theme(String name, String className, boolean custom)
		{
			this.name = name;
			this.key = name.replaceAll("\\s+", "");
			this.className = className;
			this.custom = custom;
		}
	}

	public static String getCurrentThemeKey()
	{
		return currentTheme.key;
	}

	public static String getCurrentThemeName()
	{
		return currentTheme.name;
	}

	public static List<String> getThemeNames()
	{
		List<String> list = new ArrayList<>(THEMES.size());
		for (Theme theme : THEMES)
			list.add(theme.name);
		return list;
	}

	private static void setTheme(Theme theme)
	{
		if (theme == null)
			theme = SYSTEM_THEME;
		else if (theme == currentTheme)
			return;

		if (theme.custom) {
			try {
				IntelliJTheme.setup(new FileInputStream(new File(theme.className)));
				currentTheme = theme;
				return;
			}
			catch (FileNotFoundException e) {
				Logger.logError("Could not find file for theme: " + theme.name);
			}
			// if error, reset to system
			theme = SYSTEM_THEME;
		}

		try {
			UIManager.setLookAndFeel(theme.className);
			currentTheme = theme;
		}
		catch (Exception e) {
			// many types of exceptions are possible here
			Logger.log("Could not set UI to " + theme.key, Priority.ERROR);
		}
	}

	public static void setThemeByKey(String themeKey)
	{
		if (themeKey == null || themeKey.isEmpty())
			themeKey = SYSTEM_THEME.key;

		if (currentTheme != null && themeKey.equalsIgnoreCase(currentTheme.key))
			return;

		Theme newTheme = null;
		for (Theme theme : THEMES) {
			if (theme.key.equalsIgnoreCase(themeKey)) {
				newTheme = theme;
				break;
			}
		}

		setTheme(newTheme);
	}

	public static void setThemeByName(String themeName)
	{
		if (themeName == null || themeName.isEmpty())
			themeName = SYSTEM_THEME.name;

		if (currentTheme != null && themeName.equalsIgnoreCase(currentTheme.name))
			return;

		Theme newTheme = null;
		for (Theme theme : THEMES) {
			if (theme.name.equalsIgnoreCase(themeName)) {
				newTheme = theme;
				break;
			}
		}

		setTheme(newTheme);
	}

	static {
		if (!Environment.isCommandLine()) {
			SYSTEM_THEME = new Theme("System", UIManager.getSystemLookAndFeelClassName());
			THEMES.add(SYSTEM_THEME);

			try {
				for (File f : IOUtils.getFilesWithExtension(Directories.DATABASE_THEMES.toFile(), "theme.json", true)) {
					String name = f.getName().substring(0, f.getName().length() - 11);
					THEMES.add(new Theme(name, f.getAbsolutePath(), true));
				}
			}
			catch (IOException e) {
				Logger.logError("IOException while loading custom themes: " + e.getMessage());
			}

			// @formatter:off
            THEMES.add(new Theme("Flat Light",              	   "com.formdev.flatlaf.FlatLightLaf"));
            THEMES.add(new Theme("Flat Dark",               	   "com.formdev.flatlaf.FlatDarkLaf"));
			THEMES.add(new Theme("Arc Light",                      "com.formdev.flatlaf.intellijthemes.FlatArcIJTheme"));
			THEMES.add(new Theme("Arc Light Orange",               "com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme"));
			THEMES.add(new Theme("Arc Dark",                       "com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme"));
			THEMES.add(new Theme("Arc Dark Orange",                "com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme"));
			THEMES.add(new Theme("Arc Dark (Material)",            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatArcDarkIJTheme"));
			THEMES.add(new Theme("Atom One Dark (Material)",       "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneDarkIJTheme"));
			THEMES.add(new Theme("Atom One Light (Material)",      "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneLightIJTheme"));
			THEMES.add(new Theme("Carbon",                         "com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme"));
			THEMES.add(new Theme("Cobalt 2",                       "com.formdev.flatlaf.intellijthemes.FlatCobalt2IJTheme"));
			THEMES.add(new Theme("Cyan Light",                     "com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme"));
			THEMES.add(new Theme("Dark Flat",                      "com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme"));
			THEMES.add(new Theme("Dark Purple",                    "com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme"));
			THEMES.add(new Theme("Dracula (Material)",             "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatDraculaIJTheme"));
			THEMES.add(new Theme("Dracula",                        "com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme"));
			THEMES.add(new Theme("GitHub (Material)",              "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatGitHubIJTheme"));
			THEMES.add(new Theme("GitHub Dark (Material)",         "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatGitHubDarkIJTheme"));
			THEMES.add(new Theme("Gradianto Dark Fuchsia",         "com.formdev.flatlaf.intellijthemes.FlatGradiantoDarkFuchsiaIJTheme"));
			THEMES.add(new Theme("Gradianto Deep Ocean",           "com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme"));
			THEMES.add(new Theme("Gradianto Midnight Blue",        "com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme"));
			THEMES.add(new Theme("Gradianto Nature Green",         "com.formdev.flatlaf.intellijthemes.FlatGradiantoNatureGreenIJTheme"));
			THEMES.add(new Theme("Gray",                           "com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme"));
			THEMES.add(new Theme("Gruvbox Dark Hard",              "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme"));
			THEMES.add(new Theme("Gruvbox Dark Medium",            "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkMediumIJTheme"));
			THEMES.add(new Theme("Gruvbox Dark Soft",              "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkSoftIJTheme"));
			THEMES.add(new Theme("Hiberbee Dark",                  "com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme"));
			THEMES.add(new Theme("High Contrast",                  "com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme"));
			THEMES.add(new Theme("Light Flat",                     "com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme"));
			THEMES.add(new Theme("Light Owl (Material)",           "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatLightOwlIJTheme"));
			THEMES.add(new Theme("Material Darker (Material)",     "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialDarkerIJTheme"));
			THEMES.add(new Theme("Material Deep Ocean (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialDeepOceanIJTheme"));
			THEMES.add(new Theme("Material Design Dark",           "com.formdev.flatlaf.intellijthemes.FlatMaterialDesignDarkIJTheme"));
			THEMES.add(new Theme("Material Lighter (Material)",    "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialLighterIJTheme"));
			THEMES.add(new Theme("Material Oceanic (Material)",    "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialOceanicIJTheme"));
			THEMES.add(new Theme("Material Palenight (Material)",  "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialPalenightIJTheme"));
			THEMES.add(new Theme("Monocai",                        "com.formdev.flatlaf.intellijthemes.FlatMonocaiIJTheme"));
			THEMES.add(new Theme("Monokai Pro (Material)",         "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMonokaiProIJTheme"));
			THEMES.add(new Theme("Monokai Pro",                    "com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme"));
			THEMES.add(new Theme("Moonlight (Material)",           "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMoonlightIJTheme"));
			THEMES.add(new Theme("Night Owl (Material)",           "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatNightOwlIJTheme"));
			THEMES.add(new Theme("Nord",                           "com.formdev.flatlaf.intellijthemes.FlatNordIJTheme"));
			THEMES.add(new Theme("One Dark",                       "com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme"));
			THEMES.add(new Theme("Solarized Dark (Material)",      "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatSolarizedDarkIJTheme"));
			THEMES.add(new Theme("Solarized Dark",                 "com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme"));
			THEMES.add(new Theme("Solarized Light (Material)",     "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatSolarizedLightIJTheme"));
			THEMES.add(new Theme("Solarized Light",                "com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme"));
			THEMES.add(new Theme("Spacegray",                      "com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme"));
			THEMES.add(new Theme("Vuesion",                        "com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme"));
			THEMES.add(new Theme("Xcode-Dark",                     "com.formdev.flatlaf.intellijthemes.FlatXcodeDarkIJTheme"));
			// @formatter:on

			Logger.log("Loaded " + THEMES.size() + " themes.");
		}
	}
}
