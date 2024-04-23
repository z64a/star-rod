package game.sprite.editor;

import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.LEFT;

import java.awt.Color;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import game.map.editor.ui.SwatchPanel;
import game.sprite.SpritePalette;
import net.miginfocom.swing.MigLayout;

public class PaletteSlicesRenderer extends JPanel implements ListCellRenderer<SpritePalette>
{
	private JLabel nameLabel;
	private SwatchPanel[] swatches;
	private JPanel swatchesPanel;

	public PaletteSlicesRenderer()
	{
		nameLabel = new JLabel();
		swatches = new SwatchPanel[16];
		nameLabel.setHorizontalAlignment(LEFT);
		nameLabel.setVerticalAlignment(CENTER);
		nameLabel.setVerticalTextPosition(CENTER);

		swatchesPanel = new JPanel(new MigLayout("ins 0", "[fill]2"));
		swatchesPanel.setOpaque(false);
		for (int i = 0; i < swatches.length; i++) {
			swatches[i] = new SwatchPanel(0.5f, 0.5f);
			swatchesPanel.add(swatches[i], "h 16!, w 8!");
		}

		swatchesPanel.add(new JLabel(), "growx, pushx");

		setLayout(new MigLayout("fill, ins 0, hidemode 3"));
		setOpaque(true);

		add(swatchesPanel, "sgy x");
		add(nameLabel, "sgy x, gapleft 8");
		add(new JLabel(), "growx, pushx");
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends SpritePalette> list,
		SpritePalette value,
		int index,
		boolean isSelected,
		boolean cellHasFocus)
	{
		if (isSelected) {
			setBackground(list.getSelectionBackground());
			setForeground(list.getSelectionForeground());
		}
		else {
			setBackground(list.getBackground());
			setForeground(list.getForeground());
		}

		setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

		if (value == null) {
			swatchesPanel.setVisible(false);
			nameLabel.setText("none");
			for (SwatchPanel panel : swatches)
				panel.setForeground(Color.gray);
		}
		else if (!value.hasPal()) {
			swatchesPanel.setVisible(false);
			nameLabel.setText(value.toString() + " (missing)");
			nameLabel.setForeground(Color.red);
			for (SwatchPanel panel : swatches)
				panel.setForeground(Color.gray);
		}
		else {
			swatchesPanel.setVisible(true);

			if (value.disabled) {
				nameLabel.setText(value.toString() + " (disabled)");
				nameLabel.setForeground(Color.blue);
			}
			else {
				nameLabel.setText(value.toString());
				nameLabel.setForeground(null);
			}

			Color[] colors = value.getPal().getColors();
			for (int i = 0; i < swatches.length; i++)
				swatches[i].setForeground(colors[i]);
		}

		return this;
	}
}
