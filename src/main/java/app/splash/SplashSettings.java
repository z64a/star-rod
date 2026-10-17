package app.splash;

import java.awt.Frame;
import java.util.Hashtable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import app.Environment;
import app.config.Options;
import net.miginfocom.swing.MigLayout;
import util.Logger;

/** Tune the amount of chaos against the same seed, then reroll to try other compositions. */
public final class SplashSettings extends JDialog
{

	private final SplashPanel preview = new SplashPanel(640, 427);
	private final JSlider unhinged;
	private final JLabel amount = new JLabel();
	private final JLabel status = new JLabel("Composing...");
	private final JTextArea credits = new JTextArea(3, 60);
	private SwingWorker<SplashPhotos.Splash, Void> worker;
	private long seed = ThreadLocalRandom.current().nextLong();

	public SplashSettings(Frame owner)
	{
		super(owner, "Splash Screens", true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new MigLayout("fillx, insets 12, wrap 3", "[grow][][right]"));
		add(preview, "span, w 640!, h 427!");

		unhinged = new JSlider(0, 100, Environment.mainConfig.getInteger(Options.SplashUnhinged));
		unhinged.setMajorTickSpacing(25);
		unhinged.setPaintTicks(true);
		Hashtable<Integer, JLabel> labels = new Hashtable<>();
		labels.put(0, new JLabel("Composed"));
		labels.put(50, new JLabel("Strange"));
		labels.put(100, new JLabel("Unhinged"));
		unhinged.setLabelTable(labels);
		unhinged.setPaintLabels(true);
		unhinged.addChangeListener(e -> {
			amount.setText("Unhinged: " + unhinged.getValue());
			if (!unhinged.getValueIsAdjusting())
				refresh();
		});
		amount.setText("Unhinged: " + unhinged.getValue());
		add(amount, "span, gap top 8");
		add(unhinged, "span, growx");
		add(status, "span, growx");

		credits.setEditable(false);
		credits.setLineWrap(true);
		credits.setWrapStyleWord(true);
		credits.setFont(credits.getFont().deriveFont(11f));
		add(new JScrollPane(credits), "span, growx, h 60!");
		JButton reroll = new JButton("Reroll");
		reroll.addActionListener(e -> {
			seed = ThreadLocalRandom.current().nextLong();
			refresh();
		});
		add(reroll, "left");
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(e -> dispose());
		add(cancel);
		JButton save = new JButton("Save");
		save.addActionListener(e -> {
			Environment.mainConfig.setInteger(Options.SplashUnhinged, unhinged.getValue());
			Environment.mainConfig.saveConfigFile();
			dispose();
		});
		add(save);
		getRootPane().setDefaultButton(save);
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
		refresh();
	}

	private void refresh()
	{
		if (worker != null)
			worker.cancel(true);
		long currentSeed = seed;
		int level = unhinged.getValue();
		status.setText("Composing...");
		worker = new SwingWorker<>() {
			@Override
			protected SplashPhotos.Splash doInBackground() throws Exception
			{
				return SplashPhotos.create(currentSeed, level);
			}

			@Override
			protected void done()
			{
				if (isCancelled() || worker != this || !isDisplayable())
					return;
				try {
					SplashPhotos.Splash splash = get();
					preview.setImage(splash.image());
					status.setText("A new composition on every launch. Save applies this intensity to future splashes.");
					credits.setText("Photos from Pexels (Pexels License):\n" + splash.photos().stream()
						.map(photo -> photo.photographer() + " - " + photo.source()).collect(Collectors.joining("\n")));
					credits.setCaretPosition(0);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				catch (ExecutionException e) {
					status.setText("Couldn't load splash photos. See the log for details.");
					Logger.logWarning("Couldn't preview splash: " + e.getCause().getMessage());
				}
			}
		};
		worker.execute();
	}

	@Override
	public void dispose()
	{
		if (worker != null)
			worker.cancel(true);
		super.dispose();
	}
}
