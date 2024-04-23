package util.ui;

import java.awt.Color;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;

public class LabeledDoubleSpinner extends JComponent
{
	private JSpinner spinner;

	public double getValue()
	{ return (Double) spinner.getValue(); }

	public void setValue(double val)
	{
		spinner.setValue(val);
	}

	public void addChangeListener(ChangeListener listener)
	{
		spinner.addChangeListener(listener);
	}

	public LabeledDoubleSpinner(String msg, double minValue, double maxValue, double initialValue, double step)
	{
		spinner = new JSpinner();
		SwingUtils.setFontSize(spinner, 12);

		SpinnerModel model = new SpinnerNumberModel(initialValue, minValue, maxValue, step);
		spinner.setModel(model);

		JLabel lbl = new JLabel(msg);
		SwingUtils.setFontSize(lbl, 12);

		setLayout(new MigLayout("insets 0, fill"));
		add(lbl, "pushx");
		add(spinner, "w 72!");
	}

	public LabeledDoubleSpinner(String msg, Color c, double minValue, double maxValue, double initialValue, double step)
	{
		spinner = new JSpinner();
		SwingUtils.setFontSize(spinner, 12);

		SpinnerModel model = new SpinnerNumberModel(initialValue, minValue, maxValue, step);
		spinner.setModel(model);

		JLabel lbl = new JLabel(msg);
		SwingUtils.setFontSize(lbl, 12);
		lbl.setForeground(c);

		setLayout(new MigLayout("insets 0, fill"));
		add(lbl, "pushx");
		add(spinner, "w 72!");
	}
}
