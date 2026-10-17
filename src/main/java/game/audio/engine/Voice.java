package game.audio.engine;

import game.audio.engine.EnvelopePlayer.Script;
import util.Logger;

public class Voice
{
	public enum VoiceState
	{
		INIT, // does not have an instrument assigned yet, nothing to play
		READY, // has an instrument, but not yet playing
		PLAYING, // can be paused
		PAUSED, // can resume playing
		DONE, // nothing left to play
	}

	private EnvelopePlayer envPlayer;
	private VoiceState state;

	private Instrument ins;
	private Script env;
	private float readPos;
	private float volume;
	private float pitch;
	private int pan;
	private int reverb;
	private int effectBus;

	private boolean allowLooping;
	private float playbackGain;
	private float playbackTarget;
	private VoiceState fadeEndState;
	private int loopIterations;
	private int renderedSamples;

	public Voice()
	{
		envPlayer = new EnvelopePlayer();
		readPos = 0.0f;
		volume = 1.0f;
		pitch = 1.0f;
		pan = 64;
		effectBus = 0;
		allowLooping = true;
		playbackGain = 1.0f;
		playbackTarget = 1.0f;

		state = VoiceState.INIT;
	}

	public void setInstrument(Instrument ins)
	{
		this.ins = ins;

		state = VoiceState.READY;
		playbackGain = 1.0f;
		playbackTarget = 1.0f;
		fadeEndState = null;
		loopIterations = 0;
		renderedSamples = 0;
	}

	public void setEnvelope(Script env)
	{
		this.env = env;
	}

	public void setPitch(float pitch)
	{
		this.pitch = pitch;
	}

	public void setVolume(float volume)
	{
		this.volume = volume;
	}

	public void setPan(int pan)
	{
		if (pan < 0 || pan > 127) {
			Logger.logWarning("Invalid pan value: " + pan);
			pan = Math.max(0, Math.min(127, pan));
		}
		this.pan = pan;
	}

	public void setReverb(int reverb)
	{
		if (reverb < 0 || reverb > 127) {
			Logger.logWarning("Invalid reverb value: " + reverb);
			reverb = Math.max(0, Math.min(127, reverb));
		}
		this.reverb = reverb;
	}

	public void setEffectBus(int effectBus)
	{
		if (effectBus < 0 || effectBus >= AudioEngine.NUM_EFFECT_BUSES) {
			Logger.logWarning("Invalid effect bus: " + effectBus);
			effectBus = Math.max(0, Math.min(AudioEngine.NUM_EFFECT_BUSES - 1, effectBus));
		}
		this.effectBus = effectBus;
	}

	int getEffectBus()
	{
		return effectBus;
	}

	public void setLoopingAllowed(boolean enabled)
	{
		allowLooping = enabled;
	}

	int getRenderedSamples()
	{
		return renderedSamples;
	}

	int getOutputPos()
	{
		return getOutputPos(ins, pitch, readPos);
	}

	static int getOutputDuration(Instrument instrument, float pitch)
	{
		if (instrument == null || instrument.samples.length < 2)
			return 0;
		return (int) Math.ceil((instrument.samples.length - 1) / getResampleRatio(instrument, pitch));
	}

	private static int getOutputPos(Instrument instrument, float pitch, float readPos)
	{
		if (instrument == null)
			return 0;
		return (int) Math.floor(readPos / getResampleRatio(instrument, pitch));
	}

	public void play()
	{
		if (state == VoiceState.READY) {
			state = VoiceState.PLAYING;

			if (env != null)
				envPlayer.press(env);
		}
	}

	public void setPaused(boolean paused)
	{
		if (state == VoiceState.PLAYING && paused) {
			state = VoiceState.PAUSED;
		}
		else if (state == VoiceState.PAUSED && !paused) {
			state = VoiceState.PLAYING;
		}
	}

	public void release()
	{
		if (env != null)
			envPlayer.release(env);
		else
			state = VoiceState.DONE;
	}

	public void kill()
	{
		fadeEndState = null;
		state = VoiceState.DONE;
	}

	void fadeOut()
	{
		fadeTo(VoiceState.DONE);
	}

	void fadeToPause()
	{
		fadeTo(VoiceState.PAUSED);
	}

