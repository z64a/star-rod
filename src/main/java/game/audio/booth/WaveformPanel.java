package game.audio.booth;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JPanel;

import game.audio.engine.AudioEngine;

public class WaveformPanel extends JPanel implements AudioEngine.AudioMonitor
{
	private final float[] sampleBuffer;
	private int writePos;
	private int sampleCount;

	private volatile float[] displaySamples = new float[0];

	public WaveformPanel(int capacity)
	{
		if (capacity < 1)
			throw new IllegalArgumentException("Waveform capacity must be positive");
		sampleBuffer = new float[capacity];
	}

	@Override
	public synchronized void accept(float[] left, float[] right, int start, int end)
	{
		for (int i = start; i < end; i++) {
			sampleBuffer[writePos] = (left[i] + right[i]) * 0.5f;
			writePos = (writePos + 1) % sampleBuffer.length;
			if (sampleCount < sampleBuffer.length)
				sampleCount++;
		}
	}

	public synchronized void refresh()
	{
		float[] samples = new float[sampleCount];
		int readPos = (writePos - sampleCount + sampleBuffer.length) % sampleBuffer.length;
		for (int i = 0; i < sampleCount; i++)
			samples[i] = sampleBuffer[(readPos + i) % sampleBuffer.length];
		displaySamples = samples;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		int width = getWidth();
		int height = getHeight();
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, width, height);

		float[] samples = displaySamples;
		if (samples.length == 0 || width == 0 || height == 0)
			return;

		g.setColor(Color.GREEN);
		int middleY = height / 2;
		for (int x = 0; x < width; x++) {
			int start = x * samples.length / width;
			int end = Math.max(start + 1, (x + 1) * samples.length / width);
			float min = 0.0f;
			float max = 0.0f;
			for (int i = start; i < end && i < samples.length; i++) {
				min = Math.min(min, samples[i]);
				max = Math.max(max, samples[i]);
			}
			min = Math.max(-1.0f, min);
			max = Math.min(1.0f, max);
			int topY = middleY - (int) (max * middleY);
			int bottomY = middleY - (int) (min * middleY);
			g.drawLine(x, topY, x, bottomY);
		}
	}
}
