package app;

import static app.Directories.PROJ_THUMBNAIL;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.apache.commons.io.FilenameUtils;

import app.config.Options;
import app.input.InvalidInputException;
import assets.AssetHandle;
import assets.AssetManager;
import assets.ExpectedAsset;
import game.globals.editor.GlobalsEditor;
import game.map.Map;
import game.map.compiler.BuildException;
import game.map.compiler.CollisionCompiler;
import game.map.compiler.GeometryCompiler;
import game.map.editor.MapEditor;
import game.map.editor.common.BaseEditor;
import game.map.scripts.ScriptGenerator;
import game.map.scripts.extract.Extractor;
import game.message.editor.MessageEditor;
import game.sprite.editor.SpriteEditor;
import game.texture.editor.ImageEditor;
import game.worldmap.WorldMapEditor;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.Logger.Listener;
import util.Priority;

public class StarRodMain extends StarRodFrame
{
	public static void main(String[] args) throws InterruptedException
	{
		boolean isCommandLine = args.length > 0 || GraphicsEnvironment.isHeadless();

		if (isCommandLine) {
			Environment.initialize(true);
			runCommandLine(args);
			Environment.exit();
		}
		else {
			Environment.initialize(false);
			LoadingBar.dismiss();
			new StarRodMain();
		}
	}

	private final JTextArea consoleTextArea;
	private final Listener consoleListener;

	private final JPanel progressPanel;
	private final JLabel progressLabel;
	private final JProgressBar progressBar;

	private boolean taskRunning = false;

	private List<JButton> buttons = new ArrayList<>();

