package util.ui;

import java.awt.Color;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;

public class LabeledIntegerSpinner extends JComponent
{
	private JSpinner spinner;

	public int getValue()
	{
		return (Integer) spinner.getValue();
	}

	public void setValue(int val)
	{
		spinner.setValue(val);
	}

	public void setMaximum(int val)
	{
		SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
		model.setMaximum(val);
	}

	public LabeledIntegerSpinner(String msg, int minValue, int maxValue, int initialValue)
	{
		spinner = new JSpinner();
		spinner.setFont(spinner.getFont().deriveFont(12f));

		SpinnerModel model = new SpinnerNumberModel(initialValue, minValue, maxValue, 1);
		spinner.setModel(model);

		JLabel lbl = new JLabel(msg);
		lbl.setFont(lbl.getFont().deriveFont(12f));

		setLayout(new MigLayout("insets 0, fill"));
		add(lbl, "pushx");
		add(spinner, "w 50%");
	}

	public LabeledIntegerSpinner(String msg, Color c, int minValue, int maxValue, int initialValue)
	{
		spinner = new JSpinner();
		spinner.setFont(spinner.getFont().deriveFont(12f));

		SpinnerModel model = new SpinnerNumberModel(initialValue, minValue, maxValue, 1);
		spinner.setModel(model);

		JLabel lbl = new JLabel(msg);
		lbl.setFont(lbl.getFont().deriveFont(12f));
		lbl.setForeground(c);

		setLayout(new MigLayout("insets 0, fill"));
		add(lbl, "pushx");
		add(spinner, "w 72!");
	}

	public void addChangeListener(ChangeListener listener)
	{
		spinner.addChangeListener(listener);
	}
}
