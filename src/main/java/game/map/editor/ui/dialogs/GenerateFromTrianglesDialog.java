package game.map.editor.ui.dialogs;

import java.awt.event.WindowEvent;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;

import app.SwingUtils;
import game.map.Axis;
import game.map.editor.MapEditor;
import game.map.editor.geometry.FromTrianglesGenerator;
import game.map.editor.geometry.FromTrianglesGenerator.GeneratorType;
import game.map.editor.render.PreviewDrawMode;
import game.map.editor.render.PreviewGeneratorFromTriangles;
import game.map.mesh.Triangle;
import game.map.shape.TriangleBatch;
import game.map.tree.MapObjectNode;
import net.miginfocom.swing.MigLayout;
import util.ui.DialogResult;

public class GenerateFromTrianglesDialog extends JDialog
{
	public static final String FRAME_TITLE = "Generate From Selection Options";

	private final PreviewGeneratorFromTriangles preview;

	private GeneratorType generator = GeneratorType.MESH;
	private final JComboBox<GeneratorType> generatorBox;

	private final SliderSpinner thresholdSpinner;
	private final SliderSpinner heightSlider;
	private final JLabel axisLabel;
	private final JComboBox<Axis> axisComboBox;

	public GenerateFromTrianglesDialog(JFrame parent, PreviewGeneratorFromTriangles preview,
		BiConsumer<DialogResult, TriangleBatch> onCloseCallback)
	{
		super(parent);
		this.preview = preview;
		preview.drawMode = PreviewDrawMode.FILLED;
		preview.useDepth = true;

		preview.setUpdate(() -> {
			// full update every frame
			preview.triangles = MapEditor.instance().selectionManager.getTrianglesFromSelection();
			updatePreview();
		});

		JComboBox<PreviewDrawMode> drawModeBox = new JComboBox<>(PreviewDrawMode.values());
		drawModeBox.setSelectedItem(preview.drawMode);
		drawModeBox.addActionListener((e) -> {
			if (preview != null)
				preview.drawMode = (PreviewDrawMode) drawModeBox.getSelectedItem();
		});

		JCheckBox cbUseDepth = new JCheckBox("Depth");
		cbUseDepth.addActionListener((e) -> {
			if (preview != null)
				preview.useDepth = cbUseDepth.isSelected();
		});
		cbUseDepth.setIconTextGap(8);

		JButton selectButton = new JButton("OK");
		SwingUtils.addBorderPadding(selectButton);
		selectButton.addActionListener((e) -> {
			onCloseCallback.accept(DialogResult.ACCEPT, preview.batch);
			preview.clear();
			setVisible(false);
		});

		JButton cancelButton = new JButton("Cancel");
		SwingUtils.addBorderPadding(cancelButton);
		cancelButton.addActionListener((e) -> {
			onCloseCallback.accept(DialogResult.CANCEL, null);
			preview.clear();
			setVisible(false);
		});

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				onCloseCallback.accept(DialogResult.CANCEL, null);
				preview.clear();
				setVisible(false);
			}
		});

		JLabel generatorLabel = new JLabel("Generator");
		SwingUtils.setFontSize(generatorLabel, 12);

		heightSlider = new SliderSpinner("Height", 1, 200, 64);
		heightSlider.addChangeListener((e) -> updatePreview());

		thresholdSpinner = new SliderSpinner("Threshold", 1, 500, 50);
		thresholdSpinner.addChangeListener((e) -> updatePreview());

		axisLabel = new JLabel("Projection Axis");
		axisLabel.setFont(axisLabel.getFont().deriveFont(12f));

		axisComboBox = new JComboBox<>(Axis.values());
		axisComboBox.setSelectedItem(Axis.Y);
		axisComboBox.addActionListener((e) -> updatePreview());

		heightSlider.setVisible(generator == GeneratorType.CONCAVE_HULL || generator == GeneratorType.CONVEX_HULL);
		thresholdSpinner.setVisible(generator == GeneratorType.CONCAVE_HULL);
		axisLabel.setVisible(generator == GeneratorType.PROJECTION);
		axisComboBox.setVisible(generator == GeneratorType.PROJECTION);

		generatorBox = new JComboBox<>(GeneratorType.values());
		generatorBox.setSelectedItem(generator);
		generatorBox.addActionListener((e) -> {
			generator = (GeneratorType) generatorBox.getSelectedItem();
			heightSlider.setVisible(generator == GeneratorType.CONCAVE_HULL || generator == GeneratorType.CONVEX_HULL);
			thresholdSpinner.setVisible(generator == GeneratorType.CONCAVE_HULL);

			boolean hasAxisControl = (generator == GeneratorType.PROJECTION);
			axisLabel.setVisible(hasAxisControl);
			axisComboBox.setVisible(hasAxisControl);

			pack();
			updatePreview();
		});

		setLayout(new MigLayout("ins 16, fill, hidemode 3, wrap"));

		add(new JLabel("Draw Mode"), "split 3, w 25%!");
		add(drawModeBox, "growx");
		add(cbUseDepth, "growx, wrap, gapbottom 4");
		add(new JSeparator(), "growx, wrap, gapbottom 8");

		add(generatorLabel, "w 60!, split 2");
		add(generatorBox, "growx, wrap, gapbottom 8");
		add(heightSlider, "span, growx");
		add(thresholdSpinner, "span, growx");

		add(axisLabel, "w 40%, split 2");
		add(axisComboBox, "growx");

		add(new JLabel(), "gaptop 16, growx, sg but, split 3");
		add(selectButton, "gaptop 16, growx, sg but");
		add(cancelButton, "gaptop 16, growx, sg but");

		pack();
		setResizable(false);
	}

	public void beginPreview(MapObjectNode<?> parentObj)
	{
		preview.init();
		preview.visible = true;
		preview.parentObj = parentObj;
		updatePreview();
	}

	private void updatePreview()
	{
		preview.batch = generateTriangles(preview.triangles);
	}

	public String getTypeName()
	{
		GeneratorType type = (GeneratorType) generatorBox.getSelectedItem();
		return type.objectName();
	}

	public TriangleBatch generateTriangles(List<Triangle> triangles)
	{
		GeneratorType type = (GeneratorType) generatorBox.getSelectedItem();
		switch (type) {
			case CONCAVE_HULL:
				return FromTrianglesGenerator.getConcaveHull(triangles, thresholdSpinner.getValue(), heightSlider.getValue());
			case CONVEX_HULL:
				return FromTrianglesGenerator.getConvexHull(triangles, heightSlider.getValue());
			case MESH:
				return FromTrianglesGenerator.getMesh(triangles);
			case FLOOR:
				return FromTrianglesGenerator.getFloor(triangles);
			case WALLS:
				return FromTrianglesGenerator.getWall(triangles);
			case PROJECTION:
				return FromTrianglesGenerator.getProjected(triangles, (Axis) axisComboBox.getSelectedItem());
			default:
				throw new UnsupportedOperationException("Unknown collider type: " + type);
		}
	}

	private static final class SliderSpinner extends JComponent
	{
		private JSpinner spinner;
		private JSlider slider;
		private boolean supressEvents = false;

		public int getValue()
		{
			return slider.getValue();
		}

		public SliderSpinner(String name, int minValue, int maxValue, int initialValue)
		{
			slider = new JSlider(minValue, maxValue, initialValue);
			slider.setMajorTickSpacing(0);
			slider.setMinorTickSpacing(0);
			slider.setPaintTicks(true); // necessary for windows LAF

			spinner = new JSpinner();
			spinner.setFont(spinner.getFont().deriveFont(12f));

			SpinnerModel model = new SpinnerNumberModel(initialValue, minValue, maxValue, 1);
			spinner.setModel(model);

			spinner.addChangeListener(evt -> {
				if (supressEvents)
					return;

				supressEvents = true;
				slider.setValue((Integer) spinner.getValue());
				supressEvents = false;
			});

			slider.addChangeListener(evt -> {
				if (supressEvents)
					return;

				supressEvents = true;
				spinner.setValue(slider.getValue());
				supressEvents = false;
			});

			JLabel label = new JLabel(name);
			SwingUtils.setFontSize(label, 12);

			setLayout(new MigLayout("insets 0"));
			add(label, "w 60!");
			add(slider);
			add(spinner, "w 60!");
		}

		public void addChangeListener(ChangeListener l)
		{
			slider.addChangeListener(l);
		}
	}
}
