package game.message.editor;

import static app.Directories.FN_STRING_EDITOR_CONFIG;
import static game.message.StringConstants.CLOSE_TAG;
import static game.message.StringConstants.OPEN_TAG;
import static game.texture.TileFormat.CI_4;
import static org.lwjgl.opengl.GL11.*;

import java.awt.Canvas;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import org.apache.commons.io.FilenameUtils;

import com.alexandriasoftware.swing.JSplitButton;

import app.Directories;
import app.Environment;
import app.StarRodException;
import app.StarRodMain;
import app.SwingUtils;
import app.config.Config;
import app.config.Options;
import app.config.Options.Scope;
import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetSubdir;
import game.map.editor.common.BaseEditor;
import game.map.editor.common.BaseEditorSettings;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.OpenFileChooser;
import game.message.Message;
import game.message.MessageBoxes;
import game.message.StringConstants;
import game.message.StringConstants.SpecialCharacter;
import game.message.StringConstants.StringEffect;
import game.message.StringConstants.StringFont;
import game.message.StringConstants.StringFunction;
import game.message.StringConstants.StringStyle;
import game.message.StringConstants.StringVoice;
import game.message.StringEncoder;
import game.message.editor.MessageTokenizer.Sequence;
import game.texture.ImageConverter;
import game.texture.Tile;
import net.miginfocom.swing.MigLayout;
import renderer.shaders.RenderState;
import util.Logger;
import util.ui.ImagePanel;

public class MessageEditor extends BaseEditor
{
	private static final String MENU_BAR_SPACING = "    ";

	private static final Dimension menuItemDimension = new Dimension(120, 24);

	private static final BaseEditorSettings EDITOR_SETTINGS = BaseEditorSettings.create()
		.setTitle(Environment.decorateTitle("Message Editor"))
		.setIcon(Environment.getDefaultIconImage())
		.setConfig(Scope.StringEditor, FN_STRING_EDITOR_CONFIG)
		.setLog("msg_editor.log")
		.setFullscreen(false)
		.setResizeable(true)
		.setGrabsMouse(false)
		.hasMenuBar(true)
		.setSize(960, 700)
		.setFramerate(30);

	// window settings

	private static final float PAN_SPEED = 200.0f;
	private static final float MINIMUM_ZOOM = 0.125f;
	private static final float MAXIMUM_ZOOM = 2.0f;

	private static final float DEFAULT_CAMERA_X = 160.0f;
	private static final float DEFAULT_CAMERA_Y = 47.0f;
	private static final float DEFAULT_CAMERA_ZOOM = 0.5f;
	private float cameraX = DEFAULT_CAMERA_X;
	private float cameraY = DEFAULT_CAMERA_Y;
	private float cameraZoom = DEFAULT_CAMERA_ZOOM;
	private float cameraYaw = 0.0f;

	// tool settings

	private final MessagePrinter printer = new MessagePrinter(this);
	private MessageRenderer renderer;
	private JPanel editorPanel;

	private AssetListTab assetListTab;
	private MessageListTab messageListTab;
	private JTextPane inputTextPane;
	private JTabbedPane tabs;

	// settings panel

	private ByteBuffer[] varBuffers = new ByteBuffer[StringConstants.getMaxStringVars()];
	private BufferedImage varImage;
	public File varImageFile;

	private JCheckBoxMenuItem cbPrintDelay;
	private JCheckBoxMenuItem cbShowViewportGuides;
	private JCheckBoxMenuItem cbShowGrid;
	private JCheckBoxMenuItem cbCulling;
	private JTextField[] varFields;
	private ImagePanel varImagePanel;
	private JTextField imgVarNameField;

	private volatile boolean varsChanged = false;
	private volatile boolean imageChanged = false;

	// current string

	private Message workingString;

	public static final int MAX_TEXTFIELD_CHARACTERS = 4096;
	private StyledDocument inputDocument;

	private AttributeSet attrDefault;
	private AttributeSet attrCurrent;
	private AttributeSet attrTag;
	private AttributeSet attrError;

	private volatile boolean stringChanged = false;
	private volatile boolean textChanged = false;
	private volatile boolean caretChanged = false;

	// undo/redo
	private TextHistory history;
	private volatile boolean settingString = false;
	private volatile boolean syncingString = false;
	private volatile boolean ignoreDocumentContentChanges = false;
	private volatile boolean ignoreDocumentFormatChanges = false;

	// IO
	public ArrayList<MessageAsset> resourcesToSave = new ArrayList<>();

