package game.map.editor.ui.info.marker;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.editor.ui.SwatchPanel;
import game.map.editor.ui.SwingGUI;
import net.miginfocom.swing.MigLayout;
import util.ui.HexTextField;

public class ColorChoicePanel extends JPanel
{
	private SwatchPanel preview;
	private HexTextField field;
	private JButton chooseButton;

	public ColorChoicePanel(Supplier<Integer> getter, Consumer<Integer> setter)
	{
		preview = new SwatchPanel(1.32f, 1.33f);

		field = new HexTextField(6, (newValue) -> {
			setter.accept(newValue);
		});
		field.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(field);

		chooseButton = new JButton("Choose");
		chooseButton.addActionListener((e) -> {
			Color oldColor = new Color(getter.get());
			Color newColor = SwingGUI.instance().prompt_ChooseColor("Choose Ambient Color", oldColor);
			if (newColor != null)
				setter.accept(newColor.getRGB() & 0xFFFFFF);
		});
		SwingUtils.addBorderPadding(chooseButton);

		setLayout(new MigLayout("fill, ins 0", "[sg col][sg col][sg col]"));

		add(preview, "growx, growy");
		add(field, "growx");
		add(chooseButton, "growx");
	}

	public void setValue(int rgb)
	{
		field.setValue(rgb);
		preview.setForeground(new Color(rgb));
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		field.setEnabled(enabled);
		chooseButton.setEnabled(enabled);
	}
}
