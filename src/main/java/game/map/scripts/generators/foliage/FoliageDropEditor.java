package game.map.scripts.generators.foliage;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import app.SwingUtils;
import game.DecompEnum;
import game.ProjectDatabase;
import game.map.Map;
import game.map.editor.ui.LabelWithTip;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import net.miginfocom.swing.MigLayout;

public class FoliageDropEditor extends JPanel
{
	private final FoliageDrop drop;

	private final JComboBox<String> spawnModeBox;
	private final JComboBox<String> itemNameBox;
	private final JComboBox<String> markerNameBox;
	private final JComboBox<String> spawnFlagBox;
	private final JComboBox<String> pickupFlagBox;

	//TODO change to index?
	private static final String[] AREA_FLAG_NAMES;
	static {
		AREA_FLAG_NAMES = new String[0x100];
		for (int i = 0; i < AREA_FLAG_NAMES.length; i++)
			AREA_FLAG_NAMES[i] = "" + i;
	}

	public FoliageDropEditor(Map map, FoliageDrop original)
	{
		drop = original.deepCopy();

		DecompEnum spawnModeEnum = ProjectDatabase.EItemSpawnModes;
		spawnModeBox = new JComboBox<>(spawnModeEnum.getValues());
		spawnModeBox.addActionListener((e) -> {
			Integer v = spawnModeEnum.getID((String) spawnModeBox.getSelectedItem());
			if (v == null)
				return;
			drop.type = v;
		});
		SwingUtils.addBorderPadding(spawnModeBox);

		String initialTypeString = spawnModeEnum.getName(drop.type);
		spawnModeBox.setMaximumRowCount(16);
		spawnModeBox.setEditable(true);
		spawnModeBox.setSelectedItem(initialTypeString != null ? initialTypeString : String.format("%X", drop.type));

		itemNameBox = new JComboBox<>(ProjectDatabase.getItemNames().toArray(new String[0]));
		itemNameBox.addActionListener((e) -> {
			drop.itemName = (String) itemNameBox.getSelectedItem();
		});
		SwingUtils.addBorderPadding(itemNameBox);

		itemNameBox.setMaximumRowCount(16);
		itemNameBox.setEditable(true);
		itemNameBox.setSelectedItem(drop.itemName);

		List<String> markerNames = new ArrayList<>();
		for (Marker m : map.markerTree) {
			if (m.getType() == MarkerType.Position)
				markerNames.add(m.getName());
		}
		String[] markerBoxValues = new String[markerNames.size()];
		for (int i = 0; i < markerBoxValues.length; i++)
			markerBoxValues[i] = markerNames.get(i);

		markerNameBox = new JComboBox<>(markerBoxValues);
		markerNameBox.addActionListener((e) -> {
			drop.markerName = (String) markerNameBox.getSelectedItem();
		});
		SwingUtils.addBorderPadding(markerNameBox);

		markerNameBox.setMaximumRowCount(16);
		markerNameBox.setEditable(true);
		markerNameBox.setSelectedItem(drop.markerName);

		spawnFlagBox = new JComboBox<>(AREA_FLAG_NAMES);
		spawnFlagBox.addActionListener((e) -> {
			drop.spawnFlag = (String) spawnFlagBox.getSelectedItem();
		});
		SwingUtils.addBorderPadding(spawnFlagBox);

		spawnFlagBox.setMaximumRowCount(16);
		spawnFlagBox.setEditable(true);
		spawnFlagBox.setSelectedItem(drop.spawnFlag);

		List<String> modFlagNames = ProjectDatabase.getSavedFlagNames();
		String[] modFlagBoxValues = new String[modFlagNames.size()];
		for (int i = 0; i < modFlagBoxValues.length; i++)
			modFlagBoxValues[i] = modFlagNames.get(i);

		pickupFlagBox = new JComboBox<>(modFlagBoxValues);
		pickupFlagBox.addActionListener((e) -> {
			drop.pickupFlag = (String) pickupFlagBox.getSelectedItem();
		});
		SwingUtils.addBorderPadding(pickupFlagBox);

		pickupFlagBox.setMaximumRowCount(16);
		pickupFlagBox.setEditable(true);
		pickupFlagBox.setSelectedItem(drop.pickupFlag);

		setLayout(new MigLayout("fill, wrap 2", "[]16[200::]"));
		add(new JLabel("Spawn Mode"));
		add(spawnModeBox, "growx");

		add(new JLabel("Item Name"));
		add(itemNameBox, "growx");

		add(new LabelWithTip("Spawn Marker", "Position marker where this drop will spawn."));
		add(markerNameBox, "growx");

		add(new LabelWithTip("Spawn Flag", "Set when spawned, use to require re-zoning etc before item can drop again."));
		add(spawnFlagBox, "growx");

		add(new LabelWithTip("Pickup Flag", "Set when picked up, use for one-time items. Ex: *ModFlag[200]"));
		add(pickupFlagBox, "growx");
	}

	public FoliageDrop getEditedDrop()
	{ return drop; }
}