	public static enum PollResult
	{
		OK, MISSING, MODIFIED
	}

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		BaseEditor editor = new MessageEditor();
		editor.launch();
		Environment.exit();
	}

	public MessageEditor()
	{
		super(EDITOR_SETTINGS);

		Config cfg = getConfig();
		if (cfg != null) {
			cbPrintDelay.setSelected(cfg.getBoolean(Options.StrPrintDelay));
			cbShowViewportGuides.setSelected(cfg.getBoolean(Options.StrViewportGuides));
			cbCulling.setSelected(cfg.getBoolean(Options.StrUseCulling));
		}
		else {
			cbPrintDelay.setSelected(true);
			cbShowViewportGuides.setSelected(true);
			cbCulling.setSelected(true);
		}

		readVarImage(MessageBoxes.Graphic.Letter_Peach.getFile());

		assetListTab.fullReload();
		setString(null);

		Logger.log("Loaded string editor.");
	}

	@Override
	public void cleanup(boolean crashed)
	{
		renderer.cleanup();
		getConfig().setBoolean(Options.StrPrintDelay, cbPrintDelay.isSelected());
		getConfig().setBoolean(Options.StrViewportGuides, cbShowViewportGuides.isSelected());
		getConfig().setBoolean(Options.StrUseCulling, cbCulling.isSelected());
	}

	@Override
	protected void update(double deltaTime)
	{
		handleSaving();

		if (varsChanged) {
			for (int i = 0; i < varBuffers.length; i++) {
				String s = varFields[i].getText();
				varBuffers[i] = StringEncoder.encodeVar(s, false);
			}
		}

		if (imageChanged)
			renderer.setVarImage(varImage);

		if (stringChanged || textChanged || caretChanged || varsChanged) {
			int caretPos = inputTextPane.getCaretPosition();
			if (textChanged || varsChanged) {
				printer.setSequences(StringEncoder.encodeText(getInputText(), varBuffers));
				buildStringErrorMessages();
			}
			printer.setCurrentPage(caretPos);

			updateDocumentFormatting();

			stringChanged = false;
			textChanged = false;
			caretChanged = false;
			varsChanged = false;
		}

		handleInput(deltaTime);
		assert (printer != null);
		printer.update();

		if (workingString != null && workingString.editorShouldSync) {
			workingString.editorShouldSync = false;
			setString(workingString);
		}
	}

	@Override
	public void glDraw()
	{
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		RenderState.setViewport(0, 0, glCanvasWidth(), glCanvasHeight());

		renderer.render(mouse, printer, cameraX, cameraY, cameraZoom, cameraYaw);
	}

	private void buildStringErrorMessages()
	{
		if (workingString != null) {
			StringBuilder sb = new StringBuilder();
			for (Sequence seq : printer.sequences) {
				if (seq.hasError()) {
					if (sb.length() > 0)
						sb.append(System.lineSeparator());
					sb.append(seq.getErrorMessage());
				}
			}

			if (sb.length() > 0)
				workingString.setErrorMessage(sb.toString());
			else
				workingString.setErrorMessage("");
		}
	}

	// helper method for properly getting text from the input pane
	private String getInputText()
	{
		try {
			StyledDocument document = inputTextPane.getStyledDocument();
			return document.getText(0, document.getLength());
		}
		catch (BadLocationException e) {
			throw new StarRodException(e);
		}
	}

	private void updateDocumentFormatting()
	{
		ignoreDocumentFormatChanges = true;
		String text = getInputText();

		// clear formatting
		inputDocument.setCharacterAttributes(0, text.length(), attrDefault, true);

		// highlight current page
		if (printer.currentPage != null)
			inputDocument.setCharacterAttributes(printer.currentPage.srcStart,
				printer.currentPage.srcEnd - printer.currentPage.srcStart,
				attrCurrent, false);

		// highlight errors
		for (Sequence seq : printer.sequences) {
			if (seq.hasError())
				inputDocument.setCharacterAttributes(seq.srcStart, seq.srcLen, attrError, false);
			else if (seq.tag)
				inputDocument.setCharacterAttributes(seq.srcStart, seq.srcLen, attrTag, false);
		}

		ignoreDocumentFormatChanges = false;
	}

	private void handleInput(double deltaTime)
	{
		int dw = mouse.getFrameDW();

		// zooming in
		if (dw > 0) {
			cameraZoom *= 0.91f;
			if (cameraZoom < MINIMUM_ZOOM)
				cameraZoom = MINIMUM_ZOOM;
		}

		// zooming out
		if (dw < 0) {
			cameraZoom *= 1.10f;
			if (cameraZoom > MAXIMUM_ZOOM)
				cameraZoom = MAXIMUM_ZOOM;
		}

		float vh = 0;
		float vv = 0;

		// scrolling
		if (keyboard.isKeyDown(KeyEvent.VK_UP))
			vv -= PAN_SPEED * deltaTime * cameraZoom;
		if (keyboard.isKeyDown(KeyEvent.VK_DOWN))
			vv += PAN_SPEED * deltaTime * cameraZoom;
		if (keyboard.isKeyDown(KeyEvent.VK_LEFT))
			vh -= PAN_SPEED * deltaTime * cameraZoom;
		if (keyboard.isKeyDown(KeyEvent.VK_RIGHT))
			vh += PAN_SPEED * deltaTime * cameraZoom;

		cameraX += vh;
		cameraY += vv;

		float maxW = MAXIMUM_ZOOM * glCanvasWidth() / 2;
		float maxH = MAXIMUM_ZOOM * glCanvasHeight() / 2;
		if (cameraX > maxW)
			cameraX = maxW;
		if (cameraX < -maxW)
			cameraX = -maxW;
		if (cameraY > maxH)
			cameraY = maxH;
		if (cameraY < -maxH)
			cameraY = -maxH;

		// reset camera
		if (keyboard.isKeyDown(KeyEvent.VK_SPACE)) {
			cameraX = DEFAULT_CAMERA_X;
			cameraY = printer.windowBasePosY + (printer.windowSizeY / 2);
			cameraZoom = DEFAULT_CAMERA_ZOOM;
			cameraYaw = 0.0f;
		}
	}

	private void handleSaving()
	{
		if (!resourcesToSave.isEmpty()) {
			ArrayList<MessageAsset> saved = new ArrayList<>();

			for (MessageAsset res : resourcesToSave) {
				res.saveChanges();
				saved.add(res);
			}

			if (saved.size() > 1)
				Logger.logf("Saved %d resources.", saved.size());
			else if (saved.size() == 1)
				Logger.log("Saved resource: " + saved.get(0).asset.getName());

			resourcesToSave.clear();
		}
	}

	private ImageIcon getButtonIcon(File imgFile, boolean useInterp) throws IOException
	{
		Tile tile = Tile.load(imgFile, CI_4);
		BufferedImage in = ImageConverter.convertToBufferedImage(tile);

		boolean large = (in.getWidth() < 24);

		int startX = in.getWidth();
		int startY = in.getHeight();
		int endX = 0;
		int endY = 0;

		for (int j = 0; j < in.getHeight(); j++) {
			for (int i = 0; i < in.getWidth(); i++) {
				int alpha = (in.getRGB(i, j) >> 24) & 0xFF;
				if (alpha != 0) {
					if (i < startX)
						startX = i;
					if (i > endX)
						endX = i;

					if (j < startY)
						startY = j;
					if (j > endY)
						endY = j;
				}
				// System.out.printf("%-3d ", alpha);
			}
			// System.out.println();
		}
		// System.out.printf("%d -> %d and %d -> %d%n", startX, endX, startY, endY);

		BufferedImage crop = in.getSubimage(startX, startY, 1 + endX - startX, 1 + endY - startY);

		if (!large)
			return new ImageIcon(crop);

		int newWidth = Math.round(2f * crop.getWidth());
		int newHeight = Math.round(2f * crop.getHeight());
		BufferedImage resized = new BufferedImage(newWidth, newHeight, in.getType());
		Graphics2D g = resized.createGraphics();
		if (useInterp)
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		else
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(crop, 0, 0, newWidth, newHeight, 0, 0, crop.getWidth(), crop.getHeight(), null);
		g.dispose();

		return new ImageIcon(resized);
	}

	protected JPanel createButtonPanel()
	{
		JPanel buttonPanel = new JPanel(new MigLayout("align center, ins n n 0 n"));

		int i = 1;
		for (SpecialCharacter sc : StringConstants.SpecialCharacter.values()) {
			String imgName = String.format("%02X", sc.code) + ".png";
			AssetHandle ah = AssetManager.get(AssetSubdir.STANDARD_CHARS, imgName);

			JButton button = new JButton();
			try {
				button.setIcon(getButtonIcon(ah, false));
				if (sc.name.startsWith("~")) {
					addButtonTextInsert(button, sc.name.substring(1));
				}
				else {
					addButtonTextInsert(button, sc.name);
				}
			}
			catch (IOException e) {
				button.setText(sc.name);
				Logger.logfWarning("Couldn't load image: %s", imgName);
			}

			if (i == 9) {
				buttonPanel.add(button, "w 52!, h 36!");
				button = new JButton("BR");
				SwingUtils.setFontSize(button, 14);
				addButtonTextInsert(button, "BR");
				buttonPanel.add(button, "w 52!, h 36!, wrap");
			}
			else if (i == 19)
				buttonPanel.add(button, "w 52!, h 36!, wrap");
			else
				buttonPanel.add(button, "w 52!, h 36!");

			i++;
		}

		ImageIcon[] paletteIcons = new ImageIcon[0x50];
		for (int palIndex = 0; palIndex < paletteIcons.length; palIndex++) {
			String imgName = String.format("%02X", palIndex) + ".png";
			try {
				File imgFile = AssetManager.get(AssetSubdir.STANDARD_CHARS_PAL, imgName);
				paletteIcons[palIndex] = getButtonIcon(imgFile, false);
			}
			catch (IOException e) {
				Logger.logWarning("Couldn't load image: " + imgName);
			}
		}
		ColorSelectionDialogPanel.setButtonIcons(paletteIcons);

		JButton colorsButton = new JButton("Color");
		colorsButton.addActionListener((e) -> {
			showColorSelectionDialog();
		});

		JSplitButton stylesButton = new JSplitButton("Style");
		stylesButton.setPopupMenu(getStylePopup());
		stylesButton.setAlwaysPopup(true);

		JSplitButton effectsButton = new JSplitButton("Effect");
		effectsButton.setPopupMenu(getEffectsPopup());
		effectsButton.setAlwaysPopup(true);

		JSplitButton functionsButton = new JSplitButton("Function");
		functionsButton.setPopupMenu(getFunctionsPopup());
		functionsButton.setAlwaysPopup(true);

		JSplitButton presetsButton = new JSplitButton("Preset");
		presetsButton.setPopupMenu(getPresetPopup());
		presetsButton.setAlwaysPopup(true);

		buttonPanel.add(stylesButton, "span 2, growx, h 32!");
		buttonPanel.add(colorsButton, "span 2, growx, h 32!");
		buttonPanel.add(effectsButton, "span 2, growx, h 32!");
		buttonPanel.add(functionsButton, "span 2, growx, h 32!");
		buttonPanel.add(presetsButton, "span 2, growx, h 32!");

		return buttonPanel;
	}

	private void showColorSelectionDialog()
	{
		super.incrementDialogsOpen();
		int result = ColorSelectionDialogPanel.showFramedDialog(inputTextPane);
		super.decrementDialogsOpen();
		if (result < 0)
			return;

		String selected = inputTextPane.getSelectedText();
		if (selected != null && !selected.isEmpty())
			inputTextPane.replaceSelection(String.format("[SaveColor][Color:%02X]%s[RestoreColor]", result, inputTextPane.getSelectedText()));
		else
			inputTextPane.replaceSelection(String.format("[Color:%02X]", result));
	}

	private JMenu addMenuCategory(JPopupMenu menu, String name)
	{
		JMenu submenu = new JMenu(name);
		submenu.setPreferredSize(menuItemDimension);

		menu.add(submenu);
		return submenu;
	}

	private void addFunctionMenuItem(JComponent menu, String funcName, String args, String tooltip)
	{
		JMenuItem item = new JMenuItem(funcName);
		item.setPreferredSize(menuItemDimension);

		menu.add(item);
		item.addActionListener((e) -> {
			StringBuilder sb = new StringBuilder();
			sb.append(StringConstants.OPEN_TAG).append(funcName);
			if (!args.isEmpty())
				sb.append(" ").append(args);
			sb.append(StringConstants.CLOSE_TAG);
			inputTextPane.replaceSelection(sb.toString());
		});

		if (!tooltip.isEmpty())
			item.setToolTipText(tooltip);
	}

	private void addStyleMenuItem(JComponent menu, String styleName, String args, String tooltip)
	{
		JMenuItem item = new JMenuItem(styleName);
		item.setPreferredSize(menuItemDimension);

		menu.add(item);
		item.addActionListener((e) -> {
			StringBuilder sb = new StringBuilder();
			sb.append(StringConstants.OPEN_TAG).append("Style ").append(styleName);
			if (!args.isEmpty())
				sb.append(" ").append(args);
			sb.append(StringConstants.CLOSE_TAG);
			inputTextPane.replaceSelection(sb.toString());
		});

		if (!tooltip.isEmpty())
			item.setToolTipText(tooltip);
	}

	private JPopupMenu getStylePopup()
	{
		JPopupMenu menu = new JPopupMenu();
		addStyleMenuItem(menu, StringStyle.RIGHT.name, "", "");
		addStyleMenuItem(menu, StringStyle.LEFT.name, "", "");
		addStyleMenuItem(menu, StringStyle.CENTER.name, "", "");
		addStyleMenuItem(menu, StringStyle.TATTLE.name, "", "Size automatically adjusts to fit largest page.");
		menu.addSeparator();
		addStyleMenuItem(menu, StringStyle.CHOICE.name, "pos=120,90 size=75,46",
			"Standard choice boxes; adjustable size and position.");
		addStyleMenuItem(menu, StringStyle.UPGRADE.name, "pos=40,35 size=240,42",
			"Style used for upgrade block text; adjustable size and position.");
		menu.addSeparator();
		addStyleMenuItem(menu, StringStyle.SIGN.name, "", "Used when reading wooden signs.");
		addStyleMenuItem(menu, StringStyle.LAMPPOST.name, "height=75",
			"Modified sign with adjustable height. Used for street signs in Toad Town.");
		addStyleMenuItem(menu, StringStyle.INSPECT.name, "", "Used when inspecting things.");
		menu.addSeparator();
		addStyleMenuItem(menu, StringStyle.POSTCARD.name, "index=1", "Displays a postcard image with the text.");
		addStyleMenuItem(menu, StringStyle.POPUP.name, "", "Only used to make a popup saying 'You got kooper's shell!'.");
		addStyleMenuItem(menu, StringStyle.NARRATE.name, "", "Used for messages like 'You got X!' or 'Y joined your party!'");
		addStyleMenuItem(menu, StringStyle.EPILOGUE.name, "", "Used for end of chapter text. Centered with no box.");

		// STYLE_B
		// STYLE_F

		return menu;
	}

	private JPopupMenu getFunctionsPopup()
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setLightWeightPopupEnabled(false);

		JMenu posSubmenu = addMenuCategory(menu, "Position");
		addFunctionMenuItem(posSubmenu, StringFunction.PUSH_POS.name, "",
			"Save the current printing position.");
		addFunctionMenuItem(posSubmenu, StringFunction.POP_POS.name, "",
			"Return to the most recently saved printing position.");
		posSubmenu.addSeparator();
		addFunctionMenuItem(posSubmenu, "SetPos", "10,10",
			"Set printing position (X,Y) (relative to box). Negative values are possible for Y.");
		addFunctionMenuItem(posSubmenu, StringFunction.SET_X.name, "10",
			"Set printing X position (relative to box).");
		addFunctionMenuItem(posSubmenu, StringFunction.SET_Y.name, "10",
			"Set printing Y position (relative to box).");
		posSubmenu.addSeparator();
		addFunctionMenuItem(posSubmenu, StringFunction.CENTER_X.name, "160",
			"Center the text at a given X position (or 255 = center of screen).");
		posSubmenu.addSeparator();
		addFunctionMenuItem(posSubmenu, StringFunction.RIGHT.name, "10",
			"Adjust the print position rightward (+x) from current.");
		addFunctionMenuItem(posSubmenu, StringFunction.UP.name, "10",
			"Adjust the print position upward (-y) from current.");
		addFunctionMenuItem(posSubmenu, StringFunction.DOWN.name, "10",
			"Adjust the print position downward (+y) from current.");

		JMenu formatSubmenu = addMenuCategory(menu, "Formatting");
		addFunctionMenuItem(formatSubmenu, StringFunction.FONT.name, StringFont.NORMAL.name,
			"Set font.");
		addFunctionMenuItem(formatSubmenu, StringFunction.VARIANT.name, "0",
			"Set font variation.");
		addFunctionMenuItem(formatSubmenu, StringFunction.SPACING.name, "16",
			"Force characters to be uniformly-spaced (disables kerning). Use 0 to disable.");
		formatSubmenu.addSeparator();
		addFunctionMenuItem(formatSubmenu, StringFunction.COLOR.name, "0xA",
			"Set the text color.");
		addFunctionMenuItem(formatSubmenu, StringFunction.PUSH_COLOR.name, "",
			"Save the current text color.");
		addFunctionMenuItem(formatSubmenu, StringFunction.POP_COLOR.name, "",
			"Use the most recently saved text color.");
		formatSubmenu.addSeparator();
		addFunctionMenuItem(formatSubmenu, StringFunction.SIZE.name, "16",
			"Set text size (H,W) or just (X) for both.");
		addFunctionMenuItem(formatSubmenu, StringFunction.SIZE_RESET.name, "",
			"Reset text size to default (16,16).");

		JMenu inputSubmenu = addMenuCategory(menu, "Input");
		addFunctionMenuItem(inputSubmenu, StringFunction.INPUT_OFF.name, "",
			"Ignore player input.");
		addFunctionMenuItem(inputSubmenu, StringFunction.INPUT_ON.name, "",
			"Allow player input.");
		addFunctionMenuItem(inputSubmenu, StringFunction.SKIP_OFF.name, "",
			"Prevent the player from skipping through the message.");
		inputSubmenu.addSeparator();
		addFunctionMenuItem(inputSubmenu, "RewindOff", "",
			"Prevent the player from viewing previous pages.");
		addFunctionMenuItem(inputSubmenu, "RewindOn", "",
			"Allow the player to view previous pages.");
		inputSubmenu.addSeparator();
		addFunctionMenuItem(inputSubmenu, StringFunction.ENABLE_CDOWN.name, "",
			"Allow advancing to the next page with C-Down in addition to A.");
		addFunctionMenuItem(inputSubmenu, StringFunction.SCROLL.name, "",
			"Scroll up a given number of lines.");
		addFunctionMenuItem(inputSubmenu, StringFunction.YIELD.name, "",
			"Stop printing the message, but leave the box open.");

		JMenu printSubmenu = addMenuCategory(menu, "Printing");
		addFunctionMenuItem(printSubmenu, StringFunction.DELAY_OFF.name, "",
			"Print without delays between each character.");
		addFunctionMenuItem(printSubmenu, StringFunction.DELAY_ON.name, "",
			"Print using a delay between each character.");
		addFunctionMenuItem(printSubmenu, StringFunction.SPEED.name, "delay=2 chars=1",
			"Set the printing speed.");
		printSubmenu.addSeparator();
		addFunctionMenuItem(printSubmenu, StringFunction.VOLUME.name, "percent=75",
			"Set volume for speaking sounds (default=75).");
		addFunctionMenuItem(printSubmenu, StringFunction.VOICE.name, StringVoice.NORMAL.name,
			String.format("Use one of the preset voices ('%s', '%s', or '%s').",
				StringVoice.NORMAL.name, StringVoice.BOWSER.name, StringVoice.STAR.name));
		addFunctionMenuItem(printSubmenu, StringFunction.SETVOICE.name, "soundIDs=0x141,0x142",
			"Use a custom voice by specifying two soundIDs.");

		JMenu graphicsSubmenu = addMenuCategory(menu, "Graphics");
		addFunctionMenuItem(graphicsSubmenu, StringFunction.ITEM_ICON.name, "itemID=0x83",
			"Draw an item icon at the current printing position.");
		addFunctionMenuItem(graphicsSubmenu, StringFunction.INLINE_IMAGE.name, "index=0",
			"Draw an image at the current printing position.");
		addFunctionMenuItem(graphicsSubmenu, StringFunction.IMAGE.name, "index=0 pos=85,97 hasBorder=1 alpha=255 fadeAmount=52",
			"Draw an image at a given screen position.");
		addFunctionMenuItem(graphicsSubmenu, StringFunction.HIDE_IMAGE.name, "fadeAmount=52",
			"Fade out current image. Set fadeAmount=0 to make it vanish instantly.");

		JMenu insertSubmenu = addMenuCategory(menu, "Replacement");
		addFunctionMenuItem(insertSubmenu, StringFunction.VAR.name, "0",
			"Insert the contents of a string variable.");
		addFunctionMenuItem(insertSubmenu, "Const", "example",
			"Insert a constant value from " + Directories.FN_STRING_CONSTANTS);

		return menu;
	}

	private JPopupMenu getEffectsPopup()
	{
		JPopupMenu menu = new JPopupMenu();
		addEffectMenuItem(menu, StringEffect.SHAKE, "", "");
		addEffectMenuItem(menu, StringEffect.BLUR, "dir=xy", "Blur directions can be 'x', 'y', or 'xy'.");
		addEffectMenuItem(menu, StringEffect.DROP_SHADOW, "", "");

		menu.addSeparator();

		addEffectMenuItem(menu, StringEffect.STATIC, "percent=50", "Higher values replace character images with static.");
		addEffectMenuItem(menu, StringEffect.DITHER_FADE, "percent=50", "Lower values fade out text and drop pixels.");
		addEffectMenuItem(menu, StringEffect.NOISE_OUTLINE, "", "");

		menu.addSeparator();

		addEffectMenuItem(menu, StringEffect.WAVE, "", "");
		addEffectMenuItem(menu, StringEffect.GLOBAL_WAVE, "", "Synchonized across all visible messages.");
		addEffectMenuItem(menu, StringEffect.RAINBOW, "", "");
		addEffectMenuItem(menu, StringEffect.GLOBAL_RAINBOW, "", "Synchonized across all visible messages.");

		menu.addSeparator();

		addEffectMenuItem(menu, StringEffect.SIZE_JITTER, "", "");
		addEffectMenuItem(menu, StringEffect.SIZE_WAVE, "", "");

		menu.addSeparator();

		addEffectMenuItem(menu, StringEffect.RISE_PRINT, "", "");
		addEffectMenuItem(menu, StringEffect.GROW_PRINT, "", "");

		return menu;
	}

	private void addEffectMenuItem(JPopupMenu menu, StringEffect effect, String args, String tooltip)
	{
		JMenuItem item = new JMenuItem(effect.name);
		item.setPreferredSize(menuItemDimension);

		if (!tooltip.isEmpty())
			item.setToolTipText(tooltip);

		menu.add(item);
		item.addActionListener((e) -> {
			String start = effect.name;
			if (!args.isEmpty())
				start += " " + args;
			String end = "/fx";
			replaceWithTagPair(start, end);
		});
	}

	private void replaceWithTagPair(String open, String close)
	{
		String current = inputTextPane.getSelectedText();
		if (current == null)
			current = "";
		inputTextPane.replaceSelection(String.format("[%s]%s[%s]", open, current, close));
	}

	private JPopupMenu getPresetPopup()
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem item;

		item = new JMenuItem("Choose 2");
		item.setPreferredSize(menuItemDimension);
		item.addActionListener((evt) -> {
			inputTextPane.replaceSelection(Presets.CHOICE_2);
		});
		menu.add(item);

		item = new JMenuItem("Choose 3");
		item.setPreferredSize(menuItemDimension);
		item.addActionListener((evt) -> {
			inputTextPane.replaceSelection(Presets.CHOICE_3);
		});
		menu.add(item);

		item = new JMenuItem("Choose 4");
		item.setPreferredSize(menuItemDimension);
		item.addActionListener((evt) -> {
			inputTextPane.replaceSelection(Presets.CHOICE_4);
		});
		menu.add(item);

		item = new JMenuItem("Choose 5");
		item.setPreferredSize(menuItemDimension);
		item.addActionListener((evt) -> {
			inputTextPane.replaceSelection(Presets.CHOICE_5);
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Animation");
		item.setPreferredSize(menuItemDimension);
		item.addActionListener((evt) -> {
			inputTextPane.replaceSelection(Presets.ANIMATION);
		});
		menu.add(item);

		return menu;
	}

	private void addButtonTextInsert(JButton button, String s)
	{
		button.setHorizontalTextPosition(SwingConstants.CENTER);
		button.setVerticalTextPosition(SwingConstants.CENTER);
		button.addActionListener((evt) -> {
			String text = OPEN_TAG + s + CLOSE_TAG;
			try {
				inputTextPane.getDocument().insertString(inputTextPane.getCaretPosition(), text, attrDefault);
				inputTextPane.requestFocusInWindow();
			}
			catch (BadLocationException e) {
				Logger.logfWarning("Failed to insert text: " + text);
			}
		});
	}

	@Override
	protected void createGui(JPanel toolPanel, Canvas glCanvas, JMenuBar menuBar, JLabel infoLabel, ActionListener openLogAction)
	{
		StyleContext context = StyleContext.getDefaultStyleContext();
		attrDefault = context.addAttribute(context.getEmptySet(), StyleConstants.Foreground, SwingUtils.getTextColor());
		attrCurrent = context.addAttribute(context.getEmptySet(), StyleConstants.Foreground, SwingUtils.getBlueTextColor());
		attrTag = context.addAttribute(context.getEmptySet(), StyleConstants.Foreground, SwingUtils.getGreenTextColor());
		attrError = context.addAttribute(context.getEmptySet(), StyleConstants.Foreground, SwingUtils.getRedTextColor());

		inputTextPane = new JTextPane() {
			@Override
			public String getToolTipText(MouseEvent event)
			{
				int mousePos = viewToModel2D(event.getPoint());
				if (mousePos == -1)
					return null;

				for (Sequence seq : printer.sequences) {
					if (mousePos < seq.srcStart || seq.srcEnd <= mousePos)
						continue;

					if (seq.hasError())
						return seq.getErrorMessage();
				}

				return null;
			}
		};
		inputTextPane.setToolTipText("");

		inputTextPane.setFont(new Font("monospaced", Font.PLAIN, 16));
		Border innerBorder = BorderFactory.createEmptyBorder(16, 32, 16, 32);
		inputTextPane.setBorder(innerBorder);

		history = new TextHistory(64);

		inputTextPane.setStyledDocument(new DefaultStyledDocument() {
			@Override
			public void insertString(int offs, String str, AttributeSet a) throws BadLocationException
			{
				if ((getLength() + str.length()) <= MAX_TEXTFIELD_CHARACTERS)
					super.insertString(offs, str, a);
				else
					Toolkit.getDefaultToolkit().beep();
			}
		});

		inputDocument = inputTextPane.getStyledDocument();
		inputDocument.addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				textChanged(e);
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				textChanged(e);
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				textChanged(e);
			}

			// NOTE: changedUpdate is fired when highlighted text is deleted, etc.
			// it's also fired on formatting changes! we must watch out for those.
			public void textChanged(DocumentEvent e)
			{
				if (!ignoreDocumentContentChanges && !ignoreDocumentFormatChanges) {
					if (!syncingString)
						history.set(getInputText(), false);

					if (!settingString && workingString != null) {
						if (!syncingString) {
							modified = true;
							workingString.setModified();
						}
						assetListTab.repaint();
						messageListTab.repaint();
					}
				}

				if (!ignoreDocumentFormatChanges) {
					// just notify so we can do work with this outside EDT
					textChanged = true;
				}
			}
		});

		inputTextPane.addCaretListener((e) -> {
			caretChanged = true;
		});

		inputTextPane.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "SHIFT-ENTER");
		inputTextPane.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "ENTER");

		ActionMap actions = inputTextPane.getActionMap();

		actions.put("ENTER", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				inputTextPane.replaceSelection("\n");
			}
		});
		actions.put("SHIFT-ENTER", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				inputTextPane.replaceSelection("[BR]\n");
			}
		});

		addFileMenu(menuBar, openLogAction);
		addEditorMenu(menuBar);
		addOptionsMenu(menuBar);

		messageListTab = new MessageListTab(this);
		assetListTab = new AssetListTab(this, messageListTab);

		tabs = new JTabbedPane();
		createTab(tabs, "Assets", assetListTab);
		createTab(tabs, "Messages", messageListTab);
		createTab(tabs, "Variables", makeVariablesTab());

		// wrap input pane in a scroll pane to let us scroll
		JScrollPane stringScrollPane = new JScrollPane(inputTextPane);
		stringScrollPane.setPreferredSize(new Dimension(400, 10));

		// put the scroll pane in a jpanel with its own layout to provide a suggested size and position
		JPanel inputPane = new JPanel(new MigLayout("fill, ins 0, wrap"));
		inputPane.add(stringScrollPane, "center, w 640, growy");

		editorPanel = new JPanel();
		editorPanel.setLayout(new MigLayout("fill, ins 0, wrap"));

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, glCanvas, inputPane);
		splitPane.setDividerLocation(300);
		editorPanel.add(splitPane, "grow, push");
		editorPanel.add(createButtonPanel(), "growx");

		toolPanel.add(editorPanel, "grow, push");
		toolPanel.add(tabs, "gapleft 8, growy, w 360!, wrap");

		//	toolPanel.add(infoLabel, "growx, gapleft 4, gapbottom 4, span");
	}

	@Override
	public void undoEDT()
	{
		if (history.canUndo()) {
			ignoreDocumentContentChanges = true;
			inputTextPane.setText(history.undo());
			ignoreDocumentContentChanges = false;
		}
		else {
			Toolkit.getDefaultToolkit().beep();
			Logger.log("Can't undo any more.");
		}
	}

	@Override
	public void redoEDT()
	{
		if (history.canRedo()) {
			ignoreDocumentContentChanges = true;
			inputTextPane.setText(history.redo());
			ignoreDocumentContentChanges = false;
		}
		else {
			Toolkit.getDefaultToolkit().beep();
			Logger.log("Can't redo anything.");
		}
	}

	public boolean hasMessage(String name)
	{
		return assetListTab.hasMessage(name);
	}

	private static void createTab(JTabbedPane tabs, String name, Container contents)
	{
		JLabel lbl = SwingUtils.getLabel(name, 12);
		lbl.setPreferredSize(new Dimension(60, 20));
		lbl.setHorizontalAlignment(SwingConstants.CENTER);

		tabs.addTab(null, contents);
		tabs.setTabComponentAt(tabs.getTabCount() - 1, lbl);
	}

	public void openMessagesTab()
	{
		tabs.setSelectedIndex(1);
	}

	private JPanel makeVariablesTab()
	{
		JPanel settingsPanel = new JPanel(new MigLayout("fill, ins 0, wrap", "[10%!][][10%!]"));

		settingsPanel.add(SwingUtils.getLabel("String Variables", 14), "span, growx, gaptop 12, gapbottom 4, wrap");
		varFields = new JTextField[StringConstants.getMaxStringVars()];
		for (int i = 0; i < varFields.length; i++) {
			varFields[i] = new JTextField();
			varFields[i].setMargin(SwingUtils.TEXTBOX_INSETS);
			SwingUtils.addBorderPadding(varFields[i]);
			settingsPanel.add(SwingUtils.getLabel("" + i, SwingConstants.CENTER, 14), "growx");
			settingsPanel.add(varFields[i], "growx, gap, wrap");
			SwingUtils.addChangeListener(varFields[i], (e) -> {
				varsChanged = true;
			});
		}

		varImagePanel = new ImagePanel();
		settingsPanel.add(SwingUtils.getLabel("Image Variable", 14), "span, growx, gaptop 12, gapbottom 4, wrap");

		imgVarNameField = new JTextField();
		imgVarNameField.setEditable(false);
		SwingUtils.addBorderPadding(imgVarNameField);

		File imgDir = new File(Environment.getProjectDirectory(), "assets");
		OpenFileChooser openImageChooser = new OpenFileChooser(imgDir, "Choose Image", "Images", "png");

		JButton chooseImgVarButton = new JButton("Choose");
		chooseImgVarButton.addActionListener((a) -> {
			super.incrementDialogsOpen();
			if (openImageChooser.prompt() == ChooseDialogResult.APPROVE && openImageChooser.getSelectedFile() != null)
				readVarImage(openImageChooser.getSelectedFile());
			super.decrementDialogsOpen();
		});

		JButton getCodeButton = new JButton("Get Patch to Load Image");
		getCodeButton.addActionListener((a) -> {
			if (varImageFile == null || varImage == null) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			String baseName = FilenameUtils.getBaseName(varImageFile.getName());
			baseName = baseName.replaceAll("\\s+", "_").replaceAll("[^\\w]", "");

			JTextArea textArea = new JTextArea(22, 84);
			textArea.setEditable(false);
			final Font currFont = textArea.getFont();
			textArea.setFont(new Font("Courier New", currFont.getStyle(), currFont.getSize()));

			JScrollPane detailScrollPane = new JScrollPane(textArea);
			detailScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

			textArea.append(String.format("%n"));
			textArea.append(String.format("%% place the following in a global patch:%n"));
			textArea.append(String.format("%n"));
			textArea.append(String.format("%% call this function before displaying the string%n"));
			textArea.append(String.format("#export:Function $SetVarImage_%s {%n", baseName));
			textArea.append(String.format("    PUSH      RA%n"));
			textArea.append(String.format("    LI        A0, $VarImageData_%s%n", baseName));
			textArea.append(String.format("    JAL       80125B2C%n"));
			textArea.append(String.format("    NOP%n"));
			textArea.append(String.format("    LI        V0, 2%n"));
			textArea.append(String.format("    JPOP      RA%n"));
			textArea.append(String.format("}%n"));
			textArea.append(String.format("%n"));
			textArea.append(String.format("#new:IntTable $VarImageData_%s {%n", baseName));
			textArea.append(String.format("    $%s_IMG $%s_PAL %d`s %d`s ~TileFmt:CI-4 ~TileDepth:CI-4%n",
				baseName, baseName, varImage.getWidth(), varImage.getHeight()));
			textArea.append(String.format("}%n"));
			textArea.append(String.format("%n"));
			textArea.append(String.format("%% image file must go in $mod/res/%n"));
			textArea.append(String.format("#new:IntTable $%s_IMG { ~RasterFile:CI-4:%s.png }%n", baseName, baseName));
			textArea.append(String.format("#new:IntTable $%s_PAL { ~PaletteFile:CI-4:%s.png }%n", baseName, baseName));
			textArea.append(String.format("%n"));

			super.incrementDialogsOpen();
			int choice = SwingUtils.getOptionDialog()
				.setTitle("Code for Loading Image")
				.setMessage(detailScrollPane)
				.setMessageType(JOptionPane.PLAIN_MESSAGE)
				.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
				.setIcon(Environment.ICON_DEFAULT)
				.setOptions("Copy to Clipboard")
				.choose();
			super.decrementDialogsOpen();

			if (choice == 0) {
				StringSelection stringSelection = new StringSelection(textArea.getText());
				Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
				cb.setContents(stringSelection, null);
			}
		});

		settingsPanel.add(imgVarNameField, "sgy row, skip 1, w 60%!, split 2");
		settingsPanel.add(chooseImgVarButton, "grow, wrap");

		settingsPanel.add(varImagePanel, "skip 1, grow, wrap, gaptop 8, pushy");

		return settingsPanel;
	}

	private void readVarImage(File f)
	{
		if (f.exists()) {
			try {
				varImage = ImageIO.read(f);
				setVarImage(f, varImage);
			}
			catch (IOException e) {
				Logger.logError(e.getMessage());
			}
		}
	}

	private void setVarImage(File f, BufferedImage bimg)
	{
		varImagePanel.setImage(varImage);
		varImageFile = f;
		imgVarNameField.setText(f.getName());
		imageChanged = true;
	}

	public void setString(Message selectedValue)
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (workingString != null && workingString.isModified())
			workingString.setMarkup(getInputText());

		workingString = selectedValue;

		if (workingString != null)
			workingString.editorShouldSync = false;

		SwingUtilities.invokeLater(() -> {
			settingString = true;
			inputTextPane.setText(workingString != null ? workingString.getMarkup() : "");
			inputTextPane.setCaretPosition(0);
			settingString = false;

			history.clear();
			history.set(getInputText(), false);
			stringChanged = true;
		});

		if (workingString != null)
			Logger.log("Editing " + workingString.name);
	}

	public void syncString()
	{
		assert (!SwingUtilities.isEventDispatchThread());

		if (workingString == null)
			return;

		SwingUtilities.invokeLater(() -> {
			syncingString = true;
			inputTextPane.setText(workingString.getMarkup());
			syncingString = false;
		});
	}

	private void addFileMenu(JMenuBar menuBar, ActionListener openLogAction)
	{
		JMenuItem item;
		KeyStroke awtKeyStroke;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "File" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Save");
		awtKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
		item.setAccelerator(awtKeyStroke);
		item.addActionListener((e) -> {
			if (workingString != null && workingString.isModified()) {
				workingString.setMarkup(getInputText());
				resourcesToSave.add(workingString.source);
			}
		});
		menu.add(item);

		item = new JMenuItem("Save All");
		awtKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
		item.setAccelerator(awtKeyStroke);
		item.addActionListener((e) -> {
			assetListTab.saveChanges();
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Open Log");
		item.addActionListener(openLogAction);
		menu.add(item);
		item.setPreferredSize(menuItemDimension);

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

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Editor" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Undo");
		awtKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
		item.setAccelerator(awtKeyStroke);
		item.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(awtKeyStroke, "none");
		item.addActionListener((e) -> {
			undoEDT();
		});
		menu.add(item);
		item.setPreferredSize(menuItemDimension);

		item = new JMenuItem("Redo");
		awtKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
		item.setAccelerator(awtKeyStroke);
		item.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(awtKeyStroke, "none");
		item.addActionListener((e) -> {
			redoEDT();
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("View Tips");
		item.addActionListener((e) -> {
			incrementDialogsOpen();

			SwingUtils.getMessageDialog()
				.setParent(getFrame())
				.setTitle("Message Editor Pro Tips")
				.setMessage(new MessageTipsPanel())
				.setMessageType(JOptionPane.PLAIN_MESSAGE)
				.show();

			decrementDialogsOpen();
		});
		menu.add(item);
	}

	private void addOptionsMenu(JMenuBar menuBar)
	{
		JMenu menu = new JMenu(MENU_BAR_SPACING + "Options" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		cbPrintDelay = new JCheckBoxMenuItem("Enable Printing Delay");
		cbCulling = new JCheckBoxMenuItem("Cull Message Outside Box");
		cbShowViewportGuides = new JCheckBoxMenuItem("Show Viewport Guidelines");
		cbShowGrid = new JCheckBoxMenuItem("Show Grid Guidelines");

		menu.add(cbPrintDelay);
		menu.add(cbCulling);
		menu.add(cbShowViewportGuides);
		menu.add(cbShowGrid);

		cbPrintDelay.setPreferredSize(menuItemDimension);
	}

	@Override
	protected void saveChanges()
	{
		assetListTab.saveChanges();
		handleSaving();
	}

	@Override
	public void glInit()
	{
		try {
			renderer = new MessageRenderer(this);
		}
		catch (Throwable t) {
			StarRodMain.displayStackTrace(t);
			Environment.exit(-1);
		}
	}

	public boolean shouldShowGrid()
	{
		return cbShowGrid.isSelected();
	}

	public boolean shouldShowViewportGuides()
	{
		return cbShowViewportGuides.isSelected();
	}

	public boolean printDelayEnabled()
	{
		return cbPrintDelay.isSelected();
	}

	public boolean isCullingEnabled()
	{
		return cbCulling.isSelected();
	}

	public static class MessageTipsPanel extends JPanel
	{
		public MessageTipsPanel()
		{
			JTextPane tx = new JTextPane();
			tx.setContentType("text/html");
			tx.setText("<html><body>"
				+ "<p>You can quickly insert newline tags with <b>SHIFT + ENTER</b></p>"
				+ "</body></html>");
			tx.setEditable(false);
			add(tx);
		}
	}
}
