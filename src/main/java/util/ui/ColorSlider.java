package util.ui;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;

public class ColorSlider extends JComponent
{
	public static interface SliderListener
	{
		public void update(boolean preview, int value);
	}

	private static enum UpdateMode
	{
		NONE, FROM_SLIDER, FROM_TEXTFIELD, FROM_OUTSIDE
	}

	private UpdateMode update = UpdateMode.NONE;

	private final int max;
	private final int min;

	private final JCheckBox checkbox;
	private final JTextField textField;
	private final JSlider slider;

	private final SliderListener listener;

	public ColorSlider(String lblText, String lblLayout, int minValue, int maxValue, int initialValue, int ticks, SliderListener listener)
	{
		this.listener = listener;
		min = minValue;
		max = maxValue;
		slider = new JSlider(min, max, initialValue);
		slider.setMajorTickSpacing(ticks);
		slider.setMinorTickSpacing(ticks / 2);
		slider.setPaintTicks(true);

		slider.addChangeListener((e) -> {
			if (update != UpdateMode.NONE)
				return;

			if (slider.getValueIsAdjusting())
				updatePreview(UpdateMode.FROM_SLIDER, slider.getValue());
			else
				updateValue(UpdateMode.FROM_SLIDER, slider.getValue());
		});

		checkbox = new JCheckBox();
		checkbox.setSelected(true);

		textField = new JTextField(Integer.toString(0), 5);
		textField.setFont(textField.getFont().deriveFont(12f));
		textField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(textField);

		textField.setDocument(new LimitedLengthDocument(6));

		// document filter might be nicer, but this works
		textField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent ke)
			{
				if (update != UpdateMode.NONE)
					return;

				String text = textField.getText();
				if (text.isEmpty() || text.equals("-"))
					return;

				try {
					int value = Integer.parseInt(text);
					if (value > max) {
						value = max;
						textField.setText(Integer.toString(value));
					}
					else if (value < min) {
						value = min;
						textField.setText(Integer.toString(value));
					}
					updatePreview(UpdateMode.FROM_TEXTFIELD, value);
				}
				catch (NumberFormatException e) {
					textField.setText(Integer.toString(slider.getValue()));
				}
			}
		});

		// things that commit changes from text field
		textField.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e)
			{}

			@Override
			public void focusLost(FocusEvent e)
			{
				commitTextField();
			}
		});
		textField.addActionListener((e) -> {
			commitTextField();
		});

		setLayout(new MigLayout("fillx, ins 0"));

		add(SwingUtils.getLabel(lblText, SwingConstants.CENTER, 12), lblLayout);
		add(slider, "growx, pushx");
		add(textField, "w 80!");

		setValue(slider.getValue());
	}

	private void commitTextField()
	{
		String text = textField.getText();
		if (text.isEmpty()) {
			updateValue(UpdateMode.FROM_TEXTFIELD, min);
			return;
		}

		try {
			int value = Integer.parseInt(text);
			updateValue(UpdateMode.FROM_TEXTFIELD, value);
		}
		catch (NumberFormatException n) {
			textField.setText(Integer.toString(slider.getValue()));
		}
	}

	public int getMaxValue()
	{ return max; }

	public int getValue()
	{ return slider.getValue(); }

	public void setValue(int value)
	{
		update = UpdateMode.FROM_OUTSIDE;
		textField.setText(Integer.toString(value));
		slider.setValue(value);
		update = UpdateMode.NONE;
	}

	private void updatePreview(UpdateMode mode, int value)
	{
		update = mode;
		if (mode == UpdateMode.FROM_SLIDER)
			textField.setText(Integer.toString(value));

		listener.update(true, value);
		update = UpdateMode.NONE;
	}

	private void updateValue(UpdateMode mode, int value)
	{
		update = mode;
		if (mode == UpdateMode.FROM_SLIDER)
			textField.setText(Integer.toString(value));
		else if (mode == UpdateMode.FROM_TEXTFIELD)
			slider.setValue(value);

		listener.update(false, value);
		update = UpdateMode.NONE;
	}
}
