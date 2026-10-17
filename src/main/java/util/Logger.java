package util;

import static util.Priority.STANDARD;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;

public abstract class Logger
{
	private static final List<ListenerReference> listeners = new CopyOnWriteArrayList<>();

	private static volatile Listener progressListener;

	private static Priority defaultPriority = STANDARD;
	private static boolean enabled = true;

	public static final class Message
	{
		public final String text;
		public final Priority priority;

		private Message(String text, Priority importance)
		{
			this.text = text;
			this.priority = importance;
		}
	}

	public static interface Listener
	{
		void post(Message msg);
	}

	private static final class ListenerReference
	{
		private final Listener listener;
		private final Priority priority;
		private final boolean useEDT;

		private ListenerReference(Listener listener, Priority p, boolean useEDT)
		{
			this.listener = listener;
			this.priority = p;
			this.useEDT = useEDT;
		}
	}

	public static void addListener(Listener listener)
	{
		addListener(listener, defaultPriority);
	}

	/** Registers a listener called synchronously, such as a log file that must be written before it closes. */
	public static void addListener(Listener listener, Priority p)
	{
		listeners.add(new ListenerReference(listener, p, false));
	}

	public static void addSwingListener(Listener listener)
	{
		addSwingListener(listener, defaultPriority);
	}

	/** Registers a UI listener without making a logging thread wait for the event dispatch thread. */
	public static void addSwingListener(Listener listener, Priority p)
	{
		listeners.add(new ListenerReference(listener, p, true));
	}

	public static void removeListener(Listener listener)
	{
		listeners.removeIf(ref -> ref.listener == listener);
	}

	/** The progress listener receives UPDATE and MILESTONE messages on the event dispatch thread. */
	public static void setProgressListener(Listener listener)
	{
		progressListener = listener;
	}

	public static void removeProgressListener()
	{
		progressListener = null;
	}

	private static void broadcast(String text, Priority p)
	{
		if (!enabled)
			return;

		Message msg = new Message(text, p);

		switch (p) {
			case UPDATE:
				// update messages are only intended for the progress listener
				postOnEDT(progressListener, msg);
				return;
			case MILESTONE:
				// progress listener also receives milestone messages
				postOnEDT(progressListener, msg);
				break;
			case WARNING:
				text = "WARNING: " + text;
				break;
			case ERROR:
				text = "ERROR: " + text;
				break;
			default:
		}

		if (!p.lessThan(defaultPriority)) {
			if (text == null || text.isEmpty())
				System.out.println(">");
			else
				System.out.println("> " + text);
		}

		for (ListenerReference ref : listeners) {
			if (!p.lessThan(ref.priority)) {
				if (ref.useEDT)
					postOnEDT(ref.listener, msg);
				else
					ref.listener.post(msg);
			}
		}
	}

	private static void postOnEDT(Listener listener, Message msg)
	{
		if (listener == null)
			return;
		SwingUtilities.invokeLater(() -> listener.post(msg));
	}

	public static void logError(String message)
	{
		broadcast(message, Priority.ERROR);
	}

	public static void logfError(String format, Object ... args)
	{
		broadcast(String.format(format, args), Priority.ERROR);
	}

	public static void logWarning(String message)
	{
		broadcast(message, Priority.WARNING);
	}

	public static void logfWarning(String format, Object ... args)
	{
		broadcast(String.format(format, args), Priority.WARNING);
	}

	public static void log(String message)
	{
		broadcast(message, Priority.STANDARD);
	}

	public static void logf(String format, Object ... args)
	{
		broadcast(String.format(format, args), Priority.STANDARD);
	}

	public static void logDetail(String message)
	{
		broadcast(message, Priority.DETAIL);
	}

	public static void logfDetail(String format, Object ... args)
	{
		broadcast(String.format(format, args), Priority.DETAIL);
	}

	public static void log(String message, Priority p)
	{
		broadcast(message, p);
	}

	public static void setDefaultOuputPriority(Priority p)
	{
		defaultPriority = p;
	}

	public static void disable()
	{
		enabled = false;
	}

	public static void enable()
	{
		enabled = true;
	}

	public static void printStackTrace(Throwable t)
	{
		StackTraceElement[] stackTrace = t.getStackTrace();

		String title = t.getClass().getSimpleName();
		if (title.isEmpty())
			title = "Anonymous Exception";

		if (t instanceof AssertionError)
			title = "Assertion Failed";

		broadcast(title, Priority.ERROR);
		broadcast(t.getMessage(), Priority.IMPORTANT);
		for (StackTraceElement ele : stackTrace)
			broadcast("  at " + ele, Priority.IMPORTANT);
	}
}
