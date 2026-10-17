package game.audio.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EffectBus
{
	public static final float FILTER_SCALE = 16384.0f;
	public static final float COEFF_SCALE = 16384.0f * 2.0f;

	private ChannelBus fxL;
	private ChannelBus fxR;
	private EffectPreset preset;

	public EffectBus()
	{
		this.fxL = new ChannelBus();
		this.fxR = new ChannelBus();
		setPreset(EffectPreset.NONE);
	}

	public void setPreset(EffectPreset preset)
	{
		this.preset = preset;

		fxL.setPreset(preset);
		fxR.setPreset(preset);
	}

	public void renderInto(float[] wetL, float[] wetR)
	{
		if (preset != EffectPreset.NONE) {
			fxL.process(wetL);
			fxR.process(wetR);
		}
	}

	public static class ChannelBus
	{
		private List<EffectTap> taps;
		private float[] delayBuffer;
		private int bufferSize;
		private int bufferPos;

		public ChannelBus()
		{
			this.taps = new ArrayList<>();
			this.bufferSize = 0;
			this.delayBuffer = new float[1]; // dummy buffer until setPreset is called
			this.bufferPos = 0;
		}

		public void setPreset(EffectPreset preset)
		{
			this.bufferSize = preset.length;
			this.delayBuffer = new float[bufferSize];
			this.bufferPos = 0;

			taps.clear();
			for (EffectTap tap : preset.taps) {
				taps.add(new EffectTap(tap));
			}
		}

		public void process(float[] wetSamples)
		{
			// write input into delay buffer
			for (int i = 0; i < AudioEngine.FRAME_SAMPLES; i++) {
				int pos = (bufferPos + i) % bufferSize;
				delayBuffer[pos] = wetSamples[i];
			}

			// clear wet output
			Arrays.fill(wetSamples, 0, AudioEngine.FRAME_SAMPLES, 0.0f);

			for (EffectTap tap : taps) {
				if (tap.resampler != null) {
					// can't access delayBuffer[outputOffset] directly; we need interpolated sample
					int delayOffset = tap.resampler.getSampleCount(AudioEngine.FRAME_SAMPLES);
					int rsStart = bufferPos - (tap.outputOffset - delayOffset);
					int inStart = bufferPos - tap.inputOffset;

					for (int i = 0; i < AudioEngine.FRAME_SAMPLES; i++) {
						int inPos = (bufferSize + inStart + i) % bufferSize;
						float fracIndex = tap.resampler.rsdelta + i;
						float tapOut = getInterpolatedSample(delayBuffer, bufferSize, rsStart + fracIndex);

						// feedforward only adds to tapOut; not saved back to delay line
						if (tap.ffCoef != 0.0f)
							tapOut += delayBuffer[inPos] * tap.ffCoef;

						if (tap.fbCoef != 0.0f)
							delayBuffer[inPos] += tapOut * tap.fbCoef;

						if (tap.lowpass != null)
							tapOut = tap.lowpass.process(tapOut);

						if (tap.gain != 0.0f)
							wetSamples[i] += tapOut * tap.gain;
					}
				}
				else {
					// no resampler
					int outStart = bufferPos - tap.outputOffset;
					int inStart = bufferPos - tap.inputOffset;

					for (int i = 0; i < AudioEngine.FRAME_SAMPLES; i++) {
						int inPos = (bufferSize + inStart + i) % bufferSize;
						int outPos = (bufferSize + outStart + i) % bufferSize;

						if (tap.ffCoef != 0.0f)
							delayBuffer[outPos] += delayBuffer[inPos] * tap.ffCoef;

						if (tap.fbCoef != 0.0f)
							delayBuffer[inPos] += delayBuffer[outPos] * tap.fbCoef;

						float tapOut = delayBuffer[outPos];

						if (tap.lowpass != null)
							tapOut = tap.lowpass.process(tapOut);

						if (tap.gain != 0.0f)
							wetSamples[i] += tapOut * tap.gain;
					}
				}
			}

			// advance circular buffer position
			bufferPos = (bufferPos + AudioEngine.FRAME_SAMPLES) % bufferSize;
		}
	}

	private static float getInterpolatedSample(float[] buffer, int size, float index)
	{
		int i0 = ((int) Math.floor(index)) % size;
		int i1 = (i0 + 1) % size;
		if (i0 < 0)
			i0 += size;
		if (i1 < 0)
			i1 += size;

		// linear interpolation
		float frac = index - (int) Math.floor(index);
		return (1 - frac) * buffer[i0] + frac * buffer[i1];
	}

	// simple 1-pole IIR
	public static class PoleFilter
	{
		private final float fgain;
		private float[] state = new float[2];

		public PoleFilter(int fc)
		{
			float timeConstant = (fc >> 1) / FILTER_SCALE;
			this.fgain = 1.0f - timeConstant;
		}

		public float process(float input)
		{
			float output = (1.0f - fgain) * input + fgain * state[0];
			state[1] = state[0];
			state[0] = output;
			return output;
		}
	}

	// more accurate to the PM code, but crushes the output. something wrong with this.
	/*
	public static class PoleFilter
	{
		private final float fgain;
		private final float[] fccoef = new float[16];
		private float[] state = new float[4]; // past input/output values
		private boolean first = true;

		public PoleFilter(int fc)
		{
			float timeConstant = (fc >> 1) / FILTER_SCALE;
			this.fgain = 1.0f - timeConstant;

			Arrays.fill(fccoef, 0.0f);
			fccoef[8] = timeConstant;

			double attenuation = timeConstant;
			for (int i = 9; i < fccoef.length; i++) {
				attenuation *= timeConstant;
				fccoef[i] = (float) attenuation;
			}

		}

		public float process(float input)
		{
			float output = input * fccoef[8]
				+ state[0] * fccoef[9]
				+ state[1] * fccoef[10];

			output *= fgain;

			state[1] = state[0];
			state[0] = output;

			return output;
		}
	}
	*/

	public static class Resampler
	{
		private static final double CONVERT = 173123.404906676;

		private final float rsinc;
		private final float rsgain;
		private final int delayLength;

		private float rsval;
		private float rsdelta;

		public Resampler(int chorusRate, int chorusDepth, int delayLength)
		{
			this.rsinc = (2.0f * (chorusRate / 1000.0f)) / AudioEngine.OUTPUT_RATE;
			this.rsgain = (float) (chorusDepth / CONVERT) * delayLength;
			this.delayLength = delayLength;

			rsval = 1.0f;
			rsdelta = 0.0f;
		}

		public int getSampleCount(int numSamples)
		{
			// triangle wave modulation between -1 and +1
			rsval += rsinc * numSamples;
			if (rsval > 2.0f)
				rsval -= 4.0f;
			float mod = Math.abs(rsval) - 1.0f;

			float delta = rsgain * mod;
			delta /= delayLength;

			delta = (int) (delta * 0x8000);
			delta /= 0x8000;

			// calculate total (fractional) sample count
			float fincount = rsdelta + (1.0f - delta) * numSamples;
			int count = (int) fincount;
			rsdelta = fincount - count;

			return count;
		}
	}

	/**
	 * Represents a single delay line (or "tap") in an audio effect.
	 */
	public static class EffectTap
	{
		public final int inputOffset; // in frames
		public final int outputOffset; // in frames

		public final float fbCoef; // feedback coefficient (0..0x7FFF)
		public final float ffCoef; // feedforward coefficient (0..0x7FFF)
		public final float gain; // gain applied to this tap (0..0x7FFF)

		public final int chorusRate; // modulation rate (Hz * 1000), 0 for none
		public final int chorusDepth; // modulation depth (in 100ths of cents), 0 for none
		public final int lowpassCoef; // low-pass filter coefficient (0..0x7FFF), 0 for none

		public final PoleFilter lowpass;
		public final Resampler resampler;

		public EffectTap(
			int inputOffset, int outputOffset,
			int fbCoef, int ffCoef, int gain,
			int chorusRate, int chorusDepth,
			int lowpassCoef
		)
		{
			this.inputOffset = inputOffset * AudioEngine.FRAME_SAMPLES;
			this.outputOffset = outputOffset * AudioEngine.FRAME_SAMPLES;
			this.fbCoef = fbCoef / COEFF_SCALE;
			this.ffCoef = ffCoef / COEFF_SCALE;
			this.gain = gain / COEFF_SCALE;
			this.chorusRate = chorusRate;
			this.chorusDepth = chorusDepth;
			this.lowpassCoef = lowpassCoef;
			this.lowpass = (lowpassCoef != 0) ? new PoleFilter(lowpassCoef) : null;

			if (chorusRate != 0 || chorusDepth != 0)
				this.resampler = new Resampler(chorusRate, chorusDepth, outputOffset - inputOffset);
			else
				this.resampler = null;
		}

		public EffectTap(EffectTap other)
		{
			this(
				other.inputOffset / AudioEngine.FRAME_SAMPLES,
				other.outputOffset / AudioEngine.FRAME_SAMPLES,
				(int) (other.fbCoef * COEFF_SCALE),
				(int) (other.ffCoef * COEFF_SCALE),
				(int) (other.gain * COEFF_SCALE),
				other.chorusRate,
				other.chorusDepth,
				other.lowpassCoef
			);
		}
	}

	public static enum EffectPreset
	{
		SMALL_ROOM (new EffectTap[] {
				new EffectTap(0, 9, 9830, -9830, 0, 0, 0, 0),
				new EffectTap(3, 7, 3276, -3276, 0x3FFF, 0, 0, 0),
				new EffectTap(0, 10, 5000, 0, 0, 0, 0, 0x5000),
		}, 11),

		BIG_ROOM (new EffectTap[] {
				new EffectTap(0, 9, 9830, -9830, 0, 0, 0, 0),
				new EffectTap(2, 6, 3276, -3276, 0x3FFF, 0, 0, 0),
				new EffectTap(9, 12, 3276, -3276, 0x3FFF, 0, 0, 0),
				new EffectTap(0, 13, 6000, 0, 0, 0, 0, 0x5000),
		}, 14),

		ECHO (new EffectTap[] {
				new EffectTap(0, 13, 20000, 0, 0x7FFF, 0, 0, 0x7FFF),
		}, 14),

		CHORUS (new EffectTap[] {
				new EffectTap(0, 1, 16384, 0, 0x7FFF, 7600, 700, 0),
		}, 3),

		FLANGE (new EffectTap[] {
				new EffectTap(0, 1, 0, 0x5FFF, 0x7FFF, 380, 500, 0),
		}, 3),

		NONE (new EffectTap[] {}, 0);

		public final EffectTap[] taps;
		public final int length;

		private EffectPreset(EffectTap[] taps, int frames)
		{
			this.taps = taps;
			this.length = frames * AudioEngine.FRAME_SAMPLES;
		}
	}
}
