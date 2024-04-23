package game.map.editor.ui;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import game.map.editor.render.TextureManager;
import game.texture.ModelTexture;
import net.miginfocom.swing.MigLayout;

public class TextureInfoPanel extends JPanel
{
	private ModelTexture texture;
	private final JLabel image;
	private final JLabel aux;

	private final JLabel name;
	private final JLabel size;
	private final JLabel count;

	private final JPanel auxPanel;
	private final JLabel combine;
	private final JLabel auxSize;

	public TextureInfoPanel(SwingGUI gui)
	{
		image = new JLabel();
		image.setHorizontalAlignment(SwingConstants.CENTER);

		aux = new JLabel();
		aux.setHorizontalAlignment(SwingConstants.CENTER);

		name = new JLabel();
		size = new JLabel();
		count = new JLabel();

		JButton selectModelsButton = new JButton("Select All Models");
		gui.addButtonCommand(selectModelsButton, GuiCommand.SELECT_ALL_WITH_TEXTURE);

		//	JButton previewButton = new JButton("Preview Options");
		//	gui.addButtonCommand(previewButton, GuiCommand.SHOW_TEXTURE_SCROLL);

		setLayout(new MigLayout("fillx, hidemode 3"));
		add(image, "gapleft 16");

		JPanel generalInfo = new JPanel(new MigLayout("flowy, fill, ins 0"));
		generalInfo.add(name);
		generalInfo.add(size);
		generalInfo.add(count);
		generalInfo.add(new JLabel(), "growy, pushy");
		generalInfo.add(selectModelsButton, "bottom");
		//	generalInfo.add(previewButton, "bottom");
		add(generalInfo, "growy, gapleft 8, gapright 8");

		combine = new JLabel();
		combine.setHorizontalAlignment(SwingConstants.CENTER);

		auxSize = new JLabel();
		auxSize.setHorizontalAlignment(SwingConstants.CENTER);

		auxPanel = new JPanel(new MigLayout("flowy, fill, ins 0"));

		//auxPanel.add(new JLabel("Aux Image:"), "center");
		auxPanel.add(aux, "center");
		auxPanel.add(combine, "center");
		auxPanel.add(auxSize, "center");
		generalInfo.add(new JLabel(), "growy, pushy");
		add(auxPanel, "growy");

		auxPanel.setVisible(false);
	}

	public void setTexture(ModelTexture t)
	{
		texture = t;

		if (t != null) {
			ImageIcon icon = new ImageIcon(TextureManager.background) {
				@Override
				public void paintIcon(Component c, Graphics g, int x, int y)
				{
					g.drawImage(TextureManager.background, x, y, null);
					g.drawImage(t.mainPreview, x, y, null);
				}
			};
			image.setIcon(icon);

			if (t.hasAux()) {
				ImageIcon auxIcon = new ImageIcon(TextureManager.miniBackground) {
					@Override
					public void paintIcon(Component c, Graphics g, int x, int y)
					{
						g.drawImage(TextureManager.miniBackground, x, y, null);
						g.drawImage(t.auxPreview, x, y, 48, 48, null);
					}
				};
				aux.setIcon(auxIcon);
				combine.setText(String.format("Aux Combine: %02X", t.getAuxCombine()));
				auxSize.setText(t.getAuxWidth() + " x " + t.getAuxHeight());
				auxPanel.setVisible(true);
			}
			else {
				auxPanel.setVisible(false);
			}

			name.setText(t.getName());
			setCount(t.modelCount);
			size.setText(t.getWidth() + " x " + t.getHeight());
		}
		else {
			image.setIcon(new ImageIcon(TextureManager.background));
			name.setText("no texture");
			setCount(TextureManager.untexturedCount);
			size.setText(" ");
		}

		repaint();
	}

	public void updateCount(ModelTexture t)
	{
		if (t == texture) {
			if (t != null)
				setCount(t.modelCount);
			else
				setCount(TextureManager.untexturedCount);
		}
	}

	private void setCount(int num)
	{
		if (num > 1)
			count.setText("Used by " + num + " models");
		else if (num == 1)
			count.setText("Used by 1 model");
		else
			count.setText("Unused");
	}
}
