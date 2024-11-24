package util.ui;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;

import javax.swing.UIManager;

import com.formdev.flatlaf.icons.FlatAbstractIcon;

public class CircleIcon extends FlatAbstractIcon
{
	private static CircleIcon instance;

	public static CircleIcon instance()
	{
		if (instance == null)
			instance = new CircleIcon();
		return instance;
	}

	private Shape circle;

	private CircleIcon()
	{
		super(12, 16, UIManager.getColor("Tree.icon.openColor"));

		double centerX = 6;
		double centerY = 8;
		double radius = 4;
		circle = new Ellipse2D.Double(centerX - radius, centerY - radius, 2.0 * radius, 2.0 * radius);
	}

	@Override
	protected void paintIcon(Component c, Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

		g.draw(circle);
	}
}
