package game.map.editor.ui.info.marker;

import static game.map.MapKey.*;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;

import com.alexandriasoftware.swing.JSplitButton;

import app.SwingUtils;
import game.ProjectDatabase;
import game.entity.EntityInfo.EntityParam;
import game.entity.EntityInfo.EntityType;
import game.entity.EntityMenuGroup;
import game.map.MapKey;
import game.map.MapObject.MapObjectType;
import game.map.editor.MapEditor;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.ui.MapObjectComboBox;
import game.map.editor.ui.StandardEditableComboBox;
import game.map.editor.ui.SwingGUI;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.marker.EntityComponent;
import game.map.marker.Marker.MarkerType;
import net.miginfocom.swing.MigLayout;
import util.ui.IntTextField;
import util.ui.StringField;
import util.ui.StringSelectorDialog;

public class EntitySubpanel extends JPanel
{
	private static class FieldPanel extends JPanel
	{
		private FieldPanel(String text, JCheckBox cb, JComponent comp)
		{
			setLayout(new MigLayout("fillx, ins 0", "[][grow][70%]"));
			add(cb);
			add(new JLabel(text));
			add(comp, "growx");
		}

		private FieldPanel(String text, JCheckBox cb, JComponent comp, JButton but)
		{
			setLayout(new MigLayout("fillx, ins 0", "[][grow][][]"));
			add(cb);
			add(new JLabel(text));
			add(comp, "w 61%!");
			add(but, "w 7%!");
		}

		private FieldPanel(String text, JCheckBox cb, JComponent comp, JButton but1, JButton but2)
		{
			setLayout(new MigLayout("fillx, ins 0", "[][grow][70%]"));
			add(cb);
			add(new JLabel(text));
			add(comp, "growx, pushx, split 3");
			add(but1);
			add(but2);
		}
	}

	private final MarkerInfoPanel parent;

	private JSplitButton entityTypeButton;

	private FieldPanel itemPanel;
	private JCheckBox cbHasItem;
	private StandardEditableComboBox itemBox;
	private JButton selectItemButton;

	private FieldPanel gameFlagPanel;
	private JCheckBox cbHasGameFlag;
	private StandardEditableComboBox gameFlagBox;
	private JButton selectGameFlagButton;

	private FieldPanel areaFlagPanel;
	private JCheckBox cbHasAreaFlag;
	private StandardEditableComboBox areaFlagBox;
	private JButton selectAreaFlagButton;

	private FieldPanel mapVarPanel;
	private JCheckBox cbHasMapVar;
	private StandardEditableComboBox mapVarBox;
	private JButton selectMapVarButton;

	private FieldPanel scriptPanel;
	private JCheckBox cbHasScript;
	private StringField scriptField;

	private FieldPanel indexPanel;
	private JCheckBox cbHasIndex;
	private IntTextField indexField;

	private FieldPanel stylePanel;
	private JCheckBox cbHasStyle;
	private IntTextField styleField;

	private FieldPanel modelPanel;
	private JCheckBox cbHasModel;
	private MapObjectComboBox modelNameBox;
	private JButton selectModelButton;

	private FieldPanel colliderPanel;
	private JCheckBox cbHasCollider;
	private MapObjectComboBox colliderNameBox;
	private JButton selectColliderButton;

	private FieldPanel targetPanel;
	private JCheckBox cbHasTarget;
	private MapObjectComboBox targetNameBox;
	private JButton selectTargetButton;

	private FieldPanel entryPanel;
	private JCheckBox cbHasEntry;
	private StringField entryField;

	private FieldPanel pathsPanel;
	private JCheckBox cbHasPaths;
	private StringField pathsField;

	private FieldPanel anglePanel;
	private JCheckBox cbHasAngle;
	private IntTextField angleField;

	private FieldPanel launchDistPanel;
	private JCheckBox cbHasLaunchDist;
	private IntTextField launchDistField;

	private FieldPanel spawnModePanel;
	private JCheckBox cbHasSpawnMode;
	private StandardEditableComboBox spawnModeBox;
	private JButton selectSpawnModeButton;

	private String openBoxChooserDialog(JComboBox<?> box, String title)
	{
		List<String> items = new ArrayList<>();

		for (int i = 0; i < box.getItemCount(); i++)
			items.add((String) box.getItemAt(i));

		StringSelectorDialog chooser = new StringSelectorDialog(items);
		SwingUtils.showModalDialog(chooser, SwingGUI.instance(), title);
		if (chooser.isResultAccepted())
			return chooser.getValue();
		else
			return null;
	}

