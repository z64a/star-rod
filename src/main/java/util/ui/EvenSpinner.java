package util.ui;

import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;

public class EvenSpinner extends JSpinner
{
	public EvenSpinner()
	{
		super();

		this.addChangeListener((e) -> {
			JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) this.getEditor();
			JTextField textField = editor.getTextField();

			int value = (Integer) getValue();

			// set text to red when displaying an odd number
			if (value % 2 != 0)
				textField.setForeground(SwingUtils.getRedTextColor());
			else
				textField.setForeground(null);
		});
	}

	@Override
	public Object getNextValue()
	{
		SpinnerNumberModel model = (SpinnerNumberModel) getModel();
		int current = (Integer) getValue();
		int max = (Integer) model.getMaximum();

		int nextValue = (current % 2 == 0) ? current + 2 : current + 1;
		return (nextValue <= max) ? nextValue : null;
	}

	@Override
	public Object getPreviousValue()
	{
		SpinnerNumberModel model = (SpinnerNumberModel) getModel();
		int current = (Integer) getValue();
		int min = (Integer) model.getMinimum();

		int previousValue = (current % 2 == 0) ? current - 2 : current - 1;
		return (previousValue >= min) ? previousValue : null;
	}
}
