package game.map.editor.ui.dialogs;

import java.awt.Color;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import common.Vector3f;
import game.map.Axis;
import game.map.editor.selection.Selection;
import game.map.shape.TransformMatrix;
import net.miginfocom.swing.MigLayout;
import util.ui.LabeledDoubleSpinner;
import util.ui.LabeledIntegerSpinner;

public class TransformSelectionPanel extends JPanel implements ActionListener
{
	public enum TransformType
	{
		Translate, Rotate, Scale, Resize, Flip
	}

	private static final TransformType DEFAULT_TYPE = TransformType.Scale;

	private TransformType selectedType = TransformType.Scale;
	private final JComboBox<TransformType> typeComboBox;

	private final LabeledIntegerSpinner translateSpinnerX;
	private final LabeledIntegerSpinner translateSpinnerY;
	private final LabeledIntegerSpinner translateSpinnerZ;

	private final JLabel rotateAxisLabel;
	private final JComboBox<Axis> rotateAxisBox;
	private final LabeledDoubleSpinner rotateAngleSpinner;

	private final LabeledDoubleSpinner scaleSpinnerX;
	private final LabeledDoubleSpinner scaleSpinnerY;
	private final LabeledDoubleSpinner scaleSpinnerZ;
	private final JCheckBox uniformScaleBox;

	private final LabeledIntegerSpinner resizeSpinnerX;
	private final LabeledIntegerSpinner resizeSpinnerY;
	private final LabeledIntegerSpinner resizeSpinnerZ;

	private final JCheckBox flipCheckBoxX;
	private final JCheckBox flipCheckBoxY;
	private final JCheckBox flipCheckBoxZ;

	private int selectionSizeX;
	private int selectionSizeY;
	private int selectionSizeZ;
	private Vector3f transformOrigin = null;

	private Color RED = new Color(192, 0, 0);
	private Color GREEN = new Color(0, 160, 0);
	private Color BLUE = new Color(0, 0, 192);

	private boolean ignoreScaleChanges = false;

