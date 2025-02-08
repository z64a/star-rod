package game.sprite.editor;

import static app.Directories.FN_SPRITE_EDITOR_CONFIG;
import static org.lwjgl.opengl.GL11.*;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

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
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import app.Environment;
import app.IconResource;
import app.StarRodException;
import app.SwingUtils;
import app.config.Config;
import app.config.Options;
import app.config.Options.Scope;
import common.BaseEditor;
import common.BaseEditorSettings;
import common.KeyboardInput.KeyInputEvent;
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
import game.sprite.editor.dialogs.AnimationListEditPanel;
import game.sprite.editor.dialogs.ComponentListEditPanel;
import game.sprite.editor.dialogs.ListEditPanel;
import game.texture.Palette;
import game.texture.Tile;
import net.miginfocom.swing.MigLayout;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.SpriteShader;
import util.Logger;
import util.ui.ListAdapterComboboxModel;
import util.xml.XmlWrapper.XmlWriter;

public class SpriteEditor extends BaseEditor
{
	private static final String MENU_BAR_SPACING = "    ";

	private static final int DEFAULT_SIZE_X = 1280;
	private static final int DEFAULT_SIZE_Y = 800;
	private static final int RIGHT_PANEL_WIDTH = 640;
	private static final int LEFT_PANEL_WIDTH = 300;

	private static final int MIN_SPRITE_IDX = 1;

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

	private JComboBox<SpriteAnimation> animationComboBox;
	private JComboBox<SpritePalette> paletteComboBox;

	private JTabbedPane componentTabs;
	private ArrayList<JPanel> componentTabPool;
	// only create ONE component panel and pass it around as needed
	private JPanel componentPanel;

	private JCheckBox cbOverridePalette;
	private JCheckBox cbShowOnlySelectedComponent;

	private JCheckBox cbShowComponent;
	private JSpinner compxSpinner, compySpinner, compzSpinner;

