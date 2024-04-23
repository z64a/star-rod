package app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import app.input.IOUtils;
import app.input.Line;
import app.input.StreamSource;
import util.Logger;
import util.Priority;

public abstract class Resource
{
	public static enum ResourceType
	{
		// @formatter:off
		EditorAsset			("editor/"),
		Extract             ("extract/"),
		EntityModelRoots	("entity/"),
		Font				("font/"),
		Icon                ("icon/"),
		Deluxe              ("deluxe/"),
		Splash              ("splash/"),
		Shader              ("shader/");
		// @formatter:on

		private final String path;

		private ResourceType(String path)
		{
			this.path = path;
		}

		private String getPath()
		{ return path; }
	}

	private static InputStream resolveResourceToStream(ResourceType type, String resourceName)
	{
		ClassLoader classLoader = Resource.class.getClassLoader();
		return classLoader.getResourceAsStream(type.getPath() + resourceName);
	}

	public static boolean hasResource(ResourceType type, String resourceName)
	{
		return resolveResourceToStream(type, resourceName) != null;
	}

	public static InputStream getStream(ResourceType type, String resourceName)
	{
		InputStream is = resolveResourceToStream(type, resourceName);

		if (is == null) {
			Logger.log("Unable to find resource " + resourceName, Priority.ERROR);
			throw new RuntimeException("Unable to find resource " + resourceName);
		}

		return is;
	}

	public static ArrayList<Line> getLines(ResourceType type, String resourceName)
	{
		try {
			return IOUtils.readPlainInputStream(new StreamSource(resourceName), getStream(type, resourceName));
		}
		catch (IOException e) {
			throw new StarRodException("Could not read resource: %s %n%s", resourceName, e.getMessage());
		}
	}

	public static ArrayList<String> getText(ResourceType type, String resourceName)
	{
		InputStream is = resolveResourceToStream(type, resourceName);

		if (is == null) {
			Logger.log("Unable to find resource " + resourceName, Priority.ERROR);
			throw new RuntimeException("Unable to find resource " + resourceName);
		}

		try {
			return IOUtils.readPlainTextStream(is);
		}
		catch (IOException e) {
			throw new StarRodException("Could not read resource: %s %n%s", resourceName, e.getMessage());
		}
	}

	public static ArrayList<String> getTextInput(ResourceType type, String resourceName)
	{
		return getTextInput(type, resourceName, true);
	}

	public static ArrayList<String> getTextInput(ResourceType type, String resourceName, boolean keepEmptyLines)
	{
		InputStream is = resolveResourceToStream(type, resourceName);

		if (is == null) {
			Logger.log("Unable to find resource " + resourceName, Priority.ERROR);
			throw new RuntimeException("Unable to find resource " + resourceName);
		}

		try {
			return IOUtils.readFormattedTextStream(is, keepEmptyLines);
		}
		catch (IOException e) {
			throw new StarRodException("Could not read resource: %s %n%s", resourceName, e.getMessage());
		}
	}

	public static String[] getResourceNames(ResourceType type)
	{
		try {
			return getResourceListing(Resource.class, type.path);
		}
		catch (Exception e) {
			Logger.printStackTrace(e);
			return new String[] {};
		}
	}

	/**
	  * List directory contents for a resource folder. Not recursive.
	  * This is basically a brute-force implementation.
	  * Works for regular files and also JARs.
	  *
	  * @author Greg Briggs
	  * @param clazz Any java class that lives in the same place as the resources you want.
	  * @param path Should end with "/", but not start with one.
	  * @return Just the name of each member item, not the full paths.
	  * @throws URISyntaxException
	  * @throws IOException
	  */
	private static String[] getResourceListing(Class<?> clazz, String path) throws URISyntaxException, IOException
	{
		URL dirURL = clazz.getClassLoader().getResource(path);
		if (dirURL != null && dirURL.getProtocol().equals("file")) {
			// A file path: easy enough
			return new File(dirURL.toURI()).list();
		}

		if (dirURL == null) {
			// In case of a jar file, we can't actually find a directory.
			// Have to assume the same jar as clazz.
			String me = clazz.getName().replace(".", "/") + ".class";
			dirURL = clazz.getClassLoader().getResource(me);
		}

		if (dirURL.getProtocol().equals("jar")) {
			// A JAR path
			String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!")); //strip out only the JAR file
			try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
				Enumeration<JarEntry> entries = jar.entries(); //gives ALL entries in jar
				Set<String> result = new HashSet<String>(); //avoid duplicates in case it is a subdirectory
				while (entries.hasMoreElements()) {
					String name = entries.nextElement().getName();
					if (name.startsWith(path)) { //filter according to the path
						String entry = name.substring(path.length());
						int checkSubdir = entry.indexOf("/");
						if (checkSubdir >= 0) {
							// if it is a subdirectory, we just return the directory name
							entry = entry.substring(0, checkSubdir);
						}
						result.add(entry);
					}
				}
				return result.toArray(new String[result.size()]);
			}
		}

		throw new UnsupportedOperationException("Cannot list files for URL " + dirURL);
	}
}
