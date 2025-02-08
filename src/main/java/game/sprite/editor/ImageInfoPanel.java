package game.sprite.editor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import app.SwingUtils;
import game.map.editor.render.TextureManager;
import game.sprite.ImgRef;
import net.miginfocom.swing.MigLayout;

public class ImageInfoPanel extends JPanel
{
	private static final int IMG_SIZE = 160;

	private ImgRef ref = null;

	private final boolean isBack;
	private final JPanel image;
	private final JLabel label;

	public ImageInfoPanel(boolean isBack)
	{
		this.isBack = isBack;

		image = new JPanel() {
			@Override
			public void paintComponent(Graphics g)
			{
				super.paintComponent(g);

				Graphics2D g2 = (Graphics2D) g;
				g.drawImage(TextureManager.background, 0, 0, IMG_SIZE, IMG_SIZE, null);

				if (ref != null && ref.asset != null) {
					ref.asset.previewImg = new BufferedImage(
						ref.asset.getPalette().getIndexColorModel(),
						ref.asset.previewImg.getRaster(), false, null);

					SwingUtils.centerAndFitImage(ref.asset.previewImg, this, g2);
				}
			}
		};

		label = SwingUtils.getLabel("", 12);

		setLayout(new MigLayout("fill, wrap"));
		add(image, String.format("w %d!, h %d!", IMG_SIZE, IMG_SIZE));
		add(label);
	}

	public void repaintImage()
	{
		image.repaint();
	}

	public void setImageEDT(ImgRef ref)
	{
		assert (SwingUtilities.isEventDispatchThread());

		this.ref = ref;
		image.repaint();

		String text;
		Color color = null;

		if (ref == null) {
			color = SwingUtils.getRedTextColor();
			text = String.format("ERROR");
		}
		else if (!ref.resolved) {
			color = SwingUtils.getRedTextColor();
			text = String.format("(missing)  %s", ref.getName());
		}
		else if (ref.asset == null) {
			if (isBack) {
				text = "none (same as front)";
			}
			else {
				color = SwingUtils.getBlueTextColor();
				text = "(click to bind selected)";
			}
		}
		else {
			text = String.format("(%d x %d)  %s", ref.asset.img.width, ref.asset.img.height, ref.getName());
		}

		label.setForeground(color);
		label.setText(text);
	}
}
