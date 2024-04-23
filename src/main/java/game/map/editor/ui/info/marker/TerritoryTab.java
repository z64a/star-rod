package game.map.editor.ui.info.marker;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;
import game.map.MutablePoint;
import game.map.editor.MapEditor;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.editor.ui.PathList;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.marker.Marker.MarkerType;
import game.map.marker.NpcComponent;
import game.map.marker.NpcComponent.MoveType;
import game.map.marker.NpcComponent.SetDetectPos;
import game.map.marker.NpcComponent.SetWanderPos;
import game.map.marker.PathData.AddPathPoint;
import net.miginfocom.swing.MigLayout;
import util.ui.FloatTextField;
import util.ui.IntVectorPanel;

public class TerritoryTab extends JPanel
{
	private static enum VolumeType
	{
		Circle, Box
	}

	private final MarkerInfoPanel parent;

	private JPanel wanderPanel;
	private JPanel patrolPanel;
	private PathList patrolPathList;

	private JComboBox<MoveType> moveTypeBox;
	private JCheckBox moveFlyingCheckbox;

	private JCheckBox overrideCheckbox;
	private FloatTextField overrideField;

	private IntVectorPanel detectCenterPanel;
	private JComboBox<VolumeType> detectTypeBox;
	private JSpinner detectSpinnerX;
	private JSpinner detectSpinnerZ;

	private IntVectorPanel wanderCenterPanel;
	private JComboBox<VolumeType> wanderTypeBox;
	private JSpinner wanderSpinnerX;
	private JSpinner wanderSpinnerZ;

