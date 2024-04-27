package game.map.editor.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.WindowEvent;
import java.util.function.BiConsumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;
import javax.swing.WindowConstants;

import game.map.editor.geometry.Vector3f;

import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.editor.geometry.primitive.BeveledCubeGenerator;
import game.map.editor.geometry.primitive.ConeGenerator;
import game.map.editor.geometry.primitive.CubeGenerator;
import game.map.editor.geometry.primitive.CylinderGenerator;
import game.map.editor.geometry.primitive.HemisphereGenerator;
import game.map.editor.geometry.primitive.PlaneGenerator;
import game.map.editor.geometry.primitive.RadialGridGenerator;
import game.map.editor.geometry.primitive.RingGenerator;
import game.map.editor.geometry.primitive.ShapeGenerator;
import game.map.editor.geometry.primitive.ShapeGenerator.Primitive;
import game.map.editor.geometry.primitive.SphereGenerator;
import game.map.editor.geometry.primitive.SpiralRampGenerator;
import game.map.editor.geometry.primitive.SpiralStairGenerator;
import game.map.editor.geometry.primitive.StairGenerator;
import game.map.editor.geometry.primitive.TorusGenerator;
import game.map.editor.render.PreviewDrawMode;
import game.map.editor.render.PreviewGeneratorPrimitive;
import game.map.editor.render.PreviewOriginMode;
import game.map.editor.selection.Selection;
import game.map.shape.TriangleBatch;
import game.map.tree.MapObjectNode;
import net.miginfocom.swing.MigLayout;
import util.ui.DialogResult;
import util.ui.LabeledIntegerSpinner;

public class GeneratePrimitiveOptionsDialog extends JDialog
{
	public static final String FRAME_TITLE = "Generate Primitive Options";
	private static final Primitive DEFAULT_TYPE = Primitive.CUBE;

	private Primitive primitiveType = DEFAULT_TYPE;

	private final PreviewGeneratorPrimitive preview;

	private final ShapeGenerator cube = new CubeGenerator();
	private final ShapeGenerator cubeBevel = new BeveledCubeGenerator();
	private final ShapeGenerator plane = new PlaneGenerator();
	private final ShapeGenerator web = new RadialGridGenerator();
	private final ShapeGenerator cylinder = new CylinderGenerator();
	private final ShapeGenerator cone = new ConeGenerator();
	private final ShapeGenerator ring = new RingGenerator();
	private final ShapeGenerator sphere = new SphereGenerator();
	private final ShapeGenerator hemisphere = new HemisphereGenerator();
	private final ShapeGenerator torus = new TorusGenerator();
	private final ShapeGenerator stair = new StairGenerator(this);
	private final ShapeGenerator spiralStair = new SpiralStairGenerator(this);
	private final ShapeGenerator spiralRamp = new SpiralRampGenerator();

	private ShapeGenerator getGenerator()
	{
		switch (primitiveType) {
			case PLANAR_GRID:
				return plane;
			case RADIAL_GRID:
				return web;
			case CUBE:
				return cube;
			case CUBE_BEVEL:
				return cubeBevel;
			case CYLINDER:
				return cylinder;
			case CONE:
				return cone;
			case RING:
				return ring;
			case SPHERE:
				return sphere;
			case HEMISPHERE:
				return hemisphere;
			case TORUS:
				return torus;
			case STAIR:
				return stair;
			case SPIRAL_STAIR:
				return spiralStair;
			case SPIRAL_RAMP:
				return spiralRamp;
			default:
				throw new UnsupportedOperationException("Unable to generate shape for " + primitiveType);
		}
	}

