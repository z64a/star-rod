package app.splash;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import javax.swing.JPanel;
import javax.swing.SwingWorker;

import app.Environment;
import app.config.Options;
import util.Logger;

/** Loading artwork follows the component lifecycle and never holds up the operation. */
public final class LoadingSplashPanel extends JPanel
{

	private final boolean fullSize;
	private final SplashPanel picture;
	private SwingWorker<BufferedImage, Void> worker;
	private boolean loaded;

	public LoadingSplashPanel(boolean fullSize)
	{
		super(new BorderLayout());
		this.fullSize = fullSize;
		picture = new SplashPanel(fullSize ? SplashRenderer.WIDTH : SplashRenderer.BANNER_WIDTH,
			fullSize ? SplashRenderer.HEIGHT : SplashRenderer.BANNER_HEIGHT, fullSize ? "STAR ROD" : "Loading...");
		add(picture);
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		if (loaded || worker != null)
			return;
		long seed = ThreadLocalRandom.current().nextLong();
		int unhinged = Environment.mainConfig == null ? SplashRenderer.DEFAULT_UNHINGED
			: Environment.mainConfig.getInteger(Options.SplashUnhinged);
		worker = new SwingWorker<>() {
			@Override
			protected BufferedImage doInBackground() throws Exception
			{
				return (fullSize ? SplashPhotos.create(seed, unhinged) : SplashPhotos.createBanner(seed, unhinged)).image();
			}

			@Override
			protected void done()
			{
				if (isCancelled() || worker != this || !isDisplayable())
					return;
				try {
					picture.setImage(get());
					loaded = true;
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				catch (ExecutionException e) {
					Logger.logWarning("Couldn't compose loading artwork: " + e.getCause().getMessage());
				}
			}
		};
		worker.execute();
	}

	@Override
	public void removeNotify()
	{
		if (worker != null) {
			worker.cancel(true);
			worker = null;
		}
		super.removeNotify();
	}
}
