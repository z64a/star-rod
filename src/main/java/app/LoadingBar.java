package app;

import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import app.splash.LoadingSplashPanel;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.Logger.Listener;
import util.Logger.Message;
import util.Priority;

public class LoadingBar
{
	public static void show(String title)
	{
		show(title, Priority.STANDARD, false);
	}

	public static void show(String title, Priority priority)
	{
		show(title, priority, false);
	}

	public static void show(String title, boolean fullSize)
	{
		show(title, Priority.STANDARD, fullSize);
	}

	public static void show(String title, Priority priority, boolean fullSize)
	{
		if (Environment.isCommandLine())
			return;
		if (!SwingUtilities.isEventDispatchThread()) {
			onEventThread(() -> show(title, priority, fullSize));
			return;
		}

		if (instance == null) {
			instance = new LoadingBarWindow(title, fullSize);
		}
		else {
			instance.setTitle(title);
			instance.titleLabel.setText(title);
			Logger.removeListener(instance);
		}

		Logger.addSwingListener(instance, priority);
	}

	public static void dismiss()
	{
		if (Environment.isCommandLine())
			return;
		if (!SwingUtilities.isEventDispatchThread()) {
			onEventThread(LoadingBar::dismiss);
			return;
		}
		if (instance != null) {
			instance.dispose();
			instance = null;
		}
	}

	private static LoadingBarWindow instance;

	private static void onEventThread(Runnable action)
	{
		try {
			SwingUtilities.invokeAndWait(action);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while updating the loading window", e);
		}
		catch (InvocationTargetException e) {
			throw new IllegalStateException("Could not update the loading window", e.getCause());
		}
	}

	private static class LoadingBarWindow extends StarRodFrame implements Listener
	{
		private final JProgressBar progressBar;
		private final JLabel titleLabel;

		private LoadingBarWindow(String title, boolean fullSize)
		{
			super();

			setTitle(title);

			setMinimumSize(new Dimension(320, 64));
			setLocationRelativeTo(null);
			setUndecorated(true);
			progressBar = new JProgressBar();

			progressBar.setIndeterminate(true);
			progressBar.setStringPainted(true);
			progressBar.setString("Loading...");

			titleLabel = SwingUtils.getCenteredLabel(title, 14);

			setLayout(new MigLayout("fill"));

			if (fullSize || LoadingSplashPanel.BANNERS_ENABLED)
				add(new LoadingSplashPanel(fullSize), "grow, wrap");
			add(titleLabel, "grow, wrap");
			add(progressBar, "grow, pushy");
			pack();
			setLocationRelativeTo(null);
			setVisible(true);
		}

		@Override
		public void dispose()
		{
			super.dispose();
			Logger.removeListener(this);
		}

		@Override
		public void post(Message msg)
		{
			progressBar.setString(msg.text);
		}
	}
}