	void fadeFromPause()
	{
		if (state == VoiceState.PAUSED) {
			state = VoiceState.PLAYING;
			playbackGain = 0.0f;
		}
		if (state == VoiceState.PLAYING) {
			playbackTarget = 1.0f;
			fadeEndState = null;
		}
	}

	private void fadeTo(VoiceState endState)
	{
		if (state == VoiceState.PLAYING) {
			playbackTarget = 0.0f;
			fadeEndState = endState;
		}
		else if (endState == VoiceState.DONE) {
			kill();
		}
	}

	public boolean isDone()
	{
		return state == VoiceState.DONE;
	}

	public void renderInto(float[] dryBufferL, float[] dryBufferR, float[] wetBufferL, float[] wetBufferR)
	{
		if (state != VoiceState.PLAYING)
			return;

		if (ins == null)
			return;

		float envVolumeStart = 1.0f;
		float envVolumeEnd = 1.0f;
		boolean envelopeDone = false;

		if (env != null) {
			envVolumeStart = envPlayer.getEnvelopeVolume();
			envPlayer.update();
			envVolumeEnd = envPlayer.getEnvelopeVolume();
			envelopeDone = envPlayer.isDone();
		}

		float panAngle = (float) ((pan / 127.0) * (Math.PI / 2));
		float panL = (float) Math.cos(panAngle);
		float panR = (float) Math.sin(panAngle);

		float dryAngle = (float) ((reverb / 127.0) * (Math.PI / 2));
		float dryAmt = (float) Math.cos(dryAngle);
		float wetAmt = (float) Math.sin(dryAngle);

		float resampleRatio = getResampleRatio();
		float voiceVolumeStart = volume * envVolumeStart * playbackGain;
		float voiceVolumeEnd = volume * envVolumeEnd * playbackTarget;
		float gainStart = voiceVolumeStart * voiceVolumeStart;
		float gainEnd = voiceVolumeEnd * voiceVolumeEnd;

		for (int i = 0; i < AudioEngine.FRAME_SAMPLES; i++) {
			while (canLoop() && readPos >= ins.loopEnd) {
				if (ins.loopCount != Instrument.LOOP_FOREVER)
					loopIterations++;
				readPos = ins.loopStart + (readPos - ins.loopEnd);
			}

			int i0 = (int) readPos;
			int i1 = i0 + 1;

			if (canLoop() && i1 >= ins.loopEnd)
				i1 = ins.loopStart + (i1 - ins.loopEnd);

			if (i1 >= ins.samples.length) {
				// reached end of non-looping sample
				state = VoiceState.DONE;
				return;
			}

			// n64 microcode uses linear resampling, so that's what we'll use
			float frac = readPos - i0;
			float s0 = (float) ins.samples[i0] / Short.MAX_VALUE;
			float s1 = (float) ins.samples[i1] / Short.MAX_VALUE;
			float sample = (1 - frac) * s0 + frac * s1;

			float envelopeTime = (i + 1.0f) / AudioEngine.FRAME_SAMPLES;
			float gain = gainStart + envelopeTime * (gainEnd - gainStart);
			float scaled = sample * gain;

			dryBufferL[i] += scaled * panL * dryAmt;
			dryBufferR[i] += scaled * panR * dryAmt;

			wetBufferL[i] += scaled * panL * wetAmt;
			wetBufferR[i] += scaled * panR * wetAmt;

			readPos += resampleRatio;
			if (renderedSamples < Integer.MAX_VALUE)
				renderedSamples++;
		}

		playbackGain = playbackTarget;
		if (envelopeDone) {
			state = VoiceState.DONE;
		}
		else if (fadeEndState != null) {
			state = fadeEndState;
			fadeEndState = null;
		}
	}

	private float getResampleRatio()
	{
		return getResampleRatio(ins, pitch);
	}

	private static float getResampleRatio(Instrument instrument, float pitch)
	{
		float ratio = pitch * ((float) instrument.sampleRate / AudioEngine.OUTPUT_RATE);
		return Math.min(ratio, 1.99996f);
	}

	private boolean canLoop()
	{
		return allowLooping && ins.hasLoop
			&& ins.loopStart >= 0 && ins.loopEnd > ins.loopStart && ins.loopEnd <= ins.samples.length
			&& (ins.loopCount == Instrument.LOOP_FOREVER || loopIterations < ins.loopCount);
	}
}
