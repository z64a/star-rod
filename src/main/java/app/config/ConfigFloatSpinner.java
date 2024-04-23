package app.config;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import app.config.Options.ConfigOptionEditor;

public class ConfigFloatSpinner extends JSpinner implements ConfigOptionEditor
{
	public final Options opt;

	private final SpinnerNumberModel model;

	public ConfigFloatSpinner(Options opt)
	{
		if (opt.type != Options.Type.Float)
			throw new RuntimeException("ConfigFloatSpinner is not compatible with option: " + opt);

		this.opt = opt;

		SwingUtils.setFontSize(this, 12);
		model = new SpinnerNumberModel(Float.parseFloat(opt.defaultValue), opt.min, opt.max, opt.step);
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
		super.setValue(cfg.getFloat(opt));
	}

	@Override
	public boolean write(Config cfg)
	{
		float prev = cfg.getFloat(opt);
		float value = model.getNumber().floatValue();

		if (value != prev) {
			cfg.setFloat(opt, value);
			return true;
		}

		return false;
	}
}
