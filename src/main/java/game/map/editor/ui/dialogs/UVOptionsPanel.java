package game.map.editor.ui.dialogs;

import java.awt.Window;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import game.map.editor.MapEditor;
import game.map.shape.UVGenerator;
import game.map.shape.UVGenerator.Projection;
import game.map.shape.UVGenerator.ProjectionAxis;
import net.miginfocom.swing.MigLayout;
import util.ui.LabeledDoubleSpinner;

public class UVOptionsPanel extends JPanel
{
	private Projection projectionType = Projection.PLANAR;
	private ProjectionAxis projectionAxis = ProjectionAxis.Y;

	private final JComboBox<Projection> projectionComboBox;
	private final JLabel axisLabel;
	private final JComboBox<ProjectionAxis> axisComboBox;
	private final LabeledDoubleSpinner scaleSpinner;

	public UVGenerator getUVGenerator()
	{
		return new UVGenerator(projectionType, projectionAxis, (float) scaleSpinner.getValue());
	}

	public Projection getProjectionType()
	{
		return projectionType;
	}

	public ProjectionAxis getProjectionAxis()
	{
		return projectionAxis;
	}

	public double getScale()
	{
		return scaleSpinner.getValue();
	}

	public UVOptionsPanel()
	{
		projectionComboBox = new JComboBox<>(Projection.values());
		projectionComboBox.setSelectedItem(projectionType);
		projectionComboBox.setActionCommand("choose_projection");
		projectionComboBox.addActionListener((e) -> {
			setType((Projection) projectionComboBox.getSelectedItem());
		});

		axisLabel = new JLabel("About axis ");
		axisComboBox = new JComboBox<>(ProjectionAxis.values());
		axisComboBox.setSelectedItem(projectionAxis);
		axisComboBox.setActionCommand("choose_axis");
		axisComboBox.addActionListener((e) -> {
			projectionAxis = (ProjectionAxis) axisComboBox.getSelectedItem();
		});

		scaleSpinner = new LabeledDoubleSpinner("UV Scale", 0.0125, 512.0, MapEditor.instance().getDefaultUVScale(), 0.0125);

		setLayout(new MigLayout("fill, hidemode 3"));
		add(new JLabel("Projection "));
		add(projectionComboBox, "growx, wrap");
		add(axisLabel);
		add(axisComboBox, "growx, wrap");
		add(scaleSpinner, "span, growx, wrap");
	}

	private void setType(Projection newType)
	{
		projectionType = newType;
		//	axisLabel.setVisible(projectionType != Projection.UNWRAP);
		//	axisComboBox.setVisible(projectionType != Projection.UNWRAP);

		Window w = SwingUtilities.getWindowAncestor(this);
		if (w != null)
			w.pack();
	}
}
