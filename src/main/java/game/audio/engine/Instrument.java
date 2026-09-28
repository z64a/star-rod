package game.audio.engine;

import java.util.ArrayList;
import java.util.List;

import game.audio.Envelope;
import game.audio.SoundBank;
import game.audio.engine.EnvelopePlayer.Script;

/** A decoded bank sample with the parameters the mixer needs to play it. */
public class Instrument
{
	public static final int LOOP_FOREVER = -1;

	/** The envelope used when an instrument has no script for the requested index. */
	private static final Script DEFAULT_ENVELOPE = new Script(
		new int[] { 61, 127, 0xFF, 0 },
		new int[] { 52, 0, 0xFF, 0 });

	private static final int FALLBACK_SAMPLE_RATE = 16000;
	private static final int FALLBACK_SAMPLE_COUNT = 512;
	private static final int FALLBACK_KEY_BASE = 4800;

	public final String name;
	public final String bank;
	public final short[] samples;
	public final int sampleRate;
	public final int keyBase;
	public final boolean hasLoop;
	public final int loopStart;
	public final int loopEnd;
	public final int loopCount;
	public final String envName;
	public final List<Script> envelopes = new ArrayList<>();

	public Instrument(String bank, SoundBank.Instrument ins, Envelope envelope)
	{
		this.name = ins.wav;
		this.bank = bank;
		samples = ins.decoded();
		sampleRate = ins.sampleRate;
		keyBase = ins.keyBase;
		hasLoop = ins.loop != null;
		loopStart = ins.attack.length;
		loopEnd = hasLoop ? loopStart + ins.loop.length : 0;
		loopCount = ins.loopCount;
		envName = ins.envName;
		for (int[][] script : envelope.scripts)
			envelopes.add(new Script(script[0], script[1]));
	}

	private Instrument()
	{
		name = "Default";
		bank = "";
		samples = new short[FALLBACK_SAMPLE_COUNT];
		sampleRate = FALLBACK_SAMPLE_RATE;
		keyBase = FALLBACK_KEY_BASE;
		hasLoop = false;
		loopStart = 0;
		loopEnd = 0;
		loopCount = 0;
		envName = "";
	}

	/** A silent sample that stands in for an instrument that can't be found. */
	public static Instrument silent()
	{
		return new Instrument();
	}

	public Script getEnvelope(int index)
	{
		return index >= 0 && index < envelopes.size() ? envelopes.get(index) : DEFAULT_ENVELOPE;
	}
}
