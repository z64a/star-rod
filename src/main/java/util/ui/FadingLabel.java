package util.ui;

import java.awt.Color;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JLabel;
import javax.swing.UIManager;

public class FadingLabel extends JLabel
{
	private Color initialColor = UIManager.getColor("Label.foreground");
	private final double holdTime;
	private final double fadeTime;
	private double elapsedTime = 0;
	private boolean fadeComplete = true;

	public FadingLabel(boolean useTimer, double holdTime, double fadeTime)
	{
		super("Ready");
		setForeground(getBackground());
		this.holdTime = holdTime;
		this.fadeTime = fadeTime;

		if (useTimer)
			startTimer();
	}

	public FadingLabel(boolean useTimer, int horizontalAlignment, float holdTime, float fadeTime)
	{
		super("Ready", horizontalAlignment);
		setForeground(getBackground());
		this.holdTime = holdTime;
		this.fadeTime = fadeTime;

		if (useTimer)
			startTimer();
	}

	private void startTimer()
	{
		long dt = 1000 / 30;
		Timer scheduler = new Timer(true);
		scheduler.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run()
			{
				update(dt * 0.001);
			}
		}, dt, dt);
	}

	public void setMessage(String msg, Color c)
	{
		setText(msg);
		setForeground(c);
		initialColor = c;
		elapsedTime = 0;
		fadeComplete = false;
	}

	public void update(double deltaTime)
	{
		if (fadeComplete)
			return;

		if (elapsedTime >= holdTime) {
			double alpha = (elapsedTime - holdTime) / fadeTime;
			alpha = Math.min(alpha, 1.0);

			Color bg = getBackground();
			Color in = initialColor;
			int r = (int) (alpha * bg.getRed() + (1 - alpha) * in.getRed());
			int g = (int) (alpha * bg.getGreen() + (1 - alpha) * in.getGreen());
			int b = (int) (alpha * bg.getBlue() + (1 - alpha) * in.getBlue());
			setForeground(new Color(r, g, b));

			if (alpha == 1.0) {
				fadeComplete = true;
				setText("");
			}
		}

		elapsedTime += deltaTime;
	}
}
