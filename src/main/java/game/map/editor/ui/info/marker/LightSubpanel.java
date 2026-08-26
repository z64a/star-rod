package game.map.editor.ui.info.marker;

import java.awt.Color;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.editor.ui.SwatchPanel;
import game.map.editor.ui.SwingGUI;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.marker.LightComponent;
import game.map.marker.LightComponent.SetFalloffType;
import game.map.marker.LightComponent.SetLightFalloff;
import game.map.shading.FalloffType;
import net.miginfocom.swing.MigLayout;
import util.ui.DoubleTextField;
import util.ui.HexTextField;

public class LightSubpanel extends JPanel
{
	private final MarkerInfoPanel parent;

	private SwatchPanel colorPreview;
	private HexTextField colorField;

	private DoubleTextField falloffField;

	private JCheckBox cbEnabled;
	private JComboBox<FalloffType> falloffBox;

	public LightSubpanel(MarkerInfoPanel parent)
	{
		this.parent = parent;

		colorField = new HexTextField(6, (newValue) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			LightComponent light = parent.getData().lightComponent;
			MapEditor.execute(light.color.mutator(newValue));
		});
		colorField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(colorField);

		JButton chooseColorButton = new JButton("Choose");
		chooseColorButton.addActionListener((e) -> {
			if (parent.getData() == null)
				return;
			LightComponent light = parent.getData().lightComponent;

			Color oldColor = new Color(light.color.get());
			Color newColor = SwingGUI.instance().prompt_ChooseColor("Choose Light Color", oldColor);
			if (newColor != null)
				MapEditor.execute(light.color.mutator(newColor.getRGB() & 0xFFFFFF));
		});

		falloffField = new DoubleTextField((newValue) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			LightComponent light = parent.getData().lightComponent;
			MapEditor.execute(new SetLightFalloff(light, newValue));
		});
		falloffField.setHorizontalAlignment(SwingConstants.CENTER);
		falloffField.setColumns(16);
		SwingUtils.addBorderPadding(falloffField);

		falloffBox = new JComboBox<>(FalloffType.values());
		falloffBox.addActionListener((e) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			LightComponent light = parent.getData().lightComponent;

			FalloffType newValue = (FalloffType) falloffBox.getSelectedItem();
			MapEditor.execute(new SetFalloffType(light, newValue));
		});
		SwingUtils.addBorderPadding(falloffBox);

		cbEnabled = new JCheckBox(" Initially enabled");
		cbEnabled.addActionListener((e) -> {
			if (parent.ignoreEvents() || parent.getData() == null)
				return;
			LightComponent light = parent.getData().lightComponent;

			MapEditor.execute(light.enabled.mutator(cbEnabled.isSelected()));
		});
		// SwingUtils.setFontSize(cbEnabled, 12);

		colorPreview = new SwatchPanel(1.32f, 1.33f);

		setLayout(new MigLayout("fillx, ins 0", MarkerInfoPanel.FOUR_COLUMNS));

		add(SwingUtils.getLabel("Light Settings", 14), "growx, gapbottom 4, span, wrap");

		add(new JLabel("Color"));
		add(colorPreview, "growx, growy");
		add(colorField, "growx");
		add(chooseColorButton, "growx, growy, wrap");

		add(new JLabel("Radius"));
		add(falloffField, "growx, span 2");
		add(falloffBox, "growx, wrap");

		add(cbEnabled, "skip 1, growx, span, wrap, gaptop 4");
	}

	public void updateFields()
	{
		LightComponent light = parent.getData().lightComponent;

		cbEnabled.setSelected(light.enabled.get());

		colorField.setValue(light.color.get() & 0xFFFFFF);
		colorPreview.setForeground(new Color(light.color.get()));

		falloffField.setValue(light.falloffDist);
		falloffBox.setSelectedItem(light.falloffType);
	}
}
