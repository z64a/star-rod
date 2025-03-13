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

import app.SwingUtils;
import game.map.editor.ui.SwatchPanel;
import game.sprite.SpritePalette;
import net.miginfocom.swing.MigLayout;

public class PaletteCellRenderer extends JPanel implements ListCellRenderer<SpritePalette>
{
	private JLabel nameLabel;
	private SwatchPanel[] swatches;
	private JPanel swatchesPanel;
	private String nullString;

	public PaletteCellRenderer()
	{
		this("none");
	}

	public PaletteCellRenderer(String nullString)
	{
		this.nullString = nullString;

		nameLabel = new JLabel();
		swatches = new SwatchPanel[16];
		nameLabel.setHorizontalAlignment(LEFT);
		nameLabel.setVerticalAlignment(CENTER);
		nameLabel.setVerticalTextPosition(CENTER);

		swatchesPanel = new JPanel(new MigLayout("ins 0", "[fill]0"));
		swatchesPanel.setOpaque(false);
		for (int i = 0; i < swatches.length; i++) {
			swatches[i] = new SwatchPanel(0.5f, 0.5f);
			swatchesPanel.add(swatches[i], "h 16!, w 6!");
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
		SpritePalette pal,
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

		setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 0));
		if (pal == null) {
			swatchesPanel.setVisible(false);
			nameLabel.setText(nullString);

			for (int i = 0; i < swatches.length; i++)
				swatches[i].setForeground(Color.gray);
		}
		else if (!pal.hasPal()) {
			swatchesPanel.setVisible(false);
			nameLabel.setText(pal.name + " (missing)");
			nameLabel.setForeground(SwingUtils.getRedTextColor());

			for (int i = 0; i < swatches.length; i++)
				swatches[i].setForeground(Color.gray);
		}
		else {
			swatchesPanel.setVisible(true);

			if (pal.isModified())
				nameLabel.setText(pal.name + " *");
			else
				nameLabel.setText(pal.name);

			if (pal.hasError())
				nameLabel.setForeground(SwingUtils.getRedTextColor());
			else
				nameLabel.setForeground(null);

			Color[] colors = pal.getPal().getColors();
			for (int i = 0; i < swatches.length; i++)
				swatches[i].setForeground(colors[i]);
		}

		return this;
	}
}
