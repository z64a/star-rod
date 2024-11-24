package game.map.editor;

import static app.Directories.*;
import static game.map.MapKey.*;
import static game.map.editor.EditorShortcut.*;
import static org.lwjgl.opengl.GL11.*;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.w3c.dom.Element;

import app.Directories;
import app.Environment;
import app.LoadingBar;
import app.StackTraceDialog;
import app.StarRodException;
import app.StarRodMain;
import app.SwingUtils;
import app.config.Config;
import app.config.Options;
import app.config.Options.Scope;
import assets.AssetHandle;
import assets.AssetManager;
import assets.ui.SelectMapDialog;
import assets.ui.SelectTexDialog;
import common.FrameLimiter;
import common.KeyboardInput;
import common.KeyboardInput.KeyInputEvent;
import common.KeyboardInput.KeyboardInputListener;
import common.MouseInput;
import common.MouseInput.MouseManagerListener;
import common.Vector3f;
import game.ProjectDatabase;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.Map;
import game.map.Map.PrefabImportData;
import game.map.MapObject;
import game.map.MapObject.HitType;
import game.map.MapObject.MapObjectType;
import game.map.MapObject.ShapeType;
import game.map.MapSourceRenamer;
import game.map.compiler.BuildException;
import game.map.compiler.CollisionCompiler;
import game.map.compiler.GeometryCompiler;
import game.map.editor.camera.CameraController;
import game.map.editor.camera.MapEditCamera;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.camera.OrthographicViewport;
import game.map.editor.camera.PerspBattleCamera;
import game.map.editor.camera.PerspFreeCamera;
import game.map.editor.camera.PerspTargetCamera;
import game.map.editor.camera.PerspZoneCamera;
import game.map.editor.camera.PerspectiveViewport;
import game.map.editor.camera.UVEditorViewport;
import game.map.editor.camera.ViewType;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.ApplyTexture;
import game.map.editor.commands.ApplyTextureToList;
import game.map.editor.commands.CleanupTriangles;
import game.map.editor.commands.CommandBatch;
import game.map.editor.commands.ConvertHitObjects.ConvertColliders;
import game.map.editor.commands.ConvertHitObjects.ConvertZones;
import game.map.editor.commands.CreateBVH;
import game.map.editor.commands.CreateObject;
import game.map.editor.commands.CreateObjects;
import game.map.editor.commands.FuseVertices;
import game.map.editor.commands.HideObjectTree;
import game.map.editor.commands.HideObjectTreeSimple;
import game.map.editor.commands.HideObjects;
import game.map.editor.commands.InvertNormals;
import game.map.editor.commands.JoinHitObjects.JoinColliders;
import game.map.editor.commands.JoinHitObjects.JoinZones;
import game.map.editor.commands.JoinModels;
import game.map.editor.commands.PaintVertices;
import game.map.editor.commands.SeparateVertices;
import game.map.editor.commands.SplitHitObject.SplitCollider;
import game.map.editor.commands.SplitHitObject.SplitZone;
import game.map.editor.commands.SplitModel;
import game.map.editor.commands.ToggleDoubleSided;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.render.Color4d;
import game.map.editor.render.PreviewGeneratorFromPaths;
import game.map.editor.render.PreviewGeneratorFromTriangles;
import game.map.editor.render.PreviewGeneratorPrimitive;
import game.map.editor.render.PreviewGeometry;
import game.map.editor.render.RenderMode;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.RenderingOptions.SurfaceMode;
import game.map.editor.render.TextureManager;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.Channel;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.selection.Selectable;
import game.map.editor.selection.Selection;
import game.map.editor.selection.SelectionManager;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.editor.ui.FindDialog;
import game.map.editor.ui.GuiCommand;
import game.map.editor.ui.SwingGUI;
import game.map.hit.CameraZoneData;
import game.map.hit.CameraZoneData.SetCameraFlag;
import game.map.hit.CameraZoneData.SetCameraPos;
import game.map.hit.CameraZoneData.SetCameraType;
import game.map.hit.Collider;
import game.map.hit.ControlType;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.scripts.generators.Exit;
import game.map.scripts.generators.Generator;
import game.map.scripts.generators.Generator.GeneratorType;
import game.map.shading.ShadingLightSource;
import game.map.shading.ShadingProfile;
import game.map.shading.SpriteShadingEditor;
import game.map.shape.Model;
import game.map.shape.TransformMatrix;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import game.map.shape.UVGenerator;
import game.map.tree.MapObjectNode;
import game.sprite.SpriteLoader;
import game.texture.ModelTexture;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.postprocess.PostProcessFX;
import util.LogFile;
import util.Logger;
import util.MathUtil;
import util.Priority;
import util.identity.IdentityArrayList;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class MapEditor extends GLEditor implements MouseManagerListener, KeyboardInputListener, KeyEventDispatcher
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		new MapEditor(true).launch();
		Environment.exit();
	}

	public static boolean doStepProfiling = false;

	private File clipboardFile;
	private LogFile editorLog;

	private boolean loading = true;
	private boolean exitCompletely = !Environment.mainConfig.getBoolean(Options.ExitToMenu);

	public boolean needsTextureReload;
	public boolean needsBackgroundReload;

	/**
	 * Timing
	 */

	public static final int TARGET_FPS = 60;
	private double deltaTime = 1.0f / TARGET_FPS;
	private double time = 0;
	private long frameCounter;

	/**
	 * Autosaves
	 */

	private int backupInterval;
	private double lastBackupTime;

	/**
	 * Inputs
	 */

	public static final int CLONE_KEY = KeyEvent.VK_ALT;
	public static final int SCALE_KEY = KeyEvent.VK_SPACE;

	/**
	 * Editor Modes
	 */

	public static enum EditorMode
	{
		Modify ("Modify"),
		Texture ("Apply Textures"),
		EditUVs ("Edit UVs"),
		VertexPaint ("Paint Vertices"),
		Scripts ("Edit Scripts");

		private final String name;

		private EditorMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private EditorMode editorMode = null;
	private ChangeMode lastModeChange = null;

	public Grid objectGrid = new Grid(false, 4);
	public Grid uvGrid = new UVGrid(8);
	public Grid grid = objectGrid;
	public boolean gridEnabled;

	public CursorObject cursor3D;

	private List<EditorObject> editorObjects;

	public boolean debugShowLightSets = false;

	/**
	 * Selection
	 */

	public boolean rescaling = false;
	public boolean snapScaleToGrid = false;

	private PickRay currentRay;

	private PickRay clickRayLMB;
	private PickHit clickHitLMB;

	private PickRay clickRayRMB;
	private PickHit clickHitRMB;

	private PickRay clickRayMMB;
	private PickHit clickHitMMB;

	public Vector3f dragBoxStartPoint;
	public Vector3f dragBoxEndPoint;
	public boolean draggingBox;

	public boolean selectionPainting;
	public float selectionPaintRadius;

	public boolean snapTranslation;
	public boolean snapRotation;
	public boolean snapScale;
	public boolean vertexSnap;
	public boolean vertexSnapLimit;

	public boolean translateUVs;

	/**
	 * Transformation
	 */

	private enum NudgeDirection
	{
		UP, DOWN, LEFT, RIGHT, IN, OUT
	}

	private boolean canDoNudgeTranslation = true;
	private boolean doingNudgeTranslation = false;

	/**
	 * Vertex Painting
	 */

	public PickHit paintPickHit;
	private IdentityHashSet<Vertex> paintingVertexSet;
	private IdentityHashMap<Vertex, Color4d> backupVertexColorMap;

	/**
	 * Play in Editor and Cutscene Camera
	 */

	public static enum PerspCameraMode
	{
		FREE,
		PLAY_IN_EDITOR,
		MARKER_PREVIEW,
		BATTLE,
	}

	private PerspCameraMode perspCameraMode;

	private PerspFreeCamera freeCam;
	private PerspZoneCamera zoneCam;
	private PerspTargetCamera targetCam;
	private PerspBattleCamera battleCam;

	private boolean isPlayInEditorMode;
	public boolean pieIgnoreHiddenColliders;
	public boolean pieIgnoreHiddenZones;
	public boolean pieDrawCameraInfo;
	public boolean pieEnableMapExits;
	public CameraController dummyCameraController;

	boolean usingTargetCam;

	private static enum ChangeMapState
	{
		NONE,
		CHOSE_MAP,
		READY_TO_LOAD,
		LOADING_MAP,
		LOADING_FAILED,
		EXITING,
		CHECK_MAP,
		OPEN_MAP,
		ENTER_INIT,
		ENTERING
	}

	private volatile ChangeMapState changeMapState = ChangeMapState.NONE;
	private File destMapFile;
	private Map destMap;
	private String destMapName;
	private String destMarkerName;
	private String exitMarkerName;
	private float mapChangeTimer;
	private float screenFadeAmount = 0.0f;
	private static final float EXIT_TIME = 0.5f;
	private static final float ENTER_TIME = 0.5f;

	/**
	 * Viewports and glCanvas
	 */

	private Renderer renderer;

	private static enum ViewMode
	{
		ONE, UVEDIT, PREVIEW, FOUR
	} // which viewports should we be drawing?

	private ViewMode viewMode;
	private ViewMode mainViewMode;

	private MapEditViewport fourViews[];
	private MapEditViewport uvViews[];
	private MapEditViewport previewViews[];
	private MapEditViewport activeView;
	private PerspectiveViewport perspectiveView;
	private UVEditorViewport uvEditView;

	private boolean edgeHighlights;

	private boolean enableXDivider;
	private boolean enableYDivider;
	private float hDivRatio;
	private float vDivRatio;
	private final int BORDER_HALF_SIZE = 8;
	private boolean resizingViews;

	private Dimension prevCanvasSize = null;
	private boolean dummyDraw;

	/**
	 * Rendering options
	 */

	public PostProcessFX postProcessFX = PostProcessFX.NONE;

	public boolean thumbnailMode = false;
	public boolean showModels;
	public boolean showColliders;
	public boolean showZones;
	public boolean showMarkers;
	private boolean showBoundingBoxes;
	private boolean showNormals;
	public boolean showAxes;
	public boolean showGizmo;
	public boolean showEntityCollision;
	public boolean useColliderColoring;

	private boolean useGeometryFlags;
	private boolean useFiltering;
	private boolean useTextureLOD;

	public boolean useMapCameraProperties;
	public boolean useMapBackgroundColor;
	public boolean useGameAspectRatio;

	public final PreviewGeneratorPrimitive generatePrimitivePreview;
	public final PreviewGeneratorFromTriangles generateFromTrianglesPreview;
	public final PreviewGeneratorFromPaths generateFromPathsPreview;
	public final PreviewGeometry drawGeometryPreview;

	/**
	 * GUI Event Handling Events from the GUI are processed in three ways: (1) General events are submitted as Runnable
	 * objects to the guiEventQueue via invokeNextFrame(). (2) GuiCommands are forwarded to the guiEventQueue with
	 * submitGuiCommand(). (3) EditorKeyEvents are submitted to the keyEventQueue via enqueueKeyEvent().
	 */
	private LinkedBlockingQueue<Runnable> guiEventQueue;
	private LinkedBlockingQueue<EditorShortcut> keyEventQueue;

	/**
	 * Loaded Map Data
	 */

	public final Config editorConfig;

	public Map map;
	public Map shapeOverride;
	public Map hitOverride;
	private Deque<String> recentMaps;

	/**
	 * Major Components
	 */

	public KeyboardInput keyboard;
	public MouseInput mouse;
	public SpriteLoader spriteLoader;

	public SelectionManager selectionManager; // handles object/face/vertex selections
	private CommandManager commandManager; // handles comnmand execution and undo/redo
	private DrawTrianglesManager drawTriManager;
	public SwingGUI gui;
	private boolean showLoadingScreen;

	public boolean showLightingPanel;
	public boolean showMapCameraTab;

	private List<MapInfoPanel<?>> infoPanels = new ArrayList<>();

	public void registerInfoPanel(MapInfoPanel<?> ip)
	{
		infoPanels.add(ip);
	}

	public void forceUpdateInfoPanels()
	{
		for (MapInfoPanel<?> ip : infoPanels)
			ip.update("");
	}

	private List<Tickable> tickers = new ArrayList<>();

	public void registerTickable(Tickable ticker)
	{
		tickers.add(ticker);
	}

	// singleton
	private static MapEditor instance = null;

	public static MapEditor instance()
	{
		return instance;
	}

	public static boolean exists()
	{
		return instance != null;
	}

	public static void execute(AbstractCommand cmd)
	{
		if (SwingUtilities.isEventDispatchThread())
			instance().executeNextFrame(cmd);
		else
			instance().commandManager.executeCommand(cmd);
	}

	public void flushUndoRedo()
	{
		commandManager.flush();
	}

	private List<IShutdownListener> singletonList;

	public static interface IShutdownListener
	{
		public void shutdown();
	}

	public void registerOnShutdown(IShutdownListener s)
	{
		singletonList.add(s);
	}

	public MapEditor(boolean showLoadingScreen) throws IOException
	{
		super();

		if (instance != null)
			throw new IllegalStateException("Only one MapEditor open at a time please!");

		instance = this;
		singletonList = new LinkedList<>();

		this.showLoadingScreen = showLoadingScreen;
		if (showLoadingScreen)
			LoadingBar.show("Launching Map Editor");

		// ------------------------------------------------------------
		// read settings from config

		File editorConfigFile = new File(PROJ_CFG + "/" + FN_MAP_EDITOR_CONFIG);

		editorConfig = readEditorConfig(editorConfigFile);
		loadRecentMaps();

		// ------------------------------------------------------------
		// open files

		FileUtils.forceMkdir(Directories.TEMP.toFile());
		clipboardFile = new File(Directories.TEMP + "clipboard.temp");
		clipboardFile.deleteOnExit();

		File logFile = new File(LOGS + "mapEditor.log");
		editorLog = new LogFile(logFile, false);

		// ------------------------------------------------------------
		// create editor components

		generatePrimitivePreview = new PreviewGeneratorPrimitive();
		generateFromTrianglesPreview = new PreviewGeneratorFromTriangles();
		generateFromPathsPreview = new PreviewGeneratorFromPaths();
		drawGeometryPreview = new PreviewGeometry();

		selectionManager = new SelectionManager(this);
		commandManager = new CommandManager(32);
		drawTriManager = new DrawTrianglesManager(this, drawGeometryPreview);

		KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
		manager.addKeyEventDispatcher(this);

		spriteLoader = new SpriteLoader();
		SpriteLoader.loadAnimsMetadata(true);

		keyboard = new KeyboardInput(glCanvas);
		mouse = new MouseInput(glCanvas);
		gui = new SwingGUI(this, glCanvas, logFile);
		Logger.addListener(gui);

		if (Environment.isMacOS())
			setFullScreenEnabled(gui, false);

		loadPreferences();

		// must trigger GLInit without drawing...
		dummyDraw = true;
		glCanvas.render();
		dummyDraw = false;
	}

	public boolean isLoading()
	{
		return loading;
	}

	/**
	 * Start running the editor, prompting the user to select a map
	 */
	public boolean launch()
	{
		LoadingBar.dismiss();

		Map selectedMap = checkForCrashData();

		if (selectedMap == null)
			selectedMap = showOpeningPrompt();

		if (showLoadingScreen)
			LoadingBar.show("Launching Map Editor");

		return launch(selectedMap);
	}

	/**
	 * Start running the editor with a given map
	 */
	public boolean launch(Map map)
	{
		if (map == null) {
			LoadingBar.dismiss();

			runInContext(() -> {
				TextureManager.clear();
			});

			Logger.removeListener(gui);
			gui.destroyGUI();

			editorLog.close();

			instance = null;

			for (IShutdownListener s : singletonList)
				s.shutdown();

			return true;
		}

		map = checkForBackup(map);

		// monitor for crashes
		try {
			EditableField.setCallbacksEnabled(true);

			openMap(map, false);

			// run two logic + render steps before the editor is visible to warm up
			for (int i = 0; i < 2; i++) {
				step();
				glCanvas.render();
			}

			LoadingBar.dismiss();

			gui.showGUI();

			// enter the main loop
			runLoop();

			// loop is done, prepare for shutdown
			cleanup(false);
		}
		catch (Throwable t) {
			// handle crash
			Logger.printStackTrace(t);

			try {
				cleanup(true);
			}
			catch (Throwable x) {
				// ignore exceptions in crash-cleanup
			}

			StackTraceDialog.display(t, editorLog.getFile());
			throw new StarRodException(t);
		}
		finally {
			LoadingBar.dismiss();
		}

		return !exitCompletely;
	}

	@Override
	protected void glInit()
	{
		renderer = new Renderer(this);
		TextureManager.bindEditorTextures();
	}

	private void cleanup(boolean crashed)
	{
		EditableField.setCallbacksEnabled(false);
		TextureManager.clear();
		Logger.removeListener(gui);
		glCanvas.disposeCanvas();
		gui.destroyGUI();

		// update recent/crashed maps and flush the config
		saveRecentMaps();
		if (editorConfig != null) {
			if (crashed) {
				saveCrashFile();
				editorConfig.setString(Options.CrashedMap, map.getName());
			}
			else {
				editorConfig.setString(Options.CrashedMap, "");
			}

			editorConfig.saveConfigFile();
		}

		for (IShutdownListener s : singletonList) {
			s.shutdown();
		}

		if (crashed) {
			Logger.log("Map editor crashed!");
		}
		else {
			Logger.log("Exited without error.");
		}

		editorLog.close();

		instance = null;
	}

	public void shutdown()
	{
		WindowEvent closingEvent = new WindowEvent(gui, WindowEvent.WINDOW_CLOSING);
		Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
	}

	public void changeMap(Map map)
	{
		gui.changeMap(map);
	}

	private boolean thumbnailInitialized = false;

	public void generateThumbnail(File mapFile, File thumbFile)
	{
		Map newMap = Map.loadMap(mapFile);
		if (newMap == null)
			return;

		openMap(newMap, true);
		for (MapObject obj : getCollisionMap().colliderTree)
			obj.hidden = true;
		for (MapObject obj : getCollisionMap().zoneTree)
			obj.hidden = true;
		// for(MapObject obj : map.markerTree) obj.hidden = true;

		if (!thumbnailInitialized)
			initThumbnail();

		for (int i = 0; i < 2; i++) {
			step();
			glCanvas.render();
		}

		renderThumbnail(thumbFile);
	}

	public void shutdownThumbnail()
	{
		TextureManager.clear();
		Logger.removeListener(gui);

		glCanvas.disposeCanvas();
		gui.destroyGUI();

		editorLog.close();
		instance = null;

		for (IShutdownListener s : singletonList)
			s.shutdown();
	}

	private void loadRecentMaps()
	{
		Options[] opts = {
				Options.RecentMap0, Options.RecentMap1, Options.RecentMap2,
				Options.RecentMap3, Options.RecentMap4, Options.RecentMap5 };

		recentMaps = new LinkedList<>();

		if (editorConfig == null)
			return;

		for (Options opt : opts) {
			String mapName = editorConfig.getString(opt);
			if (mapName == null || mapName.isBlank()) {
				continue;
			}

			AssetHandle ah = AssetManager.getMap(mapName);
			if (ah.exists()) {
				recentMaps.add(mapName);
			}
		}
	}

	private void saveRecentMaps()
	{
		Options[] opts = {
				Options.RecentMap0, Options.RecentMap1, Options.RecentMap2,
				Options.RecentMap3, Options.RecentMap4, Options.RecentMap5 };

		if (editorConfig == null)
			return;

		int i = 0;
		for (String mapName : recentMaps)
			editorConfig.setString(opts[i++], mapName);

		for (; i < opts.length;)
			editorConfig.setString(opts[i++], "");
	}

	private void updateRecentMaps()
	{
		String current = map.getName();
		recentMaps.remove(current);
		recentMaps.addFirst(current);
		while (recentMaps.size() > 6) {
			recentMaps.removeLast();
		}
	}

	private void saveCrashFile()
	{
		try {
			map.saveMapAs(AssetManager.getSaveMapFile(map.getName() + Directories.MAP_CRASH_SUFFIX));
			DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
			Logger.log("Saved crash at " + dateFormatter.format(LocalDateTime.now()));
		}
		catch (Exception e) {
			Logger.logError("Failed to save crash for " + map.getName());
			Logger.printStackTrace(e);
		}
	}

	private void saveBackup()
	{
		try {
			map.saveMapAs(AssetManager.getSaveMapFile(map.getName() + Directories.MAP_BACKUP_SUFFIX));
			DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
			Logger.log("Saved backup at " + dateFormatter.format(LocalDateTime.now()));
		}
		catch (Exception e) {
			Logger.logError("Backup failed for " + map.getName());
			Logger.printStackTrace(e);
		}
		lastBackupTime = time;
	}

	private Map checkForCrashData()
	{
		if (editorConfig == null)
			return null;

		String crashedMapName = editorConfig.getString(Options.CrashedMap);
		if (crashedMapName.isBlank())
			return null;

		File crashMapFile = AssetManager.getSaveMapFile(crashedMapName + Directories.MAP_CRASH_SUFFIX);
		if (!crashMapFile.exists()) {
			Logger.logWarning("Failed to locate crash data for " + crashedMapName);
			return null;
		}

		try {
			Map crashMap = Map.loadMap(crashMapFile);
			if (crashMap == null)
				return null;

			int choice = SwingUtils.getConfirmDialog()
				.setTitle("Crash Recovery")
				.setMessage("Found crash data for " + crashMap.getName(), "Would you like to load it?")
				.setMessageType(JOptionPane.PLAIN_MESSAGE)
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.choose();

			if (choice == JOptionPane.YES_OPTION)
				return crashMap;
		}
		catch (Throwable t) {
			Logger.logError("Error while loading crash data! " + t.getMessage());
			return null;
		}

		return null;
	}

	private Map checkForBackup(Map baseMap)
	{
		try {
			File backupFile = AssetManager.getSaveMapFile(baseMap.getName() + Directories.MAP_BACKUP_SUFFIX);

			if (!backupFile.exists() || backupFile.lastModified() <= baseMap.lastModified)
				return baseMap;

			Map backupMap = Map.loadMap(backupFile);
			if (backupMap == null) {
				Logger.log("Could not load backup data!");
				return baseMap;
			}

			int choice = SwingUtils.getConfirmDialog()
				.setCounter(gui.getDialogCounter())
				.setTitle("Backup Recovery")
				.setMessage("Found backup data for " + baseMap.getName(), "Would you like to load it instead?")
				.setMessageType(JOptionPane.PLAIN_MESSAGE)
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.choose();

			if (choice == JOptionPane.YES_OPTION)
				return backupMap;
		}
		catch (Throwable t) {
			t.printStackTrace();
			Logger.log("Error while loading backup data! " + t.getMessage());
			return baseMap;
		}

		return baseMap;
	}

	private Map showOpeningPrompt()
	{
		String[] options = { "Browse Maps" };
		File lastMap = null;

		if (editorConfig != null) {
			String lastMapName = editorConfig.getString(Options.RecentMap0);
			if (lastMapName != null && !lastMapName.isBlank()) {
				AssetHandle ah = AssetManager.getMap(lastMapName);
				if (ah.exists()) {
					options = new String[] { "Browse Maps", "Reopen " + lastMapName };
					lastMap = ah;
				}
			}
		}

		Map selectedMap = null;

		while (true) {
			int choice = SwingUtils.getOptionDialog()
				.setTitle("Choose Map")
				.setMessage("Which map would you like to open?")
				.setMessageType(JOptionPane.PLAIN_MESSAGE)
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.setOptions(options)
				.setDefault("Browse Maps")
				.choose();

			if (choice == JOptionPane.CLOSED_OPTION) {
				selectedMap = null;
				break;
			}

			if (choice == 0) {
				File mapFile = SelectMapDialog.showPrompt();
				if (mapFile != null) {
					selectedMap = Map.loadMap(mapFile);
					break;
				}
			}

			if (choice == 1) {
				selectedMap = Map.loadMap(lastMap);
				break;
			}
		}

		return selectedMap;
	}

	private void resetEditorSettings()
	{
		resizingViews = false;
		rescaling = false;

		clickRayLMB = null;
		clickHitLMB = null;
		clickRayRMB = null;
		clickHitRMB = null;
		clickRayMMB = null;
		clickHitMMB = null;
		paintPickHit = null;

		perspectiveView = new PerspectiveViewport(this, renderer);
		uvEditView = new UVEditorViewport(this, renderer);

		perspCameraMode = PerspCameraMode.FREE;
		freeCam = new PerspFreeCamera(perspectiveView);
		zoneCam = new PerspZoneCamera(perspectiveView);
		targetCam = new PerspTargetCamera(perspectiveView);
		battleCam = new PerspBattleCamera(perspectiveView);

		perspectiveView.setCamera(freeCam);

		generatePrimitivePreview.init();
		generateFromTrianglesPreview.init();
		generateFromPathsPreview.init();
		drawGeometryPreview.init();

		if (changeMapState == ChangeMapState.NONE) {
			hDivRatio = 0.5f;
			vDivRatio = 0.5f;

			isPlayInEditorMode = false;
			showEntityCollision = true;
			pieIgnoreHiddenColliders = false;
			pieIgnoreHiddenZones = false;
			pieDrawCameraInfo = true;
			pieEnableMapExits = true;
			dummyCameraController = new CameraController();
			usingTargetCam = false;

			PLAY_IN_EDITOR_TOGGLE.setCheckbox(isPlayInEditorMode);
			PIE_IGNORE_HIDDEN_COL.setCheckbox(pieIgnoreHiddenColliders);
			PIE_IGNORE_HIDDEN_ZONE.setCheckbox(pieIgnoreHiddenZones);
			PIE_SHOW_ACTIVE_CAMERA.setCheckbox(pieDrawCameraInfo);
			PIE_ENABLE_MAP_EXITS.setCheckbox(pieEnableMapExits);

			edgeHighlights = false;
		}

		cursor3D = new CursorObject(new Vector3f(0.0f, 0.0f, 0.0f));

		editorObjects = new IdentityArrayList<>();
		editorObjects.add(cursor3D);

		fourViews = new MapEditViewport[4];
		fourViews[0] = perspectiveView;
		fourViews[1] = new OrthographicViewport(this, renderer, ViewType.TOP);
		fourViews[2] = new OrthographicViewport(this, renderer, ViewType.SIDE);
		fourViews[3] = new OrthographicViewport(this, renderer, ViewType.FRONT);

		uvViews = new MapEditViewport[2];
		uvViews[0] = perspectiveView;
		uvViews[1] = uvEditView;

		if (changeMapState == ChangeMapState.NONE) {
			mainViewMode = ViewMode.FOUR;
			setViewMode(mainViewMode);
			resizeViews();

			activeView = perspectiveView;
			objectGrid = new Grid(false, 4);
			uvGrid = new UVGrid(8);
			grid = objectGrid;
			gridEnabled = true;
			gui.updateGridSize();
			TOGGLE_GRID.setCheckbox(gridEnabled);
			TOGGLE_GRID_TYPE.setCheckbox(!objectGrid.binary);

			snapScaleToGrid = false;
			SNAP_SCALE_GRID.setCheckbox(snapScaleToGrid);

			vertexSnap = false;
			VERTEX_SNAP.setCheckbox(vertexSnap);
			vertexSnapLimit = true;
			VERTEX_SNAP_LIMIT.setCheckbox(vertexSnapLimit);

			snapTranslation = true;
			snapRotation = true;
			snapScale = true;
			SNAP_TRANSLATION.setCheckbox(snapTranslation);
			SNAP_ROTATION.setCheckbox(snapRotation);
			SNAP_SCALE.setCheckbox(snapScale);

			Marker.movePointsWithObject = true;
			MOVE_MARKER_POINTS.setCheckbox(Marker.movePointsWithObject);

			gui.updateSnapLabel();
		}

		canDoNudgeTranslation = true;
		doingNudgeTranslation = false;

		dragBoxStartPoint = null;
		dragBoxEndPoint = null;
		draggingBox = false;

		selectionPainting = false;
		selectionPaintRadius = 16.0f;

		selectionManager = new SelectionManager(this);
		commandManager = new CommandManager(32);

		if (changeMapState == ChangeMapState.NONE) {
			showModels = true;
			showColliders = true;
			showZones = true;
			showMarkers = true;
			SHOW_MODELS.setCheckbox(showModels);
			SHOW_COLLIDERS.setCheckbox(showColliders);
			SHOW_ZONES.setCheckbox(showZones);
			SHOW_MARKERS.setCheckbox(showMarkers);

			showBoundingBoxes = false;
			showNormals = false;
			showAxes = true;
			showGizmo = true;
			thumbnailMode = false;
			useColliderColoring = true;
			SHOW_AABB.setCheckbox(showBoundingBoxes);
			SHOW_NORMALS.setCheckbox(showNormals);
			SHOW_AXES.setCheckbox(showAxes);
			SHOW_GIZMO.setCheckbox(showGizmo);
			SHOW_ENTITY_COLLISION.setCheckbox(showEntityCollision);
			USE_COLLIDER_COLORS.setCheckbox(useColliderColoring);

			useGameAspectRatio = false;
			useMapBackgroundColor = true;
			useMapCameraProperties = false;
			useGeometryFlags = false;
			useFiltering = false;
			useTextureLOD = true;
			USE_GAME_ASPECT_RATIO.setCheckbox(useGameAspectRatio);
			USE_MAP_CAM_PROPERTIES.setCheckbox(useMapCameraProperties);
			USE_MAP_BG_COLOR.setCheckbox(useMapBackgroundColor);
			USE_GEOMETRY_FLAGS.setCheckbox(useGeometryFlags);
			USE_FILTERING.setCheckbox(useFiltering);
			USE_TEXTURE_LOD.setCheckbox(useTextureLOD);
		}

		// switch to modify mode, bootstrap if necessary
		if (lastModeChange == null)
			lastModeChange = new ChangeMode(EditorMode.Modify);
		ChangeMode resetMode = new ChangeMode(EditorMode.Modify);
		resetMode.silence();
		resetMode.exec();

		guiEventQueue = new LinkedBlockingQueue<>();
		keyEventQueue = new LinkedBlockingQueue<>();
		frameCounter = 0;

		loadPreferences();
		setInitialCameraOrientation();
		resizeViews();
	}

	private static Config readEditorConfig(File configFile)
	{
		if (!configFile.exists()) {
			Config cfg = makeNewConfig(configFile);
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

		Config cfg = new Config(configFile, Scope.MapEditor);
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

	private static Config makeNewConfig(File configFile)
	{
		try {
			FileUtils.touch(configFile);
		}
		catch (IOException e) {
			return null;
		}

		Config cfg = new Config(configFile, Scope.MapEditor);
		for (Options opt : Options.values()) {
			if (opt.scope == Scope.MapEditor)
				opt.setToDefault(cfg);
		}

		return cfg;
	}

	/**
	 * Choose the initial camera position based on the center of the model scene tree bounding box and the average normal
	 * vector of model triangles.
	 */
	private void setInitialCameraOrientation()
	{
		int numMeshes = 0;
		Vector3f normalSum = new Vector3f();
		for (Model mdl : getGeometryMap().modelTree) {
			int numTriangles = 0;
			Vector3f mdlNormalSum = new Vector3f();
			for (Triangle t : mdl.getMesh()) {
				Vector3f normal = t.getNormal();
				if (normal == null)
					continue;

				mdlNormalSum.x += normal.x;
				mdlNormalSum.z += normal.z;
				numTriangles++;
			}

			if (numTriangles > 0) {
				normalSum.x += (mdlNormalSum.x / numTriangles);
				normalSum.z += (mdlNormalSum.z / numTriangles);
				numMeshes++;
			}
		}

		if (numMeshes > 0) {
			normalSum.x /= numMeshes;
			normalSum.z /= numMeshes;

			if (normalSum.length() < MathUtil.SMALL_NUMBER)
				normalSum = new Vector3f(1.0f, 0.0f, 0.0f);
			else
				normalSum.normalize();

			int dist = 240;
			float pitchDown = 8.0f;
			float yaw = (float) Math.toDegrees(Math.atan2(-normalSum.x, normalSum.z));
			yaw = 30.0f * Math.round(yaw / 30.0f);

			Vector3f center = map.modelTree.getRoot().getUserObject().AABB.getCenter();

			Vector3f newPos = new Vector3f();
			newPos.x = center.x - dist * (float) Math.sin(Math.toRadians(yaw));
			newPos.y = center.y + dist * (float) Math.tan(Math.toRadians(pitchDown));
			newPos.z = center.z + dist * (float) Math.cos(Math.toRadians(yaw));

			for (MapEditViewport view : fourViews)
				view.camera.setPosition(center);

			perspectiveView.camera.setPosition(newPos);
			perspectiveView.camera.setRotation(new Vector3f(pitchDown, yaw, 0.0f));
		}
	}

	/**
	 * Main logic loop Nothing that requires a GL context may be called from here!
	 */
	private void runLoop()
	{
		FrameLimiter limiter = new FrameLimiter();
		long t_launch = System.nanoTime();
		double avMaxFPS = 0;
		double avFPS = 0;

		while (!gui.isCloseRequested()) {
			long t_start = System.nanoTime();

			step();
			glCanvas.render();

			long t_beforesync = System.nanoTime();
			limiter.sync(TARGET_FPS);
			long t_aftersync = System.nanoTime();

			// compute timing info
			deltaTime = (t_aftersync - t_start) / 1e9;
			time = (t_aftersync - t_launch) / 1e9;

			avMaxFPS += 1e9 / (t_aftersync - t_start); // max FPS would use t_beforesync here
			avFPS += 1 / deltaTime;

			if (!selectionManager.currentSelection.transforming() && !selectionManager.uvSelection.transforming())
				gui.setLastSelectedInfo(selectionManager.getMostRecentObject());

			if (++frameCounter % 30 == 0) {
				gui.displayFPS(avFPS / 30, avMaxFPS / 30, (t_beforesync - t_start) / 1e6);
				avMaxFPS = 0;
				avFPS = 0;
			}

			if (backupInterval > 0 && time >= lastBackupTime + 60.0 * backupInterval)
				saveBackup();
		}
	}

	private void step()
	{
		BasicProfiler profiler = null;
		if (doStepProfiling) {
			profiler = new BasicProfiler();
			profiler.begin();
		}

		// handle window resizing
		if (!glCanvas.getSize().equals(prevCanvasSize)) {
			resizeViews();
			prevCanvasSize = glCanvas.getSize();
		}

		// compute the current pick ray
		currentRay = activeView.camera.getPickRay(mouse.getPosX(), mouse.getPosY());

		if (editorMode == EditorMode.VertexPaint) {
			PaintManager.update(this, deltaTime);
			paintPickHit = selectionManager.pickWorld(map, currentRay, activeView, true);
		}

		if (editorMode == EditorMode.EditUVs && !selectionManager.uvSelection.transforming())
			updateUVViewport();

		selectionManager.testGizmoMouseover(editorMode, currentRay, activeView);

		// handle input
		keyboard.update(this, gui.isFocused());
		mouse.update(this, gui.isFocused());

		if (activeView == perspectiveView && cursor3D.isSelected()) {
			int dw = mouse.getFrameDW();
			if (dw != 0)
				cursor3D.changeGuide(dw);
		}

		while (!keyEventQueue.isEmpty())
			handleShortcutPress(keyEventQueue.poll(), true);

		updateKeyboardTranslation();

		if (!isPlayInEditorMode) {
			Selection<?> selection = getSelectionForCurrentMode();
			if (selection != null && selection.transforming())
				selection.postTransformUpdateToViewport();

			if (showModeInViewport) {
				switch (editorMode) {
					case Modify:
						SelectionMode selectMode = selectionManager.getSelectionMode();
						// if(selectMode != SelectionMode.OBJECT)
						activeView.setTextUL("Edit " + selectMode, true);
						break;
					default:
						activeView.setTextUL(editorMode.toString(), true);
				}
			}
		}

		drawTriManager.tick(activeView);

		generatePrimitivePreview.update();
		generateFromTrianglesPreview.update();
		generateFromPathsPreview.update();
		drawGeometryPreview.update();

		// set camera mode based on state
		if (usingTargetCam) {
			perspCameraMode = PerspCameraMode.MARKER_PREVIEW;
		}
		else if (isPlayInEditorMode) {
			if (map.isStage)
				perspCameraMode = PerspCameraMode.BATTLE;
			else
				perspCameraMode = PerspCameraMode.PLAY_IN_EDITOR;
		}
		else {
			perspCameraMode = PerspCameraMode.FREE;
		}

		// set perspective viewport camera based on mode
		switch (perspCameraMode) {
			case FREE:
				perspectiveView.setCamera(freeCam);
				break;
			case PLAY_IN_EDITOR:
				perspectiveView.setCamera(zoneCam);
				break;
			case MARKER_PREVIEW:
				perspectiveView.setCamera(targetCam);
				break;
			case BATTLE:
				perspectiveView.setCamera(battleCam);
				break;
		}

		perspectiveView.camera.tick(deltaTime);
		activeView.camera.handleMovementInput(mouse, keyboard, (float) deltaTime);

		if (doStepProfiling)
			profiler.record("input");

		if (isPlayInEditorMode)
			updatePlayInEditorSimulation(deltaTime);

		cursor3D.updateShadow(getCollisionMap(), map, deltaTime);

		if (doStepProfiling && isPlayInEditorMode)
			profiler.record("PIE");

		// execute events from the GUI
		while (!guiEventQueue.isEmpty())
			guiEventQueue.poll().run();

		// synchronize the GUI
		gui.syncSelectionWith(selectionManager);

		if (doStepProfiling)
			profiler.record("gui events");

		// recalculate bounding boxes every frame, it's easier this way
		map.recalculateBoundingBoxes();
		selectionManager.recalculateBoundingBox();

		if (doStepProfiling)
			profiler.record("AABBs");

		for (Marker m : map.markerTree)
			m.tick(deltaTime);

		if (doStepProfiling)
			profiler.record("markers");

		for (int i = 0; i < map.scripts.texPanners.getSize(); i++)
			map.scripts.texPanners.get(i).tick(deltaTime);

		cursor3D.updateAnimation(deltaTime);

		for (Tickable ticker : tickers)
			ticker.tick(deltaTime);

		if (doStepProfiling) {
			profiler.record("the rest");
			profiler.print();
		}
	}

	public EditorMode getEditorMode()
	{
		return editorMode;
	}

	public Selection<?> getSelectionForCurrentMode()
	{
		Selection<?> selection = null;
		switch (editorMode) {
			case Modify:
				selection = selectionManager.currentSelection;
				break;
			case EditUVs:
				selection = selectionManager.uvSelection;
				break;
			case VertexPaint:
			case Texture:
			case Scripts:
				selection = selectionManager.currentSelection;
		}
		return selection;
	}

	public List<EditorObject> getEditorObjects()
	{
		return editorObjects;
	}

	public void addEditorObject(EditorObject obj)
	{
		editorObjects.add(obj);
	}

	public boolean removeEditorObject(EditorObject obj)
	{
		return editorObjects.remove(obj);
	}

	public long getFrame()
	{
		return frameCounter;
	}

	public double getDeltaTime()
	{
		return deltaTime;
	}

	public double getTotalTime()
	{
		return time;
	}

	/**
	 * These methods allow the GUI to submit events to the editor.
	 */

	public void enqueueKeyEvent(EditorShortcut event)
	{
		keyEventQueue.add(event);
	}

	public void doNextFrame(Runnable event)
	{
		guiEventQueue.add(event);
	}

	public void executeNextFrame(AbstractCommand cmd)
	{
		guiEventQueue.add(() -> {
			commandManager.executeCommand(cmd);
		});
	}

	public void submitGuiCommand(GuiCommand cmd)
	{
		guiEventQueue.add(() -> {
			executeGuiCommand(cmd);
		});
	}

	private void executeGuiCommand(GuiCommand cmd)
	{
		switch (cmd) {
			case SELECT_OBJECTS:
				changeSelectionMode(SelectionMode.OBJECT);
				break;

			case SELECT_TRIANGLES:
				changeSelectionMode(SelectionMode.TRIANGLE);
				break;

			case SELECT_VERTICES:
				changeSelectionMode(SelectionMode.VERTEX);
				break;

			case SELECT_POINTS:
				changeSelectionMode(SelectionMode.POINT);
				break;

			case COMPILE_SHAPE:
				try {
					Map shapeMap = getGeometryMap();
					Logger.log("Building " + shapeMap.getName() + "_shape...");
					new GeometryCompiler(shapeMap);
					Logger.log("Successfully compiled " + shapeMap.getName() + "_shape");
				}
				catch (BuildException be) {
					SwingUtils.getErrorDialog()
						.setParent(gui)
						.setTitle("Shape Build Failed")
						.setMessage(be.getMessage())
						.showLater();
				}
				catch (IOException ioe) {
					Logger.log("Build failed: IOException. Check log for more information.", Priority.ERROR);
				}
				break;

			case COMPILE_COLLISION:
				try {
					Map hitMap = getCollisionMap();
					Logger.log("Building " + hitMap.getName() + "_hit...");
					new CollisionCompiler(hitMap);
					Logger.log("Successfully compiled " + hitMap.getName() + "_hit");
				}
				catch (BuildException be) {
					SwingUtils.getErrorDialog()
						.setParent(gui)
						.setTitle("Collision Build Failed")
						.setMessage(be.getMessage())
						.showLater();
				}
				catch (IOException ioe) {
					Logger.log("Build failed: IOException. Check log for more information.", Priority.ERROR);
				}
				break;

			case GENERATE_SCRIPT:
				/*
				try {
					Logger.log("Generating script for " + map.name + "...");
					new DecompScriptGenerator(map);
					Logger.log("Successfully generated script for " + map.name);
				}
				catch (IOException ioe) {
					Logger.log("Script compile failed! IOException. Check log for more information.", Priority.ERROR);
					Toolkit.getDefaultToolkit().beep();
				}
				catch (InvalidInputException e) {
					Logger.log("Script compile failed! " + e.getMessage(), Priority.ERROR);
					Toolkit.getDefaultToolkit().beep();
					e.printStackTrace();
				}
				*/
				//TODO
				Logger.logWarning("Not implemented yet!");
				break;

			case SEPARATE_VERTS:
				if (editorMode == EditorMode.Modify) {
					List<Triangle> triangles = selectionManager.getTrianglesFromSelection();
					if (triangles.size() > 0)
						commandManager.executeCommand(new SeparateVertices(triangles));
				}
				break;

			case FUSE_VERTS:
				if (editorMode == EditorMode.Modify) {
					List<Triangle> triangles = selectionManager.getTrianglesFromSelection();
					if (triangles.size() > 0)
						commandManager.executeCommand(new FuseVertices(triangles));
				}
				break;

			case CLEANUP_TRIS:
				if (editorMode == EditorMode.Modify) {
					List<Triangle> triangles = selectionManager.getTrianglesFromSelection();
					if (triangles.size() > 0)
						commandManager.executeCommand(new CleanupTriangles(triangles));
				}
				break;

			case JOIN_MODELS:
				if (editorMode == EditorMode.Modify) {
					List<Model> models = selectionManager.getSelectedObjects(Model.class);
					if (models.size() > 1)
						commandManager.executeCommand(new JoinModels(models));
				}
				break;

			case JOIN_COLLIDERS:
				if (editorMode == EditorMode.Modify) {
					List<Collider> colliders = selectionManager.getSelectedObjects(Collider.class);
					if (colliders.size() > 1)
						commandManager.executeCommand(new JoinColliders(colliders));
				}
				break;

			case JOIN_ZONES:
				if (editorMode == EditorMode.Modify) {
					List<Zone> zones = selectionManager.getSelectedObjects(Zone.class);
					if (zones.size() > 1)
						commandManager.executeCommand(new JoinZones(zones));
				}
				break;

			case SPLIT_MODEL:
				if (editorMode == EditorMode.Modify) {
					List<Triangle> triangles = selectionManager.getTrianglesFromSelection(Model.class);
					if (triangles.size() > 0) {
						CommandBatch batch = new CommandBatch("Split Model");
						batch.addCommand(new SplitModel(triangles));
						batch.addCommand(selectionManager.new SetSelectionMode(SelectionMode.OBJECT));
						commandManager.executeCommand(batch);
					}
				}
				break;

			case SPLIT_COLLIDER:
				if (editorMode == EditorMode.Modify) {
					List<Triangle> triangles = selectionManager.getTrianglesFromSelection(Collider.class);
					if (triangles.size() > 0) {
						CommandBatch batch = new CommandBatch("Split Collider");
						batch.addCommand(new SplitCollider(triangles));
						batch.addCommand(selectionManager.new SetSelectionMode(SelectionMode.OBJECT));
						commandManager.executeCommand(batch);
					}
				}
				break;

			case SPLIT_ZONE:
				if (editorMode == EditorMode.Modify) {
					List<Triangle> triangles = selectionManager.getTrianglesFromSelection(Zone.class);
					if (triangles.size() > 0) {
						CommandBatch batch = new CommandBatch("Split Zone");
						batch.addCommand(new SplitZone(triangles));
						batch.addCommand(selectionManager.new SetSelectionMode(SelectionMode.OBJECT));
						commandManager.executeCommand(batch);
					}
				}
				break;

			case CONVERT_COLLIDER_TO_ZONE:
				if (editorMode == EditorMode.Modify) {
					List<Collider> colliders = selectionManager.getSelectedObjects(Collider.class);
					if (colliders.size() > 0)
						commandManager.executeCommand(new ConvertColliders(colliders));
				}
				break;

			case CONVERT_ZONE_TO_COLLIDER:
				if (editorMode == EditorMode.Modify) {
					List<Zone> zones = selectionManager.getSelectedObjects(Zone.class);
					if (zones.size() > 0)
						commandManager.executeCommand(new ConvertZones(zones));
				}
				break;

			case CREATE_BVH:
				if (editorMode == EditorMode.Modify) {
					List<Collider> colliders = selectionManager.getSelectedObjects(Collider.class);
					if (colliders.size() > 0)
						commandManager.executeCommand(new CreateBVH(colliders));
				}
				break;

			case CREATE_MODEL_GROUP:
				action_CreateModelGroup(map.modelTree.getRoot());
				break;

			case CREATE_COLLIDER_GROUP:
				action_CreateColliderGroup(null);
				break;

			case CREATE_ZONE_GROUP:
				action_CreateZoneGroup(null);
				break;

			case CREATE_MARKER_GROUP:
				action_CreateMarkerGroup(null);
				break;

			case SELECT_ALL_WITH_TEXTURE:
				List<Model> mdlList = new LinkedList<>();
				for (Model mdl : map.modelTree) {
					if (mdl.getMesh().texture == selectionManager.selectedTexture) // name.equals(name) instead?
						mdlList.add(mdl);
				}
				if (!mdlList.isEmpty())
					commandManager.executeCommand(selectionManager.getSetObjects(mdlList));
				break;

			case SET_ALL_RENDER_MODE:
				if (editorMode == EditorMode.Modify) {
					List<Model> models = selectionManager.getSelectedObjects(Model.class);
					if (models.size() > 0) {
						SwingUtilities.invokeLater(() -> {
							JComboBox<RenderMode> selectionBox = new JComboBox<>(RenderMode.getEditorModes());
							selectionBox.setSelectedItem(models.get(models.size() - 1).renderMode.get());

							int choice = SwingUtils.getConfirmDialog()
								.setParent(gui)
								.setCounter(gui.getDialogCounter())
								.setTitle("Set Render Mode")
								.setMessage(selectionBox)
								.setMessageType(JOptionPane.PLAIN_MESSAGE)
								.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
								.choose();

							RenderMode selectedMode = (RenderMode) selectionBox.getSelectedItem();

							if (choice == JOptionPane.YES_OPTION && selectedMode != null) {
								CommandBatch batch = new CommandBatch();
								for (Model mdl : models)
									batch.addCommand(mdl.renderMode.mutator(selectedMode));
								executeNextFrame(batch);
							}
						});
					}
				}
				break;

			default:
				throw new RuntimeException("Editor cannot execute GUI command " + cmd);
		}
	}

	/**
	 * This method handles mouse movement. Should only be called by {@link MouseManager}. Under ordinary circumstances,
	 * moving the mouse around only changes the active viewport. When either LMB or RMB are being held, the viewport does
	 * not change. These click+drag events are used to transform the selection maintained by the selectionManager class.
	 *
	 * @param dx
	 * @param dy
	 */
	@Override
	public void moveMouse(int dx, int dy)
	{
		// switch active viewport
		boolean holding = mouse.isHoldingLMB() || mouse.isHoldingRMB() || mouse.isHoldingMMB();
		int mouseX = mouse.getPosX();
		int mouseY = mouse.getPosY();

		if (!holding) {
			switch (viewMode) {
				case ONE:
					break;
				case UVEDIT:
					for (MapEditViewport v : uvViews)
						if ((v.contains(mouseX, mouseY)) && (activeView != v)) {
							activeView.release();
							activeView = v;
							break;
						}
					break;
				case FOUR:
					for (MapEditViewport v : fourViews)
						if ((v.contains(mouseX, mouseY)) && (activeView != v)) {
							activeView.release();
							activeView = v;
							break;
						}
					break;
				case PREVIEW:
					for (MapEditViewport v : previewViews)
						if ((v.contains(mouseX, mouseY)) && (activeView != v)) {
							activeView.release();
							activeView = v;
							break;
						}
					break;
			}
		}

		// resize the viewports
		if (viewMode != ViewMode.ONE && resizingViews) {
			if (enableXDivider) {
				int width = glCanvas.getWidth();
				int hDiv = (int) (hDivRatio * width);
				hDiv += mouse.getFrameDX();
				hDivRatio = (float) hDiv / width;
				if (hDivRatio < 0.2f)
					hDivRatio = 0.2f;
				if (hDivRatio > 0.8f)
					hDivRatio = 0.8f;
				hDiv = (int) (hDivRatio * width);
			}

			if (enableYDivider) {
				int height = glCanvas.getHeight();
				int vDiv = (int) (vDivRatio * height);
				vDiv += mouse.getFrameDY();
				vDivRatio = (float) vDiv / height;
				if (vDivRatio < 0.2f)
					vDivRatio = 0.2f;
				if (vDivRatio > 0.8f)
					vDivRatio = 0.8f;
				vDiv = (int) (vDivRatio * height);
			}

			resizeViews();
		}

		// respond to mouse motion depending on current editor mode
		if (!resizingViews) {
			Vector3f vec;
			switch (editorMode) {
				case Modify:
				case Scripts:
					vec = activeView.camera.getTranslationVector(dx, dy);
					updateCurrentDrag(selectionManager.currentSelection, vec, dx, dy);
					break;
				case EditUVs:
					vec = uvEditView.camera.getTranslationVector(dx, dy);
					updateCurrentDrag(selectionManager.uvSelection, vec, dx, dy);
					break;
				case Texture:
					break;
				case VertexPaint:
					if (mouse.isHoldingLMB())
						paintVertices();
					break;
			}

			if (mouse.isHoldingMMB()) {
				clickHitMMB = selectionManager.pickWorld(getGeometryMap(), currentRay, activeView, true);

				if (!clickHitMMB.missed())
					cursor3D.updateDrag(clickHitMMB.point);
			}
		}
	}

	private void updateCurrentDrag(Selection<?> selection, Vector3f vec, int rawDx, int rawDy)
	{
		if (mouse.isHoldingLMB()) {
			// translating selection
			if (selection.transforming()) {
				if (rescaling)
					selection.updateScale(activeView, vec);
				else if (vertexSnap && activeView instanceof OrthographicViewport ortho) {
					Iterable<Vertex> vertices;
					if (vertexSnapLimit)
						vertices = map.getVerticesWithinVolume(ortho.getViewingVolume(), selectionManager.getObjectType());
					else
						vertices = map.getVerticesWithinVolume(ortho.getViewingVolume());

					ArrayList<Vector3f> positions = new ArrayList<>();
					for (Vertex v : vertices) {
						if (!v.isSelected())
							positions.add(v.getCurrentPos());
					}
					selection.updateTranslation(activeView, vec, rawDx, rawDy, deltaTime, ortho, positions);
				}
				else
					selection.updateTranslation(activeView, vec, rawDx, rawDy, deltaTime);
			}
			else if (draggingBox)
				dragBoxEndPoint = new Vector3f(currentRay.origin);
		}

		// rotate selection
		if (mouse.isHoldingRMB()) {
			Vector3f clickPoint = new Vector3f(currentRay.origin);
			if (rescaling)
				selection.updateScale(activeView, vec);
			else
				selection.updateRotation(activeView, clickPoint);
		}
	}

	@Override
	public void mouseEnter()
	{
		// gl canvas grabs focus on mouse enter if an app window is active
		// however, do not grab focus if the mouse enter is triggered from another app
		for (Window w : Window.getWindows()) {
			if (w.isActive()) {
				// found an active window associated with this app
				glCanvas.requestFocus();
				gui.closePopupMenus();
				break;
			}
		}

		//	keyboard.reset(this);
		//	mouse.reset(this);
	}

	@Override
	public void mouseExit()
	{
		activeView.release();
	}

	@Override
	public void clickLMB()
	{
		clickRayLMB = currentRay;

		// allow viewport resizing
		resizingViews = false;
		if (enableXDivider) {
			int xDiv = (int) (hDivRatio * glCanvas.getWidth());
			int mouseX = mouse.getPosX();
			if (mouseX > xDiv - BORDER_HALF_SIZE && mouseX < xDiv + BORDER_HALF_SIZE)
				resizingViews = true;
		}
		if (enableYDivider) {
			int yDiv = (int) (vDivRatio * glCanvas.getHeight());
			int mouseY = mouse.getPosY();
			if (mouseY > yDiv - BORDER_HALF_SIZE && mouseY < yDiv + BORDER_HALF_SIZE)
				resizingViews = true;
		}
		if (resizingViews)
			return;

		// handle the click depending on current mode
		switch (editorMode) {
			case Modify:
				if (!selectionManager.currentSelection.transforming()) {
					if (drawTriManager.isActive()) {
						drawTriManager.tryAddVertex(clickRayLMB.origin, activeView);
					}
					else {
						clickHitLMB = selectionManager.pickCurrentSelection(map, clickRayLMB, activeView, false, true);
						dragBoxStartPoint = new Vector3f(clickRayLMB.origin);
					}
				}
				break;

			case Scripts:
				if (!selectionManager.currentSelection.transforming()) {
					clickHitLMB = selectionManager.pickCurrentSelection(map, clickRayLMB, activeView, false, true);
					dragBoxStartPoint = new Vector3f(clickRayLMB.origin);
				}
				break;

			case EditUVs:
				if (activeView == uvEditView) {
					if (!selectionManager.uvSelection.transforming()) {
						clickHitLMB = selectionManager.pickUV(map, clickRayLMB, uvEditView, !keyboard.isKeyDown(SCALE_KEY));
						dragBoxStartPoint = new Vector3f(clickRayLMB.origin);
					}
				}
				else {
					if (!selectionManager.uvSelection.transforming()) {
						// changing set of selected objects
						selectionManager.uvSelection.clear();
						clickHitLMB = selectionManager.pickCurrentSelection(map, clickRayLMB, activeView, true, true);
						updateUVViewport();
					}
				}
				break;

			case Texture:
				PickHit hit = Map.pickObjectFromSet(clickRayLMB, map.modelTree);
				if (!hit.missed()) {
					MapObject obj = (MapObject) hit.obj;
					setSelectedTexture(((Model) obj).getMesh().texture);
				}
				break;

			case VertexPaint:
				startPainting();
				paintVertices();
				break;
		}
	}

	@Override
	public void releaseLMB()
	{
		resizingViews = false;
		clickRayLMB = null;
		clickHitLMB = null;

		switch (editorMode) {
			case Modify:
				break;
			case Texture:
				break;
			case Scripts:
				break;
			case EditUVs:
				break;
			case VertexPaint:
				finishPainting();
				break;
		}
	}

	@Override
	public void startHoldingLMB()
	{
		if (resizingViews || drawTriManager.isActive())
			return;

		if (clickHitLMB == null)
			return;

		switch (editorMode) {
			case Modify:
				// TODO
				// if(selectionPainting)
				// ...
				// else
				startTranslateScale(selectionManager.currentSelection, clickHitLMB);
				break;
			case EditUVs:
				startTranslateScale(selectionManager.uvSelection, clickHitLMB);
				break;
			case Texture:
				break;
			case Scripts:
				startTranslateScale(selectionManager.currentSelection, clickHitLMB);
				break;
			case VertexPaint:
				paintVertices();
				break;
		}
	}

	private void startTranslateScale(Selection<?> selection, PickHit clickHit)
	{
		if (!selection.transforming()) {
			if (!selection.isEmpty() && !keyboard.isCtrlDown()) {
				boolean allowClone = (editorMode == EditorMode.Modify);
				if (allowClone && keyboard.isKeyDown(CLONE_KEY))
					selectionManager.cloneSelection();

				rescaling = keyboard.isKeyDown(SCALE_KEY);

				if (rescaling)
					selection.startScale(clickHit.point, false);
				else
					selection.startTranslation(clickHit.obj, false);
			}
			else if (activeView.allowsDragSelection()) {
				dragBoxEndPoint = new Vector3f(currentRay.origin);
				draggingBox = true;
			}
		}
	}

	@Override
	public void stopHoldingLMB()
	{
		if (drawTriManager.isActive())
			return;

		resizingViews = false;

		switch (editorMode) {
			case Modify:
				selectionManager.currentSelection.endTransform();
				rescaling = false;

				if (draggingBox) {
					BoundingBox selectionVolume = activeView.getDragSelectionVolume(dragBoxStartPoint, dragBoxEndPoint);
					selectionManager.selectAllWithinBox(map, selectionVolume);
					draggingBox = false;
				}
				break;

			case EditUVs:
				selectionManager.uvSelection.endTransform();
				rescaling = false;

				if (draggingBox) {
					BoundingBox selectionVolume = activeView.getDragSelectionVolume(dragBoxStartPoint, dragBoxEndPoint);
					selectionManager.selectUVsWithinBox(selectionVolume);
					draggingBox = false;
				}
				break;

			case Scripts:
				selectionManager.currentSelection.endTransform();
				rescaling = false;
				draggingBox = false;
				break;

			case Texture:
				break;
			case VertexPaint:
				finishPainting();
				break;
		}
	}

	@Override
	public void clickRMB()
	{
		clickRayRMB = currentRay;

		switch (editorMode) {
			case Modify:
				if (!selectionManager.currentSelection.transforming())
					clickHitRMB = selectionManager.pickCurrentSelection(map, clickRayRMB, activeView, false, false);
				break;
			case EditUVs:
				if (!selectionManager.uvSelection.transforming())
					clickHitRMB = selectionManager.pickUV(map, clickRayRMB, uvEditView, false);
				break;
			case Scripts:
				break;
			case Texture:
				PickHit hit = Map.pickObjectFromSet(clickRayRMB, map.modelTree);
				if (!hit.missed()) {
					Model mdl = (Model) hit.obj;
					List<Model> mdlList = selectionManager.getSelectedObjects(Model.class);
					if (mdlList.size() > 0 && mdlList.contains(mdl))
						commandManager.executeCommand(new ApplyTextureToList(mdlList, selectionManager.selectedTexture));
					else
						commandManager.executeCommand(new ApplyTexture(mdl, selectionManager.selectedTexture));
				}
				break;
			case VertexPaint:
				pickVertexColor();
				break;
		}
	}

	@Override
	public void releaseRMB()
	{
		clickRayRMB = null;
		clickHitRMB = null;
	}

	@Override
	public void startHoldingRMB()
	{
		Selection<?> selection;
		switch (editorMode) {
			case Modify:
			case Scripts:
				selection = selectionManager.currentSelection;
				if (!selection.isEmpty() && !selection.transforming()) {
					boolean allowClone = (editorMode == EditorMode.Modify);
					if (allowClone && keyboard.isKeyDown(CLONE_KEY))
						selectionManager.cloneSelection();

					rescaling = keyboard.isKeyDown(SCALE_KEY);

					if (rescaling)
						selection.startScale(clickHitRMB.point, true);
					else if (activeView != perspectiveView) {
						Axis axis = activeView.camera.getRotationAxis();
						selection.startRotation(axis, clickRayRMB.origin);
					}
				}
				break;

			case EditUVs:
				selection = selectionManager.uvSelection;
				if (!selection.isEmpty() && !selection.transforming()) {
					rescaling = keyboard.isKeyDown(SCALE_KEY);

					if (rescaling) {
						Vector3f mousePosition = new Vector3f(currentRay.origin.x, currentRay.origin.y, 0.0f);
						selection.startScale(mousePosition, true);
					}
					else {
						Axis axis = uvEditView.camera.getRotationAxis();
						selection.startRotation(axis, clickRayRMB.origin);
					}
				}
				break;

			case Texture:
				break;
			case VertexPaint:
				break;
		}
	}

	@Override
	public void stopHoldingRMB()
	{
		switch (editorMode) {
			case Modify:
			case Scripts:
				selectionManager.currentSelection.endTransform();
				rescaling = false;
				break;
			case EditUVs:
				selectionManager.uvSelection.endTransform();
				rescaling = false;
				break;
			case Texture:
				break;
			case VertexPaint:
				break;
		}
	}

	@Override
	public void clickMMB()
	{
		clickRayMMB = currentRay;
		clickHitMMB = selectionManager.pickWorld(getGeometryMap(), currentRay, activeView, true);

		if (!clickHitMMB.missed())
			cursor3D.setPosition(clickHitMMB.point);
	}

	@Override
	public void releaseMMB()
	{
		clickRayMMB = null;
		clickHitMMB = null;
	}

	@Override
	public void startHoldingMMB()
	{
		clickHitMMB = selectionManager.pickWorld(getGeometryMap(), currentRay, activeView, true);

		if (!clickHitMMB.missed())
			cursor3D.startDrag(clickHitMMB.point);
	}

	@Override
	public void stopHoldingMMB()
	{
		cursor3D.endDrag();

		clickRayMMB = null;
		clickHitMMB = null;
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent e)
	{
		if (e.getID() == KeyEvent.KEY_PRESSED) {
			switch (e.getKeyCode()) {
				case KeyEvent.VK_Z:
					if (e.isControlDown())
						enqueueKeyEvent(EditorShortcut.UNDO);
					break;
				case KeyEvent.VK_Y:
					if (e.isControlDown())
						enqueueKeyEvent(EditorShortcut.REDO);
					break;
			}
		}
		return false;
	}

	@Override
	public void keyPress(KeyInputEvent evt)
	{
		handleKeyEvent(evt, true);
	}

	@Override
	public void keyRelease(KeyInputEvent evt)
	{
		handleKeyEvent(evt, false);
	}

	// map key events to symbolic shortcuts and dispatch them
	private void handleKeyEvent(KeyInputEvent key, boolean press)
	{
		boolean ctrl = keyboard.isCtrlDown();
		boolean shift = keyboard.isShiftDown();

		if (key.code == KeyEvent.VK_CONTROL || key.code == KeyEvent.VK_SHIFT)
			return;

		if (ctrl && shift)
			return;

		// don't allow key events during transformations
		switch (editorMode) {
			default:
				if (selectionManager.currentSelection.transforming())
					return;
				break;
			case EditUVs:
				if (selectionManager.uvSelection.transforming())
					return;
				break;
		}

		EditorShortcut shortcut = null;
		if (ctrl)
			shortcut = EditorShortcut.getCtrl(key.code);
		else if (shift)
			shortcut = EditorShortcut.getShift(key.code);
		else
			shortcut = EditorShortcut.get(key.code);

		if (shortcut != null) {
			if (press)
				handleShortcutPress(shortcut, false);
			else
				handleShortcutRelease(shortcut);
		}
	}

	private Vector3f getNudgeVector(NudgeDirection dir)
	{
		switch (dir) {
			case UP:
				if (activeView != perspectiveView)
					return activeView.camera.getTranslationVector(0, 1);
				else
					return new Vector3f(0.0f, 0.0f, -1.0f);

			case DOWN:
				if (activeView != perspectiveView)
					return activeView.camera.getTranslationVector(0, -1);
				else
					return new Vector3f(0.0f, 0.0f, 1.0f);

			case LEFT:
				if (activeView != perspectiveView)
					return activeView.camera.getTranslationVector(-1, 0);
				else
					return new Vector3f(-1.0f, 0.0f, 0.0f);

			case RIGHT:
				if (activeView != perspectiveView)
					return activeView.camera.getTranslationVector(1, 0);
				else
					return new Vector3f(1.0f, 0.0f, 0.0f);

			case IN:
				if (activeView != perspectiveView) {
					if (activeView.type == ViewType.FRONT)
						return activeView.camera.getForward(-1.0f);
					else
						return activeView.camera.getForward(1.0f);
				}
				else
					return new Vector3f(0.0f, 1.0f, 0.0f);

			case OUT:
				if (activeView != perspectiveView) {
					if (activeView.type == ViewType.FRONT)
						return activeView.camera.getForward(1.0f);
					else
						return activeView.camera.getForward(-1.0f);
				}
				else
					return new Vector3f(0.0f, -1.0f, 0.0f);
		}

		throw new IllegalStateException("Can't determine nudge vector for " + dir);
	}

	private void startKeyboardTranslation()
	{
		Selection<?> selection = getSelectionForCurrentMode();

		if (selection != null && !selection.transforming() && !selection.isEmpty()) {
			selection.startTranslation(null, true);
			doingNudgeTranslation = true;
		}
	}

	private void updateKeyboardTranslation()
	{
		Selection<?> selection = getSelectionForCurrentMode();
		if (selection == null)
			return;

		if (!canDoNudgeTranslation)
			return;

		Vector3f keyboardTranslateVec = new Vector3f();
		Vector3f translateDir;
		int keysHeld = 0;

		if (keyboard.isKeyDown(NUDGE_UP.key)) {
			if (!selection.transforming()) {
				startKeyboardTranslation();
				return;
			}

			translateDir = getNudgeVector(NudgeDirection.UP);
			keyboardTranslateVec.add(translateDir);
			keysHeld++;
		}

		if (keyboard.isKeyDown(NUDGE_DOWN.key)) {
			if (!selection.transforming()) {
				startKeyboardTranslation();
				return;
			}

			translateDir = getNudgeVector(NudgeDirection.DOWN);
			keyboardTranslateVec.add(translateDir);
			keysHeld++;
		}

		if (keyboard.isKeyDown(NUDGE_LEFT.key)) {
			if (!selection.transforming()) {
				startKeyboardTranslation();
				return;
			}

			translateDir = getNudgeVector(NudgeDirection.LEFT);
			keyboardTranslateVec.add(translateDir);
			keysHeld++;
		}

		if (keyboard.isKeyDown(NUDGE_RIGHT.key)) {
			if (!selection.transforming()) {
				startKeyboardTranslation();
				return;
			}

			translateDir = getNudgeVector(NudgeDirection.RIGHT);
			keyboardTranslateVec.add(translateDir);
			keysHeld++;
		}

		if (keyboard.isKeyDown(NUDGE_IN.key)) {
			if (!selection.transforming()) {
				startKeyboardTranslation();
				return;
			}

			translateDir = getNudgeVector(NudgeDirection.IN);
			keyboardTranslateVec.add(translateDir);
			keysHeld++;
		}

		if (keyboard.isKeyDown(NUDGE_OUT.key)) {
			if (!selection.transforming()) {
				startKeyboardTranslation();
				return;
			}

			translateDir = getNudgeVector(NudgeDirection.OUT);
			keyboardTranslateVec.add(translateDir);
			keysHeld++;
		}

		if (!doingNudgeTranslation)
			return;

		if (keysHeld == 0) {
			// end translation
			if (selection != null)
				selection.endTransform();
			doingNudgeTranslation = false;
			return;
		}

		keyboardTranslateVec.normalize();

		float speed = 300.0f;
		if (activeView instanceof OrthographicViewport ortho)
			speed = ortho.getViewWorldSizeX() / 2.0f;

		keyboardTranslateVec.scale((float) (speed * deltaTime));
		selection.updateTranslation(activeView, keyboardTranslateVec, 1, 1, deltaTime); //TODO check rawDx, rawDy
	}

	private void handleShortcutRelease(EditorShortcut key)
	{
		switch (key) {
			case NUDGE_UP:
			case NUDGE_DOWN:
			case NUDGE_LEFT:
			case NUDGE_RIGHT:
			case NUDGE_IN:
			case NUDGE_OUT:
				canDoNudgeTranslation = true;
				break;

			case SELECTION_PAINTING:
				selectionPainting = false;
				break;

			case PLAY_IN_EDITOR_JUMP:
				cursor3D.endInputJump();
				break;

			default:
				// nothing
				break;
		}
	}

	private void handleShortcutPress(EditorShortcut key, boolean fromGui)
	{
		switch (key) {
			case UNDO:
				commandManager.action_Undo();
				break;
			case REDO:
				commandManager.action_Redo();
				break;
			case SELECT_ALL:
				selectAll();
				break;
			case FIND_OBJECT:
				findObject();
				break;

			case COPY_OBJECTS:
				copyObjects();
				break;

			case PASTE_OBJECTS:
				pasteObjects();
				break;

			case DUPLICATE_SELECTED:
				duplicateSelection();
				break;

			case DELETE_SELECTED:
				deleteSelection();
				break;

			case FLIP_SELECTED_X:
				flipSelection(Axis.X);
				break;

			case FLIP_SELECTED_Y:
				flipSelection(Axis.Y);
				break;

			case FLIP_SELECTED_Z:
				flipSelection(Axis.Z);
				break;

			case FLIP_NORMALS:
				if (editorMode == EditorMode.Modify) {
					List<Triangle> triangles = selectionManager.getTrianglesFromSelection();
					if (triangles.size() > 0)
						commandManager.executeCommand(new InvertNormals(triangles));
				}
				break;

			case NORMALS_TO_CAMERA:
				normalsToCamera();
				break;

			case ROUND_VERTICIES:
				roundVertices();
				break;

			case SELECTION_PAINTING:
				selectionPainting = true;
				break;

			case NUDGE_UP:
				canDoNudgeTranslation = false;
				nudge(getNudgeVector(NudgeDirection.UP));
				break;

			case NUDGE_DOWN:
				canDoNudgeTranslation = false;
				nudge(getNudgeVector(NudgeDirection.DOWN));
				break;

			case NUDGE_LEFT:
				canDoNudgeTranslation = false;
				nudge(getNudgeVector(NudgeDirection.LEFT));
				break;

			case NUDGE_RIGHT:
				canDoNudgeTranslation = false;
				nudge(getNudgeVector(NudgeDirection.RIGHT));
				break;

			case NUDGE_IN:
				canDoNudgeTranslation = false;
				nudge(getNudgeVector(NudgeDirection.IN));
				break;

			case NUDGE_OUT:
				canDoNudgeTranslation = false;
				nudge(getNudgeVector(NudgeDirection.OUT));
				break;

			case TOGGLE_INFO_PANEL:
				SwingUtilities.invokeLater(() -> {
					gui.toggleInfoPanel();
				});
				break;

			case OPEN_TRANSFORM_DIALOG:
				if (!selectionManager.getSelectedObjects().isEmpty()) {
					SwingUtilities.invokeLater(() -> {
						Selection<?> selection = (editorMode == EditorMode.EditUVs) ? selectionManager.uvSelection
							: selectionManager.currentSelection;
						gui.prompt_TransformSelection(selection);
					});
				}
				break;

			case TOGGLE_TWO_SIDED:
				List<Triangle> triangles = selectionManager.getTrianglesFromSelection();
				if (triangles.size() > 0)
					commandManager.executeCommand(new ToggleDoubleSided(triangles));
				break;

			case TOGGLE_UV_EDIT:
				switch (editorMode) {
					case Modify:
						commandManager.executeCommand(new ChangeMode(EditorMode.EditUVs));
						break;
					case EditUVs:
						commandManager.executeCommand(new ChangeMode(EditorMode.Modify));
						break;
					default:
				}
				break;

			case PLAY_IN_EDITOR_TOGGLE: {
				if (changeMapState != ChangeMapState.NONE) {
					// cant exit PIE during map transition
					isPlayInEditorMode = true;
					key.setCheckbox(isPlayInEditorMode);
					break;
				}

				if (!isPlayInEditorMode) {
					cursor3D.startPreviewMode();
					isPlayInEditorMode = true;
				}
				else {
					cursor3D.endPreviewMode();
					isPlayInEditorMode = false;
				}

				if (!fromGui)
					key.setCheckbox(isPlayInEditorMode);

			}
				break;

			case PLAY_IN_EDITOR_JUMP:
				if (isPlayInEditorMode)
					cursor3D.startInputJump();
				break;

			case PIE_IGNORE_HIDDEN_COL:
				pieIgnoreHiddenColliders = !pieIgnoreHiddenColliders;
				if (!fromGui)
					key.setCheckbox(pieIgnoreHiddenColliders);
				break;

			case PIE_IGNORE_HIDDEN_ZONE:
				pieIgnoreHiddenZones = !pieIgnoreHiddenZones;
				if (!fromGui)
					key.setCheckbox(pieIgnoreHiddenZones);
				break;

			case PIE_SHOW_ACTIVE_CAMERA:
				pieDrawCameraInfo = !pieDrawCameraInfo;
				if (!fromGui)
					key.setCheckbox(pieDrawCameraInfo);
				break;

			case PIE_ENABLE_MAP_EXITS:
				pieEnableMapExits = !pieEnableMapExits;
				if (!fromGui)
					key.setCheckbox(pieEnableMapExits);
				break;

			case OPEN_MODEL_TAB:
				if (editorMode == EditorMode.Modify)
					gui.setObjectTab(MapObjectType.MODEL);
				break;

			case OPEN_COLLIDER_TAB:
				if (editorMode == EditorMode.Modify)
					gui.setObjectTab(MapObjectType.COLLIDER);
				break;

			case OPEN_ZONE_TAB:
				if (editorMode == EditorMode.Modify)
					gui.setObjectTab(MapObjectType.ZONE);
				break;

			case OPEN_MARKER_TAB:
				if (editorMode == EditorMode.Modify)
					gui.setObjectTab(MapObjectType.MARKER);
				break;

			case SELECT_OBJECTS:
				changeSelectionMode(SelectionMode.OBJECT);
				break;

			case SELECT_TRIANGLES:
				changeSelectionMode(SelectionMode.TRIANGLE);
				break;

			case SELECT_VERTICIES:
				changeSelectionMode(SelectionMode.VERTEX);
				break;
			case SELECT_POINTS:
				changeSelectionMode(SelectionMode.POINT);
				break;

			case TOGGLE_GRID:
				gridEnabled = !gridEnabled;
				if (!fromGui)
					key.setCheckbox(gridEnabled);
				gui.updateGridSize();
				break;

			case TOGGLE_GRID_TYPE:
				objectGrid.toggleType();
				if (!fromGui)
					key.setCheckbox(!objectGrid.binary);
				gui.post("Using " + (objectGrid.binary ? "binary" : "decimal") + " grid");
				gui.updateGridSize();
				break;

			case INCREASE_GRID_POWER:
				grid.increasePower();
				gui.updateGridSize();
				break;

			case DECREASE_GRID_POWER:
				grid.decreasePower();
				gui.updateGridSize();
				break;

			case SNAP_TRANSLATION:
				snapTranslation = !snapTranslation;
				if (!fromGui)
					key.setCheckbox(snapTranslation);
				gui.post("Snap translation " + (snapTranslation ? "enabled" : "disabled"));
				gui.updateSnapLabel();
				break;

			case SNAP_ROTATION:
				snapRotation = !snapRotation;
				if (!fromGui)
					key.setCheckbox(snapRotation);
				gui.post("Snap rotation " + (snapRotation ? "enabled" : "disabled"));
				gui.updateSnapLabel();
				break;

			case SNAP_SCALE:
				snapScale = !snapScale;
				if (!fromGui)
					key.setCheckbox(snapScale);
				gui.post("Snap scale " + (snapScale ? "enabled" : "disabled"));
				gui.updateSnapLabel();
				break;

			case SNAP_SCALE_GRID:
				snapScaleToGrid = !snapScaleToGrid;
				if (!fromGui)
					key.setCheckbox(snapScaleToGrid);
				gui.post("Snap scale to grid " + (snapScaleToGrid ? "enabled" : "disabled"));
				gui.updateSnapLabel();
				break;

			case VERTEX_SNAP:
				vertexSnap = !vertexSnap;
				if (!fromGui)
					key.setCheckbox(vertexSnap);
				gui.post("Snap vertices " + (vertexSnap ? "enabled" : "disabled"));
				gui.updateSnapLabel();
				break;

			case VERTEX_SNAP_LIMIT:
				vertexSnapLimit = !vertexSnapLimit;
				if (!fromGui)
					key.setCheckbox(vertexSnapLimit);
				gui.post("Snap vertices to " + (vertexSnapLimit ? "like objects" : "all objects"));
				gui.updateSnapLabel();
				break;

			case MOVE_MARKER_POINTS:
				Marker.movePointsWithObject = !Marker.movePointsWithObject;
				if (!fromGui)
					key.setCheckbox(Marker.movePointsWithObject);
				break;

			case SHOW_MODELS: {
				boolean shouldHide = fromGui ? !key.getCheckBox().isSelected() : showModels;
				commandManager.executeCommand(
					new HideObjectTree("Models", map.modelTree, key.getCheckBox(), (b) -> showModels = b, shouldHide));
			}
				break;

			case SHOW_COLLIDERS: {
				boolean shouldHide = fromGui ? !key.getCheckBox().isSelected() : showColliders;
				commandManager.executeCommand(
					new HideObjectTree("Colliders", map.colliderTree, key.getCheckBox(), (b) -> showColliders = b, shouldHide));
			}
				break;

			case SHOW_ZONES: {
				boolean shouldHide = fromGui ? !key.getCheckBox().isSelected() : showZones;
				commandManager.executeCommand(
					new HideObjectTree("Zones", map.zoneTree, key.getCheckBox(), (b) -> showZones = b, shouldHide));
			}
				break;

			case SHOW_MARKERS: {
				boolean shouldHide = fromGui ? !key.getCheckBox().isSelected() : showMarkers;
				commandManager.executeCommand(
					new HideObjectTree("Markers", map.markerTree, key.getCheckBox(), (b) -> showMarkers = b, shouldHide));
			}
				break;

			case SHOW_ONLY_MODELS: {
				CommandBatch batch = new CommandBatch("Show Only Models");
				if (!showModels)
					batch.addCommand(new HideObjectTree("Models", map.modelTree, SHOW_MODELS.getCheckBox(), (b) -> showModels = b, false));
				batch.addCommand(
					new HideObjectTree("Colliders", map.colliderTree, SHOW_COLLIDERS.getCheckBox(), (b) -> showColliders = b, true));
				batch.addCommand(new HideObjectTree("Zones", map.zoneTree, SHOW_ZONES.getCheckBox(), (b) -> showZones = b, true));
				batch.addCommand(new HideObjectTree("Markers", map.markerTree, SHOW_MARKERS.getCheckBox(), (b) -> showMarkers = b, true));
				execute(batch);
			}
				break;

			case SHOW_ONLY_COLLIDERS: {
				CommandBatch batch = new CommandBatch("Show Only Colliders");
				batch.addCommand(new HideObjectTree("Models", map.modelTree, SHOW_MODELS.getCheckBox(), (b) -> showModels = b, true));
				if (!showColliders)
					batch.addCommand(
						new HideObjectTree("Colliders", map.colliderTree, SHOW_COLLIDERS.getCheckBox(), (b) -> showColliders = b, false));
				batch.addCommand(new HideObjectTree("Zones", map.zoneTree, SHOW_ZONES.getCheckBox(), (b) -> showZones = b, true));
				batch.addCommand(new HideObjectTree("Markers", map.markerTree, SHOW_MARKERS.getCheckBox(), (b) -> showMarkers = b, true));
				execute(batch);
			}
				break;

			case SHOW_ONLY_ZONES: {
				CommandBatch batch = new CommandBatch("Show Only Zones");
				batch.addCommand(new HideObjectTree("Models", map.modelTree, SHOW_MODELS.getCheckBox(), (b) -> showModels = b, true));
				batch.addCommand(
					new HideObjectTree("Colliders", map.colliderTree, SHOW_COLLIDERS.getCheckBox(), (b) -> showColliders = b, true));
				if (!showZones)
					batch.addCommand(new HideObjectTree("Zones", map.zoneTree, SHOW_ZONES.getCheckBox(), (b) -> showZones = b, false));
				batch.addCommand(new HideObjectTree("Markers", map.markerTree, SHOW_MARKERS.getCheckBox(), (b) -> showMarkers = b, true));
				execute(batch);
			}
				break;

			case SHOW_ONLY_MARKERS: {
				CommandBatch batch = new CommandBatch("Show Only Markers");
				batch.addCommand(new HideObjectTree("Models", map.modelTree, SHOW_MODELS.getCheckBox(), (b) -> showModels = b, true));
				batch.addCommand(
					new HideObjectTree("Colliders", map.colliderTree, SHOW_COLLIDERS.getCheckBox(), (b) -> showColliders = b, true));
				batch.addCommand(new HideObjectTree("Zones", map.zoneTree, SHOW_ZONES.getCheckBox(), (b) -> showZones = b, true));
				if (!showMarkers)
					batch.addCommand(new HideObjectTree("Markers", map.markerTree, SHOW_MARKERS.getCheckBox(), (b) -> showMarkers = b, false));
				execute(batch);
			}
				break;

			case SHOW_AABB:
				showBoundingBoxes = !showBoundingBoxes;
				if (!fromGui)
					key.setCheckbox(showBoundingBoxes);
				break;

			case SHOW_NORMALS:
				showNormals = !showNormals;
				if (!fromGui)
					key.setCheckbox(showNormals);
				break;

			case SHOW_AXES:
				showAxes = !showAxes;
				break;

			case SHOW_GIZMO:
				showGizmo = !showGizmo;
				break;

			case SHOW_ENTITY_COLLISION:
				showEntityCollision = !showEntityCollision;
				break;

			case USE_COLLIDER_COLORS:
				useColliderColoring = !useColliderColoring;
				break;

			case USE_GAME_ASPECT_RATIO:
				useGameAspectRatio = !useGameAspectRatio;
				resizeViews(); // force recalculation of camera perspective matricies
				break;

			case USE_MAP_CAM_PROPERTIES:
				useMapCameraProperties = !useMapCameraProperties;
				resizeViews(); // force recalculation of camera perspective matricies
				break;

			case USE_MAP_BG_COLOR:
				useMapBackgroundColor = !useMapBackgroundColor;
				break;

			case USE_GEOMETRY_FLAGS:
				useGeometryFlags = !useGeometryFlags;
				if (!fromGui)
					key.setCheckbox(useGeometryFlags);
				break;

			case USE_FILTERING:
				useFiltering = !useFiltering;
				break;

			case USE_TEXTURE_LOD:
				useTextureLOD = !useTextureLOD;
				break;

			case HIDE_SELECTED:
				if (selectionManager.getSelectionMode() == SelectionMode.OBJECT) {
					List<MapObject> objs = selectionManager.getSelectedObjects();
					commandManager.executeCommand(new HideObjects(objs));
				}
				break;

			case RESET_LAYOUT:
				hDivRatio = 0.5f;
				vDivRatio = 0.5f;
				resizeViews();
				break;

			case CENTER_VIEW:
				if (selectionManager.currentSelection.isEmpty())
					break;
				switch (viewMode) {
					case ONE:
						activeView.camera.centerOn(selectionManager.currentSelection.aabb);
						break;
					case UVEDIT:
						perspectiveView.camera.centerOn(selectionManager.currentSelection.aabb);
						break;
					case FOUR:
						fourViews[0].camera.centerOn(selectionManager.currentSelection.aabb);
						fourViews[1].camera.centerOn(selectionManager.currentSelection.aabb);
						fourViews[2].camera.centerOn(selectionManager.currentSelection.aabb);
						fourViews[3].camera.centerOn(selectionManager.currentSelection.aabb);
						break;
					case PREVIEW:
						previewViews[1].camera.centerOn(selectionManager.currentSelection.aabb);
						previewViews[2].camera.centerOn(selectionManager.currentSelection.aabb);
						previewViews[3].camera.centerOn(selectionManager.currentSelection.aabb);
						break;
				}
				break;

			case TOGGLE_QUADVIEW:
				switch (editorMode) {
					case Modify:
					case Texture:
					case Scripts:
					case VertexPaint:
						if (mainViewMode == ViewMode.FOUR)
							mainViewMode = ViewMode.ONE;
						else if (mainViewMode == ViewMode.ONE)
							mainViewMode = ViewMode.FOUR;
						else
							throw new IllegalStateException("Main view mode can only be 1 or 4");
						setViewMode(mainViewMode);
						break;
					case EditUVs:
						break;
				}
				break;

			case TOGGLE_WIREFRAME:
				activeView.wireframeMode = !activeView.wireframeMode;
				break;

			case TOGGLE_EDGES:
				edgeHighlights = !edgeHighlights;
				break;

			case SAVE:
				action_SaveMap();
				break;

			case SWITCH:
				exitCompletely = false;
			case QUIT:
				WindowEvent closingEvent = new WindowEvent(gui, WindowEvent.WINDOW_CLOSING);
				Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
				break;

			case DEBUG_TOGGLE_LIGHT_SETS:
				debugShowLightSets = !debugShowLightSets;
				if (!fromGui)
					key.setCheckbox(debugShowLightSets);
				SwingUtilities.invokeLater(() -> {
					gui.setLightSetsVisible(debugShowLightSets);
				});
				break;

			default:
				break;
		}
	}

	public void updatePlayInEditorSimulation(double deltaTime)
	{
		Map collisionMap = getCollisionMap();

		// update 'player'
		boolean allowInput = (changeMapState != ChangeMapState.EXITING) && (changeMapState != ChangeMapState.ENTERING);
		cursor3D.tickSimulation(keyboard, collisionMap, map, perspectiveView, deltaTime, (perspectiveView == activeView), allowInput,
			false);

		Vector3f start = cursor3D.getPosition();
		PickRay traceBelowCursor = new PickRay(Channel.COLLISION, new Vector3f(start.x, start.y + 10, start.z), PickRay.DOWN,
			perspectiveView);

		// update camera
		List<Zone> candidates = new ArrayList<>();
		for (Zone z : collisionMap.zoneTree) {
			if (z.hasCameraData.get())
				candidates.add(z);
		}
		PickHit zoneHit = Map.pickObjectFromSet(traceBelowCursor, candidates, pieIgnoreHiddenZones);
		if (zoneHit.dist < Float.MAX_VALUE)
			zoneCam.controlData = ((Zone) zoneHit.obj).camData;

		// update exit trigger + map transition state
		updateMapTransition(traceBelowCursor);
	}

	private void updateMapTransition(PickRay traceBelowCursor)
	{
		if (!pieEnableMapExits) {
			changeMapState = ChangeMapState.NONE;
			screenFadeAmount = 0.0f;
		}

		switch (changeMapState) {
			case NONE:
				if (tryMapExit(traceBelowCursor))
					changeMapState = ChangeMapState.CHOSE_MAP;
			case LOADING_MAP:
				break;

			case CHOSE_MAP:
				changeMapState = ChangeMapState.LOADING_MAP;
				destMapFile = AssetManager.getMap(destMapName);
				if (!destMapFile.exists()) {
					changeMapState = ChangeMapState.LOADING_FAILED;
					break;
				}

				if (!map.modified) {
					changeMapState = ChangeMapState.READY_TO_LOAD;
					break;
				}

				SwingUtilities.invokeLater(() -> {
					if (gui.promptForSave())
						changeMapState = ChangeMapState.READY_TO_LOAD;
					else
						changeMapState = ChangeMapState.LOADING_FAILED;
				});
				break;

			case READY_TO_LOAD: {
				// load map in worker thread
				destMap = null;
				Thread mapLoadThread = new Thread(() -> {
					try {
						destMap = Map.loadMap(destMapFile);
					}
					catch (Throwable t) {
						Logger.logError("Can't load " + destMapName + ": " + t.getMessage());
						changeMapState = ChangeMapState.LOADING_FAILED;
					}
				});
				mapLoadThread.start();

				mapChangeTimer = 0.0f;
				MapObject obj = map.find(MapObjectType.MARKER, exitMarkerName);
				Marker m = (Marker) obj;

				// marker yaw coord system is rotated 90 degrees from player move yaw coord system.
				// player yaw goes from xhat to zhat, marker goes from -zhat to xhat
				float yaw = (float) Math.toRadians(180.0f + m.yaw.getAngle() - 90.0);
				cursor3D.setMoveHeading(CursorObject.WALK_SPEED, yaw);

				changeMapState = ChangeMapState.EXITING;
			}
				break;

			case LOADING_FAILED:
				screenFadeAmount = 0;
				changeMapState = ChangeMapState.NONE;
				cursor3D.setMoveHeading(0.0f, 0.0f);
				break;

			case EXITING: {
				mapChangeTimer += deltaTime;
				screenFadeAmount = MathUtil.lerp(mapChangeTimer, EXIT_TIME / 2, EXIT_TIME, 0.0f, 1.0f);

				if (mapChangeTimer >= EXIT_TIME) {
					mapChangeTimer = EXIT_TIME;
					cursor3D.setMoveHeading(0.0f, 0.0f);

					if (destMap != null) {
						changeMapState = ChangeMapState.CHECK_MAP;
						SwingUtilities.invokeLater(() -> {
							destMap = checkForBackup(destMap);
							changeMapState = ChangeMapState.OPEN_MAP;
						});
					}
				}
			}
				break;
			case CHECK_MAP:
				// wait to check for backup
				break;

			case OPEN_MAP:
				// eat a frame to ensure the viewport is rendered with complete fade before opening map
				action_OpenMap(destMap);
				cursor3D.startPreviewMode();
				isPlayInEditorMode = true;

				CommandBatch syncHidden = new CommandBatch("Set Visibility");
				syncHidden.silence();
				syncHidden.setModifiesMap(false);
				syncHidden.addCommand(new HideObjectTreeSimple("Models", map.modelTree, !showModels));
				syncHidden.addCommand(new HideObjectTreeSimple("Colliders", map.colliderTree, !showColliders));
				syncHidden.addCommand(new HideObjectTreeSimple("Zones", map.zoneTree, !showZones));
				syncHidden.addCommand(new HideObjectTreeSimple("Markers", map.markerTree, !showMarkers));
				commandManager.executeCommand(syncHidden);

				changeMapState = ChangeMapState.ENTER_INIT;
				break;

			case ENTER_INIT:
				// eat a frame to prevent the long deltaTime of action_OpenMap from messing up the ENTERING state
				MapObject obj = map.find(MapObjectType.MARKER, destMarkerName);
				if (obj != null) {
					Marker m = (Marker) obj;
					// marker yaw coord system is rotated 90 degrees from player move yaw coord system.
					// player yaw goes from xhat to zhat, marker goes from -zhat to xhat
					float yaw = (float) Math.toRadians(m.yaw.getAngle() - 90.0);
					cursor3D.setMoveHeading(CursorObject.WALK_SPEED, yaw);
					Vector3f entryPos = m.position.getVector();
					cursor3D.setPosition(new Vector3f(
						entryPos.x - 60.0f * (float) Math.cos(yaw),
						entryPos.y,
						entryPos.z - 60.0f * (float) Math.sin(yaw)));
				}
				else {
					cursor3D.setPosition(new Vector3f(0.0f, 0.0f, 0.0f));
					cursor3D.setMoveHeading(0.0f, 0.0f);
				}
				changeMapState = ChangeMapState.ENTERING;
				mapChangeTimer = 0.0f;
				// tryMapExit(traceBelowCursor); // prevents triggering the loading zone until you leave it
				break;
			case ENTERING:
				screenFadeAmount = MathUtil.lerp(mapChangeTimer, 0, ENTER_TIME / 2, 1.0f, 0.0f);
				mapChangeTimer += deltaTime;

				if (mapChangeTimer >= ENTER_TIME) {
					screenFadeAmount = 0.0f;
					mapChangeTimer = 0.0f;
					changeMapState = ChangeMapState.NONE;
				}
				break;
		}
	}

	private boolean tryMapExit(PickRay traceBelowCursor)
	{
		boolean queued = false;

		Exit closestValidExit = null;
		float closestHitDist = Float.MAX_VALUE;
		List<Generator> exitList = map.scripts.generatorsTreeModel.getObjectsInCategory(GeneratorType.Exit);
		for (Generator generator : exitList) {
			Exit exit = (Exit) generator;
			String colliderName = exit.colliderName.get();
			if (colliderName == null || colliderName.isEmpty())
				continue; // invalid collider name

			MapObject obj = map.find(MapObjectType.COLLIDER, colliderName);
			if (obj == null)
				continue; // no collider with name

			PickHit hit = obj.tryPick(traceBelowCursor);
			if (hit.dist == Float.MAX_VALUE)
				continue; // did not hit

			String destMapName = exit.destMap.get();
			if (destMapName == null || destMapName.isEmpty())
				continue; // invalid dest map name

			String destMarkerName = exit.destMarkerName.get();
			if (destMarkerName == null || destMarkerName.isEmpty())
				continue; // invalid dest entry name

			String exitMarkerName = exit.markerName.get();
			if (exitMarkerName == null || exitMarkerName.isEmpty())
				continue; // invalid exit marker name

			if (hit.dist < closestHitDist) {
				closestHitDist = hit.dist;
				closestValidExit = exit;
			}
		}

		if (closestValidExit != null) {
			String newDestMapName = closestValidExit.destMap.get();
			if (!newDestMapName.equals(destMapName)) {
				destMapName = newDestMapName;
				queued = true;
			}

			String newDestMarkerName = closestValidExit.destMarkerName.get();
			if (!newDestMarkerName.equals(destMarkerName))
				destMarkerName = newDestMarkerName;

			String newExitMarkerName = closestValidExit.markerName.get();
			if (!newExitMarkerName.equals(exitMarkerName))
				exitMarkerName = newExitMarkerName;
		}
		else {
			destMapName = null;
			destMarkerName = null;
			exitMarkerName = null;
		}

		return queued;
	}

	public boolean isPlayInEditorMode()
	{
		return isPlayInEditorMode;
	}

	public PerspCameraMode getCameraMode()
	{
		return perspCameraMode;
	}

	public CameraController getCameraController()
	{
		switch (perspCameraMode) {
			case PLAY_IN_EDITOR:
				return zoneCam.controller;
			case MARKER_PREVIEW:
				return targetCam.controller;
			default:
				return null;
		}
	}

	public CameraZoneData getCameraControlData()
	{
		switch (perspCameraMode) {
			case PLAY_IN_EDITOR:
				return zoneCam.controlData;
			case MARKER_PREVIEW:
				if (targetCam.targetMarker == null)
					return null;
				else
					return targetCam.targetMarker.getCameraControlData();
			default:
				return null;
		}
	}

	public boolean usingInGameCameraProperties()
	{
		if (useMapCameraProperties)
			return true;

		switch (perspCameraMode) {
			case PLAY_IN_EDITOR:
			case MARKER_PREVIEW:
			case BATTLE:
				return true;
			default:
				return false;
		}
	}

	public boolean usingInGameAspectRatio()
	{
		if (useGameAspectRatio)
			return true;

		switch (perspCameraMode) {
			case PLAY_IN_EDITOR:
			case MARKER_PREVIEW:
			case BATTLE:
				return true;
			default:
				return false;
		}
	}

	public void toggleCameraTargetMarker(Marker m)
	{
		if (targetCam.targetMarker == m) {
			usingTargetCam = false;
			targetCam.targetMarker = null; // toggle off
		}
		else {
			usingTargetCam = true;
			targetCam.targetMarker = m; // switch / toggle on
		}
	}

	public void clearTargetCamera()
	{
		targetCam.targetMarker = null;
		usingTargetCam = false;
	}

	public void cameraLookAt(Vector3f target)
	{
		if (perspCameraMode == PerspCameraMode.FREE)
			freeCam.lookAt(target);
	}

	public Vector3f getPointAhead(float length)
	{
		Vector3f pos = perspectiveView.camera.getPosition();

		float sinP = (float) Math.sin(Math.toRadians(perspectiveView.camera.pitch));
		float cosP = (float) Math.cos(Math.toRadians(perspectiveView.camera.pitch));
		float sinY = (float) Math.sin(Math.toRadians(perspectiveView.camera.yaw));
		float cosY = (float) Math.cos(Math.toRadians(perspectiveView.camera.yaw));

		float dx = length * sinY * cosP;
		float dy = length * -sinP;
		float dz = length * -cosY * cosP;

		return new Vector3f(pos.x + dx, pos.y + dy, pos.z + dz);
	}

	public void setCameraParams(CameraZoneData controlData, Vector3f targetPos)
	{
		Vector3f camPos = perspectiveView.camera.getPosition();

		float dx = camPos.x - targetPos.x;
		float dy = camPos.y - targetPos.y;
		float dz = camPos.z - targetPos.z;
		float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

		float R = (float) Math.sqrt(dx * dx + dz * dz);

		float pitch = 0.0f;
		if (R > 0)
			pitch = (float) Math.toDegrees(Math.atan2(dy, R));

		float view = perspectiveView.camera.pitch - pitch;

		CommandBatch batch = new CommandBatch();
		batch.addCommand(new SetCameraType(controlData, ControlType.TYPE_0));
		batch.addCommand(new SetCameraFlag(controlData, false));
		batch.addCommand(new SetCameraPos(controlData, 'A', 0, camPos.x));
		batch.addCommand(new SetCameraPos(controlData, 'A', 1, targetPos.y));
		batch.addCommand(new SetCameraPos(controlData, 'A', 2, camPos.z));
		batch.addCommand(new SetCameraPos(controlData, 'B', 0, targetPos.x));
		batch.addCommand(new SetCameraPos(controlData, 'B', 1, targetPos.y));
		batch.addCommand(new SetCameraPos(controlData, 'B', 2, targetPos.z));
		batch.addCommand(controlData.boomLength.mutator(dist));
		batch.addCommand(controlData.boomPitch.mutator(pitch));
		batch.addCommand(controlData.viewPitch.mutator(view));
		execute(batch);
	}

	private ShadingProfile findShadingProfile()
	{
		if (map.scripts.hasSpriteShading.get())
			return map.scripts.shadingProfile.get();
		else
			return null;
	}

	public RenderingOptions getRenderingOptions()
	{
		RenderingOptions opts = new RenderingOptions();
		opts.canvasSizeX = prevCanvasSize.width;
		opts.canvasSizeY = prevCanvasSize.height;
		opts.editorMode = getEditorMode();
		opts.selectionMode = selectionManager.getSelectionMode();
		opts.worldFogEnabled = map.scripts.worldFogSettings.enabled.get();
		opts.entityFogEnabled = map.scripts.entityFogSettings.enabled.get();
		opts.spriteShading = findShadingProfile();
		opts.modelSurfaceMode = SurfaceMode.TEXTURED;
		opts.postProcessFX = postProcessFX;
		opts.screenFade = screenFadeAmount;
		opts.time = (float) time;

		opts.useFiltering = useFiltering;
		opts.useTextureLOD = useTextureLOD;
		opts.useGeometryFlags = useGeometryFlags;
		opts.showBoundingBoxes = showBoundingBoxes;
		opts.showNormals = showNormals;
		opts.edgeHighlights = edgeHighlights;
		opts.useColliderColoring = useColliderColoring;
		opts.showEntityCollision = showEntityCollision;
		opts.isStage = map.isStage;
		opts.thumbnailMode = thumbnailMode;

		return opts;
	}

	@Override
	protected void glDraw()
	{
		if (needsTextureReload) {
			for (Model mdl : map.modelTree)
				mdl.getMesh().texture = null;
			if (shapeOverride != null)
				for (Model mdl : shapeOverride.modelTree)
					mdl.getMesh().texture = null;
			TextureManager.clear();

			TextureManager.load(map.texName);
			TextureManager.assignModelTextures(map);
			if (shapeOverride != null)
				TextureManager.assignModelTextures(shapeOverride);
			gui.setSelectedTexture(null);
			gui.loadTexturePreviews();
			needsTextureReload = false;
		}

		if (needsBackgroundReload) {
			glDeleteTextures(map.glBackgroundTexID);
			if (map.bgImage != null)
				map.glBackgroundTexID = TextureManager.bindBufferedImage(map.bgImage);
			else
				map.glBackgroundTexID = TextureManager.glMissingTextureID;
		}

		if (dummyDraw)
			return;

		RenderState.setTime(time);
		Renderer.updateColors(time);
		RenderingOptions opts = getRenderingOptions();
		prepareVertexBuffers(opts);

		// viewports
		switch (viewMode) {
			case ONE:
				activeView.render(opts, true);
				break;
			case UVEDIT:
				uvViews[0].render(opts, uvViews[0] == activeView);
				uvViews[1].render(opts, uvViews[1] == activeView);
				break;
			case FOUR:
				fourViews[0].render(opts, fourViews[0] == activeView);
				fourViews[1].render(opts, fourViews[1] == activeView);
				fourViews[2].render(opts, fourViews[2] == activeView);
				fourViews[3].render(opts, fourViews[3] == activeView);
				break;
			case PREVIEW:
				previewViews[0].render(opts, previewViews[0] == activeView);
				previewViews[1].render(opts, previewViews[1] == activeView);
				previewViews[2].render(opts, previewViews[2] == activeView);
				previewViews[3].render(opts, previewViews[3] == activeView);
				break;
		}

		// render UI
		if (viewMode != ViewMode.ONE) {
			int sizeX = glCanvas.getWidth();
			int sizeY = glCanvas.getHeight();
			RenderState.setViewport(0, 0, sizeX, sizeY);

			TransformMatrix projMtx = TransformMatrix.identity();
			projMtx.ortho(0, sizeX, 0, sizeY, -1, 1);
			RenderState.setProjectionMatrix(projMtx);
			RenderState.setViewMatrix(null);
			RenderState.setModelMatrix(null);

			RenderState.setLineWidth(2.0f);
			RenderState.setColor(0.9f, 0.9f, 0.9f, 1.0f);

			if (enableXDivider) {
				LineRenderQueue.addLine(
					LineRenderQueue.addVertex().setPosition(sizeX * hDivRatio, 0, 1).getIndex(),
					LineRenderQueue.addVertex().setPosition(sizeX * hDivRatio, sizeY, 1).getIndex());
			}
			if (enableYDivider) {
				LineRenderQueue.addLine(
					LineRenderQueue.addVertex().setPosition(0, sizeY * vDivRatio, 1).getIndex(),
					LineRenderQueue.addVertex().setPosition(sizeX, sizeY * vDivRatio, 1).getIndex());
			}

			LineRenderQueue.render(true);
		}
	}

	private void prepareVertexBuffers(RenderingOptions opts)
	{
		Map shapeMap = getGeometryMap();
		Map hitMap = getCollisionMap();

		for (MapObject obj : shapeMap.modelTree)
			obj.prepareVertexBuffers(opts);

		for (MapObject obj : hitMap.colliderTree)
			obj.prepareVertexBuffers(opts);

		for (MapObject obj : hitMap.zoneTree)
			obj.prepareVertexBuffers(opts);
	}

	private void initThumbnail()
	{
		enableXDivider = false;
		enableYDivider = false;

		showBoundingBoxes = false;
		showNormals = false;
		showAxes = false;
		showGizmo = false;
		showEntityCollision = false;
		useColliderColoring = false;
		useGameAspectRatio = false;
		useGeometryFlags = true;
		useFiltering = true;
		useTextureLOD = true;
		useMapBackgroundColor = true;

		thumbnailMode = true;
	}

	private void renderThumbnail(File thumbFile)
	{
		runInContext(() -> {
			glReadBuffer(GL_FRONT);
			int width = perspectiveView.maxX - perspectiveView.minX;
			int height = perspectiveView.maxY - perspectiveView.minY;
			int bpp = 4;
			ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * bpp);
			glReadPixels(perspectiveView.minX, perspectiveView.minY, width, height,
				GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					int i = (x + (width * y)) * bpp;
					int r = buffer.get(i) & 0xFF;
					int g = buffer.get(i + 1) & 0xFF;
					int b = buffer.get(i + 2) & 0xFF;
					image.setRGB(x, height - (y + 1), (0xFF << 24) | (r << 16) | (g << 8) | b);
				}
			}

			image = resizeImage(image, 480);

			try {
				FileUtils.touch(thumbFile);
				ImageIO.write(image, "JPG", thumbFile);
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		});
	}

	private static BufferedImage resizeImage(BufferedImage src, int targetSize)
	{
		if (targetSize <= 0) {
			return src; // this can't be resized
		}
		int targetWidth = targetSize;
		int targetHeight = targetSize;
		float ratio = ((float) src.getHeight() / (float) src.getWidth());
		if (ratio <= 1) { // square or landscape-oriented image
			targetHeight = (int) Math.ceil(targetWidth * ratio);
		}
		else { // portrait image
			targetWidth = Math.round(targetHeight / ratio);
		}
		BufferedImage bi = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = bi.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
		g2d.dispose();
		return bi;
	}

	private void setViewMode(ViewMode mode)
	{
		switch (mode) {
			case ONE:
				enableXDivider = false;
				enableYDivider = false;
				break;
			case UVEDIT:
				enableXDivider = true;
				enableYDivider = false;
				break;
			case FOUR:
			case PREVIEW:
				enableXDivider = true;
				enableYDivider = true;
				break;
		}
		viewMode = mode;
		resizeViews();
	}

	private void resizeViews()
	{
		int width = glCanvas.getWidth();
		int height = glCanvas.getHeight();
		int hDiv = (int) (hDivRatio * width);
		int vDiv = (int) (vDivRatio * height);

		switch (viewMode) {
			case ONE:
				activeView.resize(0, 0, width, height);
				break;
			case UVEDIT:
				uvViews[0].resize(0, 0, hDiv, height);
				uvViews[1].resize(hDiv, 0, width, height);
				break;
			case FOUR:
				fourViews[0].resize(hDiv, vDiv, width, height);
				fourViews[1].resize(0, 0, hDiv, vDiv);
				fourViews[2].resize(hDiv, 0, width, vDiv);
				fourViews[3].resize(0, vDiv, hDiv, height);
				break;
			case PREVIEW:
				previewViews[0].resize(hDiv, vDiv, width, height);
				previewViews[1].resize(0, 0, hDiv, vDiv);
				previewViews[2].resize(hDiv, 0, width, vDiv);
				previewViews[3].resize(0, vDiv, hDiv, height);
				break;
		}
	}

	private void releaseMapResources(boolean reloadTextures)
	{
		if (reloadTextures)
			TextureManager.clear();
		glDeleteTextures(map.glBackgroundTexID);
		selectionManager = new SelectionManager(this);
		gui.setSelectedTexture(null);
	}

	/**
	 * Determines which assets are required by OpenGL and initializes them. Currently this involves: - Loading all required
	 * textures. - Set texture IDs on mesh objects.
	 *
	 * @param map
	 */
	private void loadMapResources(boolean reloadTextures)
	{
		boolean loadedTextures = !reloadTextures;
		if (!loadedTextures)
			loadedTextures = TextureManager.load(map.texName);

		while (!loadedTextures) {
			int choice = SwingUtils.getConfirmDialog()
				.setParent(gui)
				.setTitle("Missing Texture Archive")
				.setMessage("Could not open texture archive \"" + map.texName + "\", select a different one?")
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.choose();

			if (choice == JOptionPane.YES_OPTION) {
				File texFile = SelectTexDialog.showPrompt();
				if (texFile != null) {
					String texName = FilenameUtils.getBaseName(texFile.getName());
					loadedTextures = TextureManager.load(texName);
				}
			}
			else
				break;
		}

		TextureManager.assignModelTextures(map);

		for (Model mdl : map.modelTree)
			for (Triangle t : mdl.getMesh())
				for (Vertex v : t.vert) {
					if ((v.a & 0xFF) == 255)
						PaintManager.pushColor(new java.awt.Color((v.r & 0xFF), (v.g & 0xFF), (v.b & 0xFF), (v.a & 0xFF)));
				}

		try {
			if (map.hasBackground) {
				Logger.log("Map background is " + map.bgName);
				map.bgImage = ImageIO.read(AssetManager.getBackground(map.bgName));
				map.glBackgroundTexID = TextureManager.bindBufferedImage(map.bgImage);
			}
			else
				Logger.log("Map has no background.");
		}
		catch (IOException e) {
			Logger.log("Could not load background image!", Priority.WARNING);
		}
	}

	private void updateUVViewport()
	{
		if (selectionManager.getMostRecentModel() != null)
			uvEditView.setModel(selectionManager.getMostRecentModel());
		else
			uvEditView.setModel(null);

		List<Triangle> triangles = selectionManager.getTrianglesFromSelection(Model.class);
		boolean changed = uvEditView.setTriangles(triangles);
		if (changed)
			selectionManager.generateUVList(triangles);
	}

	private void paintVertices()
	{
		if (paintPickHit.missed())
			return;

		PaintManager.paintVertices(paintPickHit.point, paintingVertexSet);
	}

	private void startPainting()
	{
		PaintManager.pushSelectedColor();

		backupVertexColorMap = new IdentityHashMap<>();
		for (Vertex v : paintingVertexSet) {
			v.painted = false;
			backupVertexColorMap.put(v, new Color4d(v.r, v.g, v.b, v.a));
		}
	}

	private void finishPainting()
	{
		IdentityHashMap<Vertex, Color4d> newVertexColorMap = new IdentityHashMap<>();
		for (Vertex v : paintingVertexSet) {
			if (v.painted)
				newVertexColorMap.put(v, new Color4d(v.r, v.g, v.b, v.a));
		}
		commandManager.executeCommand(new PaintVertices(backupVertexColorMap, newVertexColorMap));
	}

	private void pickVertexColor()
	{
		PickHit hit = Map.pickTriangleFromObjectList(clickRayRMB, map.modelTree);
		if (hit.missed())
			return;

		Triangle t = (Triangle) hit.obj;

		Vertex closest = null;
		float closestDist = Float.MAX_VALUE;
		for (Vertex v : t.vert) {
			float dx = hit.point.x - v.getCurrentX();
			float dy = hit.point.y - v.getCurrentY();
			float dz = hit.point.z - v.getCurrentZ();
			float distSquared = dx * dx + dy * dy + dz * dz;

			if (distSquared < closestDist) {
				closestDist = distSquared;
				closest = v;
			}
		}

		if (closest != null) {
			java.awt.Color c = new java.awt.Color(
				closest.r & 0xFF,
				closest.g & 0xFF,
				closest.b & 0xFF,
				closest.a & 0xFF);
			PaintManager.setSelectedColor(c);
			PaintManager.pushSelectedColor();
		}
	}

	// ==================================================
	// actions for file operations
	// ==================================================

	/**
	 * Saves the map quietly, will prompt for a filename if this is a newly created map.
	 *
	 * @throws IOException
	 */
	public void action_SaveMap()
	{
		try {
			map.saveMap(new MapEditorMetadata(this));
			if (!map.isStage)
				new MapSourceRenamer(map);
		}
		catch (Exception e) {
			displayStackTrace(e);
			return;
		}

		map.modified = false;
		lastBackupTime = time;
	}

	public void action_SaveMapAs(File f)
	{
		try {
			map.saveMapAs(f, new MapEditorMetadata(this));
			if (!map.isStage)
				new MapSourceRenamer(map);
		}
		catch (Exception e) {
			displayStackTrace(e);
			return;
		}

		map.modified = false;
		lastBackupTime = time;

		updateWindowTitle();
		updateRecentMaps();
		gui.setRecentMaps(recentMaps);
	}

	public void action_OpenMap(File f)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		Map newMap = Map.loadMap(f);

		if (newMap == null) {
			Logger.log("Could not open " + f.getName(), Priority.WARNING);
			return;
		}

		action_OpenMap(newMap);
	}

	public void action_OpenMap(Map newMap)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		openMap(newMap, false);
		lastBackupTime = time;
	}

	public void loadOverrides()
	{
		assert (!SwingUtilities.isEventDispatchThread());
		checkShapeOverride();
		checkHitOverride();
	}

	private void checkShapeOverride()
	{
		shapeOverride = null;

		if (map.scripts.overrideShape.get()) {
			String overrideName = map.scripts.shapeOverrideName.get();
			if (overrideName == null || overrideName.isBlank()) {
				Logger.logError("Override name is missing or empty.");
				return;
			}
			AssetHandle ah = AssetManager.getMap(overrideName);
			if (!ah.exists()) {
				Logger.logError("Couldn't find map: " + overrideName + Directories.EXT_MAP);
				return;
			}
			shapeOverride = Map.loadMap(ah);
			TextureManager.assignModelTextures(shapeOverride);
			Logger.log("Loaded override geometry: " + shapeOverride.getName());
		}
	}

	private void checkHitOverride()
	{
		hitOverride = null;

		if (map.scripts.overrideHit.get()) {
			String overrideName = map.scripts.hitOverrideName.get();
			if (overrideName == null || overrideName.isBlank()) {
				Logger.logError("Override name is missing or empty.");
				return;
			}
			AssetHandle ah = AssetManager.getMap(overrideName);
			if (!ah.exists()) {
				Logger.logError("Couldn't find map: " + overrideName + Directories.EXT_MAP);
				return;
			}
			hitOverride = Map.loadMap(ah);
			Logger.log("Loaded override collision: " + hitOverride.getName());
		}
	}

	public Map getGeometryMap()
	{
		return shapeOverride == null ? map : shapeOverride;
	}

	public Map getCollisionMap()
	{
		return hitOverride == null ? map : hitOverride;
	}

	private void openMap(Map newMap, boolean thumbnailMode)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (newMap == null)
			return;

		// this MUST run with GLContext available
		runInContext(() -> {
			String prevTexName = (thumbnailMode || map == null) ? null : map.texName;
			boolean reloadTextures = !newMap.texName.equals(prevTexName);

			if (map != null)
				releaseMapResources(reloadTextures);

			loading = true;
			map = newMap;

			loadMapResources(reloadTextures);

			loadOverrides();

			updateRecentMaps();
			gui.setRecentMaps(recentMaps);

			resetEditorSettings();
			map.initializeAllObjects();
			map.loadVarNames();

			ShadingProfile profile = map.scripts.shadingProfile.get();
			if (map.scripts.hasSpriteShading.get() && profile != null) {
				for (ShadingLightSource source : profile.sources)
					addEditorObject(source);
			}

			if (!thumbnailMode) {
				final Map guiMap = newMap;
				SwingUtilities.invokeLater(() -> {
					updateWindowTitle();
					gui.setMap(guiMap);
				});

				if (map.editorData != null) {
					for (int i = 0; i < 4; i++) {
						MapEditCamera cam = fourViews[i].camera;
						if (cam.pos.x != 0x10000)
							cam.pos.x = Float.isNaN(map.editorData.cameraPos[i][0]) ? 0 : map.editorData.cameraPos[i][0];
						if (cam.pos.y != 0x10000)
							cam.pos.y = Float.isNaN(map.editorData.cameraPos[i][1]) ? 0 : map.editorData.cameraPos[i][1];
						if (cam.pos.z != 0x10000)
							cam.pos.z = Float.isNaN(map.editorData.cameraPos[i][2]) ? 0 : map.editorData.cameraPos[i][2];
					}

					perspectiveView.camera.pitch = Float.isNaN(map.editorData.perspPitch) ? 0 : map.editorData.perspPitch;
					perspectiveView.camera.yaw = Float.isNaN(map.editorData.perspYaw) ? 0 : map.editorData.perspYaw;
				}
			}
		});

		loading = false;
	}

	private void updateWindowTitle()
	{
		gui.setTitle(Environment.decorateTitle("Map Editor") + " - " + map.getName());
	}

	public void action_SaveShading()
	{
		try {
			SpriteShadingEditor.saveShadingProfiles(ProjectDatabase.SpriteShading);
			ProjectDatabase.SpriteShading.modified = false;
		}
		catch (Exception e) {
			displayStackTrace(e);
		}
	}

	// ==================================================
	// actions that modify the selection
	// ==================================================

	private void selectAll()
	{
		if (editorMode == EditorMode.Modify)
			selectionManager.selectAll(map);
	}

	private void duplicateSelection()
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (editorMode != EditorMode.Modify || selectionManager.getSelectionMode() != SelectionMode.OBJECT)
			return;

		List<MapObject> selectedList = map.getCompleteSelection();

		if (selectedList.size() > 0) {
			List<MapObject> duplicateList = new LinkedList<>();
			for (MapObject obj : selectedList)
				duplicateList.add(MapObject.deepCopyWithChildren(obj));

			CommandBatch batch = new CommandBatch("Duplicate Objects");
			batch.addCommand(new CreateObjects(duplicateList));
			batch.addCommand(selectionManager.getModifyObjects(null, selectedList));
			commandManager.executeCommand(batch);
		}
	}

	private void deleteSelection()
	{
		if (editorMode == EditorMode.Modify || editorMode == EditorMode.EditUVs)
			selectionManager.deleteSelection();

		if (editorMode == EditorMode.EditUVs)
			updateUVViewport();
	}

	private void flipSelection(Axis axis)
	{
		TransformMatrix rescale = new TransformMatrix();
		double sx = (axis == Axis.X) ? -1.0 : 1.0;
		double sy = (axis == Axis.Y) ? -1.0 : 1.0;
		double sz = (axis == Axis.Z) ? -1.0 : 1.0;
		Vector3f center;

		switch (editorMode) {
			case Modify:
				if (selectionManager.currentSelection.isEmpty())
					return;

				center = selectionManager.currentSelection.getCenter();
				rescale.setScaleAbout(sx, sy, sz, center.x, center.y, center.z);
				selectionManager.currentSelection.applyMatrixTransformation(rescale);
				break;

			case EditUVs:
				if (selectionManager.uvSelection.isEmpty() || axis == Axis.Z)
					return;

				center = selectionManager.uvSelection.getCenter();
				rescale.setScaleAbout(sx, sy, sz, center.x, center.y, center.z);
				selectionManager.uvSelection.applyMatrixTransformation(rescale);
				break;

			default:
		}
	}

	private void roundVertices()
	{
		switch (editorMode) {
			case Modify:
				if (selectionManager.currentSelection.isEmpty())
					return;

				selectionManager.currentSelection.startDirectTransformation();
				for (Vertex v : selectionManager.getVerticesFromSelection())
					v.round(objectGrid.getSpacing());
				selectionManager.currentSelection.endDirectTransformation();
				break;

			case EditUVs:
				if (selectionManager.uvSelection.isEmpty())
					return;

				selectionManager.uvSelection.startDirectTransformation();
				for (UV uv : selectionManager.uvSelection.selectableList)
					uv.round(uvGrid.getSpacing());
				selectionManager.uvSelection.endDirectTransformation();
				break;

			default:
		}
	}

	private void nudge(Vector3f nudgeDirection)
	{
		switch (editorMode) {
			case Modify:
				if (selectionManager.currentSelection.isEmpty())
					return;
				selectionManager.currentSelection.nudgeAlong(nudgeDirection);
				break;

			case EditUVs:
				if (selectionManager.uvSelection.isEmpty())
					return;
				selectionManager.uvSelection.nudgeAlong(nudgeDirection);
			default:
		}
	}

	public Vector3f getCursorPosition()
	{
		return cursor3D != null ? cursor3D.getPosition() : new Vector3f();
	}

	private void findObject()
	{
		assert (!SwingUtilities.isEventDispatchThread());

		SwingUtilities.invokeLater(() -> {
			FindDialog dlg = new FindDialog(map);
			dlg.setLocationRelativeTo(gui);
			dlg.setVisible(true);

			MapObject result = dlg.getResult();
			if (result != null)
				executeNextFrame(selectionManager.getAddObject(result));
		});
	}

	private void copyObjects()
	{
		if (editorMode != EditorMode.Modify || selectionManager.getSelectionMode() != SelectionMode.OBJECT)
			return;

		try {
			int numCopied = map.exportPrefab(clipboardFile, true);
			if (numCopied > 0)
				Logger.log("Copied " + numCopied + " objects to clipboard.");
		}
		catch (IOException e) {
			displayStackTrace(e);
		}
	}

	private void pasteObjects()
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (editorMode != EditorMode.Modify || selectionManager.getSelectionMode() != SelectionMode.OBJECT || !clipboardFile.exists())
			return;

		PrefabImportData imported = map.importPrefab(clipboardFile);
		if (imported.getNumObjects() > 0) {
			commandManager.executeCommand(imported.getCommand("Paste Objects"));
			Logger.log("Pasted " + imported.getNumObjects() + " objects from clipboard.");
		}
	}

	private void pasteObjects(MapObjectNode<? extends MapObject> node)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (editorMode != EditorMode.Modify || selectionManager.getSelectionMode() != SelectionMode.OBJECT || !clipboardFile.exists())
			return;

		PrefabImportData imported = map.importPrefab(clipboardFile, node);
		if (imported.getNumObjects() > 0) {
			commandManager.executeCommand(imported.getCommand("Paste Objects"));
			Logger.log("Pasted " + imported.getNumObjects() + " objects from clipboard.");
		}
	}

	private void changeSelectionMode(SelectionMode mode)
	{
		// leaving mode
		switch (selectionManager.getSelectionMode()) {
			default:
		}

		// starting mode
		switch (mode) {
			default:
		}

		selectionManager.setSelectionMode(mode);
	}

	public void setSelectedTexture(ModelTexture selectedTexture)
	{
		selectionManager.selectedTexture = selectedTexture;
		gui.setSelectedTexture(selectedTexture);
	}

	private void normalsToCamera()
	{
		if (editorMode == EditorMode.Modify) {
			List<Triangle> triangles = selectionManager.getTrianglesFromSelection();
			List<Triangle> flipList = new LinkedList<>();

			Vector3f camPos = perspectiveView.camera.getPosition();
			for (Triangle t : triangles) {
				Vector3f rel = Vector3f.sub(t.getCenter(), camPos);
				if (Vector3f.dot(rel, t.getNormalSafe()) > 0.0f)
					flipList.add(t);
			}

			if (flipList.size() > 0)
				commandManager.executeCommand(new InvertNormals(flipList));
		}
	}

	// ==================================================
	// actions for creating objects
	// ==================================================

	public void action_CreateModel(TriangleBatch batch, String name, MapObjectNode<Model> parent)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		Model mdl = Model.create(batch, name);
		mdl.getNode().parentNode = parent;
		mdl.getNode().childIndex = parent.getChildCount();
		mdl.getMesh().setTexture(selectionManager.selectedTexture);
		createObject(mdl);
	}

	public void action_CreateCollider(TriangleBatch batch, String name, MapObjectNode<Collider> parent)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		Collider c = Collider.create(batch, name);
		c.getNode().parentNode = parent;
		c.getNode().childIndex = parent.getChildCount();
		createObject(c);
	}

	public void action_CreateZone(TriangleBatch batch, String name, MapObjectNode<Zone> parent)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		Zone z = Zone.create(batch, name);
		z.getNode().parentNode = parent;
		z.getNode().childIndex = parent.getChildCount();
		createObject(z);
	}

	public void action_CreateMarker(String name, MarkerType type, MapObjectNode<Marker> parent)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		Vector3f pos = cursor3D.getPosition();

		Marker m = new Marker(name, type, pos.x, pos.y, pos.z, 0);
		m.getNode().parentNode = parent;
		m.getNode().childIndex = parent.getChildCount();
		createObject(m);
	}

	private void createObject(MapObject obj)
	{
		CommandBatch batch = new CommandBatch("Create Object");
		batch.addCommand(new CreateObject(obj));

		List<MapObject> selectedList = map.getCompleteSelection();
		if (selectedList.size() > 0)
			batch.addCommand(selectionManager.getModifyObjects(null, selectedList));

		commandManager.executeCommand(batch);
	}

	@SuppressWarnings("unchecked")
	public void action_CreateGroup(MapObjectNode<? extends MapObject> parent)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		MapObjectType type = parent.getUserObject().getObjectType();

		switch (type) {
			case MODEL:
				action_CreateModelGroup((MapObjectNode<Model>) parent);
				break;
			case COLLIDER:
				action_CreateColliderGroup((MapObjectNode<Collider>) parent);
				break;
			case ZONE:
				action_CreateZoneGroup((MapObjectNode<Zone>) parent);
				break;
			case MARKER:
				action_CreateMarkerGroup((MapObjectNode<Marker>) parent);
				break;
			default:
				throw new IllegalStateException("Invalid MapObjectType for createNewGroup: " + type);
		}
	}

	private void action_CreateModelGroup(MapObjectNode<Model> parent)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		Model group = new Model(ShapeType.GROUP);

		group.getNode().parentNode = parent;
		group.getNode().childIndex = parent.getChildCount();
		group.setName("New Group");
		group.lights.set(map.lightSets.get(0));

		group.setProperties(Model.basicGroupProperties);

		group.updateTransformHierarchy();

		commandManager.executeCommand(new CreateObject(group));

		// have this to run next frame as a separate command after the CreateObject resolves
		doNextFrame(() -> {
			action_MoveSelectedModels(group.getNode(), -1);
		});
	}

	private void action_CreateColliderGroup(MapObjectNode<Collider> parent)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		Collider group = new Collider(HitType.GROUP);

		group.getNode().parentNode = parent;
		group.setName("New Group");

		commandManager.executeCommand(new CreateObject(group));

		// have this to run next frame as a separate command after the CreateObject resolves
		doNextFrame(() -> {
			action_MoveSelectedColliders(group.getNode(), -1);
		});
	}

	private void action_CreateZoneGroup(MapObjectNode<Zone> parent)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		Zone group = new Zone(HitType.GROUP);

		group.getNode().parentNode = parent;
		group.setName("New Group");

		commandManager.executeCommand(new CreateObject(group));

		// have this to run next frame as a separate command after the CreateObject resolves
		doNextFrame(() -> {
			action_MoveSelectedZones(group.getNode(), -1);
		});
	}

	private void action_CreateMarkerGroup(MapObjectNode<Marker> parent)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		Marker group = Marker.createGroup("New Group");

		group.getNode().parentNode = parent;
		group.setName("New Group");

		commandManager.executeCommand(new CreateObject(group));

		// have this to run next frame as a separate command after the CreateObject resolves
		doNextFrame(() -> {
			action_MoveSelectedMarkers(group.getNode(), -1);
		});
	}

	public void action_PasteObjectsTo(MapObjectNode<? extends MapObject> parent)
	{
		if (!parent.getAllowsChildren())
			return;

		pasteObjects(parent);
	}

	// ==================================================
	// actions that modify meshes
	// ==================================================

	// Undoable because UVGenerator uses startDirectTransformation() and endDirectTransformation()
	// methods of the UV selection, which issues a DirectTransformSelection command to the Editor.
	public void action_GenerateUVs(UVGenerator g)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		List<Triangle> triangles = selectionManager.getTrianglesFromSelection(Model.class);

		if (triangles.size() > 0)
			g.generateUVs(this, triangles);
	}

	public <T extends Selectable> void action_TransformSelection(TransformMatrix m, Selection<T> currentSelection)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		currentSelection.applyMatrixTransformation(m);
	}

	// ==================================================
	// actions that move objects in their scene tree
	// ==================================================

	public void action_MoveSelectedModels(MapObjectNode<Model> newParent, int childIndex)
	{
		List<Model> mdlList = selectionManager.getSelectedObjects(Model.class);

		List<MapObjectNode<Model>> nodeList = new ArrayList<>(mdlList.size());
		for (Model mdl : mdlList)
			nodeList.add(mdl.getNode());

		if (mdlList.size() > 0)
			commandManager.executeCommand(map.modelTree.getTree().new MoveNodes(
				map.modelTree, nodeList, newParent, childIndex));
	}

	public void action_MoveSelectedColliders(MapObjectNode<Collider> newParent, int childIndex)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		List<Collider> colliderList = selectionManager.getSelectedObjects(Collider.class);

		List<MapObjectNode<Collider>> nodeList = new ArrayList<>(colliderList.size());
		for (Collider c : colliderList)
			nodeList.add(c.getNode());

		if (colliderList.size() > 0)
			commandManager.executeCommand(map.colliderTree.getTree().new MoveNodes(
				map.colliderTree, nodeList, newParent, childIndex));
	}

	public void action_MoveSelectedZones(MapObjectNode<Zone> newParent, int childIndex)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		List<Zone> zoneList = selectionManager.getSelectedObjects(Zone.class);

		List<MapObjectNode<Zone>> nodeList = new ArrayList<>(zoneList.size());
		for (Zone z : zoneList)
			nodeList.add(z.getNode());

		if (zoneList.size() > 0)
			commandManager.executeCommand(map.zoneTree.getTree().new MoveNodes(
				map.zoneTree, nodeList, newParent, childIndex));
	}

	public void action_MoveSelectedMarkers(MapObjectNode<Marker> newParent, int childIndex)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		List<Marker> zoneList = selectionManager.getSelectedObjects(Marker.class);

		List<MapObjectNode<Marker>> markerList = new ArrayList<>(zoneList.size());
		for (Marker m : zoneList)
			markerList.add(m.getNode());

		if (zoneList.size() > 0)
			commandManager.executeCommand(map.markerTree.getTree().new MoveNodes(
				map.markerTree, markerList, newParent, childIndex));
	}

	public class ChangeMode extends AbstractCommand
	{
		private final ChangeMode lastChange;
		private final EditorMode mode;
		private final CommandBatch additionalCommands = new CommandBatch();

		public ChangeMode(EditorMode mode)
		{
			super("Switch to " + mode + " Mode");

			if (lastModeChange == null && editorMode != null) {
				Logger.logWarning("Illegal editor state detected, save your progress and exit.");
				lastModeChange = new ChangeMode(EditorMode.Modify);
			}

			lastChange = lastModeChange;
			this.mode = mode;

			switch (mode) {
				case Scripts:
					additionalCommands.addCommand(selectionManager.new SetSelectionMode(SelectionMode.OBJECT));
					break;
				case EditUVs:
					additionalCommands.addCommand(selectionManager.getClearUVs());
					break;
				default:
			}
		}

		@Override
		public boolean modifiesMap()
		{
			return false;
		}

		@Override
		public boolean shouldExec()
		{
			switch (mode) {
				case EditUVs:
					return (selectionManager.getMostRecentModel() != null) && (editorMode == EditorMode.Modify);
				default:
					return true;
			}
		}

		@Override
		public void exec()
		{
			super.exec();

			additionalCommands.exec();

			lastChange.end();
			start();

			gui.setEditorMode(lastChange.mode, mode);
			lastModeChange = this;
		}

		@Override
		public void undo()
		{
			super.undo();

			end();
			lastChange.start();

			gui.setEditorMode(mode, lastChange.mode);
			lastModeChange = lastChange;

			additionalCommands.undo();
		}

		protected void start()
		{
			editorMode = mode;

			switch (mode) {
				case Modify:
					break;

				case Texture:
					break;

				case Scripts:

					break;

				case EditUVs: {
					grid = uvGrid;
					gui.updateGridSize();
					setViewMode(ViewMode.UVEDIT);
					Model mostRecent = selectionManager.getMostRecentModel();
					if (mostRecent != null && mostRecent.hasMesh())
						uvEditView.setModel(mostRecent);
					else
						uvEditView.setModel(null);
					List<Triangle> triangles = selectionManager.getTrianglesFromSelection(Model.class);
					uvEditView.setTriangles(triangles);
					selectionManager.generateUVList(triangles);
				}
					break;

				case VertexPaint:
					paintingVertexSet = new IdentityHashSet<>();
					switch (selectionManager.getSelectionMode()) {
						case OBJECT:
							if (selectionManager.currentSelection.isEmpty()) {
								for (Model mdl : map.modelTree)
									for (Triangle t : mdl.getMesh())
										for (Vertex v : t.vert)
											paintingVertexSet.add(v);
							}
							else {
								for (Triangle t : selectionManager.getTrianglesFromSelection(Model.class))
									for (Vertex v : t.vert)
										paintingVertexSet.add(v);
							}
							break;

						case TRIANGLE:
							for (Triangle t : selectionManager.getTrianglesFromSelection(Model.class))
								for (Vertex v : t.vert)
									paintingVertexSet.add(v);
							break;

						case VERTEX:
							for (Vertex v : selectionManager.getVerticesFromSelection(Model.class))
								paintingVertexSet.add(v);
							break;

						case POINT:
							break;
					}
					break;
			}
		}

		protected void end()
		{
			switch (mode) {
				case Modify:
					break;

				case Texture:
					break;

				case Scripts:
					break;

				case EditUVs:
					grid = objectGrid;
					gui.updateGridSize();
					setViewMode(mainViewMode);
					activeView = perspectiveView;
					resizeViews();
					break;

				case VertexPaint:
					paintPickHit = null;
					break;
			}
		}
	}

	// ==================================================
	// preferences
	// ==================================================

	private float uvScale = 16.0f;
	private float normalsLength = 16.0f;
	private float rotationSnapIncrement = 15.0f;
	private boolean debugMode = false;
	private boolean showModeInViewport = true;

	public void loadPreferences()
	{
		if (editorConfig == null)
			return;

		backupInterval = editorConfig.getInteger(Options.BackupInterval);

		uvScale = editorConfig.getFloat(Options.uvScale);
		normalsLength = editorConfig.getFloat(Options.NormalsLength);

		int undoLimit = editorConfig.getInteger(Options.UndoLimit);
		commandManager.setUndoLimit(undoLimit);

		rotationSnapIncrement = editorConfig.getFloat(Options.AngleSnap);

		debugMode = editorConfig.getBoolean(Options.EditorDebugMode);

		showModeInViewport = editorConfig.getBoolean(Options.ShowCurrentMode);

		gui.setScrollSensitivity(editorConfig.getInteger(Options.ScrollSensitivity));
	}

	public float getDefaultUVScale()
	{
		return uvScale;
	}

	public float getNormalsLength()
	{
		return normalsLength;
	}

	public double getRotationSnap()
	{
		return rotationSnapIncrement;
	}

	public boolean debugModeEnabled()
	{
		return debugMode;
	}

	public void displayStackTrace(Throwable t)
	{
		displayStackTrace(t, t.getMessage());
	}

	public void displayStackTrace(Throwable t, String errorMessage)
	{
		SwingUtilities.invokeLater(() -> {
			Toolkit.getDefaultToolkit().beep();
			Logger.logError(errorMessage);

			gui.getDialogCounter().increment();
			StarRodMain.displayStackTrace(t);
			gui.getDialogCounter().decrement();
		});
	}

	public static class MapEditorMetadata implements XmlSerializable
	{
		public float[][] cameraPos;
		public float perspPitch;
		public float perspYaw;

		public MapEditorMetadata(MapEditor editor)
		{
			cameraPos = new float[4][3];

			if (editor != null) {
				for (int i = 0; i < 4; i++) {
					MapEditCamera cam = editor.fourViews[i].camera;
					cameraPos[i][0] = cam.pos.x;
					cameraPos[i][1] = cam.pos.y;
					cameraPos[i][2] = cam.pos.z;
				}

				perspPitch = editor.perspectiveView.camera.pitch;
				perspYaw = editor.perspectiveView.camera.yaw;
			}
		}

		@Override
		public void toXML(XmlWriter xmw)
		{
			XmlTag camTag = xmw.createTag(TAG_EDITOR_CAMERA, true);
			xmw.addFloatArray(camTag, ATTR_POS, cameraPos[0]);
			xmw.addFloat(camTag, ATTR_EDITOR_CAM_PITCH, perspPitch);
			xmw.addFloat(camTag, ATTR_EDITOR_CAM_YAW, perspYaw);
			xmw.printTag(camTag);

			camTag = xmw.createTag(TAG_EDITOR_CAMERA, true);
			xmw.addFloatArray(camTag, ATTR_POS, cameraPos[1]);
			xmw.printTag(camTag);

			camTag = xmw.createTag(TAG_EDITOR_CAMERA, true);
			xmw.addFloatArray(camTag, ATTR_POS, cameraPos[2]);
			xmw.printTag(camTag);

			camTag = xmw.createTag(TAG_EDITOR_CAMERA, true);
			xmw.addFloatArray(camTag, ATTR_POS, cameraPos[3]);
			xmw.printTag(camTag);
		}

		@Override
		public void fromXML(XmlReader xmr, Element editorElem)
		{
			List<Element> cameraElems = xmr.getTags(editorElem, TAG_EDITOR_CAMERA);

			if (cameraElems.size() > 0) {
				Element elem0 = cameraElems.get(0);
				perspPitch = xmr.readFloat(elem0, ATTR_EDITOR_CAM_PITCH);
				perspYaw = xmr.readFloat(elem0, ATTR_EDITOR_CAM_YAW);
			}

			if (cameraElems.size() >= 4) {
				for (int i = 0; i < 4; i++) {
					Element elem = cameraElems.get(i);
					cameraPos[i] = xmr.readFloatArray(elem, ATTR_POS, 3);
				}
			}
		}
	}
}
