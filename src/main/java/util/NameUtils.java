package util;

public abstract class NameUtils
{
	public static String toEnumStyle(String name)
	{
		return name.replaceAll("((?<=[a-z0-9])[A-Z]|(?!^)(?<!_)[A-Z](?=[a-z]))", "_$1").toUpperCase();
	}
}