	private volatile boolean ignoreComponentTabChanges = false;

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
		Reset, End, Play, Pause, Stop, PrevFrame, NextFrame
	}

	private volatile Queue<PlayerEvent> playerEventQueue = new LinkedList<>();
	private volatile boolean paused = false;
	private JLabel playbackStatusLabel;
	private JLabel playbackFrameLabel;
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

		modified = true; // actually tracking this will be difficult

		Logger.log("Loaded sprite editor.");
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

		final SpriteAnimation selectedAnimation = (SpriteAnimation) animationComboBox.getSelectedItem();
		if (currentAnim != selectedAnimation)
			setAnimation(selectedAnimation);

		/*
		// works, but adds noticeable stutter since tab doesn't get to repaint until following frame
		// using a changelistener on the tabs works better
		final int selectedTab = componentTabs.getSelectedIndex();
		if(componentID != selectedTab)
			setComponent(selectedTab, false);
		 */

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
					if (paused && currentComp != null && currentComp.frameCount < 32768) {
						int prevFrame = currentComp.frameCount - 2;
						currentAnim.reset();
						stepCurrentAnim();

						while (currentComp.frameCount < prevFrame)
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

		refreshAnimList();

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

	private void refreshAnimList()
	{
		if (sprite.animations.size() > 0) {
			setAnimation(sprite.animations.get(0));
			animationComboBox.setModel(new ListAdapterComboboxModel<>(sprite.animations));
			animationComboBox.setSelectedIndex(0);
			animationComboBox.setEnabled(true);
			componentTabs.setVisible(true);
		}
		else {
			setAnimation(null);
			animationComboBox.setModel(new ListAdapterComboboxModel<>(new DefaultListModel<SpriteAnimation>()));
			animationComboBox.setEnabled(false);
			componentTabs.setVisible(false);
		}
	}

	private boolean setAnimation(SpriteAnimation animation)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (currentAnim == animation)
			return true;

		String animName = (animation == null) ? "null" : "" + animation.getIndex();
		Logger.logDetail("Set animation: " + animName);

		currentAnim = animation;

		if (currentAnim != null)
			setComponent(0, false);

		playerEventQueue.add(PlayerEvent.Reset);
		return true;
	}

	public SpriteAnimation getAnimation()
	{
		return currentAnim;
	}

	private void setComponent(int id, boolean fromTabChange)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (id < 0 || currentAnim == null)
			return;

		assert (id >= 0) : id;

		Logger.logDetail("Set component: " + id);

		currentComp = currentAnim.components.get(id);
		currentAnim.setComponentSelected(id);

		SwingUtilities.invokeLater(() -> {

			currentComp.bind(this, commandListPanel, commandEditPanel);

			compxSpinner.setValue(currentComp.posx);
			compySpinner.setValue(currentComp.posy);
			compzSpinner.setValue(currentComp.posz);

			cbShowComponent.setSelected(currentComp.hidden);

			if (!fromTabChange) {
				ignoreComponentTabChanges = true;
				populateComponentTabs();
				componentTabs.setSelectedIndex(id);
				ignoreComponentTabChanges = false;
			}

			refreshTab();
		});
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

		if (key.code == KeyEvent.VK_DELETE && !ctrl && !alt && !shift) {
			switch (editorMode) {
				case Animation:
					if (sprite != null && currentAnim != null && currentComp != null && currentComp.selected) {
						if (currentAnim.components.size() > 1) {
							SwingUtilities.invokeLater(() -> {
								if (JOptionPane.YES_OPTION == super.getConfirmDialog("Confirm", "Delete component?").choose()) {
									invokeLater(() -> {
										currentAnim.components.removeElement(currentComp);
										sprite.recalculateIndices();
										setComponent(0, false);
									});
								}
							});
						}
					}
					break;
				case Palettes:
				case Rasters:
					break;
				default:
					throw new IllegalStateException("Invalid editor mode in handleInput().");
			}
		}
		else if (key.code == KeyEvent.VK_H && !ctrl && !alt && !shift) {
			cbShowComponent.doClick();
		}
		else {
			SwingUtilities.invokeLater(() -> {
				handleKey(ctrl, alt, shift, key.code);
			});
		}
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
			//	boolean showHighlight = mouse.hasLocation() || highlightComponent;

			if (cbShowOnlySelectedComponent.isSelected())
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

		editorModeTabs = new JTabbedPane();
		editorModeTabs.addTab(EditorMode.Rasters.tabName, rastersTab);
		editorModeTabs.addTab(EditorMode.Palettes.tabName, palettesTab);
		editorModeTabs.addTab(EditorMode.Animation.tabName, getAnimationsTab());
		editorModeTabs.addChangeListener((e) -> {
			JTabbedPane sourceTabbedPane = (JTabbedPane) e.getSource();
			int index = sourceTabbedPane.getSelectedIndex();
			editorMode = EditorMode.values()[index];
		});

		JPanel rightSide = new JPanel(new MigLayout("fill, ins 0"));
		rightSide.add(editorModeTabs, "grow, push, span, wrap");

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

		//	JScrollPane scrollPane = new JScrollPane(side);
		//	scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		//	scrollPane.setBorder(null);

		toolPanel.add(spriteSetTabs, "grow, w " + LEFT_PANEL_WIDTH + "!");
		toolPanel.add(glCanvas, "grow, push");
		toolPanel.add(rightSide, "hidemode 3, gapleft 8, grow, wrap, w " + RIGHT_PANEL_WIDTH + "!");

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
					setComponent(currentComp.getIndex(), false);
				}
			});
		});
		menu.add(item);

		item = new JMenuItem("Convert to Commands");
		item.addActionListener((e) -> {
			invokeLater(() -> {
				if (sprite != null) {
					sprite.convertToCommands();
					setComponent(currentComp.getIndex(), false);
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

	private JPanel getControlPanel()
	{
		final Insets buttonInsets = new Insets(0, 2, 0, 2);

		resetButton = new JButton(IconResource.ICON_START);
		resetButton.setToolTipText("Reset");
		resetButton.setMargin(buttonInsets);
		resetButton.addActionListener((e) -> {
			playerEventQueue.add(PlayerEvent.Reset);
		});

		endButton = new JButton(IconResource.ICON_END);
		endButton.setToolTipText("Goto End");
		endButton.setMargin(buttonInsets);
		endButton.addActionListener((e) -> {
			playerEventQueue.add(PlayerEvent.End);
		});

		prevFrameButton = new JButton(IconResource.ICON_PREV);
		prevFrameButton.setToolTipText("Step Back");
		prevFrameButton.setMargin(buttonInsets);
		prevFrameButton.addActionListener((e) -> {
			playerEventQueue.add(PlayerEvent.PrevFrame);
		});
		prevFrameButton.setEnabled(false);

		nextFrameButton = new JButton(IconResource.ICON_NEXT);
		nextFrameButton.setToolTipText("Step Forward");
		nextFrameButton.setMargin(buttonInsets);
		nextFrameButton.addActionListener((e) -> {
			playerEventQueue.add(PlayerEvent.NextFrame);
		});
		nextFrameButton.setEnabled(false);

		playButton = new JButton(IconResource.ICON_PAUSE);

		stopButton = new JButton(IconResource.ICON_STOP);
		stopButton.setToolTipText("Stop");
		stopButton.setMargin(buttonInsets);
		stopButton.addActionListener((e) -> {
			playerEventQueue.add(PlayerEvent.Stop);

			// stop playing, button is now 'play'
			playerEventQueue.add(PlayerEvent.Stop);
			playButton.setIcon(IconResource.ICON_PLAY);
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
				playButton.setIcon(IconResource.ICON_PAUSE);
				playButton.setToolTipText("Pause");
				nextFrameButton.setEnabled(false);
				prevFrameButton.setEnabled(false);
				stopButton.setEnabled(true);
			}
			else {
				// pause playing, button is now 'play'
				playerEventQueue.add(PlayerEvent.Pause);
				playButton.setIcon(IconResource.ICON_PLAY);
				playButton.setToolTipText("Play");
				nextFrameButton.setEnabled(true);
				prevFrameButton.setEnabled(true);
				stopButton.setEnabled(false);
			}
			super.revalidateFrame();
		});

		String fmt = "sg button, pushx, growx, h 20!";
		JPanel controlPanel = new JPanel(new MigLayout("fill, ins 0", "[fill]0"));
		controlPanel.add(resetButton, fmt);
		controlPanel.add(prevFrameButton, fmt);
		controlPanel.add(stopButton, fmt);
		controlPanel.add(playButton, fmt);
		controlPanel.add(nextFrameButton, fmt + ", wrap");

		JComboBox<String> playbackRateBox = new JComboBox<>(new String[] { "Normal", "1 / 2", "1 / 4", "1 / 8", "1 / 16", "1 / 32", "1 / 256" });
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
				case 6: playbackRate = 256; break;
				// @formatter:on
			}
			playbackCounter = playbackRate;
		});

		playbackStatusLabel = SwingUtils.getCenteredLabel("", 12);
		controlPanel.add(playbackStatusLabel, "growx, span 3, h 32!");
		controlPanel.add(playbackRateBox, "span 2, growx");

		return controlPanel;
	}

	private JPanel getAnimationsTab()
	{
		JLabel defaultPaletteLabel = new JLabel("Palette");
		SwingUtils.setFontSize(defaultPaletteLabel, 12);

		paletteComboBox = new JComboBox<>();
		SwingUtils.setFontSize(paletteComboBox, 14);
		paletteComboBox.setMaximumRowCount(24);
		paletteComboBox.setRenderer(new IndexableComboBoxRenderer());
		paletteComboBox.addActionListener((e) -> {
			if (cbOverridePalette.isSelected())
				animOverridePalette = (SpritePalette) paletteComboBox.getSelectedItem();
		});

		animationComboBox = new JComboBox<>();
		SwingUtils.setFontSize(animationComboBox, 14);
		animationComboBox.setMaximumRowCount(24);
		animationComboBox.setRenderer(new IndexableComboBoxRenderer());

		compxSpinner = new JSpinner();
		SwingUtils.setFontSize(compxSpinner, 12);
		compxSpinner.setModel(new SpinnerNumberModel(0, -128, 128, 1));
		compxSpinner.addChangeListener((e) -> {
			if (currentComp != null)
				currentComp.posx = (int) compxSpinner.getValue();
		});
		SwingUtils.centerSpinnerText(compxSpinner);

		compySpinner = new JSpinner();
		SwingUtils.setFontSize(compySpinner, 12);
		compySpinner.setModel(new SpinnerNumberModel(0, -128, 128, 1));
		compySpinner.addChangeListener((e) -> {
			if (currentComp != null)
				currentComp.posy = (int) compySpinner.getValue();
		});
		SwingUtils.centerSpinnerText(compySpinner);

		compzSpinner = new JSpinner();
		SwingUtils.setFontSize(compzSpinner, 12);
		compzSpinner.setModel(new SpinnerNumberModel(0, -32, 32, 1));
		compzSpinner.addChangeListener((e) -> {
			if (currentComp != null)
				currentComp.posz = (int) compzSpinner.getValue();
		});
		SwingUtils.centerSpinnerText(compzSpinner);

		cbShowComponent = new JCheckBox(" Hide component");
		cbShowComponent.addActionListener((e) -> {
			if (currentComp != null) {
				currentComp.hidden = cbShowComponent.isSelected();
				updateComponentTabNames();
			}
		});

		cbShowOnlySelectedComponent = new JCheckBox(" Draw current only");
		cbShowOnlySelectedComponent.setSelected(false);
		cbShowOnlySelectedComponent.setToolTipText("Only render the currently selected component in the preview window.");

		cbOverridePalette = new JCheckBox(" Override default pal");
		cbOverridePalette.setSelected(false);
		cbOverridePalette.addActionListener((e) -> {
			if (cbOverridePalette.isSelected())
				animOverridePalette = (SpritePalette) paletteComboBox.getSelectedItem();
			else
				animOverridePalette = null;
		});

		JButton editAnimationsButton = new JButton("Edit Animation List");
		editAnimationsButton.addActionListener((e) -> {
			if (sprite == null)
				return;
			showAnimationsEditorWindow();
		});

		JButton addComponentButton = new JButton("Edit Components");
		addComponentButton.addActionListener((e) -> {
			if (sprite == null || currentAnim == null)
				return;
			showComponentsEditorWindow();
		});

		JPanel relativePosPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));
		relativePosPanel.add(SwingUtils.getLabel("Relative Component Position:", SwingConstants.RIGHT, 12));
		relativePosPanel.add(compxSpinner, "h 20!, w 72!, sg spin, split 3, gaptop 4");
		relativePosPanel.add(compySpinner, "w 72!, sg spin");
		relativePosPanel.add(compzSpinner, "w 72!, sg spin, wrap");

		relativePosPanel.add(cbShowComponent, "h 32!, gapbottom push");

		playbackFrameLabel = SwingUtils.getLabel("", SwingConstants.RIGHT, 12);

		JPanel playbackPanel = new JPanel(new MigLayout("fill, ins 0"));
		playbackPanel.add(SwingUtils.getLabel("Playback Controls:", SwingConstants.RIGHT, 12));
		playbackPanel.add(playbackFrameLabel, "growx, w 40::, wrap");
		playbackPanel.add(getControlPanel(), "pushx, growx, span");

		commandListPanel = new JPanel(new MigLayout("ins 0, fill"));
		commandEditPanel = new JPanel(new MigLayout("ins 0, fill"));

		componentPanel = new JPanel(new MigLayout("fill, ins 16", "[]32[grow]"));
		componentPanel.add(relativePosPanel, "grow");
		componentPanel.add(playbackPanel, "grow, wrap");
		componentPanel.add(commandListPanel, "gaptop 16, grow, pushy");
		componentPanel.add(commandEditPanel, "gaptop 16, grow, push");

		componentTabPool = new ArrayList<>();

		componentTabs = new JTabbedPane();
		componentTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

		componentTabs.addChangeListener((e) -> {
			if (ignoreComponentTabChanges)
				return;

			final int id = componentTabs.getSelectedIndex();
			if (id >= 0) {
				invokeLater(() -> {
					setComponent(id, true);
				});
			}
		});

		componentTabs.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt)
			{
				if (evt.getClickCount() == 3) {
					int tabIndex = componentTabs.indexAtLocation(evt.getX(), evt.getY());
					if (tabIndex < 0)
						return;

					incrementDialogsOpen();
					String input = JOptionPane.showInputDialog(
						componentTabs,
						"Enter a new name", "Rename Component",
						JOptionPane.PLAIN_MESSAGE);
					decrementDialogsOpen();

					if (input == null)
						return;

					String newName = input.trim();

					if (newName.isEmpty() || currentAnim == null)
						return;

					SpriteComponent comp = currentAnim.components.get(tabIndex);
					comp.name = newName;
					componentTabs.setTitleAt(tabIndex, String.format("%-8s", newName));
				}
			}
		});

		JPanel animationsTab = new JPanel(new MigLayout("fill, wrap, ins 16"));

		animationsTab.add(SwingUtils.getLabel("Animation: ", SwingConstants.RIGHT, 12), "sg etc, split 4");
		animationsTab.add(animationComboBox, "w 200!");
		animationsTab.add(editAnimationsButton, "gapleft 8, sg but, grow");
		animationsTab.add(addComponentButton, "sg but, grow");

		animationsTab.add(SwingUtils.getLabel("Palette: ", SwingConstants.RIGHT, 12), "sg etc, split 4");
		animationsTab.add(paletteComboBox, "w 200!");
		animationsTab.add(cbOverridePalette, "gapleft 8, sg but, grow");
		animationsTab.add(cbShowOnlySelectedComponent, "sg but, grow");
		animationsTab.add(componentTabs, "grow, push, gaptop 8");

		return animationsTab;
	}

	private void refreshTab()
	{
		assert (SwingUtilities.isEventDispatchThread());

		JPanel tabPanel = (JPanel) componentTabs.getSelectedComponent();
		if (tabPanel != null) {
			tabPanel.add(componentPanel, "grow");
			tabPanel.repaint();
		}
		else
			Logger.logDetail("null tabPanel!");
	}

	private void showAnimationsEditorWindow()
	{
		if (sprite == null)
			return;

		ListEditPanel<SpriteAnimation> listPanel = new AnimationListEditPanel(sprite, sprite.animations, this);
		getOptionDialog("Edit " + sprite + " Animations", listPanel).setOptions("Done").choose();

		invokeLater(() -> {
			sprite.recalculateIndices();
			if (currentAnim != null) {
				if (sprite.animations.contains(currentAnim))
					animationComboBox.setSelectedItem(currentAnim);
				else if (!sprite.animations.isEmpty())
					animationComboBox.setSelectedIndex(0);
				else
					animationComboBox.setSelectedItem(null);
			}
			animationComboBox.repaint();
		});
	}

	private void showComponentsEditorWindow()
	{
		if (sprite == null || currentAnim == null)
			return;

		ListEditPanel<SpriteComponent> listPanel = new ComponentListEditPanel(currentAnim, currentAnim.components);
		getOptionDialog("Edit " + currentAnim + " Components", listPanel).setOptions("Done").choose();

		invokeLater(() -> {
			sprite.recalculateIndices();
			if (currentComp != null || currentAnim != null) {
				if (currentAnim.components.contains(currentComp))
					setComponent(currentComp.listIndex, false);
				else if (!currentAnim.components.isEmpty())
					setComponent(0, false);
				else
					currentComp = null;
			}
		});
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

	// resets the tabs on the component tabbed pane and selects the first one
	private void populateComponentTabs()
	{
		assert (SwingUtilities.isEventDispatchThread());

		componentTabs.removeAll();

		if (currentAnim == null)
			return;

		for (int i = 0; i < currentAnim.components.size(); i++) {
			if (i >= componentTabPool.size()) {
				componentTabPool.add(new JPanel(new MigLayout("fill, ins 0")));
				Logger.logDetail("Tab pool size increased to " + componentTabPool.size());
			}

			SpriteComponent sc = currentAnim.components.getElementAt(i);

			String tabName = sc.name.isEmpty() ? String.format("%02X", sc.getIndex()) : sc.name;
			if (sc.hidden)
				tabName = "<html><i>" + tabName;
			componentTabs.add(componentTabPool.get(i), String.format("%-8s", tabName));
		}
	}

	private void updateComponentTabNames()
	{
		for (int i = 0; i < currentAnim.components.size(); i++) {
			SpriteComponent sc = currentAnim.components.getElementAt(i);

			String tabName = sc.name.isEmpty() ? String.format("%02X", sc.getIndex()) : sc.name;
			if (sc.hidden)
				tabName = "<html><i>" + tabName;

			componentTabs.setTitleAt(i, String.format("%-8s", tabName));
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
					setComponent(selected, false);
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
		//	assert(currentComp.frameCount >= 2);
		playbackStatusLabel.setText(paused ? "(PAUSED) " : "");
		playbackFrameLabel.setText(currentComp != null ? Math.max(currentComp.frameCount - 2, 0) + "" : "");
	}
}
