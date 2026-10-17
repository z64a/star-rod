package app.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import app.SwingUtils;
import app.config.Options.Scope;
import app.config.Options.Type;
import app.input.IOUtils;
import app.input.InputFileException;
import util.Logger;

public class Config
{
	private final LinkedHashMap<String, String> settings = new LinkedHashMap<>();
	private final File file;

	private Scope[] allowedScopes;

	public Config(File cfg, Scope ... allowedScopes)
	{
		file = cfg;
		this.allowedScopes = allowedScopes;
	}

	public File getFile()
	{
		return file;
	}

	public void readConfig() throws IOException
	{
		readConfig(file);
	}

	public void readConfig(File source) throws IOException
	{
		List<String> lines = IOUtils.readFormattedTextFile(source, false);
		Config loadedConfig = new Config(source, allowedScopes);

		for (String line : lines) {
			int assignmentPos = line.indexOf('=');
			if (assignmentPos < 0)
				throw new InputFileException(source, "Missing assignment on line: %n%s", line);

			String key = line.substring(0, assignmentPos).trim();
			String value = line.substring(assignmentPos + 1).trim();

			Options opt = Options.getOption(key);
			if (opt == null) {
				Logger.logWarning("Unknown config entry: " + key);
				continue;
			}

			if (value.isEmpty()) {
				loadedConfig.settings.put(opt.key, opt.defaultValue);
				continue;
			}

			try {
				switch (opt.type) {
					case Boolean:
						loadedConfig.setBoolean(opt, value);
						break;
					case Integer:
						loadedConfig.setInteger(opt, value);
						break;
					case Hex:
						loadedConfig.setHex(opt, value);
						break;
					case Float:
						loadedConfig.setFloat(opt, value);
						break;
					case String:
						loadedConfig.setString(opt, value);
						break;
				}
			}
			catch (ConfigEntryException e) {
				Logger.logWarning(e.getMessage());
				loadedConfig.settings.put(opt.key, opt.defaultValue);
			}
		}

		for (Options opt : Options.values()) {
			if (loadedConfig.allowed(opt) && !loadedConfig.settings.containsKey(opt.key) && opt.required)
				loadedConfig.settings.put(opt.key, opt.defaultValue);
		}

		settings.clear();
		settings.putAll(loadedConfig.settings);
	}

	public void saveConfigFile()
	{
		try {
			List<String> lines = new ArrayList<>(settings.size() + 1);
			lines.add("% Auto-generated config file, modify with care.");

			for (Entry<String, String> entry : settings.entrySet())
				lines.add(String.format("%s = %s", entry.getKey(), entry.getValue()));

			IOUtils.atomicWriteLines(lines, file);
		}
		catch (IOException e) {
			SwingUtils.getErrorDialog()
				.setTitle("Config Write Exception")
				.setMessage("Could not update config:", file.getAbsolutePath())
				.show();
			System.exit(-1);
		}

		Logger.log("Saved config: " + file.getName());
	}

	private boolean allowed(Options opt)
	{
		for (Scope s : allowedScopes) {
			if (opt.scope == s)
				return true;
		}
		return false;
	}

	public void setString(Options opt, String value)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to set option " + opt.key);

		if (opt.type != Options.Type.String)
			throw new ConfigEntryException(opt, "Cannot set option as string: " + opt.key);

		if (value == null)
			Logger.logWarning("Set " + opt.key + " to null");

