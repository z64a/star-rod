package app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
		ProtoDatabase       ("database/"),
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
		{
			return path;
		}
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

	public static List<String> getResourceNames(ResourceType type)
	{
		try {
			return getAllResourceNames(Resource.class, type.path);
		}
		catch (Exception e) {
			Logger.printStackTrace(e);
			return new ArrayList<String>();
		}
	}

	/**
	  * List directory contents for a resource folder. Not recursive.
	  * This is basically a brute-force implementation.
	  * Works for regular files and also JARs.
	  * Modified from a method written by Greg Briggs
	  *
	  * @param clazz Any java class that lives in the same place as the resources you want.
	  * @param path Should end with "/", but not start with one.
	  * @return Just the name of each member item, not the full paths.
	  * @throws URISyntaxException
	  * @throws IOException
	  */
	private static List<String> getAllResourceNames(Class<?> clazz, String path) throws IOException
	{
		URL dirURL = clazz.getClassLoader().getResource(path);
		if (dirURL != null && dirURL.getProtocol().equals("file")) {
			// resource exists as a file
			try {
				Path dirPath = Paths.get(dirURL.toURI());
				return Files.walk(dirPath)
					.filter(Files::isRegularFile)
					.map(filePath -> dirPath.relativize(filePath).toString().replace("\\", "/"))
					.toList();
			}
			catch (URISyntaxException e) {
				Logger.printStackTrace(e);
				return new ArrayList<String>();
			}
		}

		if (dirURL == null) {
			// In case of a jar file, we can't actually find a directory.
			// Have to assume the same jar as clazz.
			String me = clazz.getName().replace(".", "/") + ".class";
			dirURL = clazz.getClassLoader().getResource(me);
		}

		if (dirURL.getProtocol().equals("jar")) {
			// resource exists within the jar
			String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!")); // strip out only the JAR file
			try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
				return jar.stream()
					.map(JarEntry::getName)
					.filter(name -> name.startsWith(path) && !name.endsWith("/")) // only files
					.map(name -> name.substring(path.length())) // strip out path prefix
					.map(name -> name.startsWith("/") ? name.substring(1) : name) // remove leading slash if present
					.toList();
			}
		}

		throw new UnsupportedOperationException("Cannot list files for URL " + dirURL);
	}

	public static void copyMissing(ResourceType type, File dir) throws IOException
	{
		for (String filename : getAllResourceNames(Resource.class, type.path)) {
			// the destination file in the user's database directory
			File targetFile = new File(Directories.DATABASE.toFile(), filename);
			targetFile.getParentFile().mkdirs();

			try (InputStream resourceStream = Resource.getStream(type, filename)) {
				if (resourceStream != null) {
					// copy the file if it doesn't exist
					if (!targetFile.exists()) {
						try (OutputStream outStream = new FileOutputStream(targetFile)) {
							byte[] buffer = new byte[4096];
							int bytesRead;
							while ((bytesRead = resourceStream.read(buffer)) != -1) {
								outStream.write(buffer, 0, bytesRead);
							}
						}
					}
				}
			}
		}
	}
}
