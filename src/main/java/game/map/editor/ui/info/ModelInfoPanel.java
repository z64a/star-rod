package game.map.editor.ui.info;

import java.awt.Toolkit;
import java.text.DecimalFormat;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSpinner.NumberEditor;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;

import app.SwingUtils;
import game.map.MapObject.SetObjectName;
import game.map.MapObject.ShapeType;
import game.map.editor.MapEditor;
import game.map.editor.MapInfoPanel;
import game.map.editor.render.RenderMode;
import game.map.editor.render.RenderMode.RenderModeComboBoxRenderer;
import game.map.editor.ui.SwingGUI;
import game.map.shape.LightSet;
import game.map.shape.Model;
import game.map.shape.ModelReplaceType;
import game.map.shape.TexturePanner;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.HexTextField;
import util.ui.ListAdapterComboboxModel;
import util.ui.NameTextField;

public class ModelInfoPanel extends MapInfoPanel<Model>
{
	private static final int DISPLAY_LIST_TAB_INDEX = 1;
	private static final int TRANSFORM_TAB_INDEX = 2;

	private JTabbedPane tabs;

	private NameTextField nameField;
	private JLabel idLabel;

	private JComboBox<ShapeType> shapeTypeBox;

	private JPanel groupPropertiesPanel;
	private JPanel modelPropertiesPanel;

	private JCheckBox prop60Checkbox;
	private HexTextField prop60aField;
	private HexTextField prop60bField;
	private JCheckBox hasMeshCheckbox;

	private JComboBox<RenderMode> renderModeBox;

	private JCheckBox cbAuxProperties;

	private JSpinner auxOffsetSSpinner;
	private JSpinner auxOffsetTSpinner;

	private JComboBox<AuxScale> auxShiftTBox;
	private JComboBox<AuxScale> auxShiftSBox;

	private JComboBox<Integer> presetPannerBox;
	private JComboBox<String> previewScrollUnitBox;

	private JComboBox<ModelReplaceType> replaceTypeBox;

	private JLabel lightSetLabel;
	private JComboBox<LightSet> lightSetComboBox;

	private DisplayListPanel displayListPanel;

	private TransformPanel transformPanel;

	public ModelInfoPanel()
	{
		super(false);

		displayListPanel = DisplayListPanel.instance();
		transformPanel = new TransformPanel();

		tabs = new JTabbedPane();
		tabs.addTab("Model", createGeneralTab());
		tabs.addTab("Display List", displayListPanel);
		tabs.addTab("Transform", transformPanel);

		setLayout(new MigLayout("fill, ins 0"));
		add(tabs, "span, grow, pushy");
	}

	@Override
	public void beforeSetData(Model mdl)
	{
		lightSetComboBox.setModel(new ListAdapterComboboxModel<>(MapEditor.instance().map.lightSets));
	}

	@Override
	public void afterSetData(Model mdl)
	{
		if (mdl == null)
			return;

		idLabel.setText(String.format("0x%X", mdl.getNode().getTreeIndex()));
	}

	public static final String tag_GeneralTab = "GeneralTab";
	public static final String tag_DisplayListTab = "DisplayListTab";
	public static final String tag_TransformTab = "TransformTab";
	public static final String tag_CameraTab = "CameraTab";

	@Override
	public void updateFields(Model mdl, String tag)
	{
		if (getData() == mdl) {
			if (tag.isEmpty()) {
				updateGeneralFields(mdl);
				updateDisplayTab(mdl);
				updateTransformationTab(mdl);
			}
			else if (tag.equalsIgnoreCase(tag_GeneralTab))
				updateGeneralFields(mdl);
			else if (tag.equalsIgnoreCase(tag_DisplayListTab))
				updateDisplayTab(mdl);
			else if (tag.equalsIgnoreCase(tag_TransformTab))
				updateTransformationTab(mdl);
		}

		SwingGUI.instance().repaintObjectPanel();
	}

	private static class AuxScale
	{
		public final int shift;
		public final String name;

