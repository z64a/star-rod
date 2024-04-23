package app.config;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import app.config.Options.ConfigOptionEditor;

public class ConfigIntSpinner extends JSpinner implements ConfigOptionEditor
{
	public final Options opt;

	private final SpinnerNumberModel model;

	public ConfigIntSpinner(Options opt)
	{
		if (opt.type != Options.Type.Integer)
			throw new RuntimeException("ConfigIntSpinner is not compatible with option: " + opt);

		this.opt = opt;

		SwingUtils.setFontSize(this, 12);
		model = new SpinnerNumberModel(Integer.parseInt(opt.defaultValue), Math.round(opt.min), Math.round(opt.max), Math.round(opt.step));
		setModel(model);

		JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) getEditor();
		JFormattedTextField tf = editor.getTextField();

		tf.setHorizontalAlignment(SwingConstants.CENTER);

		if (!opt.guiDesc.isEmpty())
			super.setToolTipText(opt.guiDesc);
	}

	@Override
	public void read(Config cfg)
	{
		super.setValue(cfg.getInteger(opt));
	}

	@Override
	public boolean write(Config cfg)
	{
		int prev = cfg.getInteger(opt);
		int value = model.getNumber().intValue();

		if (value != prev) {
			cfg.setInteger(opt, value);
			return true;
		}

		return false;
	}
}