	public EntitySubpanel(MarkerInfoPanel parent)
	{
		this.parent = parent;

		JPopupMenu entityTypePopupMenu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(entityTypePopupMenu);
		createPopupMenu(entityTypePopupMenu);

		setLayout(new MigLayout("fillx, hidemode 3, ins 0, wrap"));
		add(SwingUtils.getLabel("Entity Settings", 14), "growx, gapbottom 4");

		entityTypeButton = new JSplitButton("EntityType");
		entityTypeButton.setPopupMenu(entityTypePopupMenu);
		entityTypeButton.setAlwaysPopup(true);
		SwingUtils.addBorderPadding(entityTypeButton);
		entityTypeButton.setHorizontalAlignment(SwingConstants.LEFT);

		add(new JLabel("Entity Type"), "split 2, growx");
		add(entityTypeButton, "w 70%!");

		addItemPanel();
		addGameFlagPanel();
		addAreaFlagPanel();
		addMapVarPanel();
		addScriptPanel();

		addIndexPanel();
		addStylePanel();

		addModelPanel();
		addColliderPanel();
		addTargetPanel();

		addEntryPanel();
		addPathsPanel();
		addAnglePanel();
		addLaunchDistPanel();
		addSpawnModePanel();

		add(new JLabel(), "pushy");
	}

	private JPopupMenu createPopupMenu(JPopupMenu popupMenu)
	{
		for (EntityMenuGroup group : EntityMenuGroup.values()) {
			if (group == EntityMenuGroup.Hidden)
				continue;

			JMenu groupMenu = new JMenu(group.toString() + "      ");
			popupMenu.add(groupMenu);

			for (EntityType type : EntityType.values()) {
				if (type.menuGroup != group)
					continue;

				JMenuItem typeItem = new JMenuItem(type.toString());
				typeItem.setPreferredSize(new Dimension(144, typeItem.getPreferredSize().height));
				typeItem.setActionCommand(type.name());
				typeItem.addActionListener(parent);
				groupMenu.add(typeItem);
			}
		}

		return popupMenu;
	}

