package app.splash;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Keeps a readable placeholder while the background renderer is running or unavailable. */
public final class SplashPanel extends JPanel
{

	private BufferedImage image;

	public SplashPanel(int width, int height)
	{
		this(width, height, "STAR ROD");
	}

	public SplashPanel(int width, int height, String message)
	{
		super(new BorderLayout());
		setPreferredSize(new Dimension(width, height));
		JLabel placeholder = new JLabel(message, SwingConstants.CENTER);
		placeholder.setFont(placeholder.getFont().deriveFont(Font.BOLD, 40f));
		add(placeholder);
	}

	public void setImage(BufferedImage image)
	{
		this.image = image;
		removeAll();
		revalidate();
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (image == null)
			return;
		Graphics2D scaled = (Graphics2D) g.create();
		scaled.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		double scale = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
		int width = (int) Math.round(image.getWidth() * scale);
		int height = (int) Math.round(image.getHeight() * scale);
		scaled.drawImage(image, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, null);
		scaled.dispose();
	}
}
