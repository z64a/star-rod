package app;

import static app.Directories.DATABASE_THEMES;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.LookAndFeel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.formdev.flatlaf.FlatLaf;

import app.Themes.Theme;
import app.config.Options;
import net.miginfocom.swing.MigLayout;

public class ThemeDesignerDialog extends JDialog
{
	private static final int PREVIEW_DELAY = 80;

	private static ThemeDesignerDialog instance;

	private static enum PropertyType
	{
		COLOR,
		INTEGER,
		BOOLEAN,
	}

	private static class ThemeProperty
	{
		private final String group;
		private final String name;
		private final String key;
		private final String[] relatedKeys;
		private final PropertyType type;
		private final int min;
		private final int max;

		private ThemeProperty(String group, String name, String key, PropertyType type, String ... relatedKeys)
		{
			this.group = group;
			this.name = name;
			this.key = key;
			this.relatedKeys = relatedKeys;
			this.type = type;
			this.min = 0;
			this.max = 0;
		}

		private ThemeProperty(String group, String name, String key, int min, int max)
		{
			this.group = group;
			this.name = name;
			this.key = key;
			this.relatedKeys = new String[0];
			this.type = PropertyType.INTEGER;
			this.min = min;
			this.max = max;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private static class ColorSwatchIcon implements Icon
	{
		private static final int SIZE = 16;

		private final Color color;

		private ColorSwatchIcon(Color color)
		{
			this.color = color;
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			graphics.setColor(color);
			graphics.fillRect(x + 1, y + 1, SIZE - 2, SIZE - 2);
			Color borderColor = UIManager.getColor("Component.borderColor");
			graphics.setColor((borderColor == null) ? Color.GRAY : borderColor);
			graphics.drawRect(x, y, SIZE - 1, SIZE - 1);
		}
	}

	private static class ContentWidthScrollPane extends JScrollPane
	{
		private int preferredWidth;

		private ContentWidthScrollPane(Component view)
		{
			super(view);
			setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		}

		@Override
		public Dimension getPreferredSize()
		{
			Dimension size = super.getPreferredSize();
			Component view = getViewport().getView();
			if (view == null)
				return size;

			Insets insets = getInsets();
			int width = view.getPreferredSize().width + insets.left + insets.right + getVerticalScrollBar().getPreferredSize().width;
			Border viewportBorder = getViewportBorder();
			if (viewportBorder != null) {
				Insets viewportInsets = viewportBorder.getBorderInsets(this);
				width += viewportInsets.left + viewportInsets.right;
			}
			preferredWidth = Math.max(preferredWidth, width);
			size.width = Math.max(size.width, preferredWidth);
			return size;
		}
	}

	private static final ThemeProperty[] PROPERTIES = {
			new ThemeProperty("General Colors", "Background", "Panel.background", PropertyType.COLOR,
				"@background", "RootPane.background", "Viewport.background", "ScrollPane.background", "SplitPane.background", "ToolBar.background",
				"OptionPane.background", "ColorChooser.background", "Label.background", "MenuBar.disabledBackground", "Separator.background",
				"CheckBox.background", "RadioButton.background", "ToggleButton.background", "Slider.background"),
			new ThemeProperty("General Colors", "Text", "Label.foreground", PropertyType.COLOR,
				"Button.foreground", "Button.default.foreground", "CheckBox.foreground", "RadioButton.foreground", "ToggleButton.foreground",
				"ComboBox.foreground",
				"Spinner.foreground", "TextField.foreground", "FormattedTextField.foreground", "PasswordField.foreground", "TextArea.foreground",
				"TextPane.foreground", "EditorPane.foreground", "List.foreground", "Tree.foreground", "Table.foreground", "TabbedPane.foreground",
				"MenuBar.foreground", "Menu.foreground", "MenuItem.foreground", "CheckBoxMenuItem.foreground", "RadioButtonMenuItem.foreground",
				"Panel.foreground", "RootPane.foreground", "Viewport.foreground", "ScrollPane.foreground", "PopupMenu.foreground", "ToolBar.foreground",
				"ScrollBar.foreground", "Slider.foreground", "Tree.modifiedItemForeground", "ToolTip.foreground", "TitledBorder.titleColor", "@foreground"),
			new ThemeProperty("General Colors", "Accent", "@accentBaseColor", PropertyType.COLOR,
				"Button.default.background", "Button.default.startBackground", "Button.default.endBackground", "ProgressBar.foreground",
				"Slider.trackValueColor", "Slider.thumbColor"),
			new ThemeProperty("General Colors", "Disabled Background", "Button.disabledBackground", PropertyType.COLOR,
				"ComboBox.disabledBackground", "Spinner.disabledBackground", "TextField.disabledBackground", "FormattedTextField.disabledBackground",
				"PasswordField.disabledBackground", "TextArea.disabledBackground", "TextPane.disabledBackground", "EditorPane.disabledBackground",
				"TextField.inactiveBackground", "FormattedTextField.inactiveBackground", "PasswordField.inactiveBackground", "TextArea.inactiveBackground",
				"TextPane.inactiveBackground", "EditorPane.inactiveBackground",
				"CheckBox.icon.disabledBackground", "CheckBox.icon.disabledSelectedBackground", "@disabledBackground"),
			new ThemeProperty("General Colors", "Disabled Text", "Label.disabledForeground", PropertyType.COLOR,
				"Button.disabledText", "CheckBox.disabledText", "RadioButton.disabledText", "ComboBox.disabledForeground", "Spinner.disabledForeground",
				"TabbedPane.disabledForeground", "TextField.inactiveForeground", "FormattedTextField.inactiveForeground", "PasswordField.inactiveForeground",
				"TextArea.inactiveForeground", "TextPane.inactiveForeground", "EditorPane.inactiveForeground", "ComboBox.buttonDisabledArrowColor",
				"Spinner.buttonDisabledArrowColor", "ScrollBar.buttonDisabledArrowColor", "Menu.icon.disabledArrowColor", "@disabledForeground"),
			new ThemeProperty("General Colors", "Focus", "Component.focusColor", PropertyType.COLOR,
				"Component.focusedBorderColor", "Button.focusedBorderColor", "Button.default.focusColor", "CheckBox.icon.focusColor",
				"CheckBox.icon.focusedBorderColor"),
			//	new ThemeProperty("General Colors", "Links", "Component.linkColor", PropertyType.COLOR),
			new ThemeProperty("General Colors", "Separators", "Separator.foreground", PropertyType.COLOR,
				"ToolBar.separatorColor"),
			new ThemeProperty("General Colors", "Borders", "Component.borderColor", PropertyType.COLOR,
				"Button.borderColor", "Button.startBorderColor", "Button.endBorderColor", "Button.default.borderColor", "Button.default.startBorderColor",
				"Button.default.endBorderColor", "ComboBox.buttonSeparatorColor", "Spinner.buttonSeparatorColor", "PopupMenu.borderColor",
				"MenuBar.borderColor", "RootPane.activeBorderColor", "RootPane.inactiveBorderColor"),
			//	new ThemeProperty("General Colors", "Error Border", "Component.error.focusedBorderColor", PropertyType.COLOR),
			//	new ThemeProperty("General Colors", "Warning Border", "Component.warning.focusedBorderColor", PropertyType.COLOR),
			new ThemeProperty("General Colors", "Icons and Arrows", "Tree.icon.openColor", PropertyType.COLOR,
				"Tree.icon.closedColor", "Tree.icon.leafColor", "Tree.icon.expandedColor", "Tree.icon.collapsedColor", "ComboBox.buttonArrowColor",
				"Spinner.buttonArrowColor", "ScrollBar.buttonArrowColor", "PopupMenu.scrollArrowColor", "Menu.icon.arrowColor", "ToolBar.gripColor",
				"Table.sortIconColor", "@icon"),

			new ThemeProperty("Text Highlights", "Red Text", SwingUtils.UI_KEY_RED_TEXT, PropertyType.COLOR),
			new ThemeProperty("Text Highlights", "Green Text", SwingUtils.UI_KEY_GREEN_TEXT, PropertyType.COLOR),
			new ThemeProperty("Text Highlights", "Blue Text", SwingUtils.UI_KEY_BLUE_TEXT, PropertyType.COLOR),
			new ThemeProperty("Text Highlights", "Grey Text", SwingUtils.UI_KEY_GREY_TEXT, PropertyType.COLOR),

			new ThemeProperty("Selection", "Background", "List.selectionBackground", PropertyType.COLOR,
				"Tree.selectionBackground", "Table.selectionBackground", "ComboBox.selectionBackground", "TextField.selectionBackground",
				"FormattedTextField.selectionBackground", "PasswordField.selectionBackground", "TextArea.selectionBackground", "TextPane.selectionBackground",
				"EditorPane.selectionBackground", "@selectionBackground"),
			new ThemeProperty("Selection", "Text", "List.selectionForeground", PropertyType.COLOR,
				"Tree.selectionForeground", "Table.selectionForeground", "ComboBox.selectionForeground", "TextField.selectionForeground",
				"FormattedTextField.selectionForeground", "PasswordField.selectionForeground", "TextArea.selectionForeground", "TextPane.selectionForeground",
				"EditorPane.selectionForeground", "@selectionForeground"),
			new ThemeProperty("Selection", "Inactive Background", "List.selectionInactiveBackground", PropertyType.COLOR,
				"Tree.selectionInactiveBackground", "Table.selectionInactiveBackground", "@selectionInactiveBackground"),
			new ThemeProperty("Selection", "Inactive Text", "List.selectionInactiveForeground", PropertyType.COLOR,
				"Tree.selectionInactiveForeground", "Table.selectionInactiveForeground", "@selectionInactiveForeground"),
			new ThemeProperty("Selection", "Cell Focus", "List.cellFocusColor", PropertyType.COLOR,
				"Table.cellFocusColor", "Tree.selectionBorderColor", "@cellFocusColor"),

			new ThemeProperty("Buttons", "Fill", "Button.background", PropertyType.COLOR,
				"Button.startBackground", "Button.endBackground", "@buttonBackground"),
			new ThemeProperty("Buttons", "Hover", "Button.hoverBackground", PropertyType.COLOR,
				"Button.default.hoverBackground", "Button.toolbar.hoverBackground"),
			new ThemeProperty("Buttons", "Pressed", "Button.pressedBackground", PropertyType.COLOR,
				"Button.default.pressedBackground", "Button.toolbar.pressedBackground"),
			new ThemeProperty("Buttons", "Selected", "Button.selectedBackground", PropertyType.COLOR,
				"Button.toolbar.selectedBackground", "Button.disabledSelectedBackground"),
			new ThemeProperty("Buttons", "Corner Radius", "Button.arc", 0, 40),
			new ThemeProperty("Buttons", "Minimum Width", "Button.minimumWidth", 0, 200),

			new ThemeProperty("Text Fields", "Background", "TextField.background", PropertyType.COLOR,
				"FormattedTextField.background", "PasswordField.background", "TextArea.background", "TextPane.background", "EditorPane.background",
				"ComboBox.background", "ComboBox.buttonBackground", "ComboBox.buttonEditableBackground", "ComboBox.nonEditableBackground",
				"Spinner.background", "Spinner.buttonBackground", "ProgressBar.background", "@componentBackground"),
			new ThemeProperty("Text Fields", "Corner Radius", "TextComponent.arc", 0, 40),

			new ThemeProperty("Lists", "Background", "List.background", PropertyType.COLOR),
			new ThemeProperty("Lists", "Selection Radius", "List.selectionArc", 0, 40),

			new ThemeProperty("Trees", "Background", "Tree.background", PropertyType.COLOR),
			new ThemeProperty("Trees", "Selection Radius", "Tree.selectionArc", 0, 40),
			new ThemeProperty("Trees", "Row Height", "Tree.rowHeight", 0, 64),

			new ThemeProperty("Tables", "Background", "Table.background", PropertyType.COLOR),
			new ThemeProperty("Tables", "Grid Color", "Table.gridColor", PropertyType.COLOR),
			new ThemeProperty("Tables", "Row Height", "Table.rowHeight", 12, 64),
			new ThemeProperty("Tables", "Horizontal Lines", "Table.showHorizontalLines", PropertyType.BOOLEAN),
			new ThemeProperty("Tables", "Vertical Lines", "Table.showVerticalLines", PropertyType.BOOLEAN),

			new ThemeProperty("Tabs", "Background", "TabbedPane.background", PropertyType.COLOR),
			new ThemeProperty("Tabs", "Text", "TabbedPane.foreground", PropertyType.COLOR),
			new ThemeProperty("Tabs", "Selected Background", "TabbedPane.selectedBackground", PropertyType.COLOR),
			new ThemeProperty("Tabs", "Selected Text", "TabbedPane.selectedForeground", PropertyType.COLOR),
			new ThemeProperty("Tabs", "Underline", "TabbedPane.underlineColor", PropertyType.COLOR,
				"TabbedPane.inactiveUnderlineColor", "TabbedPane.disabledUnderlineColor"),
			new ThemeProperty("Tabs", "Focus", "TabbedPane.focusColor", PropertyType.COLOR),
			new ThemeProperty("Tabs", "Hover", "TabbedPane.hoverColor", PropertyType.COLOR,
				"TabbedPane.buttonHoverBackground", "TabbedPane.buttonPressedBackground"),
			new ThemeProperty("Tabs", "Content Border", "TabbedPane.contentAreaColor", PropertyType.COLOR),
			new ThemeProperty("Tabs", "Height", "TabbedPane.tabHeight", 16, 64),

			new ThemeProperty("Menus", "Background", "MenuBar.background", PropertyType.COLOR,
				"PopupMenu.background", "Menu.background", "MenuItem.background", "CheckBoxMenuItem.background", "RadioButtonMenuItem.background",
				"@menuBackground"),
			new ThemeProperty("Menus", "Selection Background", "MenuItem.selectionBackground", PropertyType.COLOR,
				"Menu.selectionBackground", "CheckBoxMenuItem.selectionBackground", "RadioButtonMenuItem.selectionBackground", "MenuBar.hoverBackground",
				"MenuItem.underlineSelectionBackground"),
			new ThemeProperty("Menus", "Selection Text", "MenuItem.selectionForeground", PropertyType.COLOR,
				"Menu.selectionForeground", "CheckBoxMenuItem.selectionForeground", "RadioButtonMenuItem.selectionForeground"),

			new ThemeProperty("Scrollbars", "Track", "ScrollBar.track", PropertyType.COLOR,
				"ScrollBar.background", "ScrollBar.hoverTrackColor"),
			new ThemeProperty("Scrollbars", "Thumb", "ScrollBar.thumb", PropertyType.COLOR,
				"ScrollBar.hoverThumbColor", "ScrollBar.pressedThumbColor"),
			new ThemeProperty("Scrollbars", "Width", "ScrollBar.width", 6, 40),
			new ThemeProperty("Scrollbars", "Show Buttons", "ScrollBar.showButtons", PropertyType.BOOLEAN),

			new ThemeProperty("Checkboxes and Radios", "Background", "CheckBox.icon.background", PropertyType.COLOR,
				"RadioButton.icon.background", "CheckBox.icon.hoverBackground", "RadioButton.icon.hoverBackground", "CheckBox.icon.pressedBackground",
				"RadioButton.icon.pressedBackground", "CheckBox.icon.focusedBackground", "RadioButton.icon.focusedBackground"),
			new ThemeProperty("Checkboxes and Radios", "Selected Background", "CheckBox.icon.selectedBackground", PropertyType.COLOR,
				"RadioButton.icon.selectedBackground", "CheckBox.icon[filled].selectedBackground", "RadioButton.icon[filled].selectedBackground",
				"CheckBox.icon[filled].hoverSelectedBackground", "RadioButton.icon[filled].hoverSelectedBackground",
				"CheckBox.icon[filled].pressedSelectedBackground", "RadioButton.icon[filled].pressedSelectedBackground"),
			new ThemeProperty("Checkboxes and Radios", "Border", "CheckBox.icon.borderColor", PropertyType.COLOR,
				"RadioButton.icon.borderColor", "CheckBox.icon.selectedBorderColor", "RadioButton.icon.selectedBorderColor",
				"CheckBox.icon[filled].selectedBorderColor", "RadioButton.icon[filled].selectedBorderColor"),
			new ThemeProperty("Checkboxes and Radios", "Mark", "CheckBox.icon.checkmarkColor", PropertyType.COLOR,
				"RadioButton.icon.checkmarkColor", "CheckBox.icon[filled].checkmarkColor", "RadioButton.icon[filled].checkmarkColor"),

			new ThemeProperty("Component Metrics", "Corner Radius", "Component.arc", 0, 40),
			new ThemeProperty("Component Metrics", "Minimum Width", "Component.minimumWidth", 0, 200),
			new ThemeProperty("Component Metrics", "Focus Width", "Component.focusWidth", 0, 10),
	};

	private final Theme initialTheme;
	private Theme baseTheme;
	private final Map<String, String> overrides = new LinkedHashMap<>();
	private File themeFile;
	private boolean keepTheme;
	private boolean ignoreChanges;
	private boolean adjustingColor;
	private boolean pendingColorPreview;

	private final JTextField nameField;
	private final JComboBox<Theme> baseThemeBox;
	private final JTree propertyTree;
	private ThemeProperty selectedProperty;

	private final JLabel propertyNameLabel;
	private final JLabel propertyKeyLabel;
	private final JLabel propertySourceLabel;
	private final JLabel colorValueLabel;
	private final JLabel statusLabel;
	private final JButton resetButton;
	private final JButton saveButton;

	private final CardLayout editorLayout;
	private final JPanel editorCards;
	private final JColorChooser colorChooser;
	private final JSpinner integerSpinner;
	private final JCheckBox booleanCheckBox;
	private final Timer previewTimer;
	private final AWTEventListener mouseListener;

	public static void showDesigner(Component parent)
	{
		ThemesEditor.cancelThemeDialog();
		cancelOpenDesigner();

		instance = new ThemeDesignerDialog(parent);
		instance.setVisible(true);
	}

	static void cancelOpenDesigner()
	{
		if (instance != null && instance.isDisplayable())
			instance.cancel();
	}

	private ThemeDesignerDialog(Component parent)
	{
		super((parent instanceof Window) ? (Window) parent : parent == null ? null : SwingUtilities.getWindowAncestor(parent), "Theme Designer",
			ModalityType.MODELESS);

		initialTheme = Themes.getCurrentTheme();
		if (initialTheme != null && initialTheme.isStarRodTheme()) {
			baseTheme = Themes.getBaseTheme(initialTheme);
			overrides.putAll(initialTheme.overrides);
			if (!initialTheme.fileName.isEmpty())
				themeFile = new File(initialTheme.fileName);
		}
		else if (initialTheme != null && initialTheme.isBuiltIn()) {
			baseTheme = initialTheme;
		}
		else {
			baseTheme = Themes.getBaseTheme(initialTheme);
		}

		String initialName = (initialTheme != null && initialTheme.isStarRodTheme()) ? initialTheme.name : "Custom " + baseTheme.name;
		nameField = new JTextField(initialName);
		SwingUtils.addBorderPadding(nameField);

		baseThemeBox = new JComboBox<>();
		for (Theme theme : Themes.getBaseThemes())
			baseThemeBox.addItem(theme);
		baseThemeBox.setSelectedItem(baseTheme);
		SwingUtils.addBorderPadding(baseThemeBox);

		propertyTree = createPropertyTree();
		propertyTree.addTreeSelectionListener(e -> {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) propertyTree.getLastSelectedPathComponent();
			if (node != null && node.getUserObject() instanceof ThemeProperty)
				showProperty((ThemeProperty) node.getUserObject());
		});

		propertyNameLabel = SwingUtils.getLabel("Select a property", 16);
		propertyKeyLabel = new JLabel(" ");
		propertySourceLabel = new JLabel(" ");
		colorValueLabel = new JLabel(" ");
		statusLabel = new JLabel("Changes are previewed live in every open editor.");

		resetButton = new JButton("Reset");
		resetButton.setEnabled(false);
		resetButton.addActionListener(e -> resetSelectedProperty());

		colorChooser = new JColorChooser();
		colorChooser.setPreviewPanel(new JPanel());
		colorChooser.getSelectionModel().addChangeListener(e -> colorChanged());

		integerSpinner = new JSpinner();
		integerSpinner.addChangeListener(e -> integerChanged());

		booleanCheckBox = new JCheckBox("Enabled");
		booleanCheckBox.addActionListener(e -> booleanChanged());

		editorLayout = new CardLayout();
		editorCards = new JPanel(editorLayout);
		editorCards.add(new JPanel(), "NONE");
		editorCards.add(createColorEditor(), PropertyType.COLOR.name());
		editorCards.add(createIntegerEditor(), PropertyType.INTEGER.name());
		editorCards.add(createBooleanEditor(), PropertyType.BOOLEAN.name());

		previewTimer = new Timer(PREVIEW_DELAY, e -> applyPreview());
		previewTimer.setRepeats(false);
		mouseListener = event -> mouseEvent((MouseEvent) event);
		Toolkit.getDefaultToolkit().addAWTEventListener(mouseListener, java.awt.AWTEvent.MOUSE_EVENT_MASK);

		baseThemeBox.addActionListener(e -> baseThemeChanged());

		saveButton = new JButton("Save");
		saveButton.setEnabled(themeFile != null);
		saveButton.addActionListener(e -> saveTheme(themeFile));

		JButton saveAsButton = new JButton("Save As...");
		saveAsButton.addActionListener(e -> saveThemeAs());

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> cancel());

