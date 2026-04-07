package game.map.scripts;

import java.awt.Dimension;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import app.Environment;
import app.SwingUtils;
import common.commands.AbstractCommand;
import game.ProjectDatabase;
import game.map.Map;
import game.map.editor.MapEditor;
import game.map.editor.UpdateListener;
import game.map.editor.ui.SwingGUI;
import game.map.editor.ui.info.marker.ColorChoicePanel;
import game.map.shading.EditableShadingData;
import game.map.shading.EditableShadingData.EditableShadingProfile;
import game.map.shading.ShadingTreePanel;
import net.miginfocom.swing.MigLayout;
import util.ui.IntTextField;
import util.ui.IntVectorPanel;
import util.ui.StringField;

public class OptionsPanel extends JPanel implements UpdateListener
{
	public static final String TAG_GENERAL = "General";
	public static final String TAG_CAMERA = "Camera";
	public static final String TAG_SHADING = "Shading";

	private boolean ignoreChanges = false;

	private Map map;

	private JCheckBox cbOverrideShape;
	private JCheckBox cbOverrideHit;
	private JCheckBox cbOverrideTex;
	private StringField overrideShapeText;
	private StringField overrideHitText;
	private StringField overrideTexText;

	private JCheckBox cbHasBackground;
	private StringField backgroundText;

	private JCheckBox cbLeadPlayer;
	private IntTextField camVfovField;
	private IntVectorPanel camClipRange;
	private IntVectorPanel camBackgroundColor;

	private JCheckBox cbWorldFog;
	private IntVectorPanel worldFogDist;
	private IntVectorPanel worldFogColor;

	private JCheckBox cbEntityFog;
	private IntVectorPanel entityFogDist;
	private IntVectorPanel entityFogColor;

	private JButton chooseProfileButton;
	private JCheckBox cbHasShading;
	private JTextField profileNameField;

	private ColorChoicePanel shadingColorPanel;
	private IntTextField shadingOffsetField;

	private void tryExec(AbstractCommand cmd)
	{
		if (ignoreChanges || map.features == null)
			return;
		MapEditor.execute(cmd);
	}

	private void createOverrideComponents()
	{
		cbOverrideShape = new JCheckBox(" Geometry");
		cbOverrideShape.addActionListener((e) -> {
			tryExec(map.features.overrideShape.mutator(cbOverrideShape.isSelected()));
		});

		overrideShapeText = new StringField(SwingConstants.LEFT, (s) -> {
			tryExec(map.features.shapeOverrideName.mutator(s));
		});

		cbOverrideHit = new JCheckBox(" Collision");
		cbOverrideHit.addActionListener((e) -> {
			tryExec(map.features.overrideHit.mutator(cbOverrideHit.isSelected()));
		});

		overrideHitText = new StringField(SwingConstants.LEFT, (s) -> {
			tryExec(map.features.hitOverrideName.mutator(s));
		});

		cbOverrideTex = new JCheckBox(" Textures");
		cbOverrideTex.addActionListener((e) -> {
			tryExec(map.features.overrideTex.mutator(cbOverrideTex.isSelected()));
		});

		overrideTexText = new StringField(SwingConstants.LEFT, (s) -> {
			tryExec(map.features.texOverrideName.mutator(s));
		});

		cbHasBackground = new JCheckBox(" Background");
		cbOverrideTex.addActionListener((e) -> {
			tryExec(map.features.overrideTex.mutator(cbOverrideTex.isSelected()));
		});

		overrideTexText = new StringField(SwingConstants.LEFT, (s) -> {
			tryExec(map.features.texOverrideName.mutator(s));
		});
	}

	private void createCameraComponents()
	{
		cbLeadPlayer = new JCheckBox(" Camera leads player motion");
		cbLeadPlayer.addActionListener((e) -> {
			tryExec(map.features.camLeadsPlayer.mutator(cbLeadPlayer.isSelected()));
		});

		camVfovField = new IntTextField((v) -> {
			tryExec(map.features.camVfov.mutator(v));
		});
		camVfovField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addVerticalBorderPadding(camVfovField);

		camClipRange = new IntVectorPanel(true, 2, (index, value) -> {
			if (ignoreChanges || map.features == null)
				return;
			if (index == 0)
				MapEditor.execute(map.features.camNearClip.mutator(value));
			else if (index == 1)
				MapEditor.execute(map.features.camFarClip.mutator(value));
		});

		camBackgroundColor = new IntVectorPanel(true, 3, (index, value) -> {
			if (ignoreChanges || map.features == null)
				return;
			if (index == 0)
				MapEditor.execute(map.features.bgColorR.mutator(value));
			else if (index == 1)
				MapEditor.execute(map.features.bgColorG.mutator(value));
			else if (index == 2)
				MapEditor.execute(map.features.bgColorB.mutator(value));
		});
	}

