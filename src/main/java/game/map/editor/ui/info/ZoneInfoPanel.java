package game.map.editor.ui.info;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.MapObject.HitType;
import game.map.MapObject.SetObjectName;
import game.map.editor.MapEditor;
import game.map.editor.MapInfoPanel;
import game.map.editor.ui.SwingGUI;
import game.map.hit.Zone;
import net.miginfocom.swing.MigLayout;
import util.ui.NameTextField;

public class ZoneInfoPanel extends MapInfoPanel<Zone>
{
	private JTabbedPane tabs;

	private NameTextField nameField;
	private JLabel idLabel;

	private JCheckBox hasDataCheckbox;

	private CameraInfoPanel cameraPanel;

	public ZoneInfoPanel()
	{
		super(false);

		tabs = new JTabbedPane();
		tabs.addTab("Zone", createGeneralTab());

		setLayout(new MigLayout("fill, ins 0"));
		add(tabs, "span, grow, pushy");
	}

	private JPanel createGeneralTab()
	{
		idLabel = new JLabel();

		nameField = new NameTextField((name) -> {
			MapEditor.execute(new SetObjectName(getData(), name));
		});
		SwingUtils.addBorderPadding(nameField);

		hasDataCheckbox = new JCheckBox(" Has camera data?");
		hasDataCheckbox.addItemListener(e -> {
			final boolean value = hasDataCheckbox.isSelected();
			if (!ignoreEvents())
				MapEditor.execute(getData().hasCameraData.mutator(value));
		});

		cameraPanel = new CameraInfoPanel();

		JPanel tab = new JPanel();
		tab.setLayout(new MigLayout("fillx, ins n 16 0 16, hidemode 3"));

		tab.add(new JLabel("Name"), "w 15%!");
		tab.add(nameField, "w 50%!");
		tab.add(new JLabel("ID:", SwingConstants.RIGHT), "pushx, growx");
		tab.add(idLabel, "w 10%!, wrap");

		tab.add(hasDataCheckbox, "growx, span, wrap");
		tab.add(cameraPanel, "growx, span");

		return tab;
	}

	@Override
	public void afterSetData(Zone zone)
	{
		if (getData() == null)
			return;

		idLabel.setText("0x" + String.format("%X", getData().getNode().getTreeIndex()));

		if (getData().getType() == HitType.ROOT) {
			nameField.setEnabled(false);
			idLabel.setText("N/A");
		}
		else {
			nameField.setEnabled(true);
		}

		cameraPanel.setData(getData().camData);
	}

	@Override
	public void updateFields(Zone zone, String tag)
	{
		if (getData() == zone) {
			nameField.setValue(getData().getName());

			hasDataCheckbox.setSelected(getData().hasCameraData.get());
			cameraPanel.setVisible(getData().hasCameraData.get());
		}

		SwingGUI.instance().repaintObjectPanel();
	}
}