		JPanel settingsPanel = new JPanel(new MigLayout("fillx, ins 0", "[][grow][][grow]"));
		settingsPanel.add(new JLabel("Name"));
		settingsPanel.add(nameField, "growx");
		settingsPanel.add(new JLabel("Base Theme"));
		settingsPanel.add(baseThemeBox, "growx");

		JScrollPane treeScrollPane = new ContentWidthScrollPane(propertyTree);

		JPanel propertyPanel = new JPanel(new MigLayout("fill, wrap, ins 12"));
		propertyPanel.add(propertyNameLabel, "split 2, growx, pushx");
		propertyPanel.add(resetButton);
		propertyPanel.add(propertyKeyLabel);
		propertyPanel.add(propertySourceLabel, "gapbottom 8");
		propertyPanel.add(editorCards, "grow, push");

		JPanel propertiesPanel = new JPanel(new MigLayout("fill, ins 0", "[270!][grow]"));
		propertiesPanel.add(treeScrollPane, "grow");
		propertiesPanel.add(propertyPanel, "grow, push");

		JPanel buttonPanel = new JPanel(new MigLayout("ins 0", "[grow]push[][][][]"));
		buttonPanel.add(statusLabel, "growx");
		buttonPanel.add(saveButton);
		buttonPanel.add(saveAsButton);
		buttonPanel.add(cancelButton);

