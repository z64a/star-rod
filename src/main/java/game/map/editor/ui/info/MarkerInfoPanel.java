package game.map.editor.ui.info;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.entity.EntityInfo.EntityType;
import game.map.MapObject.SetObjectName;
import game.map.MutableAngle;
import game.map.MutablePoint;
import game.map.editor.MapEditor;
import game.map.editor.MapInfoPanel;
import game.map.editor.ui.SwingGUI;
import game.map.editor.ui.info.marker.CamTargetSubpanel;
import game.map.editor.ui.info.marker.EntitySubpanel;
import game.map.editor.ui.info.marker.GridSubpanel;
import game.map.editor.ui.info.marker.NpcSubpanel;
import game.map.editor.ui.info.marker.PathSubpanel;
import game.map.editor.ui.info.marker.TerritoryTab;
import game.map.editor.ui.info.marker.VolumeSubpanel;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.Marker.SetAngle;
import game.map.marker.Marker.SetType;
import game.map.marker.Marker.SetX;
import game.map.marker.Marker.SetY;
import game.map.marker.Marker.SetZ;
import net.miginfocom.swing.MigLayout;
import util.ui.FloatTextField;
import util.ui.IntTextField;
import util.ui.NameTextField;

public class MarkerInfoPanel extends MapInfoPanel<Marker> implements ActionListener
{
	private JTabbedPane tabs;
	private JPanel leafPanel;

	private NameTextField nameField;
	private JComboBox<MarkerType> markerTypeBox;
	private IntTextField posXField;
	private IntTextField posYField;
	private IntTextField posZField;
	private FloatTextField angleField;

	private JLabel heightAboveGroundLabel;

	private Container subpanelContainer;
	private VolumeSubpanel volumeSubpanel;
	private GridSubpanel gridSubpanel;
	private PathSubpanel pathSubpanel;
	private EntitySubpanel entitySubpanel;
	private NpcSubpanel npcSubpanel;

	private boolean movementTabAvailable = false;
	private TerritoryTab territoryTab;

	//private MarkerCameraTab cameraTab;

	private CamTargetSubpanel cameraSubpanel;

	// update tags (not used much)

	public static final String tag_GeneralTab = "GeneralTab";
	public static final String tag_NPCMovementTab = "NPCMovementTab";
	public static final String tag_EntityTab = "EntityTab";
	public static final String tag_CameraTab = "CameraTab";
	public static final String tag_SetSprite = "SetSprite";

	public MarkerInfoPanel()
	{
		super(true);

		territoryTab = new TerritoryTab(this);

		tabs = new JTabbedPane();
		tabs.addTab("Marker", createGeneralTab());

		setLayout(new MigLayout("fill, ins 0"));
		add(tabs, "span, grow, pushy");
	}

	public static final String FIELD_NAME_WIDTH = "18%!";
	public static final String FOUR_COLUMNS = "[" + FIELD_NAME_WIDTH + "][sg xyz][sg xyz][sg xyz]";

	private JPanel createGeneralTab()
	{
		nameField = new NameTextField((name) -> MapEditor.execute(new SetObjectName(getData(), name)));
		SwingUtils.addBorderPadding(nameField);

		markerTypeBox = new JComboBox<>(MarkerType.values());
		markerTypeBox.setMaximumRowCount(MarkerType.values().length);
		markerTypeBox.removeItem(MarkerType.Root);
		markerTypeBox.removeItem(MarkerType.Group);
		markerTypeBox.addActionListener(e -> {
			if (ignoreEvents())
				return;

			MarkerType type = (MarkerType) markerTypeBox.getSelectedItem();
			MapEditor.execute(new SetType(getData(), type));
		});
		SwingUtils.addBorderPadding(markerTypeBox);

		posXField = new IntTextField((v) -> MapEditor.execute(new SetX(getData(), v)));
		posYField = new IntTextField((v) -> MapEditor.execute(new SetY(getData(), v)));
		posZField = new IntTextField((v) -> MapEditor.execute(new SetZ(getData(), v)));
		posXField.setHorizontalAlignment(SwingConstants.CENTER);
		posYField.setHorizontalAlignment(SwingConstants.CENTER);
		posZField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addVerticalBorderPadding(posXField);
		SwingUtils.addVerticalBorderPadding(posYField);
		SwingUtils.addVerticalBorderPadding(posZField);

		heightAboveGroundLabel = new JLabel("", SwingConstants.CENTER);

		angleField = new FloatTextField((angle) -> MapEditor.execute(new SetAngle(getData(), angle)));
		angleField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addVerticalBorderPadding(angleField);

		leafPanel = new JPanel(new MigLayout("fillx, ins 0", FOUR_COLUMNS));

		leafPanel.add(new JLabel("Type"), "sgy row");
		leafPanel.add(markerTypeBox, "growx, span 2, wrap");

		leafPanel.add(new JLabel("Position"), "sgy row");
		leafPanel.add(posXField, "growx");
		leafPanel.add(posYField, "growx");
		leafPanel.add(posZField, "growx, wrap");

		leafPanel.add(new JLabel("Angle"), "sgy row");
		leafPanel.add(angleField, "growx");

		leafPanel.add(heightAboveGroundLabel, "span, growx, wrap");

		subpanelContainer = new Container();
		subpanelContainer.setLayout(new MigLayout("fillx, ins 0"));

		npcSubpanel = new NpcSubpanel(this);
		pathSubpanel = new PathSubpanel(this);
		gridSubpanel = new GridSubpanel(this);
		volumeSubpanel = new VolumeSubpanel(this);
		entitySubpanel = new EntitySubpanel(this);
		cameraSubpanel = new CamTargetSubpanel(this);

		JPanel commonMarkerPanel = new JPanel();
		commonMarkerPanel.setLayout(new MigLayout("fillx, ins 0"));
		commonMarkerPanel.add(new JLabel("Name"), "w " + FIELD_NAME_WIDTH);
		commonMarkerPanel.add(nameField, "growx, pushx, wrap");

		JPanel generalTab = new JPanel();
		generalTab.setLayout(new MigLayout("fillx, ins n 16 n 16, hidemode 3, wrap"));

		generalTab.add(commonMarkerPanel, "growx");
		generalTab.add(leafPanel, "growx");
		generalTab.add(subpanelContainer, "grow, pushy");

		return generalTab;
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		if (ignoreEvents())
			return;

		String cmd = e.getActionCommand();
		EntityType type = EntityType.valueOf(cmd);
		MapEditor.execute(getData().entityComponent.type.mutator(type));
	}