	public TransformSelectionPanel()
	{
		typeComboBox = new JComboBox<>(TransformType.values());
		typeComboBox.setSelectedItem(selectedType);
		typeComboBox.setActionCommand("choose_transform_type");
		typeComboBox.addActionListener(this);

		// -32768 to -32767
		translateSpinnerX = new LabeledIntegerSpinner("X", RED, -4096, 4096, 0);
		translateSpinnerY = new LabeledIntegerSpinner("Y", GREEN, -4096, 4096, 0);
		translateSpinnerZ = new LabeledIntegerSpinner("Z", BLUE, -4096, 4096, 0);

		rotateAxisLabel = new JLabel("Axis");
		rotateAxisLabel.setFont(rotateAxisLabel.getFont().deriveFont(12f));
		rotateAxisBox = new JComboBox<>(Axis.values());
		((JLabel) rotateAxisBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
		rotateAxisBox.setSelectedItem(Axis.Y);
		rotateAxisBox.setActionCommand("choose_rotation_axis");
		rotateAxisBox.addActionListener(this);
		rotateAngleSpinner = new LabeledDoubleSpinner("Angle", -180, 180, 0, 0.001);

		scaleSpinnerX = new LabeledDoubleSpinner("X", RED, 0, 8192, 1, 0.001);
		scaleSpinnerY = new LabeledDoubleSpinner("Y", GREEN, 0, 8192, 1, 0.001);
		scaleSpinnerZ = new LabeledDoubleSpinner("Z", BLUE, 0, 8192, 1, 0.001);
		uniformScaleBox = new JCheckBox("Uniform Rescale");
		uniformScaleBox.setHorizontalTextPosition(SwingConstants.LEFT);

		scaleSpinnerX.addChangeListener((e) -> {
			if (uniformScaleBox.isSelected() && !ignoreScaleChanges) {
				double v = scaleSpinnerX.getValue();
				ignoreScaleChanges = true;
				scaleSpinnerY.setValue(v);
				scaleSpinnerZ.setValue(v);
				ignoreScaleChanges = false;
			}
		});

		scaleSpinnerY.addChangeListener((e) -> {
			if (uniformScaleBox.isSelected() && !ignoreScaleChanges) {
				double v = scaleSpinnerY.getValue();
				ignoreScaleChanges = true;
				scaleSpinnerX.setValue(v);
				scaleSpinnerZ.setValue(v);
				ignoreScaleChanges = false;
			}
		});

		scaleSpinnerZ.addChangeListener((e) -> {
			if (uniformScaleBox.isSelected() && !ignoreScaleChanges) {
				double v = scaleSpinnerZ.getValue();
				ignoreScaleChanges = true;
				scaleSpinnerX.setValue(v);
				scaleSpinnerY.setValue(v);
				ignoreScaleChanges = false;
			}
		});

		resizeSpinnerX = new LabeledIntegerSpinner("X", RED, 0, 65535, 0);
		resizeSpinnerY = new LabeledIntegerSpinner("Y", GREEN, 0, 65535, 0);
		resizeSpinnerZ = new LabeledIntegerSpinner("Z", BLUE, 0, 65535, 0);

		flipCheckBoxX = new JCheckBox("Mirror X");
		flipCheckBoxY = new JCheckBox("Mirror Y");
		flipCheckBoxZ = new JCheckBox("Mirror Z");

		setLayout(new MigLayout("fill, hidemode 3"));
		add(typeComboBox, "growx, wrap, span, gapbottom 8");

		add(translateSpinnerX, "span, growx, wrap");
		add(translateSpinnerY, "span, growx, wrap");
		add(translateSpinnerZ, "span, growx, wrap");

		add(rotateAxisLabel, "pushx");
		add(rotateAxisBox, "w 72!, wrap");
		add(rotateAngleSpinner, "span, growx, wrap");

		add(scaleSpinnerX, "span, growx, wrap");
		add(scaleSpinnerY, "span, growx, wrap");
		add(scaleSpinnerZ, "span, growx, wrap");
		add(uniformScaleBox, "span, al right, wrap");

		add(resizeSpinnerX, "span, growx, wrap");
		add(resizeSpinnerY, "span, growx, wrap");
		add(resizeSpinnerZ, "span, growx, wrap");

		add(flipCheckBoxX, "span, growx, wrap");
		add(flipCheckBoxY, "span, growx, wrap");
		add(flipCheckBoxZ, "span, growx, wrap");

		translateSpinnerX.setVisible(false);
		translateSpinnerY.setVisible(false);
		translateSpinnerZ.setVisible(false);

		rotateAxisLabel.setVisible(false);
		rotateAxisBox.setVisible(false);
		rotateAngleSpinner.setVisible(false);

		scaleSpinnerX.setVisible(false);
		scaleSpinnerY.setVisible(false);
		scaleSpinnerZ.setVisible(false);
		uniformScaleBox.setVisible(false);

		resizeSpinnerX.setVisible(false);
		resizeSpinnerY.setVisible(false);
		resizeSpinnerZ.setVisible(false);

		flipCheckBoxX.setVisible(false);
		flipCheckBoxY.setVisible(false);
		flipCheckBoxZ.setVisible(false);

		setType(DEFAULT_TYPE);
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		if (e.getActionCommand().equals("choose_transform_type")) {
			setType((TransformType) typeComboBox.getSelectedItem());
		}
	}

	public void setTransformType(TransformType newType)
	{
		// this will trigger and update from the combo box which invokes setType below
		typeComboBox.setSelectedItem(newType);
	}

	public void setSelection(Selection<?> selection)
	{
		transformOrigin = selection.getCenter();

		selectionSizeX = selection.aabb.max.getX() - selection.aabb.min.getX();
		selectionSizeY = selection.aabb.max.getY() - selection.aabb.min.getY();
		selectionSizeZ = selection.aabb.max.getZ() - selection.aabb.min.getZ();

		resizeSpinnerX.setValue(selectionSizeX);
		resizeSpinnerY.setValue(selectionSizeY);
		resizeSpinnerZ.setValue(selectionSizeZ);
	}

	private void setType(TransformType newType)
	{
		switch (selectedType) {
			case Translate:
				translateSpinnerX.setVisible(false);
				translateSpinnerY.setVisible(false);
				translateSpinnerZ.setVisible(false);
				break;
			case Rotate:
				rotateAxisLabel.setVisible(false);
				rotateAxisBox.setVisible(false);
				rotateAngleSpinner.setVisible(false);
				break;
			case Scale:
				scaleSpinnerX.setVisible(false);
				scaleSpinnerY.setVisible(false);
				scaleSpinnerZ.setVisible(false);
				uniformScaleBox.setVisible(false);
				break;
			case Resize:
				resizeSpinnerX.setVisible(false);
				resizeSpinnerY.setVisible(false);
				resizeSpinnerZ.setVisible(false);
				break;
			case Flip:
				flipCheckBoxX.setVisible(false);
				flipCheckBoxY.setVisible(false);
				flipCheckBoxZ.setVisible(false);
				break;
		}

		selectedType = newType;

		switch (selectedType) {
			case Translate:
				translateSpinnerX.setVisible(true);
				translateSpinnerY.setVisible(true);
				translateSpinnerZ.setVisible(true);
				break;
			case Rotate:
				rotateAxisLabel.setVisible(true);
				rotateAxisBox.setVisible(true);
				rotateAngleSpinner.setVisible(true);
				break;
			case Scale:
				scaleSpinnerX.setVisible(true);
				scaleSpinnerY.setVisible(true);
				scaleSpinnerZ.setVisible(true);
				uniformScaleBox.setVisible(true);
				break;
			case Resize:
				resizeSpinnerX.setVisible(true);
				resizeSpinnerY.setVisible(true);
				resizeSpinnerZ.setVisible(true);
				break;
			case Flip:
				flipCheckBoxX.setVisible(true);
				flipCheckBoxY.setVisible(true);
				flipCheckBoxZ.setVisible(true);
				break;
		}

		Window w = SwingUtilities.getWindowAncestor(this);
		if (w != null)
			w.pack();
	}

	// returns null if an identity transform is selected
	public TransformMatrix createTransformMatrix()
	{
		TransformMatrix m = new TransformMatrix();
		double sx, sy, sz;

		switch (selectedType) {
			case Translate:
				if (translateSpinnerX.getValue() == 0
					&& translateSpinnerY.getValue() == 0
					&& translateSpinnerZ.getValue() == 0)
					return null;

				m.setTranslation(translateSpinnerX.getValue(), translateSpinnerY.getValue(), translateSpinnerZ.getValue());
				break;

			case Rotate:
				if (rotateAngleSpinner.getValue() == 0)
					return null;

				TransformMatrix r = new TransformMatrix();
				r.setRotation((Axis) rotateAxisBox.getSelectedItem(), rotateAngleSpinner.getValue());

				m.setTranslation(-transformOrigin.x, -transformOrigin.y, -transformOrigin.z);
				m.concat(r);
				m.translate(transformOrigin);
				break;

			case Scale:
				if (scaleSpinnerX.getValue() == 1
					&& scaleSpinnerY.getValue() == 1
					&& scaleSpinnerZ.getValue() == 1)
					return null;

				sx = scaleSpinnerX.getValue();
				sy = scaleSpinnerY.getValue();
				sz = scaleSpinnerZ.getValue();

				TransformMatrix scale = new TransformMatrix();
				scale.setScale(sx, sy, sz);

				m.setTranslation(-transformOrigin.x, -transformOrigin.y, -transformOrigin.z);
				m.concat(scale);
				m.translate(transformOrigin);
				break;

			case Resize:
				if (resizeSpinnerX.getValue() == selectionSizeX
					&& resizeSpinnerY.getValue() == selectionSizeY
					&& resizeSpinnerZ.getValue() == selectionSizeZ)
					return null;

				sx = (selectionSizeX == 0) ? 1 : (resizeSpinnerX.getValue() / (double) selectionSizeX);
				sy = (selectionSizeY == 0) ? 1 : (resizeSpinnerY.getValue() / (double) selectionSizeY);
				sz = (selectionSizeZ == 0) ? 1 : (resizeSpinnerZ.getValue() / (double) selectionSizeZ);

				TransformMatrix rescale = new TransformMatrix();
				rescale.setScale(sx, sy, sz);

				m.setTranslation(-transformOrigin.x, -transformOrigin.y, -transformOrigin.z);
				m.concat(rescale);
				m.translate(transformOrigin);
				break;

			case Flip:
				if (!flipCheckBoxX.isSelected()
					&& !flipCheckBoxY.isSelected()
					&& !flipCheckBoxZ.isSelected())
					return null;

				sx = flipCheckBoxX.isSelected() ? -1 : 1;
				sy = flipCheckBoxY.isSelected() ? -1 : 1;
				sz = flipCheckBoxZ.isSelected() ? -1 : 1;

				TransformMatrix flip = new TransformMatrix();
				flip.setScale(sx, sy, sz);

				m.setTranslation(-transformOrigin.x, -transformOrigin.y, -transformOrigin.z);
				m.concat(flip);
				m.translate(transformOrigin);
				break;
		}

		return m;
	}
}
