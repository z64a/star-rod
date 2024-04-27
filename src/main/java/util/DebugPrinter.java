package util;

// Use this to print temporary debug messages. This way, we won't end up
// with calls to System.out.println hiding throughout the codebase.
public abstract class DebugPrinter
{
	private static boolean enabled = true;

	public static void println(String s)
	{
		if (enabled)
			System.out.println("<> " + s);
	}

	public static void println(Object obj)
	{
		if (enabled)
			println(obj.toString());
	}

	public static void println(String fmt, Object ... args)
	{
		if (enabled)
			println(String.format(fmt, args));
	}

	public static void setEnabled(boolean value)
	{
		enabled = value;
	}
}
