package app.splash;

import java.awt.Frame;
import java.util.Hashtable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.formdev.flatlaf.FlatDarkLaf;

import net.miginfocom.swing.MigLayout;
import util.Logger;

/** Standalone developer preview; needs no project, ROM, or application configuration. */
public final class SplashPreviewWindow extends JDialog
{
	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(() -> {
			FlatDarkLaf.setup();
			new SplashPreviewWindow().setVisible(true);
		});
	}

	private final SplashPanel preview = new SplashPanel(640, 427);
	private final JSlider unhinged;
	private final JCheckBox deluxe = new JCheckBox("Deluxe");
	private final JLabel amount = new JLabel();
	private final JLabel status = new JLabel("Composing...");
	private final JTextArea credits = new JTextArea(3, 60);
	private SwingWorker<SplashPhotos.Splash, Void> worker;
	private long seed = ThreadLocalRandom.current().nextLong();

	public SplashPreviewWindow()
	{
		super((Frame) null, "Splash Preview", true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new MigLayout("fillx, insets 12, wrap 3", "[grow][][right]"));
		add(preview, "span, w 640!, h 427!");

		unhinged = new JSlider(0, 100, SplashRenderer.DEFAULT_UNHINGED);
		unhinged.setMajorTickSpacing(25);
		unhinged.setPaintTicks(true);
		Hashtable<Integer, JLabel> labels = new Hashtable<>();
		labels.put(0, new JLabel("Composed"));
		labels.put(50, new JLabel("Strange"));
		labels.put(100, new JLabel("Unhinged"));
		unhinged.setLabelTable(labels);
		unhinged.setPaintLabels(true);
		unhinged.addChangeListener(e -> {
			amount.setText("Preview Unhinged: " + unhinged.getValue());
			if (!unhinged.getValueIsAdjusting())
				refresh();
		});
		amount.setText("Preview Unhinged: " + unhinged.getValue());
		add(amount, "span 2, growx, gap top 8");
		deluxe.setToolTipText("Preview the rare gold edition without changing startup odds.");
		deluxe.addActionListener(e -> refresh());
		add(deluxe, "right");
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
		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());
		add(close, "skip 1, right");
		getRootPane().setDefaultButton(close);
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
		refresh();
	}

	private void refresh()
	{
		if (worker != null)
			worker.cancel(true);
		long currentSeed = seed;
		int level = unhinged.getValue();
		boolean deluxePreview = deluxe.isSelected();
		status.setText("Composing...");
		worker = new SwingWorker<>() {
			@Override
			protected SplashPhotos.Splash doInBackground() throws Exception
			{
				return deluxePreview ? SplashPhotos.createDeluxe(currentSeed, level) : SplashPhotos.create(currentSeed, level);
			}

			@Override
			protected void done()
			{
				if (isCancelled() || worker != this || !isDisplayable())
					return;
				try {
					SplashPhotos.Splash splash = get();
					preview.setImage(splash.image());
					status.setText(deluxePreview
						? "Deluxe preview. Reroll for another gold composition; startup odds stay 1 in 1,000."
						: "Startup Unhinged rises by 2-5 each launch, then resets to 50 after reaching 100.");
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
