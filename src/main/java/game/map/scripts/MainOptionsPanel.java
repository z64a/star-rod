package game.map.scripts;

import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.ProjectDatabase;
import game.map.Map;
import game.map.editor.MapEditor;
import game.map.editor.ui.StandardEditableComboBox;
import game.map.scripts.extract.Extractor;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.IntTextField;
import util.ui.IntVectorPanel;
import util.ui.StringField;

public class MainOptionsPanel extends JPanel
{
	private boolean ignoreChanges = false;

	private Map map;
	private ScriptData scripts;

	private JButton analyzeButton;
	private JButton generateButton;

	private StandardEditableComboBox locationsBox;

	private JCheckBox cbDarkness;

	private JCheckBox cbOverrideShape;
	private JCheckBox cbOverrideHit;
	private JCheckBox cbOverrideTex;
	private StringField overrideShapeText;
	private StringField overrideHitText;

	private JCheckBox cbCallbackBeforeEnter;
	private JCheckBox cbCallbackAfterEnter;

	private JCheckBox cbHasMusic;
	private StandardEditableComboBox songBox;

	private JCheckBox cbHasSounds;
	private StandardEditableComboBox soundsBox;

	// camera settings
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

	public MainOptionsPanel()
	{
		analyzeButton = new JButton("Analyze C Files");
		analyzeButton.addActionListener((evt) -> {
			try {
				new Extractor(map, true);
				MapEditor.instance().flushUndoRedo();
				MapEditor.instance().action_SaveMap();
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		});
		SwingUtils.addBorderPadding(analyzeButton);

		generateButton = new JButton("Generate Template");
		generateButton.addActionListener((evt) -> {
			/*
			try {
				Logger.log("Generating scripts for " + map.getName() + "...");
				new ScriptGenerator(map);
				Logger.log("Successfully generated scripts for " + map.getName());
			}
			catch (InvalidInputException iie) {
				Logger.logError(iie.getMessage());
				Toolkit.getDefaultToolkit().beep();
				MapEditor.instance().displayStackTrace(iie);
			}
			catch (IOException ioe) {
				Logger.logError("Script generation failed! IOException. Check log for more information.");
				Toolkit.getDefaultToolkit().beep();
				MapEditor.instance().displayStackTrace(ioe);
			}
			*/
			//TODO
			Logger.logWarning("Not implemented yet!");
		});
		SwingUtils.addBorderPadding(generateButton);

		overrideShapeText = new StringField((s) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.shapeOverrideName.mutator(s));
		});
		overrideHitText = new StringField((s) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.hitOverrideName.mutator(s));
		});

		cbOverrideShape = new JCheckBox(" Geometry");
		cbOverrideShape.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.overrideShape.mutator(cbOverrideShape.isSelected()));
		});

		cbOverrideHit = new JCheckBox(" Collision");
		cbOverrideHit.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.overrideHit.mutator(cbOverrideHit.isSelected()));
		});

		cbOverrideTex = new JCheckBox(" Match textures in editor");
		cbOverrideTex.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.overrideTex.mutator(cbOverrideTex.isSelected()));
		});

		cbDarkness = new JCheckBox(" Dark, requires Watt to see");
		cbDarkness.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.isDark.mutator(cbDarkness.isSelected()));
		});

		locationsBox = new StandardEditableComboBox((s) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.locationName.mutator(s));

		}, ProjectDatabase.ELocations.getValues());
		locationsBox.setMaximumRowCount(20);

		cbCallbackBeforeEnter = new JCheckBox(" Add callback before EnterMap");
		cbCallbackBeforeEnter.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.addCallbackBeforeEnterMap.mutator(cbCallbackBeforeEnter.isSelected()));
		});

		cbCallbackAfterEnter = new JCheckBox(" Add callback after EnterMap");
		cbCallbackAfterEnter.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.addCallbackAfterEnterMap.mutator(cbCallbackAfterEnter.isSelected()));
		});

		cbHasMusic = new JCheckBox();
		cbHasMusic.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.hasMusic.mutator(cbHasMusic.isSelected()));
		});

		songBox = new StandardEditableComboBox((s) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.songName.mutator(s));
		}, ProjectDatabase.ESongs.getValues());

		cbHasSounds = new JCheckBox();
		cbHasSounds.addActionListener((e) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.hasAmbientSFX.mutator(cbHasSounds.isSelected()));
		});

		soundsBox = new StandardEditableComboBox((s) -> {
			if (ignoreChanges || scripts == null)
				return;
			MapEditor.execute(scripts.ambientSFX.mutator(s));
		}, ProjectDatabase.EAmbientSounds.getValues());

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

		JPanel buttons = new JPanel(new MigLayout("fillx, ins 0", "[sg but][sg but]"));

		buttons.add(SwingUtils.getLabel("Script Files", 14), "span");
		buttons.add(analyzeButton, "growx");
		buttons.add(generateButton, "growx");

		JPanel overrides = new JPanel(new MigLayout("fillx, ins 0, wrap 2", "[][70%]"));
		overrides.add(SwingUtils.getLabel("Asset Overrides", 14), "span");

		overrides.add(cbOverrideTex, "gapleft 8, span, growx");
		overrides.add(cbOverrideShape, "gapleft 8");
		overrides.add(overrideShapeText, "growx");
		overrides.add(cbOverrideHit, "gapleft 8");
		overrides.add(overrideHitText, "growx");

		JPanel options = new JPanel(new MigLayout("fillx, ins 0, wrap 3", "[][grow][70%]"));
		options.add(SwingUtils.getLabel("Main Options", 14), "span");

		JCheckBox cbDummy = new JCheckBox();
		cbDummy.setSelected(true);
		cbDummy.setEnabled(false);

		options.add(cbDummy, "gapleft 8");
		options.add(new JLabel("Location"));
		options.add(locationsBox, "growx");

		options.add(cbHasMusic, "gapleft 8");
		options.add(new JLabel("Song"));
		options.add(songBox, "growx");

		options.add(cbHasSounds, "gapleft 8");
		options.add(new JLabel("Ambience"));
		options.add(soundsBox, "growx");

		options.add(cbCallbackBeforeEnter, "gapleft 8, span, growx");
		options.add(cbCallbackAfterEnter, "gapleft 8, span, growx");
		options.add(cbDarkness, "gapleft 8, span, growx");

		/*
		JPanel camera = new JPanel(new MigLayout("fillx, ins 0, wrap 2", "[][70%]"));
		camera.add(SwingUtils.getLabel("Camera Settings", 14), "span");
		camera.add(SwingUtils.getLabel("Vertical FOV", 12), "gapleft 8");
		camera.add(vfovField);
		camera.add(SwingUtils.getLabel("Clip Planes", 12), "gapleft 8");
		camera.add(clipDist);
		camera.add(SwingUtils.getLabel("BG Color", 12), "gapleft 8");
		camera.add(bgColor);
		camera.add(cbLeadPlayer, "gapleft 8, span, growx");
		*/

		JPanel fog = new JPanel(new MigLayout("fillx, ins 0, wrap 2", "[][70%]"));
		SwingUtils.setFontSize(cbWorldFog, 14);
		fog.add(cbWorldFog, "gaptop 8, span");

		fog.add(SwingUtils.getLabel("Distance", 12), "gapleft 8");
		fog.add(worldFogDist, "growx");
		fog.add(SwingUtils.getLabel("Color", 12), "gapleft 8");
		fog.add(worldFogColor, "growx");

		SwingUtils.setFontSize(cbEntityFog, 14);
		fog.add(cbEntityFog, "gaptop 8, span");

		fog.add(SwingUtils.getLabel("Distance", 12), "gapleft 8");
		fog.add(entityFogDist, "growx");
		fog.add(SwingUtils.getLabel("Color", 12), "gapleft 8");
		fog.add(entityFogColor, "growx");

		setLayout(new MigLayout("fill, ins 16 16 16 16, wrap"));
		add(buttons, "growx");
		add(overrides, "growx, gaptop 16");
		add(options, "growx, gaptop 16");
		//	add(camera, "growx, gaptop 16");
		add(fog, "growx, gaptop 16");

		/*
		setLayout(new MigLayout("fill, wrap, ins 0 16 16 16"));
		
		add(SwingUtils.getLabel("Script Files", 14), "gaptop 16");
		add(analyzeButton, "w 40%, gapleft 8, gapright 8, split 2");
		add(generateButton, "w 40%, gapleft 8");
		
		add(SwingUtils.getLabel("Asset Overrides", 14), "gaptop 16");
		add(cbOverrideTex, "gapleft 8, sgy row, growx");
		add(cbOverrideShape, "gapleft 8, sg lbl, gapright 24, split 2");
		add(overrideShapeText, "sgy row, w 120!");
		add(cbOverrideHit, "gapleft 8, sg lbl, gapright 24, split 2");
		add(overrideHitText, "sgy row, w 120!");
		
		add(SwingUtils.getLabel("Main Options", 14), "gaptop 16");
		
		add(cbHasMusic, "gapleft 8, sg lbl, gapright 24, split 2");
		add(songBox, "sgy row, growx");
		
		add(cbHasSounds, "gapleft 8, sg lbl, gapright 24, split 2");
		add(soundsBox, "sgy row, growx");
		
		add(SwingUtils.getLabel("Location", 12),
			"gapleft 8, sg lbl, gapright 24, split 2");
		add(locationsBox, "growx");
		
		add(cbCallbackBeforeEnter, "gapleft 8, sgy row, growx");
		add(cbCallbackAfterEnter, "gapleft 8, sgy row, growx");
		
		add(SwingUtils.getLabel("Camera Settings", 14), "gaptop 16, gapbottom 2");
		add(SwingUtils.getLabel("Vertical FOV", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(vfovField);
		add(SwingUtils.getLabel("Clip Planes", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(clipDist);
		add(SwingUtils.getLabel("Background Color", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(bgColor);
		add(cbLeadPlayer, "sgy row, gapleft 8, growx");
		add(cbDarkness, "sgy row, gapleft 8, growx");
		
		SwingUtils.setFontSize(cbWorldFog, 14);
		add(cbWorldFog, "gaptop 8");
		
		add(SwingUtils.getLabel("Distance", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(worldFogDist);
		add(SwingUtils.getLabel("Color", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(worldFogColor);
		
		SwingUtils.setFontSize(cbEntityFog, 14);
		add(cbEntityFog, "gaptop 8");
		
		add(SwingUtils.getLabel("Distance", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(entityFogDist);
		add(SwingUtils.getLabel("Color", 12), "gapleft 8, sg lbl, gapright 24, split 2");
		add(entityFogColor);
		*/

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

		overrideShapeText.setText(data.shapeOverrideName.get());
		overrideHitText.setText(data.hitOverrideName.get());

		cbOverrideShape.setSelected(data.overrideShape.get());
		overrideShapeText.setEnabled(data.overrideShape.get());

		cbOverrideHit.setSelected(data.overrideHit.get());
		overrideHitText.setEnabled(data.overrideHit.get());

		cbOverrideTex.setSelected(data.overrideTex.get());

		locationsBox.setSelectedItem(data.locationName.get());
		cbDarkness.setSelected(data.isDark.get());

		cbCallbackBeforeEnter.setSelected(data.addCallbackBeforeEnterMap.get());
		cbCallbackAfterEnter.setSelected(data.addCallbackAfterEnterMap.get());

		cbHasMusic.setSelected(data.hasMusic.get());
		songBox.setSelectedItem(data.songName.get());
		songBox.setEnabled(data.hasMusic.get());

		cbHasSounds.setSelected(data.hasAmbientSFX.get());
		soundsBox.setSelectedItem(data.ambientSFX.get());
		soundsBox.setEnabled(data.hasAmbientSFX.get());

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
