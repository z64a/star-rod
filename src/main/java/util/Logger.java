package util;

import static util.Priority.MILESTONE;
import static util.Priority.STANDARD;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class Logger
{
	private static List<ListenerReference> listeners = new LinkedList<>();
	private static Priority minListenerPriority = STANDARD;

	private static Listener progressListener;

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

		private ListenerReference(Listener listener, Priority p)
		{
			this.listener = listener;
			this.priority = p;
		}
	}

	public static void addListener(Listener listener)
	{
		listeners.add(new ListenerReference(listener, defaultPriority));

		if (minListenerPriority.greaterThan(defaultPriority))
			minListenerPriority = defaultPriority;
	}

	public static void addListener(Listener listener, Priority p)
	{
		listeners.add(new ListenerReference(listener, p));

		if (minListenerPriority.greaterThan(p))
			minListenerPriority = p;
	}

	public static void removeListener(Listener listener)
	{
		Iterator<ListenerReference> iter = listeners.iterator();
		while (iter.hasNext()) {
			ListenerReference ref = iter.next();
			if (ref.listener == listener) {
				iter.remove();
			}
		}

		minListenerPriority = MILESTONE;
		for (ListenerReference r : listeners) {
			if (minListenerPriority.greaterThan(r.priority))
				minListenerPriority = r.priority;
		}
	}

	public static void setProgressListener(Listener listener)
	{ progressListener = listener; }

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
				if (progressListener != null)
					progressListener.post(msg);
				return;
			case MILESTONE:
				// progress listener also recieves milestone messages
				if (progressListener != null)
					progressListener.post(msg);
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

		if (p.lessThan(minListenerPriority))
			return;

		for (ListenerReference ref : listeners) {
			if (!p.lessThan(ref.priority))
				ref.listener.post(msg);
		}
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
	{ defaultPriority = p; }

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
