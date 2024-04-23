package game.map.editor.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import game.map.editor.MapEditor;
import game.map.editor.render.TextureManager;
import game.texture.ModelTexture;
import net.miginfocom.swing.MigLayout;

public class TexturePreview extends JPanel
{
	private ModelTexture texture;

	public TexturePreview(final MapEditor editor, final ModelTexture tex)
	{
		texture = tex;
		JLabel textLabel = null;
		JButton button = new JButton();
		button.setContentAreaFilled(false);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setPreferredSize(new Dimension(96, 96));
		button.addActionListener((e) -> {
			editor.doNextFrame(() -> {
				editor.setSelectedTexture(texture);
			});
		});

		if (tex != null) {
			ImageIcon icon = new ImageIcon(TextureManager.background) {
				@Override
				public void paintIcon(Component c, Graphics g, int x, int y)
				{
					g.drawImage(TextureManager.background, x, y, null);
					g.drawImage(tex.mainPreview, x, y, null);

					if (tex.hasAux()) {
						int w = TextureManager.background.getWidth();
						int h = TextureManager.background.getHeight();
						int dx1 = x + w / 2;
						int dx2 = x + w;
						int dy1 = y + h / 2;
						int dy2 = y + h;

						g.setColor(Color.RED);
						g.fillRect(dx1 - 4, dy1 - 4, dx2 - dx1 + 4, dy2 - dy1 + 4);
						g.drawImage(tex.auxPreview,
							dx1 - 2, dy1 - 2, dx2 - 2, dy2 - 2,
							0, 0, tex.auxPreview.getWidth(), tex.auxPreview.getHeight(), null);
					}
				}
			};
			button.setIcon(icon);
			textLabel = new JLabel(tex.getName());
		}
		else {
			button.setIcon(new ImageIcon(TextureManager.background));
			textLabel = new JLabel("no texture");
		}

		textLabel.setHorizontalAlignment(SwingConstants.CENTER);
		setLayout(new MigLayout("wrap, fill"));
		add(button);
		add(textLabel);
	}
}
