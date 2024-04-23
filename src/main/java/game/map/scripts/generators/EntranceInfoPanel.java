package game.map.scripts.generators;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.ProjectDatabase;
import game.map.MapObject.MapObjectType;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.ui.BoundObjectPanel;
import game.map.editor.ui.LabelWithTip;
import game.map.editor.ui.ScriptManager;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.generators.Entrance.EntranceType;
import net.miginfocom.swing.MigLayout;
import util.ui.StringField;

public class EntranceInfoPanel extends JPanel implements IShutdownListener
{
	private static EntranceInfoPanel instance = null;

	public static EntranceInfoPanel instance()
	{
		if (instance == null) {
			instance = new EntranceInfoPanel();
			MapEditor.instance().registerOnShutdown(instance);
		}
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
	}

	private Entrance selectedEntrance;

	private boolean ignoreChanges = false;

	private JLabel entranceNameLabel;

	private StringField nameField;
	private JCheckBox cbHasCallback;

	private JComboBox<EntranceType> typeBox;

	private BoundObjectPanel entryPanel;
	private BoundObjectPanel door1ModelPanel;
	private BoundObjectPanel door2ModelPanel;

	private BoundObjectPanel pipeColliderPanel;
	private BoundObjectPanel pipeEntityPanel;

	private JComboBox<String> doorSoundBox;
	private JComboBox<String> doorSwingBox;
	private JPanel doorSoundPanel;
	private JPanel doorSwingPanel;

	private static final String doorTooltip = "<html>Door should be the only child of a transform group.<br>"
		+ "Its local position must line up with the world axis of the door in xz with its hinge along the y-axis.</html>";

	private EntranceInfoPanel()
	{
		nameField = new StringField((s) -> {
			if (ignoreChanges || selectedEntrance == null)
				return;
			MapEditor.execute(selectedEntrance.overrideName.mutator(s));
		});
		nameField.setHorizontalAlignment(SwingConstants.LEFT);

		cbHasCallback = new JCheckBox(" Add callback before using entrance");
		cbHasCallback.addActionListener((e) -> {
			if (ignoreChanges || selectedEntrance == null)
				return;
			MapEditor.execute(selectedEntrance.hasCallback.mutator(cbHasCallback.isSelected()));
		});

		typeBox = new JComboBox<>(EntranceType.values());
		typeBox.addActionListener((e) -> {
			if (ignoreChanges || selectedEntrance == null)
				return;
			MapEditor.execute(selectedEntrance.type.mutator((EntranceType) typeBox.getSelectedItem()));
		});
		SwingUtils.addBorderPadding(typeBox);

		entryPanel = new BoundObjectPanel(MarkerType.Entry, "Marker", (s) -> {
			if (ignoreChanges || selectedEntrance == null)
				return;
			MapEditor.execute(selectedEntrance.markerName.mutator(s));
		});

		door1ModelPanel = new BoundObjectPanel(MapObjectType.MODEL, "Door Model", doorTooltip, (s) -> {
			if (ignoreChanges || selectedEntrance == null)
				return;
			MapEditor.execute(selectedEntrance.door1Name.mutator(s));
		});

		door2ModelPanel = new BoundObjectPanel(MapObjectType.MODEL, "Door Model", doorTooltip, (s) -> {
			if (ignoreChanges || selectedEntrance == null)
				return;
			MapEditor.execute(selectedEntrance.door2Name.mutator(s));
		});

		pipeColliderPanel = new BoundObjectPanel(MapObjectType.COLLIDER, "Pipe Collider", (s) -> {
			if (ignoreChanges || selectedEntrance == null)
				return;
			MapEditor.execute(selectedEntrance.pipeCollider.mutator(s));
		});

		pipeEntityPanel = new BoundObjectPanel(MarkerType.Entity, "Pipe Entity", (s) -> {
			if (ignoreChanges || selectedEntrance == null)
				return;
			MapEditor.execute(selectedEntrance.warpPipeEntity.mutator(s));
		});

		doorSoundBox = new JComboBox<>(ProjectDatabase.EDoorSounds.getValues());
		doorSoundBox.addActionListener((e) -> {
			if (ignoreChanges || selectedEntrance == null)
				return;
			MapEditor.execute(selectedEntrance.doorSound.mutator((String) doorSoundBox.getSelectedItem()));
		});
		SwingUtils.addBorderPadding(doorSoundBox);

		doorSwingBox = new JComboBox<>(ProjectDatabase.EDoorSwings.getValues());
		doorSwingBox.addActionListener((e) -> {
			if (ignoreChanges || selectedEntrance == null)
				return;
			MapEditor.execute(selectedEntrance.doorSwing.mutator((String) doorSwingBox.getSelectedItem()));
		});
		SwingUtils.addBorderPadding(doorSwingBox);

		doorSoundPanel = new JPanel(new MigLayout("fill, ins 0"));
		doorSoundPanel.add(SwingUtils.getLabel("Door Sounds", 12), "w 80!, gapleft 8, gapright 8, split 2");
		doorSoundPanel.add(doorSoundBox, "growx");

		doorSwingPanel = new JPanel(new MigLayout("fill, ins 0"));
		doorSwingPanel.add(SwingUtils.getLabel("Door Swing", 12), "w 80!, gapleft 8, gapright 8, split 2");
		doorSwingPanel.add(doorSwingBox, "growx");

		setLayout(new MigLayout("fill, wrap, hidemode 3"));

		entranceNameLabel = SwingUtils.getLabel("", 14);
		add(entranceNameLabel, "gapbottom 8");

		add(new LabelWithTip("Name", "Expected to be unique."), "w 80!, gapleft 8, gapright 8, split 2");
		add(nameField, "growx");

		add(SwingUtils.getLabel("Type", 12), "w 80!, gapleft 8, gapright 8, split 2");
		add(typeBox, "growx");

		add(entryPanel, "growx");

		add(pipeColliderPanel, "grow");
		add(pipeEntityPanel, "grow");
		add(door1ModelPanel, "growx");
		add(door2ModelPanel, "grow");
		add(doorSwingPanel, "grow");
		add(doorSoundPanel, "grow");

		add(new JLabel(""), "w 80!, gapleft 8, gapright 8, gaptop 8, split 2");
		add(cbHasCallback, "grow");

		add(new JLabel(""), "pushy");
	}

