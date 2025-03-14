package game.map.editor.ui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.assimp.Assimp;

import app.Environment;
import app.StarRodFrame;
import app.StarRodMain;
import app.SwingUtils;
import app.SwingUtils.OpenDialogCounter;
import assets.AssetHandle;
import assets.AssetManager;
import assets.ui.SelectBackgroundDialog;
import assets.ui.SelectMapDialog;
import assets.ui.SelectTexDialog;
import common.EditorCanvas;
import common.commands.AbstractCommand;
import game.ProjectDatabase;
import game.map.Map;
import game.map.Map.SetBackground;
import game.map.Map.ToggleBackground;
import game.map.Map.ToggleStage;
import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.editor.EditorShortcut;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.EditorMode;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.MapPreferencesPanel;
import game.map.editor.PaintManager;
import game.map.editor.commands.ChangeTextureArchive;
import game.map.editor.commands.CreateObjects;
import game.map.editor.render.PreviewGeneratorPrimitive;
import game.map.editor.render.TextureManager;
import game.map.editor.selection.Selection;
import game.map.editor.selection.SelectionManager;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.EditPannerDialog;
import game.map.editor.ui.dialogs.GenerateFromPathsDialog;
import game.map.editor.ui.dialogs.GenerateFromTrianglesDialog;
import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.editor.ui.dialogs.MarkerOptionsPanel;
import game.map.editor.ui.dialogs.OpenFileChooser;
import game.map.editor.ui.dialogs.SaveFileChooser;
import game.map.editor.ui.dialogs.SetPositionPanel;
import game.map.editor.ui.dialogs.TransformSelectionPanel;
import game.map.editor.ui.dialogs.TransformSelectionPanel.TransformType;
import game.map.editor.ui.dialogs.UVOptionsPanel;
import game.map.editor.ui.info.DisplayListPanel.AddTriangles;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.impex.ImportDialog;
import game.map.impex.ImportDialog.ImportDialogResult;
import game.map.marker.Marker;
import game.map.shape.Model;
import game.map.shape.TexturePanner;
import game.map.shape.TransformMatrix;
import game.map.shape.TriangleBatch;
import game.map.shape.UVGenerator;
import game.map.tree.MapObjectNode;
import game.texture.ModelTexture;
import net.miginfocom.swing.MigLayout;
import renderer.shaders.postprocess.PostProcessFX;
import util.Logger;
import util.Logger.Listener;
import util.Logger.Message;
import util.ui.DialogResult;

public final class SwingGUI extends StarRodFrame implements ActionListener, Logger.Listener, IShutdownListener
{
	public static final int SIDE_PANEL_WIDTH = 400;

	private final MapEditor editor;

	// At the top level, the GUI has:
	// (1) a menubar along the top of the screen
	// (2) glCanvas  -- handled by LWJGL in the Editor class
	// (3) sidePanel -- on the right, created via createSidePanel()
	// (4) a small ribbon along the bottom of the screen woth several JLabels
	// The sidePanel is entirely filled by a JTabbedPane that has a tab
	// for different EditorStates: modify, texture, and vertex paint.

	private volatile boolean closeRequested = false;
	private volatile OpenDialogCounter openDialogCount = new OpenDialogCounter();
	private volatile List<JPopupMenu> popups = new LinkedList<>();

	private HashMap<String, GuiCommand> commandMap;

	public JCheckBoxMenuItem hasBackgroundCheckbox;

	public JCheckBoxMenuItem isStageCheckbox;

	private boolean ignoreSelectionRadioButtonChanges = false;
	private JRadioButton objectRadioButton;
	private JRadioButton triangleRadioButton;
	public JRadioButton vertexRadioButton;
	public JRadioButton pointRadioButton;

	private boolean ignoreTabChanges = false;
	private JTabbedPane tabbedPane;
	private MapObjectPanel objectPanel;

	private OpenFileChooser importFileChooser;
	private SaveFileChooser exportFileChooser;

	private Listener logListener;

	private static final double DEFAULT_SPLIT_FACTOR = 0.50;
	private boolean infoPanelHidden = false;
	private JSplitPane objectSplitPane;

	private JLabel infoLabel;
	private JLabel extraInfoLabel;
	private JLabel snapModeLabel;
	private JLabel gridSizeLabel;
	private JLabel fpsLabel;

	private int desktopX;
	private int desktopY;

	private JMenu recentMapsMenu;

	private static final String MENU_BAR_SPACING = "    ";
	private static final Dimension menuItemDimension = new Dimension(200, 24);

	private JPanel texturePreviewPanel;
	private JScrollPane textureScrollPane;

	private TextureInfoPanel currentTexturePanel;
	private UVOptionsPanel uvOptionsPanel;

	private GeneratePrimitiveOptionsDialog generatePrimitiveDialog;
	private GenerateFromTrianglesDialog generateFromTrianglesDialog;
	private GenerateFromPathsDialog generateFromPathsDialog;
	private EditPannerDialog editTexPannerDialog;

	private TransformSelectionPanel transformSelectionPanel;

	// singleton
	private static SwingGUI instance = null;

	public static SwingGUI instance()
	{
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
		Logger.removeListener(logListener);
	}