	private StarRodMain()
	{
		setTitle(Environment.decorateTitle("Star Rod"));
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		setMinimumSize(new Dimension(480, 32));
		setLocationRelativeTo(null);

		JTextField projectDirField = new JTextField();
		projectDirField.setMinimumSize(new Dimension(64, 24));
		projectDirField.setText(Environment.getProjectDirectory().getAbsolutePath());

		projectDirField.addActionListener((e) -> {
			File choice = new File(projectDirField.getText());
			if (choice != null) {
				try {
					boolean validProject = Environment.loadProject(choice);
					if (!validProject) {
						projectDirField.setText(Environment.getProjectDirectory().getAbsolutePath());
					}
				}
				catch (Throwable t) {
					displayStackTrace(t);
				}
			}
		});

		JButton chooseFolderButton = new JButton("Choose");
		chooseFolderButton.addActionListener(e -> {
			try {
				Environment.promptChangeProject();
				projectDirField.setText(Environment.getProjectDirectory().getAbsolutePath());
			}
			catch (Throwable t) {
				displayStackTrace(t);
			}
		});
		buttons.add(chooseFolderButton);

		JButton mapEditorButton = new JButton("Map Editor");
		trySetIcon(mapEditorButton, ExpectedAsset.ICON_MAP_EDITOR);
		SwingUtils.setFontSize(mapEditorButton, 12);
		mapEditorButton.addActionListener((e) -> {
			action_openMapEditor();
		});
		buttons.add(mapEditorButton);

		JButton spriteEditorButton = new JButton("Sprite Editor");
		trySetIcon(spriteEditorButton, ExpectedAsset.ICON_SPRITE_EDITOR);
		SwingUtils.setFontSize(spriteEditorButton, 12);
		spriteEditorButton.addActionListener((e) -> {
			action_openSpriteEditor();
		});
		buttons.add(spriteEditorButton);

		JButton msgEditorButton = new JButton("Message Editor");
		trySetIcon(msgEditorButton, ExpectedAsset.ICON_MSG_EDITOR);
		SwingUtils.setFontSize(msgEditorButton, 12);
		msgEditorButton.addActionListener((e) -> {
			action_openMessageEditor();
		});
		buttons.add(msgEditorButton);

		JButton globalsEditorButton = new JButton("Globals Editor");
		trySetIcon(globalsEditorButton, ExpectedAsset.ICON_GLOBALS_EDITOR);
		SwingUtils.setFontSize(globalsEditorButton, 12);
		globalsEditorButton.addActionListener((e) -> {
			action_openGlobalsEditor();
		});
		buttons.add(spriteEditorButton);

		JButton worldEditorButton = new JButton("World Map Editor");
		trySetIcon(worldEditorButton, ExpectedAsset.ICON_WORLD_EDITOR);
		SwingUtils.setFontSize(worldEditorButton, 12);
		worldEditorButton.addActionListener((e) -> {
			action_openWorldMapEditor();
		});
		buttons.add(worldEditorButton);

		JButton imageEditorButton = new JButton("Image Editor");
		trySetIcon(imageEditorButton, ExpectedAsset.ICON_IMAGE_EDITOR);
		SwingUtils.setFontSize(imageEditorButton, 12);
		imageEditorButton.addActionListener((e) -> {
			action_openImageEditor();
		});
		buttons.add(imageEditorButton);

		JButton themesMenuButton = new JButton("Choose Theme");
		trySetIcon(themesMenuButton, ExpectedAsset.ICON_THEMES);
		SwingUtils.setFontSize(themesMenuButton, 12);
		themesMenuButton.addActionListener((e) -> {
			action_openThemesMenu();
		});
		buttons.add(themesMenuButton);

		JButton extractDataButton = new JButton("Extract Map Data");
		trySetIcon(extractDataButton, ExpectedAsset.ICON_EXTRACT);
		SwingUtils.setFontSize(themesMenuButton, 12);
		extractDataButton.addActionListener((e) -> {
			action_extractMapData();
		});
		buttons.add(extractDataButton);

		// not ready
		/*
		JButton captureThumbnailsButton = new JButton("Capture Thumbnails");
		trySetIcon(captureThumbnailsButton, ExpectedAsset.ICON_THEMES);
		SwingUtils.setFontSize(themesMenuButton, 12);
		captureThumbnailsButton.addActionListener((e) -> {
			action_captureThumbnails();
		});
		buttons.add(captureThumbnailsButton);
		*/

		consoleTextArea = new JTextArea();
		consoleTextArea.setRows(8);
		consoleTextArea.setEditable(false);

		JScrollPane consoleScrollPane = new JScrollPane(consoleTextArea);
		consoleScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		//	consoleScrollPane.setVisible(false);

		consoleListener = (msg) -> {
			consoleTextArea.append(msg.text + System.lineSeparator());
			JScrollBar vertical = consoleScrollPane.getVerticalScrollBar();
			vertical.setValue(vertical.getMaximum());
		};

		JMenuItem copyText = new JMenuItem("Copy Text");
		JPopupMenu copyTextMenu = new JPopupMenu();
		copyTextMenu.add(copyText);
		consoleScrollPane.setComponentPopupMenu(copyTextMenu);
		copyText.addActionListener(e -> {
			StringSelection stringSelection = new StringSelection(consoleTextArea.getText());
			Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
			cb.setContents(stringSelection, null);
		});

		progressLabel = new JLabel("Waiting...");
		progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		progressPanel = new JPanel();
		progressPanel.setLayout(new MigLayout("fillx"));
		progressPanel.add(progressLabel, "wrap");
		progressPanel.add(progressBar, "grow, wrap 8");
		progressPanel.setVisible(false);

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				int choice = JOptionPane.OK_OPTION;

				if (taskRunning)
					choice = SwingUtils.getConfirmDialog()
						.setTitle("Task Still Running")
						.setMessage("A task is still running.", "Are you sure you want to exit?")
						.setMessageType(JOptionPane.WARNING_MESSAGE)
						.setOptionsType(JOptionPane.YES_NO_OPTION)
						.choose();

				if (choice == JOptionPane.OK_OPTION) {
					dispose();
					Environment.exit();
				}
			}
		});

		setLayout(new MigLayout("fillx, ins 16 16 16 16, wrap 2, hidemode 3", "[sg main, grow]8[sg main, grow]"));
		SwingUtils.addBorderPadding(projectDirField);

		add(new JLabel("Project:"), "sgy field, span, split 3");
		add(projectDirField, "pushx, growx, sgy field");
		add(chooseFolderButton, "wrap, sgy field, gapbottom 8");

		add(mapEditorButton, "grow");
		add(spriteEditorButton, "grow");

		add(globalsEditorButton, "grow");
		add(msgEditorButton, "grow");

		add(worldEditorButton, "grow");
		add(imageEditorButton, "grow");

		add(themesMenuButton, "grow");
		add(extractDataButton, "grow");

		add(progressPanel, "grow, span, wrap, gap top 8");
		add(consoleScrollPane, "grow, span");

		pack();
		setResizable(false);
		setLocationRelativeTo(null);
		setVisible(true);

		Logger.addListener(consoleListener);
	}

	public interface EditorWork
	{
		void execute() throws Exception;
	}

	private class EditorWorker extends SwingWorker<Boolean, String>
	{
		private final EditorWork work;

		private EditorWorker(EditorWork work)
		{
			this.work = work;

			setVisible(false);
			execute();
		}

		@Override
		protected Boolean doInBackground()
		{
			try {
				work.execute();
			}
			catch (Throwable t) {
				LoadingBar.dismiss();
				displayStackTrace(t);
			}
			return true;
		}

		@Override
		protected void done()
		{
			setVisible(true);
		}
	}

	private void action_openMapEditor()
	{
		new EditorWorker(() -> {
			MapEditor editor = new MapEditor(true);
			editor.launch();
		});
	}

	private void action_openSpriteEditor()
	{
		new EditorWorker(() -> {
			BaseEditor editor = new SpriteEditor();
			editor.launch();
		});
	}

	private void action_openMessageEditor()
	{
		new EditorWorker(() -> {
			MessageEditor editor = new MessageEditor();
			editor.launch();
		});
	}

	private void action_openGlobalsEditor()
	{
		new EditorWorker(() -> {
			CountDownLatch editorClosedSignal = new CountDownLatch(1);
			new GlobalsEditor(editorClosedSignal);
			editorClosedSignal.await();
		});
	}

	private void action_openWorldMapEditor()
	{
		new EditorWorker(() -> {
			BaseEditor editor = new WorldMapEditor();
			editor.launch();
		});
	}

	private void action_openImageEditor()
	{
		new EditorWorker(() -> {
			BaseEditor editor = new ImageEditor();
			editor.launch();
		});
	}

	private void action_openThemesMenu()
	{
		new EditorWorker(() -> {
			CountDownLatch editorClosedSignal = new CountDownLatch(1);
			new ThemesEditor(editorClosedSignal);
			editorClosedSignal.await();
		});
	}

	private void action_extractMapData()
	{
		new EditorWorker(() -> {
			if (!Environment.projectConfig.getBoolean(Options.ExtractedMapData)) {
				int choice = SwingUtils.getConfirmDialog()
					.setTitle("Extraction Warning")
					.setMessage("This action will modify the source files of almost every map.",
						"Consider creating a backup or committing any changes before proceeding.",
						"Are you ready to begin extracting?")
					.setMessageType(JOptionPane.WARNING_MESSAGE)
					.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
					.choose();

				if (choice == JOptionPane.YES_OPTION) {
					Logger.log("Extracting map data...", Priority.MILESTONE);
					Extractor.extractAll();

					SwingUtils.getMessageDialog()
						.setTitle("All Data Extracted")
						.setMessage("Complete!")
						.setMessageType(JOptionPane.PLAIN_MESSAGE)
						.show();

					Environment.projectConfig.setBoolean(Options.ExtractedMapData, true);
					Environment.projectConfig.saveConfigFile();
				}
			}
			else {
				SwingUtils.getWarningDialog()
					.setTitle("Data Already Extracted")
					.setMessage("Map data has already been extracted for this project.")
					.show();
			}
		});
	}

	private void action_captureThumbnails()
	{
		new EditorWorker(() -> {
			Logger.log("Capturing missing map thumbnails...", Priority.MILESTONE);
			MapEditor editor = null;

			try {
				editor = new MapEditor(false);

				for (File f : AssetManager.getMapSources()) {
					String mapName = FilenameUtils.getBaseName(f.getName());
					File thumbFile = new File(PROJ_THUMBNAIL + mapName + ".jpg");
					if (thumbFile.exists())
						continue;
					Logger.log("Capturing thumbnail for " + mapName + "...", Priority.MILESTONE);
					editor.generateThumbnail(f, thumbFile);
				}
			}
			catch (Exception e) {
				Logger.printStackTrace(e);
			}
			finally {
				if (editor != null)
					editor.shutdownThumbnail();
			}
		});
	}

	public static void handleEarlyCrash(Throwable e)
	{
		if (!Environment.isCommandLine()) {
			Toolkit.getDefaultToolkit().beep();
			StackTraceDialog.display(e, null);
		}
		System.exit(-1);
	}

	public static void displayStackTrace(Throwable e)
	{
		displayStackTrace(e, null);
	}

	public static void displayStackTrace(Throwable e, File log)
	{
		Logger.printStackTrace(e);

		if (!Environment.isCommandLine()) {
			SwingUtilities.invokeLater(() -> {
				Toolkit.getDefaultToolkit().beep();
				StackTraceDialog.display(e, log);
			});
		}
	}

	public static void openTextFile(File file)
	{
		if (file == null)
			return;

		try {
			Desktop.getDesktop().open(file);
		}
		catch (IOException openDefaultIOE) {
			try {
				if (Environment.isWindows()) {
					Runtime rs = Runtime.getRuntime();
					rs.exec("notepad " + file.getCanonicalPath());
				}
				else {
					openDefaultIOE.printStackTrace();
				}
			}
			catch (IOException nativeIOE) {
				nativeIOE.printStackTrace();
			}
		}
	}

	private static void runCommandLine(String[] args)
	{
		for (int i = 0; i < args.length; i++) {
			switch (args[i].toUpperCase()) {
				case "-VERSION":
					System.out.println("VERSION=" + Environment.getVersionString());
					break;

				case "-COMPILESHAPE":
				case "-COMPILEHIT":
				case "-GENERATESCRIPT":
				case "-COMPILEMAP":
					if (args.length > i + 1) {
						String mapName = args[i + 1];
						AssetHandle mapAsset = AssetManager.getMap(mapName);

						if (mapAsset == null) {
							Logger.logfError("Cannot find map '%s'!", mapName);
							break;
						}

						Map map = Map.loadMap(mapAsset);
						try {
							if (args[i].equalsIgnoreCase("-CompileMap")) {
								new GeometryCompiler(map);
								new CollisionCompiler(map);
							}
							else if (args[i].equalsIgnoreCase("-CompileShape")) {
								new GeometryCompiler(map);
							}
							else if (args[i].equalsIgnoreCase("-CompileHit")) {
								new CollisionCompiler(map);
							}
							else if (args[i].equalsIgnoreCase("-GenerateScript")) {
								new ScriptGenerator(map);
							}
							else {
								throw new IllegalStateException();
							}
						}
						catch (BuildException | IOException | InvalidInputException e) {
							Logger.printStackTrace(e);
						}

						i++;
					}
					else
						Logger.logfError("%s expects a mapName argument!", args[i]);
					break;

				case "-COMPILEMAPS":
					try {
						File buildDir = AssetManager.getMapBuildDir();
						for (AssetHandle ah : AssetManager.getMapSources()) {
							// get existing compiled binaries
							String mapName = Map.deriveName(ah);
							File binShape = new File(buildDir, mapName + "_shape.bin");
							File binHit = new File(buildDir, mapName + "_hit.bin");

							// check if the map source is newer
							boolean buildShape = !binShape.exists() || binShape.lastModified() < ah.lastModified();
							boolean buildHit = !binHit.exists() || binHit.lastModified() < ah.lastModified();

							if (!buildShape && !buildHit) {
								continue;
							}

							try {
								Map map = Map.loadMap(ah);
								if (buildShape) {
									new GeometryCompiler(map);
								}
								if (buildHit) {
									new CollisionCompiler(map);
								}
							}
							catch (IOException | BuildException e) {
								Logger.printStackTrace(e);
							}
						}
					}
					catch (IOException e) {
						Logger.printStackTrace(e);
					}
					break;

				default:
					Logger.logfError("Unrecognized command line arg: ", args[i]);
			}
		}
	}

	private static final void trySetIcon(AbstractButton button, ExpectedAsset asset)
	{
		if (!(new File(Directories.getDumpPath())).exists()) {
			Logger.log("Dump directory could not be found.");
			SwingUtils.addBorderPadding(button);
			return;
		}

		ImageIcon imageIcon;

		try {
			imageIcon = new ImageIcon(ImageIO.read(asset.getFile()));
		}
		catch (IOException e) {
			Logger.logError("Exception while reading icon " + asset.getPath());
			SwingUtils.addBorderPadding(button);
			return;
		}

		int size = 24;

		Image image = imageIcon.getImage().getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH); // scale it the smooth way
		imageIcon = new ImageIcon(image);

		button.setIcon(imageIcon);
		button.setIconTextGap(24);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setVerticalTextPosition(SwingConstants.CENTER);
		button.setHorizontalTextPosition(SwingConstants.RIGHT);
	}

	/**
	 * @return positive = a later than b, negative = b later than a, 0 = equal
	 */
	public static int compareVersionStrings(String a, String b)
	{
		int[] avals, bvals;

		avals = tokenizeVersionString(a);
		bvals = tokenizeVersionString(b);

		for (int i = 0; i < avals.length; i++) {
			if (avals[i] > bvals[i])
				return 1;
			else if (avals[i] < bvals[i])
				return -1;
		}

		return 0;
	}

	private static int[] tokenizeVersionString(String ver)
	{
		if (ver == null || !ver.contains("."))
			throw new IllegalArgumentException("Invalid version string: " + ver);

		String[] tokens = ver.split("\\.");
		int[] values = new int[3];

		for (int i = 0; i < 3; i++) {
			try {
				values[i] = Integer.parseInt(tokens[i]);
			}
			catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid version string: " + ver);
			}
		}

		return values;
	}
}
