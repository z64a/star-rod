package app.config;

import javax.swing.SwingConstants;

import app.config.Options.ConfigOptionEditor;
import util.ui.HexTextField;

public class ConfigHexField extends HexTextField implements ConfigOptionEditor
{
	public final Options opt;

	public ConfigHexField(Options opt)
	{
		super(6, true, (i) -> {});

		if (opt.type != Options.Type.Hex)
			throw new RuntimeException("ConfigHexField is not compatible with option: " + opt);

		this.opt = opt;

		setHorizontalAlignment(SwingConstants.CENTER);

		if (!opt.guiDesc.isEmpty())
			super.setToolTipText(opt.guiDesc);
	}

	@Override
	public void read(Config cfg)
	{
		setValue(cfg.getHex(opt));
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
