package game.map.editor;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import app.config.Config;
import app.config.ConfigCheckBox;
import app.config.ConfigFloatSpinner;
import app.config.ConfigIntSpinner;
import app.config.Options;
import app.config.Options.ConfigOptionEditor;
import net.miginfocom.swing.MigLayout;

public class MapPreferencesPanel extends JPanel
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

	public MapPreferencesPanel()
	{
		ConfigIntSpinner is;
		ConfigFloatSpinner fs;
		ConfigCheckBox cb;
		optEditors = new ArrayList<>();

		setLayout(new MigLayout("wrap 2", "[fill,grow]8[fill,grow]"));

		is = new ConfigIntSpinner(Options.MapUndoLimit);
		optEditors.add(is);
		add(new JLabel(Options.MapUndoLimit.guiName));
		add(is);

		is = new ConfigIntSpinner(Options.BackupInterval);
		optEditors.add(is);
		add(new JLabel(Options.BackupInterval.guiName));
		add(is);

		fs = new ConfigFloatSpinner(Options.AngleSnap);
		optEditors.add(fs);
		add(new JLabel(Options.AngleSnap.guiName));
		add(fs);

		fs = new ConfigFloatSpinner(Options.uvScale);
		optEditors.add(fs);
		add(new JLabel(Options.uvScale.guiName));
		add(fs);

		fs = new ConfigFloatSpinner(Options.NormalsLength);
		optEditors.add(fs);
		add(new JLabel(Options.NormalsLength.guiName));
		add(fs);

		is = new ConfigIntSpinner(Options.ScrollSensitivity);
		optEditors.add(is);
		add(new JLabel(Options.ScrollSensitivity.guiName));
		add(is);

		cb = new ConfigCheckBox(Options.ShowCurrentMode);
		cb.setText("");
		optEditors.add(cb);
		add(new JLabel(Options.ShowCurrentMode.guiName));
		add(cb);
	}
}
