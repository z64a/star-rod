package game.map.editor.common;

import static app.Directories.PROJ_CFG;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import app.LoadingBar;
import app.StarRodException;
import app.StarRodMain;
import app.SwingUtils;
import app.SwingUtils.DialogBuilder;
import app.SwingUtils.OpenDialogCounter;
import app.config.Config;
import app.config.Options;
import app.config.Options.Scope;
import game.map.editor.CommandManager;
import game.map.editor.GLEditor;
import game.map.editor.Tickable;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.common.KeyboardInput.KeyboardInputListener;
import game.map.editor.common.MouseInput.MouseManagerListener;
import net.miginfocom.swing.MigLayout;
import util.LogFile;
import util.Logger;
import util.Logger.Listener;
import util.Logger.Message;
import util.ui.FadingLabel;

/**
 * Sets up LWJGL and manages the lifecycle of an editor tool.
 */
public abstract class BaseEditor extends GLEditor implements Logger.Listener, MouseManagerListener, KeyboardInputListener
{
	private final int targetFPS;
	private double deltaTime;
	private double time = Double.MIN_VALUE;
	private long frameCounter;

	private enum RunState
	{
		INIT, RUN, CLOSE
	}

	private RunState runState = RunState.INIT;
	private boolean exitToMenu = false;

	private List<Tickable> tickers = new ArrayList<>();

	// swing components
	private JFrame frame;
	private FadingLabel infoLabel;
	private JMenuBar menuBar;

	protected KeyboardInput keyboard;
	protected MouseInput mouse;

	private static final float MESSAGE_HOLD_TIME = 4.0f;
	private static final float MESSAGE_FADE_TIME = 0.5f;

	// editor state
	protected volatile boolean modified = false;
	private volatile boolean closeRequested = false;
	private volatile OpenDialogCounter openDialogs = new OpenDialogCounter();

	protected boolean glWindowGrabsMouse;
	protected boolean glWindowHaltsForDialogs;

	private CommandManager commandManager; // handles comnmand execution and undo/redo
	private BlockingQueue<Runnable> eventQueue = new ArrayBlockingQueue<>(16);

	// logging
	private Listener logListener;
	private File logFile;
	private LogFile log;

	private Config config = null;

	private Dimension prevCanvasSize = null;

	// gl renderer information

	public BaseEditor(BaseEditorSettings settings)
	{
		super();

		LoadingBar.show("Please Wait");

		glWindowGrabsMouse = settings.glWindowGrabsMouse;
		glWindowHaltsForDialogs = settings.glWindowHaltsForDialogs;
		targetFPS = settings.targetFPS;
		deltaTime = 1.0f / targetFPS;

		if (settings.hasLog) {
			logFile = new File(Directories.LOGS + settings.logName);
			try {
				log = new LogFile(logFile, false);
			}
			catch (IOException e1) {
				Logger.logWarning("Could not open log file: " + logFile.getAbsolutePath());
				log = null;
			}
		}

		if (settings.hasConfig) {
			File editorConfigFile = new File(PROJ_CFG + "/" + settings.configFileName);
			config = readEditorConfig(settings.configScope, editorConfigFile);
		}

		commandManager = new CommandManager(32);
		beforeCreateGui();

		// create the GUI
		CountDownLatch guiReadySignal = new CountDownLatch(1);
		SwingUtilities.invokeLater(() -> {
			createFrame(settings);
			guiReadySignal.countDown();
		});

		// wait for the swing thread to finish creating the GUI
		try {
			guiReadySignal.await();
		}
		catch (InterruptedException e) {
			StarRodMain.displayStackTrace(e);
			Environment.exit(-1);
		}

		if (Environment.isMacOS())
			setFullScreenEnabled(frame, false);
		frame.pack();

		Logger.addListener(this);
	}

	public void invokeLater(Runnable run)
	{
		eventQueue.add(run);
	}

