package game.map.editor.ui.info.marker;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import common.Vector3f;
import game.map.MutablePoint.SetPosition;
import game.map.editor.MapEditor;
import game.map.editor.ui.info.CameraInfoPanel;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.marker.CamTargetComponent;
import game.map.marker.Marker;
import net.miginfocom.swing.MigLayout;
import util.ui.FloatTextField;

public class CamTargetSubpanel extends JPanel
{
	private final MarkerInfoPanel parent;

	private JButton btUseCurrentView;
	private JButton btLookAtTarget;
	private CameraInfoPanel cameraPanel;

	private JCheckBox cbCamGeneratePan;
	private FloatTextField camMoveSpeedField;

	private JCheckBox cbCamSampleZone;
	private JLabel sampleNameLabel;

	private JPanel samplePanel;
	private JCheckBox cbCamOverrideLength;
	private JCheckBox cbCamOverrideAngles;
	private FloatTextField boomLengthField;
	private FloatTextField boomPitchField;
	private FloatTextField viewPitchField;

	public CamTargetSubpanel(MarkerInfoPanel parent)
	{
		this.parent = parent;

		boomLengthField = new FloatTextField((newValue) -> {
			MapEditor.execute(parent.getData().cameraComponent.boomLength.mutator(newValue));
		});
		boomLengthField.setHorizontalAlignment(SwingConstants.CENTER);

		boomPitchField = new FloatTextField((newValue) -> {
			MapEditor.execute(parent.getData().cameraComponent.boomPitch.mutator(newValue));
		});
		boomPitchField.setHorizontalAlignment(SwingConstants.CENTER);

		viewPitchField = new FloatTextField((newValue) -> {
			MapEditor.execute(parent.getData().cameraComponent.viewPitch.mutator(newValue));
		});
		viewPitchField.setHorizontalAlignment(SwingConstants.CENTER);

		camMoveSpeedField = new FloatTextField((newValue) -> {
			MapEditor.execute(parent.getData().cameraComponent.moveSpeed.mutator(newValue));
		});
		camMoveSpeedField.setHorizontalAlignment(SwingConstants.CENTER);

		cbCamSampleZone = new JCheckBox(" Use settings from zone below");
		cbCamSampleZone.addActionListener((e) -> {
			MapEditor.execute(parent.getData().cameraComponent.useZone.mutator(cbCamSampleZone.isSelected()));
		});

		cbCamOverrideLength = new JCheckBox(" Override boom length");
		cbCamOverrideLength.addActionListener((e) -> {
			MapEditor.execute(parent.getData().cameraComponent.overrideDist.mutator(cbCamOverrideLength.isSelected()));
		});

		cbCamOverrideAngles = new JCheckBox(" Override pitch angles");
		cbCamOverrideAngles.addActionListener((e) -> {
			MapEditor.execute(parent.getData().cameraComponent.overrideAngles.mutator(cbCamOverrideAngles.isSelected()));
		});

		cbCamGeneratePan = new JCheckBox(" Generate pan motion");
		cbCamGeneratePan.addActionListener((e) -> {
			MapEditor.execute(parent.getData().cameraComponent.generatePan.mutator(cbCamGeneratePan.isSelected()));
		});

		sampleNameLabel = new JLabel();

		samplePanel = new JPanel(new MigLayout("fill, ins 0, hidemode 3", "[40%][sg vec][sg vec]"));

		samplePanel.add(cbCamOverrideLength, "growx");
		samplePanel.add(boomLengthField, "growx, span, wrap");
		samplePanel.add(cbCamOverrideAngles, "growx");
		samplePanel.add(boomPitchField, "growx");
		samplePanel.add(viewPitchField, "growx, wrap");

		cameraPanel = new CameraInfoPanel();

		btUseCurrentView = new JButton("Use Current View");
		btUseCurrentView.addActionListener((e) -> {
			Marker m = parent.getData();
			if (m != null)
				MapEditor.instance().setCameraParams(m.cameraComponent.controlData, m.position.getVector());
		});
		btUseCurrentView.setToolTipText("Set camera parameters for target to match current view.");

		btLookAtTarget = new JButton("Look at Target");
		btLookAtTarget.addActionListener((e) -> {
			Marker m = parent.getData();
			if (m != null)
				MapEditor.instance().cameraLookAt(m.position.getVector());
		});
		btLookAtTarget.setToolTipText("Reorient current view to center on this target.");

		JPanel sharedPanel = new JPanel(new MigLayout("fill, ins 0, hidemode 3", "[40%][sg vec][sg vec]"));
		sharedPanel.add(cbCamGeneratePan, "growx");
		sharedPanel.add(camMoveSpeedField, "growx");

		JButton btTogglePreview = new JButton("Toggle Preview");
		btTogglePreview.addActionListener((e) -> {
			Marker m = parent.getData();
			if (m != null)
				MapEditor.instance().toggleCameraTargetMarker(m);
		});
		btTogglePreview.setToolTipText("Preview this target's camera.");

		JButton btCenterMarker = new JButton("Center Marker");
		btCenterMarker.addActionListener((e) -> {
			Marker m = parent.getData();
			if (m != null) {
				CamTargetComponent target = parent.getData().cameraComponent;
				float boomLen = target.boomLength.get();

				if (target.useZone.get() && !target.overrideDist.get())
					boomLen = target.controlData.boomLength.get();

				Vector3f newPos = MapEditor.instance().getPointAhead(boomLen);

				MapEditor.execute(new SetPosition(m.position, newPos));
			}
		});
		btCenterMarker.setToolTipText("Move marker to viewport center, with distance = boomLength.");

		setLayout(new MigLayout("fillx, ins 0, wrap 2, hidemode 2", "[grow, sg half][grow, sg half]"));

		add(sharedPanel, "span, growx");
		add(cbCamSampleZone, "span, split 2");
		add(sampleNameLabel, "growx");
		add(samplePanel, "span, growx");
		add(cameraPanel, "span, growx");
		add(btUseCurrentView, "h 32!, growx");
		add(btLookAtTarget, "h 32!, growx");
		add(btTogglePreview, "h 32!, growx");
		add(btCenterMarker, "h 32!, growx");
	}