		setLayout(new MigLayout("fill, wrap, ins 12"));
		add(settingsPanel, "growx");
		add(propertiesPanel, "grow, push");
		add(buttonPanel, "growx");

		setIconImage(Environment.getDefaultIconImage());
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				cancel();
			}

			@Override
			public void windowClosed(WindowEvent e)
			{
				Toolkit.getDefaultToolkit().removeAWTEventListener(mouseListener);
				previewTimer.stop();
				if (!keepTheme)
					Themes.setTheme(initialTheme);
				if (instance == ThemeDesignerDialog.this)
					instance = null;
			}
		});

		setMinimumSize(new Dimension(800, 600));
		pack();
		setResizable(true);
		setLocationRelativeTo(parent);
		selectFirstProperty();
		applyPreview();
		showProperty(selectedProperty);
	}

	private JTree createPropertyTree()
	{
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Theme Properties");
		Map<String, DefaultMutableTreeNode> groups = new LinkedHashMap<>();
		for (ThemeProperty property : PROPERTIES) {
			DefaultMutableTreeNode group = groups.get(property.group);
			if (group == null) {
				group = new DefaultMutableTreeNode(property.group);
				groups.put(property.group, group);
				root.add(group);
			}
			group.add(new DefaultMutableTreeNode(property));
		}

		JTree tree = new JTree(new DefaultTreeModel(root));
		tree.setCellRenderer(new DefaultTreeCellRenderer() {
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
				boolean hasFocus)
			{
				super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
				if (node.getUserObject() instanceof ThemeProperty) {
					ThemeProperty property = (ThemeProperty) node.getUserObject();
					if (property.type == PropertyType.COLOR) {
						Object propertyValue = getCurrentValue(property);
						Color color = (propertyValue instanceof Color) ? (Color) propertyValue : Color.BLACK;
						setIcon(new ColorSwatchIcon(color));
					}
					else {
						setIcon(null);
					}
				}
				return this;
			}
		});
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		for (int i = 0; i < tree.getRowCount(); i++)
			tree.expandRow(i);
		return tree;
	}

	private JPanel createColorEditor()
	{
		JPanel panel = new JPanel(new MigLayout("fill, wrap, ins 0"));
		panel.add(colorValueLabel, "growx, pushx");
		panel.add(colorChooser, "grow, push");
		return panel;
	}

	private JPanel createIntegerEditor()
	{
		JPanel panel = new JPanel(new MigLayout("fillx, ins 0"));
		panel.add(new JLabel("Value"));
		panel.add(integerSpinner, "w 120!");
		return panel;
	}

	private JPanel createBooleanEditor()
	{
		JPanel panel = new JPanel(new MigLayout("fillx, ins 0"));
		panel.add(booleanCheckBox);
		return panel;
	}

	private void selectFirstProperty()
	{
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) propertyTree.getModel().getRoot();
		DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getFirstChild();
		DefaultMutableTreeNode property = (DefaultMutableTreeNode) group.getFirstChild();
		propertyTree.setSelectionPath(new TreePath(property.getPath()));
	}

	private void showProperty(ThemeProperty property)
	{
		selectedProperty = property;
		propertyNameLabel.setText(property.name);
		propertyKeyLabel.setText(property.relatedKeys.length == 0 ? property.key : property.key + " and " + property.relatedKeys.length + " related defaults");
		propertySourceLabel.setText(hasOverride(property) ? "Custom value" : "Inherited from " + baseTheme.name);
		resetButton.setEnabled(hasOverride(property));

		ignoreChanges = true;
		Object value = getCurrentValue(property);
		switch (property.type) {
			case COLOR:
				Color color = (value instanceof Color) ? (Color) value : Color.BLACK;
				colorChooser.setColor(color);
				colorValueLabel.setText(toColorString(color));
				break;
			case INTEGER:
				int intValue = (value instanceof Number) ? ((Number) value).intValue() : property.min;
				intValue = Math.max(property.min, Math.min(property.max, intValue));
				integerSpinner.setModel(new SpinnerNumberModel(intValue, property.min, property.max, 1));
				break;
			case BOOLEAN:
				booleanCheckBox.setSelected(value instanceof Boolean && (Boolean) value);
				break;
		}
		ignoreChanges = false;
		editorLayout.show(editorCards, property.type.name());
	}

	private Object getCurrentValue(ThemeProperty property)
	{
		try {
			String override = overrides.get(property.key);
			if (override == null) {
				for (String relatedKey : property.relatedKeys) {
					override = overrides.get(relatedKey);
					if (override != null)
						break;
				}
			}
			if (override != null)
				return FlatLaf.parseDefaultsValue(property.key, override, getValueClass(property.type));
			if (property.key.startsWith("@"))
				return FlatLaf.parseDefaultsValue(property.key, property.key, getValueClass(property.type));
			Object value = UIManager.get(property.key);
			if (value == null)
				value = getStarRodDefault(property.key);
			return value;
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	private Color getStarRodDefault(String key)
	{
		if (SwingUtils.UI_KEY_RED_TEXT.equals(key))
			return SwingUtils.getRedTextColor();
		if (SwingUtils.UI_KEY_GREEN_TEXT.equals(key))
			return SwingUtils.getGreenTextColor();
		if (SwingUtils.UI_KEY_BLUE_TEXT.equals(key))
			return SwingUtils.getBlueTextColor();
		if (SwingUtils.UI_KEY_GREY_TEXT.equals(key))
			return SwingUtils.getGrayTextColor();
		return null;
	}

	private Class<?> getValueClass(PropertyType type)
	{
		switch (type) {
			case COLOR:
				return Color.class;
			case INTEGER:
				return Integer.class;
			case BOOLEAN:
				return Boolean.class;
		}
		return null;
	}

	private boolean hasOverride(ThemeProperty property)
	{
		if (overrides.containsKey(property.key))
			return true;
		for (String relatedKey : property.relatedKeys) {
			if (overrides.containsKey(relatedKey))
				return true;
		}
		return false;
	}

	private void setOverride(ThemeProperty property, String value)
	{
		overrides.put(property.key, value);
		for (String relatedKey : property.relatedKeys)
			overrides.put(relatedKey, value);
	}

	private void clearOverride(ThemeProperty property)
	{
		overrides.remove(property.key);
		for (String relatedKey : property.relatedKeys)
			overrides.remove(relatedKey);
	}

	private void colorChanged()
	{
		if (ignoreChanges || selectedProperty == null || selectedProperty.type != PropertyType.COLOR)
			return;
		Color color = colorChooser.getColor();
		setOverride(selectedProperty, toColorString(color));
		colorValueLabel.setText(toColorString(color));
		pendingColorPreview = adjustingColor;
		propertyChanged();
	}

	private void mouseEvent(MouseEvent event)
	{
		if (!(event.getSource() instanceof Component))
			return;
		Component source = (Component) event.getSource();

		if (!SwingUtilities.isDescendingFrom(source, colorChooser))
			return;

		if (event.getID() == MouseEvent.MOUSE_PRESSED) {
			adjustingColor = true;
			previewTimer.stop();
		}
		else if (event.getID() == MouseEvent.MOUSE_RELEASED) {
			adjustingColor = false;
			if (pendingColorPreview) {
				pendingColorPreview = false;
				previewTimer.restart();
			}
		}
	}

	private void integerChanged()
	{
		if (ignoreChanges || selectedProperty == null || selectedProperty.type != PropertyType.INTEGER)
			return;
		setOverride(selectedProperty, integerSpinner.getValue().toString());
		propertyChanged();
	}

	private void booleanChanged()
	{
		if (ignoreChanges || selectedProperty == null || selectedProperty.type != PropertyType.BOOLEAN)
			return;
		setOverride(selectedProperty, Boolean.toString(booleanCheckBox.isSelected()));
		propertyChanged();
	}

	private void propertyChanged()
	{
		propertySourceLabel.setText("Custom value");
		resetButton.setEnabled(true);
		statusLabel.setForeground(null);
		statusLabel.setText("Updating preview...");
		propertyTree.repaint();
		if (!adjustingColor)
			previewTimer.restart();
	}

	private void resetSelectedProperty()
	{
		if (selectedProperty == null)
			return;
		clearOverride(selectedProperty);
		previewTimer.stop();
		applyPreview();
		showProperty(selectedProperty);
		propertyTree.repaint();
	}

	private void baseThemeChanged()
	{
		if (ignoreChanges)
			return;
		baseTheme = (Theme) baseThemeBox.getSelectedItem();
		previewTimer.stop();
		applyPreview();
		if (selectedProperty != null)
			showProperty(selectedProperty);
		propertyTree.repaint();
	}

	private boolean applyPreview()
	{
		boolean success = Themes.previewTheme(nameField.getText(), baseTheme, overrides, this);
		refreshDesignerUI();
		if (success) {
			statusLabel.setForeground(null);
			statusLabel.setText("Previewing " + overrides.size() + " custom properties live.");
		}
		else {
			statusLabel.setForeground(SwingUtils.getRedTextColor());
			statusLabel.setText("Could not apply this preview; the previous theme was restored.");
		}
		propertyTree.repaint();
		return success;
	}

	private void refreshDesignerUI()
	{
		boolean wasIgnoringChanges = ignoreChanges;
		ignoreChanges = true;
		refreshComponentUI(this);
		ignoreChanges = wasIgnoringChanges;
		revalidate();
		repaint();
	}

	private void refreshComponentUI(Component component)
	{
		if (component == colorChooser) {
			LookAndFeel.installColorsAndFont(colorChooser, "ColorChooser.background", "ColorChooser.foreground", "ColorChooser.font");
			LookAndFeel.installBorder(colorChooser, "ColorChooser.border");
		}
		else if (component instanceof JComponent) {
			((JComponent) component).updateUI();
		}

		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents())
				refreshComponentUI(child);
		}
	}

	private void saveThemeAs()
	{
		JFileChooser chooser = new JFileChooser(DATABASE_THEMES.toFile());
		chooser.setDialogTitle("Save Star Rod Theme");
		chooser.setFileFilter(new FileNameExtensionFilter("Star Rod theme (*" + Themes.STAR_ROD_THEME_SUFFIX + ")", "json"));
		chooser.setSelectedFile(new File(DATABASE_THEMES.toFile(), getSafeFileName(nameField.getText()) + Themes.STAR_ROD_THEME_SUFFIX));

		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return;

		File file = chooser.getSelectedFile();
		if (!file.getName().endsWith(Themes.STAR_ROD_THEME_SUFFIX))
			file = new File(file.getParentFile(), file.getName() + Themes.STAR_ROD_THEME_SUFFIX);
		if (file.exists()) {
			int choice = JOptionPane.showConfirmDialog(this, "Overwrite " + file.getName() + "?", "Save Theme", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.YES_OPTION)
				return;
		}

		saveTheme(file);
	}

	private void saveTheme(File file)
	{
		if (file == null) {
			saveThemeAs();
			return;
		}
		previewTimer.stop();
		if (!applyPreview())
			return;

		try {
			Theme savedTheme = Themes.saveStarRodTheme(file, nameField.getText(), baseTheme, overrides);
			Themes.setTheme(savedTheme);
			Environment.mainConfig.setString(Options.Theme, Themes.getConfigKey(savedTheme));
			Environment.mainConfig.saveConfigFile();
			themeFile = file;
			keepTheme = true;
			dispose();
		}
		catch (IOException | RuntimeException e) {
			SwingUtils.getErrorDialog()
				.setParent(this)
				.setTitle("Save Theme Failed")
				.setMessage(e.getMessage())
				.show();
		}
	}

	private void cancel()
	{
		previewTimer.stop();
		Themes.setTheme(initialTheme);
		keepTheme = true;
		dispose();
	}

	private static String toColorString(Color color)
	{
		return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
	}

	private static String getSafeFileName(String name)
	{
		String safeName = name.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
		return safeName.isEmpty() ? "Custom_Theme" : safeName;
	}
}
