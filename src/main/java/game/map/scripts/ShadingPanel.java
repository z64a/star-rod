package game.map.scripts;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import app.SwingUtils;
import game.ProjectDatabase;
import game.map.Map;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.CommandBatch;
import game.map.editor.ui.SwingGUI;
import game.map.shading.ShadingLightSource;
import game.map.shading.ShadingProfile;
import game.map.shading.ShadingProfile.ShadingProfileComboBoxRenderer;
import game.map.shading.SpriteShadingData.CreateProfile;
import game.map.shading.SpriteShadingData.DeleteProfile;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

/**
 * Singleton JPanel for dipsplaying model lighting data.
 */
public class ShadingPanel extends JPanel implements IShutdownListener
{
	private Map map;

	private JCheckBox cbHasShading;
	private JComboBox<ShadingProfile> profileBox;
	private ShadingProfileInfoPanel profilePanel;

	private boolean ignoreChanges = false;

	private static ShadingPanel instance = null;

	public static ShadingPanel instance()
	{
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
	}

	public void repaintComboBox()
	{
		profileBox.repaint();
	}

	public ShadingPanel()
	{
		if (instance != null)
			throw new IllegalStateException("There can be only one LightingPanel");
		instance = this;
		MapEditor.instance().registerOnShutdown(this);

		cbHasShading = new JCheckBox(" Using profile");
		cbHasShading.addActionListener((e) -> {
			if (ignoreChanges || map.scripts == null)
				return;
			MapEditor.execute(map.scripts.hasSpriteShading.mutator(cbHasShading.isSelected()));
		});

		profileBox = new JComboBox<>(new ListAdapterComboboxModel<>(ProjectDatabase.SpriteShading.listModel));
		profileBox.addActionListener((e) -> {
			if (ignoreChanges || map.scripts == null)
				return;
			ShadingProfile selected = (ShadingProfile) profileBox.getSelectedItem();
			MapEditor.execute(new SetShadingProfile(selected));
		});

		SwingUtils.addBorderPadding(profileBox);
		profileBox.setMaximumRowCount(24);
		profileBox.setRenderer(new ShadingProfileComboBoxRenderer());

		profilePanel = new ShadingProfileInfoPanel(this);

		JButton createProfileButton = new JButton("Create");
		createProfileButton.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			CreateProfile createCmd = new CreateProfile(ProjectDatabase.SpriteShading);
			ShadingProfile newProfile = createCmd.getProfile();
			CommandBatch createBatch = new CommandBatch("Create Shading Profile");
			createBatch.addCommand(createCmd);
			createBatch.addCommand(new SetShadingProfile(newProfile));
			MapEditor.execute(createBatch);
		});

		JButton deleteProfileButton = new JButton("Delete");
		deleteProfileButton.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			ShadingProfile selected = (ShadingProfile) profileBox.getSelectedItem();
			if (selected == null)
				return;

			boolean shouldDelete = true;
			if (selected.vanilla) {
				SwingGUI.instance().notify_OpenDialog();
				int response = SwingUtils.showFramedConfirmDialog(SwingGUI.instance(),
					"Selected profile is vanilla.\r\nAre you sure you want to delete it?\r\n",
					"Warning", JOptionPane.YES_NO_CANCEL_OPTION);
				SwingGUI.instance().notify_CloseDialog();

				shouldDelete = (response == JOptionPane.YES_OPTION);
			}

			if (shouldDelete) {
				CommandBatch deleteBatch = new CommandBatch("Delete Shading Profile");
				deleteBatch.addCommand(new SetShadingProfile(null));
				deleteBatch.addCommand(new DeleteProfile(ProjectDatabase.SpriteShading, selected));
				MapEditor.execute(deleteBatch);
			}
		});

		setLayout(new MigLayout("fill, hidemode 0, ins 0 16 16 16", "[][50%]"));

		add(SwingUtils.getLabel("Sprite Shading", 14), "gaptop 16, gapbottom 8, wrap, span");

		add(cbHasShading, "growx, growy, gapleft 8");
		add(profileBox, "growx, wrap");

		add(new JPanel(), "grow");
		add(createProfileButton, "grow, split 2");
		add(deleteProfileButton, "grow, wrap");

		add(profilePanel, "pushy, grow, span, gaptop 16");
	}

	public void setMap(Map m)
	{
		this.map = m;
		updateFields(m.scripts);
	}

	public void updateFields(ScriptData data)
	{
		ignoreChanges = true;

		cbHasShading.setSelected(data.hasSpriteShading.get());
		profileBox.setEnabled(data.hasSpriteShading.get());
		profileBox.setSelectedItem(data.shadingProfile.get());

		ShadingProfile profile = data.shadingProfile.get();
		profilePanel.setData(profile);
		profilePanel.setVisible(profile != null);

		ignoreChanges = false;
	}

	private class SetShadingProfile extends AbstractCommand
	{
		private final ShadingProfile oldShading;
		private final ShadingProfile newShading;

		public SetShadingProfile(ShadingProfile profile)
		{
			super("Change Shading Profile");

			this.oldShading = map.scripts.shadingProfile.get();
			this.newShading = profile;
		}

		@Override
		public boolean shouldExec()
		{
			return newShading != oldShading;
		}

		@Override
		public void exec()
		{
			super.exec();
			map.scripts.shadingProfile.set(newShading);
			updateFields(map.scripts);

			if (oldShading != null) {
				for (ShadingLightSource source : oldShading.sources) {
					editor.removeEditorObject(source);
					editor.selectionManager.deleteObject(source);
				}
			}

			if (newShading != null) {
				for (ShadingLightSource source : newShading.sources) {
					editor.addEditorObject(source);
					editor.selectionManager.createObject(source);
				}
			}
		}

		@Override
		public void undo()
		{
			super.undo();

			if (newShading != null) {
				for (ShadingLightSource source : newShading.sources) {
					editor.removeEditorObject(source);
					editor.selectionManager.deleteObject(source);
				}
			}

			if (oldShading != null) {
				for (ShadingLightSource source : oldShading.sources) {
					editor.addEditorObject(source);
					editor.selectionManager.createObject(source);
				}
			}

			map.scripts.shadingProfile.set(oldShading);
			updateFields(map.scripts);
		}
	}
}