	private void createFogComponents()
	{
		cbWorldFog = new JCheckBox(" World Fog");
		cbWorldFog.addActionListener((e) -> {
			tryExec(map.features.worldFog.enabled.mutator(cbWorldFog.isSelected()));
		});
		worldFogDist = new IntVectorPanel(true, 2, (index, value) -> {
			if (index == 0)
				tryExec(map.features.worldFog.start.mutator(value));
			else if (index == 1)
				tryExec(map.features.worldFog.end.mutator(value));

		});
		worldFogColor = new IntVectorPanel(true, 3, (index, value) -> {
			if (index == 0)
				tryExec(map.features.worldFog.R.mutator(value));
			else if (index == 1)
				tryExec(map.features.worldFog.G.mutator(value));
			else if (index == 2)
				tryExec(map.features.worldFog.B.mutator(value));

		});

		cbEntityFog = new JCheckBox(" Entity Fog");
		cbEntityFog.addActionListener((e) -> {
			tryExec(map.features.entityFog.enabled.mutator(cbEntityFog.isSelected()));
		});
		entityFogDist = new IntVectorPanel(true, 2, (index, value) -> {
			if (index == 0)
				tryExec(map.features.entityFog.start.mutator(value));
			else if (index == 1)
				tryExec(map.features.entityFog.end.mutator(value));

		});
		entityFogColor = new IntVectorPanel(true, 3, (index, value) -> {
			if (index == 0)
				tryExec(map.features.entityFog.R.mutator(value));
			else if (index == 1)
				tryExec(map.features.entityFog.G.mutator(value));
			else if (index == 2)
				tryExec(map.features.entityFog.B.mutator(value));

		});
	}

	private void createShadingComponents()
	{
		cbHasShading = new JCheckBox(" Sprite Shading");
		cbHasShading.addActionListener((e) -> {
			tryExec(map.features.hasSpriteShading.mutator(cbHasShading.isSelected()));
		});
		cbHasShading.setVerticalAlignment(SwingConstants.CENTER);

		profileNameField = new JTextField();
		profileNameField.setEnabled(false);
		profileNameField.setEditable(false);
		profileNameField.setHorizontalAlignment(JTextField.CENTER);
		SwingUtils.addBorderPadding(profileNameField);

		chooseProfileButton = new JButton("Choose");
		chooseProfileButton.addActionListener((e) -> {
			if (ignoreChanges || map.features == null)
				return;

			String profileName = map.features.shadingProfileName.get();
			EditableShadingProfile capturedProfile = map.captureCurrentShadingProfile(profileName);
			ProjectDatabase.SpriteShading.update(profileName, capturedProfile);

			EditableShadingData workingCopy = ProjectDatabase.SpriteShading.deepCopy();

			ShadingTreePanel panel = new ShadingTreePanel(workingCopy);
			panel.setPreferredSize(new Dimension(400, 480));

			int result = SwingUtils.getOptionDialog()
				.setParent(SwingGUI.instance())
				.setCounter(SwingGUI.instance().getDialogCounter())
				.setTitle("Sprite Shading Profiles")
				.setMessage(panel)
				.setMessageType(JOptionPane.PLAIN_MESSAGE)
				.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
				.setOptions("Select", "Cancel")
				.choose();

			if (result != JOptionPane.OK_OPTION)
				return;

			ProjectDatabase.SpriteShading.replaceWith(workingCopy);

			EditableShadingProfile selectedProfile = panel.getSelectedProfile();
			if (selectedProfile != null)
				map.changeShadingProfile(selectedProfile);

		});
		SwingUtils.addBorderPadding(chooseProfileButton);

		shadingColorPanel = new ColorChoicePanel(
			() -> {
				return map.features.shadingBaseColor.get();
			},
			(v) -> {
				tryExec(map.features.shadingBaseColor.mutator(v));
			});

		shadingOffsetField = new IntTextField((newValue) -> {
			tryExec(map.features.shadingOffset.mutator(newValue & 0xFF));
		});
		shadingOffsetField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(shadingOffsetField);
	}

