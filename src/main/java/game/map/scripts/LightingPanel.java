package game.map.scripts;

import java.awt.Color;
import java.util.LinkedList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;

import app.SwingUtils;
import game.map.Map;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.ui.SwingGUI;
import game.map.shape.Light;
import game.map.shape.LightSet;
import game.map.shape.LightSet.CreateLight;
import game.map.shape.LightSet.SetAmbientChannel;
import game.map.shape.LightSet.SetAmbientColor;
import game.map.shape.LightSet.SetLightingName;
import game.map.shape.Model;
import net.miginfocom.swing.MigLayout;
import util.ui.IntVectorPanel;
import util.ui.ListAdapterComboboxModel;
import util.ui.NameTextField;

/**
 * Singleton JPanel for dipsplaying model lighting data.
 */
public class LightingPanel extends JPanel implements IShutdownListener
{
	private LightSet lightSet;

	private JComboBox<LightSet> lightSetComboBox;
	private NameTextField nameField;
	private IntVectorPanel colorPanel;
	private JPanel lightListPanel;
	private JButton addLightButton;

	private boolean ignoreChanges = false;

	private static LightingPanel instance = null;

	public static LightingPanel instance()
	{
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
	}

	public LightingPanel()
	{
		if (instance != null)
			throw new IllegalStateException("There can be only one LightingPanel");
		instance = this;
		MapEditor.instance().registerOnShutdown(this);

		nameField = new NameTextField((newValue) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(new SetLightingName(lightSet, newValue));
		});