	public void execute(AbstractCommand cmd)
	{
		if (SwingUtilities.isEventDispatchThread())
			eventQueue.add(() -> commandManager.executeCommand(cmd));
		else
			commandManager.executeCommand(cmd);
	}

	public void registerTickable(Tickable ticker)
	{
		tickers.add(ticker);
	}

	public void deregisterTickable(Tickable ticker)
	{
		tickers.remove(ticker);
	}

	public final boolean launch()
	{
		if (runState != RunState.INIT)
			throw new IllegalStateException("Cannot launch an editor which is already running!");
		else
			runState = RunState.RUN;

		try {
			FrameLimiter limiter = new FrameLimiter();
			boolean warmup = true;

			// force glInit
			glCanvas.render();

			// begin main loop
			while (!closeRequested) {
				long t0 = System.nanoTime();
				time = t0 / 1e9;

				// gl canvas acquires focus on mouseover
				if (glWindowGrabsMouse && !glCanvas.hasFocus() && mouse.hasLocation() && openDialogs.isZero()) {
					java.awt.EventQueue.invokeLater(() -> {
						glCanvas.requestFocusInWindow();
					});
				}

				// handle window resizing
				if (!glCanvas.getSize().equals(prevCanvasSize)) {
					resizeViews();
					prevCanvasSize = glCanvas.getSize();
				}

				keyboard.update(this, frame.isFocused());
				mouse.update(this, frame.isFocused());

				if (!glWindowHaltsForDialogs || !areDialogsOpen()) {
					runInContext(() -> {
						while (!eventQueue.isEmpty())
							eventQueue.poll().run();

						for (Tickable ticker : tickers)
							ticker.tick(deltaTime);

						// let the child do things
						update(deltaTime);
					});
				}

				if (glCanvas.isValid())
					glCanvas.render();
				limiter.sync(targetFPS);

				// maybe before limiter?
				infoLabel.update(deltaTime);
				deltaTime = (System.nanoTime() - t0) / 1e9;
				frameCounter++;

				if (warmup) {
					warmup = false;
					LoadingBar.dismiss();
					frame.setVisible(true);
				}
			}
			// end main loop

			runState = RunState.CLOSE;
			base_cleanup(false);
		}
		catch (Throwable t) {
			// handle crash from gl thread
			Logger.printStackTrace(t);

			try {
				if (runState != RunState.CLOSE)
					base_cleanup(true);
			}
			catch (Throwable x) {
				// ignore exceptions in crash-cleanup
			}
			if (logFile != null)
				throw new StarRodException(t, logFile);
			else
				throw new StarRodException(t);
		}

		return exitToMenu;
	}

	private void base_cleanup(boolean crashed)
	{
		cleanup(crashed);

		Logger.removeListener(logListener);
		if (log != null)
			log.close();

		if (config != null)
			config.saveConfigFile();

		frame.setVisible(false);
		frame.dispose();
		LoadingBar.dismiss();
	}

	protected void close(boolean returnToMainMenu)
	{
		exitToMenu = returnToMainMenu;
		WindowEvent closingEvent = new WindowEvent(frame, WindowEvent.WINDOW_CLOSING);
		Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
	}