	public OptionsPanel()
	{
		createOverrideComponents();
		createCameraComponents();
		createFogComponents();
		createShadingComponents();

		setLayout(new MigLayout("fillx, ins 8 8 0 8, wrap 5", "[3%][][21%][21%][21%]"));
		add(SwingUtils.getLabel("Asset Overrides", 14), "span");

		add(cbOverrideShape, "skip 1");
		add(overrideShapeText, "span, growx");
		add(cbOverrideHit, "skip 1");
		add(overrideHitText, "span, growx");

		if (!Environment.usingTexturePool())
			add(cbOverrideTex, "skip 1, span, growx");

		add(SwingUtils.getLabel("Camera Settings", 14), "gaptop 8, span");
		add(SwingUtils.getLabel("Vertical FOV", 12), "skip 1");
		add(camVfovField, "growx, wrap");
		add(SwingUtils.getLabel("Clip Planes", 12), "skip 1");
		add(camClipRange, "span 2, growx, wrap");
		add(SwingUtils.getLabel("BG Color", 12), "skip 1");
		add(camBackgroundColor, "span 3, growx, wrap");
		add(cbLeadPlayer, "skip 1, span");

		//JPanel fog = new JPanel(new MigLayout("fillx, ins 0, wrap 3", "[8][][50%]"));
		SwingUtils.setFontSize(cbWorldFog, 14);
		add(cbWorldFog, "gaptop 8, span");

		add(SwingUtils.getLabel("Distance", 12), "skip 1");
		add(worldFogDist, "span, growx");
		add(SwingUtils.getLabel("Color", 12), "skip 1");
		add(worldFogColor, "span, growx");

		SwingUtils.setFontSize(cbEntityFog, 14);
		add(cbEntityFog, "span, gaptop 8");

		add(SwingUtils.getLabel("Distance", 12), "skip 1");
		add(entityFogDist, "span, growx");
		add(SwingUtils.getLabel("Color", 12), "skip 1");
		add(entityFogColor, "span, growx");

		SwingUtils.setFontSize(cbHasShading, 14);
		add(cbHasShading, "span, gaptop 8");

		add(SwingUtils.getLabel("Profile", 12), "skip 1");
		add(profileNameField, "span 2, growx");
		add(chooseProfileButton, "growx");

		add(SwingUtils.getLabel("Base Color", 12), "skip 1");
		add(shadingColorPanel, "span, growx");

		add(SwingUtils.getLabel("Edge Offset", 12), "skip 1");
		add(shadingOffsetField, "growx");
		add(new JLabel(""), "span"); // dummy

		add(new JPanel(), "pushy");
	}

	@Override
	public void update(String tag)
	{
		updateFields(map.features);
	}

	public void setMap(Map m)
	{
		if (map != null)
			map.features.deregisterListener(this);

		map = m;

		if (map != null)
			map.features.registerListener(this);

		updateFields(map.features);
	}

	public void updateFields(Features features)
	{
		ignoreChanges = true;

		overrideShapeText.setText(features.shapeOverrideName.get());
		overrideHitText.setText(features.hitOverrideName.get());

		cbOverrideShape.setSelected(features.overrideShape.get());
		overrideShapeText.setEnabled(features.overrideShape.get());

		cbOverrideHit.setSelected(features.overrideHit.get());
		overrideHitText.setEnabled(features.overrideHit.get());

		cbOverrideTex.setSelected(features.overrideTex.get());

		cbLeadPlayer.setSelected(features.camLeadsPlayer.get());

		camVfovField.setValue(features.camVfov.get());
		camClipRange.setValues(features.camNearClip.get(), features.camFarClip.get());
		camBackgroundColor.setValues(features.bgColorR.get(), features.bgColorG.get(), features.bgColorB.get());

		cbWorldFog.setSelected(features.worldFog.enabled.get());
		worldFogDist.setEnabled(features.worldFog.enabled.get());
		worldFogColor.setEnabled(features.worldFog.enabled.get());
		worldFogDist.setValues(features.worldFog.start.get(), features.worldFog.end.get());
		worldFogColor.setValues(features.worldFog.R.get(), features.worldFog.G.get(), features.worldFog.B.get());

		cbEntityFog.setSelected(features.entityFog.enabled.get());
		entityFogDist.setEnabled(features.entityFog.enabled.get());
		entityFogColor.setEnabled(features.entityFog.enabled.get());
		entityFogDist.setValues(features.entityFog.start.get(), features.entityFog.end.get());
		entityFogColor.setValues(features.entityFog.R.get(), features.entityFog.G.get(), features.entityFog.B.get());

		boolean hasShading = features.hasSpriteShading.get();
		cbHasShading.setSelected(hasShading);

		if (!Environment.isDX()) {
			profileNameField.setText(features.shadingProfileName.get());
			profileNameField.setForeground(features.hasValidShadingProfile ? null : SwingUtils.getRedTextColor());
			chooseProfileButton.setEnabled(hasShading);
		}

		shadingOffsetField.setValue(features.shadingOffset.get());
		shadingOffsetField.setEnabled(hasShading);

		shadingColorPanel.setValue(features.shadingBaseColor.get());
		shadingColorPanel.setEnabled(hasShading);

		ignoreChanges = false;
	}
}
