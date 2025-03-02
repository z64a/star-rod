package game.sprite.editor;

import static app.Directories.FN_SPRITE_EDITOR_CONFIG;
import static org.lwjgl.opengl.GL11.*;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import app.Environment;
import app.StarRodException;
import app.SwingUtils;
import app.config.Config;
import app.config.Options;
import app.config.Options.Scope;
import common.BaseEditor;
import common.BaseEditorSettings;
import common.KeyboardInput.KeyInputEvent;
import common.commands.AbstractCommand;
import common.commands.CommandBatch;
import game.map.editor.render.PresetColor;
import game.map.editor.render.TextureManager;
import game.map.shape.TransformMatrix;
import game.sprite.ImgAsset;
import game.sprite.PalAsset;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.SpriteMetadata;
import game.sprite.SpriteLoader.SpriteSet;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.SpriteCamera.BasicTraceRay;
import game.sprite.editor.animators.CommandAnimatorEditor;
import game.sprite.editor.animators.KeyframeAnimatorEditor;
import game.sprite.editor.commands.CreateAnimation;
import game.sprite.editor.commands.CreateComponent;
import game.sprite.editor.commands.DeleteComponent;
import game.sprite.editor.commands.SelectAnimation;
import game.sprite.editor.commands.SelectComponent;
import game.sprite.editor.commands.SelectModesTab;
import game.sprite.editor.commands.SelectSpritesTab;
import game.sprite.editor.commands.SetOverridePalette;
import game.sprite.editor.commands.SpriteCommandManager;
import game.sprite.editor.commands.ToggleDrawCurrent;
import game.sprite.editor.commands.TogglePaletteOverride;
import game.texture.Palette;
import game.texture.Tile;
import net.miginfocom.swing.MigLayout;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.SpriteShader;
import util.Logger;
import util.identity.IdentityHashSet;
import util.ui.DragReorderList;
import util.ui.EvenSpinner;
import util.ui.IntTextField;
import util.ui.ListAdapterComboboxModel;
import util.ui.ThemedIcon;
import util.xml.XmlWrapper.XmlWriter;

public class SpriteEditor extends BaseEditor
{
	private static final String MENU_BAR_SPACING = "    ";

	private static final int DEFAULT_SIZE_X = 1280;
	private static final int DEFAULT_SIZE_Y = 800;
	private static final int RIGHT_PANEL_WIDTH = 640;
	private static final int LEFT_PANEL_WIDTH = 300;

	private static final int UNDO_LIMIT = 64;

	private static final BaseEditorSettings EDITOR_SETTINGS = BaseEditorSettings.create()
		.setTitle(Environment.decorateTitle("Sprite Editor"))
		.setConfig(Scope.SpriteEditor, FN_SPRITE_EDITOR_CONFIG)
		.setLog("sprite_editor.log")
		.setFullscreen(true)
		.setResizeable(true)
		.hasMenuBar(true)
		.setSize(DEFAULT_SIZE_X, DEFAULT_SIZE_Y)
		.setFramerate(30);

	private SpriteLoader spriteLoader;

	private IdentityHashSet<Sprite> modifiedSprites = new IdentityHashSet<>();
	private IdentityHashSet<DragReorderList<?>> dragLists = new IdentityHashSet<>();

	private SpriteList playerSpriteList;
	private SpriteList npcSpriteList;

	private JComboBox<SpritePalette> paletteComboBox;

	private AnimationsList animList;
	private ComponentsList compList;

	private JPanel componentPanel;

	private JCheckBox cbOverridePalette;
	private JCheckBox cbShowOnlySelectedComponent;

	private JSpinner compxSpinner, compySpinner, compzSpinner;

	// this is implemented but unused
	// doesn't work for mounted WSL file systems while running in Windows
	private SpriteAssetWatcher assetWatcher;

	private JTabbedPane editorModeTabs;
	private Container commandListPanel;
	private Container commandEditPanel;

	// DONT INITIALIZE THESE HERE!
	// gui is initialized before instance variable initialization!
	// since the gui is created in the superclass constructor,
	// setting values here will desync them from the checkboxes!
	private boolean showGuide;
	private boolean showAxes;
	private boolean showBackground;
	private boolean flipHorizontal;
	private boolean useBackFacing;
	private boolean useFiltering;
	public boolean highlightCommand;
	private boolean highlightComponent;

	// animation player controls
	private enum PlayerEvent
	{
		Reset, End, Play, Pause, Stop, PrevFrame, NextFrame, Goto
	}

	private volatile Queue<PlayerEvent> playerEventQueue = new LinkedList<>();
	private volatile boolean paused = false;
	private IntTextField playbackTime;
	private JButton resetButton;
	private JButton endButton;
	private JButton prevFrameButton;
	private JButton nextFrameButton;
	private JButton playButton;
	private JButton stopButton;

	private int playbackRate = 1;
	private int playbackCounter = playbackRate;

	private final SpriteCamera animCamera;
	private final SpriteCamera sheetCamera;
	private BasicTraceRay trace;

	// sprite sheet tab

	private RastersTab rastersTab;
	private PalettesTab palettesTab;

	private static enum EditorMode
	{
		// @formatter:off
		Rasters 	(0, "Rasters"),
		Palettes	(1, "Palettes"),
		Animation	(2, "Animations");
		// @formatter:on

		public final int tabIndex;
		public final String tabName;

		private EditorMode(int tabIndex, String tabName)
		{
			this.tabIndex = tabIndex;
			this.tabName = tabName;
		}
	}

	private int modeTabIndex = 0; // slightly awkward duplication of state with editorMode
	private EditorMode editorMode = EditorMode.Rasters;

	private int spriteTabIndex = 0; // slightly awkward duplication of state with spriteSet
	private SpriteSet spriteSet = SpriteSet.Npc;

	private Tile referenceTile;
	private Palette referencePal;
	private int referencePos;

	// current state
	private Sprite sprite;
	private SpriteAnimation currentAnim;
	private SpriteComponent currentComp;

	private ImgAsset highlightedImgAsset;

	private SpriteComponent dragComp;
	private float dragStartX;
	private float dragStartY;

	private Sprite unloadSprite = null;
	private Sprite loadSprite = null;

	private SpriteMetadata curMetadata = null;

	public volatile boolean suppressCommands = false;

