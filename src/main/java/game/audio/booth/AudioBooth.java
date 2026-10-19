package game.audio.booth;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import common.FrameLimiter;

import app.Environment;
import app.ThemesEditor;
import game.audio.engine.AudioEngine;
import game.audio.engine.PlaybackSession;
import game.audio.engine.SoundLibrary;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.ThemedIcon;

/** Previews the project's samples, sound effects, ambient sequences, and music from the merged asset stack. */
public class AudioBooth
{
	private static final int VOLUME_SLIDER_MAX = 100;
	private static final int WAVEFORM_SAMPLE_COUNT = 8192;

	private final Object threadLock = new Object();

	private final AudioEngine engine;
	private final CountDownLatch guiClosedSignal;
	private List<AudioBoothTab> boothTabs;
	private AudioBoothTab selectionOwner;

	private final JLabel statusLabel;
	private final JLabel timeLabel;
	private final JButton pauseButton;
	private final JButton restartButton;
	private final JButton stopButton;
	private final JSlider timeSlider;
	private final JSlider masterVolumeSlider;
	private final JButton muteButton;
	private final JTabbedPane assetTabs;
	private final WaveformPanel waveformPanel;

	private boolean ignoreSliderUpdate;
	private volatile boolean running = true;
	private volatile int seekTime = -1;
	private int timelineDuration;
	private PlaybackSession playbackSession;
	private boolean playbackWasActive;
	private int unmutedVolume = VOLUME_SLIDER_MAX;