		settings.put(opt.key, value);
	}

	public String getString(Options opt)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to get option " + opt.key);

		if (opt.type != Type.String)
			throw new ConfigEntryException(opt, "Cannot get string value for option: " + opt.key);

		String s = settings.get(opt.key);
		return (s == null || s.equals("null")) ? opt.defaultValue : s;
	}

	public void setBoolean(Options opt, boolean value)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to set option " + opt.key);

		if (opt.type != Options.Type.Boolean)
			throw new ConfigEntryException(opt, "Cannot set option as boolean: " + opt.key);

		if (value)
			settings.put(opt.key, "true");
		else
			settings.put(opt.key, "false");
	}

	public void setBoolean(Options opt, String value)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to set option " + opt.key);

		if (opt.type != Options.Type.Boolean)
			throw new ConfigEntryException(opt, "Cannot set option as boolean: " + opt.key);

		if (value.equalsIgnoreCase("true"))
			settings.put(opt.key, "true");
		else if (value.equalsIgnoreCase("false"))
			settings.put(opt.key, "false");
		else
			throw new ConfigEntryException(opt, opt.key + " requires a boolean value (true|false).");
	}

	public boolean getBoolean(Options opt)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to get option " + opt.key);

		if (opt.type != Type.Boolean)
			throw new ConfigEntryException(opt, "Cannot get boolean value for option: " + opt.key);

		String s = settings.get(opt.key);
		if (s == null)
			s = opt.defaultValue;

		return s.equalsIgnoreCase("true");
	}

	public void setInteger(Options opt, int value)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to set option " + opt.key);

		if (opt.type != Options.Type.Integer)
			throw new ConfigEntryException(opt, "Cannot set option as integer: " + opt.key);

		int min = (int) Math.round(opt.min);
		if (opt.min <= Integer.MIN_VALUE)
			min = Integer.MIN_VALUE;

		int max = (int) Math.round(opt.max);
		if (opt.max >= Integer.MAX_VALUE)
			max = Integer.MAX_VALUE;

		if (value < min)
			value = min;
		if (value > max)
			value = max;

		settings.put(opt.key, String.valueOf(value));
	}

	public void setInteger(Options opt, String svalue)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to set option " + opt.key);

		if (opt.type != Options.Type.Integer)
			throw new ConfigEntryException(opt, "Cannot set option as integer: " + opt.key);

		int value;
		try {
			value = Integer.parseInt(svalue);
		}
		catch (NumberFormatException e) {
			throw new ConfigEntryException(opt, opt.key + " requires an integer value.");
		}

		int min = (int) Math.round(opt.min);
		if (opt.min <= Integer.MIN_VALUE)
			min = Integer.MIN_VALUE;

		int max = (int) Math.round(opt.max);
		if (opt.max >= Integer.MAX_VALUE)
			max = Integer.MAX_VALUE;

		if (value < min)
			value = min;
		if (value > max)
			value = max;

		settings.put(opt.key, Integer.toString(value));
	}

	public int getInteger(Options opt)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to get option " + opt.key);

		if (opt.type != Type.Integer)
			throw new ConfigEntryException(opt, "Cannot get integer value for option: " + opt.key);

		String s = settings.get(opt.key);
		if (s == null)
			s = opt.defaultValue;

		return Integer.parseInt(s);
	}

	public void setHex(Options opt, int value)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to set option " + opt.key);

		if (opt.type != Options.Type.Hex)
			throw new ConfigEntryException(opt, "Cannot set option as hex integer: " + opt.key);

		int min = (int) Math.round(opt.min);
		if (opt.min <= Integer.MIN_VALUE)
			min = Integer.MIN_VALUE;

		int max = (int) Math.round(opt.max);
		if (opt.max >= Integer.MAX_VALUE)
			max = Integer.MAX_VALUE;

		if (value < min)
			value = min;
		if (value > max)
			value = max;

		settings.put(opt.key, "0x" + Integer.toString(value, 16));
	}

	public void setHex(Options opt, String svalue)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to set option " + opt.key);

		if (opt.type != Options.Type.Hex)
			throw new ConfigEntryException(opt, "Cannot set option as hex integer: " + opt.key);

		if (svalue.startsWith("0x"))
			svalue = svalue.substring(2);

		int value;
		try {
			value = (int) Long.parseLong(svalue, 16);
		}
		catch (NumberFormatException e) {
			throw new ConfigEntryException(opt, opt.key + " requires a hex integer value.");
		}

		int min = (int) Math.round(opt.min);
		if (opt.min <= Integer.MIN_VALUE)
			min = Integer.MIN_VALUE;

		int max = (int) Math.round(opt.max);
		if (opt.max >= Integer.MAX_VALUE)
			max = Integer.MAX_VALUE;

		if (value < min)
			value = min;
		if (value > max)
			value = max;

		settings.put(opt.key, "0x" + Integer.toString(value, 16));
	}

	public int getHex(Options opt)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to get option " + opt.key);

		if (opt.type != Type.Hex)
			throw new ConfigEntryException(opt, "Cannot get hex integer value for option: " + opt.key);

		String s = settings.get(opt.key);
		if (s == null)
			s = opt.defaultValue;

		if (s.startsWith("0x"))
			s = s.substring(2);

		return (int) Long.parseLong(s, 16);
	}

	public void setFloat(Options opt, float value)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to set option " + opt.key);

		if (opt.type != Options.Type.Float)
			throw new ConfigEntryException(opt, "Cannot set option as float: " + opt.key);

		float min = (float) opt.min;
		if (opt.min <= Float.MIN_VALUE)
			min = Float.MIN_VALUE;

		float max = (float) opt.max;
		if (opt.max >= Float.MAX_VALUE)
			max = Float.MAX_VALUE;

		if (value < min)
			value = min;
		if (value > max)
			value = max;

		settings.put(opt.key, String.valueOf(value));
	}

	public void setFloat(Options opt, String svalue)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to set option " + opt.key);

		if (opt.type != Options.Type.Float)
			throw new ConfigEntryException(opt, "Cannot set option as float: " + opt.key);

		float value;
		try {
			value = Float.parseFloat(svalue);
		}
		catch (NumberFormatException e) {
			throw new ConfigEntryException(opt, opt.key + " requires a float value.");
		}

		float min = (float) opt.min;
		if (opt.min <= Float.MIN_VALUE)
			min = Float.MIN_VALUE;

		float max = (float) opt.max;
		if (opt.max >= Float.MAX_VALUE)
			max = Float.MAX_VALUE;

		if (value < min)
			value = min;
		if (value > max)
			value = max;

		settings.put(opt.key, Float.toString(value));
	}

	public float getFloat(Options opt)
	{
		if (!allowed(opt))
			throw new ConfigEntryException(opt, file.getName() + " does not have permission to get option " + opt.key);

		if (opt.type != Type.Float)
			throw new ConfigEntryException(opt, "Cannot get float value for option: " + opt.key);

		String s = settings.get(opt.key);
		if (s == null)
			s = opt.defaultValue;

		return Float.parseFloat(s);
	}
}