	private final void createFrame(BaseEditorSettings windowSettings)
	{
		frame = new JFrame();

		if (windowSettings.fullscreen)
			frame.setExtendedState(Frame.MAXIMIZED_BOTH);
		else
			frame.setBounds(0, 0, windowSettings.sizeX, windowSettings.sizeY);

		if (windowSettings.resizeable)
			frame.setMinimumSize(new Dimension(windowSettings.sizeX, windowSettings.sizeY));

		frame.setLocationRelativeTo(null);
		frame.setTitle(windowSettings.title);

		frame.setIconImage(windowSettings.iconImage);

		keyboard = new KeyboardInput(glCanvas);
		mouse = new MouseInput(glCanvas);

		if (windowSettings.resizeable)
			glCanvas.setMinimumSize(new Dimension(1, 1));

		infoLabel = new FadingLabel(false, SwingConstants.LEFT, MESSAGE_HOLD_TIME, MESSAGE_FADE_TIME);

		JButton openLog = new JButton("Open Log");
		SwingUtils.setFontSize(openLog, 10);
		ActionListener openLogAction = createOpenLogAction();

		JPanel toolPanel = new JPanel(new MigLayout());
		toolPanel.setPreferredSize(new Dimension(windowSettings.sizeX, windowSettings.sizeY));

		if (windowSettings.hasMenuBar) {
			menuBar = new JMenuBar();
			frame.setJMenuBar(menuBar);
		}

		createGui(toolPanel, glCanvas, menuBar, infoLabel, openLogAction);

		JPanel contentPanel = new JPanel(new MigLayout("fill, insets 0"));
		contentPanel.add(toolPanel, "grow");

		frame.setContentPane(contentPanel);
		frame.setResizable(windowSettings.resizeable);

		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				openDialogs.increment();
				closeRequested = !modified || promptForSave();
				if (!closeRequested)
					openDialogs.decrement();
			}
		});

		KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
		manager.addKeyEventDispatcher(e -> {
			if (e.getID() == KeyEvent.KEY_PRESSED) {
				switch (e.getKeyCode()) {
					case KeyEvent.VK_Z:
						if (e.isControlDown())
							undoEDT();
						break;
					case KeyEvent.VK_Y:
						if (e.isControlDown())
							redoEDT();
						break;
				}
			}
			return false;
		});
	}

	protected void undoEDT()
	{}

	protected void redoEDT()
	{}

	private ActionListener createOpenLogAction()
	{
		JTextArea logTextArea = new JTextArea(32, 72);
		logTextArea.setEditable(false);

		JScrollPane logScrollPane = new JScrollPane(logTextArea);
		logScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		logListener = msg -> {
			logTextArea.append(msg.text + System.lineSeparator());

			JScrollBar vertical = logScrollPane.getVerticalScrollBar();
			vertical.setValue(vertical.getMaximum());
		};
		Logger.addListener(logListener);

		return ((e) -> {
			boolean success = false;

			if (log != null) {
				try {
					Desktop.getDesktop().open(logFile);
					success = true;
				}
				catch (IOException openDefaultIOE) {
					try {
						if (Environment.isWindows()) {
							Runtime rs = Runtime.getRuntime();
							rs.exec("notepad " + logFile.getCanonicalPath());
							success = true;
						}
					}
					catch (IOException nativeIOE) {}
				}
			}

			if (!success) {
				openDialogs.increment();
				int choice = SwingUtils.getOptionDialog()
					.setTitle("Editor Log")
					.setMessage(logScrollPane)
					.setMessageType(JOptionPane.PLAIN_MESSAGE)
					.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
					.setIcon(Environment.ICON_DEFAULT)
					.setOptions("Copy to Clipboard")
					.choose();
				openDialogs.decrement();

				if (choice == 0) {
					StringSelection stringSelection = new StringSelection(logTextArea.getText());
					Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
					cb.setContents(stringSelection, null);
				}
			}
		});
	}

	private Config readEditorConfig(Scope scope, File configFile)
	{
		if (!configFile.exists()) {
			Config cfg = makeNewConfig(scope, configFile);
			if (cfg == null) {
				SwingUtils.getErrorDialog()
					.setTitle("Create Config Failed")
					.setMessage("Failed to create new config:", configFile.getAbsolutePath())
					.show();
				return null;
			}

			cfg.saveConfigFile();
			return cfg;
		}

		Config cfg = new Config(configFile, scope);
		try {
			cfg.readConfig();
		}
		catch (IOException e) {
			SwingUtils.getErrorDialog()
				.setTitle("Load Config Failed")
				.setMessage("IOException occured while reading config:", configFile.getAbsolutePath())
				.show();
			return null;
		}
		return cfg;
	}

	private Config makeNewConfig(Scope scope, File configFile)
	{
		try {
			FileUtils.touch(configFile);
		}
		catch (IOException e) {
			return null;
		}

		Config cfg = new Config(configFile, scope);
		for (Options opt : Options.values()) {
			if (opt.scope == scope)
				opt.setToDefault(cfg);
		}

		return cfg;
	}

	protected Config getConfig()
	{
		return config;
	}

	private final boolean promptForSave()
	{
		openDialogs.increment();

		int choice = SwingUtils.getConfirmDialog()
			.setTitle("Warning")
			.setMessage("Unsaved changes will be lost!", "Would you like to save now?")
			.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
			.choose();

		openDialogs.decrement();

		switch (choice) {
			case JOptionPane.YES_OPTION:
				saveChanges();
				break;
			case JOptionPane.NO_OPTION:
				break;
			case JOptionPane.CANCEL_OPTION:
				closeRequested = false;
				return false;
		}

		return true;
	}

	protected final DialogBuilder getMessageDialog(String title, Object message)
	{
		return SwingUtils.getMessageDialog()
			.setParent(frame)
			.setTitle(title)
			.setMessage(message)
			.setCounter(openDialogs);
	}

	protected final DialogBuilder getConfirmDialog(String title, Object message)
	{
		return SwingUtils.getConfirmDialog()
			.setParent(frame)
			.setTitle(title)
			.setMessage(message)
			.setCounter(openDialogs)
			.setMessageType(JOptionPane.PLAIN_MESSAGE);
	}

	protected final DialogBuilder getOptionDialog(String title, Object message)
	{
		return SwingUtils.getOptionDialog()
			.setParent(frame)
			.setTitle(title)
			.setMessage(message)
			.setCounter(openDialogs)
			.setMessageType(JOptionPane.PLAIN_MESSAGE);
	}

	/**
	 * Interface for children
	 */

	protected void beforeCreateGui()
	{}

	@Override
	protected void glInit()
	{}

	protected void cleanup(boolean crashed)
	{}

	protected void resizeViews()
	{}

	protected abstract void createGui(JPanel toolPanel, Canvas glCanvas, JMenuBar menubar, JLabel infoLabel, ActionListener openLogAction);

	protected abstract void update(double deltaTime);

	@Override
	protected void glDraw()
	{}

	protected abstract void saveChanges();

	/**
	 * Exposed for children
	 */

	public final long getFrameCount()
	{
		return frameCounter;
	}

	public final double getTime()
	{
		return time;
	}

	public final double getDeltaTime()
	{
		return deltaTime;
	}

	public final int glCanvasWidth()
	{
		return glCanvas.getWidth();
	}

	public final int glCanvasHeight()
	{
		return glCanvas.getHeight();
	}

	protected final void revalidateFrame()
	{
		frame.revalidate();
	}

	protected final void showErrorDialog(String title, String msg)
	{
		SwingUtils.getErrorDialog()
			.setParent(frame)
			.setTitle(title)
			.setMessage(msg)
			.showLater();
	}

	protected final void showStackTrace(Throwable t)
	{
		openDialogs.increment();
		StarRodMain.displayStackTrace(t);
		openDialogs.decrement();
	}

	protected final JFrame getFrame()
	{
		return frame;
	}

	public boolean areDialogsOpen()
	{
		return !openDialogs.isZero();
	}

	public final void incrementDialogsOpen()
	{
		openDialogs.increment();
	}

	public final void decrementDialogsOpen()
	{
		openDialogs.decrement();
	}

	/**
	 * Implement interfaces
	 */

	@Override
	public void post(Message msg)
	{
		Color c = null;
		switch (msg.priority) {
			case WARNING:
			case ERROR:
				c = SwingUtils.getRedTextColor();
				break;
			default:
				c = SwingUtils.getTextColor();
				break;
		}
		infoLabel.setMessage(msg.text, c);
	}
}
