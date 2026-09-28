package game.audio.engine;

import game.audio.engine.EnvelopePlayer.Script;

public class SamplePlayer implements PlaybackSession
{
	private final AudioEngine engine;

	private Voice voice;
	private Instrument instrument;
	private Script envelope;
	private float pitch = 1.0f;
	private boolean paused;
	private boolean releasing;

	public SamplePlayer(AudioEngine engine)
	{
		this.engine = engine;
	}

	public void play(Instrument instrument, Script envelope)
	{
		stop();
		this.instrument = instrument;
		this.envelope = envelope;
		restart();
	}

	@Override
	public void restart()
	{
		if (voice != null) {
			voice.fadeOut();
			voice = null;
		}
		paused = false;
		releasing = false;
		if (instrument == null || envelope == null)
			return;

		voice = new Voice();
		voice.setInstrument(instrument);
		voice.setEnvelope(envelope);
		voice.setPitch(pitch);
		voice.setLoopingAllowed(true);
		engine.addVoice(voice);
		voice.play();
	}

	@Override
	public void stop()
	{
		if (voice != null) {
			voice.fadeOut();
			voice = null;
		}
		paused = false;
		releasing = false;
	}

	public void release()
	{
		if (voice != null && !voice.isDone()) {
			voice.release();
			releasing = true;
		}
	}

	public boolean isReleasing()
	{
		return releasing && isPlaying();
	}

	@Override
	public boolean isPlaying()
	{
		return voice != null && !voice.isDone();
	}

	@Override
	public boolean isPaused()
	{
		return paused && isPlaying();
	}

	@Override
	public int getTime()
	{
		if (voice == null)
			return 0;
		return Math.min(voice.getOutputPos(), getDuration());
	}

	@Override
	public int getDuration()
	{
		if (instrument == null)
			return 0;
		return Voice.getOutputDuration(instrument, pitch);
	}

	@Override
	public void seekTime(int time)
	{
		int duration = getDuration();
		if (instrument == null || envelope == null || time < 0 || time >= duration)
			return;

		if (time == getTime())
			return;

		boolean wasPaused = isPaused();
		restart();
		engine.prepareForSeek();
		while (isPlaying() && voice.getRenderedSamples() < time)
			engine.renderFrame(AudioEngine.MIXER_BLOCK_TIME, true);
		if (wasPaused) {
			paused = true;
			voice.setPaused(true);
		}
		engine.finishSeek();
	}

	@Override
	public void setPaused(boolean paused)
	{
		if (!isPlaying() || this.paused == paused)
			return;
		this.paused = paused;
		if (paused)
			voice.fadeToPause();
		else
			voice.fadeFromPause();
	}

	public void setPitch(float pitch)
	{
		this.pitch = pitch;
		if (voice != null)
			voice.setPitch(pitch);
	}

}