	public GeneratePrimitiveOptionsDialog(JFrame parent, PreviewGeneratorPrimitive preview,
		BiConsumer<DialogResult, TriangleBatch> onCloseCallback)
	{
		super(parent);
		this.preview = preview;

		preview.setUpdate(() -> {
			Vector3f target = new Vector3f(0, 0, 0);
			switch (preview.originMode) {
				case ORIGIN:
					//already fine
					break;
				case CURSOR:
					target = MapEditor.instance().getCursorPosition();
					break;
				case SELECTION:
					Selection<?> currentSelection = MapEditor.instance().selectionManager.currentSelection;
					if (!currentSelection.isEmpty())
						target = currentSelection.getCenter();
					break;
			}

			if (!target.equals(preview.center)) {
				preview.center.set(target);
				updatePreview();
			}
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

		JComboBox<PreviewOriginMode> originModeBox = new JComboBox<>(PreviewOriginMode.values());
		originModeBox.setSelectedItem(preview.originMode);
		originModeBox.addActionListener((e) -> {
			if (preview != null)
				preview.originMode = (PreviewOriginMode) originModeBox.getSelectedItem();
		});

		JComboBox<Primitive> shapeComboBox = new JComboBox<>(Primitive.values());
		shapeComboBox.setSelectedItem(primitiveType);
		shapeComboBox.addActionListener((e) -> {
			setType((Primitive) shapeComboBox.getSelectedItem());
			updatePreview();
		});

		shapeComboBox.setMaximumRowCount(shapeComboBox.getModel().getSize());
		shapeComboBox.setRenderer(new SeparatedComboBoxRenderer(shapeComboBox.getRenderer()));
		SwingUtils.setFontSize(shapeComboBox, 12);

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

		setLayout(new MigLayout("ins 16, fill, hidemode 3"));

		add(new JLabel("Draw Mode"), "split 3, w 25%!");
		add(drawModeBox, "growx");
		add(cbUseDepth, "growx, wrap");

		add(new JLabel("Location"), "split 2, w 25%!");
		add(originModeBox, "growx, wrap, gapbottom 4");

		add(new JSeparator(), "growx, wrap, gapbottom 8");

		add(new JLabel("Template"), "split 2, w 25%!");
		add(shapeComboBox, "growx, wrap, gapbottom 8");

		plane.addFields(this);
		plane.setVisible(false);

		web.addFields(this);
		web.setVisible(false);

		cube.addFields(this);
		cube.setVisible(false);

		cubeBevel.addFields(this);
		cubeBevel.setVisible(false);

		cylinder.addFields(this);
		cylinder.setVisible(false);

		cone.addFields(this);
		cone.setVisible(false);

		ring.addFields(this);
		ring.setVisible(false);

		sphere.addFields(this);
		sphere.setVisible(false);

		hemisphere.addFields(this);
		hemisphere.setVisible(false);

		torus.addFields(this);
		torus.setVisible(false);

		stair.addFields(this);
		stair.setVisible(false);

		spiralStair.addFields(this);
		spiralStair.setVisible(false);

		spiralRamp.addFields(this);
		spiralRamp.setVisible(false);

		add(new JLabel(), "gaptop 16, growx, sg but, split 3");
		add(selectButton, "gaptop 16, growx, sg but");
		add(cancelButton, "gaptop 16, growx, sg but");

		setType(DEFAULT_TYPE);
		setResizable(false);
	}

	public void addSpinner(LabeledIntegerSpinner spinner)
	{
		add(spinner, "span, growx, wrap");
		spinner.addChangeListener((e) -> updatePreview());
	}

	public void addCheckBox(JCheckBox checkBox)
	{
		add(checkBox, "span, growx, wrap");
		checkBox.addActionListener((e) -> updatePreview());
	}

	public void addComboBox(JComboBox<?> comboBox)
	{
		add(comboBox, "span, growx, wrap");
		comboBox.addActionListener((e) -> updatePreview());
	}

	private void setType(Primitive newType)
	{
		ShapeGenerator gen = getGenerator();
		gen.setVisible(false);

		primitiveType = newType;

		gen = getGenerator();
		gen.setVisible(true);

		pack();
	}

	public void beginPreview(MapObjectNode<?> parentObj, TriangleBatch targetBatch)
	{
		preview.init();
		preview.visible = true;
		preview.parentObj = parentObj;
		preview.targetBatch = targetBatch;
		updatePreview();
	}

	public void updatePreview()
	{
		preview.batch = getGenerator().generateTriangles(preview.center);
	}

	public String getTypeName()
	{
		return primitiveType.name;
	}

	/**
	 * ComboBox renderer with separators, adapted from code by:
	 * @author Santhosh Kumar T
	 * @email santhosh.tekuri@gmail.com
	 */
	private static class SeparatedComboBoxRenderer implements ListCellRenderer<Primitive>
	{
		private ListCellRenderer<? super Primitive> delegate;
		private JPanel separatorPanel = new JPanel(new BorderLayout());
		private JSeparator separator = new JSeparator();

		public SeparatedComboBoxRenderer(ListCellRenderer<? super Primitive> delegate)
		{
			this.delegate = delegate;
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Primitive> list, Primitive value, int index, boolean isSelected,
			boolean cellHasFocus)
		{
			Component comp = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (index != -1 && addSeparatorAfter(list, value, index)) {
				// index==1 if renderer is used to paint current value in combo
				separatorPanel.removeAll();
				separatorPanel.add(comp, BorderLayout.CENTER);
				separatorPanel.add(separator, BorderLayout.SOUTH);
				return separatorPanel;
			}
			else
				return comp;
		}

		protected boolean addSeparatorAfter(JList<? extends Primitive> list, Primitive value, int index)
		{
			return (value == Primitive.TORUS);
		}
	}
}