	public SwingGUI(MapEditor editor, EditorCanvas glCanvas, File logFile)
	{
		if (instance != null)
			throw new IllegalStateException("Only one EditorGUI may exist at a time!");
		instance = this;
		editor.registerOnShutdown(this);

		this.editor = editor;

		Dimension displaySize = Toolkit.getDefaultToolkit().getScreenSize();
		desktopX = (int) displaySize.getWidth();
		desktopY = (int) displaySize.getHeight();
		setMinimumSize(new Dimension(800, 600));
		setPreferredSize(new Dimension(desktopX * 4 / 5, desktopY * 4 / 5));

		// handle window closing
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				openDialogCount.increment();
				closeRequested = (!editor.map.modified || promptForSave()) && (!ProjectDatabase.SpriteShading.modified || promptSaveShading());
				if (!closeRequested)
					openDialogCount.decrement();
			}
		});

		// set file chooser behavior
		File mapDir = Environment.getProjectDirectory();

		importFileChooser = new OpenFileChooser(mapDir, "Import Geometry", "Importables", "prefab", "obj", "fbx", "gltf", "glb");
		exportFileChooser = new SaveFileChooser(mapDir, "Export Geometry", null, "prefab", "obj");

		commandMap = new HashMap<>();
		for (GuiCommand cmd : GuiCommand.values())
			commandMap.put(cmd.name(), cmd);

		createMenu();

		glCanvas.setMinimumSize(new Dimension(400, 300));
		glCanvas.setPreferredSize(new Dimension(desktopX, desktopY));

		infoLabel = new JLabel("");
		snapModeLabel = new JLabel("", SwingConstants.LEFT);
		gridSizeLabel = new JLabel("", SwingConstants.RIGHT);
		extraInfoLabel = new JLabel("");
		fpsLabel = new JLabel("", SwingConstants.RIGHT);

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

		JButton openLog = new JButton("Open Log");
		SwingUtils.setFontSize(openLog, 10);
		openLog.addActionListener((e) -> {
			boolean success = false;

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

			if (!success) {
				int choice = SwingUtils.getOptionDialog()
					.setParent(this)
					.setCounter(openDialogCount)
					.setTitle("Editor Log")
					.setMessage(logScrollPane)
					.setMessageType(JOptionPane.PLAIN_MESSAGE)
					.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
					.setIcon(Environment.ICON_DEFAULT)
					.setOptions("Copy to Clipboard")
					.choose();

				if (choice == 0) {
					StringSelection stringSelection = new StringSelection(logTextArea.getText());
					Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
					cb.setContents(stringSelection, null);
				}
			}
		});

		//	PlatformDefaults.setLogicalPixelBase( PlatformDefaults.BASE_REAL_PIXEL );

		setLayout(new MigLayout());
		add(glCanvas, "grow, push");

		add(createSidePanel(), "w " + SIDE_PANEL_WIDTH + "!, growy, wrap");
		//	add(createSidePanel(), "w 400:36%:36%, growy, wrap"); //TODO test this on 4k

		//		JSplitPane sideSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, glCanvas, createSidePanel());
		//		sideSplitPane.setOneTouchExpandable(true);
		//		sideSplitPane.setDividerLocation(0.8);
		//		add(sideSplitPane, "span, grow, wrap");

		add(openLog, "split 4");
		add(infoLabel, "growx, pushx, h 16!, gapleft 16");
		add(snapModeLabel);
		add(gridSizeLabel, "w 120!");
		add(extraInfoLabel, "growx, split 2, gapleft 16");
		add(fpsLabel, "w 100!, right");

		pack();
	}

	public void setMap(Map m)
	{
		tabbedPane.setSelectedIndex(0);

		loadTexturePreviews();
		loadMapObjects();

		objectPanel.setMap(m);

		ScriptManager.instance().setMap(m);

		hasBackgroundCheckbox.setSelected(m.hasBackground);
		isStageCheckbox.setSelected(m.isStage);
	}

	public void setEditorMode(MapEditor.EditorMode oldMode, MapEditor.EditorMode newMode)
	{
		SwingUtilities.invokeLater(() -> {
			// set the new tab
			int index = 0;
			switch (newMode) {
				case Modify:
					index = 0;
					break;
				case Texture:
					index = 1;
					break;
				case VertexPaint:
					index = 2;
					break;
				case Scripts:
					index = 3;
					break;
				default:
					index = 0;
					break;
			}

			ignoreTabChanges = true;
			tabbedPane.setSelectedIndex(index);
			ignoreTabChanges = false;
		});
	}

	public void showGUI()
	{
		setLightSetsVisible(false);
		setVisible(true);
		setExtendedState(Frame.MAXIMIZED_BOTH);
		SwingUtilities.invokeLater(() -> {
			objectSplitPane.setDividerLocation(DEFAULT_SPLIT_FACTOR);
		});
	}

	public void destroyGUI()
	{
		setVisible(false);
		dispose();
	}

	public boolean isModalDialogOpen()
	{
		return !openDialogCount.isZero();
	}

	public OpenDialogCounter getDialogCounter()
	{
		return openDialogCount;
	}

	public void registerPopupMenu(JPopupMenu menu)
	{
		popups.add(menu);
	}

	public void closePopupMenus()
	{
		for (JPopupMenu menu : popups) {
			if (menu.isVisible())
				menu.setVisible(false);
		}
	}

	public boolean isCloseRequested()
	{
		return closeRequested;
	}

	public boolean promptForSave()
	{
		int choice = SwingUtils.getConfirmDialog()
			.setParent(this)
			.setCounter(openDialogCount)
			.setTitle("Warning")
			.setMessage("Unsaved changes will be lost!", "Would you like to save now?")
			.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
			.choose();

		switch (choice) {
			case JOptionPane.YES_OPTION:
				editor.action_SaveMap();
			case JOptionPane.NO_OPTION:
				break;
			case JOptionPane.CANCEL_OPTION:
				closeRequested = false;
				return false;
		}

		return true;
	}

	private boolean promptSaveShading()
	{
		int choice = SwingUtils.getConfirmDialog()
			.setParent(this)
			.setCounter(openDialogCount)
			.setTitle("Warning")
			.setMessage("Sprite shading data was changed!", "Would you like to save now?")
			.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
			.choose();

		switch (choice) {
			case JOptionPane.YES_OPTION:
				editor.action_SaveShading();
			case JOptionPane.NO_OPTION:
				break;
			case JOptionPane.CANCEL_OPTION:
				closeRequested = false;
				return false;
		}

		return true;
	}

	/**
	 * Creates the menu bar and installs it in the frame.
	 */
	private void createMenu()
	{
		JMenuBar menuBar = new JMenuBar();
		this.setJMenuBar(menuBar);
		addFileMenu(menuBar);
		addEditorMenu(menuBar);
		addMapMenu(menuBar);
		addSelectionMenu(menuBar);
		addViewportsMenu(menuBar);
		addPlayMenu(menuBar);
		if (editor.debugModeEnabled())
			addDebugMenu(menuBar);
	}

	public void setRecentMaps(Deque<String> recentMaps)
	{
		recentMapsMenu.removeAll();

		int i = 0;
		for (String s : recentMaps) {
			// do not list current map
			if (i++ == 0)
				continue;

			JMenuItem item = new JMenuItem(s);
			item.addActionListener(evt -> {
				prompt_OpenMap(s);
			});
			recentMapsMenu.add(item);
		}
	}

	private void addFileMenu(JMenuBar menuBar)
	{
		JMenuItem item;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "File" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Open");
		addButtonCommand(item, GuiCommand.OPEN_FILE);
		menu.add(item);

		recentMapsMenu = new JMenu("Recent");
		recentMapsMenu.getPopupMenu().setLightWeightPopupEnabled(false);
		menu.add(recentMapsMenu);

		menu.addSeparator();

		item = new JMenuItem("Save");
		EditorShortcut.SAVE.bindMenuItem(editor, item);
		menu.add(item);

		item = new JMenuItem("Save As...");
		addButtonCommand(item, GuiCommand.SAVE_AS);
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Import");
		addButtonCommand(item, GuiCommand.PROMPT_IMPORT);
		menu.add(item);

		item = new JMenuItem("Export");
		addButtonCommand(item, GuiCommand.PROMPT_EXPORT);
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Switch Tools");
		EditorShortcut.SWITCH.bindMenuItem(editor, item);
		menu.add(item);

		item = new JMenuItem("Exit");
		EditorShortcut.QUIT.bindMenuItem(editor, item);
		menu.add(item);

		item.setPreferredSize(menuItemDimension);
	}

	private void addEditorMenu(JMenuBar menuBar)
	{
		JMenuItem item;
		JCheckBoxMenuItem checkbox;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Editor" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Undo");
		EditorShortcut.UNDO.bindMenuItem(editor, item);
		menu.add(item);
		item.setPreferredSize(menuItemDimension);

		item = new JMenuItem("Redo");
		EditorShortcut.REDO.bindMenuItem(editor, item);
		menu.add(item);

		menu.addSeparator();

		JMenu gridMenu = new JMenu("Grid");
		gridMenu.getPopupMenu().setLightWeightPopupEnabled(false);
		menu.add(gridMenu);

		checkbox = new JCheckBoxMenuItem("Enable Grid");
		EditorShortcut.TOGGLE_GRID.bindMenuCheckbox(editor, checkbox);
		gridMenu.add(checkbox);
		checkbox.setPreferredSize(menuItemDimension);

		checkbox = new JCheckBoxMenuItem("Use Decimal Grid");
		EditorShortcut.TOGGLE_GRID_TYPE.bindMenuCheckbox(editor, checkbox);
		gridMenu.add(checkbox);

		item = new JCheckBoxMenuItem("Increase Grid Size");
		EditorShortcut.INCREASE_GRID_POWER.bindMenuItem(editor, item);
		gridMenu.add(item);

		item = new JCheckBoxMenuItem("Decrease Grid Size");
		EditorShortcut.DECREASE_GRID_POWER.bindMenuItem(editor, item);
		gridMenu.add(item);

		JMenu snapMenu = new JMenu("Snap");
		snapMenu.getPopupMenu().setLightWeightPopupEnabled(false);
		menu.add(snapMenu);

		checkbox = new JCheckBoxMenuItem("Translation");
		EditorShortcut.SNAP_TRANSLATION.bindMenuCheckbox(editor, checkbox);
		snapMenu.add(checkbox);
		checkbox.setPreferredSize(menuItemDimension);

		checkbox = new JCheckBoxMenuItem("Rotation");
		EditorShortcut.SNAP_ROTATION.bindMenuCheckbox(editor, checkbox);
		snapMenu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Scale");
		EditorShortcut.SNAP_SCALE.bindMenuCheckbox(editor, checkbox);
		snapMenu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Scale to Grid");
		EditorShortcut.SNAP_SCALE_GRID.bindMenuCheckbox(editor, checkbox);
		snapMenu.add(checkbox);

		JMenu vertexSnapMenu = new JMenu("Vertex Snap");
		vertexSnapMenu.getPopupMenu().setLightWeightPopupEnabled(false);
		menu.add(vertexSnapMenu);

		checkbox = new JCheckBoxMenuItem("Enable");
		EditorShortcut.VERTEX_SNAP.bindMenuCheckbox(editor, checkbox);
		vertexSnapMenu.add(checkbox);
		checkbox.setPreferredSize(menuItemDimension);

		checkbox = new JCheckBoxMenuItem("Like Objects Only");
		EditorShortcut.VERTEX_SNAP_LIMIT.bindMenuCheckbox(editor, checkbox);
		vertexSnapMenu.add(checkbox);
		checkbox.setPreferredSize(menuItemDimension);

		menu.addSeparator();

		item = new JMenuItem("View Shortcuts");
		addButtonCommand(item, GuiCommand.SHOW_SHORTCUTS);
		menu.add(item);

		item = new JMenuItem("Preferences");
		addButtonCommand(item, GuiCommand.SHOW_EDITOR_PREFERENCES);
		menu.add(item);
	}

	private void addMapMenu(JMenuBar menuBar)
	{
		JMenu menu = new JMenu(MENU_BAR_SPACING + "Map" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);
		JMenuItem item;

		item = new JMenuItem("Build Geometry");
		addButtonCommand(item, GuiCommand.COMPILE_SHAPE);
		menu.add(item);

		item = new JMenuItem("Build Collision");
		addButtonCommand(item, GuiCommand.COMPILE_COLLISION);
		menu.add(item);

		item = new JMenuItem("Generate Script");
		addButtonCommand(item, GuiCommand.GENERATE_SCRIPT);
		menu.add(item);

		menu.addSeparator();

		hasBackgroundCheckbox = new JCheckBoxMenuItem("Has Background");
		hasBackgroundCheckbox.setSelected(false);
		hasBackgroundCheckbox.addActionListener((e) -> {
			editor.executeNextFrame(new ToggleBackground());
		});
		menu.add(hasBackgroundCheckbox);

		isStageCheckbox = new JCheckBoxMenuItem("Is Battle Stage");
		isStageCheckbox.setSelected(false);
		isStageCheckbox.addActionListener((e) -> {
			editor.executeNextFrame(new ToggleStage());
		});
		menu.add(isStageCheckbox);

		item = new JMenuItem("Add Stage Markers");
		item.addActionListener((e) -> {
			List<Marker> markers = editor.map.getStageMarkers();
			MapEditor.execute(new CreateObjects(markers));
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Change Texture Archive");
		item.addActionListener((e) -> {
			prompt_LoadTextureArchive();
		});
		menu.add(item);

		item = new JMenuItem("Change Background");
		item.addActionListener((e) -> {
			prompt_ChangeBackground();
		});
		menu.add(item);

		item.setPreferredSize(menuItemDimension);
	}

	private void addSelectionMenu(JMenuBar menuBar)
	{
		JMenu menu = new JMenu(MENU_BAR_SPACING + "Selection" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);
		JMenuItem item;

		item = new JMenuItem("Translate");
		addButtonCommand(item, GuiCommand.SHOW_TRANSLATE_SELECTION_DIALOG);
		menu.add(item);

		item = new JMenuItem("Rotate");
		addButtonCommand(item, GuiCommand.SHOW_ROTATE_SELECTION_DIALOG);
		menu.add(item);

		item = new JMenuItem("Scale");
		addButtonCommand(item, GuiCommand.SHOW_SCALE_SELECTION_DIALOG);
		menu.add(item);

		item = new JMenuItem("Resize");
		addButtonCommand(item, GuiCommand.SHOW_RESIZE_SELECTION_DIALOG);
		menu.add(item);

		item = new JMenuItem("Flip");
		addButtonCommand(item, GuiCommand.SHOW_FLIP_SELECTION_DIALOG);
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Save as Prefab");
		addButtonCommand(item, GuiCommand.SAVE_PREFAB);
		menu.add(item);

		menu.addSeparator();

		JCheckBoxMenuItem checkbox = new JCheckBoxMenuItem("Transform Points with Markers");
		EditorShortcut.MOVE_MARKER_POINTS.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		item.setPreferredSize(menuItemDimension);
	}

	private void addViewportsMenu(JMenuBar menuBar)
	{
		JMenuItem item;
		JCheckBoxMenuItem checkbox;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Viewports" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Reset Layout");
		EditorShortcut.RESET_LAYOUT.bindMenuItem(editor, item);
		menu.add(item);
		item.setPreferredSize(menuItemDimension);

		item = new JMenuItem("Center on Selection");
		EditorShortcut.CENTER_VIEW.bindMenuItem(editor, item);
		menu.add(item);

		menu.addSeparator();

		checkbox = new JCheckBoxMenuItem("Force In-Game Aspect Ratio");
		EditorShortcut.USE_GAME_ASPECT_RATIO.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Use Map Camera Settings");
		EditorShortcut.USE_MAP_CAM_PROPERTIES.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Use Map Background Color");
		EditorShortcut.USE_MAP_BG_COLOR.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Use Geometry Flags");
		EditorShortcut.USE_GEOMETRY_FLAGS.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Use Texture Filtering");
		EditorShortcut.USE_FILTERING.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Use Texture LODs");
		EditorShortcut.USE_TEXTURE_LOD.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		menu.addSeparator();

		checkbox = new JCheckBoxMenuItem("Show Models");
		EditorShortcut.SHOW_MODELS.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Show Colliders");
		EditorShortcut.SHOW_COLLIDERS.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Show Zones");
		EditorShortcut.SHOW_ZONES.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Show Markers");
		EditorShortcut.SHOW_MARKERS.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		menu.addSeparator();

		checkbox = new JCheckBoxMenuItem("Color Colliders by Flags");
		EditorShortcut.USE_COLLIDER_COLORS.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Show Collision for Entities");
		EditorShortcut.SHOW_ENTITY_COLLISION.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Show Transform Gizmo");
		EditorShortcut.SHOW_GIZMO.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Show Normals");
		EditorShortcut.SHOW_NORMALS.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Show Bounding Boxes");
		EditorShortcut.SHOW_AABB.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Show Axes");
		EditorShortcut.SHOW_AXES.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		menu.addSeparator();

		JMenu shaders = new JMenu("Post Processing Shaders");
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menu.add(shaders);

		ButtonGroup shadersGroup = new ButtonGroup();
		for (PostProcessFX effect : PostProcessFX.values()) {
			JRadioButtonMenuItem button = new JRadioButtonMenuItem(effect.toString());
			shaders.add(button);
			shadersGroup.add(button);
			if (effect == PostProcessFX.NONE)
				button.setSelected(true);
			button.addActionListener((e) -> {
				editor.doNextFrame(() -> {
					editor.postProcessFX = effect;
				});
			});

		}
	}

	private void addPlayMenu(JMenuBar menuBar)
	{
		JMenu menu = new JMenu(MENU_BAR_SPACING + "Play in Editor" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		JCheckBoxMenuItem checkbox;

		checkbox = new JCheckBoxMenuItem("Simulate");
		EditorShortcut.PLAY_IN_EDITOR_TOGGLE.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Ignore Hidden Colliders");
		EditorShortcut.PIE_IGNORE_HIDDEN_COL.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Ignore Hidden Zones");
		EditorShortcut.PIE_IGNORE_HIDDEN_ZONE.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Show Current Zone Lines");
		EditorShortcut.PIE_SHOW_ACTIVE_CAMERA.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);

		checkbox = new JCheckBoxMenuItem("Interactive Map Exits");
		EditorShortcut.PIE_ENABLE_MAP_EXITS.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);
	}

	private void addDebugMenu(JMenuBar menuBar)
	{
		JMenu menu = new JMenu(MENU_BAR_SPACING + "Debug" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		JCheckBoxMenuItem checkbox;

		checkbox = new JCheckBoxMenuItem("Show Light Sets");
		EditorShortcut.DEBUG_TOGGLE_LIGHT_SETS.bindMenuCheckbox(editor, checkbox);
		menu.add(checkbox);
	}

	private static void createTab(JTabbedPane tabs, String name, Container contents)
	{
		JLabel lbl = SwingUtils.getLabel(name, 12);
		lbl.setPreferredSize(new Dimension(60, 20));
		lbl.setHorizontalAlignment(SwingConstants.CENTER);

		tabs.addTab(null, contents);
		tabs.setTabComponentAt(tabs.getTabCount() - 1, lbl);
	}

	private JPanel createSidePanel()
	{
		JPanel sidePanelContainer = new JPanel();
		tabbedPane = new JTabbedPane();
		JPanel paintVertexTab = PaintManager.createPaintVertexTab(this);

		JScrollPane paintScrollPane = new JScrollPane(paintVertexTab);
		paintScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		paintScrollPane.setBorder(null);

		createTab(tabbedPane, "Objects", createTransformTab());
		createTab(tabbedPane, "Texture", createTextureTab());
		createTab(tabbedPane, "Paint", paintScrollPane);
		createTab(tabbedPane, "Scripts", ScriptManager.instance().createScriptsTab());

		tabbedPane.addChangeListener((e) -> {
			if (ignoreTabChanges)
				return;

			JTabbedPane pane = (JTabbedPane) e.getSource();
			EditorMode mode;

			switch (pane.getSelectedIndex()) {
				case 0:
					mode = EditorMode.Modify;
					break;
				case 1:
					mode = EditorMode.Texture;
					break;
				case 2:
					mode = EditorMode.VertexPaint;
					break;
				case 3:
					mode = EditorMode.Scripts;
					break;
				default:
					mode = EditorMode.Modify;
					break; // workaround to initialize mode
			}

			editor.executeNextFrame(editor.new ChangeMode(mode));
		});

		sidePanelContainer.setLayout(new MigLayout("fill, ins 0"));
		sidePanelContainer.add(tabbedPane, "grow");
		//	sidePanelContainer.setPreferredSize(new Dimension(SIDE_PANEL_WIDTH, sidePanelContainer.getPreferredSize().height));

		return sidePanelContainer;
	}

	private JPanel createTransformTab()
	{
		objectRadioButton = new JRadioButton("Objects");
		addButtonCommand(objectRadioButton, GuiCommand.SELECT_OBJECTS);
		objectRadioButton.setSelected(true);

		triangleRadioButton = new JRadioButton("Triangles");
		addButtonCommand(triangleRadioButton, GuiCommand.SELECT_TRIANGLES);

		vertexRadioButton = new JRadioButton("Vertices");
		addButtonCommand(vertexRadioButton, GuiCommand.SELECT_VERTICES);

		pointRadioButton = new JRadioButton("Points");
		addButtonCommand(pointRadioButton, GuiCommand.SELECT_POINTS);

		ButtonGroup bg = new ButtonGroup();
		bg.add(objectRadioButton);
		bg.add(triangleRadioButton);
		bg.add(vertexRadioButton);
		bg.add(pointRadioButton);

		JPanel selectionTypePanel = new JPanel(new MigLayout("fill"));

		selectionTypePanel.add(SwingUtils.getLabel("Selection mode:", 12), "growx, wrap, gapbottom 4");
		selectionTypePanel.add(objectRadioButton, "sg selection_type, growx, split 4");
		selectionTypePanel.add(triangleRadioButton, "sg selection_type, growx");
		selectionTypePanel.add(vertexRadioButton, "sg selection_type, growx");
		selectionTypePanel.add(pointRadioButton, "sg selection_type, growx, wrap");

		// create dialogs
		uvOptionsPanel = new UVOptionsPanel();
		transformSelectionPanel = new TransformSelectionPanel();

		Container infoPanelContainer = new JPanel(new MigLayout("fill, ins 0"));
		objectPanel = new MapObjectPanel(this, editor, infoPanelContainer);

		JPanel modifyTab = new JPanel();
		modifyTab.setLayout(new MigLayout("fill, flowy, ins 4"));
		modifyTab.add(selectionTypePanel, "grow");

		//		modifyTab.add(objectPanel, "grow, pushy");
		//		modifyTab.add(infoPanelContainer, "h 40%!, grow");

		JScrollPane infoScrollPane = new JScrollPane(infoPanelContainer);
		infoScrollPane.setBorder(null);
		infoScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		infoScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		objectSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, objectPanel, infoScrollPane);
		objectSplitPane.setContinuousLayout(true);
		objectSplitPane.setDividerSize(16);

		modifyTab.add(objectSplitPane, "grow, pushy");

		return modifyTab;
	}

	public void setSelectionMode(SelectionManager.SelectionMode mode)
	{
		ignoreSelectionRadioButtonChanges = true;

		switch (mode) {
			case OBJECT:
				objectRadioButton.setSelected(true);
				break;
			case TRIANGLE:
				triangleRadioButton.setSelected(true);
				break;
			case VERTEX:
				vertexRadioButton.setSelected(true);
				break;
			case POINT:
				pointRadioButton.setSelected(true);
				break;
		}

		ignoreSelectionRadioButtonChanges = false;
	}

	public MapObjectType getObjectTab()
	{
		switch (objectPanel.getSelectedIndex()) {
			case 0:
				return MapObjectType.MODEL;
			case 1:
				return MapObjectType.COLLIDER;
			case 2:
				return MapObjectType.ZONE;
			case 3:
				return MapObjectType.MARKER;
			default:
				throw new IllegalStateException("objectPanel tab is invalid!");
		}
	}

	public void setObjectTab(MapObjectType type)
	{
		switch (type) {
			case MODEL:
				objectPanel.setSelectedIndex(0);
				break;
			case COLLIDER:
				objectPanel.setSelectedIndex(1);
				break;
			case ZONE:
				objectPanel.setSelectedIndex(2);
				break;
			case MARKER:
				objectPanel.setSelectedIndex(3);
				break;
			case EDITOR:
				throw new IllegalStateException("Cannot setObjectTab for EditorObjects!");
		}
	}

	public void syncSelectionWith(SelectionManager sm)
	{
		sm.syncGUI(objectPanel);
	}

	public void repaintObjectPanel()
	{
		objectPanel.repaint();
	}

	public void repaintVisibleTree()
	{
		objectPanel.repaintCurrentTree();
	}

	public void reloadObjectInTree(MapObject obj)
	{
		objectPanel.reload(obj);
	}

	public void toggleInfoPanel()
	{
		infoPanelHidden = !infoPanelHidden;
		objectSplitPane.setDividerLocation(infoPanelHidden ? 1.0 : DEFAULT_SPLIT_FACTOR);
	}

	public void loadMapObjects()
	{
		objectPanel.loadMapObjects();
	}

	private JPanel createTextureTab()
	{
		texturePreviewPanel = new JPanel();
		textureScrollPane = new JScrollPane(texturePreviewPanel);
		textureScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		currentTexturePanel = new TextureInfoPanel(this);
		currentTexturePanel.setTexture(null);

		JPanel textureTab = new JPanel();
		textureTab.setLayout(new MigLayout("fillx, insets 8 8 0 8"));

		textureTab.add(SwingUtils.getLabel("Selected Texture:", 14), "span, wrap");
		textureTab.add(currentTexturePanel, "span, wrap");
		textureTab.add(SwingUtils.getLabel("Available Textures:", 14), "span, wrap");
		textureTab.add(textureScrollPane, "span, grow, wrap");

		return textureTab;
	}

	public void setSelectedTexture(ModelTexture selectedTexture)
	{
		currentTexturePanel.setTexture(selectedTexture);
	}

	public void loadTexturePreviews()
	{
		texturePreviewPanel.removeAll();
		texturePreviewPanel.setLayout(new MigLayout("wrap 2, fillx"));
		texturePreviewPanel.add(new TexturePreview(editor, null), "center");

		for (ModelTexture t : TextureManager.textureList)
			texturePreviewPanel.add(new TexturePreview(editor, t), "center");

		repaint();
	}

	public void addButtonCommand(AbstractButton button, GuiCommand cmd)
	{
		button.setActionCommand(cmd.name());
		button.addActionListener(this);
	}

	/*
	 * This method lives in the Swing EDT (Event Dispatch Thread), but the main loop
	 * of the Editor class lives in a different thread (which has the GL context).
	 * Communication between these threads follows a producer-consumer pattern using
	 * anonymous EditorEvent objects or GuiCommands. During the next frame of the
	 * main loop, and pending events from the GUI and handled.
	 *
	 * User input from the GUI is represented by the GuiCommand enum. Certain supported
	 * GuiCommands can be forwarded directly to the Editor. This is the default behavior.
	 * For more complicated cases, we need to use EditorEvents.
	 *
	 * EditorEvents are more generalized and allow us to safely invoke public methods
	 * in the Editor thread from this class.
	 */
	@Override
	public void actionPerformed(ActionEvent e)
	{
		GuiCommand cmd = commandMap.get(e.getActionCommand());
		switch (cmd) {
			case OPEN_FILE:
				prompt_OpenMap();
				break;
			case SAVE_AS:
				prompt_SaveMapAs();
				break;

			case PROMPT_EXPORT:
				prompt_Export();
				break;
			case PROMPT_IMPORT:
				prompt_Import(null);
				break;
			case SAVE_PREFAB:
				prompt_Prefab();
				break;

			case SHOW_TRANSLATE_SELECTION_DIALOG:
				prompt_TransformSelection(TransformType.Translate);
				break;

			case SHOW_ROTATE_SELECTION_DIALOG:
				prompt_TransformSelection(TransformType.Rotate);
				break;

			case SHOW_SCALE_SELECTION_DIALOG:
				prompt_TransformSelection(TransformType.Scale);
				break;

			case SHOW_RESIZE_SELECTION_DIALOG:
				prompt_TransformSelection(TransformType.Resize);
				break;

			case SHOW_FLIP_SELECTION_DIALOG:
				prompt_TransformSelection(TransformType.Flip);
				break;

			case SHOW_CREATE_PRIMITIVE_MODEL_DIALOG:
				prompt_CreatePrimitiveObject(editor.map.modelTree.getRoot());
				break;

			case SHOW_CREATE_PRIMITIVE_COLLIDER_DIALOG:
				prompt_CreatePrimitiveObject(editor.map.colliderTree.getRoot());
				break;

			case SHOW_CREATE_PRIMITIVE_ZONE_DIALOG:
				prompt_CreatePrimitiveObject(editor.map.zoneTree.getRoot());
				break;

			case SHOW_CREATE_MARKER_DIALOG:
				prompt_CreateMarker(editor.map.markerTree.getRoot());
				break;

			case SHOW_GENERATE_UV_DIALOG:
				prompt_GenerateUV();
				break;

			case SHOW_CREATE_MODEL_FROM_DIALOG:
				prompt_GenerateFromTriangles(editor.map.modelTree.getRoot());
				break;

			case SHOW_CREATE_COLLIDER_FROM_DIALOG:
				prompt_GenerateFromTriangles(editor.map.colliderTree.getRoot());
				break;

			case SHOW_CREATE_ZONE_FROM_DIALOG:
				prompt_GenerateFromTriangles(editor.map.zoneTree.getRoot());
				break;

			case SHOW_EXTRUDE_RIBBON_MODEL_DIALOG:
				prompt_GenerateFromPaths(editor.map.modelTree.getRoot());
				break;

			case SHOW_CHOOSE_COLOR_DIALOG:
				prompt_ChooseColor();
				break;

			case SELECT_OBJECTS:
			case SELECT_TRIANGLES:
			case SELECT_VERTICES:
			case SELECT_POINTS:
				if (ignoreSelectionRadioButtonChanges)
					break;
				editor.submitGuiCommand(cmd);
				break;

			case SHOW_SHORTCUTS:
				showControls();
				break;

			case SHOW_EDITOR_PREFERENCES:
				showPreferences();
				break;

			// following commands are forwarded directly to the editor
			/*
			case SAVE_MAP:

			case COMPILE_SHAPE:
			case COMPILE_COLLISION:

			case SHOW_MODELS:
			case SHOW_COLLIDERS:
			case SHOW_ZONES:
			case SHOW_MARKERS:

			case RESET_CAMERAS:
			case RESET_LAYOUT:

			case SEPARATE_VERTS:
			case FUSE_VERTS:
			case JOIN_MODELS:
			case SPLIT_MODEL:

			case CONVERT_COLLIDER_TO_ZONE:
			case CONVERT_ZONE_TO_COLLIDER:

			case CREATE_COLLIDER_GROUP:
			case CREATE_ZONE_GROUP:

			case DEBUG_RECOMPUTE_BOUNDING_BOXES:
			 */
			default:
				editor.submitGuiCommand(cmd);
				break;
		}
	}

	public void changeMap(Map map)
	{
		if (!editor.map.modified || promptForSave()) {
			editor.doNextFrame(() -> {
				editor.action_OpenMap(map);
			});
		}
	}

	private void prompt_OpenMap()
	{
		if (!editor.map.modified || promptForSave()) {
			openDialogCount.increment();
			File mapFile = SelectMapDialog.showPrompt(this);
			openDialogCount.decrement();

			if (mapFile != null) {
				editor.doNextFrame(() -> {
					editor.action_OpenMap(mapFile);
				});
			}
		}
	}

	private void prompt_OpenMap(String mapName)
	{
		if (!editor.map.modified || promptForSave()) {
			AssetHandle ah = AssetManager.getMap(mapName);
			if (ah.exists()) {
				editor.doNextFrame(() -> {
					editor.action_OpenMap(ah);
				});
			}
		}
	}

	private void prompt_SaveMapAs()
	{
		openDialogCount.increment();
		File newMapFile = SelectMapDialog.requestNewMapFile();
		openDialogCount.decrement();

		if (newMapFile != null) {
			editor.doNextFrame(() -> {
				editor.action_SaveMapAs(newMapFile);
			});
		}
	}

	private void prompt_LoadTextureArchive()
	{
		openDialogCount.increment();
		File texFile = SelectTexDialog.showPrompt(this, editor.map.texName);
		openDialogCount.decrement();

		if (texFile != null) {
			String texName = FilenameUtils.getBaseName(texFile.getName());
			editor.doNextFrame(() -> {
				AbstractCommand cmd = new ChangeTextureArchive(texName);
				MapEditor.execute(cmd);
			});
		}
	}

	private void prompt_ChangeBackground()
	{
		openDialogCount.increment();
		File bgFile = SelectBackgroundDialog.showPrompt(this, editor.map.bgName);
		openDialogCount.decrement();

		if (bgFile != null) {
			editor.doNextFrame(() -> {
				try {
					String bgName = FilenameUtils.getBaseName(bgFile.getName());
					BufferedImage bgImage = ImageIO.read(bgFile);
					MapEditor.execute(new SetBackground(bgName, bgImage));
				}
				catch (IOException e) {
					MapEditor.instance().displayStackTrace(e, "Could not load background image!");
				}
			});
		}
	}

	public void prompt_Import(MapObjectNode<? extends MapObject> node)
	{
		openDialogCount.increment();
		ChooseDialogResult result = importFileChooser.prompt();
		openDialogCount.decrement();

		if (result != ChooseDialogResult.APPROVE)
			return;

		File in = importFileChooser.getSelectedFile();
		String ext = FilenameUtils.getExtension(in.getName()).toLowerCase();

		if ("prefab".equals(ext)) {
			editor.map.importFromFile(in, node);
		}
		else if ("obj".equals(ext)) {
			editor.map.importFromFile(in, node);
		}
		else if (Assimp.aiIsExtensionSupported(ext)) {
			openDialogCount.increment();

			ImportDialog importDialog = new ImportDialog(this, in, node != null);
			importDialog.pack();
			importDialog.setVisible(true);

			openDialogCount.decrement();

			if (importDialog.getResult() == ImportDialogResult.READY)
				editor.map.importViaAssimp(in, importDialog.getOptions(), node);
		}
		else {
			SwingUtils.getErrorDialog()
				.setParent(this)
				.setCounter(openDialogCount)
				.setTitle("Unable to Import")
				.setMessage("Unsupported file extension: " + ext)
				.setOptions("OK")
				.show();
		}

	}

	public void prompt_Export()
	{
		exportFileChooser.setFilters("Export Geometry", "obj");

		openDialogCount.increment();
		ChooseDialogResult result = exportFileChooser.prompt();
		openDialogCount.decrement();

		if (result != ChooseDialogResult.APPROVE)
			return;

		try {
			File out = exportFileChooser.getSelectedFile();
			String ext = FilenameUtils.getExtension(out.getName()).toLowerCase();

			if ("obj".equals(ext)) {
				editor.map.exportToFile(out);
			}
			else {
				SwingUtils.getErrorDialog()
					.setParent(this)
					.setCounter(openDialogCount)
					.setTitle("Unable to Export")
					.setMessage("Unsupported file extension: " + ext)
					.setOptions("OK")
					.show();

			}
		}
		catch (Exception e) {
			openDialogCount.increment();
			StarRodMain.displayStackTrace(e);
			openDialogCount.decrement();
		}
	}

	public void prompt_Prefab()
	{
		Selection<?> selection = editor.selectionManager.currentSelection;

		if (selection.isEmpty())
			return;

		exportFileChooser.setFilters("Prefabs", "prefab");

		openDialogCount.increment();
		ChooseDialogResult result = exportFileChooser.prompt();
		openDialogCount.decrement();

		if (result != ChooseDialogResult.APPROVE)
			return;

		try {
			File out = exportFileChooser.getSelectedFile();

			int count = editor.map.exportPrefab(out, false);
			if (count > 0)
				Logger.log("Exported " + count + " objects to prefab.");
		}
		catch (Exception e) {
			openDialogCount.increment();
			StarRodMain.displayStackTrace(e);
			openDialogCount.decrement();
		}
	}

	public void prompt_EditTexPanner(TexturePanner panner)
	{
		if (editTexPannerDialog == null)
			editTexPannerDialog = new EditPannerDialog(this);

		editTexPannerDialog.setPanner(panner);
		editTexPannerDialog.pack();
		editTexPannerDialog.setVisible(true);
	}

	/**
	 * Opens the 'Transform Selection' dialog with the specified transform type.
	 * Operates on the current selection (including UVs).
	 * @param transformType
	 */
	private void prompt_TransformSelection(TransformType transformType)
	{
		Selection<?> selection;

		if (editor.getEditorMode() == EditorMode.EditUVs)
			selection = editor.selectionManager.uvSelection;
		else
			selection = editor.selectionManager.currentSelection;

		if (selection.isEmpty())
			return;

		transformSelectionPanel.setTransformType(transformType);
		prompt_TransformSelection(selection);
	}

	public void prompt_TransformSelection(Selection<?> selection)
	{
		if (selection.isEmpty())
			return;

		transformSelectionPanel.setSelection(selection);

		int choice = SwingUtils.getConfirmDialog()
			.setParent(this)
			.setCounter(openDialogCount)
			.setTitle("Transform Selection")
			.setMessage(transformSelectionPanel)
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
			.choose();

		if (choice == JOptionPane.YES_OPTION) {
			final TransformMatrix m = transformSelectionPanel.createTransformMatrix();
			if (m != null) {
				editor.doNextFrame(() -> {
					editor.action_TransformSelection(m, selection);
				});
			}
		}
	}

	public int[] prompt_GetPositionVector(int x, int y, int z)
	{
		SetPositionPanel panel = new SetPositionPanel(x, y, z);

		int choice = SwingUtils.getConfirmDialog()
			.setParent(this)
			.setCounter(openDialogCount)
			.setTitle("Set Position")
			.setMessage(panel)
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
			.choose();

		if (choice == JOptionPane.YES_OPTION) {
			return panel.getVector();
		}
		else {
			return null;
		}
	}

	public void prompt_AddPrimitiveTriangles(TriangleBatch targetBatch)
	{
		prompt_CreatePrimitive(editor.map.modelTree.getRoot(), targetBatch);
	}

	public void prompt_CreatePrimitiveObject(MapObjectNode<?> parent)
	{
		prompt_CreatePrimitive(parent, null);
	}

	private void prompt_CreatePrimitive(MapObjectNode<?> parent, TriangleBatch targetBatch)
	{
		if (generatePrimitiveDialog == null) {
			generatePrimitiveDialog = new GeneratePrimitiveOptionsDialog(this,
				editor.generatePrimitivePreview, (result, batch) -> {
					if (result != DialogResult.ACCEPT)
						return;
					if (batch == null)
						return;

					PreviewGeneratorPrimitive preview = editor.generatePrimitivePreview;
					if (preview.targetBatch != null)
						MapEditor.execute(new AddTriangles(preview.targetBatch, batch.triangles));
					else
						createObjectFromBatch(batch, generatePrimitiveDialog.getTypeName(), preview.parentObj);
				});

			generatePrimitiveDialog.setTitle(GeneratePrimitiveOptionsDialog.FRAME_TITLE);
			generatePrimitiveDialog.setIconImage(Environment.getDefaultIconImage());
			generatePrimitiveDialog.setLocationRelativeTo(null);
			generatePrimitiveDialog.setModal(false);
		}

		generatePrimitiveDialog.beginPreview(parent, targetBatch);
		generatePrimitiveDialog.pack();
		generatePrimitiveDialog.setVisible(true);
	}

	public void prompt_GenerateFromTriangles(MapObjectNode<?> parent)
	{
		if (generateFromTrianglesDialog == null) {
			generateFromTrianglesDialog = new GenerateFromTrianglesDialog(this,
				editor.generateFromTrianglesPreview, (result, batch) -> {
					if (result != DialogResult.ACCEPT)
						return;
					if (batch == null)
						return;

					createObjectFromBatch(batch,
						generateFromTrianglesDialog.getTypeName(),
						editor.generateFromTrianglesPreview.parentObj);
				});

			generateFromTrianglesDialog.setTitle(GeneratePrimitiveOptionsDialog.FRAME_TITLE);
			generateFromTrianglesDialog.setIconImage(Environment.getDefaultIconImage());
			generateFromTrianglesDialog.setLocationRelativeTo(null);
			generateFromTrianglesDialog.setModal(false);
		}

		generateFromTrianglesDialog.beginPreview(parent);
		generateFromTrianglesDialog.pack();
		generateFromTrianglesDialog.setVisible(true);
	}

	public void prompt_GenerateFromPaths(MapObjectNode<?> parent)
	{
		if (generateFromPathsDialog == null) {
			generateFromPathsDialog = new GenerateFromPathsDialog(this,
				editor.generateFromPathsPreview, (result, batch) -> {
					if (result != DialogResult.ACCEPT)
						return;
					if (batch == null)
						return;
					createObjectFromBatch(batch,
						generateFromPathsDialog.getTypeName(),
						editor.generateFromPathsPreview.parentObj);
				});

			generateFromPathsDialog.setTitle(GeneratePrimitiveOptionsDialog.FRAME_TITLE);
			generateFromPathsDialog.setIconImage(Environment.getDefaultIconImage());
			generateFromPathsDialog.setLocationRelativeTo(null);
			generateFromPathsDialog.setModal(false);
		}

		generateFromPathsDialog.beginPreview(parent);
		generateFromPathsDialog.pack();
		generateFromPathsDialog.setVisible(true);
	}

	@SuppressWarnings("unchecked")
	private void createObjectFromBatch(TriangleBatch batch, String namePrefix, MapObjectNode<?> parent)
	{
		MapObjectType type = parent.getUserObject().getObjectType();

		switch (type) {
			case MODEL:
				editor.doNextFrame(() -> {
					editor.action_CreateModel(batch, namePrefix + "Model", (MapObjectNode<Model>) parent);
				});
				break;
			case COLLIDER:
				editor.doNextFrame(() -> {
					editor.action_CreateCollider(batch, namePrefix + "Collider", (MapObjectNode<Collider>) parent);
				});
				break;
			case ZONE:
				editor.doNextFrame(() -> {
					editor.action_CreateZone(batch, namePrefix + "Zone", (MapObjectNode<Zone>) parent);
				});
				break;
			default:
				throw new IllegalStateException("Invalid MapObjectType for CreatePrimitive: " + type);
		}
	}

	public void prompt_CreateMarker(MapObjectNode<Marker> parent)
	{
		int choice = SwingUtils.getConfirmDialog()
			.setParent(this)
			.setCounter(openDialogCount)
			.setTitle("Create Marker")
			.setMessage(MarkerOptionsPanel.getInstance())
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
			.choose();

		if (choice == JOptionPane.OK_OPTION) {
			editor.doNextFrame(() -> {
				editor.action_CreateMarker(
					MarkerOptionsPanel.getMarkerName(),
					MarkerOptionsPanel.getMarkerType(),
					parent);
			});
		}
	}

	private void prompt_GenerateUV()
	{
		int choice = SwingUtils.getConfirmDialog()
			.setParent(this)
			.setCounter(openDialogCount)
			.setTitle("UV Projection Options")
			.setMessage(uvOptionsPanel)
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
			.choose();

		if (choice == JOptionPane.OK_OPTION) {
			final UVGenerator gen = uvOptionsPanel.getUVGenerator();
			editor.doNextFrame(() -> {
				editor.action_GenerateUVs(gen);
			});
		}
	}

	public void prompt_ConfirmDialog(Object message, String title, Runnable action)
	{
		int choice = SwingUtils.getConfirmDialog()
			.setParent(this)
			.setCounter(openDialogCount)
			.setTitle(title)
			.setMessage(message)
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
			.choose();

		if (choice == JOptionPane.OK_OPTION) {
			action.run();
		}
	}

	private void prompt_ChooseColor()
	{
		openDialogCount.increment();
		Color c = null;
		c = JColorChooser.showDialog(this, "Choose Color", c);
		openDialogCount.decrement();

		if (c != null) {
			PaintManager.setSelectedColor(c);
			PaintManager.pushSelectedColor();
		}
	}

	private void showControls()
	{
		SwingUtils.getMessageDialog()
			.setParent(this)
			.setCounter(openDialogCount)
			.setTitle("Controls and Shortcuts")
			.setMessage(new MapShortcutsPanel())
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.show();
	}

	private void showPreferences()
	{
		if (editor.editorConfig == null)
			return;

		MapPreferencesPanel preferences = new MapPreferencesPanel();
		preferences.setValues(editor.editorConfig);

		int choice = SwingUtils.getConfirmDialog()
			.setParent(this)
			.setCounter(openDialogCount)
			.setTitle("Editor Preferences")
			.setMessage(preferences)
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
			.choose();

		if (choice == JOptionPane.OK_OPTION) {
			preferences.getValues(editor.editorConfig);
			editor.loadPreferences();
			editor.editorConfig.saveConfigFile();
			Logger.log("Saved preferences to " + editor.editorConfig.getFile().getName());
		}
	}

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
				c = null;
				break;
		}

		infoLabel.setForeground(c);
		infoLabel.setText(msg.text);
	}

	public void post(String text)
	{
		infoLabel.setForeground(null);
		infoLabel.setText(text);
	}

	public void setTransformInfo(String fmt, Object ... args)
	{
		extraInfoLabel.setText(String.format(fmt, args));
	}

	public void setLastSelectedInfo(MapObject obj)
	{
		if (obj != null && obj.getName() != null) {
			String name = obj.getName();
			if (name.length() > 30)
				name = name.substring(0, 30) + "...";
			extraInfoLabel.setText("Last selected: " + name);
		}
		else
			extraInfoLabel.setText("");
	}

	public void displayFPS(double current, double max, double ms)
	{
		fpsLabel.setText(String.format("%s FPS  (%.1f ms)", (int) max, ms));
	}

	public void updateSnapLabel()
	{
		List<String> modes = new ArrayList<>(4);
		if (editor.vertexSnap)
			modes.add("Vertex");
		if (editor.snapTranslation)
			modes.add("Translation");
		if (editor.snapRotation)
			modes.add("Rotation");
		if (editor.snapScale) {
			if (editor.snapScaleToGrid)
				modes.add("Scale-to-Grid");
			else
				modes.add("Scale");
		}

		String text;
		if (modes.size() == 0)
			text = "Snap = [None]";
		else {
			StringBuilder sb = new StringBuilder("Snap = [");
			sb.append(modes.get(0));

			for (int i = 1; i < modes.size(); i++) {
				sb.append(",");
				sb.append(modes.get(i));
			}

			sb.append("]");

			text = sb.toString();
		}
		snapModeLabel.setText(text);
	}

	public void updateGridSize()
	{
		if (editor.gridEnabled)
			gridSizeLabel.setText("Grid spacing = " + editor.grid.getSpacing());
		else
			gridSizeLabel.setText("Grid Disabled");
	}

	public void updateTextureCount(ModelTexture texture)
	{
		currentTexturePanel.updateCount(texture);
	}

	public void setLightSetsVisible(boolean debugShowLightSets)
	{
		ScriptManager.instance().setLightSetsVisible(debugShowLightSets);
		objectPanel.setLightSetsVisible(debugShowLightSets);
	}

	public void setScrollSensitivity(int sensitivity)
	{
		textureScrollPane.getVerticalScrollBar().setUnitIncrement(sensitivity);
	}
}