		private AuxScale(int value, String name)
		{
			this.name = name;
			this.shift = value;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private static final AuxScale[] AUX_SCALES = new AuxScale[16];
	static {
		AUX_SCALES[0] = new AuxScale(11, "1/32");
		AUX_SCALES[1] = new AuxScale(12, "1/16");
		AUX_SCALES[2] = new AuxScale(13, "1/8");
		AUX_SCALES[3] = new AuxScale(14, "1/4");
		AUX_SCALES[4] = new AuxScale(15, "1/2");
		AUX_SCALES[5] = new AuxScale(0, "1");
		AUX_SCALES[6] = new AuxScale(1, "2");
		AUX_SCALES[7] = new AuxScale(2, "4");
		AUX_SCALES[8] = new AuxScale(3, "8");
		AUX_SCALES[9] = new AuxScale(4, "16");
		AUX_SCALES[10] = new AuxScale(5, "32");
		AUX_SCALES[11] = new AuxScale(6, "64");
		AUX_SCALES[12] = new AuxScale(7, "128");
		AUX_SCALES[13] = new AuxScale(8, "256");
		AUX_SCALES[14] = new AuxScale(9, "512");
		AUX_SCALES[15] = new AuxScale(10, "1024");
	}

	private int getScaleListIndex(int shift)
	{
		if (shift < 0 || shift > 15) {
			Logger.logfError("Tried using invalid aux scale: %X", shift);
			return 5;
		}

		for (int i = 0; i < AUX_SCALES.length; i++) {
			if (AUX_SCALES[i].shift == shift)
				return i;
		}

		throw new IllegalStateException();
	}

	private void updateGeneralFields(Model mdl)
	{
		if (mdl == getData()) {
			nameField.setValue(mdl.getName());

			shapeTypeBox.setSelectedItem(mdl.modelType.get());

			if (mdl.modelType.get() == ShapeType.ROOT) {
				nameField.setEditable(false);
				shapeTypeBox.setEnabled(false);
			}
			else {
				nameField.setEditable(true);
				shapeTypeBox.setEnabled(true);
			}

			if (mdl.modelType.get() == ShapeType.MODEL) {
				groupPropertiesPanel.setVisible(false);
				modelPropertiesPanel.setVisible(true);
			}
			else {
				groupPropertiesPanel.setVisible(true);
				modelPropertiesPanel.setVisible(false);
			}

			// group properties

			lightSetComboBox.setSelectedItem(mdl.lights.get());

			prop60Checkbox.setSelected(mdl.hasProp60.get());
			prop60aField.setValue(mdl.prop60a.get());
			prop60bField.setValue(mdl.prop60b.get());
			prop60aField.setEnabled(mdl.hasProp60.get());
			prop60bField.setEnabled(mdl.hasProp60.get());

			hasMeshCheckbox.setSelected(mdl.hasMesh());

			// leaf properies

			previewScrollUnitBox.setSelectedIndex(mdl.pannerID.get() + 1);

			renderModeBox.setSelectedItem(mdl.renderMode.get());

			cbAuxProperties.setSelected(mdl.hasAuxProperties.get());

			auxOffsetTSpinner.setValue(mdl.auxOffsetT.get() / 4.0);
			auxOffsetSSpinner.setValue(mdl.auxOffsetS.get() / 4.0);
			auxShiftTBox.setSelectedIndex(getScaleListIndex(mdl.auxShiftT.get()));
			auxShiftSBox.setSelectedIndex(getScaleListIndex(mdl.auxShiftS.get()));

			presetPannerBox.setSelectedItem(mdl.defaultPannerID.get());
			replaceTypeBox.setSelectedItem(mdl.replaceWith.get());

			auxOffsetTSpinner.setEnabled(mdl.hasAuxProperties.get());
			auxOffsetSSpinner.setEnabled(mdl.hasAuxProperties.get());
			auxShiftTBox.setEnabled(mdl.hasAuxProperties.get());
			auxShiftSBox.setEnabled(mdl.hasAuxProperties.get());
			presetPannerBox.setEnabled(mdl.hasAuxProperties.get());
			replaceTypeBox.setEnabled(mdl.hasAuxProperties.get());
		}

		SwingGUI.instance().repaintObjectPanel();
	}

	private void updateDisplayTab(Model mdl)
	{
		boolean shouldShow = mdl.hasMesh();
		tabs.setEnabledAt(DISPLAY_LIST_TAB_INDEX, shouldShow);

		if (!shouldShow) {
			if (tabs.getSelectedIndex() == DISPLAY_LIST_TAB_INDEX)
				tabs.setSelectedIndex(0);
			return;
		}

		displayListPanel.setModel(getData());
		revalidate();
	}

	private void updateTransformationTab(Model mdl)
	{
		boolean shouldShow = (mdl.modelType.get() != ShapeType.MODEL);
		tabs.setEnabledAt(TRANSFORM_TAB_INDEX, shouldShow);

		if (!shouldShow) {
			if (tabs.getSelectedIndex() == TRANSFORM_TAB_INDEX)
				tabs.setSelectedIndex(0);
			return;
		}

		transformPanel.setModel(getData());
	}

	private JPanel createGeneralTab()
	{
		idLabel = new JLabel();

		nameField = new NameTextField((newValue) -> {
			MapEditor.execute(new SetObjectName(getData(), newValue));
		});
		SwingUtils.addBorderPadding(nameField);

		shapeTypeBox = new JComboBox<>(ShapeType.values());
		shapeTypeBox.addActionListener((e) -> {
			if (ignoreEvents())
				return;

			ShapeType type = (ShapeType) shapeTypeBox.getSelectedItem();
			boolean allow = true;

			switch (type) {
				case ROOT:
					allow = false;
					break;
				case MODEL:
					if (getData().getNode().getChildCount() != 0)
						allow = false;
					break;
				case GROUP:
					break;
				case SPECIAL:
					break;
			}

			if (allow) {
				MapEditor.execute(getData().modelType.mutator(type));
			}
			else {
				Toolkit.getDefaultToolkit().beep();
				shapeTypeBox.setSelectedItem(getData().modelType.get());
			}
		});
		SwingUtils.addBorderPadding(shapeTypeBox);

		makeGroupPanel();
		makeLeafPanel();

		JPanel tab = new JPanel(new MigLayout("fillx, ins n 16 0 16, hidemode 3"));

		tab.add(new JLabel("Name"), "w 15%!");
		tab.add(nameField, "w 50%!");
		tab.add(new JLabel("ID:", SwingConstants.RIGHT), "pushx, growx");
		tab.add(idLabel, "w 10%!, wrap");

		tab.add(new JLabel("Type"), "w 15%!");
		tab.add(shapeTypeBox, "w 50%!, wrap");

		tab.add(groupPropertiesPanel, "span, grow, wrap, gaptop 8");
		tab.add(modelPropertiesPanel, "span, grow, wrap, gaptop 8");

		return tab;
	}

	private void makeGroupPanel()
	{
		// create components

		lightSetComboBox = new JComboBox<>();
		lightSetComboBox.addActionListener((e) -> {
			if (ignoreEvents())
				return;
			LightSet newLights = (LightSet) lightSetComboBox.getSelectedItem();
			if (newLights != null)
				getData().lights.mutator(newLights);
		});
		SwingUtils.addBorderPadding(lightSetComboBox);

		prop60Checkbox = new JCheckBox();
		prop60Checkbox.addActionListener((e) -> {
			if (!ignoreEvents())
				MapEditor.execute(getData().hasProp60.mutator(prop60Checkbox.isSelected()));
		});

		prop60aField = new HexTextField(2, (newValue) -> {
			if (!ignoreEvents())
				MapEditor.execute(getData().prop60a.mutator(newValue));
		});
		SwingUtils.addBorderPadding(prop60aField);

		prop60bField = new HexTextField(2, (newValue) -> {
			if (!ignoreEvents())
				MapEditor.execute(getData().prop60b.mutator(newValue));
		});
		SwingUtils.addBorderPadding(prop60bField);

		hasMeshCheckbox = new JCheckBox();
		hasMeshCheckbox.addActionListener((e) -> {
			if (!ignoreEvents())
				MapEditor.execute(getData().hasMesh.mutator(hasMeshCheckbox.isSelected()));
		});

		// create panel and do layout

		groupPropertiesPanel = new JPanel(new MigLayout("fill, wrap, ins 0, hidemode 3"));

		lightSetLabel = new JLabel("Lights");
		groupPropertiesPanel.add(lightSetLabel, "w 15%!, split 2");
		groupPropertiesPanel.add(lightSetComboBox, "growx");

		groupPropertiesPanel.add(prop60Checkbox, "split 4");
		groupPropertiesPanel.add(new JLabel("Property 60"), "w 25%");
		groupPropertiesPanel.add(prop60aField, "growx, sg prop60");
		groupPropertiesPanel.add(prop60bField, "growx, sg prop60");

		groupPropertiesPanel.add(hasMeshCheckbox, "split 2");
		groupPropertiesPanel.add(new JLabel("Has Mesh"));
	}

	private void makeLeafPanel()
	{
		// create components

		renderModeBox = new JComboBox<>(RenderMode.getEditorModes());
		renderModeBox.addActionListener((e) -> {
			if (ignoreEvents())
				return;
			RenderMode mode = (RenderMode) renderModeBox.getSelectedItem();
			MapEditor.execute(getData().renderMode.mutator(mode));
		});
		SwingUtils.addBorderPadding(renderModeBox);
		renderModeBox.setRenderer(new RenderModeComboBoxRenderer());

		previewScrollUnitBox = new JComboBox<>(new String[] {
				"None", "0", "1", "2", "3", "4", "5", "6", "7",
				"8", "9", "10", "11", "12", "13", "14", "15" });
		previewScrollUnitBox.addActionListener((e) -> {
			if (!ignoreEvents())
				MapEditor.execute(getData().pannerID.mutator(previewScrollUnitBox.getSelectedIndex() - 1));
		});
		previewScrollUnitBox.setMaximumRowCount(17);
		SwingUtils.centerComboBoxText(previewScrollUnitBox);
		SwingUtils.addBorderPadding(previewScrollUnitBox);

		JButton editButton = new JButton("Edit");
		editButton.addActionListener((e) -> {
			int pannerID = previewScrollUnitBox.getSelectedIndex() - 1;
			if (pannerID < 0)
				return;
			TexturePanner panner = MapEditor.instance().map.scripts.texPanners.get(pannerID);
			if (panner != null)
				SwingGUI.instance().prompt_EditTexPanner(panner);
		});
		SwingUtils.addBorderPadding(editButton);

		cbAuxProperties = new JCheckBox(" Has Special Properties");
		cbAuxProperties.addActionListener((e) -> {
			if (!ignoreEvents())
				MapEditor.execute(getData().hasAuxProperties.mutator(cbAuxProperties.isSelected()));
		});

		NumberEditor editor;
		DecimalFormat format;

		auxOffsetTSpinner = new JSpinner(new SpinnerNumberModel(0, 0.0, 1023.75, 0.25));
		editor = (NumberEditor) auxOffsetTSpinner.getEditor();
		format = editor.getFormat();
		format.setMinimumFractionDigits(2);
		auxOffsetTSpinner.addChangeListener((e) -> {
			if (ignoreEvents())
				return;
			int value = (int) Math.round((Double) auxOffsetTSpinner.getValue() * 4.0);
			MapEditor.execute(getData().auxOffsetT.mutator(value));
		});
		SwingUtils.setFontSize(auxOffsetTSpinner, 12);
		SwingUtils.centerSpinnerText(auxOffsetTSpinner);

		auxOffsetSSpinner = new JSpinner(new SpinnerNumberModel(0, 0.0, 1023.75, 0.25));
		editor = (NumberEditor) auxOffsetSSpinner.getEditor();
		format = editor.getFormat();
		format.setMinimumFractionDigits(2);
		auxOffsetSSpinner.addChangeListener((e) -> {
			if (ignoreEvents())
				return;
			int value = (int) Math.round((Double) auxOffsetSSpinner.getValue() * 4.0);
			MapEditor.execute(getData().auxOffsetS.mutator(value));
		});
		SwingUtils.setFontSize(auxOffsetSSpinner, 12);
		SwingUtils.centerSpinnerText(auxOffsetSSpinner);

		auxShiftTBox = new JComboBox<>(AUX_SCALES);
		auxShiftTBox.addActionListener((e) -> {
			if (ignoreEvents())
				return;
			AuxScale scale = (AuxScale) auxShiftTBox.getSelectedItem();
			MapEditor.execute(getData().auxShiftT.mutator(scale.shift));
		});
		auxShiftTBox.setMaximumRowCount(AUX_SCALES.length);
		SwingUtils.centerComboBoxText(auxShiftTBox);

		auxShiftSBox = new JComboBox<>(AUX_SCALES);
		auxShiftSBox.addActionListener((e) -> {
			if (ignoreEvents())
				return;
			AuxScale scale = (AuxScale) auxShiftSBox.getSelectedItem();
			MapEditor.execute(getData().auxShiftS.mutator(scale.shift));
		});
		auxShiftSBox.setMaximumRowCount(AUX_SCALES.length);
		SwingUtils.centerComboBoxText(auxShiftSBox);

		replaceTypeBox = new JComboBox<>(ModelReplaceType.values());
		replaceTypeBox.addActionListener((e) -> {
			if (ignoreEvents())
				return;
			ModelReplaceType type = (ModelReplaceType) replaceTypeBox.getSelectedItem();
			MapEditor.execute(getData().replaceWith.mutator(type));
		});
		SwingUtils.centerComboBoxText(replaceTypeBox);

		presetPannerBox = new JComboBox<>(new Integer[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 });
		presetPannerBox.addActionListener((e) -> {
			if (ignoreEvents())
				return;
			int newValue = (Integer) presetPannerBox.getSelectedItem();
			MapEditor.execute(getData().defaultPannerID.mutator(newValue));
		});
		presetPannerBox.setMaximumRowCount(16);
		SwingUtils.centerComboBoxText(presetPannerBox);

		// create panel and do layout

		modelPropertiesPanel = new JPanel(new MigLayout("fill, ins 0"));

		modelPropertiesPanel.add(new JLabel("Render Mode"), "w 20%!");
		modelPropertiesPanel.add(renderModeBox, "span, growx, wrap");

		modelPropertiesPanel.add(new JLabel("Tex Panner"), "w 20%!");
		modelPropertiesPanel.add(previewScrollUnitBox, "growx, pushx");
		modelPropertiesPanel.add(editButton, "growx, pushx, wrap");

		modelPropertiesPanel.add(cbAuxProperties, "span, wrap, gaptop 8");

		JPanel specialPropertiesPanel = new JPanel(new MigLayout("fill, ins 12 12 12 12"));

		Border border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
		specialPropertiesPanel.setBorder(border);

		specialPropertiesPanel.add(new JLabel("Aux Offset"), "growx, pushx");
		specialPropertiesPanel.add(auxOffsetSSpinner, "growx");
		specialPropertiesPanel.add(auxOffsetTSpinner, "growx, wrap");

		specialPropertiesPanel.add(new JLabel("Aux Scale"));
		specialPropertiesPanel.add(auxShiftSBox, "growx");
		specialPropertiesPanel.add(auxShiftTBox, "growx, wrap");

		specialPropertiesPanel.add(new JLabel("Replace With"));
		specialPropertiesPanel.add(replaceTypeBox, "growx, span, wrap");

		specialPropertiesPanel.add(new JLabel("Preset Panner"));
		specialPropertiesPanel.add(presetPannerBox, "growx");

		modelPropertiesPanel.add(specialPropertiesPanel, "span");
	}

	public void setLightSetsVisible(boolean debugShowLightSets)
	{
		lightSetLabel.setVisible(debugShowLightSets);
		lightSetComboBox.setVisible(debugShowLightSets);
	}
}
