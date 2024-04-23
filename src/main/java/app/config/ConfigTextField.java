package app.config;

import javax.swing.JTextField;
import javax.swing.SwingConstants;

import app.config.Options.ConfigOptionEditor;

public class ConfigTextField extends JTextField implements ConfigOptionEditor
{
	public final Options opt;

	public ConfigTextField(Options opt)
	{
		if (opt.type != Options.Type.String)
			throw new RuntimeException("ConfigTextField is not compatible with option: " + opt);

		this.opt = opt;

		setHorizontalAlignment(SwingConstants.CENTER);

		if (!opt.guiDesc.isEmpty())
			super.setToolTipText(opt.guiDesc);
	}

	@Override
	public void read(Config cfg)
	{
		setText(cfg.getString(opt));
	}

	@Override
	public boolean write(Config cfg)
	{
		String prev = cfg.getString(opt);
		String value = getText();

		if (!value.equals(prev)) {
			cfg.setString(opt, value);
			return true;
		}

		return false;
	}
}