		colorPanel = new IntVectorPanel(3, (index, newValue) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(new SetAmbientChannel(lightSet, index, newValue));
		});

		JButton createSetButton = new JButton("Create");
		createSetButton.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(new CreateLightset());
		});

		JButton deleteSetButton = new JButton("Delete");
		deleteSetButton.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(new DeleteLightset(lightSet));
		});

		addLightButton = new JButton("Add Light");
		addLightButton.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(new CreateLight(lightSet));
		});

		JButton chooseColorButton = new JButton("Choose");
		chooseColorButton.addActionListener((e) -> {
			SwingGUI.instance().notify_OpenDialog();
			Color c = new Color(lightSet.ambient[0], lightSet.ambient[1], lightSet.ambient[2]);
			c = JColorChooser.showDialog(null, "Choose Ambient Color", c);
			SwingGUI.instance().notify_CloseDialog();

			if (c != null)
				MapEditor.execute(new SetAmbientColor(lightSet, c.getRed(), c.getGreen(), c.getBlue()));
		});

		lightListPanel = new JPanel(new MigLayout("wrap, fillx, ins 0"));

		JScrollPane lightListScrollPane = new JScrollPane(lightListPanel);
		lightListScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		lightListScrollPane.setBorder(null);

		JPanel lightSetPanel = new JPanel(new MigLayout("fill", "[grow][push][grow]"));

		Border border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
		lightSetPanel.setBorder(border);

		lightSetPanel.add(new JLabel("Name"), "growx");
		lightSetPanel.add(nameField, "growx, span 2, wrap");

		lightSetPanel.add(new JLabel("Ambient"), "growx");
		lightSetPanel.add(colorPanel, "growx");
		lightSetPanel.add(chooseColorButton, "growx, wrap");

		lightSetPanel.add(new JPanel(), "span, h 8!, growx, wrap");
		lightSetPanel.add(addLightButton, "span, w 50%, center, wrap");
		lightSetPanel.add(new JPanel(), "span, h 8!, growx, wrap");

		lightSetPanel.add(lightListScrollPane, "span, grow, pushy, wrap");

		lightSetComboBox = new JComboBox<>();
		lightSetComboBox.addActionListener((e) -> {
			if (ignoreChanges)
				return;

			LightSet newLights = (LightSet) lightSetComboBox.getSelectedItem();
			if (newLights != null)
				MapEditor.execute(new SetLightSet(newLights));
		});

		setLayout(new MigLayout("fill, hidemode 3"));
		add(SwingUtils.getLabel("Light Sets", 12), "gaptop 8, gapbottom 4, wrap");

		add(lightSetComboBox, "growx, wrap");
		add(createSetButton, "growx, split 2");
		add(deleteSetButton, "growx, wrap");
		add(lightSetPanel, "span, grow, pushy, gaptop 4");
	}

	public void setMap(Map map)
	{
		ignoreChanges = true;

		lightSetComboBox.setModel(new ListAdapterComboboxModel<>(map.lightSets));

		lightSetComboBox.setSelectedIndex(0);
		setLights((LightSet) lightSetComboBox.getSelectedItem());

		ignoreChanges = false;
	}

	public void setLights(LightSet newLightSet)
	{
		setLights(newLightSet, true);
	}

	public void setLights(LightSet newLightSet, boolean refreshList)
	{
		//	if(lightSet == newLightSet)
		//		return;

		lightSet = newLightSet;

		ignoreChanges = true;
		lightSetComboBox.setSelectedItem(lightSet);

		nameField.setValue(lightSet.name);
		colorPanel.setValues(lightSet.ambient);

		if (refreshList) {
			lightListPanel.removeAll();
			for (Light light : lightSet)
				lightListPanel.add(light.panel, "growx");

			addLightButton.setEnabled(lightSet.getLightCount() < LightSet.MAX_LIGHTS);
		}

		for (Light light : lightSet)
			light.panel.updateFields();

		repaint();
		ignoreChanges = false;
	}

	private class SetLightSet extends AbstractCommand
	{
		private final LightSet oldLights;
		private final LightSet newLights;

		public SetLightSet(LightSet lights)
		{
			super("Change Light Set");

			this.oldLights = lightSet;
			this.newLights = lights;
		}

		@Override
		public boolean shouldExec()
		{
			return newLights != oldLights;
		}

		@Override
		public void exec()
		{
			super.exec();
			setLights(newLights);
		}

		@Override
		public void undo()
		{
			super.undo();
			setLights(oldLights);
		}
	}

	public final class CreateLightset extends AbstractCommand
	{
		private final Map map;
		private final LightSet oldLightSet;
		private final LightSet newLightSet;

		public CreateLightset(LightSet newLights)
		{
			super("Create Lighting");
			this.map = editor.map;
			newLightSet = newLights;
			oldLightSet = lightSet;
		}

		// creates a new lightset for a model
		public CreateLightset()
		{
			super("Create Light Set");
			this.map = editor.map;
			newLightSet = LightSet.createEmptySet();
			newLightSet.name = "Lights_New";
			oldLightSet = lightSet;
		}

		@Override
		public void exec()
		{
			super.exec();
			map.lightSets.addElement(newLightSet);
			setLights(newLightSet);
		}

		@Override
		public void undo()
		{
			super.undo();
			setLights(oldLightSet);
			map.lightSets.removeElement(newLightSet);
		}
	}

	public final class DeleteLightset extends AbstractCommand
	{
		private final Map map;
		private final List<Model> mdlList;
		private final LightSet lightSet;
		private final int listIndex;

		public DeleteLightset(LightSet lightSet)
		{
			super("Delete Light Set");
			this.map = editor.map;
			this.lightSet = lightSet;
			this.listIndex = map.lightSets.indexOf(lightSet);

			mdlList = new LinkedList<>();
			for (Model mdl : map.modelTree.getList()) {
				if (mdl.lights.get() == lightSet)
					mdlList.add(mdl);
			}
		}

		@Override
		public boolean shouldExec()
		{
			return map.lightSets.size() > 1;
		}

		@Override
		public void exec()
		{
			super.exec();
			map.lightSets.removeElementAt(listIndex);
			for (Model mdl : mdlList)
				mdl.lights.set(map.lightSets.get(0));
			setLights(map.lightSets.get(0));
		}

		@Override
		public void undo()
		{
			super.undo();
			map.lightSets.add(listIndex, lightSet);
			for (Model mdl : mdlList)
				mdl.lights.set(lightSet);
			setLights(lightSet);
		}
	}
}
