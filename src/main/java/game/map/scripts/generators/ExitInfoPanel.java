package game.map.scripts.generators;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.MapObject.MapObjectType;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.ui.BoundObjectPanel;
import game.map.editor.ui.LabelWithTip;
import game.map.editor.ui.ScriptManager;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.generators.Exit.ExitType;
import net.miginfocom.swing.MigLayout;
import util.ui.StringField;

public class ExitInfoPanel extends JPanel implements IShutdownListener
{
	private static ExitInfoPanel instance = null;

	public static ExitInfoPanel instance()
	{
		if (instance == null) {
			instance = new ExitInfoPanel();
			MapEditor.instance().registerOnShutdown(instance);
		}
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
	}

	private Exit selectedExit;

	private boolean ignoreChanges = false;

	private JLabel exitNameLabel;

	private StringField nameField;
	private JCheckBox cbHasCallback;

	private JComboBox<ExitType> typeBox;

	private BoundObjectPanel entryPanel;
	private BoundObjectPanel colliderPanel;
	private BoundObjectPanel door1ModelPanel;
	private BoundObjectPanel door2ModelPanel;
	private BoundObjectPanel lockEntityPanel;

	private StringField destField;
	private StringField destMarkerField;
	private JCheckBox cbDestUseIndex;

	private JComboBox<String> doorSoundBox;
	private JComboBox<String> doorSwingBox;

	private JPanel doorSoundPanel;
	private JPanel doorSwingPanel;

	private static final String doorTooltip = "<html>Door should be the only child of a transform group.<br>"
		+ "Its local position must line up with the world axis of the door in xz with its hinge along the y-axis.</html>";

