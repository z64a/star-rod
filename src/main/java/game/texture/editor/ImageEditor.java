package game.texture.editor;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import app.Environment;
import app.SwingUtils;
import common.BaseEditor;
import common.BaseEditorSettings;
import common.BasicCamera;
import common.BasicCommandManager;
import common.BasicEditorCommand;
import common.KeyboardInput.KeyInputEvent;
import common.MouseInput.MouseManagerListener;
import common.MousePixelRead;
import game.map.editor.render.PresetColor;
import game.map.editor.render.TextureManager;
import game.map.editor.ui.SwatchPanel;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.OpenFileChooser;
import game.map.editor.ui.dialogs.SaveFileChooser;
import game.map.shape.TransformMatrix;
import game.texture.ImageConverter.ImageFormatException;
import game.texture.Palette;
import game.texture.Tile;
import game.texture.TileFormat;
import game.texture.editor.Dither.DitherMethod;
import game.texture.editor.ImageColorChooser.ColorModel;
import game.texture.editor.ImageColorChooser.ColorUpdateListener;
import game.texture.editor.dialogs.ConvertOptionsPanel;
import game.texture.editor.dialogs.ConvertOptionsPanel.ConvertSettings;
import game.texture.editor.dialogs.ConvertOptionsPanel.ConvertSettings.IntensityMethod;
import game.texture.editor.dialogs.CreateOptionsPanel;
import game.texture.editor.dialogs.ImageShortcutsPanel;
import game.texture.editor.dialogs.ImportOptionsPanel;
import game.texture.editor.dialogs.ResizeOptionsPanel;
import game.texture.editor.dialogs.ResizeOptionsPanel.ResizeOptions;
import net.miginfocom.swing.MigLayout;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicTexturedShader;
import renderer.text.DrawableString;
import renderer.text.TextRenderer;
import renderer.text.TextStyle;
import util.Logger;