	public TerritoryTab(MarkerInfoPanel parent)
	{
		this.parent = parent;

		moveTypeBox = new JComboBox<>(MoveType.values());
		moveTypeBox.addActionListener(e -> {
			if (parent.ignoreEvents())
				return;

			MoveType type = (MoveType) moveTypeBox.getSelectedItem();
			MapEditor.execute(parent.getData().npcComponent.moveType.mutator(type));
		});
		SwingUtils.addBorderPadding(moveTypeBox);

		moveFlyingCheckbox = new JCheckBox(" Is Flying?");
		moveFlyingCheckbox.addActionListener((e) -> MapEditor.execute(
			parent.getData().npcComponent.flying.mutator(moveFlyingCheckbox.isSelected())));

		overrideCheckbox = new JCheckBox(" Override Movement Speed?");
		overrideCheckbox.addActionListener((e) -> MapEditor.execute(
			parent.getData().npcComponent.overrideMovementSpeed.mutator(overrideCheckbox.isSelected())));

		overrideField = new FloatTextField((speed) -> MapEditor.execute(
			parent.getData().npcComponent.movementSpeedOverride.mutator(speed)));

		detectCenterPanel = new IntVectorPanel(3, (i, v) -> MapEditor.execute(new SetDetectPos(parent.getData(), i, v)));
		detectCenterPanel.addBorderPaddings();

		detectTypeBox = new JComboBox<>(VolumeType.values());
		detectTypeBox.addActionListener(e -> {
			if (parent.ignoreEvents())
				return;

			boolean useCircle = (VolumeType) detectTypeBox.getSelectedItem() == VolumeType.Circle;
			MapEditor.execute(parent.getData().npcComponent.useDetectCircle.mutator(useCircle));
		});

		detectSpinnerX = new JSpinner(new SpinnerNumberModel(256, 0, 4096, 1));
		detectSpinnerX.addChangeListener((e) -> {
			int v = (int) detectSpinnerX.getValue();
			if (parent.getData().npcComponent.useDetectCircle.get())
				MapEditor.execute(parent.getData().npcComponent.detectRadius.mutator(v));
			else
				MapEditor.execute(parent.getData().npcComponent.detectSizeX.mutator(v));
		});
		SwingUtils.addVerticalBorderPadding(detectSpinnerX);

		detectSpinnerZ = new JSpinner(new SpinnerNumberModel(256, 0, 4096, 1));
		detectSpinnerZ.addChangeListener((e) -> {
			int v = (int) detectSpinnerZ.getValue();
			if (!parent.getData().npcComponent.useDetectCircle.get())
				MapEditor.execute(parent.getData().npcComponent.detectSizeZ.mutator(v));
		});
		SwingUtils.addVerticalBorderPadding(detectSpinnerZ);

		wanderCenterPanel = new IntVectorPanel(3, (i, v) -> MapEditor.execute(new SetWanderPos(parent.getData(), i, v)));
		wanderCenterPanel.addBorderPaddings();

		wanderTypeBox = new JComboBox<>(VolumeType.values());
		wanderTypeBox.addActionListener(e -> {
			if (parent.ignoreEvents())
				return;

			boolean useCircle = (VolumeType) wanderTypeBox.getSelectedItem() == VolumeType.Circle;
			MapEditor.execute(parent.getData().npcComponent.useWanderCircle.mutator(useCircle));
		});

		wanderSpinnerX = new JSpinner(new SpinnerNumberModel(256, 0, 4096, 1));
		wanderSpinnerX.addChangeListener((e) -> {
			int v = (int) wanderSpinnerX.getValue();
			if (parent.getData().npcComponent.useWanderCircle.get())
				MapEditor.execute(parent.getData().npcComponent.wanderRadius.mutator(v));
			else
				MapEditor.execute(parent.getData().npcComponent.wanderSizeX.mutator(v));
		});
		SwingUtils.addVerticalBorderPadding(wanderSpinnerX);

		wanderSpinnerZ = new JSpinner(new SpinnerNumberModel(256, 0, 4096, 1));
		wanderSpinnerZ.addChangeListener((e) -> {
			int v = (int) wanderSpinnerZ.getValue();
			if (!parent.getData().npcComponent.useWanderCircle.get())
				MapEditor.execute(parent.getData().npcComponent.wanderSizeZ.mutator(v));
		});
		SwingUtils.addVerticalBorderPadding(wanderSpinnerZ);

		JButton addPointButton = new JButton("Add Point");
		addPointButton.addActionListener((e) -> {
			MapEditor.execute(new AddPathPoint(parent.getData().npcComponent.patrolPath));
		});
		SwingUtils.addBorderPadding(addPointButton);

		wanderPanel = new JPanel(new MigLayout("fill, ins 0"));

		wanderPanel.add(new JLabel("Wandering Volume"), "growx, wrap");
		wanderPanel.add(new JLabel("Center"), "w 17%!, span, split 4");
		wanderPanel.add(wanderCenterPanel, "span, growx, wrap");

		wanderPanel.add(new JLabel("Shape"), "w 17%!, span, split 4");
		wanderPanel.add(wanderTypeBox, "growx, sg shape");
		wanderPanel.add(wanderSpinnerX, "growx, sg shape");
		wanderPanel.add(wanderSpinnerZ, "growx, sg shape, wrap");

		patrolPathList = new PathList();

		JScrollPane scrollPane = new JScrollPane(patrolPathList);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(null);

		patrolPanel = new JPanel(new MigLayout("fill, ins 0"));
		patrolPanel.add(new JLabel("Patrol Path"), "growx, wrap");
		patrolPanel.add(scrollPane, "growx, growy, pushy, wrap");
		patrolPanel.add(addPointButton, "w 50%, center, wrap");

		setLayout(new MigLayout("fillx, ins n 16 n 16, hidemode 3"));

		add(new JLabel("Type"), "span, split 3, w 15%!");
		add(moveTypeBox, "growx");
		add(moveFlyingCheckbox, "w 25%!, gapleft 3%, wrap");

		add(new JLabel(), "span, split 3, w 15%!");
		add(overrideCheckbox, "growx");
		add(overrideField, "w 25%!, gapleft 3%, wrap");

		add(new JLabel(), "h 0!, wrap");

		add(new JLabel("Detection Volume"), "growx, wrap");
		add(new JLabel("Center"), "w 15%!, span, split 2");
		add(detectCenterPanel, "span, growx, wrap");

		add(new JLabel("Shape"), "w 15%!, span, split 4");
		add(detectTypeBox, "growx, sg shape");
		add(detectSpinnerX, "growx, sg shape");
		add(detectSpinnerZ, "growx, sg shape, wrap");

		add(new JLabel(), "h 0!, wrap");

		add(wanderPanel, "growx, span, wrap");
		add(patrolPanel, "grow, pushy, span");
	}

