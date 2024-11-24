package util.ui;

import java.util.LinkedHashMap;

import javax.swing.JComboBox;

public class EnumComboBox extends JComboBox<String>
{
	private final LinkedHashMap<String, Integer> itemMap;

	public EnumComboBox(LinkedHashMap<String, Integer> itemMap)
	{
		super(itemMap.keySet().toArray(new String[0]));
		this.itemMap = itemMap;
	}

	public Integer getSelectedValue()
	{
		String selectedKey = (String) getSelectedItem();
		return itemMap.get(selectedKey);
	}
}
