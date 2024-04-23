package game.map.editor.ui.info;

import java.awt.Color;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.editor.MapInfoPanel;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.selection.SelectablePoint;
import game.map.hit.CameraZoneData;
import game.map.hit.CameraZoneData.SetCameraFlag;
import game.map.hit.CameraZoneData.SetCameraPos;
import game.map.hit.CameraZoneData.SetCameraType;
import game.map.hit.ControlType;
import net.miginfocom.swing.MigLayout;
import util.ui.FloatTextField;

public class CameraInfoPanel extends MapInfoPanel<CameraZoneData>
{
	private JComboBox<ControlType> typeComboBox;
	private FloatTextField boomLengthField;
	private FloatTextField boomPitchField;
	private FloatTextField viewPitchField;

	private final VectorPanel panelA;
	private final VectorPanel panelB;
	private final VectorPanel panelC;

	private JCheckBox cbFlag;

	private boolean updatingFields = false;

	public CameraInfoPanel()
	{
		super(true);

		typeComboBox = new JComboBox<>(ControlType.values());
		typeComboBox.addActionListener(e -> {
			if (updatingFields)
				return;
			ControlType type = (ControlType) typeComboBox.getSelectedItem();
			MapEditor.execute(new SetCameraType(getData(), type));
		});
		SwingUtils.addBorderPadding(typeComboBox);

		boomLengthField = new FloatTextField((newValue) -> {
			MapEditor.execute(getData().boomLength.mutator(newValue));
		});
		boomLengthField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(boomLengthField);

		boomPitchField = new FloatTextField((newValue) -> {
			MapEditor.execute(getData().boomPitch.mutator(newValue));
		});
		boomPitchField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(boomPitchField);

		viewPitchField = new FloatTextField((newValue) -> {
			MapEditor.execute(getData().viewPitch.mutator(newValue));
		});
		viewPitchField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(viewPitchField);

		panelA = new VectorPanel(this, SwingUtils.getRedTextColor(), "Point A", 'A');
		panelB = new VectorPanel(this, SwingUtils.getBlueTextColor(), "Point B", 'B');
		panelC = new VectorPanel(this, SwingUtils.getGreenTextColor(), "Point C", 'C');

		cbFlag = new JCheckBox();
		cbFlag.addActionListener((e) -> {
			MapEditor.execute(new SetCameraFlag(getData(), cbFlag.isSelected()));
		});

		JLabel boomLabel = new JLabel("Boom*");
		boomLabel.setToolTipText("<html>These fields are:<br>"
			+ "Boom Length: Radial camera distance from target.<br>"
			+ "Boom Pitch: Boom angle measured upward from the ground.<br>"
			+ "View Pitch: Camera pitch adjustment, negative looking downward.</html>");

		setLayout(new MigLayout("fill, ins 0, hidemode 3"));

		add(new JLabel("Type"), "w 15%!");
		add(typeComboBox, "growx, pushx, wrap");

		add(cbFlag, "skip, wrap");

		add(boomLabel, "w 15%!");
		add(boomLengthField, "split 3, growx, sg boom");
		add(boomPitchField, "growx, sg boom");
		add(viewPitchField, "growx, sg boom, wrap");

		add(panelA, "span, growx, wrap");
		add(panelB, "span, growx, wrap");
		add(panelC, "span, growx, wrap");
	}

	private void updateFieldVisibility()
	{
		panelA.setVisible(false);
		panelB.setVisible(false);
		panelC.setVisible(false);

		switch (getData().getType()) {
			case TYPE_0:
			case TYPE_1:
			case TYPE_6:
				panelA.setVisible(true);
				panelB.setVisible(true);
				break;
			case TYPE_2:
			case TYPE_5:
				panelA.setVisible(true);
				panelB.setVisible(true);
				panelC.setVisible(true);
				break;
			case TYPE_4:
				panelA.setVisible(true);
				panelB.setVisible(true);
				break;
			case TYPE_3:
				break;
		}

		revalidate();
	}

	@Override
	public void updateFields(CameraZoneData cc, String tag)
	{
		if (getData() == cc) {
			updatingFields = true;
			typeComboBox.setSelectedItem(getData().getType());
			boomLengthField.setValue(getData().boomLength.get());
			boomPitchField.setValue(getData().boomPitch.get());
			viewPitchField.setValue(getData().viewPitch.get());

			cbFlag.setText(" " + getData().getType().flagName);

			panelA.setFields(getData().posA);
			panelB.setFields(getData().posB);
			panelC.setFields(getData().posC);

			updateFieldVisibility();

			cbFlag.setSelected(getData().getFlag());
			updatingFields = false;
		}
	}

	@Override
	public void tick(double deltaTime)
	{
		if (getData() == null)
			return;

		if (getData().posA.isTransforming())
			panelA.setFields(getData().posA);

		if (getData().posB.isTransforming())
			panelB.setFields(getData().posB);

		if (getData().posC.isTransforming())
			panelC.setFields(getData().posC);
	}

	private class VectorPanel extends JPanel
	{
		private FloatTextField xField;
		private FloatTextField yField;
		private FloatTextField zField;

		private VectorPanel(CameraInfoPanel infoPanel, Color c, String label, char pointID)
		{
			JLabel lbl = new JLabel(label);
			lbl.setForeground(c);
			xField = new FloatTextField(newValue -> {
				AbstractCommand cmd = new SetCameraPos(infoPanel.getData(), pointID, 0, newValue);
				MapEditor.execute(cmd);
			});
			yField = new FloatTextField(newValue -> {
				AbstractCommand cmd = new SetCameraPos(infoPanel.getData(), pointID, 1, newValue);
				MapEditor.execute(cmd);
			});
			zField = new FloatTextField(newValue -> {
				AbstractCommand cmd = new SetCameraPos(infoPanel.getData(), pointID, 2, newValue);
				MapEditor.execute(cmd);
			});
			xField.setHorizontalAlignment(SwingConstants.CENTER);
			yField.setHorizontalAlignment(SwingConstants.CENTER);
			zField.setHorizontalAlignment(SwingConstants.CENTER);
			SwingUtils.addBorderPadding(xField);
			SwingUtils.addBorderPadding(yField);
			SwingUtils.addBorderPadding(zField);

			setLayout(new MigLayout("fill, ins 0"));
			add(lbl, "w 15%!, shrink 0");

			add(xField, "split 3, pushx, growx, sg components");
			add(yField, "pushx, growx, sg components");
			add(zField, "pushx, growx, sg components");
		}

		public void setFields(SelectablePoint posA)
		{
			xField.setValue(posA.getX());
			yField.setValue(posA.getY());
			zField.setValue(posA.getZ());
		}
	}
}
