package game.worldmap;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.KeyStroke;

import app.Environment;
import app.SwingUtils;
import assets.ExpectedAsset;
import game.ProjectDatabase;
import game.map.editor.common.BaseEditor;
import game.map.editor.common.BaseEditorSettings;
import game.map.editor.common.BasicCamera;
import game.map.editor.common.BasicCommandManager;
import game.map.editor.common.BasicEditorCommand;
import game.map.editor.common.KeyboardInput.KeyInputEvent;
import game.map.editor.common.MouseInput.MouseManagerListener;
import game.map.editor.common.MousePixelRead;
import game.map.editor.render.PresetColor;
import game.map.editor.render.TextureManager;
import game.worldmap.WorldMapModder.WorldLocation;
import game.worldmap.WorldMapModder.WorldMarker;
import game.worldmap.WorldMapModder.WorldPathElement;
import net.miginfocom.swing.MigLayout;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicTexturedShader;
import renderer.shaders.scene.WSPointShader;
import util.Logger;
import util.MathUtil;

public class WorldMapEditor extends BaseEditor implements MouseManagerListener
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		ProjectDatabase.initialize();

		BaseEditor editor = new WorldMapEditor();
		editor.launch();
		Environment.exit();
	}

	private static final BaseEditorSettings EDITOR_SETTINGS = BaseEditorSettings.create()
		.setTitle(Environment.decorateTitle("World Map Editor"))
		.setIcon(Environment.getDefaultIconImage())
		.setLog("world_map_editor.log")
		.setFullscreen(true)
		.setResizeable(true)
		.hasMenuBar(true)
		.setSize(1080, 720)
		.setFramerate(60);

	private static final int MAX_SIZE = 320;

	private List<WorldLocation> locations;

	private JCheckBox cbMoveTogether;
	private JCheckBox cbUseOriginals;
	private JSlider saturationSlider;
	private JComboBox<String> locationsBox;
	private JComboBox<String> storyBox;

	private BasicCommandManager commandManager;
	private final BasicCamera cam;

	private boolean glTexDirty = true;
	private int glBackgroundTexID;
	private int glLocationMarkerTexID;
	private int glPathMarkerTexID;

	private boolean bDrawBackground = true;
	private boolean bDrawGrid = true;
	private boolean bDrawLines = true;
	private boolean bDrawIcons = true;

	private JCheckBoxMenuItem cbBackground;
	private JCheckBoxMenuItem cbGrid;
	private JCheckBoxMenuItem cbLines;
	private JCheckBoxMenuItem cbIcons;

	private MousePixelRead framePick;
	private WorldMarker mouseMarker;
	private WorldMarker dragMarker;
	private WorldMarker parentingMarker;
	private WorldLocation selectedLocation;

	private JPanel sidePanel;
	private JPanel selectedPanel;

	public WorldMapEditor()
	{
		super(EDITOR_SETTINGS);

		cam = new BasicCamera(
			0.0f, 0.0f, 0.5f,
			0.08f, 0.0125f, 1.0f,
			true, true);

		resetEditor();
		loadData();
	}

	private void resetEditor()
	{
		commandManager = new BasicCommandManager(32);

		cbBackground.setSelected(bDrawBackground);
		cbLines.setSelected(bDrawLines);
		cbIcons.setSelected(bDrawIcons);
		cbUseOriginals.setSelected(false);

		saturationSlider.setValue(255);

		mouseMarker = null;
		dragMarker = null;
		setSelectedLocation(null);

		resetCam();
	}

	public void push(BasicEditorCommand cmd)
	{
		commandManager.pushCommand(cmd);
		modified = true;
	}

	@Override
	protected void createGui(JPanel toolPanel, Canvas glCanvas, JMenuBar menubar, JLabel infoLabel,
		ActionListener openLogAction)
	{
		cbMoveTogether = new JCheckBox("Move path when parent moves");
		cbMoveTogether.setIconTextGap(16);

		cbUseOriginals = new JCheckBox("Use original icon textures");
		cbUseOriginals.setIconTextGap(16);

		saturationSlider = new JSlider(0, 255, 0);
		saturationSlider.setMajorTickSpacing(16);
		saturationSlider.setPaintTicks(true);

		String[] locNames = ProjectDatabase.ELocations.getValues();
		locationsBox = new JComboBox<>(WorldMapModder.stripAllPrefix(locNames, WorldMapModder.PREFIX_LOC));
		locationsBox.addActionListener((e) -> {
			if (selectedLocation != null)
				selectedLocation.name = (String) locationsBox.getSelectedItem();
		});
		locationsBox.setMaximumRowCount(20);
		SwingUtils.addBorderPadding(locationsBox);

		String[] storyNames = ProjectDatabase.EStoryProgress.getValues();
		storyBox = new JComboBox<>(WorldMapModder.stripAllPrefix(storyNames, WorldMapModder.PREFIX_STORY));
		storyBox.addActionListener((e) -> {
			if (selectedLocation != null)
				selectedLocation.descUpdate = (String) storyBox.getSelectedItem();
		});
		storyBox.setMaximumRowCount(20);
		SwingUtils.addBorderPadding(storyBox);

		selectedPanel = new JPanel(new MigLayout("ins 0, fill, wrap"));
		selectedPanel.add(SwingUtils.getLabel("Selected Location", 14), "growx");
		selectedPanel.add(locationsBox, "w 240!");

		selectedPanel.add(SwingUtils.getLabel("Story Update", 14), "growx");
		selectedPanel.add(storyBox, "w 240!");
		selectedPanel.add(new JPanel(), "pushy");

		sidePanel = new JPanel(new MigLayout("w 320!, ins 8, wrap, hidemode 3"));
		sidePanel.add(SwingUtils.getLabel("Options", 14), "gaptop 8");
		sidePanel.add(cbMoveTogether, "grow");
		//	sidePanel.add(cbUseOriginals, "grow");

		sidePanel.add(SwingUtils.getLabel("Background Intensity", 14), "gaptop 24");
		sidePanel.add(saturationSlider, "w 240!");

		sidePanel.add(selectedPanel, "grow, push, gaptop 24");

		toolPanel.setLayout(new MigLayout("fill, ins 0, hidemode 2"));
		toolPanel.add(glCanvas, "grow, push");
		toolPanel.add(sidePanel, "growy, wrap");
		toolPanel.add(infoLabel, "h 16!, gapleft 4, gapbottom 4, span");
		addOptionsMenu(menubar, openLogAction);
		addEditorMenu(menubar);
		addViewMenu(menubar);
	}

	@Override
	public void glInit()
	{
		TextureManager.bindEditorTextures();
	}

	private void addOptionsMenu(JMenuBar menuBar, ActionListener openLogAction)
	{
		JMenuItem item;

		JMenu menu = new JMenu(String.format("  %-10s", "File"));
		menu.setPreferredSize(new Dimension(60, 20));
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Reload");
		item.addActionListener((e) -> {
			loadData();
			glTexDirty = true;
		});
		menu.add(item);

		item = new JMenuItem("Save");
		item.addActionListener((e) -> {
			saveChanges();
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Open Log");
		item.addActionListener(openLogAction);
		menu.add(item);

		menu.addSeparator();

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

	private void addEditorMenu(JMenuBar menuBar)
	{
		JMenuItem item;

		JMenu menu = new JMenu(String.format("  %-10s", "Editor"));
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("View Controls");
		item.addActionListener((e) -> {
			showControls();
		});
		menu.add(item);
	}

	private void addViewMenu(JMenuBar menuBar)
	{
		KeyStroke dummyKeyStroke;

		JMenu menu = new JMenu(String.format("  %-10s", "  View"));
		menu.setPreferredSize(new Dimension(60, 20));
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		cbBackground = new JCheckBoxMenuItem("Background");
		cbBackground.addActionListener((e) -> {
			invokeLater(() -> {
				bDrawBackground = cbBackground.isSelected();
			});
		});
		dummyKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_B, 0);
		cbBackground.setAccelerator(dummyKeyStroke);
		cbBackground.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(dummyKeyStroke, "none");
		menu.add(cbBackground);

		cbLines = new JCheckBoxMenuItem("Lines");
		cbLines.addActionListener((e) -> {
			invokeLater(() -> {
				bDrawLines = cbLines.isSelected();
			});
		});
		dummyKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_N, 0);
		cbLines.setAccelerator(dummyKeyStroke);
		cbLines.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(dummyKeyStroke, "none");
		menu.add(cbLines);

		cbIcons = new JCheckBoxMenuItem("Icons");
		cbIcons.addActionListener((e) -> {
			invokeLater(() -> {
				bDrawIcons = cbIcons.isSelected();
			});
		});
		dummyKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_M, 0);
		cbIcons.setAccelerator(dummyKeyStroke);
		cbIcons.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(dummyKeyStroke, "none");
		menu.add(cbIcons);

		cbGrid = new JCheckBoxMenuItem("Grid");
		cbGrid.addActionListener((e) -> {
			invokeLater(() -> {
				bDrawGrid = cbGrid.isSelected();
			});
		});
		dummyKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_G, 0);
		cbGrid.setAccelerator(dummyKeyStroke);
		cbGrid.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(dummyKeyStroke, "none");
		menu.add(cbGrid);
	}

	@Override
	protected void update(double deltaTime)
	{
		framePick = cam.getMousePosition(mouse.getPosX(), mouse.getPosY(), false, false);

		colorLerpAlpha = 0.5f * (float) Math.sin(omega * getTime());
		colorLerpAlpha = 0.5f + colorLerpAlpha * colorLerpAlpha; // more pleasing

		if (framePick != null) {
			float posX = framePick.worldPos.x;
			float posY = framePick.worldPos.y;
			float sizeScale = MathUtil.lerp(cam.getDist(), 50f, 300f, 1.0f, 1.5f);

			double closestDist = Double.MAX_VALUE;
			mouseMarker = null;

			for (WorldLocation loc : locations) {
				for (WorldPathElement step : loc.path) {
					step.mouseOver = false;
					double dist = step.getDistTo(posX, posY);
					if (dist < sizeScale * 3.0f && dist < closestDist) {
						mouseMarker = step;
						closestDist = dist;
					}
				}

				loc.mouseOver = false;
				double dist = loc.getDistTo(posX, posY);
				if (dist < sizeScale * 5.0f && dist < closestDist) {
					mouseMarker = loc;
					closestDist = dist;
				}
			}

			if (mouseMarker != null)
				mouseMarker.mouseOver = true;
		}

		cam.handleInput(mouse, keyboard, deltaTime, glCanvasWidth(), glCanvasHeight());
	}

	@Override
	public void keyPress(KeyInputEvent key)
	{
		boolean ctrl = keyboard.isCtrlDown();
		boolean shift = keyboard.isShiftDown();
		boolean alt = keyboard.isAltDown();

		switch (key.code) {
			case KeyEvent.VK_SPACE:
				if (!shift && !ctrl && !alt)
					resetCam();
				break;
			case KeyEvent.VK_Z:
				if (!shift && ctrl && !alt)
					commandManager.undo();
				break;
			case KeyEvent.VK_Y:
				if (!shift && ctrl && !alt)
					commandManager.redo();
				break;
			case KeyEvent.VK_G:
				if (!shift && !ctrl && !alt) {
					bDrawGrid = !bDrawGrid;
					cbGrid.setSelected(bDrawGrid);
				}
				break;
			case KeyEvent.VK_N:
				if (!shift && !ctrl && !alt) {
					bDrawLines = !bDrawLines;
					cbLines.setSelected(bDrawLines);
				}
				break;
			case KeyEvent.VK_M:
				if (!shift && !ctrl && !alt) {
					bDrawIcons = !bDrawIcons;
					cbIcons.setSelected(bDrawIcons);
				}
				break;
			case KeyEvent.VK_B:
				if (!shift && !ctrl && !alt) {
					bDrawBackground = !bDrawBackground;
					cbBackground.setSelected(bDrawBackground);
				}
				break;
			default:
		}
	}

	@Override
	protected void undoEDT()
	{
		invokeLater(() -> {
			commandManager.undo();
		});
	}

	@Override
	protected void redoEDT()
	{
		invokeLater(() -> {
			commandManager.redo();
		});
	}

	private void resetCam()
	{
		int width = MAX_SIZE;
		int height = MAX_SIZE;

		cam.centerOn(glCanvasWidth(), glCanvasHeight(),
			width / 2, height / 2, 0,
			width, height, 0);

		cam.setMinPos(0, 0);
		cam.setMaxPos(width, height);
	}

	private static double omega = 1.2 * Math.PI;
	private float colorLerpAlpha = 0.0f;

	private float interpColor(float min, float max)
	{
		return min + colorLerpAlpha * (max - min);
	}

	@Override
	public void glDraw()
	{
		if (glTexDirty) {
			glBackgroundTexID = TextureManager.loadTexture(ExpectedAsset.WORLD_MAP_BG.getFile());
			//	glLocationMarkerTexID = TextureManager.loadTexture(ExpectedAsset.WORLD_MAP_LOC.getFile());
			//	glPathMarkerTexID = TextureManager.loadTexture(ExpectedAsset.WORLD_MAP_PATH.getFile());
			glTexDirty = false;
		}

		cam.glSetViewport(0, 0, glCanvasWidth(), glCanvasHeight());

		glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

		RenderState.setDepthWrite(false);
		RenderState.setPolygonMode(PolygonMode.FILL);

		cam.setOrthoView();
		cam.glLoadTransform();
		RenderState.setModelMatrix(null);

		if (bDrawBackground) {
			BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
			shader.enableFiltering.set(true);
			shader.multiplyBaseColor.set(true);
			shader.baseColor.set(1.0f, 1.0f, 1.0f, saturationSlider.getValue() / 255.0f);
			shader.saturation.set(saturationSlider.getValue() / 255.0f);
			drawImage(shader, glBackgroundTexID, 0, 0, MAX_SIZE, MAX_SIZE);
			RenderState.setModelMatrix(null);
		}

		if (bDrawGrid)
			drawGrid(1.0f);

		//	drawAxes(2.0f);

		if (bDrawLines) {
			RenderState.setLineWidth(2.0f);

			// parenting lines
			RenderState.setColor(PresetColor.TEAL);
			for (WorldLocation loc : locations) {
				if (loc == parentingMarker) {
					if (framePick != null) {
						LineRenderQueue.addLine(
							LineRenderQueue.addVertex().setPosition(loc.getX(), loc.getY(), 0).getIndex(),
							LineRenderQueue.addVertex().setPosition(framePick.worldPos.x, framePick.worldPos.y, 0)
								.getIndex());
					}
				}
				else {
					if (loc.parent != null) {
						LineRenderQueue.addLine(
							LineRenderQueue.addVertex().setPosition(loc.parent.getX(), loc.parent.getY(), 0)
								.getIndex(),
							LineRenderQueue.addVertex().setPosition(loc.getX(), loc.getY(), 0).getIndex());
					}
				}
			}

			// path lines
			RenderState.setColor(PresetColor.YELLOW);
			for (WorldLocation loc : locations) {
				int lastX = loc.getX();
				int lastY = loc.getY();
				for (WorldPathElement marker : loc.path) {
					LineRenderQueue.addLine(
						LineRenderQueue.addVertex().setPosition(lastX, lastY, 0).getIndex(),
						LineRenderQueue.addVertex().setPosition(marker.getX(), marker.getY(), 0).getIndex());
					lastX = marker.getX();
					lastY = marker.getY();
				}
			}

			LineRenderQueue.render(true);
		}

		if (bDrawIcons) {
			if (cbUseOriginals.isSelected()) {
				BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
				shader.enableFiltering.set(true);
				shader.multiplyBaseColor.set(true);

				for (WorldLocation loc : locations) {
					if ((loc.mouseOver && dragMarker == null) || loc == dragMarker)
						shader.baseColor.set(1f, 0f, 1f, 1f);
					else if (loc == selectedLocation)
						shader.baseColor.set(1f, interpColor(0.2f, 0.8f), interpColor(0.2f, 0.8f), 1f);
					else
						shader.baseColor.set(220 / 255f, 80 / 255f, 30 / 255f, 1f);
					drawCenteredImage(shader, glLocationMarkerTexID, loc.getX(), loc.getY(), 16, 16);
				}

				for (WorldLocation loc : locations) {
					for (WorldPathElement marker : loc.path) {
						PointRenderQueue.addPoint().setPosition(marker.getX(), marker.getY(), 0);

						if ((marker.mouseOver && dragMarker == null) || marker == dragMarker)
							shader.baseColor.set(1f, 0f, 1f, 1f);
						else
							shader.baseColor.set(230 / 255f, 190 / 255f, 180 / 255f, 1f);
						drawCenteredImage(shader, glPathMarkerTexID, marker.getX(), marker.getY(), 16, 16);
					}
				}
			}
			else {
				WSPointShader shader = ShaderManager.use(WSPointShader.class);

				for (WorldLocation loc : locations) {
					shader.color.set(0.1f, 0.1f, 0.1f, 1f);
					drawCenteredImage(shader, glLocationMarkerTexID, loc.getX() + 1, 0.5f + loc.getY() - 1f, 11f, 7.5f);

					if ((loc.mouseOver && dragMarker == null) || loc == dragMarker)
						shader.color.set(1f, 0f, 1f, 1f);
					//	else if(loc == parentingMarker)
					//		shader.color.set(0f, 1f, 1f, 1f);
					else if (loc == selectedLocation)
						shader.color.set(1f, interpColor(0.2f, 0.8f), interpColor(0.2f, 0.8f), 1f);
					else
						shader.color.set(220 / 255f, 80 / 255f, 30 / 255f, 1f);
					drawCenteredImage(shader, glLocationMarkerTexID, loc.getX(), 0.5f + loc.getY(), 11f, 7.5f);
				}

				for (WorldLocation loc : locations) {
					for (WorldPathElement marker : loc.path) {
						PointRenderQueue.addPoint().setPosition(marker.getX(), marker.getY(), 0);

						shader.color.set(0.1f, 0.1f, 0.1f, 1f);
						drawCenteredImage(shader, glPathMarkerTexID, marker.getX() + 1, marker.getY() - 1f, 4, 4);

						if ((marker.mouseOver && dragMarker == null) || marker == dragMarker)
							shader.color.set(1f, 0f, 1f, 1f);
						else
							shader.color.set(230 / 255f, 190 / 255f, 180 / 255f, 1f);
						drawCenteredImage(shader, glPathMarkerTexID, marker.getX(), marker.getY(), 4, 4);
					}
				}
			}
		}
	}

	private void drawCenteredImage(BasicTexturedShader shader, int texID, float x, float y, float sizeX, float sizeY)
	{
		drawImage(shader, texID, x - sizeX / 2, y - sizeY / 2, x + sizeX / 2, y + sizeY / 2);
	}

	private void drawImage(BasicTexturedShader shader, int texID, float x1, float y1, float x2, float y2)
	{
		shader.texture.bind(texID);
		shader.setXYQuadCoords(x1, y1, x2, y2, 0);
		shader.renderQuad();
	}

	private void drawCenteredImage(WSPointShader shader, int texID, float x, float y, float sizeX, float sizeY)
	{
		drawImage(shader, texID, x - sizeX / 2, y - sizeY / 2, x + sizeX / 2, y + sizeY / 2);
	}

	private void drawImage(WSPointShader shader, int texID, float x1, float y1, float x2, float y2)
	{
		shader.setXYQuadCoords(x1, y1, x2, y2, 0);
		shader.renderQuad();
	}

	protected static void drawAxes(float lineWidth)
	{
		RenderState.setLineWidth(lineWidth);
		float zpos = -10.0f;

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, 0, zpos).setColor(PresetColor.RED).getIndex(),
			LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, 0, zpos).setColor(PresetColor.RED).getIndex());

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, 0, zpos).setColor(PresetColor.GREEN).getIndex(),
			LineRenderQueue.addVertex().setPosition(0, Short.MAX_VALUE, zpos).setColor(PresetColor.GREEN).getIndex());

		LineRenderQueue.render(true);
	}

	private void drawGrid(float lineWidth)
	{
		RenderState.setColor(0.8f, 0.8f, 0.8f, 0.25f);
		RenderState.setLineWidth(lineWidth);
		float zpos = -10.0f;

		int max = MAX_SIZE;
		int step = 32;

		for (int i = 0; i <= max; i += step) {
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(i, 0, zpos).getIndex(),
				LineRenderQueue.addVertex().setPosition(i, max, zpos).getIndex());

		}

		for (int i = 0; i <= max; i += step) {
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(0, i, zpos).getIndex(),
				LineRenderQueue.addVertex().setPosition(max, i, zpos).getIndex());
		}

		RenderState.setDepthWrite(false);
		LineRenderQueue.render(true);
		RenderState.setDepthWrite(true);
	}

	private void showControls()
	{
		incrementDialogsOpen();

		SwingUtils.getMessageDialog()
			.setParent(getFrame())
			.setTitle("Controls and Shortcuts")
			.setMessage(new WorldShortcutsPanel())
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.show();

		decrementDialogsOpen();
	}

	private void loadData()
	{
		try {
			locations = WorldMapModder.loadLocations();
			Logger.log("Loaded world map.");
		}
		catch (IOException e) {
			locations = new ArrayList<>();
			Logger.printStackTrace(e);
		}
	}

	@Override
	protected void saveChanges()
	{
		WorldMapModder.saveLocations(locations);
		Logger.log("Saved world map.");
	}

	@Override
	public void moveMouse(int dx, int dy)
	{
		if (dragMarker != null) {
			float scale = cam.getZoom();
			dragMarker.dragX += dx * scale;
			dragMarker.dragY += dy * scale;

			if ((dragMarker instanceof WorldLocation loc) && cbMoveTogether.isSelected()) {
				for (WorldPathElement elem : loc.path) {
					elem.dragX += dx * scale;
					elem.dragY += dy * scale;
				}
			}
		}
	}

	@Override
	public void clickLMB()
	{
		if (keyboard.isCtrlDown()) {
			if (dragMarker == null && mouseMarker != null)
				addPathPoint(mouseMarker);
		}
		else {
			if (mouseMarker == null || mouseMarker instanceof WorldLocation)
				setSelectedLocation((WorldLocation) mouseMarker);
		}
	}

	@Override
	public void clickRMB()
	{
		if (dragMarker == null && keyboard.isCtrlDown()) {
			if (mouseMarker != null)
				removePathPoint(mouseMarker);
		}
	}

	@Override
	public void startHoldingLMB()
	{
		if (mouseMarker != null) {
			dragMarker = mouseMarker;
		}
	}

	@Override
	public void stopHoldingLMB()
	{
		if (dragMarker != null) {
			for (WorldLocation loc : locations) {
				if (loc.dragX != 0 || loc.dragY != 0) {
					loc.x += Math.round(loc.dragX);
					loc.y += Math.round(loc.dragY);
					loc.dragX = 0;
					loc.dragY = 0;
				}

				for (WorldPathElement elem : loc.path) {
					if (elem.dragX != 0 || elem.dragY != 0) {
						elem.x += Math.round(elem.dragX);
						elem.y += Math.round(elem.dragY);
						elem.dragX = 0;
						elem.dragY = 0;
					}
				}
			}

			dragMarker = null;
		}
	}

	@Override
	public void startHoldingRMB()
	{
		if (mouseMarker != null && mouseMarker instanceof WorldLocation) {
			parentingMarker = mouseMarker;
		}
	}

	@Override
	public void stopHoldingRMB()
	{
		if (parentingMarker != null) {
			if (mouseMarker != null) {
				if (mouseMarker instanceof WorldLocation loc)
					((WorldLocation) parentingMarker).parent = loc;
				else if (mouseMarker instanceof WorldPathElement pathElem)
					((WorldLocation) parentingMarker).parent = pathElem.owner;
			}

			parentingMarker = null;
		}
	}

	private static void addPathPoint(WorldMarker marker)
	{
		if (marker instanceof WorldLocation) {
			WorldLocation loc = (WorldLocation) marker;
			if (loc.path.size() >= 32) {
				Toolkit.getDefaultToolkit().beep();
				Logger.log("Path has reached maximum size (32 points)");
				return;
			}

			int prev1X, prev1Y;
			int prev2X, prev2Y;

			if (loc.path.size() > 1) {
				WorldPathElement prev2 = loc.path.get(loc.path.size() - 2);
				WorldPathElement prev1 = loc.path.get(loc.path.size() - 1);
				prev2X = prev2.x;
				prev2Y = prev2.y;
				prev1X = prev1.x;
				prev1Y = prev1.y;
			}
			else if (loc.path.size() == 1) {
				WorldPathElement prev1 = loc.path.get(loc.path.size() - 1);
				prev2X = loc.x;
				prev2Y = loc.y;
				prev1X = prev1.x;
				prev1Y = prev1.y;
			}
			else {
				prev2X = loc.x;
				prev2Y = loc.y + 10;
				prev1X = loc.x;
				prev1Y = loc.y;
			}

			int dX = prev1X - prev2X;
			int dY = prev1Y - prev2Y;
			loc.path.add(new WorldPathElement(loc, prev1X + dX, prev1Y + dY));
		}
		else if (marker instanceof WorldPathElement) {
			WorldPathElement elem = (WorldPathElement) marker;
			if (elem.owner.path.size() >= 32) {
				Toolkit.getDefaultToolkit().beep();
				Logger.log("Path already has 32 points!");
				return;
			}

			List<WorldPathElement> path = elem.owner.path;
			int pos = path.indexOf(elem);

			int prev1X, prev1Y;
			int prev2X, prev2Y;
			int dX, dY;

			// last marker in path
			if (pos == path.size() - 1) {
				if (pos == 0) {
					prev2X = elem.owner.x;
					prev2Y = elem.owner.y;
					prev1X = elem.x;
					prev1Y = elem.y;
				}
				else {
					WorldPathElement prev = elem.owner.path.get(pos - 1);
					prev2X = prev.x;
					prev2Y = prev.y;
					prev1X = elem.x;
					prev1Y = elem.y;
				}

				dX = prev1X - prev2X;
				dY = prev1Y - prev2Y;
			}
			else {
				WorldPathElement next = elem.owner.path.get(pos + 1);
				prev2X = next.x;
				prev2Y = next.y;
				prev1X = elem.x;
				prev1Y = elem.y;

				dX = (prev2X - prev1X) / 2;
				dY = (prev2Y - prev1Y) / 2;
			}

			elem.owner.path.add(pos + 1, new WorldPathElement(elem.owner, prev1X + dX, prev1Y + dY));
		}
	}

	private static void removePathPoint(WorldMarker marker)
	{
		if (marker instanceof WorldLocation) {
			WorldLocation loc = (WorldLocation) marker;
			if (loc.path.size() == 0) {
				Toolkit.getDefaultToolkit().beep();
				Logger.log("Path is empty!");
				return;
			}

			loc.path.remove(loc.path.size() - 1);
		}
		else if (marker instanceof WorldPathElement) {
			WorldPathElement elem = (WorldPathElement) marker;
			elem.owner.path.remove(elem);
		}
	}

	private void setSelectedLocation(WorldLocation loc)
	{
		selectedLocation = loc;
		selectedPanel.setVisible(selectedLocation != null);
		if (selectedLocation != null) {
			locationsBox.setSelectedItem(selectedLocation.name);
			storyBox.setSelectedItem(selectedLocation.descUpdate);
		}
	}
}
