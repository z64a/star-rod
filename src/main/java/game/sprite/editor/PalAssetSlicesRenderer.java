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
import game.sprite.PalAsset;
import net.miginfocom.swing.MigLayout;

public class PalAssetSlicesRenderer extends JPanel implements ListCellRenderer<PalAsset>
{
	private JLabel nameLabel;
	private SwatchPanel[] swatches;
	private JPanel swatchesPanel;

	public PalAssetSlicesRenderer()
	{
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
		JList<? extends PalAsset> list,
		PalAsset asset,
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
		if (asset != null) {
			nameLabel.setText(asset.toString());
			nameLabel.setForeground(null);

			Color[] colors = asset.pal.getColors();
			for (int i = 0; i < swatches.length; i++)
				swatches[i].setForeground(colors[i]);

			swatchesPanel.setVisible(true);
		}
		else {
			nameLabel.setText("NULL");
			swatchesPanel.setVisible(false);
		}

		return this;
	}
}