	public AudioBooth(CountDownLatch guiClosedSignal) throws Exception
	{
		this.guiClosedSignal = guiClosedSignal;
		SoundLibrary library = new SoundLibrary();
		engine = new AudioEngine();
		waveformPanel = new WaveformPanel(WAVEFORM_SAMPLE_COUNT);
		engine.setAudioMonitor(waveformPanel);
		boothTabs = createTabs(library);
		attachTabs(boothTabs);

		JFrame frame = new JFrame(Environment.decorateTitle("Audio Booth"));
		frame.setIconImage(Environment.getDefaultIconImage());
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				close(frame);
			}
		});

		frame.setLayout(new MigLayout("fill, ins 12", "[grow,fill]", "[grow][]"));

		masterVolumeSlider = new JSlider(0, VOLUME_SLIDER_MAX,
			gainToVolumeSlider(engine.getMasterVolume()));
		if (masterVolumeSlider.getValue() > 0)
			unmutedVolume = masterVolumeSlider.getValue();

		muteButton = new JButton(ThemedIcon.VOLUME_UP_16);
		muteButton.setMargin(new Insets(0, 2, 0, 2));
		muteButton.addActionListener((e) -> toggleMute());
		masterVolumeSlider.addChangeListener((e) -> updateMasterVolume());
		updateMasterVolume();

		statusLabel = new JLabel("Select an audio asset.");
		statusLabel.setVerticalAlignment(SwingConstants.CENTER);
		timeLabel = new JLabel(formatTime(0) + " / " + formatTime(0));

		timeSlider = new JSlider(0, 1, 0);
		timeSlider.setEnabled(false);
		timeSlider.addChangeListener((e) -> {
			if (!ignoreSliderUpdate)
				seekTime = timeSlider.getValue();
		});

		Insets playbackButtonInsets = new Insets(0, 2, 0, 2);

		pauseButton = new JButton(ThemedIcon.PAUSE_24);
		pauseButton.setToolTipText("Pause");
		pauseButton.setMargin(playbackButtonInsets);
		pauseButton.setEnabled(false);
		pauseButton.addActionListener((e) -> {
			boolean restart = false;
			boolean paused = false;
			synchronized (threadLock) {
				if (playbackSession == null || !playbackSession.isPlaying()) {
					restart = true;
				}
				else {
					playbackSession.setPaused(!playbackSession.isPaused());
					paused = playbackSession.isPaused();
				}
			}
			if (restart)
				restartPlayback();
			else
				setPlaybackPaused(paused);
		});

		restartButton = new JButton(ThemedIcon.REWIND_24);
		restartButton.setToolTipText("Restart");
		restartButton.setMargin(playbackButtonInsets);
		restartButton.setEnabled(false);
		restartButton.addActionListener((e) -> restartPlayback());

		stopButton = new JButton(ThemedIcon.STOP_24);
		stopButton.setToolTipText("Stop");
		stopButton.setMargin(playbackButtonInsets);
		stopButton.setEnabled(false);
		stopButton.addActionListener((e) -> stopPlayback());

		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem reloadItem = new JMenuItem("Reload");
		reloadItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
		reloadItem.addActionListener((e) -> reload());
		JMenuItem exitItem = new JMenuItem("Exit");
		exitItem.addActionListener((e) -> close(frame));
		fileMenu.add(reloadItem);
		fileMenu.add(exitItem);
		menuBar.add(fileMenu);
		ThemesEditor.addThemeMenuItem(menuBar, frame);
		frame.setJMenuBar(menuBar);

		assetTabs = new JTabbedPane();
		populateAssetTabs();

		int volumeSliderWidth = timeLabel.getPreferredSize().width;
		JPanel playbackPanel = new JPanel(new MigLayout(
			"ins 0 8 0 8, fillx", "[][][][grow,fill][][" + volumeSliderWidth + "!,fill]"));
		playbackPanel.add(restartButton, "sg playbackButton, h 24!");
		playbackPanel.add(stopButton, "sg playbackButton, h 24!");
		playbackPanel.add(pauseButton, "sg playbackButton, h 24!");
		playbackPanel.add(timeSlider, "span 2, pushx, growx");
		playbackPanel.add(timeLabel, "growx, wrap");

		waveformPanel.setPreferredSize(new Dimension(100, 24));
		playbackPanel.add(waveformPanel, "span 3, growx, h 24!");
		playbackPanel.add(statusLabel, "growx");
		playbackPanel.add(muteButton, "h 24!, gapright 0");
		playbackPanel.add(masterVolumeSlider, "growx, gapleft 0");

		frame.add(assetTabs, "grow, push, wrap");
		frame.add(playbackPanel, "growx");

		frame.setMinimumSize(new Dimension(640, 360));
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		Thread audioThread = new Thread(() -> {
			try {
				run();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			catch (Exception e) {
				Logger.logError("Audio Booth playback thread failed");
				Logger.printStackTrace(e);
			}
		}, "Audio Booth");
		audioThread.start();
	}

	private List<AudioBoothTab> createTabs(SoundLibrary library)
	{
		List<AudioBoothTab> tabs = new ArrayList<>();
		tabs.add(new SamplesTab(this, engine, library));
		tabs.add(new SfxTab(this, engine, library));
		tabs.add(new MseqTab(this, engine, library));
		tabs.add(new BgmTab(this, engine, library));
		return tabs;
	}

	private void populateAssetTabs()
	{
		assetTabs.removeAll();
		for (AudioBoothTab tab : boothTabs)
			addAssetTab(tab);
	}

	private void addAssetTab(AudioBoothTab tab)
	{
		assetTabs.addTab(tab.getTitle(), tab);
	}

	/** Reads the audio of the project again and, if a sound was selected, plays it again. */
	private void reload()
	{
		AudioBoothTab oldOwner = selectionOwner != null && selectionOwner.hasSelection() ? selectionOwner : null;
		int selectedTab = assetTabs.getSelectedIndex();

		try {
			List<AudioBoothTab> replacementTabs = createTabs(new SoundLibrary());
			AudioBoothTab.PreparedSelection preparedSelection = oldOwner == null ? null
				: oldOwner.prepareReload(replacementTabs.get(boothTabs.indexOf(oldOwner)));

			synchronized (threadLock) {
				closeTabs(boothTabs);
				attachTabs(replacementTabs);
				seekTime = -1;
				playbackSession = null;
			}

			boothTabs = replacementTabs;
			selectionOwner = null;

			populateAssetTabs();
			assetTabs.setSelectedIndex(selectedTab);
			updateTimeline(0);
			setPlaybackPaused(false);
			updatePlaybackControls();
			statusLabel.setText("Reloaded the audio.");
			if (preparedSelection != null)
				preparedSelection.restore();
		}
		catch (Exception e) {
			Logger.logError("Could not reload the audio");
			Logger.printStackTrace(e);
			statusLabel.setText("Could not reload the audio: " + e.getMessage());
		}
	}

	boolean startPlayback(AudioBoothTab owner, PlaybackSession session, Runnable action)
	{
		if (owner.getSession() != session)
			throw new IllegalArgumentException("Playback session does not belong to the requesting Audio Booth tab");

		int duration;
		boolean playing;
		synchronized (threadLock) {
			stopAllTabs();
			seekTime = -1;
			action.run();
			selectionOwner = owner;
			playing = session.isPlaying();
			playbackSession = playing ? session : null;
			duration = session.getDuration();
		}
		updateTimeline(duration);
		setPlaybackPaused(false);
		updatePlaybackControls();
		return playing;
	}

	void selectWithoutPlayback(AudioBoothTab owner)
	{
		synchronized (threadLock) {
			stopAllTabs();
			seekTime = -1;
			selectionOwner = owner;
			playbackSession = null;
		}
		updateTimeline(0);
		setPlaybackPaused(false);
		updatePlaybackControls();
	}

	void clearSelection(AudioBoothTab owner)
	{
		if (selectionOwner != owner)
			return;
		synchronized (threadLock) {
			stopAllTabs();
			seekTime = -1;
			selectionOwner = null;
			playbackSession = null;
		}
		updateTimeline(0);
		setPlaybackPaused(false);
		updatePlaybackControls();
	}

	boolean ownsSelection(AudioBoothTab owner)
	{
		return selectionOwner == owner;
	}

	boolean isCurrentSession(PlaybackSession session)
	{
		return playbackSession == session;
	}

	void runAudioAction(Runnable action)
	{
		synchronized (threadLock) {
			action.run();
		}
	}

	void refreshTimeline(PlaybackSession session)
	{
		if (playbackSession != session)
			return;
		int time;
		int duration;
		synchronized (threadLock) {
			time = session.getTime();
			duration = session.getDuration();
		}
		updateTimeline(duration, time);
	}

	void setStatus(String status)
	{
		statusLabel.setText(status);
	}

	void updatePlaybackControls()
	{
		boolean hasPlayback = playbackSession != null;
		restartButton.setEnabled(hasPlayback);
		pauseButton.setEnabled(hasPlayback);
		stopButton.setEnabled(isPlaybackActive());
		for (AudioBoothTab tab : boothTabs)
			tab.updatePlaybackState(playbackSession);
	}

	private void updateTimeline(int duration)
	{
		updateTimeline(duration, 0);
	}

	private void updateTimeline(int duration, int time)
	{
		timelineDuration = duration;
		ignoreSliderUpdate = true;
		timeSlider.setMaximum(Math.max(1, duration));
		timeSlider.setValue(Math.max(0, Math.min(time, duration)));
		ignoreSliderUpdate = false;
		timeSlider.setEnabled(duration > 0);
		updateTimeLabel(time);
	}

	private boolean isPlaybackActive()
	{
		return playbackSession != null && playbackSession.isPlaying();
	}

	private void setPlaybackPaused(boolean paused)
	{
		pauseButton.setIcon(paused ? ThemedIcon.PLAY_24 : ThemedIcon.PAUSE_24);
		pauseButton.setToolTipText(paused ? "Play" : "Pause");
	}

	private void toggleMute()
	{
		if (masterVolumeSlider.getValue() == 0)
			masterVolumeSlider.setValue(unmutedVolume);
		else {
			unmutedVolume = masterVolumeSlider.getValue();
			masterVolumeSlider.setValue(0);
		}
	}

	private void updateMasterVolume()
	{
		int volume = masterVolumeSlider.getValue();
		engine.setMasterVolume(volumeSliderToGain(volume));
		if (volume == 0) {
			muteButton.setIcon(ThemedIcon.VOLUME_OFF_16);
			muteButton.setToolTipText("Unmute");
		}
		else {
			unmutedVolume = volume;
			muteButton.setIcon(ThemedIcon.VOLUME_UP_16);
			muteButton.setToolTipText("Mute");
		}
	}

	private void stopPlayback()
	{
		synchronized (threadLock) {
			if (playbackSession != null)
				playbackSession.stop();
			seekTime = -1;
		}

		ignoreSliderUpdate = true;
		timeSlider.setValue(0);
		ignoreSliderUpdate = false;
		timeSlider.setEnabled(false);
		updateTimeLabel(0);
		setPlaybackPaused(true);
		updatePlaybackControls();
		statusLabel.setText("Playback stopped.");
	}

	private void restartPlayback()
	{
		int duration;
		boolean playing;
		synchronized (threadLock) {
			if (playbackSession == null)
				return;
			seekTime = -1;
			playbackSession.restart();
			duration = playbackSession.getDuration();
			playing = playbackSession.isPlaying();
		}
		updateTimeline(duration);
		setPlaybackPaused(false);
		updatePlaybackControls();
		statusLabel.setText(playing ? "Playback restarted." : "Could not restart playback.");
	}

	private void updateTimeLabel(int playbackTime)
	{
		timeLabel.setText(formatTime(playbackTime) + " / " + formatTime(timelineDuration));
	}

	private static String formatTime(int time)
	{
		long milliseconds = Math.round(time * 1000.0 / AudioEngine.OUTPUT_RATE);
		long minutes = milliseconds / 60000;
		long seconds = (milliseconds / 1000) % 60;
		long millis = milliseconds % 1000;
		return String.format("%d:%02d.%03d", minutes, seconds, millis);
	}

	private static int gainToVolumeSlider(int gain)
	{
		double ratio = Math.max(0.0, Math.min(1.0,
			gain / (double) AudioEngine.MAX_MASTER_VOLUME));
		return (int) Math.round(Math.sqrt(ratio) * VOLUME_SLIDER_MAX);
	}

	private static int volumeSliderToGain(int volume)
	{
		double ratio = volume / (double) VOLUME_SLIDER_MAX;
		return (int) Math.round(ratio * ratio * AudioEngine.MAX_MASTER_VOLUME);
	}

	private static void attachTabs(List<AudioBoothTab> tabs)
	{
		for (AudioBoothTab tab : tabs)
			tab.attach();
	}

	private static void closeTabs(List<AudioBoothTab> tabs)
	{
		for (AudioBoothTab tab : tabs)
			tab.close();
	}

	private void stopAllTabs()
	{
		for (AudioBoothTab tab : boothTabs)
			tab.stop();
	}

	private void close(JFrame frame)
	{
		if (!running)
			return;

		running = false;
		synchronized (threadLock) {
			engine.setAudioMonitor(null);
			closeTabs(boothTabs);
			engine.shutdown();
		}
		frame.dispose();
		guiClosedSignal.countDown();
	}

	private void run() throws InterruptedException
	{
		double deltaTime = AudioEngine.FRAME_TIME;
		FrameLimiter limiter = new FrameLimiter();

		while (running) {
			long startTime = System.nanoTime();
			int playbackTime;
			boolean playbackActiveBeforeRender;
			boolean playbackActive;
			boolean playbackPaused;

			synchronized (threadLock) {
				if (seekTime >= 0 && playbackSession != null) {
					playbackSession.seekTime(seekTime);
					seekTime = -1;
				}

				playbackActiveBeforeRender = playbackSession != null && playbackSession.isPlaying();
				engine.renderFrame(deltaTime, false);
				if (playbackSession == null) {
					playbackTime = 0;
					playbackActive = false;
					playbackPaused = false;
				}
				else {
					playbackTime = playbackSession.getTime();
					playbackActive = playbackSession.isPlaying();
					playbackPaused = playbackSession.isPaused();
				}
			}

			boolean playbackFinished = (playbackWasActive || playbackActiveBeforeRender) && !playbackActive;
			playbackWasActive = playbackActive;
			updateTimeSlider(playbackTime, playbackPaused, playbackFinished);
			limiter.sync(AudioEngine.TARGET_FPS);
			deltaTime = (System.nanoTime() - startTime) / 1e9;
		}
	}

	private void updateTimeSlider(int playbackTime, boolean playbackPaused, boolean refreshPlaybackControls)
	{
		SwingUtilities.invokeLater(() -> {
			if (!running)
				return;
			waveformPanel.refresh();
			ignoreSliderUpdate = true;
			timeSlider.setValue(playbackTime);
			ignoreSliderUpdate = false;
			updateTimeLabel(playbackTime);
			if (refreshPlaybackControls) {
				setPlaybackPaused(playbackPaused || !isPlaybackActive());
				updatePlaybackControls();
			}
		});
	}
}
