package game.sprite.editor;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import app.config.Config;
import app.config.ConfigCheckBox;
import app.config.Options;
import app.config.Options.ConfigOptionEditor;
import net.miginfocom.swing.MigLayout;

public class SpritePreferencesPanel extends JPanel
{
	private final List<ConfigOptionEditor> optEditors;

	public void setValues(Config cfg)
	{
		for (ConfigOptionEditor editor : optEditors)
			editor.read(cfg);
	}

	public void getValues(Config cfg)
	{
		for (ConfigOptionEditor editor : optEditors)
			editor.write(cfg);
	}

	public SpritePreferencesPanel()
	{
		ConfigCheckBox cb;
		optEditors = new ArrayList<>();

		setLayout(new MigLayout("fill, wrap"));

		cb = new ConfigCheckBox(Options.SprWarnIrreversible);
		cb.setIconTextGap(8);
		optEditors.add(cb);
		add(cb);

		cb = new ConfigCheckBox(Options.SprStrictErrorChecking);
		cb.setIconTextGap(12);
		optEditors.add(cb);
		add(cb);
	}
}
