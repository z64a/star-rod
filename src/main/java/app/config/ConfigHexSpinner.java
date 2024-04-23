package app.config;

import javax.swing.SwingConstants;

import app.config.Options.ConfigOptionEditor;
import util.ui.HexSpinner;

public class ConfigHexSpinner extends HexSpinner implements ConfigOptionEditor
{
	public final Options opt;

	public ConfigHexSpinner(Options opt)
	{
		super((int) Math.round(opt.min), (int) Math.round(opt.max), (int) Long.parseLong(opt.defaultValue, 16), (int) opt.step,
			SwingConstants.CENTER);

		if (opt.type != Options.Type.Hex)
			throw new RuntimeException("ConfigHexSpinner is not compatible with option: " + opt);

		this.opt = opt;

		if (!opt.guiDesc.isEmpty())
			super.setToolTipText(opt.guiDesc);
	}

	@Override
	public void read(Config cfg)
	{
		super.setValue(cfg.getHex(opt));
	}

	@Override
	public boolean write(Config cfg)
	{
		int prev = cfg.getHex(opt);
		int value = getValue();

		if (value != prev) {
			cfg.setHex(opt, value);
			return true;
		}

		return false;
	}
}
