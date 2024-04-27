package game.map.scripts;

import java.awt.Color;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.editor.MapInfoPanel;
import game.map.editor.ui.SwatchPanel;
import game.map.editor.ui.SwingGUI;
import game.map.shading.ShadingLightSource;
import game.map.shading.ShadingLightSource.FalloffType;
import game.map.shading.ShadingLightSource.SetLightColor;
import game.map.shading.ShadingLightSource.SetLightCoord;
import game.map.shading.ShadingLightSource.SetLightEnabled;
import game.map.shading.ShadingLightSource.SetLightFalloff;
import game.map.shading.ShadingLightSource.SetLightFalloffType;
import net.miginfocom.swing.MigLayout;
import util.ui.FloatTextField;
import util.ui.HexTextField;
import util.ui.IntTextField;

public class ShadingSourceInfoPanel extends MapInfoPanel<ShadingLightSource>
{
	private final ShadingProfileInfoPanel parent;

	private SwatchPanel colorPreview;
	private HexTextField colorField;

	private IntTextField xPosField;
	private IntTextField yPosField;
	private IntTextField zPosField;

	private FloatTextField falloffField;

	private JCheckBox cbEnabled;
	private JComboBox<FalloffType> falloffBox;

	public ShadingSourceInfoPanel(ShadingProfileInfoPanel parent)
	{
		super(false);
		this.parent = parent;

		colorField = new HexTextField(6, (newValue) -> {
			if (ignoreEvents() || getData() == null)
				return;
			MapEditor.execute(new SetLightColor(getData(), newValue));
		});
		colorField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(colorField);

		JButton chooseColorButton = new JButton("Choose");
		chooseColorButton.addActionListener((e) -> {
			if (getData() == null)
				return;

			SwingGUI.instance().getDialogCounter().increment();
			Color c = new Color(getData().color.get());
			c = JColorChooser.showDialog(null, "Choose Ambient Color", c);
			SwingGUI.instance().getDialogCounter().decrement();

			if (c != null)
				MapEditor.execute(new SetLightColor(getData(), c.getRGB()));
		});

		xPosField = new IntTextField((newValue) -> {
			if (ignoreEvents() || getData() == null)
				return;
			MapEditor.execute(new SetLightCoord(getData(), 0, newValue));
		});
		xPosField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(xPosField);

		yPosField = new IntTextField((newValue) -> {
			if (ignoreEvents() || getData() == null)
				return;
			MapEditor.execute(new SetLightCoord(getData(), 1, newValue));
		});
		yPosField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(yPosField);

		zPosField = new IntTextField((newValue) -> {
			if (ignoreEvents() || getData() == null)
				return;
			MapEditor.execute(new SetLightCoord(getData(), 2, newValue));
		});
		zPosField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(zPosField);

		falloffField = new FloatTextField((newValue) -> {
			if (ignoreEvents() || getData() == null)
				return;

			float value = (newValue == 0.0f) ? 0.0f : 1 / newValue;
			MapEditor.execute(new SetLightFalloff(getData(), value));
		});
		falloffField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(falloffField);

		cbEnabled = new JCheckBox(" Initially enabled");
		cbEnabled.addActionListener((e) -> {
			if (ignoreEvents() || getData() == null)
				return;
			MapEditor.execute(new SetLightEnabled(getData(), cbEnabled.isSelected()));
		});

		falloffBox = new JComboBox<>(FalloffType.values());
		falloffBox.addActionListener((e) -> {
			if (ignoreEvents() || getData() == null)
				return;
			FalloffType newValue = (FalloffType) falloffBox.getSelectedItem();
			MapEditor.execute(new SetLightFalloffType(getData(), newValue));
		});
		SwingUtils.addBorderPadding(falloffBox);

		colorPreview = new SwatchPanel(1.32f, 1.33f);

		setLayout(new MigLayout("ins 0, fill", "[grow][22%][22%][22%]"));

		add(SwingUtils.getLabel("Selected Light Source", 14), "gaptop 8, gapbottom 8, span, wrap");

		add(new JLabel("Light Color"));
		add(colorPreview, "growx, growy");
		add(colorField, "growx");
		add(chooseColorButton, "growx, growy, wrap");

		add(new JLabel("Light Position"));
		add(xPosField, "growx");
		add(yPosField, "growx");
		add(zPosField, "growx, wrap");

		add(new JLabel("Falloff"));
		add(falloffField, "growx");
		add(falloffBox, "growx, wrap");

		add(cbEnabled, "growx, span, wrap, gapleft 8, gaptop 4");
	}

	@Override
	public void updateFields(ShadingLightSource newData, String tag)
	{
		if (newData == null)
			return;

		colorField.setValue(newData.color.get() & 0xFFFFFF);
		colorPreview.setForeground(new Color(newData.color.get()));

		xPosField.setValue(newData.position.getX());
		yPosField.setValue(newData.position.getY());
		zPosField.setValue(newData.position.getZ());

		float falloff = newData.falloff.get();
		float value = (falloff == 0.0f) ? 0.0f : 1 / falloff;
		falloffField.setValue(value);

		falloffBox.setSelectedItem(newData.falloffType.get());
		cbEnabled.setSelected(newData.enabled.get());

		parent.repaintSourceList();
	}
}
