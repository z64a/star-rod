package game.map.editor.ui.info;

import static app.IconResource.ICON_DOWN;
import static app.IconResource.ICON_UP;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.editor.ui.SwingGUI;
import game.map.shape.Model;
import game.map.shape.Model.SetMatrix;
import game.map.shape.Model.SetMatrixElement;
import net.miginfocom.swing.MigLayout;
import util.ui.FloatTextField;

public class TransformPanel extends JPanel
{
	private Model model;

	private JCheckBox useTransformMatrix;

	private FloatTextField translateX;
	private FloatTextField translateY;
	private FloatTextField translateZ;

	private JSpinner rotX;
	private JSpinner rotY;
	private JSpinner rotZ;

	private FloatTextField scaleX;
	private FloatTextField scaleY;
	private FloatTextField scaleZ;

	private JButton bakeButton;
	private JButton decomposeButton;

	private JCheckBox usePreview;

	private FloatTextField[][] rotTable;

	private boolean ignoreChanges = false;

	public TransformPanel()
	{
		useTransformMatrix = new JCheckBox("  Enable Transform Matrix");
		useTransformMatrix.addActionListener((e) -> {
			if (!ignoreChanges)
				MapEditor.execute(model.hasTransformMatrix.mutator(useTransformMatrix.isSelected()));
		});

		translateX = new FloatTextField((newX) -> {
			if (!ignoreChanges)
				MapEditor.execute(new SetMatrixElement(model, 0, 3, newX));
		});
		translateY = new FloatTextField((newY) -> {
			if (!ignoreChanges)
				MapEditor.execute(new SetMatrixElement(model, 1, 3, newY));
		});
		translateZ = new FloatTextField((newZ) -> {
			if (!ignoreChanges)
				MapEditor.execute(new SetMatrixElement(model, 2, 3, newZ));
		});

		rotTable = new FloatTextField[3][3];
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 3; j++) {
				final int row = i;
				final int col = j;
				rotTable[i][j] = new FloatTextField((newValue) -> {
					MapEditor.execute(new SetMatrixElement(model, row, col, newValue));
				});
			}

		bakeButton = new JButton("Bake Transform", ICON_UP);
		bakeButton.addActionListener((e) -> {
			MapEditor.execute(new SetMatrix(model,
				model.localTransformMatrix.getBakedMatrix(),
				model.localTransformMatrix.baked));
			setModel(model);
		});
		bakeButton.setToolTipText("Set transform matrix from rotation and scale. Uses XYZ rotation order.");

		decomposeButton = new JButton("Try Decompose", ICON_DOWN);
		decomposeButton.addActionListener((e) -> {
			model.localTransformMatrix.decompose();
			setModel(model);
			model.localTransformMatrix.baked = true;
			SwingGUI.instance().repaintVisibleTree();
		});
		decomposeButton.setToolTipText("Attemps to decompose the matrix. Assumes positive, nonuniform scale and XYZ rotation order.");

		rotX = new JSpinner(new SpinnerNumberModel(0.0, -360.0, 360.0, 0.5));
		rotX.addChangeListener((e) -> {
			model.localTransformMatrix.txRot[0] = (Double) rotX.getValue();
			if (model.localTransformMatrix.usePreview)
				model.updateTransformHierarchy();
			model.localTransformMatrix.baked = false;
			SwingGUI.instance().repaintVisibleTree();
		});
		SwingUtils.centerSpinnerText(rotX);

		rotY = new JSpinner(new SpinnerNumberModel(0.0, -360.0, 360.0, 0.5));
		rotY.addChangeListener((e) -> {
			model.localTransformMatrix.txRot[1] = (Double) rotY.getValue();
			if (model.localTransformMatrix.usePreview)
				model.updateTransformHierarchy();
			model.localTransformMatrix.baked = false;
			SwingGUI.instance().repaintVisibleTree();
		});
		SwingUtils.centerSpinnerText(rotY);

		rotZ = new JSpinner(new SpinnerNumberModel(0.0, -360.0, 360.0, 0.5));
		rotZ.addChangeListener((e) -> {
			model.localTransformMatrix.txRot[2] = (Double) rotZ.getValue();
			if (model.localTransformMatrix.usePreview)
				model.updateTransformHierarchy();
			model.localTransformMatrix.baked = false;
			SwingGUI.instance().repaintVisibleTree();
		});
		SwingUtils.centerSpinnerText(rotZ);