	// handles comnmand execution and undo/redo
	private SpriteCommandManager commandManager;

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		BaseEditor editor = new SpriteEditor();
		editor.launch();
		Environment.exit();
	}

	public SpriteEditor()
	{
		super(EDITOR_SETTINGS);

		if (instance != null)
			throw new IllegalStateException("Only one SpriteEditor open at a time please!");

		instance = this;

		animCamera = new SpriteCamera(
			0.0f, 64.0f, 0.7f,
			0.5f, 0.125f, 2.0f,
			true, false);

		sheetCamera = new SpriteCamera(
			0.0f, 0.0f, 0.7f,
			1.0f, 0.125f, 2.0f,
			true, true);

		assetWatcher = new SpriteAssetWatcher();

		setup();

		SwingUtilities.invokeLater(() -> {
			loadPlayerSpriteList();
			loadNpcSpriteList();
		});

		Logger.log("Loaded sprite editor.");
	}

	// singleton
	private static SpriteEditor instance = null;

	public static SpriteEditor instance()
	{
		return instance;
	}

	public static boolean exists()
	{
		return instance != null;
	}

	public static void execute(AbstractCommand cmd)
	{
		instance().commandManager.executeCommand(cmd);
	}

	public void flushUndoRedo()
	{
		commandManager.flush();
	}

	@Override
	protected void undoEDT()
	{
		assert (SwingUtilities.isEventDispatchThread());

		commandManager.action_Undo();
	}

	@Override
	protected void redoEDT()
	{
		assert (SwingUtilities.isEventDispatchThread());

		commandManager.action_Redo();
	}

	@Override
	public void beforeCreateGui()
	{
		Config cfg = getConfig();
		if (cfg != null) {
			useFiltering = cfg.getBoolean(Options.SprUseFiltering);
			showAxes = cfg.getBoolean(Options.SprEnableAxes);
			showBackground = cfg.getBoolean(Options.SprEnableBackground);
			highlightComponent = cfg.getBoolean(Options.SprHighlightSelected);
			highlightCommand = cfg.getBoolean(Options.SprHighlightCommand);
			showGuide = cfg.getBoolean(Options.SprShowScaleReference);
			flipHorizontal = cfg.getBoolean(Options.SprFlipHorizontal);
			useBackFacing = cfg.getBoolean(Options.SprBackFacing);
		}

		playerSpriteList = new SpriteList(this);
		npcSpriteList = new SpriteList(this);

		spriteLoader = new SpriteLoader();
		spriteLoader.tryLoadingPlayerAssets(false);

		commandManager = new SpriteCommandManager(this, UNDO_LIMIT);
	}

	@Override
	public void glInit()
	{
		TextureManager.bindEditorTextures();

		Sprite referenceSprite = spriteLoader.getSprite(SpriteSet.Player, 1);
		if (referenceSprite != null) {
			SpriteRaster referenceRaster = referenceSprite.rasters.get(0);
			referenceTile = new Tile(referenceRaster.front.asset.img);
			referenceTile.glLoad(GL_REPEAT, GL_REPEAT, false);

			Color c = new Color(0, 60, 80, 255);
			Color[] pal = new Color[16];
			for (int i = 1; i < pal.length; i++)
				pal[i] = c;
			pal[0] = new Color(0, 0, 0, 0);
			referencePal = new Palette(pal);
			referencePal.glLoad();
		}

		glEnable(GL_STENCIL_TEST);
		glClearStencil(0);
		glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);

		glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
	}

	@Override
	public void cleanup(boolean crashed)
	{
		if (referenceTile != null) {
			referenceTile.glDelete();
			referencePal.glDelete();
		}

		if (npcSpriteList.getSelected() != null)
			getConfig().setString(Options.SprLastNpcSprite, npcSpriteList.getSelected().name);

		if (playerSpriteList.getSelected() != null)
			getConfig().setString(Options.SprLastPlayerSprite, playerSpriteList.getSelected().name);

		instance = null;
	}

	@Override
	protected void update(double deltaTime)
	{
		assetWatcher.process();

		switch (editorMode) {
			case Rasters:
			case Palettes:
				trace = sheetCamera.getTraceRay(mouse.getPosX(), mouse.getPosY());

				if (sprite != null) {
					checkPalettesForChanges();
					highlightedImgAsset = sprite.tryAtlasPick(trace);
				}

				handleInput(deltaTime);
				break;

			case Animation:
				trace = animCamera.getTraceRay(mouse.getPosX(), mouse.getPosY());

				if (currentAnim != null) {
					int highlightComp = (trace.pixelData.stencilValue - 1);
					currentAnim.setComponentHighlight(highlightComp);

					if (--playbackCounter <= 0) {
						updateAnim();
						playbackCounter = playbackRate;
					}
				}

				if (dragComp != null)
					componentDragUpdate();

				handleInput(deltaTime);
				break;
		}
	}

	@Override
	protected void resizeViews()
	{
		switch (editorMode) {
			case Animation:
				break;
			case Rasters:
			case Palettes:
				if (sprite != null)
					sprite.centerAtlas(sheetCamera, glCanvasWidth(), glCanvasHeight());
				break;
		}
	}

	@Override
	public void glDraw()
	{
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

		if (unloadSprite != null) {
			unloadSprite.unloadTextures();
			unloadSprite = null;
		}

		if (loadSprite != null) {
			loadSprite.loadTextures();
			loadSprite = null;
		}

		switch (editorMode) {
			case Rasters:
			case Palettes:
				renderSpriteSheet();
				break;
			case Animation:
				renderAnim();
				break;
		}
	}

	private void updateAnim()
	{
		if (sprite == null)
			return;

		while (!playerEventQueue.isEmpty()) {
			if (currentAnim == null)
				continue;

			switch (playerEventQueue.poll()) {
				case Play:
					paused = false;
					break;

				case Pause:
					paused = true;
					break;

				case Reset:
					currentAnim.reset();
					if (paused)
						stepCurrentAnim();
					break;

				case Stop:
					// semantically equivalent to Pause+Reset
					paused = true;
					currentAnim.reset();
					stepCurrentAnim();
					break;

				case NextFrame:
					if (paused)
						stepCurrentAnim();
					break;

				case PrevFrame:
					if (paused && currentAnim != null && currentAnim.animTime < 32768) {
						int prevFrame = currentAnim.animTime - 2;
						currentAnim.reset();
						stepCurrentAnim();

						while (currentAnim.animTime < prevFrame)
							stepCurrentAnim();
					}
					break;

				case Goto:
					int targetTime = playbackTime.getValue() + 2;
					if (targetTime >= 0 && targetTime < 32768) {
						currentAnim.reset();
						stepCurrentAnim();

						while (currentAnim.animTime < targetTime)
							stepCurrentAnim();
					}
					break;

				case End:
					paused = true;
					currentAnim.end();
					commandListPanel.repaint();
					break;

			}
			updatePlaybackStatus();
		}

		if (!paused && currentAnim != null)
			stepCurrentAnim();
	}

	private void stepCurrentAnim()
	{
		for (DragReorderList<?> list : dragLists) {
			if (list.isDragging())
				return;
		}

		currentAnim.step();
		commandListPanel.repaint();
		updatePlaybackStatus();
	}

	/**
	 * Initialize {@link npcSpriteList} from metadata provided by SpriteLoader
	 * and choose the initially selected sprite.
	 */
	private void loadNpcSpriteList()
	{
		assert (SwingUtilities.isEventDispatchThread());

		Collection<SpriteMetadata> spriteMetas = SpriteLoader.getValidSprites(SpriteSet.Npc);
		if (spriteMetas.isEmpty())
			throw new StarRodException("No valid NPC sprites could be found!");

		npcSpriteList.ignoreSelectionChange = true;

		npcSpriteList.setSprites(spriteMetas);

		String lastName = getConfig().getString(Options.SprLastNpcSprite);
		int id = npcSpriteList.getInitialSelection(lastName);
		if (id == -1) {
			npcSpriteList.setSelectedIndex(0);
		}

		while (npcSpriteList.getSelected() == null && id > 0) {
			npcSpriteList.setSelectedIndex(id);
			id--;
		}

		if (id < 0)
			Logger.log("Could not select initial NPC sprite");

		setSprite(npcSpriteList.getSelected(), true);

		npcSpriteList.ignoreSelectionChange = false;
	}

	/**
	 * Initialize {@link playerSpriteList} from metadata provided by SpriteLoader
	 * and choose the initially selected sprite.
	 */
	private void loadPlayerSpriteList()
	{
		assert (SwingUtilities.isEventDispatchThread());

		Collection<SpriteMetadata> spriteMetas = SpriteLoader.getValidSprites(SpriteSet.Player);
		if (spriteMetas.isEmpty())
			throw new StarRodException("No valid player sprites could be found!");

		playerSpriteList.ignoreSelectionChange = true;

		playerSpriteList.setSprites(spriteMetas);

		String lastName = getConfig().getString(Options.SprLastPlayerSprite);
		int id = playerSpriteList.getInitialSelection(lastName);
		if (id == -1) {
			playerSpriteList.setSelectedIndex(0);
		}

		while (playerSpriteList.getSelected() == null && id > 0) {
			playerSpriteList.setSelectedIndex(id);
			id--;
		}

		if (id < 0)
			Logger.log("Could not select initial player sprite");

		playerSpriteList.ignoreSelectionChange = false;
	}

	public void setModesTab(int tabIndex)
	{
		modeTabIndex = tabIndex;
		editorMode = EditorMode.values()[tabIndex];
	}

	public int getModesTab()
	{
		return modeTabIndex;
	}

	public void setSpritesTab(int tabIndex)
	{
		spriteTabIndex = tabIndex;
		SpriteList spriteList;

		if (spriteTabIndex == 0) {
			spriteSet = SpriteSet.Npc;
			spriteList = npcSpriteList;
		}
		else {
			spriteSet = SpriteSet.Player;
			spriteList = playerSpriteList;
		}

		setSprite(spriteList.getSelected(), false);
	}

	public int getSpriteTab()
	{
		return spriteTabIndex;
	}

	public void setSprite(SpriteMetadata newMetadata, boolean forceReload)
	{
		assert (SwingUtilities.isEventDispatchThread());

		if (curMetadata == newMetadata && !forceReload)
			return;

		highlightedImgAsset = null;

		unloadSprite = sprite;

		assetWatcher.release();

		curMetadata = newMetadata;
		Logger.logDetail("Set sprite: " + curMetadata);

		if (curMetadata == null) {
			sprite = null;
			setAnimation(null);
			editorModeTabs.setVisible(false);
			return;
		}

		// save current animation selection for old sprite
		if (sprite != null)
			sprite.lastSelectedAnim = animList.getSelectedIndex();

		sprite = spriteLoader.getSprite(curMetadata, forceReload);

		// suppress selection events from setSelectedIndex and setModel operations on animList
		animList.ignoreSelectionChange = true;

		if (sprite != null) {
			editorModeTabs.setVisible(true);

			assetWatcher.acquire(sprite);

			sprite.prepareForEditor();
			sprite.enableStencilBuffer = true;
			CommandAnimatorEditor.setModels(sprite);
			KeyframeAnimatorEditor.setModels(sprite);

			sprite.makeAtlas();
			sprite.centerAtlas(sheetCamera, glCanvasWidth(), glCanvasHeight());

			sprite.bindPalettes();
			sprite.bindRasters();

			sprite.loadEditorImages();
			loadSprite = sprite;

			rastersTab.setSprite(sprite);
			palettesTab.setSprite(sprite);

			animList.setModel(sprite.animations);

			// restore component selection (if valid) for new animation
			int old = sprite.lastSelectedAnim;
			if (old >= -1 && old < sprite.animations.size()) {
				// note: -1 is a valid index corresponding to no selection
				animList.setSelectedIndex(old);
				setAnimation(animList.getSelectedValue());
			}
			else if (sprite.animations.size() > 0) {
				// safety condition -- remove? should not be needed if undo/redo state intact
				Logger.logfWarning("Reference to out of range animation ID: %d", old);
				animList.setSelectedIndex(0);
				setAnimation(animList.getSelectedValue());
			}
			else {
				// extra safety condition -- also remove?
				Logger.logfWarning("Reference to invalid animation ID: %d", old);
				animList.setSelectedIndex(-1);
				setAnimation(null);
			}

			paletteComboBox.setModel(new ListAdapterComboboxModel<>(sprite.palettes));
			paletteComboBox.setSelectedItem(sprite.overridePalette);
			paletteComboBox.setEnabled(sprite.usingOverridePalette);

			cbOverridePalette.setSelected(sprite.usingOverridePalette);

			animList.setEnabled(true);
		}
		else {
			editorModeTabs.setVisible(false);

			animList.setModel(new DefaultListModel<>()); // empty model
			animList.setEnabled(false);

			setAnimation(null);
		}

		// re-enable selection events from animList
		animList.ignoreSelectionChange = false;
	}

	public Sprite getSprite()
	{
		return sprite;
	}

	public boolean setAnimation(SpriteAnimation animation)
	{
		assert (SwingUtilities.isEventDispatchThread());

		if (currentAnim == animation)
			return true;

		String animName = (animation == null) ? "null" : "" + animation.getIndex();
		Logger.logDetail("Set animation: " + animName);

		// save current component selection for old animation
		if (currentAnim != null)
			currentAnim.lastSelectedComp = compList.getSelectedIndex();

		currentAnim = animation;

		// suppress selection events from setSelectedIndex and setModel operations on compList
		compList.ignoreSelectionChange = true;

		if (currentAnim != null) {
			compList.setModel(currentAnim.components);

			// restore component selection (if valid) for new animation
			int old = currentAnim.lastSelectedComp;
			if (old >= -1 && old < currentAnim.components.size()) {
				// note: -1 is a valid index corresponding to no selection
				compList.setSelectedIndex(old);
				setComponent(compList.getSelectedValue());
			}
			else if (currentAnim.components.size() > 0) {
				// safety condition -- remove? should not be needed if undo/redo state intact
				Logger.logfWarning("Reference to out of range component ID: %d", old);
				compList.setSelectedIndex(0);
				setComponent(compList.getSelectedValue());
			}
			else {
				// extra safety condition -- also remove?
				Logger.logfWarning("Reference to invalid component ID: %d", old);
				compList.setSelectedIndex(-1);
				setComponent(null);
			}

			compList.setEnabled(true);
		}
		else {
			compList.setModel(new DefaultListModel<>()); // empty model
			compList.setEnabled(false);

			setComponent(null);
		}

		// re-enable selection events from compList
		compList.ignoreSelectionChange = false;

		playerEventQueue.add(PlayerEvent.Reset);
		return true;
	}

	public SpriteAnimation getAnimation()
	{
		return currentAnim;
	}

	private void setComponentByID(int id)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (currentAnim == null || id < 0 || id >= currentAnim.components.size())
			return;

		SwingUtilities.invokeLater(() -> {
			compList.setSelectedIndex(id);
		});
	}

	/**
	 * Sets <code>currentComp</code> and updates the content of component panel accordingly
	 * @param component
	 */
	public void setComponent(SpriteComponent component)
	{
		assert (SwingUtilities.isEventDispatchThread());

		currentComp = component;

		if (currentComp == null) {
			Logger.logDetail("Set component: NULL");
			componentPanel.setVisible(false);

			if (currentAnim != null)
				currentAnim.setComponentSelected(-1);
		}
		else {
			Logger.logDetail("Set component: " + currentComp.name);
			componentPanel.setVisible(true);

			if (currentAnim != null)
				currentAnim.setComponentSelected(currentComp.listIndex);

			currentComp.bind(this, commandListPanel, commandEditPanel);
			currentComp.calculateTiming();

			suppressCommands = true;
			compxSpinner.setValue(currentComp.posx);
			compySpinner.setValue(currentComp.posy);
			compzSpinner.setValue(currentComp.posz);
			suppressCommands = false;

			componentPanel.repaint();
		}
	}

	public SpriteComponent getComponent()
	{
		return currentComp;
	}

	private void checkPalettesForChanges()
	{
		for (PalAsset asset : sprite.palAssets) {
			if (asset.dirty) {
				asset.pal.glReload();
				asset.dirty = false;
			}
		}
	}

	private void handleInput(double deltaTime)
	{
		switch (editorMode) {
			case Animation: {
				animCamera.handleInput(keyboard, mouse, deltaTime, glCanvasWidth(), glCanvasHeight());
				if (keyboard.isKeyDown(KeyEvent.VK_SPACE))
					animCamera.reset();
			}
				break;
			case Rasters:
			case Palettes: {
				sheetCamera.handleInput(keyboard, mouse, deltaTime, glCanvasWidth(), glCanvasHeight());
				if (keyboard.isKeyDown(KeyEvent.VK_SPACE))
					sprite.centerAtlas(sheetCamera, glCanvasWidth(), glCanvasHeight());
			}
				break;
			default:
				throw new IllegalStateException("Invalid editor mode in handleInput().");
		}
	}

	@Override
	public void keyPress(KeyInputEvent key)
	{
		boolean ctrl = keyboard.isCtrlDown();
		boolean alt = keyboard.isAltDown();
		boolean shift = keyboard.isShiftDown();

		switch (editorMode) {
			case Animation:
				if (key.code == KeyEvent.VK_DELETE && !ctrl && !alt && !shift) {
					if (sprite != null && currentAnim != null && currentComp != null && currentComp.selected) {
						SwingUtilities.invokeLater(() -> {
							CommandBatch batch = new CommandBatch("Delete Component");
							batch.addCommand(new SelectComponent(compList, null));
							batch.addCommand(new DeleteComponent(currentAnim, currentComp));
							SpriteEditor.execute(batch);
						});
					}
					return;
				}
				else if (key.code == KeyEvent.VK_H && !ctrl && !alt && !shift) {
					if (currentComp != null)
						currentComp.hidden = !currentComp.hidden;

					compList.repaint();
					return;
				}
				break;
			case Palettes:
			case Rasters:
				break;
			default:
				throw new IllegalStateException("Invalid editor mode in handleInput().");
		}

		SwingUtilities.invokeLater(() -> {
			handleKey(ctrl, alt, shift, key.code);
		});
	}

	private void renderSpriteSheet()
	{
		sheetCamera.glSetViewport(0, 0, glCanvasWidth(), glCanvasHeight());

		if (showBackground)
			sheetCamera.drawBackground();

		sheetCamera.setOrthoView();
		sheetCamera.glLoadTransform();
		RenderState.setModelMatrix(null);

		//	drawAxes(1.0f);

		if (sprite == null)
			return;

		ImgAsset highlight = highlightedImgAsset;

		ImgAsset selected = null;
		if (editorMode == EditorMode.Rasters)
			selected = sprite.selectedImgAsset;

		Palette overridePal = null;
		if (editorMode == EditorMode.Palettes && sprite.selectedPalAsset != null)
			overridePal = sprite.selectedPalAsset.pal;

		sprite.renderAtlas(selected, highlight, overridePal, useFiltering);
	}

	private void renderAnim()
	{
		animCamera.glSetViewport(0, 0, glCanvasWidth(), glCanvasHeight());

		if (showBackground)
			animCamera.drawBackground();

		animCamera.setOrthoView();
		animCamera.glLoadTransform();
		RenderState.setModelMatrix(null);

		if (showAxes)
			drawAxes(1.0f);

		if (showGuide && referenceTile != null)
			renderReference();

		if (flipHorizontal) {
			TransformMatrix mtx = TransformMatrix.identity();
			mtx.scale(-1.0, 1.0, 1.0);
			RenderState.setModelMatrix(mtx);
		}

		glEnable(GL_STENCIL_TEST);
		glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);

		if (currentAnim != null) {
			SpritePalette override = sprite.usingOverridePalette ? sprite.overridePalette : null;

			if (cbShowOnlySelectedComponent.isSelected() && currentComp != null)
				sprite.render(null, currentAnim, currentComp, override, useBackFacing, highlightComponent, false, useFiltering);
			else
				sprite.render(null, currentAnim, override, useBackFacing, highlightComponent, false, useFiltering);
		}

		glDisable(GL_STENCIL_TEST);
	}

	private void renderReference()
	{
		if (sprite == null)
			return;

		int xmax = flipHorizontal ? -sprite.aabb.min.getX() : sprite.aabb.max.getX();
		if (xmax != 0) {
			int delta = xmax - referencePos;
			if (referencePos == 0 || delta >= 30)
				referencePos = xmax;
			else
				referencePos = referencePos + (xmax - referencePos) / 5;
		}

		TransformMatrix mtx = RenderState.pushModelMatrix();
		mtx.translate(referencePos + 20, 0, 0);

		SpriteShader shader = ShaderManager.use(SpriteShader.class);

		referenceTile.glBind(shader.texture);
		referencePal.glBind(shader.palette);

		shader.useFiltering.set(useFiltering);
		shader.selected.set(false);
		shader.highlighted.set(false);

		float h = referenceTile.height;
		float w = referenceTile.width / 2;

		shader.setXYQuadCoords(-w, 0, w, h, 0);
		shader.renderQuad(mtx);

		RenderState.popModelMatrix();
	}

	private static void drawAxes(float lineWidth)
	{
		RenderState.setLineWidth(lineWidth);

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, 0, -10).setColor(PresetColor.RED).getIndex(),
			LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, 0, -10).setColor(PresetColor.RED).getIndex());

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, 0, -10).setColor(PresetColor.GREEN).getIndex(),
			LineRenderQueue.addVertex().setPosition(0, Short.MAX_VALUE, -10).setColor(PresetColor.GREEN).getIndex());

		LineRenderQueue.render(true);
	}

	@Override
	protected void createGui(JPanel toolPanel, Canvas glCanvas, JMenuBar menuBar, JLabel infoLabel, ActionListener openLogAction)
	{
		rastersTab = new RastersTab(this);
		palettesTab = new PalettesTab(this);

		CommandAnimatorEditor.init();
		KeyframeAnimatorEditor.init();

		editorModeTabs = new JTabbedPane();
		editorModeTabs.addTab(EditorMode.Rasters.tabName, rastersTab);
		editorModeTabs.addTab(EditorMode.Palettes.tabName, palettesTab);
		editorModeTabs.addTab(EditorMode.Animation.tabName, getAnimationsTab());

		editorModeTabs.addChangeListener((e) -> {
			JTabbedPane tabs = (JTabbedPane) e.getSource();
			int index = tabs.getSelectedIndex();

			if (!suppressCommands)
				execute(new SelectModesTab(tabs, index));
		});

		JPanel playerSpriteListPanel = new JPanel(new MigLayout("fill, ins 0"));
		playerSpriteListPanel.add(playerSpriteList, "growy, pushy, gaptop 16, gapleft 8, gapright 8");

		JPanel npcSpriteListPanel = new JPanel(new MigLayout("fill, ins 0"));
		npcSpriteListPanel.add(npcSpriteList, "growy, pushy, gaptop 16, gapleft 8, gapright 8");

		JTabbedPane spriteSetTabs = new JTabbedPane();
		spriteSetTabs.addTab("NPC Sprites", npcSpriteListPanel);
		spriteSetTabs.addTab("Player Sprites", playerSpriteListPanel);

		spriteSetTabs.addChangeListener((e) -> {
			JTabbedPane tabs = (JTabbedPane) e.getSource();
			int index = tabs.getSelectedIndex();

			if (!suppressCommands)
				execute(new SelectSpritesTab(tabs, index));
		});

		toolPanel.add(spriteSetTabs, "grow, w " + LEFT_PANEL_WIDTH + "!");
		toolPanel.add(glCanvas, "grow, push, gapleft 8, gapright 8");
		toolPanel.add(editorModeTabs, "grow, wrap, w " + RIGHT_PANEL_WIDTH + "!");

		toolPanel.add(infoLabel, "h 16!, growx, span");

		addOptionsMenu(menuBar, openLogAction);
		addEditorMenu(menuBar);
		addSpriteMenu(menuBar);
		addRenderingMenu(menuBar);

		KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
		manager.addKeyEventDispatcher(e -> {
			if (e.getID() == KeyEvent.KEY_PRESSED)
				return handleKey(e.isControlDown(), e.isAltDown(), e.isShiftDown(), e.getKeyCode());
			else
				return false;
		});
	}

	private boolean handleKey(boolean ctrl, boolean alt, boolean shift, int key)
	{
		// no multiple modifers
		int count = 0;
		if (ctrl)
			count++;
		if (alt)
			count++;
		if (shift)
			count++;
		if (count > 1)
			return false;

		if (editorMode == EditorMode.Animation && alt) {
			switch (key) {
				case KeyEvent.VK_HOME:
					resetButton.doClick();
					return true;
				case KeyEvent.VK_UP:
					playButton.doClick();
					return true;
				case KeyEvent.VK_DOWN:
					stopButton.doClick();
					return true;
				case KeyEvent.VK_LEFT:
					prevFrameButton.doClick();
					return true;
				case KeyEvent.VK_RIGHT:
					nextFrameButton.doClick();
					return true;
			}
		}

		return false;
	}

	private void addEditorMenu(JMenuBar menuBar)
	{
		JMenuItem item;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Editor" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("View Shortcuts");
		item.addActionListener((e) -> {
			showControls();
		});
		menu.add(item);
	}

	private void addOptionsMenu(JMenuBar menuBar, ActionListener openLogAction)
	{
		JMenuItem item;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Options" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		/*
		item = new JMenuItem("Create New Sprite");
		item.addActionListener((e)-> {
			invokeLater(() -> {
				try {
					int id = SpriteLoader.getMaximumID(spriteSet) + 1;
					SpriteLoader.create(spriteSet, id);

					if(spriteSet == SpriteSet.Npc)
						useNpcFiles(id);
					else
						usePlayerFiles(id);

				} catch (Throwable t) {
					Logger.logError("Failed to create new sprite.");
					incrementDialogsOpen();
					StarRodDev.displayStackTrace(t);
					decrementDialogsOpen();
				}
			});
		});
		menu.add(item);

		menu.addSeparator();
		 */

		item = new JMenuItem("Open Log");
		item.addActionListener(openLogAction);
		menu.add(item);

		item = new JMenuItem("Switch Tools");
		item.addActionListener((e) -> {
			invokeLater(() -> {
				super.close(true);
			});
		});
		menu.add(item);

		item = new JMenuItem("Exit");
		item.addActionListener((e) -> {
			invokeLater(() -> {
				super.close(false);
			});
		});
		menu.add(item);
	}

	private void addSpriteMenu(JMenuBar menuBar)
	{
		JMenuItem item;
		KeyStroke awtKeyStroke;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Sprite" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Save Changes");
		awtKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
		item.setAccelerator(awtKeyStroke);
		item.addActionListener((e) -> {
			saveSprite();
		});
		menu.add(item);

		item = new JMenuItem("Reload Current Sprite");
		item.addActionListener((e) -> {
			// remove sprite from 'modified' list
			sprite.modified = false;
			modifiedSprites.remove(sprite);

			setSprite(curMetadata, true);
		});
		menu.add(item);

		item = new JMenuItem("Open Content Folder");
		item.addActionListener((evt) -> {
			if (sprite == null)
				return;
			try {
				Desktop.getDesktop().open(new File(sprite.getDirectoryName()));
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Convert to Keyframes");
		item.addActionListener((e) -> {
			if (sprite != null) {
				sprite.convertToKeyframes();
				setComponent(currentComp);
			}
		});
		menu.add(item);

		item = new JMenuItem("Convert to Commands");
		item.addActionListener((e) -> {
			if (sprite != null) {
				sprite.convertToCommands();
				setComponent(currentComp);
			}
		});
		menu.add(item);
	}

	private void addRenderingMenu(JMenuBar menuBar)
	{
		JMenu menu = new JMenu(MENU_BAR_SPACING + "Rendering" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		final JMenuItem itemAxes = new JCheckBoxMenuItem("Show Axes");
		itemAxes.setSelected(showAxes);
		itemAxes.addActionListener((e) -> {
			showAxes = itemAxes.isSelected();
			getConfig().setBoolean(Options.SprEnableAxes, showAxes);
		});
		menu.add(itemAxes);

		final JMenuItem itemBackground = new JCheckBoxMenuItem("Show Background");
		itemBackground.setSelected(showBackground);
		itemBackground.addActionListener((e) -> {
			showBackground = itemBackground.isSelected();
			getConfig().setBoolean(Options.SprEnableBackground, showBackground);
		});
		menu.add(itemBackground);

		final JMenuItem itemFilter = new JCheckBoxMenuItem("Use Filtering");
		itemFilter.setSelected(useFiltering);
		itemFilter.addActionListener((e) -> {
			useFiltering = itemFilter.isSelected();
			getConfig().setBoolean(Options.SprUseFiltering, useFiltering);
		});
		menu.add(itemFilter);

		menu.addSeparator();

		final JMenuItem itemGuide = new JCheckBoxMenuItem("Show Scale Reference");
		itemGuide.setSelected(showGuide);
		itemGuide.addActionListener((e) -> {
			showGuide = itemGuide.isSelected();
			getConfig().setBoolean(Options.SprShowScaleReference, showGuide);
		});
		menu.add(itemGuide);

		final JMenuItem itemFlip = new JCheckBoxMenuItem("Flip Horizontally");
		itemFlip.setSelected(flipHorizontal);
		itemFlip.addActionListener((e) -> {
			flipHorizontal = itemFlip.isSelected();
			getConfig().setBoolean(Options.SprFlipHorizontal, flipHorizontal);
		});
		menu.add(itemFlip);

		final JMenuItem itemBack = new JCheckBoxMenuItem("Show Back Facing");
		itemBack.setSelected(useBackFacing);
		itemBack.addActionListener((e) -> {
			useBackFacing = itemBack.isSelected();
			getConfig().setBoolean(Options.SprBackFacing, useBackFacing);
		});
		menu.add(itemBack);

		menu.addSeparator();

		final JMenuItem itemHighlightComp = new JCheckBoxMenuItem("Highlight Selected Component");
		itemHighlightComp.setSelected(highlightComponent);
		itemHighlightComp.addActionListener((e) -> {
			highlightComponent = itemHighlightComp.isSelected();
			getConfig().setBoolean(Options.SprHighlightSelected, highlightComponent);
		});
		menu.add(itemHighlightComp);

		final JMenuItem itemHighlightCmd = new JCheckBoxMenuItem("Highlight Current Command");
		itemHighlightCmd.setSelected(highlightCommand);
		itemHighlightCmd.addActionListener((e) -> {
			highlightCommand = itemHighlightCmd.isSelected();
			getConfig().setBoolean(Options.SprHighlightCommand, highlightCommand);
		});
		menu.add(itemHighlightCmd);
	}

	private JPanel getPlaybackPanel()
	{
		final Insets buttonInsets = new Insets(0, 2, 0, 2);

		resetButton = new JButton(ThemedIcon.REWIND_24);
		resetButton.setToolTipText("Reset");
		resetButton.setMargin(buttonInsets);
		resetButton.addActionListener((e) -> {
			playerEventQueue.add(PlayerEvent.Reset);
		});

		endButton = new JButton(ThemedIcon.FFWD_24);
		endButton.setToolTipText("Goto End");
		endButton.setMargin(buttonInsets);
		endButton.addActionListener((e) -> {
			playerEventQueue.add(PlayerEvent.End);
		});

		prevFrameButton = new JButton(ThemedIcon.PREV_24);
		prevFrameButton.setToolTipText("Step Back");
		prevFrameButton.setMargin(buttonInsets);
		prevFrameButton.addActionListener((e) -> {
			playerEventQueue.add(PlayerEvent.PrevFrame);
		});
		prevFrameButton.setEnabled(false);

		nextFrameButton = new JButton(ThemedIcon.NEXT_24);
		nextFrameButton.setToolTipText("Step Forward");
		nextFrameButton.setMargin(buttonInsets);
		nextFrameButton.addActionListener((e) -> {
			playerEventQueue.add(PlayerEvent.NextFrame);
		});
		nextFrameButton.setEnabled(false);

		playButton = new JButton(ThemedIcon.PAUSE_24);

		stopButton = new JButton(ThemedIcon.STOP_24);
		stopButton.setToolTipText("Stop");
		stopButton.setMargin(buttonInsets);
		stopButton.addActionListener((e) -> {
			playerEventQueue.add(PlayerEvent.Stop);

			// stop playing, button is now 'play'
			playerEventQueue.add(PlayerEvent.Stop);
			playButton.setIcon(ThemedIcon.PLAY_24);
			playButton.setToolTipText("Play");
			nextFrameButton.setEnabled(true);
			prevFrameButton.setEnabled(true);
			stopButton.setEnabled(false);
		});

		playButton.setMargin(buttonInsets);
		playButton.addActionListener((e) -> {
			if (paused) {
				// start playing, button is now 'pause'
				playerEventQueue.add(PlayerEvent.Play);
				playButton.setIcon(ThemedIcon.PAUSE_24);
				playButton.setToolTipText("Pause");
				nextFrameButton.setEnabled(false);
				prevFrameButton.setEnabled(false);
				stopButton.setEnabled(true);
			}
			else {
				// pause playing, button is now 'play'
				playerEventQueue.add(PlayerEvent.Pause);
				playButton.setIcon(ThemedIcon.PLAY_24);
				playButton.setToolTipText("Play");
				nextFrameButton.setEnabled(true);
				prevFrameButton.setEnabled(true);
				stopButton.setEnabled(false);
			}
			super.revalidateFrame();
		});

		String fmt = "sg button, pushx, growx, w 48!, h 32!";
		JPanel controlButtons = new JPanel(new MigLayout("fill, ins 0", "[]0"));
		controlButtons.add(resetButton, fmt);
		controlButtons.add(prevFrameButton, fmt);
		controlButtons.add(stopButton, fmt);
		controlButtons.add(playButton, fmt);
		controlButtons.add(nextFrameButton, fmt);

		playbackTime = new IntTextField((v) -> playerEventQueue.add(PlayerEvent.Goto));
		playbackTime.setHorizontalAlignment(SwingConstants.CENTER);

		JComboBox<String> playbackRateBox = new JComboBox<>(new String[] {
				"Full", "1 / 2", "1 / 4", "1 / 8", "1 / 16", "1 / 32" });
		SwingUtils.addBorderPadding(playbackRateBox);
		playbackRateBox.addActionListener((e) -> {
			switch (playbackRateBox.getSelectedIndex()) {
				// @formatter:off
				default: playbackRate = 1; break;
				case 1: playbackRate = 2; break;
				case 2: playbackRate = 4; break;
				case 3: playbackRate = 8; break;
				case 4: playbackRate = 16; break;
				case 5: playbackRate = 32; break;
				// @formatter:on
			}
			playbackCounter = playbackRate;
		});
		DefaultListCellRenderer listRenderer = new DefaultListCellRenderer();
		listRenderer.setHorizontalAlignment(DefaultListCellRenderer.CENTER);
		playbackRateBox.setRenderer(listRenderer);

		JPanel controlPanel = new JPanel(new MigLayout("fill, ins 0", "[pref]10[grow, sg edge]40[center]40[pref]10[grow, sg edge]"));

		controlPanel.add(new JLabel("Speed"));
		controlPanel.add(playbackRateBox, "growx, sgy field");

		controlPanel.add(controlButtons);

		controlPanel.add(new JLabel("Frame"));
		controlPanel.add(playbackTime, "growx, sgy field");

		return controlPanel;
	}

	private JPanel getComponentOffsetsPanel()
	{
		compxSpinner = new JSpinner();
		SwingUtils.setFontSize(compxSpinner, 12);
		compxSpinner.setModel(new SpinnerNumberModel(0, -128, 128, 1));
		compxSpinner.addChangeListener((e) -> {
			if (!suppressCommands)
				execute(new SetComponentPosCommand(currentComp, 0, (int) compxSpinner.getValue()));
		});
		SwingUtils.centerSpinnerText(compxSpinner);
		SwingUtils.addBorderPadding(compxSpinner);

		compySpinner = new JSpinner();
		SwingUtils.setFontSize(compySpinner, 12);
		compySpinner.setModel(new SpinnerNumberModel(0, -128, 128, 1));
		compySpinner.addChangeListener((e) -> {
			if (!suppressCommands)
				execute(new SetComponentPosCommand(currentComp, 1, (int) compySpinner.getValue()));
		});
		SwingUtils.centerSpinnerText(compySpinner);
		SwingUtils.addBorderPadding(compySpinner);

		compzSpinner = new EvenSpinner();
		SwingUtils.setFontSize(compzSpinner, 12);
		compzSpinner.setModel(new SpinnerNumberModel(0, -32, 32, 1));
		compzSpinner.addChangeListener((e) -> {
			if (!suppressCommands)
				execute(new SetComponentPosCommand(currentComp, 2, (int) compzSpinner.getValue()));
		});
		SwingUtils.centerSpinnerText(compzSpinner);
		SwingUtils.addBorderPadding(compzSpinner);

		JPanel relativePosPanel = new JPanel(new MigLayout("fill, ins 0, wrap", "[pref]8[sg spin]4[sg spin]4[sg spin]"));
		relativePosPanel.add(SwingUtils.getLabel("Pos offset", 12), "pushx");
		relativePosPanel.add(compxSpinner, "w 72!");
		relativePosPanel.add(compySpinner, "w 72!");
		relativePosPanel.add(compzSpinner, "w 72!");

		return relativePosPanel;
	}

	public class SetComponentPosCommand extends AbstractCommand
	{
		private final SpriteComponent comp;
		private final int coord;
		private final int next;
		private final int prev;

		public SetComponentPosCommand(SpriteComponent comp, int coord, int next)
		{
			super("Set Component Offset");

			this.comp = comp;
			this.coord = coord;
			this.next = next;

			switch (coord) {
				case 0:
					this.prev = comp.posx;
					break;
				case 1:
					this.prev = comp.posy;
					break;
				default:
					this.prev = comp.posz;
					break;
			}
		}

		@Override
		public void exec()
		{
			switch (coord) {
				case 0:
					comp.posx = next;
					break;
				case 1:
					comp.posy = next;
					break;
				default:
					comp.posz = next;
					break;
			}

			suppressCommands = true;
			switch (coord) {
				case 0:
					compxSpinner.setValue(next);
					break;
				case 1:
					compySpinner.setValue(next);
					break;
				default:
					compzSpinner.setValue(next);
					break;
			}
			suppressCommands = false;
		}

		@Override
		public void undo()
		{
			switch (coord) {
				case 0:
					comp.posx = prev;
					break;
				case 1:
					comp.posy = prev;
					break;
				default:
					comp.posz = prev;
					break;
			}

			suppressCommands = true;
			switch (coord) {
				case 0:
					compxSpinner.setValue(prev);
					break;
				case 1:
					compySpinner.setValue(prev);
					break;
				default:
					compzSpinner.setValue(prev);
					break;
			}
			suppressCommands = false;
		}
	}

	private JPanel getComponentPanel()
	{
		JPanel relativePosPanel = getComponentOffsetsPanel();

		commandListPanel = new JPanel(new MigLayout("ins 0, fill"));
		commandEditPanel = new JPanel(new MigLayout("ins 0, fill"));

		componentPanel = new JPanel(new MigLayout("fill, ins 0, gapy 8", "[sg half]32[sg half]"));
		componentPanel.add(SwingUtils.getLabel("Component Properties", 14), "span, grow");

		componentPanel.add(relativePosPanel, "grow, wrap");
		componentPanel.add(commandListPanel, "grow, pushy");
		componentPanel.add(commandEditPanel, "grow, pushy");

		return componentPanel;
	}

	private JPanel getAnimationsTab()
	{
		animList = new AnimationsList(this);
		compList = new ComponentsList(this);

		cbShowOnlySelectedComponent = new JCheckBox(" Draw current only");
		cbShowOnlySelectedComponent.setSelected(false);
		cbShowOnlySelectedComponent.addActionListener((e) -> {
			if (!suppressCommands)
				execute(new ToggleDrawCurrent(cbShowOnlySelectedComponent));
		});

		paletteComboBox = new JComboBox<>();
		SwingUtils.setFontSize(paletteComboBox, 14);
		paletteComboBox.setMaximumRowCount(24);
		//XXX	paletteComboBox.setRenderer(new IndexableComboBoxRenderer());
		paletteComboBox.addActionListener((e) -> {
			if (!suppressCommands && sprite != null)
				execute(new SetOverridePalette(paletteComboBox, sprite));
		});

		cbOverridePalette = new JCheckBox(" Override palette");
		cbOverridePalette.setSelected(false);
		cbOverridePalette.addActionListener((e) -> {
			if (!suppressCommands && sprite != null)
				execute(new TogglePaletteOverride(cbOverridePalette, sprite, (b) -> {
					paletteComboBox.setEnabled(b);
				}));
		});

		JButton btnAddAnim = new JButton(ThemedIcon.ADD_16);
		btnAddAnim.setToolTipText("Add new animation");
		btnAddAnim.addActionListener((e) -> {
			if (sprite == null) {
				return;
			}

			if (sprite.animations.size() >= Sprite.MAX_ANIMATIONS) {
				Logger.logError("Cannot have more than " + Sprite.MAX_ANIMATIONS + " animations!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			SpriteAnimation anim = new SpriteAnimation(sprite);
			String newName = anim.createUniqueName(String.format("Anim_%X", sprite.animations.size()));

			if (newName == null) {
				Logger.logError("Could not generate valid name for new animation!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			anim.name = newName;
			CommandBatch batch = new CommandBatch("Add Animation");
			batch.addCommand(new CreateAnimation("Add Animation", sprite, anim));
			batch.addCommand(new SelectAnimation(animList, anim));
			execute(batch);
		});

		JButton btnAddComp = new JButton(ThemedIcon.ADD_16);
		btnAddComp.setToolTipText("Add new component");
		btnAddComp.addActionListener((e) -> {
			if (sprite == null) {
				return;
			}

			if (currentAnim.components.size() >= Sprite.MAX_COMPONENTS) {
				Logger.logError("Cannot have more than " + Sprite.MAX_COMPONENTS + " components!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			SpriteComponent comp = new SpriteComponent(currentAnim);
			String newName = comp.createUniqueName(String.format("Comp_%X", currentAnim.components.size()));

			if (newName == null) {
				Logger.logError("Could not generate valid name for new component!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			comp.name = newName;
			CommandBatch batch = new CommandBatch("Add Component");
			batch.addCommand(new CreateComponent("Add Component", currentAnim, comp));
			batch.addCommand(new SelectComponent(compList, comp));
			execute(batch);
		});

		JScrollPane animScrollPane = new JScrollPane(animList);
		animScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		JScrollPane compScrollPane = new JScrollPane(compList);
		compScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		JPanel listsPanel = new JPanel(new MigLayout("fill, ins 0, wrap 2", "[grow, sg col][grow, sg col]"));

		listsPanel.add(btnAddAnim, "split 2");
		listsPanel.add(new JLabel("Animations"), "growx");

		listsPanel.add(btnAddComp, "split 3");
		listsPanel.add(new JLabel("Components"), "growx");
		listsPanel.add(cbShowOnlySelectedComponent, "gapleft push, align right");

		listsPanel.add(animScrollPane, "pushy, grow, sg list");
		listsPanel.add(compScrollPane, "pushy, grow, sg list");

		JPanel playbackPanel = getPlaybackPanel();

		componentPanel = getComponentPanel();

		JPanel animTab = new JPanel(new MigLayout("fill, wrap, ins 16 16 0 16, gapy 8"));
		animTab.add(listsPanel, "grow, h 25%");

		animTab.add(cbOverridePalette, "span, split 2, gapright 8, aligny center");
		animTab.add(paletteComboBox, "growx");
		animTab.add(playbackPanel, "span, growx, gaptop 8");
		animTab.add(componentPanel, "span, grow, pushy");

		componentPanel.setVisible(false);

		return animTab;
	}

	private void showControls()
	{
		incrementDialogsOpen();

		SwingUtils.getMessageDialog()
			.setParent(getFrame())
			.setTitle("Controls and Shortcuts")
			.setMessage(new SpriteShortcutsPanel())
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.show();

		decrementDialogsOpen();
	}

	private void saveSprite()
	{
		if (sprite == null)
			return;

		File xmlFile = sprite.source;
		if (!xmlFile.exists()) {
			super.showErrorDialog(
				"Missing XML File",
				"Could not find XML file:",
				xmlFile.getAbsolutePath());
			return;
		}

		sprite.revalidate();
		sprite.saveChanges();
		assert (sprite.source.getParentFile().isDirectory());

		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			sprite.savePalettes();
			sprite.toXML(xmw);
			xmw.save();

			// clear 'modified' status for sprite
			sprite.modified = false;
			modifiedSprites.remove(sprite);

			// force a reload
			setSprite(curMetadata, true);
			Logger.log("Saved sprite to /" + xmlFile.getParentFile().getName() + "/");
		}
		catch (Throwable t) {
			Logger.logError("Failed to save sprite /" + xmlFile.getParentFile().getName() + "/");
			super.showStackTrace(t);
		}
	}

	public void notifyModified()
	{
		if (sprite != null) {
			sprite.modified = true;
			modifiedSprites.add(sprite);
		}
	}

	@Override
	protected boolean isModified()
	{
		return !modifiedSprites.isEmpty();
	}

	@Override
	protected void saveChanges()
	{
		saveSprite();
	}

	@Override
	public void clickLMB()
	{
		switch (editorMode) {
			case Rasters:
				ImgAsset picked = sprite.tryAtlasPick(trace);
				SwingUtilities.invokeLater(() -> {
					rastersTab.selectAsset(picked);
				});
				break;
			case Palettes:
				break;
			case Animation:
				if (sprite == null || currentAnim == null)
					return;

				int selected = (trace.pixelData.stencilValue - 1);
				currentAnim.setComponentSelected(selected);
				if (selected >= 0)
					setComponentByID(selected);
				break;
		}
	}

	@Override
	public void startHoldingLMB()
	{
		if (editorMode == EditorMode.Animation && currentComp != null && currentComp.highlighted)
			componentDragBegin();
	}

	@Override
	public void stopHoldingLMB()
	{
		if (editorMode == EditorMode.Animation && dragComp != null)
			componentDragEnd();
	}

	@Override
	public void mouseExit()
	{
		if (editorMode == EditorMode.Animation && dragComp != null)
			componentDragEnd();
	}

	private void componentDragBegin()
	{
		dragComp = currentComp;
		dragComp.dragX = 0;
		dragComp.dragY = 0;

		dragStartX = animCamera.toWorldX(mouse.getPosX());
		dragStartY = animCamera.toWorldY(mouse.getPosY());
	}

	private void componentDragEnd()
	{
		CommandBatch batch = new CommandBatch("Set Component Offset");
		batch.addCommand(new SetComponentPosCommand(dragComp, 0, dragComp.posx + dragComp.dragX));
		batch.addCommand(new SetComponentPosCommand(dragComp, 1, dragComp.posy + dragComp.dragY));
		execute(batch);

		dragComp.dragX = 0;
		dragComp.dragY = 0;
		dragComp = null;
	}

	private void componentDragUpdate()
	{
		dragComp.dragX = Math.round(animCamera.toWorldX(mouse.getPosX()) - dragStartX);
		dragComp.dragY = Math.round(animCamera.toWorldY(mouse.getPosY()) - dragStartY);

		if (flipHorizontal)
			dragComp.dragX = -dragComp.dragX;

		if (currentComp != null) {
			SwingUtilities.invokeLater(() -> {
				suppressCommands = true;
				compxSpinner.setValue(currentComp.posx + currentComp.dragX);
				compySpinner.setValue(currentComp.posy + currentComp.dragY);
				suppressCommands = false;
			});
		}
	}

	public void resetAnimation()
	{
		playerEventQueue.add(PlayerEvent.Reset);
	}

	public void updatePlaybackStatus()
	{
		//TODO creates flickering of mouse icon during drag and drop
		runInEDT(() -> {
			if (currentAnim == null) {
				playbackTime.setEnabled(false);
				playbackTime.setValue(0);
			}
			else {
				playbackTime.setEnabled(true);
				playbackTime.setValue(Math.max(currentAnim.animTime - 2, 0));
			}
		});
	}

	public void registerDragList(DragReorderList<?> list)
	{
		dragLists.add(list);
	}

	public SpriteRaster promptForRaster(Sprite s)
	{
		RasterSelectDialog dialog = new RasterSelectDialog(s.rasters);
		showModalDialog(dialog, "Choose Raster");
		return dialog.getSelected();
	}
}
