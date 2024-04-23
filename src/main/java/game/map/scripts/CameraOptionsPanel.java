package game.map.scripts;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.Map;
import game.map.editor.MapEditor;
import net.miginfocom.swing.MigLayout;
import util.ui.IntTextField;
import util.ui.IntVectorPanel;

public class CameraOptionsPanel extends JPanel
{
	private boolean ignoreChanges = false;

	private Map map;
	private ScriptData scripts;

	private JCheckBox cbLeadPlayer;

	private IntTextField vfovField;
	private IntVectorPanel clipDist;
	private IntVectorPanel bgColor;

	private JCheckBox cbWorldFog;
	private IntVectorPanel worldFogDist;
	private IntVectorPanel worldFogColor;

	private JCheckBox cbEntityFog;
	private IntVectorPanel entityFogDist;
	private IntVectorPanel entityFogColor;

	public CameraOptionsPanel()
	{
		cbLeadPlayer = new JCheckBox(" Camera leads player motion");
		cbLeadPlayer.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(scripts.cameraLeadsPlayer.mutator(cbLeadPlayer.isSelected()));
		});

		vfovField = new IntTextField((v) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(scripts.camVfov.mutator(v));
		});
		vfovField.setHorizontalAlignment(SwingConstants.CENTER);

		clipDist = new IntVectorPanel(2, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.camNearClip.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.camFarClip.mutator(value));
		});

		bgColor = new IntVectorPanel(3, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.bgColorR.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.bgColorG.mutator(value));
			else if (index == 2)
				MapEditor.execute(scripts.bgColorB.mutator(value));
		});

		cbWorldFog = new JCheckBox(" Enable World Fog");
		cbWorldFog.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			MapEditor.execute(scripts.worldFogSettings.enabled.mutator(cbWorldFog.isSelected()));
		});
		worldFogDist = new IntVectorPanel(2, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.worldFogSettings.start.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.worldFogSettings.end.mutator(value));

		});
		worldFogColor = new IntVectorPanel(3, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.worldFogSettings.R.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.worldFogSettings.G.mutator(value));
			else if (index == 2)
				MapEditor.execute(scripts.worldFogSettings.B.mutator(value));

		});

		cbEntityFog = new JCheckBox(" Enable Entity Fog");
		cbEntityFog.addActionListener((e) -> {
			if (ignoreChanges)
				return;

			MapEditor.execute(scripts.entityFogSettings.enabled.mutator(cbEntityFog.isSelected()));
		});
		entityFogDist = new IntVectorPanel(2, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.entityFogSettings.start.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.entityFogSettings.end.mutator(value));

		});
		entityFogColor = new IntVectorPanel(3, (index, value) -> {
			if (ignoreChanges)
				return;
			if (index == 0)
				MapEditor.execute(scripts.entityFogSettings.R.mutator(value));
			else if (index == 1)
				MapEditor.execute(scripts.entityFogSettings.G.mutator(value));
			else if (index == 2)
				MapEditor.execute(scripts.entityFogSettings.B.mutator(value));

		});

		setLayout(new MigLayout("fill, wrap, ins 0 16 16 16"));

		add(SwingUtils.getLabel("Camera Settings", 14), "gaptop 16, gapbottom 2");
		add(SwingUtils.getLabel("Vertical FOV", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(vfovField);
		add(SwingUtils.getLabel("Clip Planes", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(clipDist);
		add(SwingUtils.getLabel("Background Color", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(bgColor);
		add(cbLeadPlayer, "sgy row, gapleft 8");

		SwingUtils.setFontSize(cbWorldFog, 14);
		add(cbWorldFog, "gaptop 16");

		add(SwingUtils.getLabel("Distance", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(worldFogDist);
		add(SwingUtils.getLabel("Color", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(worldFogColor);

		SwingUtils.setFontSize(cbEntityFog, 14);
		add(cbEntityFog, "gaptop 16");

		add(SwingUtils.getLabel("Distance", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(entityFogDist);
		add(SwingUtils.getLabel("Color", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(entityFogColor);

		add(new JLabel(), "grow, pushy");
	}

	public void setMap(Map m)
	{
		this.map = m;
		this.scripts = map.scripts;
		updateFields(map.scripts);
	}

	public void updateFields(ScriptData data)
	{
		ignoreChanges = true;

		cbLeadPlayer.setSelected(data.cameraLeadsPlayer.get());

		vfovField.setValue(data.camVfov.get());
		clipDist.setValues(data.camNearClip.get(), data.camFarClip.get());
		bgColor.setValues(data.bgColorR.get(), data.bgColorG.get(), data.bgColorB.get());

		cbWorldFog.setSelected(data.worldFogSettings.enabled.get());
		worldFogDist.setEnabled(data.worldFogSettings.enabled.get());
		worldFogColor.setEnabled(data.worldFogSettings.enabled.get());
		worldFogDist.setValues(data.worldFogSettings.start.get(), data.worldFogSettings.end.get());
		worldFogColor.setValues(data.worldFogSettings.R.get(), data.worldFogSettings.G.get(), data.worldFogSettings.B.get());

		cbEntityFog.setSelected(data.entityFogSettings.enabled.get());
		entityFogDist.setEnabled(data.entityFogSettings.enabled.get());
		entityFogColor.setEnabled(data.entityFogSettings.enabled.get());
		entityFogDist.setValues(data.entityFogSettings.start.get(), data.entityFogSettings.end.get());
		entityFogColor.setValues(data.entityFogSettings.R.get(), data.entityFogSettings.G.get(), data.entityFogSettings.B.get());

		ignoreChanges = false;
	}
}
