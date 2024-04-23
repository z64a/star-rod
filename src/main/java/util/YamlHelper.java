package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public abstract class YamlHelper
{
	public static class YamlException extends RuntimeException
	{
		public YamlException(String msg)
		{
			super(msg);
		}
	}

	public static Map<String, Object> readAsMap(File yamlFile)
	{
		Map<String, Object> list;

		try {
			list = new Yaml().load(new FileInputStream(yamlFile));
		}
		catch (FileNotFoundException e) {
			Logger.logError(e.getMessage());
			return null;
		}

		return list;
	}

	public static ArrayList<Object> readAsList(File yamlFile)
	{
		ArrayList<Object> list;

		try {
			list = new Yaml().load(new FileInputStream(yamlFile));
		}
		catch (FileNotFoundException e) {
			Logger.logError(e.getMessage());
			return null;
		}

		return list;
	}

	public static Object getObject(Object map, String key)
	{
		if (!(map instanceof Map))
			throw new YamlException("Expected Map, found " + map.getClass().getName());

		@SuppressWarnings("unchecked")
		Object obj = ((Map<String, Object>) map).get(key);

		if (obj == null)
			throw new YamlException("Missing expected attribute: " + key);

		return obj;
	}

	public static boolean getInt(Object map, String key)
	{
		if (!(map instanceof Map))
			throw new YamlException("Expected Map, found " + map.getClass().getName());

		@SuppressWarnings("unchecked")
		Object obj = ((Map<String, Object>) map).get(key);

		if (obj == null)
			throw new YamlException("Missing expected attribute: " + key);

		if (obj instanceof Boolean)
			return (Boolean) obj;
		else
			throw new YamlException("Expected boolean for " + key + ": " + obj);
	}

	public static boolean getBoolean(Object map, String key, boolean defaultValue)
	{
		if (!(map instanceof Map))
			throw new YamlException("Expected Map, found " + map.getClass().getName());

		@SuppressWarnings("unchecked")
		Object obj = ((Map<String, Object>) map).get(key);

		if (obj == null)
			return defaultValue;

		if (obj instanceof Boolean)
			return (Boolean) obj;
		else
			throw new YamlException("Expected boolean for " + key + ": " + obj);
	}

	public static int getBoolean(Object map, String key)
	{
		if (!(map instanceof Map))
			throw new YamlException("Expected Map, found " + map.getClass().getName());

		@SuppressWarnings("unchecked")
		Object obj = ((Map<String, Object>) map).get(key);

		if (obj == null)
			throw new YamlException("Missing expected attribute: " + key);

		if (obj instanceof Integer)
			return (Integer) obj;
		else
			throw new YamlException("Expected integer for " + key + ": " + obj);
	}

	public static int getInt(Object map, String key, int defaultValue)
	{
		if (!(map instanceof Map))
			throw new YamlException("Expected Map, found " + map.getClass().getName());

		@SuppressWarnings("unchecked")
		Object obj = ((Map<String, Object>) map).get(key);

		if (obj == null)
			return defaultValue;

		if (obj instanceof Integer)
			return (Integer) obj;
		else
			throw new YamlException("Expected integer for " + key + ": " + obj);
	}

	public static String getString(Object map, String key)
	{
		if (!(map instanceof Map))
			throw new YamlException("Expected Map, found " + map.getClass().getName());

		@SuppressWarnings("unchecked")
		Object obj = ((Map<String, Object>) map).get(key);

		if (obj == null)
			throw new YamlException("Missing expected attribute: " + key);

		if (obj instanceof String)
			return (String) obj;
		else
			throw new YamlException("Expected string for " + key + ": " + obj);
	}

	public static String getString(Object map, String key, String defaultValue)
	{
		if (!(map instanceof Map))
			throw new YamlException("Expected Map, found " + map.getClass().getName());

		@SuppressWarnings("unchecked")
		Object obj = ((Map<String, Object>) map).get(key);

		if (obj == null)
			return defaultValue;

		if (obj instanceof String)
			return (String) obj;
		else
			throw new YamlException("Expected string for " + key + ": " + obj);
	}

	@SuppressWarnings("unchecked")
	public static List<String> getList(Object map, String key)
	{
		if (!(map instanceof Map))
			throw new YamlException("Expected Map, found " + map.getClass().getName());

		Object obj = ((Map<String, Object>) map).get(key);

		if (obj == null)
			throw new YamlException("Missing expected attribute: " + key);

		if (obj instanceof List)
			return (List<String>) obj;
		else
			throw new YamlException("Expected list for " + key + ": " + obj);
	}
}
