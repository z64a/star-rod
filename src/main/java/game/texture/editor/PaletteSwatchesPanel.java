package game.texture.editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;

import game.map.editor.render.TextureManager;

public class PaletteSwatchesPanel extends JPanel
{
	private final TexturePaint bgPaint;

	private Color[] palette;

	private final int num;
	private final int nRow;
	private final int nCol;

	private final int outerPad = 1;
	private final int W = 30;
	private final int H = 15;

	private final Color highlightColor = new Color(0, 255, 255, 255);
	private final Color selectColor = new Color(255, 255, 0, 255);
	private final Color bothColor = selectColor; //new Color(255, 255, 255, 255);

	boolean highlight = false;
	int highlightCol;
	int highlightRow;

	boolean selected = false;
	int selectedCol;
	int selectedRow;

	public void setPalette(Color[] newPalette)
	{
		assert (newPalette != null);
		assert (newPalette.length == num) : newPalette.length + " != " + num;
		palette = newPalette;
	}

	public Color getPaletteColor(int index)
	{
		return palette[index];
	}

	public void setPaletteColor(int index, Color c)
	{
		palette[index] = c;
	}

	public void setSelectedIndex(int index)
	{
		assert (index < num && index >= 0);
		if (index < 0)
			index = 0;
		if (index >= num)
			index = num - 1;

		selected = true;
		selectedRow = index / nCol;
		selectedCol = index % nCol;

		repaint();
	}

	public PaletteSwatchesPanel(ImageEditor editor, int num, int nCol)
	{
		this(editor, num, nCol, 1f);
	}

	public PaletteSwatchesPanel(ImageEditor editor, int num, int nCol, float scale)
	{
		this.bgPaint = new TexturePaint(TextureManager.background, new Rectangle(0, 0,
			Math.round(scale * TextureManager.background.getWidth()),
			Math.round(scale * TextureManager.background.getHeight())));

		this.num = num;
		this.nCol = nCol;
		nRow = num / nCol;

		Dimension size = new Dimension(W * nCol + 2 * outerPad, H * nRow + 2 * outerPad);
		setMinimumSize(size);
		setPreferredSize(size);

		//setOpaque(false);

		final MouseAdapter mouseAdapter = new MouseAdapter() {
			private Color background;

			@Override
			public void mouseEntered(MouseEvent e)
			{
				highlight = true;
				updateMousePick(e.getX(), e.getY());
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				highlight = false;
				repaint();
			}

			@Override
			public void mouseMoved(MouseEvent e)
			{
				updateMousePick(e.getX(), e.getY());
				repaint();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				updateMousePick(e.getX(), e.getY());
				if (highlight) {
					if (highlightRow * nCol + highlightCol >= palette.length)
						return;

					selected = true;
					selectedCol = highlightCol;
					selectedRow = highlightRow;

					int index = selectedRow * nCol + selectedCol;

					editor.invokeLater(() -> {
						if (e.isShiftDown())
							editor.selectByColor(index);
						else
							editor.setSelectedIndex(index, true);
					});

					repaint();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				setBackground(background);
			}

			private void updateMousePick(int X, int Y)
			{
				if (highlight) {
					highlightCol = X / W;
					highlightRow = Y / H;
				}
			}
		};

		addMouseListener(mouseAdapter);
		addMouseMotionListener(mouseAdapter);
	}

	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;

		int k = 0;
		for (int i = 0; i < nRow; i++)
			for (int j = 0; j < nCol; j++) {
				g2.setPaint(bgPaint);
				g2.fillRect(outerPad + (j * W), outerPad + (i * H), W, H);
				g2.setPaint(palette[k++]);
				g2.fillRect(outerPad + (j * W), outerPad + (i * H), W, H);
			}

		boolean same = (highlightCol == selectedCol) && (highlightRow == selectedRow);

		if (selected) {
			Stroke prevStroke = g2.getStroke();
			g2.setStroke(new BasicStroke(2.0f));
			g2.setPaint((highlight && same) ? bothColor : selectColor);
			g2.drawRect(outerPad + (selectedCol * W), outerPad + (selectedRow * H), W, H);
			g2.setStroke(prevStroke);
		}

		if (highlight && (!selected || !same)) {
			Stroke prevStroke = g2.getStroke();
			g2.setStroke(new BasicStroke(2.0f));
			g2.setPaint(highlightColor);
			g2.drawRect(outerPad + (highlightCol * W), outerPad + (highlightRow * H), W, H);
			g2.setStroke(prevStroke);
		}
	}
}
