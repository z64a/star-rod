package game.map.editor.ui;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import app.SwingUtils;

public class StandardEditableComboBox extends JComboBox<String>
{
	public StandardEditableComboBox(Consumer<String> editCallback, String[] entries)
	{
		this(editCallback, Arrays.asList(entries));
	}

	public StandardEditableComboBox(Consumer<String> editCallback, List<String> entries)
	{
		super(new DefaultComboBoxModel<String>());

		addActionListener((e) -> {
			editCallback.accept((String) getSelectedItem());
		});
		SwingUtils.addBorderPadding(this);

		setPrototypeDisplayValue("");
		setMaximumRowCount(16);
		setEditable(true);

		updateModel(entries);
	}

	public void updateModel(String[] entries)
	{
		updateModel(Arrays.asList(entries));
	}

	public void updateModel(List<String> entries)
	{
		String current = (String) getSelectedItem();

		removeAllItems();
		for (String s : entries)
			addItem(s);

		setSelectedItem(current);
	}

	public void setSelectedItem(String s)
	{
		super.setSelectedItem(s);

		boolean found = false;
		if (s != null) {
			for (int i = 0; i < getItemCount(); i++) {
				if (getItemAt(i).equals(s)) {
					found = true;
					break;
				}
			}
		}
		setForeground(found ? null : SwingUtils.getRedTextColor());
	}
}