	private void addItemPanel()
	{
		cbHasItem = new JCheckBox();
		cbHasItem.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.itemName.enabler(cbHasItem.isSelected())
		));

		itemBox = new StandardEditableComboBox((s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.itemName.mutator(s));
		}, ProjectDatabase.getItemNames());

		selectItemButton = new JButton("~");
		selectItemButton.addActionListener((e) -> {
			String newName = openBoxChooserDialog(itemBox, "Choose Item");
			if (newName != null)
				MapEditor.execute(parent.getData().entityComponent.itemName.mutator(newName));
		});
		SwingUtils.addBorderPadding(selectItemButton);

		itemPanel = new FieldPanel("Item", cbHasItem, itemBox, selectItemButton);
		add(itemPanel, "growx");
	}

	private void addGameFlagPanel()
	{
		cbHasGameFlag = new JCheckBox();
		cbHasGameFlag.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.gameFlagName.enabler(cbHasGameFlag.isSelected())
		));

		gameFlagBox = new StandardEditableComboBox((s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.gameFlagName.mutator(s));
		}, ProjectDatabase.getSavedFlagNames());

		selectGameFlagButton = new JButton("~");
		selectGameFlagButton.setToolTipText("Select from list");
		selectGameFlagButton.addActionListener((e) -> {
			String newFlag = openBoxChooserDialog(gameFlagBox, "Choose Game Flag");
			if (newFlag != null)
				MapEditor.execute(parent.getData().entityComponent.gameFlagName.mutator(newFlag));
		});
		SwingUtils.addBorderPadding(selectGameFlagButton);

		gameFlagPanel = new FieldPanel("Game Flag", cbHasGameFlag, gameFlagBox, selectGameFlagButton);
		add(gameFlagPanel, "growx");
	}

	private void addAreaFlagPanel()
	{
		cbHasAreaFlag = new JCheckBox();
		cbHasAreaFlag.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.areaFlagName.enabler(cbHasAreaFlag.isSelected())
		));

		areaFlagBox = new StandardEditableComboBox((s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.areaFlagName.mutator(s));
		}, new String[] {}); // empty initial list

		selectAreaFlagButton = new JButton("~");
		selectAreaFlagButton.setToolTipText("Select from list");
		selectAreaFlagButton.addActionListener((e) -> {
			String newFlag = openBoxChooserDialog(areaFlagBox, "Choose Area Flag");
			if (newFlag != null)
				MapEditor.execute(parent.getData().entityComponent.areaFlagName.mutator(newFlag));
		});
		SwingUtils.addBorderPadding(selectAreaFlagButton);

		areaFlagPanel = new FieldPanel("Area Flag", cbHasAreaFlag, areaFlagBox, selectAreaFlagButton);
		add(areaFlagPanel, "growx");
	}

	private void addMapVarPanel()
	{
		cbHasMapVar = new JCheckBox();
		cbHasMapVar.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.mapVarName.enabler(cbHasMapVar.isSelected())
		));

		mapVarBox = new StandardEditableComboBox((s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.mapVarName.mutator(s));
		}, new String[] {}); // empty initial list

		selectMapVarButton = new JButton("~");
		selectMapVarButton.setToolTipText("Select from list");
		selectMapVarButton.addActionListener((e) -> {
			String newFlag = openBoxChooserDialog(mapVarBox, "Choose Map Var");
			if (newFlag != null)
				MapEditor.execute(parent.getData().entityComponent.mapVarName.mutator(newFlag));
		});
		SwingUtils.addBorderPadding(selectMapVarButton);

		mapVarPanel = new FieldPanel("Map Var", cbHasMapVar, mapVarBox, selectMapVarButton);
		add(mapVarPanel, "growx");
	}

	private void addScriptPanel()
	{
		cbHasScript = new JCheckBox();
		cbHasScript.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.scriptName.enabler(cbHasScript.isSelected())
		));

		scriptField = new StringField(SwingConstants.LEFT, (s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.scriptName.mutator(s));
		});

		scriptPanel = new FieldPanel("Script", cbHasScript, scriptField);
		add(scriptPanel, "growx");
	}

	private void addIndexPanel()
	{
		cbHasIndex = new JCheckBox();
		cbHasIndex.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.index.enabler(cbHasIndex.isSelected())
		));

		indexField = new IntTextField((v) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.index.mutator(v));
		});
		SwingUtils.addBorderPadding(indexField);

		indexPanel = new FieldPanel("Index", cbHasIndex, indexField);
		add(indexPanel, "growx");
	}

	private void addStylePanel()
	{
		cbHasStyle = new JCheckBox();
		cbHasStyle.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.style.enabler(cbHasStyle.isSelected())
		));

		styleField = new IntTextField((v) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.style.mutator(v));
		});
		SwingUtils.addBorderPadding(styleField);

		stylePanel = new FieldPanel("Style", cbHasStyle, styleField);
		add(stylePanel, "growx");
	}

	private void addModelPanel()
	{
		cbHasModel = new JCheckBox();
		cbHasModel.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.modelName.enabler(cbHasModel.isSelected())
		));

		modelNameBox = new MapObjectComboBox(MapObjectType.MODEL, (s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.modelName.mutator(s));
		});

		selectModelButton = new JButton("~");
		selectModelButton.setToolTipText("Select from list");
		selectModelButton.addActionListener((e) -> {
			String newModel = openBoxChooserDialog(modelNameBox, "Choose Model");
			if (newModel != null)
				MapEditor.execute(parent.getData().entityComponent.modelName.mutator(newModel));
		});
		SwingUtils.addBorderPadding(selectModelButton);

		modelPanel = new FieldPanel("Model", cbHasModel, modelNameBox, selectModelButton);
		add(modelPanel, "growx");
	}

	private void addColliderPanel()
	{
		cbHasCollider = new JCheckBox();
		cbHasCollider.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.colliderName.enabler(cbHasCollider.isSelected())
		));

		colliderNameBox = new MapObjectComboBox(MapObjectType.COLLIDER, (s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.colliderName.mutator(s));
		});

		selectColliderButton = new JButton("~");
		selectColliderButton.setToolTipText("Select from list");
		selectColliderButton.addActionListener((e) -> {
			String newCollider = openBoxChooserDialog(colliderNameBox, "Choose Collider");
			if (newCollider != null)
				MapEditor.execute(parent.getData().entityComponent.colliderName.mutator(newCollider));
		});
		SwingUtils.addBorderPadding(selectColliderButton);

		colliderPanel = new FieldPanel("Collider", cbHasCollider, colliderNameBox, selectColliderButton);
		add(colliderPanel, "growx");
	}

	private void addTargetPanel()
	{
		cbHasTarget = new JCheckBox();
		cbHasTarget.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.targetName.enabler(cbHasTarget.isSelected())
		));

		targetNameBox = new MapObjectComboBox(MarkerType.Position, (s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.targetName.mutator(s));
		});

		selectTargetButton = new JButton("~");
		selectTargetButton.setToolTipText("Select from list");
		selectTargetButton.addActionListener((e) -> {
			String newTarget = openBoxChooserDialog(targetNameBox, "Choose Target Marker");
			if (newTarget != null)
				MapEditor.execute(parent.getData().entityComponent.targetName.mutator(newTarget));
		});
		SwingUtils.addBorderPadding(selectTargetButton);

		targetPanel = new FieldPanel("Target", cbHasTarget, targetNameBox, selectTargetButton);
		add(targetPanel, "growx");
	}

	private void addEntryPanel()
	{
		cbHasEntry = new JCheckBox();
		cbHasEntry.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.entryName.enabler(cbHasEntry.isSelected())
		));

		entryField = new StringField(SwingConstants.LEFT, (s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.entryName.mutator(s));
		});

		entryPanel = new FieldPanel("Entry", cbHasEntry, entryField);
		add(entryPanel, "growx");
	}

	private void addPathsPanel()
	{
		cbHasPaths = new JCheckBox();
		cbHasPaths.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.pathsName.enabler(cbHasPaths.isSelected())
		));

		pathsField = new StringField(SwingConstants.LEFT, (s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.pathsName.mutator(s));
		});

		pathsPanel = new FieldPanel("Paths", cbHasPaths, pathsField);
		add(pathsPanel, "growx");
	}

	private void addAnglePanel()
	{
		cbHasAngle = new JCheckBox();
		cbHasAngle.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.angle.enabler(cbHasAngle.isSelected())
		));

		angleField = new IntTextField((v) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.angle.mutator(v));
		});
		SwingUtils.addBorderPadding(angleField);

		anglePanel = new FieldPanel("Angle", cbHasAngle, angleField);
		add(anglePanel, "growx");
	}

	private void addLaunchDistPanel()
	{
		cbHasLaunchDist = new JCheckBox();
		cbHasLaunchDist.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.launchDist.enabler(cbHasLaunchDist.isSelected())
		));

		launchDistField = new IntTextField((v) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.launchDist.mutator(v));
		});
		SwingUtils.addBorderPadding(launchDistField);

		launchDistPanel = new FieldPanel("Launch Dist", cbHasLaunchDist, launchDistField);
		add(launchDistPanel, "growx");
	}

	private void addSpawnModePanel()
	{
		cbHasSpawnMode = new JCheckBox();
		cbHasSpawnMode.addActionListener((e) -> MapEditor.execute(
			parent.getData().entityComponent.spawnMode.enabler(cbHasSpawnMode.isSelected())
		));

		spawnModeBox = new StandardEditableComboBox((s) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			MapEditor.execute(parent.getData().entityComponent.spawnMode.mutator(s));
		}, ProjectDatabase.EItemSpawnModes.getValues());

		selectSpawnModeButton = new JButton("~");
		selectSpawnModeButton.addActionListener((e) -> {
			String newName = openBoxChooserDialog(spawnModeBox, "Choose Spawn Mode");
			if (newName != null)
				MapEditor.execute(parent.getData().entityComponent.spawnMode.mutator(newName));
		});
		SwingUtils.addBorderPadding(selectSpawnModeButton);

		spawnModePanel = new FieldPanel("Spawn Mode", cbHasSpawnMode, spawnModeBox, selectSpawnModeButton);
		add(spawnModePanel, "growx");
	}

	public void onSetData()
	{
		modelNameBox.updateNames(parent.getData().entityComponent.modelName.get());
		colliderNameBox.updateNames(parent.getData().entityComponent.colliderName.get());
		targetNameBox.updateNames(parent.getData().entityComponent.targetName.get());

		areaFlagBox.updateModel(MapEditor.instance().map.areaFlagNames);
		mapVarBox.updateModel(MapEditor.instance().map.mapVarNames);
	}

	private void updateBoxPanel(MapKey key, JPanel panel, JCheckBox cb,
		StandardEditableComboBox ui, EditableField<String> field)
	{
		EntityComponent data = parent.getData().entityComponent;
		EntityParam param = data.type.get().getParam(key);

		if (param != null) {
			boolean cbEnabled;
			boolean uiEnabled;

			if (param.required) {
				cbEnabled = false;
				uiEnabled = true;
			}
			else {
				cbEnabled = true;
				uiEnabled = field.isEnabled();
			}

			cb.setEnabled(cbEnabled);
			cb.setSelected(uiEnabled);
			ui.setEnabled(uiEnabled);

			ui.setSelectedItem(field.get());
			panel.setVisible(true);
		}
		else {
			panel.setVisible(false);
		}
	}

	private void updateMapObjPanel(MapKey key, JPanel panel, JCheckBox cb,
		MapObjectComboBox ui, EditableField<String> field)
	{
		EntityComponent data = parent.getData().entityComponent;
		EntityParam param = data.type.get().getParam(key);

		if (param != null) {
			boolean cbEnabled;
			boolean uiEnabled;

			if (param.required) {
				cbEnabled = false;
				uiEnabled = true;
			}
			else {
				cbEnabled = true;
				uiEnabled = field.isEnabled();
			}

			cb.setEnabled(cbEnabled);
			cb.setSelected(uiEnabled);
			ui.setEnabled(uiEnabled);

			ui.setSelectedItem(field.get());
			panel.setVisible(true);
		}
		else {
			panel.setVisible(false);
		}
	}

	private void updateStringPanel(MapKey key, JPanel panel, JCheckBox cb,
		StringField ui, EditableField<String> field)
	{
		EntityComponent data = parent.getData().entityComponent;
		EntityParam param = data.type.get().getParam(key);

		if (param != null) {
			boolean cbEnabled;
			boolean uiEnabled;

			if (param.required) {
				cbEnabled = false;
				uiEnabled = true;
			}
			else {
				cbEnabled = true;
				uiEnabled = field.isEnabled();
			}

			cb.setEnabled(cbEnabled);
			cb.setSelected(uiEnabled);
			ui.setEnabled(uiEnabled);

			ui.setText(field.get());
			panel.setVisible(true);
		}
		else {
			panel.setVisible(false);
		}
	}

	private void updateIntPanel(MapKey key, JPanel panel, JCheckBox cb,
		IntTextField ui, EditableField<Integer> field)
	{
		EntityComponent data = parent.getData().entityComponent;
		EntityParam param = data.type.get().getParam(key);

		if (param != null) {
			boolean cbEnabled;
			boolean uiEnabled;

			if (param.required) {
				cbEnabled = false;
				uiEnabled = true;
			}
			else {
				cbEnabled = true;
				uiEnabled = field.isEnabled();
			}

			cb.setEnabled(cbEnabled);
			cb.setSelected(uiEnabled);
			ui.setEnabled(uiEnabled);

			ui.setValue(field.get());
			panel.setVisible(true);
		}
		else {
			panel.setVisible(false);
		}
	}

	public void onUpdateFields()
	{
		EntityComponent data = parent.getData().entityComponent;
		EntityType type = data.type.get();

		entityTypeButton.setText(type.toString());

		updateBoxPanel(ATTR_NTT_ITEM, itemPanel, cbHasItem, itemBox, data.itemName);
		updateBoxPanel(ATTR_NTT_GAME_FLAG, gameFlagPanel, cbHasGameFlag, gameFlagBox, data.gameFlagName);
		updateBoxPanel(ATTR_NTT_AREA_FLAG, areaFlagPanel, cbHasAreaFlag, areaFlagBox, data.areaFlagName);
		updateBoxPanel(ATTR_NTT_MAP_VAR, mapVarPanel, cbHasMapVar, mapVarBox, data.mapVarName);
		updateStringPanel(ATTR_NTT_SCRIPT, scriptPanel, cbHasScript, scriptField, data.scriptName);

		updateIntPanel(ATTR_NTT_INDEX, indexPanel, cbHasIndex, indexField, data.index);
		updateIntPanel(ATTR_NTT_STYLE, stylePanel, cbHasStyle, styleField, data.style);

		updateMapObjPanel(ATTR_NTT_MODEL, modelPanel, cbHasModel, modelNameBox, data.modelName);
		updateMapObjPanel(ATTR_NTT_COLLIDER, colliderPanel, cbHasCollider, colliderNameBox, data.colliderName);
		updateMapObjPanel(ATTR_NTT_TARGET, targetPanel, cbHasTarget, targetNameBox, data.targetName);
		updateStringPanel(ATTR_NTT_ENTRY, entryPanel, cbHasEntry, entryField, data.entryName);

		updateStringPanel(ATTR_NTT_PATHS, pathsPanel, cbHasPaths, pathsField, data.pathsName);

		updateIntPanel(ATTR_NTT_ANGLE, anglePanel, cbHasAngle, angleField, data.angle);
		updateIntPanel(ATTR_NTT_LAUNCH_DIST, launchDistPanel, cbHasLaunchDist, launchDistField, data.launchDist);

		updateBoxPanel(ATTR_NTT_SPAWN_MODE, spawnModePanel, cbHasSpawnMode, spawnModeBox, data.spawnMode);
	}
}
