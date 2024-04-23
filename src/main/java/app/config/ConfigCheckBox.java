package app.config;

import javax.swing.JCheckBox;

import app.config.Options.ConfigOptionEditor;

public class ConfigCheckBox extends JCheckBox implements ConfigOptionEditor
{
	public final Options opt;

	public ConfigCheckBox(Options opt)
	{
		super(opt.guiName);

		if (opt.type != Options.Type.Boolean)
			throw new RuntimeException("ConfigCheckBox is not compatible with option: " + opt);

		this.opt = opt;

		if (!opt.guiDesc.isEmpty())
			super.setToolTipText(opt.guiDesc);
	}

	@Override
	public void read(Config cfg)
	{
		setSelected(cfg.getBoolean(opt));
	}

	@Override
	public boolean write(Config cfg)
	{
		boolean prev = cfg.getBoolean(opt);
		boolean value = isSelected();

		if (value != prev) {
			cfg.setBoolean(opt, value);
			return true;
		}

		return false;
	}
}