	public void updateFields(Entrance entrance)
	{
		if (entrance != null && selectedEntrance == entrance) {
			ignoreChanges = true;

			nameField.setText(entrance.overrideName.get());
			cbHasCallback.setSelected(entrance.hasCallback.get());

			if (entrance.markerName != null)
				entranceNameLabel.setText("Entrance via " + entrance.markerName);
			else
				entranceNameLabel.setText("Invalid Entrance");

			typeBox.setSelectedItem(entrance.type.get());
			entryPanel.setText(entrance.markerName.get());

			door1ModelPanel.setText(entrance.door1Name.get());
			door2ModelPanel.setText(entrance.door2Name.get());

			pipeColliderPanel.setText(entrance.pipeCollider.get());
			pipeEntityPanel.setText(entrance.warpPipeEntity.get());

			doorSoundBox.setSelectedItem(entrance.doorSound.get());
			doorSwingBox.setSelectedItem(entrance.doorSwing.get());

			door1ModelPanel.setVisible(false);
			door2ModelPanel.setVisible(false);
			doorSoundPanel.setVisible(false);
			doorSwingPanel.setVisible(false);
			pipeEntityPanel.setVisible(false);
			pipeColliderPanel.setVisible(false);

			switch (entrance.type.get()) {
				case Teleport:
				case Walk:
					break;
				case SingleDoor:
					door1ModelPanel.setVisible(true);
					doorSoundPanel.setVisible(true);
					doorSwingPanel.setVisible(true);
					door1ModelPanel.setLabelText("Door");
					break;
				case DoubleDoor:
					door1ModelPanel.setVisible(true);
					door2ModelPanel.setVisible(true);
					doorSoundPanel.setVisible(true);
					door1ModelPanel.setLabelText("Left Door");
					door2ModelPanel.setLabelText("Right Door");
					break;
				case BlueWarpPipe:
					pipeEntityPanel.setVisible(true);
					break;
				case HorizontalPipe:
					pipeColliderPanel.setVisible(true);
					break;
				case VerticalPipe:
					break;
			}

			ignoreChanges = false;
		}

		ScriptManager.instance().updateGeneratorTree();
	}

	public void setEntrance(Entrance entrance)
	{
		selectedEntrance = entrance;
		setVisible(entrance != null);
		if (entrance == null)
			return;

		updateFields(entrance);
	}
}
