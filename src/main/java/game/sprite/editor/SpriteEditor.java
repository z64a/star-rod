package game.sprite.editor;

import static app.Directories.FN_SPRITE_EDITOR_CONFIG;
import static org.lwjgl.opengl.GL11.*;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
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
import common.commands.CommandManager;
import game.map.editor.render.PresetColor;
import game.map.editor.render.TextureManager;
import game.map.shape.TransformMatrix;
import game.sprite.ImgAsset;
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
import game.sprite.editor.commands.SpriteCommandManager;
import game.texture.Palette;
import game.texture.Tile;
import net.miginfocom.swing.MigLayout;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.SpriteShader;
import util.Logger;
import util.ui.DragReorderList;
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

	private static final int MIN_SPRITE_IDX = 1;

	private static final int EYE_ICON_WIDTH = 16;

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

	private SpriteList playerSpriteList;
	private SpriteList npcSpriteList;

	private JComboBox<SpritePalette> paletteComboBox;

	private DragReorderList<SpriteAnimation> animList;
	private DragReorderList<SpriteComponent> compList;

	private SpriteAnimation animClipboard;
	private SpriteComponent compClipboard;

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

	private boolean useAtlasOverride = false;

	private static enum EditorMode
	{
		// @formatter:off
		Rasters 	("Rasters"),
		Palettes	("Palettes"),
		Animation	("Animations");
		// @formatter:on

		public final String tabName;

		private EditorMode(String tabName)
		{
			this.tabName = tabName;
		}
	}

	private static EditorMode editorMode = EditorMode.Rasters;

	private SpriteSet spriteSet = SpriteSet.Npc;

	private Tile referenceTile;
	private Palette referencePal;
	private int referenceMaxPos;

	// only a single sprite can be selected at a time
	private Sprite sprite;
	private SpriteAnimation currentAnim;
	private SpriteComponent currentComp;
	private ImgAsset selectedImgAsset;
	private ImgAsset highlightedImgAsset;

	private volatile SpritePalette animOverridePalette;

	// fields used by the renderer, don't set these from the gui
	private volatile int spriteID;

	private CommandManager commandManager; // handles comnmand execution and undo/redo

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

		animCamera = new SpriteCamera(
			0.0f, 32.0f, 0.3f,
			0.5f, 0.125f, 2.0f,
			true, false);

		sheetCamera = new SpriteCamera(
			0.0f, 0.0f, 0.7f,
			1.0f, 0.125f, 2.0f,
			true, true);

		loadPlayerSpriteList();
		loadNpcSpriteList();

		assetWatcher = new SpriteAssetWatcher();

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
		if (SwingUtilities.isEventDispatchThread())
			instance().invokeLater(() -> instance().commandManager.executeCommand(cmd));
		else
			instance().commandManager.executeCommand(cmd);
	}

	public void flushUndoRedo()
	{
		commandManager.flush();
	}

	@Override
	public void beforeCreateGui()
	{
		Config cfg = getConfig();
		if (cfg != null) {
			useFiltering = cfg.getBoolean(Options.SprUseFiltering);
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

		commandManager = new SpriteCommandManager(this, 32);
	}

	@Override
	public void glInit()
	{
		TextureManager.bindEditorTextures();

		Sprite referenceSprite = spriteLoader.getSprite(SpriteSet.Player, 1);
		if (referenceSprite != null) {
			referenceTile = referenceSprite.rasters.get(0).getFront().img;
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
	}

	@Override
	protected void update(double deltaTime)
	{
		/*
		final int guiSpriteID = spriteList.getSelected().id;
		if(spriteID != guiSpriteID)
		{
			setSprite(guiSpriteID, false);
		}
		 */

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

				handleInput(deltaTime);
				break;
		}
	}

	@Override
	public void glDraw()
	{
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

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

		final SpritePalette selectedPalette = (SpritePalette) paletteComboBox.getSelectedItem();
		if (selectedPalette != null && animOverridePalette != selectedPalette)
			setPalette(selectedPalette);

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
		Collection<SpriteMetadata> spriteMetas = SpriteLoader.getValidSprites(SpriteSet.Npc);
		if (spriteMetas.isEmpty())
			throw new StarRodException("No valid NPC sprites could be found!");

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
	}

	/**
	 * Initialize {@link playerSpriteList} from metadata provided by SpriteLoader
	 * and choose the initially selected sprite.
	 */
	private void loadPlayerSpriteList()
	{
		Collection<SpriteMetadata> spriteMetas = SpriteLoader.getValidSprites(SpriteSet.Player);
		if (spriteMetas.isEmpty())
			throw new StarRodException("No valid player sprites could be found!");

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
	}

	public boolean setSprite(int id, boolean forceReload)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (!forceReload && spriteID == id)
			return true;

		selectedImgAsset = null;
		highlightedImgAsset = null;

		if (sprite != null) {
			sprite.unloadTextures();
		}

		assetWatcher.release();

		Logger.logDetail("Set sprite: " + id);

		if (id < MIN_SPRITE_IDX) {
			sprite = null;
			setAnimation(null);
			editorModeTabs.setVisible(false);
			return true;
		}

		sprite = spriteLoader.getSprite(spriteSet, id, forceReload);

		if (sprite == null) {
			setAnimation(null);
			editorModeTabs.setVisible(false);
			return false;
		}

		if (!editorModeTabs.isVisible())
			editorModeTabs.setVisible(true);

		assetWatcher.acquire(sprite);

		spriteID = id;
		sprite.prepareForEditor();
		sprite.enableStencilBuffer = true;
		CommandAnimatorEditor.setModels(sprite);
		KeyframeAnimatorEditor.setModels(sprite);

		sprite.makeAtlas();
		sprite.centerAtlas(sheetCamera, glCanvasWidth(), glCanvasHeight());

		sprite.loadTextures();

		SwingUtilities.invokeLater(() -> {
			//spriteList.setSelectedId(id);
			rastersTab.setSpriteEDT(sprite);
			palettesTab.setSpriteEDT(sprite);
		});

		referenceMaxPos = 0;

		animList.setModel(sprite.animations);

		if (sprite.animations.size() > 0)
			animList.setSelectedIndex(0);

		if (sprite.palettes.size() > 0) {
			paletteComboBox.setModel(new ListAdapterComboboxModel<>(sprite.palettes));
			paletteComboBox.setSelectedIndex(0);
			paletteComboBox.setEnabled(true);
		}
		else {
			// this is not expected to be a valid state...
			animOverridePalette = null;
			paletteComboBox.setModel(new ListAdapterComboboxModel<>(new DefaultListModel<SpritePalette>()));
			paletteComboBox.setEnabled(false);
		}

		return true;
	}

	private boolean setAnimation(SpriteAnimation animation)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (currentAnim == animation)
			return true;

		String animName = (animation == null) ? "null" : "" + animation.getIndex();
		Logger.logDetail("Set animation: " + animName);

		// save current component selection for old animation
		if (currentAnim != null)
			currentAnim.lastSelectedComp = compList.getSelectedIndex();

		currentAnim = animation;

		if (currentAnim != null) {
			compList.setModel(currentAnim.components);
			compList.setEnabled(true);

			// restore component selection (if valid) for new animation
			if (currentAnim.lastSelectedComp >= 0 && currentAnim.lastSelectedComp < currentAnim.components.size())
				compList.setSelectedIndex(currentAnim.lastSelectedComp);
			else if (currentAnim.components.size() > 0)
				compList.setSelectedIndex(0);
		}
		else {
			compList.setModel(new DefaultListModel<>()); // empty model
			compList.setEnabled(false);
		}

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

		compList.setSelectedIndex(id);
	}

	private void setComponent(SpriteComponent component)
	{
		assert (!SwingUtilities.isEventDispatchThread());

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

			SwingUtilities.invokeLater(() -> {
				currentComp.bind(this, commandListPanel, commandEditPanel);

				compxSpinner.setValue(currentComp.posx);
				compySpinner.setValue(currentComp.posy);
				compzSpinner.setValue(currentComp.posz);

				componentPanel.repaint();
			});
		}
	}

	public ImgAsset getSelectedImage()
	{
		return selectedImgAsset;
	}

	private void setPalette(SpritePalette pm)
	{
		if (cbOverridePalette.isSelected())
			animOverridePalette = pm;
	}

	private void checkPalettesForChanges()
	{
		for (int i = 0; i < sprite.palettes.size(); i++) {
			SpritePalette sp = sprite.palettes.get(i);
			if (sp.dirty && sp.hasPal()) {
				sp.getPal().glReload();
				sp.dirty = false;
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
						if (currentAnim.components.size() > 1) {
							SwingUtilities.invokeLater(() -> {
								if (JOptionPane.YES_OPTION == super.getConfirmDialog("Confirm", "Delete component?").choose()) {
									invokeLater(() -> {
										currentAnim.components.removeElement(currentComp);
										currentAnim.parentSprite.recalculateIndices();
									});
								}
							});
						}
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

		SpritePalette override = useAtlasOverride ? palettesTab.getOverridePalette() : null;
		sprite.renderAtlas(selectedImgAsset, highlightedImgAsset, override, useFiltering);
	}

	private void renderAnim()
	{
		animCamera.glSetViewport(0, 0, glCanvasWidth(), glCanvasHeight());

		if (showBackground)
			animCamera.drawBackground();

		animCamera.setPerspView();
		animCamera.glLoadTransform();
		RenderState.setModelMatrix(null);

		drawAxes(1.0f);

		if (showGuide && referenceTile != null)
			renderReference();

		TransformMatrix viewMtx = animCamera.viewMatrix;
		if (flipHorizontal)
			viewMtx.scale(-1.0, 1.0, 1.0);
		RenderState.setViewMatrix(viewMtx);

		glEnable(GL_STENCIL_TEST);
		glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);

		if (currentAnim != null) {
			if (cbShowOnlySelectedComponent.isSelected() && currentComp != null)
				sprite.render(null, currentAnim, currentComp, animOverridePalette, useBackFacing, highlightComponent, false, useFiltering);
			else
				sprite.render(null, currentAnim, animOverridePalette, useBackFacing, highlightComponent, false, useFiltering);
		}

		glDisable(GL_STENCIL_TEST);
	}

	private void renderReference()
	{
		int xmax = sprite.aabb.max.getX();
		if (xmax > referenceMaxPos)
			referenceMaxPos = xmax;

		TransformMatrix mtx = RenderState.pushModelMatrix();
		mtx.translate(referenceMaxPos + 20, 0, 0);

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
		palettesTab = new PalettesTab();

		CommandAnimatorEditor.init();
		KeyframeAnimatorEditor.init();

		editorModeTabs = new JTabbedPane();
		editorModeTabs.addTab(EditorMode.Rasters.tabName, rastersTab);
		editorModeTabs.addTab(EditorMode.Palettes.tabName, palettesTab);
		editorModeTabs.addTab(EditorMode.Animation.tabName, getAnimationsTab());
		editorModeTabs.addChangeListener((e) -> {
			JTabbedPane sourceTabbedPane = (JTabbedPane) e.getSource();
			int index = sourceTabbedPane.getSelectedIndex();
			editorMode = EditorMode.values()[index];
		});

		JPanel playerSpriteListPanel = new JPanel(new MigLayout("fill, ins 0"));
		playerSpriteListPanel.add(playerSpriteList, "growy, pushy, gaptop 16, gapleft 8, gapright 8");

		JPanel npcSpriteListPanel = new JPanel(new MigLayout("fill, ins 0"));
		npcSpriteListPanel.add(npcSpriteList, "growy, pushy, gaptop 16, gapleft 8, gapright 8");

		JTabbedPane spriteSetTabs = new JTabbedPane();
		spriteSetTabs.addTab("NPC Sprites", npcSpriteListPanel);
		spriteSetTabs.addTab("Player Sprites", playerSpriteListPanel);
		spriteSetTabs.addChangeListener((e) -> {
			JTabbedPane sourceTabbedPane = (JTabbedPane) e.getSource();
			int index = sourceTabbedPane.getSelectedIndex();
			SpriteList sprites;

			if (index == 0) {
				spriteSet = SpriteSet.Npc;
				sprites = npcSpriteList;
			}
			else {
				spriteSet = SpriteSet.Player;
				sprites = playerSpriteList;
			}

			invokeLater(() -> {
				if (sprites.getSelected() == null)
					setSprite(-1, false);
				else
					setSprite(sprites.getSelected().id, false);
			});

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
			invokeLater(() -> {
				saveSprite();
			});
		});
		menu.add(item);

		item = new JMenuItem("Reload Current Sprite");
		item.addActionListener((e) -> {
			invokeLater(() -> {
				setSprite(spriteID, true);
			});
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
			invokeLater(() -> {
				if (sprite != null) {
					sprite.convertToKeyframes();
					setComponent(currentComp);
				}
			});
		});
		menu.add(item);

		item = new JMenuItem("Convert to Commands");
		item.addActionListener((e) -> {
			invokeLater(() -> {
				if (sprite != null) {
					sprite.convertToCommands();
					setComponent(currentComp);
				}
			});
		});
		menu.add(item);
	}

	private void addRenderingMenu(JMenuBar menuBar)
	{
		JMenu menu = new JMenu(MENU_BAR_SPACING + "Rendering" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

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

	protected void selectRasterEDT(SpriteRaster sr)
	{
		rastersTab.setRasterEDT(sr);
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

	private JPanel getComponentPanel()
	{
		compxSpinner = new JSpinner();
		SwingUtils.setFontSize(compxSpinner, 12);
		compxSpinner.setModel(new SpinnerNumberModel(0, -128, 128, 1));
		compxSpinner.addChangeListener((e) -> {
			if (currentComp != null)
				currentComp.posx = (int) compxSpinner.getValue();
		});
		SwingUtils.centerSpinnerText(compxSpinner);
		SwingUtils.addBorderPadding(compxSpinner);

		compySpinner = new JSpinner();
		SwingUtils.setFontSize(compySpinner, 12);
		compySpinner.setModel(new SpinnerNumberModel(0, -128, 128, 1));
		compySpinner.addChangeListener((e) -> {
			if (currentComp != null)
				currentComp.posy = (int) compySpinner.getValue();
		});
		SwingUtils.centerSpinnerText(compySpinner);
		SwingUtils.addBorderPadding(compySpinner);

		compzSpinner = new JSpinner();
		SwingUtils.setFontSize(compzSpinner, 12);
		compzSpinner.setModel(new SpinnerNumberModel(0, -32, 32, 1));
		compzSpinner.addChangeListener((e) -> {
			if (currentComp != null)
				currentComp.posz = (int) compzSpinner.getValue();
		});
		SwingUtils.centerSpinnerText(compzSpinner);
		SwingUtils.addBorderPadding(compzSpinner);

		JPanel relativePosPanel = new JPanel(new MigLayout("fill, ins 0, wrap", "[pref]8[sg spin]4[sg spin]4[sg spin]"));
		relativePosPanel.add(SwingUtils.getLabel("Pos offset", 12), "pushx");
		relativePosPanel.add(compxSpinner, "w 72!");
		relativePosPanel.add(compySpinner, "w 72!");
		relativePosPanel.add(compzSpinner, "w 72!");

		commandListPanel = new JPanel(new MigLayout("ins 0, fill"));
		commandEditPanel = new JPanel(new MigLayout("ins 0, fill"));

		componentPanel = new JPanel(new MigLayout("fill, ins 0, gapy 8", "[sg half]32[sg half]"));
		componentPanel.add(SwingUtils.getLabel("Component Properties", 14), "span, grow");

		componentPanel.add(relativePosPanel, "grow, wrap");
		componentPanel.add(commandListPanel, "grow, pushy");
		componentPanel.add(commandEditPanel, "grow, pushy");

		return componentPanel;
	}

	private void promptRenameAnim(SpriteAnimation anim)
	{
		String name = SwingUtils.getInputDialog()
			.setParent(getFrame())
			.setTitle("Set Animation Name")
			.setMessage("Choose a unique animation name")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.setDefault(anim.name)
			.prompt();

		if (name == null) {
			// prompt canceled
			return;
		}

		name = name.trim();

		if (name.isBlank() || name.equals(anim.name)) {
			// invalid name provided
			return;
		}

		boolean success = anim.assignUniqueName(name);
		if (!success) {
			Toolkit.getDefaultToolkit().beep();
		}
	}

	private void promptRenameComp(SpriteComponent comp)
	{
		String name = SwingUtils.getInputDialog()
			.setParent(getFrame())
			.setTitle("Set Component Name")
			.setMessage("Choose a unique component name")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.setDefault(comp.name)
			.prompt();

		if (name == null) {
			// prompt canceled
			return;
		}

		name = name.trim();

		if (name.isBlank() || name.equals(comp.name)) {
			// invalid name provided
			return;
		}

		boolean success = comp.assignUniqueName(name);
		if (!success) {
			Toolkit.getDefaultToolkit().beep();
		}
	}

	private void buildAnimationsList()
	{
		animList = new DragReorderList<>();
		animList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		animList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		animList.setCellRenderer(new SpriteAnimCellRenderer());

		animList.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			setAnimation(animList.getSelectedValue());
		});

		animList.addDropListener(() -> {
			sprite.recalculateIndices();
		});

		animList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				// double click to rename
				if (e.getClickCount() == 2) {
					int index = animList.locationToIndex(e.getPoint());
					if (index != -1) {
						SpriteAnimation anim = animList.getModel().getElementAt(index);
						promptRenameAnim(anim);
					}
				}
			}
		});

		animList.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e)
			{
				// rename with 'F2' key
				if (e.getKeyCode() == KeyEvent.VK_F2) {
					int index = animList.getSelectedIndex();
					if (index != -1) {
						SpriteAnimation anim = animList.getModel().getElementAt(index);
						promptRenameAnim(anim);
					}
				}
			}
		});

		InputMap im = animList.getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap am = animList.getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "duplicate");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");

		am.put("copy", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SpriteAnimation cur = animList.getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				animClipboard = cur.copy();
			}
		});

		am.put("paste", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = animList.getSelectedIndex();
				if (i == -1 || animClipboard == null || animClipboard.parentSprite != sprite) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (animList.getDefaultModel().size() >= Sprite.MAX_ANIMATIONS) {
					Logger.logError("Cannot have more than " + Sprite.MAX_ANIMATIONS + " animations!");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				SpriteAnimation copy = animClipboard.copy();
				if (copy.assignUniqueName(copy.name)) {
					animList.getDefaultModel().add(i + 1, copy);
					sprite.recalculateIndices();
				}
				else {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
				}
			}
		});

		am.put("duplicate", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SpriteAnimation cur = animList.getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (animList.getDefaultModel().size() >= Sprite.MAX_ANIMATIONS) {
					Logger.logError("Cannot have more than " + Sprite.MAX_ANIMATIONS + " animations!");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				int i = animList.getSelectedIndex();
				SpriteAnimation copy = cur.copy();

				if (copy.assignUniqueName(copy.name)) {
					animList.getDefaultModel().add(i + 1, copy);
					sprite.recalculateIndices();
				}
				else {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
				}
			}
		});

		am.put("delete", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = animList.getSelectedIndex();
				if (i == -1) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				animList.getDefaultModel().remove(i);
				sprite.recalculateIndices();
			}
		});
	}

	private void buildComponentsList()
	{
		compList = new DragReorderList<>();
		compList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		compList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		compList.setCellRenderer(new SpriteCompCellRenderer());

		// visibility toggles in the component list are not really functional, we implment them
		// via a mouse listener on the list and calculate whether mouse click locations overlap
		// with the open/closed eye icons
		compList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int index = compList.locationToIndex(e.getPoint());
				if (index != -1) {
					// double click to rename
					if (e.getClickCount() == 2) {
						SpriteComponent comp = compList.getModel().getElementAt(index);
						promptRenameComp(comp);
					}
					else {
						// test for clicking on visibility toggle icons
						Rectangle cellBounds = compList.getCellBounds(index, index);
						int min = cellBounds.x + 8;
						int max = min + EYE_ICON_WIDTH;
						if (cellBounds != null && e.getX() > min && e.getX() < max) {
							SpriteComponent comp = compList.getModel().getElementAt(index);
							comp.hidden = !comp.hidden;
							compList.repaint();
						}
					}
				}
			}
		});

		compList.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e)
			{
				int index = compList.getSelectedIndex();

				switch (e.getKeyCode()) {
					// rename with 'F2' key
					case KeyEvent.VK_F2:
						if (index != -1) {
							SpriteComponent comp = compList.getModel().getElementAt(index);
							promptRenameComp(comp);
						}
						break;
					// toggle visibility with 'H' key
					case KeyEvent.VK_H:
						if (index != -1) {
							SpriteComponent comp = compList.getModel().getElementAt(index);
							comp.hidden = !comp.hidden;
							compList.repaint();
						}
						break;
				}
			}
		});

		compList.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			setComponent(compList.getSelectedValue());
		});

		InputMap im = compList.getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap am = compList.getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "duplicate");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");

		am.put("copy", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SpriteComponent cur = compList.getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				compClipboard = cur.copy();
			}
		});

		am.put("paste", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = compList.getSelectedIndex();
				if (i == -1 || compClipboard == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (compClipboard.parentAnimation.parentSprite != currentAnim.parentSprite) {
					Logger.logError("Can't paste component " + compClipboard.name + " from sprite " + compClipboard.parentAnimation.parentSprite);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (compList.getDefaultModel().size() >= Sprite.MAX_COMPONENTS) {
					Logger.logError("Cannot have more than " + Sprite.MAX_COMPONENTS + " components!");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				SpriteComponent copy = new SpriteComponent(currentAnim, compClipboard);
				if (copy.assignUniqueName(copy.name)) {
					compList.getDefaultModel().add(i + 1, copy);
					currentAnim.parentSprite.recalculateIndices();
				}
				else {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
				}
			}
		});

		am.put("duplicate", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SpriteComponent cur = compList.getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (compList.getDefaultModel().size() >= Sprite.MAX_COMPONENTS) {
					Logger.logError("Cannot have more than " + Sprite.MAX_COMPONENTS + " components!");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				int i = compList.getSelectedIndex();
				SpriteComponent copy = cur.copy();

				if (copy.assignUniqueName(copy.name)) {
					compList.getDefaultModel().add(i + 1, copy);
					currentAnim.parentSprite.recalculateIndices();
				}
				else {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
				}
			}
		});

		am.put("delete", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = compList.getSelectedIndex();
				if (i == -1) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				compList.getDefaultModel().remove(i);
				currentAnim.parentSprite.recalculateIndices();
			}
		});
	}

	private JPanel getAnimationsTab()
	{
		buildAnimationsList();
		buildComponentsList();

		cbShowOnlySelectedComponent = new JCheckBox(" Draw current only");
		cbShowOnlySelectedComponent.setSelected(false);

		paletteComboBox = new JComboBox<>();
		SwingUtils.setFontSize(paletteComboBox, 14);
		paletteComboBox.setMaximumRowCount(24);
		paletteComboBox.setRenderer(new IndexableComboBoxRenderer());
		paletteComboBox.addActionListener((e) -> {
			if (cbOverridePalette.isSelected())
				animOverridePalette = (SpritePalette) paletteComboBox.getSelectedItem();
		});

		cbOverridePalette = new JCheckBox(" Override palette");
		cbOverridePalette.setSelected(false);
		cbOverridePalette.addActionListener((e) -> {
			if (cbOverridePalette.isSelected())
				animOverridePalette = (SpritePalette) paletteComboBox.getSelectedItem();
			else
				animOverridePalette = null;
		});

		JButton btnAddAnim = new JButton(ThemedIcon.ADD_16);
		btnAddAnim.setToolTipText("New Animation");
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
			anim.assignUniqueName(String.format("Anim_%X", sprite.animations.size()));
			sprite.animations.addElement(anim);
			sprite.recalculateIndices();

			animList.setSelectedValue(anim, true);
		});

		JButton btnAddComp = new JButton(ThemedIcon.ADD_16);
		btnAddComp.setToolTipText("New Component");
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
			comp.assignUniqueName(String.format("Comp_%X", currentAnim.components.size()));
			currentAnim.components.addElement(comp);
			sprite.recalculateIndices();

			compList.setSelectedValue(comp, true);
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

		listsPanel.add(animScrollPane, "growx, sg list");
		listsPanel.add(compScrollPane, "growx, sg list");

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

	private static class SpriteAnimCellRenderer extends JPanel implements ListCellRenderer<SpriteAnimation>
	{
		private JLabel nameLabel;
		private JLabel idLabel;

		public SpriteAnimCellRenderer()
		{
			idLabel = new JLabel();
			nameLabel = new JLabel();

			setLayout(new MigLayout("ins 0, fillx"));
			add(idLabel, "gapleft 16, w 32!");
			add(nameLabel, "growx, pushx, gapright push");

			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends SpriteAnimation> list,
			SpriteAnimation anim,
			int index,
			boolean isSelected,
			boolean cellHasFocus)
		{
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}

			setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			if (anim != null) {
				idLabel.setText(String.format("%02X", anim.getIndex()));
				nameLabel.setText(anim.name);
			}
			else {
				idLabel.setText("XXX");
				nameLabel.setText("error!");
			}

			return this;
		}
	}

	private static class SpriteCompCellRenderer extends JPanel implements ListCellRenderer<SpriteComponent>
	{
		private JLabel iconLabel;
		private JLabel nameLabel;

		public SpriteCompCellRenderer()
		{
			iconLabel = new JLabel();
			nameLabel = new JLabel();

			setLayout(new MigLayout("ins 0, fillx", "8[" + EYE_ICON_WIDTH + "]8[grow]"));
			add(iconLabel, "growx");
			add(nameLabel, "growx");

			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends SpriteComponent> list,
			SpriteComponent comp,
			int index,
			boolean isSelected,
			boolean cellHasFocus)
		{
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}

			setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			if (comp != null) {
				iconLabel.setIcon(comp.hidden ? ThemedIcon.VISIBILITY_OFF_16 : ThemedIcon.VISIBILITY_ON_16);
				nameLabel.setText(comp.name);
				nameLabel.setFont(getFont().deriveFont(comp.hidden ? Font.ITALIC : Font.PLAIN));
			}
			else {
				iconLabel.setIcon(null);
				nameLabel.setText("error!");
			}

			return this;
		}
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

	private void onRasterListChange()
	{
		assert (!SwingUtilities.isEventDispatchThread());
		sprite.recalculateIndices();
		sprite.glRefreshRasters();
		sprite.makeAtlas();

		CommandAnimatorEditor.setModels(sprite);
		KeyframeAnimatorEditor.setModels(sprite);

		//XXX
		for (int i = 0; i < sprite.animations.size(); i++) {
			SpriteAnimation anim = sprite.animations.get(i);
			anim.cleanDeletedRasters();
		}
	}

	private void onPaletteListChange()
	{
		assert (!SwingUtilities.isEventDispatchThread());
		sprite.recalculateIndices();
		sprite.glRefreshPalettes();

		CommandAnimatorEditor.setModels(sprite);
		KeyframeAnimatorEditor.setModels(sprite);

		//XXX
		for (int i = 0; i < sprite.animations.size(); i++) {
			SpriteAnimation anim = sprite.animations.get(i);
			anim.cleanDeletedPalettes();
		}
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

		sprite.recalculateIndices();
		sprite.saveChanges();
		assert (sprite.source.getParentFile().isDirectory());

		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			sprite.savePalettes();
			sprite.toXML(xmw);
			xmw.save();
			// force a reload
			setSprite(spriteID, true);
			Logger.log("Saved sprite to /" + xmlFile.getParentFile().getName() + "/");
		}
		catch (Throwable t) {
			Logger.logError("Failed to save sprite /" + xmlFile.getParentFile().getName() + "/");
			super.showStackTrace(t);
		}
	}

	@Override
	protected void saveChanges()
	{
		saveSprite();
		modified = false;
	}

	@Override
	public void clickLMB()
	{
		switch (editorMode) {
			case Rasters:
				ImgAsset picked = sprite.tryAtlasPick(trace);
				if (picked != null) {
					SwingUtilities.invokeLater(() -> {
						for (SpriteRaster sr : sprite.rasters) {
							if (sr.getFront() == picked || sr.getBack() == picked) {
								rastersTab.selectRaster(sr);
								break;
							}
						}
					});
				}
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
	public void clickRMB()
	{
		switch (editorMode) {
			case Rasters:
			case Palettes:
				selectedImgAsset = sprite.tryAtlasPick(trace);
				break;
			case Animation:
				break;
		}
	}

	public void resetAnimation()
	{
		playerEventQueue.add(PlayerEvent.Reset);
	}

	public void updatePlaybackStatus()
	{
		if (currentAnim == null) {
			playbackTime.setEnabled(false);
			playbackTime.setValue(0);
		}
		else {
			playbackTime.setEnabled(true);
			playbackTime.setValue(Math.max(currentAnim.animTime - 2, 0));
		}
	}

	public SpriteRaster promptForRaster(Sprite s)
	{
		RasterSelectDialog dialog = new RasterSelectDialog(s.rasters);
		showModalDialog(dialog, "Choose Raster");
		return dialog.getSelected();
	}
}
