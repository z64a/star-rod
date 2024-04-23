package util.ui;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

public class HexVectorPanel extends JPanel
{
	private HexTextField[] fields;

	public static interface IntUpdateListener
	{
		void handleUpdate(int component, int newValue);
	}

	public HexVectorPanel(int dimension, IntUpdateListener listener)
	{
		fields = new HexTextField[dimension];

		setLayout(new MigLayout("fillx, ins 0"));

		for (int i = 0; i < dimension; i++) {
			final int index = i;
			fields[i] = new HexTextField((newValue) -> listener.handleUpdate(index, newValue));
			add(fields[i], "growx, sg vec");
		}
	}

	public void setValues(int ... values)
	{
		if (values.length != fields.length)
			throw new IllegalArgumentException("Number of supplied values does not match vector length.");

		for (int i = 0; i < values.length; i++)
			fields[i].setValue(values[i]);
	}
}
