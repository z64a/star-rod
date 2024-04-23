package game.map.scripts.generators.foliage;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import app.SwingUtils;
import game.map.Map;
import game.map.shape.Model;
import net.miginfocom.swing.MigLayout;

public class FoliageModelEditor extends JPanel
{
	private String name;
	private final JComboBox<String> nameBox;

	public FoliageModelEditor(Map map, FoliageModel m)
	{
		name = m.modelName.get();

		List<String> names = new ArrayList<>();
		for (Model mdl : map.modelTree) {
			if (mdl.hasMesh())
				names.add(mdl.getName());
		}
		String[] markerBoxValues = new String[names.size()];
		for (int i = 0; i < markerBoxValues.length; i++)
			markerBoxValues[i] = names.get(i);

		nameBox = new JComboBox<>(markerBoxValues);
		nameBox.addActionListener((e) -> {
			name = (String) nameBox.getSelectedItem();
		});
		SwingUtils.addBorderPadding(nameBox);

		nameBox.setMaximumRowCount(16);
		nameBox.setEditable(true);
		nameBox.setSelectedItem(name);

		setLayout(new MigLayout("fill, wrap 2", "[]16[200::]"));

		add(new JLabel("Model Name"));
		add(nameBox, "growx");
	}

	public String getValue()
	{ return name; }
}
