package util.ui;

import java.util.function.BiConsumer;

import javax.swing.JPanel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;

public class IntVectorPanel extends JPanel
{
	private IntTextField[] fields;

	public IntVectorPanel(int dimension, BiConsumer<Integer, Integer> listener)
	{
		this(dimension, SwingConstants.CENTER, listener);
	}

	public IntVectorPanel(int dimension, int alignment, BiConsumer<Integer, Integer> listener)
	{
		fields = new IntTextField[dimension];

		setLayout(new MigLayout("fillx, ins 0"));

		for (int i = 0; i < dimension; i++) {
			final int index = i;
			fields[i] = new IntTextField((newValue) -> listener.accept(index, newValue));
			fields[i].setHorizontalAlignment(alignment);
			add(fields[i], "growx, sg vec");
		}
	}

	public void addBorderPaddings()
	{
		for (IntTextField field : fields)
			SwingUtils.addVerticalBorderPadding(field);
	}

	public void setValues(int ... values)
	{
		if (values.length != fields.length)
			throw new IllegalArgumentException("Number of supplied values does not match vector length.");

		for (int i = 0; i < values.length; i++)
			fields[i].setValue(values[i]);
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		for (IntTextField field : fields)
			field.setEnabled(enabled);
	}
}