	public void updateFields()
	{
		assert (parent.getData().getType() == MarkerType.NPC);

		NpcComponent npc = parent.getData().npcComponent;

		moveTypeBox.setSelectedItem(npc.moveType.get());
		moveFlyingCheckbox.setSelected(npc.flying.get());

		overrideField.setValue(npc.movementSpeedOverride.get());

		boolean override = npc.overrideMovementSpeed.get();
		overrideCheckbox.setSelected(override);
		overrideField.setEnabled(override);

		detectCenterPanel.setValues(npc.detectCenter.getX(), npc.detectCenter.getY(), npc.detectCenter.getZ());

		if (npc.useDetectCircle.get()) {
			detectTypeBox.setSelectedItem(VolumeType.Circle);
			detectSpinnerZ.setEnabled(false);

			detectSpinnerX.setValue(npc.detectRadius.get());
			detectSpinnerZ.setValue(0);
		}
		else {
			detectTypeBox.setSelectedItem(VolumeType.Box);
			detectSpinnerZ.setEnabled(true);

			detectSpinnerX.setValue(npc.detectSizeX.get());
			detectSpinnerZ.setValue(npc.detectSizeZ.get());
		}

		wanderCenterPanel.setValues(npc.wanderCenter.getX(), npc.wanderCenter.getY(), npc.wanderCenter.getZ());

		wanderPanel.setVisible(npc.moveType.get() == MoveType.Wander);
		if (npc.moveType.get() == MoveType.Wander) {
			if (npc.useWanderCircle.get()) {
				wanderTypeBox.setSelectedItem(VolumeType.Circle);
				wanderSpinnerZ.setEnabled(false);

				wanderSpinnerX.setValue(npc.wanderRadius.get());
				wanderSpinnerZ.setValue(0);
			}
			else {
				wanderTypeBox.setSelectedItem(VolumeType.Box);
				wanderSpinnerZ.setEnabled(true);

				wanderSpinnerX.setValue(npc.wanderSizeX.get());
				wanderSpinnerZ.setValue(npc.wanderSizeZ.get());
			}
		}

		patrolPanel.setVisible(npc.moveType.get() == MoveType.Patrol);
		if (npc.moveType.get() == MoveType.Patrol) {
			patrolPathList.setModel(npc.patrolPath.points);
			npc.patrolPath.markDegenerates();
		}
	}

	public void updateDynamicFields(boolean force)
	{
		NpcComponent npc = parent.getData().npcComponent;
		boolean pointSelectionMode = (MapEditor.instance().selectionManager.getSelectionMode() == SelectionMode.POINT);
		if (!pointSelectionMode)
			return;

		MutablePoint point = npc.detectCenter.point;
		if (force || point.isTransforming())
			detectCenterPanel.setValues(point.getX(), point.getY(), point.getZ());

		switch (npc.moveType.get()) {
			case Stationary:
				break;

			case Wander:
				point = npc.wanderCenter.point;
				if (force || point.isTransforming())
					wanderCenterPanel.setValues(point.getX(), point.getY(), point.getZ());
				break;

			case Patrol:
				npc.patrolPath.markDegenerates();
				patrolPathList.repaint();
				break;
		}
	}
}
