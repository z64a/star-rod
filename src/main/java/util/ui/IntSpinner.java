package util.ui;

import java.util.function.Consumer;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import app.SwingUtils;

public class IntSpinner extends JSpinner
{
	public IntSpinner(Consumer<Integer> listener)
	{
		JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) getEditor();
		JFormattedTextField tf = editor.getTextField();

		tf.setHorizontalAlignment(SwingConstants.CENTER);
		tf.setMargin(SwingUtils.TEXTBOX_INSETS);

		addChangeListener((e) -> {
			listener.accept((int) getValue());
		});
	}

	public IntSpinner(Consumer<Integer> listener, SpinnerNumberModel model)
	{
		super(model);

		JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) getEditor();
		JFormattedTextField tf = editor.getTextField();

		tf.setHorizontalAlignment(SwingConstants.CENTER);
		tf.setMargin(SwingUtils.TEXTBOX_INSETS);

		addChangeListener((e) -> {
			listener.accept((int) getValue());
		});
	}

	public IntSpinner(int min, int max, Consumer<Integer> listener)
	{
		this(listener, new SpinnerNumberModel(0, min, max, 1));
	}
}
