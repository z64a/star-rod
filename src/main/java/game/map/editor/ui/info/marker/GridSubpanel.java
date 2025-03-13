package game.map.editor.ui.info.marker;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import common.commands.EditableField;
import game.map.editor.MapEditor;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.marker.GridComponent;
import net.miginfocom.swing.MigLayout;

public class GridSubpanel extends JPanel
{
	private final MarkerInfoPanel parent;

	private JSpinner gridSystemID;
	private JSpinner gridXSpinner;
	private JSpinner gridZSpinner;
	private JSpinner gridSizeSpinner;
	private JCheckBox cbGridGravity;
	private JButton btEnableEditing;

	public GridSubpanel(MarkerInfoPanel parent)
	{
		this.parent = parent;

		gridSystemID = new JSpinner(new SpinnerNumberModel(0, 0, 255, 1));
		((JSpinner.DefaultEditor) gridSystemID.getEditor()).getTextField()
			.setHorizontalAlignment(SwingConstants.CENTER);
		gridSystemID.addChangeListener((e) -> MapEditor.execute(
			parent.getData().gridComponent.gridIndex.mutator((Integer) gridSystemID.getValue())));

		gridXSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 255, 1));
		((JSpinner.DefaultEditor) gridXSpinner.getEditor()).getTextField()
			.setHorizontalAlignment(SwingConstants.CENTER);
		gridXSpinner.addChangeListener((e) -> MapEditor.execute(
			parent.getData().gridComponent.gridSizeX.mutator((Integer) gridXSpinner.getValue())));

		gridZSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 255, 1));
		((JSpinner.DefaultEditor) gridZSpinner.getEditor()).getTextField()
			.setHorizontalAlignment(SwingConstants.CENTER);
		gridZSpinner.addChangeListener((e) -> MapEditor.execute(
			parent.getData().gridComponent.gridSizeZ.mutator((Integer) gridZSpinner.getValue())));

		gridSizeSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1000, 1));
		((JSpinner.DefaultEditor) gridSizeSpinner.getEditor()).getTextField()
			.setHorizontalAlignment(SwingConstants.CENTER);
		gridSizeSpinner.addChangeListener((e) -> MapEditor.execute(
			parent.getData().gridComponent.gridSpacing.mutator((Integer) gridSizeSpinner.getValue())));

		cbGridGravity = new JCheckBox(" Blocks have gravity");
		cbGridGravity.addActionListener((e) -> MapEditor.execute(
			parent.getData().gridComponent.gridUseGravity.mutator(cbGridGravity.isSelected())));

		btEnableEditing = new JButton("Enable Interactive Editing");
		btEnableEditing.addActionListener((e) -> {
			EditableField<Boolean> field = parent.getData().gridComponent.showEditHandles;
			MapEditor.execute(field.mutator(!field.get()));
		});

		setLayout(new MigLayout("fillx, ins 0", MarkerInfoPanel.FOUR_COLUMNS));
		add(SwingUtils.getLabel("Push Block Grid", 14), "growx, span, wrap, gapbottom 4");

		add(new JLabel("Grid ID"));
		add(gridSystemID, "growx, wrap");

		add(new JLabel("Grid Size"));
		add(gridXSpinner, "growx");
		add(gridZSpinner, "growx, wrap");
		//	gridPanel.add(cbGridGravity, "growx, span, wrap, gaptop 8, gapleft 8");

		add(btEnableEditing, "span, center, w 60%!, h 40!, growx, wrap, gaptop 8");
	}

	public void updateFields()
	{
		GridComponent grid = parent.getData().gridComponent;

		gridSystemID.setValue(grid.gridIndex.get());
		gridXSpinner.setValue(grid.gridSizeX.get());
		gridZSpinner.setValue(grid.gridSizeZ.get());
		gridSizeSpinner.setValue(grid.gridSpacing.get());
		cbGridGravity.setSelected(grid.gridUseGravity.get());
		btEnableEditing.setText((grid.showEditHandles.get() ? "Disable" : "Enable") + " Interactive Editing");
	}
}