	@Override
	public void afterSetData(Marker newData)
	{
		if (getData() == null)
			return;

		entitySubpanel.onSetData();
	}

	@Override
	public void updateFields(Marker marker, String tag)
	{
		if (getData() == marker) {
			nameField.setValue(getData().getName());
			markerTypeBox.setSelectedItem(getData().getType());

			if (getData().getType() == MarkerType.Root || getData().getType() == MarkerType.Group) {
				leafPanel.setVisible(false);
				subpanelContainer.setVisible(false);
			}
			else {
				leafPanel.setVisible(true);
				subpanelContainer.setVisible(true);
				updateDynamicFields(true);
			}

			subpanelContainer.removeAll();

			switch (marker.type) {
				case Sphere:
				case Cylinder:
				case Volume:
					subpanelContainer.add(volumeSubpanel, "growx");
					volumeSubpanel.updateFields();
					break;
				case Path:
					subpanelContainer.add(pathSubpanel, "grow, pushy");
					pathSubpanel.updateFields();
					break;
				case BlockGrid:
					subpanelContainer.add(gridSubpanel, "growx");
					gridSubpanel.updateFields();
					break;
				case NPC:
					subpanelContainer.add(npcSubpanel, "growx");
					territoryTab.updateDynamicFields(true);
					territoryTab.updateFields();
					npcSubpanel.updateFields();
					break;
				case Entity:
					subpanelContainer.add(entitySubpanel, "growx");
					entitySubpanel.onUpdateFields();
					break;
				case CamTarget:
					subpanelContainer.add(cameraSubpanel, "growx");
					cameraSubpanel.updateFields();
					break;
				default:
					break;
			}

			subpanelContainer.repaint();

			// npc movement tab visibility
			if (getData().getType() == MarkerType.NPC) {
				if (!movementTabAvailable) {
					tabs.addTab("Territory", territoryTab);
					movementTabAvailable = true;
				}
			}
			else {
				if (movementTabAvailable) {
					tabs.remove(territoryTab);
					movementTabAvailable = false;
				}
			}
		}
		SwingGUI.instance().repaintObjectPanel();
	}

	@Override
	public void tick(double deltaTime)
	{
		if (getData() == null || !isShowing())
			return;

		Marker m = getData();

		int height = (int) m.heightAboveGround;

		String text;
		if (m.heightAboveGround == Float.MAX_VALUE)
			text = "<html><i>No ground below!</i><html>";
		else if (height == 1)
			text = "<html><i>1 unit above ground</i><html>";
		else
			text = "<html><i>" + height + " units above ground</i><html>";

		heightAboveGroundLabel.setText(text);

		updateDynamicFields(false);
	}

	private void updateDynamicFields(boolean force)
	{
		if (getData() == null)
			return;

		MutablePoint point = getData().position;
		MutableAngle angle = getData().yaw;

		if (force || point.isTransforming()) {
			posXField.setValue(point.getX());
			posYField.setValue(point.getY());
			posZField.setValue(point.getZ());
		}

		if (force || angle.isTransforming()) {
			angleField.setValue((float) getData().yaw.getAngle());
		}

		switch (getData().getType()) {
			case NPC:
				territoryTab.updateDynamicFields(force);
				break;
			case Path:
				pathSubpanel.updateDynamicFields(force);
				break;
			case CamTarget:
				cameraSubpanel.updateDynamicFields(force);
				break;
			default:
		}
	}
}
