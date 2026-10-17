package game.audio.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class AudioEngine
{
	public interface PcmOutput
	{
		public void write(byte[] data, int offset, int length);
	}

	public interface AudioMonitor
	{
		// samples are taken after mixing and effects, but before master volume is applied
		// buffers are reused by the engine; samples must be consumed or copied before this method returns
		public void accept(float[] left, float[] right, int start, int end);
	}

	public static final int TARGET_FPS = 60; // PM audio thread
	public static final double FRAME_TIME = 1.0 / TARGET_FPS;

	public static final int OUTPUT_RATE = 32000;
	public static final int MAX_MASTER_VOLUME = 256;
	public static final int FRAME_SAMPLES = 184;
	// Rounds up to exactly one mixer block in renderFrame().
	public static final double MIXER_BLOCK_TIME = (FRAME_SAMPLES - 0.5) / OUTPUT_RATE;
	public static final int NUM_EFFECT_BUSES = 2;

	private static final int NUM_CHANNELS = 2; // stereo

	private static final int BYTES_PER_SAMPLE = NUM_CHANNELS * Short.BYTES;

	private int overflowSamples = 0;

	private final SourceDataLine line;
	private final PcmOutput output;
	private volatile AudioMonitor audioMonitor;
	private final EffectBus[] effectBuses;
	private final List<Voice> voices;

	private final List<AudioClient> clients;

	private float[][] dryBufferL;
	private float[][] dryBufferR;

	private float[][] wetBufferL;
	private float[][] wetBufferR;
	private StereoDelay[] stereoDelays;

	private float[] mixedBufferL;
	private float[] mixedBufferR;

	private byte[] outBuffer;

	private int masterVolume = MAX_MASTER_VOLUME;

	/** An engine that plays through the default audio device. */
	public AudioEngine()
	{
		this(true, null);
	}

	/** An engine with no audio device that hands the mixed 16-bit stereo samples to an output. */
	public AudioEngine(PcmOutput output)
	{
		this(false, output);
	}

	private AudioEngine(boolean enableOutput, PcmOutput output)
	{
		effectBuses = new EffectBus[NUM_EFFECT_BUSES];
		for (int i = 0; i < effectBuses.length; i++)
			effectBuses[i] = new EffectBus();
		voices = new ArrayList<>();
		clients = new ArrayList<>();

		if (enableOutput) {
			AudioFormat format = new AudioFormat(OUTPUT_RATE, Short.SIZE, NUM_CHANNELS, true, false);
			try {
				line = AudioSystem.getSourceDataLine(format);
				line.open(format);
				line.start();
			}
			catch (LineUnavailableException e) {
				throw new IllegalStateException("could not open the audio device: " + e.getMessage(), e);
			}
		}
		else {
			line = null;
		}
		if (line != null) {
			this.output = (data, offset, length) -> line.write(data, offset, length);
		}
		else {
			this.output = output;
		}

		dryBufferL = new float[NUM_EFFECT_BUSES][FRAME_SAMPLES];
		dryBufferR = new float[NUM_EFFECT_BUSES][FRAME_SAMPLES];

		wetBufferL = new float[NUM_EFFECT_BUSES][FRAME_SAMPLES];
		wetBufferR = new float[NUM_EFFECT_BUSES][FRAME_SAMPLES];
		stereoDelays = new StereoDelay[NUM_EFFECT_BUSES];
		for (int i = 0; i < stereoDelays.length; i++)
			stereoDelays[i] = new StereoDelay();

		mixedBufferL = new float[FRAME_SAMPLES];
		mixedBufferR = new float[FRAME_SAMPLES];

		outBuffer = new byte[FRAME_SAMPLES * BYTES_PER_SAMPLE];
	}

	public void flush()
	{
		if (line != null)
			line.flush();
		resetRenderState();
	}

	public void resetRenderState()
	{
		overflowSamples = 0;
	}

	public void shutdown()
	{
		if (line != null) {
			line.drain();
			line.stop();
			line.close();
		}
	}

	public void addClient(AudioClient client)
	{
		if (!clients.contains(client))
			clients.add(client);
	}

	public void removeClient(AudioClient client)
	{
		clients.remove(client);
	}

	public void addVoice(Voice voice)
	{
		voices.add(voice);
	}

	public void setAudioMonitor(AudioMonitor audioMonitor)
	{
		this.audioMonitor = audioMonitor;
	}

	public void setMasterVolume(int volume)
	{
		masterVolume = volume;
	}

	public int getMasterVolume()
	{
		return masterVolume;
	}

	public boolean hasActiveVoices()
	{
		for (Voice voice : voices) {
			if (!voice.isDone())
				return true;
		}
		return false;
	}

	public void setEffectPreset(int bus, EffectBus.EffectPreset preset)
	{
		if (bus < 0 || bus >= effectBuses.length)
			return;
		effectBuses[bus].setPreset(preset);
	}

	public void setStereoDelay(int bus, int side, int length)
	{
		if (bus < 0 || bus >= stereoDelays.length)
			return;
		stereoDelays[bus].set(side, length);
	}

	public void resetEffects()
	{
		for (int i = 0; i < effectBuses.length; i++) {
			effectBuses[i].setPreset(EffectBus.EffectPreset.NONE);
			stereoDelays[i].set(0, 0);
		}
	}

	private static final byte[] DUMMY_FRAME = new byte[BYTES_PER_SAMPLE * FRAME_SAMPLES];

	public void padLine(double time)
	{
		if (line == null)
			return;
		int reqFrames = (int) Math.ceil((time * OUTPUT_RATE) / FRAME_SAMPLES);

		for (int i = 0; i < reqFrames; i++)
			line.write(DUMMY_FRAME, 0, BYTES_PER_SAMPLE * FRAME_SAMPLES);
	}

	public void renderFrame(double deltaTime, boolean fastForward)
	{
		float masterVolumeRatio = (float) (masterVolume / (double) MAX_MASTER_VOLUME);

		int totalSamples = (int) Math.ceil(deltaTime * OUTPUT_RATE);
		int processed = 0;

		if (overflowSamples > 0) {
			int startPos = FRAME_SAMPLES - overflowSamples; // where the last frame left off and overflow began
			if (!fastForward) {
				monitorSamples(mixedBufferL, mixedBufferR, startPos, FRAME_SAMPLES);
				if (output != null)
					writeSamples(output, mixedBufferL, mixedBufferR, outBuffer, startPos, FRAME_SAMPLES, masterVolumeRatio);
			}
			processed += overflowSamples;
			overflowSamples = 0;
		}

		// how many bytes are currently sitting in the data line
		int lineBytesQueued = 0;
		if (line != null)
			lineBytesQueued = line.getBufferSize() - line.available();

		if (!fastForward && lineBytesQueued >= 2 * totalSamples * BYTES_PER_SAMPLE) {
			// buffer already contains two frames of data, skip rendering
			return;
		}

		// remove any voices which have finished playing
		voices.removeIf(voice -> voice.isDone());

		while (processed < totalSamples) {
			for (AudioClient client : clients) {
				client.nextFrame(fastForward);
			}

			// clear buffers
			for (int i = 0; i < NUM_EFFECT_BUSES; i++) {
				Arrays.fill(dryBufferL[i], 0, FRAME_SAMPLES, 0.0f);
				Arrays.fill(dryBufferR[i], 0, FRAME_SAMPLES, 0.0f);
				Arrays.fill(wetBufferL[i], 0, FRAME_SAMPLES, 0.0f);
				Arrays.fill(wetBufferR[i], 0, FRAME_SAMPLES, 0.0f);
			}

			// mix voices
			for (Voice v : voices) {
				int bus = v.getEffectBus();
				v.renderInto(dryBufferL[bus], dryBufferR[bus], wetBufferL[bus], wetBufferR[bus]);
			}

			// process effects
			for (int i = 0; i < NUM_EFFECT_BUSES; i++) {
				effectBuses[i].renderInto(wetBufferL[i], wetBufferR[i]);
				for (int j = 0; j < FRAME_SAMPLES; j++) {
					dryBufferL[i][j] += wetBufferL[i][j];
					dryBufferR[i][j] += wetBufferR[i][j];
				}
				stereoDelays[i].process(dryBufferL[i], dryBufferR[i]);
			}

			// final mixdown for output samples
			for (int i = 0; i < FRAME_SAMPLES; i++) {
				mixedBufferL[i] = 0.0f;
				mixedBufferR[i] = 0.0f;
				for (int j = 0; j < NUM_EFFECT_BUSES; j++) {
					mixedBufferL[i] += dryBufferL[j][i];
					mixedBufferR[i] += dryBufferR[j][i];
				}
			}

			processed += FRAME_SAMPLES;

			int writeSamples;

			if (processed > totalSamples) {
				overflowSamples = processed - totalSamples;
				writeSamples = FRAME_SAMPLES - overflowSamples;
			}
			else {
				overflowSamples = 0;
				writeSamples = FRAME_SAMPLES;
			}

			if (!fastForward) {
				monitorSamples(mixedBufferL, mixedBufferR, 0, writeSamples);
				if (output != null)
					writeSamples(output, mixedBufferL, mixedBufferR, outBuffer, 0, writeSamples, masterVolumeRatio);
			}
		}
	}

	public void prepareForSeek()
	{
		padLine(0.050);
	}

	public void finishSeek()
	{
		flush();
		padLine(0.020);
	}

	private void monitorSamples(float[] left, float[] right, int start, int end)
	{
		AudioMonitor monitor = audioMonitor;
		if (monitor != null)
			monitor.accept(left, right, start, end);
	}

	private static void writeSamples(PcmOutput output, float[] mixedL, float[] mixedR, byte[] outBuffer, int start, int end, float gain)
	{
		int sampleCount = (end - start);

		for (int i = 0; i < sampleCount; i++) {
			short left = floatToPCM(mixedL[start + i] * gain);
			short right = floatToPCM(mixedR[start + i] * gain);
			outBuffer[4 * i + 0] = (byte) left;
			outBuffer[4 * i + 1] = (byte) (left >> 8);
			outBuffer[4 * i + 2] = (byte) right;
			outBuffer[4 * i + 3] = (byte) (right >> 8);
		}

		output.write(outBuffer, 0, sampleCount * BYTES_PER_SAMPLE);
	}

	// clamp and convert to PCM
	private static short floatToPCM(float sample)
	{
		sample = Math.max(-1.0f, Math.min(1.0f, sample)); // hard clip
		return (short) (sample * Short.MAX_VALUE);
	}

	public static float detuneToPitchRatio(int detune)
	{
		detune = Math.max(-0x3FFF, Math.min(0xFFF, detune));
		return (float) Math.pow(2, detune / 1200.0); // 1200 = CENTS_PER_OCTAVE
	}

	private static final class StereoDelay
	{
		private final float[] buffer = new float[4 * FRAME_SAMPLES];
		private int side;
		private int length;
		private int pos;
		private int requestedSide;
		private int requestedLength;

		private void set(int side, int length)
		{
			if (side == requestedSide && length == requestedLength)
				return;

			requestedSide = side;
			requestedLength = length;
			if (side < 1 || side > 2 || length < 2) {
				this.side = 0;
				this.length = 0;
			}
			else {
				this.side = side;
				this.length = Math.min(4, length);
			}
			pos = 0;
			Arrays.fill(buffer, 0.0f);
		}

		private void process(float[] left, float[] right)
		{
			if (side == 0)
				return;

			float[] channel = side == 1 ? left : right;
			int delaySamples = (length - 1) * FRAME_SAMPLES;
			for (int i = 0; i < FRAME_SAMPLES; i++) {
				float sample = channel[i];
				channel[i] = buffer[pos];
				buffer[pos] = sample;
				pos++;
				if (pos == delaySamples)
					pos = 0;
			}
		}
	}
}
