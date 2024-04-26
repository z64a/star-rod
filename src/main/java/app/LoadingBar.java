package app;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;

import app.Resource.ResourceType;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.Logger.Listener;
import util.Logger.Message;
import util.Priority;
import util.ui.ImagePanel;

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

	public static void show(String title, boolean useImage)
	{
		show(title, Priority.STANDARD, useImage);
	}

	public static void show(String title, Priority priority, boolean useImage)
	{
		if (Environment.isCommandLine())
			return;

		if (instance == null) {
			instance = new LoadingBarWindow(title, useImage);
		}
		else {
			instance.setTitle(title);
			instance.titleLabel.setText(title);
			Logger.removeListener(instance);
		}

		Logger.addListener(instance, priority);
	}

	public static void dismiss()
	{
		if (instance != null) {
			instance.dispose();
			instance = null;
		}
	}

	private static LoadingBarWindow instance;

	private static class LoadingBarWindow extends JFrame implements Listener
	{
		private final JProgressBar progressBar;
		private final JLabel titleLabel;
		private final ImagePanel imagePanel;

		private LoadingBarWindow(String title, boolean useImage)
		{
			super();

			setTitle("Initializing");
			setIconImage(Environment.getDefaultIconImage());

			setMinimumSize(new Dimension(320, 64));
			setLocationRelativeTo(null);
			setUndecorated(true);
			progressBar = new JProgressBar();

			imagePanel = new ImagePanel();
			if (useImage) {
				try {
					BufferedImage bimg = null;
					if (Environment.isDeluxe()) {
						bimg = ImageIO.read(Resource.getStream(ResourceType.Deluxe, "splash.jpg"));
					}
					else {
						String[] splashNames = Resource.getResourceNames(ResourceType.Splash);

						if (splashNames.length > 0) {
							int randomNum = ThreadLocalRandom.current().nextInt(0, splashNames.length);
							bimg = ImageIO.read(Resource.getStream(ResourceType.Splash, splashNames[randomNum]));
						}
					}

					if (bimg != null) {
						if (bimg.getHeight() > 512) // do not resize classic splash screen
							bimg = resizeImage(bimg, 512);
						imagePanel.setImage(bimg);
						imagePanel.setPreferredSize(new Dimension(bimg.getWidth(), bimg.getHeight()));
					}
				}
				catch (IOException e) {
					Logger.logWarning("Couldn't load splash screen: " + e.getMessage());
				}
			}

			progressBar.setIndeterminate(true);
			progressBar.setStringPainted(true);
			progressBar.setString("Loading...");

			titleLabel = SwingUtils.getCenteredLabel(title, 14);

			setLayout(new MigLayout("fill"));

			if (useImage && imagePanel.hasImage())
				add(imagePanel, "grow, wrap");
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

	private static BufferedImage resizeImage(BufferedImage src, int targetHeight)
	{
		float ratio = ((float) src.getHeight() / (float) src.getWidth());
		int targetWidth = Math.round(targetHeight / ratio);

		BufferedImage bi = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = bi.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
		g2d.dispose();
		return bi;
	}
}