public class ImageEditor extends BaseEditor implements MouseManagerListener, ColorUpdateListener
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		BaseEditor editor = new ImageEditor();
		editor.launch();
		Environment.exit();
	}

	private static final BaseEditorSettings EDITOR_SETTINGS = BaseEditorSettings.create()
		.setTitle(Environment.decorateTitle("Image Editor"))
		.setLog("image_editor.log")
		.setFullscreen(true)
		.setResizeable(true)
		.hasMenuBar(true)
		.setSize(800, 600)
		.setFramerate(60);

	private EditorImage image;

	private DrawableString greetingStr;

	private BasicCommandManager commandManager;
	private final BasicCamera cam;

	private OpenFileChooser importFileChooser;
	private SaveFileChooser exportFileChooser;

	private volatile boolean modified = false;

	private boolean bDrawGrid = true;
	private boolean bDrawBackground = false;
	private JCheckBoxMenuItem cbGrid;
	private JCheckBoxMenuItem cbBackground;

	private MousePixelRead framePick;
	private boolean mousePixelValid;
	private int mousePixelX;
	private int mousePixelY;

	private final Pixel pickedPixel = new Pixel();

	private JPanel sidePanel;

	private JLabel imageLabel;
	private JLabel imageDetailsLabel;
	private SwatchPanel colorPreview;

	private ImageColorChooser chooser;

	private JScrollPane colorSwatchesScrollPane;
	private Container colorSwatchesContainer;
	private PaletteSwatchesPanel currentSwatches;

	private PaletteSwatchesPanel palettePanel4bit;
	private PaletteSwatchesPanel palettePanel8bit;

	private static enum DrawMode
	{
		Color, Select, Deselect
	}

	private DrawMode drawMode = DrawMode.Color;
	private boolean drawing = false;
	private boolean adjustingColor = false;

	public ImageEditor()
	{
		super(EDITOR_SETTINGS);

		cam = new BasicCamera(
			0.0f, 0.0f, 0.5f,
			0.08f, 0.0125f, 1.0f,
			true, true);

		resetEditor();
		setup();
	}

	private void resetEditor()
	{
		commandManager = new BasicCommandManager(32);

		mousePixelValid = false;
		pickedPixel.clear();
		drawing = false;
		adjustingColor = false;
	}

	public void push(BasicEditorCommand cmd)
	{
		commandManager.pushCommand(cmd);
		modified = true;
	}

	@Override
	protected void createGui(JPanel toolPanel, Canvas glCanvas, JMenuBar menubar, JLabel infoLabel, ActionListener openLogAction)
	{
		getFrame().setTransferHandler(new FileTransferHandler(this, fileList -> openImageEDT(fileList.get(fileList.size() - 1))));

		File imgDir = Environment.getProjectDirectory();
		importFileChooser = new OpenFileChooser(imgDir, "Import Image", "Images", "png", "jpg", "jpeg", "gif");
		exportFileChooser = new SaveFileChooser(imgDir, "Export Image", "Images", "png");

		// major components
		colorPreview = new SwatchPanel(1.32f, 1.33f);
		chooser = new ImageColorChooser(this);
		palettePanel4bit = new PaletteSwatchesPanel(this, 16, 8);
		palettePanel8bit = new PaletteSwatchesPanel(this, 256, 8);

		colorSwatchesContainer = new JPanel(new MigLayout("ins 0, fill"));

		colorSwatchesScrollPane = new JScrollPane(colorSwatchesContainer);
		colorSwatchesScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		colorSwatchesScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

		imageLabel = SwingUtils.getLabel("Selected", SwingConstants.CENTER, 14);
		imageDetailsLabel = SwingUtils.getLabel("Details", SwingConstants.CENTER, 12);

		sidePanel = new JPanel(new MigLayout("w 320!, ins 8, wrap, hidemode 3"));
		sidePanel.add(imageLabel, "grow");
		sidePanel.add(imageDetailsLabel, "grow");
		sidePanel.add(colorPreview, "h 96!, align center, grow");
		sidePanel.add(chooser);
		sidePanel.add(colorSwatchesScrollPane, "growx, top, gaptop 16");

		sidePanel.setVisible(false);

		toolPanel.setLayout(new MigLayout("fill, ins 0, hidemode 2"));
		toolPanel.add(glCanvas, "grow, push");
		toolPanel.add(sidePanel, "growy, wrap");
		toolPanel.add(infoLabel, "h 16!, gapleft 4, gapbottom 4, span");
		addOptionsMenu(menubar, openLogAction);
		addEditorMenu(menubar);
		addImageMenu(menubar);
		addViewMenu(menubar);
	}

	@Override
	public void glInit()
	{
		TextureManager.bindEditorTextures();

		glClearStencil(0);
		glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);

		TextRenderer.init();

		TextStyle style = new TextStyle(TextRenderer.FONT_MONO);
		style.setCentered(true, true).setThickness(0.5f, 0.2f);

		greetingStr = new DrawableString(style);
		greetingStr.setText("drag an image file into this viewport");
	}

	@Override
	public void updateColor(Color c, boolean bAdjusting)
	{
		assert (SwingUtilities.isEventDispatchThread());

		pickedPixel.r = c.getRed();
		pickedPixel.g = c.getGreen();
		pickedPixel.b = c.getBlue();
		pickedPixel.a = c.getAlpha();

		colorPreview.setForeground(c);

		// edting palette color
		if (currentSwatches != null && image.editablePalette) {
			if (!adjustingColor) {
				image.startPaletteEdit(pickedPixel.index);
				adjustingColor = true;
			}

			currentSwatches.setPaletteColor(pickedPixel.index, c);
			currentSwatches.repaint();

			if (!bAdjusting) {
				image.endPaletteEdit(pickedPixel.index);
				adjustingColor = false;
			}
		}
	}

	public void setSelectedIndex(int index, boolean fromEditor)
	{
		assert (!SwingUtilities.isEventDispatchThread());
		assert (currentSwatches != null);

		pickedPixel.index = index;

		Color c = currentSwatches.getPaletteColor(index);
		chooser.setSelectedColor(c);
		colorPreview.setForeground(c);

		if (!fromEditor)
			currentSwatches.setSelectedIndex(index);
	}

	public void selectByColor(int index)
	{
		assert (!SwingUtilities.isEventDispatchThread());
		assert (currentSwatches != null);

		if (image == null)
			return;

		image.startSelection();
		image.selectByIndex(index);
		image.endSelection();
	}

	public void setSelectedColor(Color c)
	{
		assert (!SwingUtilities.isEventDispatchThread());
		assert (currentSwatches == null);

		pickedPixel.r = c.getRed();
		pickedPixel.g = c.getGreen();
		pickedPixel.b = c.getBlue();
		pickedPixel.a = c.getAlpha();

		chooser.setSelectedColor(c);
		colorPreview.setForeground(c);
	}

	private void showControls()
	{
		incrementDialogsOpen();

		SwingUtils.getMessageDialog()
			.setParent(getFrame())
			.setTitle("Controls and Shortcuts")
			.setMessage(new ImageShortcutsPanel())
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.show();

		decrementDialogsOpen();
	}

	private void setPixelEditorEDT()
	{
		assert (SwingUtilities.isEventDispatchThread());

		if (image == null) {
			sidePanel.setVisible(false);
			sidePanel.revalidate();
			return;
		}

		imageLabel.setText((image.source != null) ? image.source.getName() : "New Image");
		imageDetailsLabel.setText(String.format("%d x %d %s", image.width, image.height, image.format));

		colorSwatchesContainer.removeAll();

		switch (image.format) {
			case CI_4:
				currentSwatches = palettePanel4bit;
				image.bindPalette(currentSwatches);

				colorSwatchesScrollPane.setVisible(true);
				colorSwatchesContainer.add(currentSwatches, "top");
				currentSwatches.repaint();

				chooser.setVisible(true);
				chooser.enableAlpha(true);
				chooser.setColorModel(ColorModel.RGB);
				break;

			case CI_8:
				currentSwatches = palettePanel8bit;
				image.bindPalette(currentSwatches);

				colorSwatchesScrollPane.setVisible(true);
				colorSwatchesContainer.add(currentSwatches, "top");
				currentSwatches.repaint();

				chooser.setVisible(true);
				chooser.enableAlpha(true);
				chooser.setColorModel(ColorModel.RGB);
				break;

			case I_4:
				currentSwatches = palettePanel4bit;
				image.bindPalette(currentSwatches);

				colorSwatchesScrollPane.setVisible(true);
				colorSwatchesContainer.add(currentSwatches, "top");
				currentSwatches.repaint();

				// palette only
				chooser.setVisible(false);
				break;
			case I_8:
				currentSwatches = null;

				colorSwatchesScrollPane.setVisible(false);

				chooser.setVisible(true);
				chooser.enableAlpha(false);
				chooser.setColorModel(ColorModel.Intensity);
				break;
			case IA_4:
				currentSwatches = palettePanel4bit;
				image.bindPalette(currentSwatches);

				colorSwatchesScrollPane.setVisible(true);
				colorSwatchesContainer.add(currentSwatches, "top");
				currentSwatches.repaint();

				// palette only
				chooser.setVisible(false);
				break;
			case IA_8:
				currentSwatches = palettePanel8bit;
				image.bindPalette(currentSwatches);

				colorSwatchesScrollPane.setVisible(true);
				colorSwatchesContainer.add(currentSwatches, "top");
				currentSwatches.repaint();

				// palette only
				chooser.setVisible(false);
				break;
			case IA_16:
				currentSwatches = null;
				colorSwatchesScrollPane.setVisible(false);
				chooser.setVisible(true);
				chooser.enableAlpha(true);
				chooser.setColorModel(ColorModel.Intensity);
				break;
			case RGBA_16:
				currentSwatches = null;
				colorSwatchesScrollPane.setVisible(false);
				chooser.setVisible(true);
				chooser.enableAlpha(true);
				chooser.setColorModel(ColorModel.RGB);
				break;
			case RGBA_32:
				currentSwatches = null;
				colorSwatchesScrollPane.setVisible(false);
				chooser.setVisible(true);
				chooser.enableAlpha(true);
				chooser.setColorModel(ColorModel.RGB);
				break;
			default:
				currentSwatches = null;
				colorSwatchesScrollPane.setVisible(false);
				chooser.setVisible(false);
				sidePanel.setVisible(true);
				sidePanel.revalidate();
				Logger.logWarning("Unsupported format: " + image.format);
				invokeLater(() -> {
					setImage(null);
				});
				return;
		}

		invokeLater(() -> {
			if (currentSwatches != null)
				setSelectedIndex(0, false);
			else
				setSelectedColor(Color.BLACK);
		});

		sidePanel.setVisible(true);
		sidePanel.revalidate();
	}

	private void addOptionsMenu(JMenuBar menuBar, ActionListener openLogAction)
	{
		JMenuItem item;

		JMenu menu = new JMenu(String.format("  %-10s", "File"));
		menu.setPreferredSize(new Dimension(60, 20));
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("New");
		item.addActionListener((e) -> {
			newImageEDT();
		});
		menu.add(item);

		item = new JMenuItem("Import");
		item.addActionListener((e) -> {
			promptImportImage();
		});
		menu.add(item);

		item = new JMenuItem("Export");
		item.addActionListener((e) -> {
			exportImage();
		});
		menu.add(item);

		item = new JMenuItem("Close");
		item.addActionListener((e) -> {
			invokeLater(() -> {
				setImage(null);
			});
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
		KeyStroke awtKeyStroke;

		JMenu menu = new JMenu(String.format("  %-10s", "Editor"));
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Undo");
		awtKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
		item.setAccelerator(awtKeyStroke);
		item.addActionListener((e) -> {
			undoEDT();
		});
		menu.add(item);

		item = new JMenuItem("Redo");
		awtKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
		item.setAccelerator(awtKeyStroke);
		item.addActionListener((e) -> {
			redoEDT();
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("View Controls");
		item.addActionListener((e) -> {
			showControls();
		});
		menu.add(item);
	}

	private void addImageMenu(JMenuBar menuBar)
	{
		JMenuItem item;

		JMenu menu = new JMenu(String.format("  %-10s", "Image"));
		menu.setPreferredSize(new Dimension(60, 20));
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Convert Format");
		item.addActionListener((e) -> {
			convertImageEDT();
		});
		menu.add(item);

		item = new JMenuItem("Choose Palette");
		item.addActionListener((e) -> {
			choosePaletteEDT();
		});
		menu.add(item);

		item = new JMenuItem("Resize");
		item.addActionListener((e) -> {
			resizeImageEDT();
		});
		menu.add(item);

		item = new JMenuItem("Clear Selection Mask");
		item.addActionListener((e) -> {
			invokeLater(() -> {
				clearSelection();
			});
		});
		menu.add(item);
	}

	private void addViewMenu(JMenuBar menuBar)
	{
		JMenu menu = new JMenu(String.format("  %-10s", "  View"));
		menu.setPreferredSize(new Dimension(60, 20));
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		cbGrid = new JCheckBoxMenuItem("Draw Grid");
		cbGrid.setSelected(true);
		cbGrid.addActionListener((e) -> {
			invokeLater(() -> {
				bDrawGrid = cbGrid.isSelected();
			});
		});
		menu.add(cbGrid);

		cbBackground = new JCheckBoxMenuItem("Draw Background");
		cbBackground.setSelected(false);
		cbBackground.addActionListener((e) -> {
			invokeLater(() -> {
				bDrawBackground = cbBackground.isSelected();
			});
		});
		menu.add(cbBackground);
	}

	@Override
	protected void update(double deltaTime)
	{
		framePick = cam.getMousePosition(mouse.getPosX(), mouse.getPosY(), false, false);

		mousePixelValid = (framePick != null && image != null);
		if (mousePixelValid) {
			mousePixelX = (int) Math.floor(framePick.worldPos.x);
			mousePixelY = (int) Math.floor(framePick.worldPos.y);

			mousePixelValid = (mousePixelX >= 0 && mousePixelX < image.width &&
				mousePixelY >= 0 && mousePixelY < image.height);
		}

		cam.handleInput(mouse, keyboard, deltaTime, glCanvasWidth(), glCanvasHeight());

		time += deltaTime;
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
			case KeyEvent.VK_F:
				if (!ctrl) {
					if (shift)
						selectionFill();
					else if (alt)
						deselectionFill();
					else
						fillSelection();
				}
				break;
			case KeyEvent.VK_K:
				if (!shift && ctrl && !alt)
					clearSelection();
				break;
			case KeyEvent.VK_G:
				if (!shift && !ctrl && !alt) {
					bDrawGrid = !bDrawGrid;
					cbGrid.setSelected(bDrawGrid);
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
		int width = (image != null) ? image.width : 100;
		int height = (image != null) ? image.height : 100;

		cam.centerOn(glCanvasWidth(), glCanvasHeight(),
			width / 2, height / 2, 0,
			width, height, 0);

		cam.setMinPos(0, 0);
		cam.setMaxPos(width, height);
	}

	// animated selection box
	private static double omega = 1.0 * Math.PI / 1.6;
	private double time = Double.MIN_VALUE;
	private float colorLerpAlpha = 0.0f;

	private float interpColor(float min, float max)
	{
		return min + colorLerpAlpha * (max - min);
	}

	@Override
	public void glDraw()
	{
		colorLerpAlpha = 0.5f * (float) Math.sin(omega * time);
		colorLerpAlpha = 0.5f + colorLerpAlpha * colorLerpAlpha; // more pleasing

		cam.glSetViewport(0, 0, glCanvasWidth(), glCanvasHeight());

		if (image != null && bDrawBackground)
			glClearColor(0.6f, 0.6f, 0.6f, 1.0f);
		else
			glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

		if (image != null && bDrawBackground)
			drawBackground();

		cam.setOrthoView();
		cam.glLoadTransform();
		RenderState.setModelMatrix(null);

		if (image != null) {
			image.renderImage(interpColor(0.0f, 1.0f));
			drawGrid(1.0f);
		}
		else {
			TransformMatrix projMatrix = TransformMatrix.identity();
			projMatrix.ortho(0, glCanvasWidth(), glCanvasHeight(), 0, -1, 1);
			RenderState.setProjectionMatrix(projMatrix);
			RenderState.setViewMatrix(null);

			greetingStr.draw(24, glCanvasWidth() / 2.0f, glCanvasHeight() / 2.0f, (float) getDeltaTime());
		}
	}

	private void drawBackground()
	{
		TransformMatrix projMatrix = TransformMatrix.identity();
		projMatrix.ortho(0.0f, 1.0f, 1.0f, 0.0f, -1.0f, 1.0f);
		RenderState.setProjectionMatrix(projMatrix);
		RenderState.setViewMatrix(null);

		float scaleU = glCanvasWidth() / 512.0f;
		float scaleV = scaleU * glCanvasHeight() / glCanvasWidth();

		BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
		shader.texture.bind(TextureManager.glBackground);
		shader.setXYQuadCoords(0, 0, 1, 1, 0);
		shader.setQuadTexCoords(0, 0, scaleU, scaleV);

		RenderState.setDepthWrite(false);
		shader.renderQuad();
		RenderState.setDepthWrite(true);
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
		RenderState.setLineWidth(lineWidth);
		float zpos = -10.0f;

		if (bDrawGrid) {
			float nearDist = 30.0f;
			float farDist = 120.0f;
			float nearAlpha = 0.5f;
			float farAlpha = 0.0f;

			float dist = cam.getDist();
			float alpha;

			if (dist > farDist)
				alpha = farAlpha;
			else if (dist < nearDist)
				alpha = nearAlpha;
			else
				alpha = nearAlpha - (nearAlpha - farAlpha) * (dist - nearDist) / (farDist - nearDist);

			RenderState.setColor(0.8f, 0.8f, 0.8f, alpha);

			for (int i = 1; i < image.width; i++) {
				LineRenderQueue.addLine(
					LineRenderQueue.addVertex().setPosition(i, 0, zpos).getIndex(),
					LineRenderQueue.addVertex().setPosition(i, image.height, zpos).getIndex());

			}

			for (int i = 1; i < image.height; i++) {
				LineRenderQueue.addLine(
					LineRenderQueue.addVertex().setPosition(0, i, zpos).getIndex(),
					LineRenderQueue.addVertex().setPosition(image.width, i, zpos).getIndex());
			}
		}

		RenderState.setColor(0.9f, 0.9f, 0.9f, 1.0f);
		LineRenderQueue.addLineLoop(
			LineRenderQueue.addVertex().setPosition(0, 0, zpos).getIndex(),
			LineRenderQueue.addVertex().setPosition(0, image.height, zpos).getIndex(),
			LineRenderQueue.addVertex().setPosition(image.width, image.height, zpos).getIndex(),
			LineRenderQueue.addVertex().setPosition(image.width, 0, zpos).getIndex());

		if (mousePixelValid) {
			zpos = -20.0f;
			RenderState.setColor(PresetColor.TEAL);
			LineRenderQueue.addLineLoop(
				LineRenderQueue.addVertex().setPosition(mousePixelX, mousePixelY, zpos).getIndex(),
				LineRenderQueue.addVertex().setPosition(mousePixelX + 1, mousePixelY, zpos).getIndex(),
				LineRenderQueue.addVertex().setPosition(mousePixelX + 1, mousePixelY + 1, zpos).getIndex(),
				LineRenderQueue.addVertex().setPosition(mousePixelX, mousePixelY + 1, zpos).getIndex());
		}

		RenderState.setDepthWrite(false);
		LineRenderQueue.render(true);
		RenderState.setDepthWrite(true);
	}

	@Override
	protected boolean isModified()
	{
		return modified;
	}

	@Override
	protected void saveChanges()
	{
		exportImage();
	}

	private void exportImage()
	{
		assert (SwingUtilities.isEventDispatchThread());

		if (image == null)
			return;

		File file = null;

		if (image.source == null)
			file = promptChooseExportFile(null);
		else
			file = promptOverwrite(image.source);

		if (file == null)
			return;

		Tile out = image.getTile();

		try {
			out.savePNG(file.getAbsolutePath());
			image.source = file;
			modified = false;
			imageLabel.setText((image.source != null) ? image.source.getName() : "New Image");
			Logger.log("Exported " + file.getName());
		}
		catch (IOException e) {
			super.showStackTrace(e);
		}
	}

	private File promptOverwrite(File file)
	{
		int choice = getOptionDialog("Export Image", "File already exists: \n" + file.getName())
			.setOptions("Overwrite", "Choose File", "Cancel")
			.choose();

		if (choice == 0)
			return file;

		if (choice == 2)
			return null;

		return promptChooseExportFile(file);
	}

	private File promptChooseExportFile(File file)
	{
		File chosen = null;

		if (file != null)
			exportFileChooser.setCurrentDirectory(file.getParentFile());
		super.incrementDialogsOpen();
		if (exportFileChooser.prompt() == ChooseDialogResult.APPROVE)
			chosen = importFileChooser.getSelectedFile();
		super.decrementDialogsOpen();

		return chosen;
	}

	private void setImage(EditorImage newImage)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		image = newImage;
		modified = false;

		resetEditor();
		resetCam();

		SwingUtilities.invokeLater(() -> {
			setPixelEditorEDT();
		});
	}

	private void newImageEDT()
	{
		assert (SwingUtilities.isEventDispatchThread());

		CreateOptionsPanel createOptions = new CreateOptionsPanel();
		int choice = getConfirmDialog("New Image Options", createOptions).choose();
		if (choice != JOptionPane.OK_OPTION)
			return;

		Tile newTile;
		try {
			TileFormat tileFmt = createOptions.getFormat();
			newTile = new Tile(tileFmt, createOptions.getImageHeight(), createOptions.getImageWidth());
			switch (tileFmt) {
				case CI_4:
					newTile.palette = Palette.createDefaultForEditor(16, 0.8f);
					break;
				case CI_8:
					newTile.palette = Palette.createDefaultForEditor(256, 0.8f);
					break;
				case IA_16:
					break;
				case IA_4:
					break;
				case IA_8:
					break;
				case I_4:
					break;
				case I_8:
					break;
				case RGBA_16:
					break;
				case RGBA_32:
					break;
				default:
					Logger.logWarning("Unsupported format: " + image.format);
					break;
			}
		}
		catch (Throwable t) {
			super.showStackTrace(t);
			return;
		}

		invokeLater(() -> {
			setImage(new EditorImage(this, newTile, null));
			Logger.log("Created new " + newTile.format + " image");
		});
	}

	private void promptImportImage()
	{
		super.incrementDialogsOpen();
		if (image != null && image.source != null)
			importFileChooser.setDirectoryContaining(image.source.getParentFile());
		if (importFileChooser.prompt() == ChooseDialogResult.APPROVE) {
			File f = importFileChooser.getSelectedFile();
			if (f != null && f.exists())
				openImageEDT(f);
		}
		super.decrementDialogsOpen();
	}

	private void openImageEDT(File file)
	{
		assert (SwingUtilities.isEventDispatchThread());

		ImportOptionsPanel importOptions = new ImportOptionsPanel();
		int choice = getConfirmDialog("Import Options", importOptions).choose();
		if (choice != JOptionPane.OK_OPTION)
			return;

		EditorImage loadedImage;
		try {
			try {
				Tile newTile = Tile.load(file, importOptions.getFormat());
				loadedImage = new EditorImage(this, newTile, file);
			}
			catch (ImageFormatException e) {
				if (importOptions.getFormat().type == TileFormat.TYPE_CI) {
					choice = getConfirmDialog("Incorrect Image Format",
						"Image file is not color-indexed. \nWould you like to convert it?").choose();
					if (choice != JOptionPane.OK_OPTION)
						return;

					Tile newTile = Tile.load(file, TileFormat.RGBA_32);
					EditorImage rgbaImage = new EditorImage(this, newTile, file);

					loadedImage = EditorImage.convert(rgbaImage, new ConvertSettings(
						importOptions.getFormat(),
						IntensityMethod.Luminance,
						DitherMethod.None));

					Logger.log("Converted format to " + loadedImage.format);
				}
				else
					throw e;
			}
		}
		catch (Throwable t) {
			super.showStackTrace(t);
			return;
		}

		final EditorImage newImage = loadedImage;
		invokeLater(() -> {
			setImage(newImage);
			Logger.log("Opened image " + file.getName());
		});
	}

	private Palette getPaletteEDT(File file)
	{
		assert (SwingUtilities.isEventDispatchThread());

		ImportOptionsPanel importOptions = new ImportOptionsPanel();
		int choice = getConfirmDialog("Import Options", importOptions).choose();
		if (choice != JOptionPane.OK_OPTION)
			return null;

		Palette pal = null;

		File f = importFileChooser.getSelectedFile();
		if (f != null && f.exists()) {
			try {
				try {
					Tile newTile = Tile.load(f, importOptions.getFormat());
					pal = newTile.palette;
				}
				catch (ImageFormatException e) {
					if (importOptions.getFormat().type == TileFormat.TYPE_CI) {
						showErrorDialog("Incorrect Image Format",
							"Image file is not color-indexed.",
							"Cannot be used for palette.");
					}
					else
						throw e;
				}
			}
			catch (Throwable t) {
				super.showStackTrace(t);
				return null;
			}
		}

		return pal;
	}

	private void choosePaletteEDT()
	{
		assert (SwingUtilities.isEventDispatchThread());

		if (image == null)
			return;

		Palette pal = null;
		File file = null;

		super.incrementDialogsOpen();
		if (image != null && image.source != null)
			importFileChooser.setDirectoryContaining(image.source.getParentFile());
		if (importFileChooser.prompt() == ChooseDialogResult.APPROVE) {
			ImportOptionsPanel importOptions = new ImportOptionsPanel();
			int choice = getConfirmDialog("Import Options", importOptions).choose();
			if (choice != JOptionPane.OK_OPTION)
				return;

			file = importFileChooser.getSelectedFile();
			if (file != null && file.exists())
				pal = getPaletteEDT(file);
		}
		super.decrementDialogsOpen();

		EditorImage newImage = EditorImage.forcePalette(image, pal);
		final String filename = file.getName();
		invokeLater(() -> {
			setImage(newImage);
			Logger.log("Using palette from " + filename);
		});
	}

	private void resizeImageEDT()
	{
		assert (SwingUtilities.isEventDispatchThread());

		if (image == null)
			return;

		ResizeOptionsPanel resizeOptionsPanel = new ResizeOptionsPanel();
		int choice = getConfirmDialog("Resize Image Options", resizeOptionsPanel).choose();
		if (choice != JOptionPane.OK_OPTION)
			return;

		final ResizeOptions resizeOptions = resizeOptionsPanel.getOptions();
		invokeLater(() -> {
			setImage(EditorImage.resize(image, resizeOptions));
			Logger.logf("Resized image to %d x %d", resizeOptions.width, resizeOptions.height);
		});
	}

	private void convertImageEDT()
	{
		assert (SwingUtilities.isEventDispatchThread());

		if (image == null)
			return;

		ConvertOptionsPanel convertOptionsPanel = new ConvertOptionsPanel();
		int choice = getConfirmDialog("Convert Image Options", convertOptionsPanel).choose();
		if (choice != JOptionPane.OK_OPTION)
			return;

		final ConvertSettings settings = convertOptionsPanel.getSettings();
		if (settings.fmt == image.format)
			return;

		invokeLater(() -> {
			setImage(EditorImage.convert(image, settings));
			Logger.log("Converted image to " + settings.fmt);
		});
	}

	public void clearSelection()
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (image == null)
			return;
		image.startSelection();
		image.clearSelection();
		image.endSelection();
	}

	public void selectionFill()
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (image == null)
			return;

		image.startSelection();
		image.selectionFill(mousePixelX, mousePixelY);
		image.endSelection();
	}

	public void deselectionFill()
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (image == null)
			return;

		image.startSelection();
		image.deselectionFill(mousePixelX, mousePixelY);
		image.endSelection();
	}

	public void fillSelection()
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (image == null)
			return;
		image.startDrawing();
		image.fillSelection(pickedPixel);
		image.endDrawing();
	}

	@Override
	public void moveMouse(int dx, int dy)
	{
		if (!mousePixelValid)
			return;

		if (drawing && mouse.isHoldingLMB()) {
			switch (drawMode) {
				case Color:
					image.draw(mousePixelX, mousePixelY, pickedPixel);
					break;
				case Select:
					image.select(mousePixelX, mousePixelY);
					break;
				case Deselect:
					image.deselect(mousePixelX, mousePixelY);
					break;
			}
		}

		/*
		if(mouseManager.holdingLMB && mousePixelValid)
		{
			if(selectionMode)
				image.select(mousePixelX, mousePixelY);
			else
				image.draw(mousePixelX, mousePixelY, pickedPixel);
		}

		if(mouseManager.holdingRMB && mousePixelValid)
			image.deselect(mousePixelX, mousePixelY);
			*/
	}

	@Override
	public void clickLMB()
	{
		assert (pickedPixel != null);

		boolean shift = keyboard.isShiftDown();
		boolean alt = keyboard.isAltDown();

		if (!mousePixelValid) {
			if (shift && image.hasSelectedPixels())
				clearSelection();
			return;
		}

		drawing = true;
		if (shift)
			drawMode = DrawMode.Select;
		else if (alt)
			drawMode = DrawMode.Deselect;
		else
			drawMode = DrawMode.Color;

		switch (drawMode) {
			case Color:
				image.startDrawing();
				image.draw(mousePixelX, mousePixelY, pickedPixel);
				break;
			case Select:
				image.startSelection();
				image.select(mousePixelX, mousePixelY);
				break;
			case Deselect:
				image.startSelection();
				image.deselect(mousePixelX, mousePixelY);
				break;
		}
	}

	@Override
	public void releaseLMB()
	{
		if (image == null)
			return;

		switch (drawMode) {
			case Color:
				image.endDrawing();
				break;
			case Select:
				image.endSelection();
				break;
			case Deselect:
				image.endSelection();
				break;
		}
		drawing = false;
	}

	@Override
	public void stopHoldingLMB()
	{
		if (image == null)
			return;

		switch (drawMode) {
			case Color:
				image.endDrawing();
				break;
			case Select:
				image.endSelection();
				break;
			case Deselect:
				image.endSelection();
				break;
		}
		drawing = false;
	}

	@Override
	public void clickRMB()
	{
		if (drawing)
			return;

		if (!mousePixelValid) {
			pickedPixel.clear();
			return;
		}

		image.sample(mousePixelX, mousePixelY, pickedPixel);
		if (currentSwatches != null)
			setSelectedIndex(pickedPixel.index, false);
		else
			setSelectedColor(new Color(pickedPixel.r, pickedPixel.g, pickedPixel.b, pickedPixel.a));
	}
}