	private ExitInfoPanel()
	{
		nameField = new StringField((s) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.overrideName.mutator(s));
		});
		nameField.setHorizontalAlignment(SwingConstants.LEFT);

		cbHasCallback = new JCheckBox(" Add callback after using exit");
		cbHasCallback.addActionListener((e) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.hasCallback.mutator(cbHasCallback.isSelected()));
		});

		typeBox = new JComboBox<>(ExitType.values());
		typeBox.addActionListener((e) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.type.mutator((ExitType) typeBox.getSelectedItem()));
		});
		SwingUtils.addBorderPadding(typeBox);

		entryPanel = new BoundObjectPanel(MarkerType.Entry, "Exit Marker", (s) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.markerName.mutator(s));
		});

		colliderPanel = new BoundObjectPanel(MapObjectType.COLLIDER, "Exit Collider", (s) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.colliderName.mutator(s));
		});

		door1ModelPanel = new BoundObjectPanel(MapObjectType.MODEL, "Left Door", doorTooltip, (s) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.door1Name.mutator(s));
		});

		door2ModelPanel = new BoundObjectPanel(MapObjectType.MODEL, "Right Door", doorTooltip, (s) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.door2Name.mutator(s));
		});

		lockEntityPanel = new BoundObjectPanel(MarkerType.Entity, "Lock Entity", "Optional", (s) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.lockName.mutator(s));
		});

		destField = new StringField((s) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.destMap.mutator(s));
		});
		destField.setHorizontalAlignment(SwingConstants.LEFT);

		destMarkerField = new StringField((s) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.destMarkerName.mutator(s));
		});
		destMarkerField.setHorizontalAlignment(SwingConstants.LEFT);

		cbDestUseIndex = new JCheckBox("Interpret as ID");
		cbDestUseIndex.setToolTipText("Should the name to the left be interpreted as a marker name or an ID number?");
		cbDestUseIndex.addActionListener((e) -> {
			if (ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.useDestMarkerID.mutator(cbDestUseIndex.isSelected()));
		});

		//XXX
		doorSoundBox = new JComboBox<>();
		/*
		doorSoundBox = new JComboBox<>(ProjectDatabase.DoorSoundsType.getValues());
		doorSoundBox.addActionListener((e) -> {
			if(ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.doorSound.mutator((String)doorSoundBox.getSelectedItem()));
		})
		*/

		SwingUtils.addBorderPadding(doorSoundBox);

		//XXX
		doorSwingBox = new JComboBox<>();
		/*
		doorSwingBox = new JComboBox<>(ProjectDatabase.DoorSwingsType.getValues());
		doorSwingBox.addActionListener((e) -> {
			if(ignoreChanges || selectedExit == null)
				return;
			MapEditor.execute(selectedExit.doorSwing.mutator((String)doorSwingBox.getSelectedItem()));
		});
		*/
		SwingUtils.addBorderPadding(doorSwingBox);

		doorSoundPanel = new JPanel(new MigLayout("fill, ins 0"));
		doorSoundPanel.add(SwingUtils.getLabel("Door Sounds", 12), "w 80!, gapleft 8, gapright 8, split 2");
		doorSoundPanel.add(doorSoundBox, "growx");

		doorSwingPanel = new JPanel(new MigLayout("fill, ins 0"));
		doorSwingPanel.add(SwingUtils.getLabel("Door Swing", 12), "w 80!, gapleft 8, gapright 8, split 2");
		doorSwingPanel.add(doorSwingBox, "growx");

		setLayout(new MigLayout("fill, wrap, hidemode 3"));

		exitNameLabel = SwingUtils.getLabel("", 14);

		add(exitNameLabel, "gapbottom 8");

		add(new LabelWithTip("Name", "Expected to be unique."), "w 80!, gapleft 8, gapright 8, split 2");
		add(nameField, "growx");

		add(SwingUtils.getLabel("Exit Type", 12), "w 80!, gapleft 8, gapright 8, split 2");
		add(typeBox, "growx");

		add(SwingUtils.getLabel("Dest Map", 12), "w 80!, gapleft 8, gapright 8, split 2");
		add(destField, "growx");

		add(SwingUtils.getLabel("Dest Marker", 12), "w 80!, gapleft 8, gapright 8, split 3");
		add(destMarkerField, "growx");
		add(cbDestUseIndex, "w 120!");

		add(entryPanel, "growx");
		add(colliderPanel, "growx");
		add(door1ModelPanel, "growx");
		add(door2ModelPanel, "grow");

		add(door1ModelPanel, "grow");
		add(door2ModelPanel, "grow");
		add(doorSwingPanel, "grow");
		add(doorSoundPanel, "grow");
		add(lockEntityPanel, "grow");

		add(new JLabel(""), "w 80!, gapleft 8, gapright 8, gaptop 8, split 2");
		add(cbHasCallback, "grow");

		add(new JLabel(""), "pushy");
	}

	public void updateFields(Exit exit)
	{
		if (exit != null && selectedExit == exit) {
			ignoreChanges = true;

			nameField.setText(exit.overrideName.get());
			cbHasCallback.setSelected(exit.hasCallback.get());

			if (exit.markerName.get() != null && !exit.markerName.get().isEmpty())
				exitNameLabel.setText("Exit via " + exit.markerName.get());
			else
				exitNameLabel.setText("Invalid Exit");

			typeBox.setSelectedItem(exit.type.get());
			entryPanel.setText(exit.markerName.get());
			colliderPanel.setText(exit.colliderName.get());
			destField.setText(exit.destMap.get());
			destMarkerField.setText(exit.destMarkerName.get());
			cbDestUseIndex.setSelected(exit.useDestMarkerID.get());

			door1ModelPanel.setText(exit.door1Name.get());
			door2ModelPanel.setText(exit.door2Name.get());
			lockEntityPanel.setText(exit.lockName.get());
			doorSoundBox.setSelectedItem(exit.doorSound.get());
			doorSwingBox.setSelectedItem(exit.doorSwing.get());

			door1ModelPanel.setVisible(false);
			door2ModelPanel.setVisible(false);
			doorSoundPanel.setVisible(false);
			doorSwingPanel.setVisible(false);
			lockEntityPanel.setVisible(false);

			switch (exit.type.get()) {
				case Walk:
				case HorizontalPipe:
				case VerticalPipe:
					break;
				case SingleDoor:
					door1ModelPanel.setVisible(true);
					doorSoundPanel.setVisible(true);
					doorSwingPanel.setVisible(true);
					lockEntityPanel.setVisible(true);
					door1ModelPanel.setLabelText("Door");
					break;
				case DoubleDoor:
					door1ModelPanel.setVisible(true);
					door2ModelPanel.setVisible(true);
					doorSoundPanel.setVisible(true);
					lockEntityPanel.setVisible(true);
					door1ModelPanel.setLabelText("Left Door");
					door2ModelPanel.setLabelText("Right Door");
					break;
			}

			ignoreChanges = false;
		}

		ScriptManager.instance().updateGeneratorTree();
	}

	public void setExit(Exit exit)
	{
		selectedExit = exit;
		setVisible(exit != null);
		if (exit == null)
			return;

		updateFields(exit);
	}
}
