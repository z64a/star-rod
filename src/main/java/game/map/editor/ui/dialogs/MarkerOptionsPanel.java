package game.map.editor.ui.dialogs;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import game.map.marker.Marker.MarkerType;
import net.miginfocom.swing.MigLayout;

public class MarkerOptionsPanel extends JPanel implements ActionListener
{
	private static final MarkerType DEFAULT_TYPE = MarkerType.Position;
	private MarkerType markerType = DEFAULT_TYPE;

	private final JComboBox<MarkerType> typeComboBox;
	private final JTextField nameField;

	private static final String DEFAULT_NAME = "Marker";

	private static MarkerOptionsPanel instance = null;

	public static MarkerOptionsPanel getInstance()
	{
		if (instance == null)
			instance = new MarkerOptionsPanel();

		return instance;
	}

	private MarkerOptionsPanel()
	{
		typeComboBox = new JComboBox<>(MarkerType.values());
		typeComboBox.setMaximumRowCount(MarkerType.values().length);
		typeComboBox.removeItem(MarkerType.Root);
		typeComboBox.setSelectedItem(markerType);
		typeComboBox.setActionCommand("choose_primitive_type");
		typeComboBox.addActionListener(this);

		nameField = new JTextField(DEFAULT_NAME);

		setLayout(new MigLayout("fill, hidemode 3"));
		add(new JLabel("Name"), "w 25%");
		add(nameField, "pushx, growx, wrap");
		add(new JLabel("Type"), "w 25%");
		add(typeComboBox, "pushx, growx, wrap, gapbottom 8");

		setType(DEFAULT_TYPE);
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		if (e.getActionCommand().equals("choose_primitive_type")) {
			setType((MarkerType) typeComboBox.getSelectedItem());
		}
	}

	private void setType(MarkerType newType)
	{
		markerType = newType;

		Window w = SwingUtilities.getWindowAncestor(this);
		if (w != null)
			w.pack();
	}

	public static String getMarkerName()
	{
		String name = instance.nameField.getText();
		return name.isEmpty() ? DEFAULT_NAME : name;
	}

	public static MarkerType getMarkerType()
	{
		return instance.markerType;
	}
}