	public void updateFields()
	{
		CamTargetComponent target = parent.getData().cameraComponent;

		boolean useZone = target.useZone.get();
		cbCamSampleZone.setSelected(useZone);
		sampleNameLabel.setVisible(useZone);
		samplePanel.setVisible(useZone);
		btUseCurrentView.setVisible(!useZone);
		btLookAtTarget.setVisible(!useZone);
		cameraPanel.setVisible(!useZone);

		boolean generatePan = target.generatePan.get();
		cbCamGeneratePan.setSelected(generatePan);
		camMoveSpeedField.setValue(target.moveSpeed.get());
		camMoveSpeedField.setEnabled(generatePan);

		if (useZone) {
			boolean overrideDist = target.overrideDist.get();
			cbCamOverrideLength.setSelected(overrideDist);
			boomLengthField.setValue(target.boomLength.get());
			boomLengthField.setEnabled(overrideDist);

			boolean overrideAngles = target.overrideAngles.get();
			cbCamOverrideAngles.setSelected(overrideAngles);
			boomPitchField.setValue(target.boomPitch.get());
			viewPitchField.setValue(target.viewPitch.get());
			boomPitchField.setEnabled(overrideAngles);
			viewPitchField.setEnabled(overrideAngles);
		}
		else
			cameraPanel.setData(target.controlData);
	}

	public void updateDynamicFields(boolean force)
	{
		CamTargetComponent target = parent.getData().cameraComponent;

		if (target.useZone.get()) {
			if (target.sampleHitPos == null) {
				sampleNameLabel.setText("<html><i>(NOT FOUND!)</i><html>");
				sampleNameLabel.setForeground(SwingUtils.getRedTextColor());
			}
			else {
				sampleNameLabel.setText("<html><i>(found " + target.sampleZone.getName() + ")</i><html>");
				sampleNameLabel.setForeground(null);
			}
		}
	}
}