		scaleX = new FloatTextField((newX) -> {
			model.localTransformMatrix.txScale[0] = newX;
			if (model.localTransformMatrix.usePreview)
				model.updateTransformHierarchy();
			model.localTransformMatrix.baked = false;
			SwingGUI.instance().repaintVisibleTree();
		});
		scaleY = new FloatTextField((newY) -> {
			model.localTransformMatrix.txScale[1] = newY;
			if (model.localTransformMatrix.usePreview)
				model.updateTransformHierarchy();
			model.localTransformMatrix.baked = false;
			SwingGUI.instance().repaintVisibleTree();
		});
		scaleZ = new FloatTextField((newZ) -> {
			model.localTransformMatrix.txScale[2] = newZ;
			if (model.localTransformMatrix.usePreview)
				model.updateTransformHierarchy();
			model.localTransformMatrix.baked = false;
			SwingGUI.instance().repaintVisibleTree();
		});

		usePreview = new JCheckBox(" Live preview");
		usePreview.setToolTipText("Does not update the underlying transform matrix! Remember to bake!");
		usePreview.addActionListener((e) -> {
			model.localTransformMatrix.usePreview = usePreview.isSelected();
			model.updateTransformHierarchy();
		});

		setLayout(new MigLayout("fillx, ins n 16 0 16, wrap"));

		add(useTransformMatrix);
		add(new JLabel("Translation"), " split 4, w 20%");
		add(translateX, "growx, sg fields");
		add(translateY, "growx, sg fields");
		add(translateZ, "growx, sg fields, gapbottom 4");

		add(new JLabel("Matrix"), "top, split 2");

		JPanel matrixPanel = new JPanel(new MigLayout("fill, ins 0, gap 0"));
		matrixPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

		for (int row = 0; row < 3; row++) {
			matrixPanel.add(rotTable[row][0], "pushx, growx, sg fields");
			matrixPanel.add(rotTable[row][1], "pushx, growx, sg fields");
			matrixPanel.add(rotTable[row][2], "pushx, growx, sg fields, wrap");
		}

		add(matrixPanel, "grow, span");

		add(decomposeButton, "split 2, pushx, growx, sg buttons, gaptop 8");
		add(bakeButton, "pushx, growx, sg buttons, gapbottom 8");

		add(new JLabel("Rotation"), "split 4, w 20%");
		add(rotX, "pushx, growx, sg fields");
		add(rotY, "pushx, growx, sg fields");
		add(rotZ, "pushx, growx, sg fields");

		add(new JLabel("Scale"), "split 4, w 20%");
		add(scaleX, "pushx, growx, sg fields");
		add(scaleY, "pushx, growx, sg fields");
		add(scaleZ, "pushx, growx, sg fields");

		add(usePreview, "pushx, growx");
	}

	public void setModel(Model mdl)
	{
		ignoreChanges = true;

		this.model = mdl;
		boolean hasMatrix = mdl.hasTransformMatrix.get();

		useTransformMatrix.setSelected(mdl.hasTransformMatrix.get());

		translateX.setValue((float) (mdl.localTransformMatrix.get(0, 3)));
		translateY.setValue((float) (mdl.localTransformMatrix.get(1, 3)));
		translateZ.setValue((float) (mdl.localTransformMatrix.get(2, 3)));

		translateX.setEnabled(hasMatrix);
		translateY.setEnabled(hasMatrix);
		translateZ.setEnabled(hasMatrix);

		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 3; j++) {
				rotTable[i][j].setValue((float) mdl.localTransformMatrix.get(i, j));
				rotTable[i][j].setEnabled(hasMatrix);
			}

		ignoreChanges = false;

		rotX.setValue(mdl.localTransformMatrix.txRot[0]);
		rotY.setValue(mdl.localTransformMatrix.txRot[1]);
		rotZ.setValue(mdl.localTransformMatrix.txRot[2]);

		scaleX.setValue((float) mdl.localTransformMatrix.txScale[0]);
		scaleY.setValue((float) mdl.localTransformMatrix.txScale[1]);
		scaleZ.setValue((float) mdl.localTransformMatrix.txScale[2]);

		usePreview.setSelected(mdl.localTransformMatrix.usePreview);

		bakeButton.setEnabled(hasMatrix);
		decomposeButton.setEnabled(hasMatrix);

		rotX.setEnabled(hasMatrix);
		rotY.setEnabled(hasMatrix);
		rotZ.setEnabled(hasMatrix);

		scaleX.setEnabled(hasMatrix);
		scaleY.setEnabled(hasMatrix);
		scaleZ.setEnabled(hasMatrix);

		usePreview.setEnabled(hasMatrix);
	}
}
