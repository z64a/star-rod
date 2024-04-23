package game.map.editor.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.TexturePaint;

import javax.swing.JPanel;

import game.map.editor.render.TextureManager;

public class SwatchPanel extends JPanel
{
	private TexturePaint paint;

	public SwatchPanel()
	{
		this(1.0f, 1.0f);
	}

	public SwatchPanel(float scaleX, float scaleY)
	{
		this.paint = new TexturePaint(TextureManager.background, new Rectangle(0, 0,
			Math.round(scaleX * TextureManager.background.getWidth()),
			Math.round(scaleY * TextureManager.background.getHeight())));

		//	setOpaque(false);
	}

	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Graphics2D g2 = (Graphics2D) g;
		g2.setPaint(paint);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setPaint(getForeground());
		g2.fillRect(0, 0, getWidth(), getHeight());
	}
}
