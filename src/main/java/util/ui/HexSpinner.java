package util.ui;

import java.text.ParseException;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;

import app.SwingUtils;

public class HexSpinner extends JSpinner
{
	public HexSpinner(int minValue, int maxValue, int initialValue, int step)
	{
		this(minValue, maxValue, initialValue, step, SwingConstants.CENTER);
	}

	public HexSpinner(int minValue, int maxValue, int initialValue, int step, int alignment)
	{
		SwingUtils.setFontSize(this, 12);
		setModel(new SpinnerNumberModel(initialValue, minValue, maxValue, step));

		JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) getEditor();
		JFormattedTextField tf = editor.getTextField();

		tf.setHorizontalAlignment(alignment);

		DefaultFormatterFactory ff = (DefaultFormatterFactory) tf.getFormatterFactory();
		ff.setDefaultFormatter(new HexFormatter());

		setValue(initialValue);
	}

	private static class HexFormatter extends DefaultFormatter
	{
		@Override
		public Object stringToValue(String text) throws ParseException
		{
			try {
				return (int) Long.parseLong(text, 16);
			}
			catch (NumberFormatException nfe) {
				throw new ParseException(text, 0);
			}
		}

		@Override
		public String valueToString(Object value) throws ParseException
		{
			return Long.toHexString(
				((Integer) value).intValue()).toUpperCase();
		}
	}

	public void setMaximum(int val)
	{
		SpinnerNumberModel model = (SpinnerNumberModel) getModel();
		model.setMaximum(val);
	}

	@Override
	public Integer getValue()
	{
		return (Integer) super.getValue();
	}
}
