package game.audio.engine;

import java.util.List;

import game.audio.AudioTables.Drum;
import game.audio.Mseq;
import game.audio.Mseq.Command;
import game.audio.Mseq.Ramp;
import game.audio.engine.EnvelopePlayer.Script;
import util.Logger;

public class MseqPlayer implements AudioClient, PlaybackSession
{
	private static final int NUM_VOICES = 16;
	private static final int MAX_COMMANDS_PER_UPDATE = 65536;
	public static final int SAMPLES_PER_TICK = 2 * AudioEngine.FRAME_SAMPLES;

	public static enum PlayerState
	{
		PLAYING,
		PAUSED,
		DONE
	}

	private static final int NUM_TRACKS = 10;
	private static final int DRUM_TRACK = 9;
	private static final float MAX_VOL_8 = 127.0f;
	private static final float MAX_VOL_16 = 32767.0f;

	private final AudioEngine engine;
	private final SoundLibrary library;
	private boolean attached;

	private PlayerState state;

	private List<Command> commands;
	private List<Ramp> ramps;
	private int[] startTimes;
	private int duration;
	private String name;
	private int curPos;
	private int delayTime;

	private int curTime;
	private int curDuration;

	private MseqTrack[] tracks;
	private MseqVoice[] voices;

	// loop state
	private int[] loopPositions;
	private int[] loopIterations;
	private int[] timelineLoopCounts;

	// these values cause the player to only update/tick on every other audio frame
	private static final int UPDATE_INTERVAL = 2;
	private int updateCounter = UPDATE_INTERVAL;

	public static class MseqVoice extends Voice
	{
		public final MseqTrack track;
		public final int tuneID;

		public float baseVolume;
		public int baseDetune;

		public MseqVoice(MseqTrack track, int tuneID)
		{
			this.track = track;
			this.tuneID = tuneID;
		}

		public void updateVolume()
		{
			setVolume(baseVolume * track.volumeLerp.current);
		}

		public void updatePitch()
		{
			int detune = baseDetune;
			if (track.index != DRUM_TRACK)
				detune += Math.round(track.tuneLerp.current);
			setPitch(AudioEngine.detuneToPitchRatio(detune));
		}
	}

	public static class LerpState
	{
		float current = 1.0f;
		float goal = 1.0f;
		float step;
		int time;
	}

	public static class MseqTrack
	{
		public final int index;

		public Instrument instrument;
		public Script envelope;

		public LerpState volumeLerp = new LerpState();
		public LerpState tuneLerp = new LerpState();

		public int pan;
		public int reverb;
		public boolean isResumable;

		public MseqTrack(int index)
		{
			this.index = index;
		}

		public void reset()
		{
			volumeLerp = new LerpState();
			tuneLerp = new LerpState();

			volumeLerp.current = 1.0f;
			tuneLerp.current = 0;

			pan = 64;
			reverb = 0;
			isResumable = false;
		}
	}

	public MseqPlayer(AudioEngine engine, SoundLibrary library)
	{
		this.engine = engine;
		this.library = library;
	}

	@Override
	public void attach()
	{
		if (!attached) {
			engine.addClient(this);
			attached = true;
		}
	}

	@Override
	public boolean isPaused()
	{
		return state == PlayerState.PAUSED;
	}

	@Override
	public boolean isPlaying()
	{
		return state == PlayerState.PLAYING || state == PlayerState.PAUSED;
	}

	public void triggerTrackRamps()
	{
		if (commands == null)
			return;

		for (Ramp ramp : ramps) {
			MseqTrack track = tracks[ramp.track()];
			if (!ramp.isVolume()) {
				track.tuneLerp.time = ramp.time();
				track.tuneLerp.goal = ramp.goal();
				track.tuneLerp.step = ((float) ramp.delta()) / ramp.time();
			}
			else {
				track.volumeLerp.time = ramp.time();
				track.volumeLerp.goal = ramp.goal() / MAX_VOL_16;
				track.volumeLerp.step = (ramp.delta() / MAX_VOL_16) / ramp.time();
			}
		}
	}

	@Override
	public int getTimelineLoopCount()
	{
		if (timelineLoopCounts == null)
			return 0;
		return Math.max(timelineLoopCounts[0], timelineLoopCounts[1]);
	}

	@Override
	public void stop()
	{
		if (voices != null) {
			for (MseqVoice voice : voices) {
				if (voice != null)
					voice.kill();
			}
		}
		state = PlayerState.DONE;
	}

	@Override
	public void restart()
	{
		if (commands != null)
			play(name, commands, ramps);
	}

	@Override
	public void setPaused(boolean pause)
	{
		boolean changed = false;

		if (state == PlayerState.PLAYING && pause) {
			state = PlayerState.PAUSED;
			changed = true;
		}
		else if (state == PlayerState.PAUSED && !pause) {
			state = PlayerState.PLAYING;
			changed = true;
		}

		if (changed) {
			for (MseqVoice voice : voices) {
				if (voice != null) {
					voice.setPaused(pause);
				}
			}
		}
	}

	@Override
	public int getTime()
	{
		long time = (long) (curTime + (curDuration - delayTime)) * SAMPLES_PER_TICK;
		return (int) Math.min(Integer.MAX_VALUE, time);
	}

	@Override
	public int getDuration()
	{
		if (commands == null)
			return 0;
		return (int) Math.min(Integer.MAX_VALUE, (long) duration * SAMPLES_PER_TICK);
	}

	@Override
	public void seekTime(int seekTime)
	{
		if (commands == null || commands.isEmpty())
			return;

		if (seekTime < 0 || seekTime >= getDuration())
			return;

		if (seekTime == getTime())
			return;

		boolean wasPaused = (state == PlayerState.PAUSED);

		// hard reset playback to the very start
		play(name, commands, ramps);

		engine.prepareForSeek();

		// silently fast forward through everything until seek time
		while (getTime() < seekTime && state != PlayerState.DONE)
			engine.renderFrame(AudioEngine.MIXER_BLOCK_TIME, true);

		// resume audible playback (or not)
		if (wasPaused) {
			state = PlayerState.PAUSED;
			for (MseqVoice voice : voices) {
				if (voice != null) {
					voice.setPaused(true);
				}
			}
		}
		else {
			state = PlayerState.PLAYING;
		}

		engine.finishSeek();
	}

	@Override
	public void close()
	{
		stop();
		if (attached) {
			engine.removeClient(this);
			attached = false;
		}
	}

	public void setMseq(Mseq mseq, String name)
	{
		play(name, mseq.commands(), mseq.ramps());
	}

	private void play(String name, List<Command> commands, List<Ramp> ramps)
	{
		if (this.commands != null) {
			// kill any voices from previous MSEQ
			for (int i = 0; i < voices.length; i++) {
				MseqVoice voice = voices[i];
				if (voice != null) {
					voice.kill();
				}
			}
		}

		this.name = name;
		this.commands = commands;
		this.ramps = ramps;
		startTimes = new int[commands.size()];
		duration = 0;
		for (int i = 0; i < startTimes.length; i++) {
			startTimes[i] = duration;
			if (commands.get(i).op() == Mseq.Op.Delay)
				duration += commands.get(i).values()[0];
		}

		tracks = new MseqTrack[NUM_TRACKS];
		for (int i = 0; i < NUM_TRACKS; i++) {
			tracks[i] = new MseqTrack(i);
		}

		voices = new MseqVoice[NUM_VOICES];

		// loop state
		loopPositions = new int[2];
		loopIterations = new int[2];
		timelineLoopCounts = new int[2];

		curPos = 0;
		curTime = 0;
		curDuration = 0;
		delayTime = 0;
		updateCounter = UPDATE_INTERVAL;

		for (int i = 0; i < tracks.length; i++) {
			tracks[i].reset();
		}

		engine.resetRenderState();
		state = PlayerState.PLAYING;
	}

	@Override
	public void nextFrame(boolean fastForward)
	{
		updateCounter--;
		if (updateCounter <= 0) {
			updateCounter += UPDATE_INTERVAL;
			update(fastForward);
		}
	}

	private void update(boolean fastForward)
	{
		if (commands == null)
			return;

		if (state != PlayerState.PLAYING)
			return;

		// clear voices which have finished playing/releasing
		for (int i = 0; i < voices.length; i++) {
			MseqVoice voice = voices[i];
			if (voice != null && voice.isDone())
				voices[i] = null;
		}

		// update track ramps
		for (MseqTrack track : tracks) {
			if (track.volumeLerp.time != 0) {
				track.volumeLerp.time--;
				if (track.volumeLerp.time != 0)
					track.volumeLerp.current += track.volumeLerp.step;
				else
					track.volumeLerp.current = track.volumeLerp.goal;
			}

			if (track.tuneLerp.time != 0) {
				track.tuneLerp.time--;
				if (track.tuneLerp.time != 0)
					track.tuneLerp.current += track.tuneLerp.step;
				else
					track.tuneLerp.current = track.tuneLerp.goal;
			}
		}

		// update client params for voices
		for (int i = 0; i < voices.length; i++) {
			MseqVoice voice = voices[i];
			if (voice == null)
				continue;

			voice.updateVolume();
			voice.updatePitch();
		}

		if (delayTime > 0)
			delayTime--;

		// consume commands
		int executed = 0;
		while (delayTime == 0) {
			if (++executed > MAX_COMMANDS_PER_UPDATE) {
				Logger.logfError("MSEQ %s exceeded the per-update command limit", name);
				stop();
				curTime = duration;
				curDuration = 0;
				return;
			}

			if (commands.size() == curPos) {
				state = PlayerState.DONE;
				curTime = duration;
				curDuration = 0;
				return;
			}

			Command command = commands.get(curPos);
			int[] v = command.values();
			curTime = startTimes[curPos];
			curDuration = command.op() == Mseq.Op.Delay ? v[0] : 0;
			curPos++;

			switch (command.op()) {
				case Delay -> delayTime = v[0];
				case StopSound -> {
					for (MseqVoice voice : voices) {
						if (voice != null && voice.track == tracks[v[0]] && voice.tuneID == v[1])
							voice.release();
					}
				}
				case PlaySound -> {
					MseqTrack track = tracks[v[0]];
					if (track.instrument == null) {
						Logger.logfWarning("MSEQ %s: [%X] Play Sound: no instrument", name, v[0]);
					}
					else {
						playVoice(track, v[1], (v[2] & 0x7F) / MAX_VOL_8, ((v[1] & 0x7F) * 100) - track.instrument.keyBase);
					}
				}
				case PlayDrum -> playDrum(v[0] & 0x7F, v[1] & 0x7F);
				case SetVolume -> {
					MseqTrack track = tracks[v[0]];
					track.volumeLerp.current = v[1] / MAX_VOL_8;
					for (MseqVoice voice : voices) {
						if (voice != null && voice.track == track)
							voice.updateVolume();
					}
				}
				case SetTune -> {
					MseqTrack track = tracks[v[0]];
					track.tuneLerp.current = (short) v[1];
					for (MseqVoice voice : voices) {
						if (voice != null && voice.track == track)
							voice.updatePitch();
					}
				}
				case SetPan -> {
					MseqTrack track = tracks[v[0]];
					track.pan = v[1];
					if (v[0] != DRUM_TRACK) {
						for (MseqVoice voice : voices) {
							if (voice != null && voice.track == track)
								voice.setPan(v[1]);
						}
					}
				}
				case SetInstrument -> {
					MseqTrack track = tracks[v[0]];
					track.instrument = library.getInstrument(command.wav());
					track.envelope = track.instrument == null ? null : track.instrument.getEnvelope(v[1]);
				}
				case SetReverb -> tracks[v[0]].reverb = v[1];
				case SetResumable -> tracks[v[0]].isResumable = v[1] != 0;
				case StartLoop -> loopPositions[v[0] & 1] = curPos;
				case EndLoop -> {
					int loopID = v[0] & 1;
					int startPos = loopPositions[loopID];
					int count = v[1];

					if (fastForward) {
						loopIterations[loopID] = 0;
					}
					else if (count == 0) {
						// infinite loop, jump to loop start
						loopIterations[loopID] = 0;
						timelineLoopCounts[loopID]++;
						curPos = startPos;
					}
					else if (loopIterations[loopID] != 0) {
						loopIterations[loopID]--;
						if (loopIterations[loopID] != 0) {
							// not the last iteration, jump to loop start
							curPos = startPos;
						}
					}
					else {
						// first iteration, jump to loop start
						loopIterations[loopID] = count;
						curPos = startPos;
					}
				}
			}
		}
	}

	private void playDrum(int drumID, int volume)
	{
		MseqTrack track = tracks[DRUM_TRACK];
		Drum drum = library.getDrum(drumID);
		Instrument ins = drum == null ? null : library.getInstrument(drum.wav());
		if (ins == null) {
			Logger.logfWarning("MSEQ %s: drum %X has no instrument", name, drumID);
			track.instrument = null;
			track.envelope = null;
			return;
		}

		track.instrument = ins;
		track.envelope = ins.getEnvelope(drum.envelope());
		MseqVoice voice = playVoice(track, drumID, (drum.volume() / MAX_VOL_8) * volume / MAX_VOL_8,
			drum.keyBase() - ins.keyBase);
		voice.setReverb(drum.reverb());
		voice.setPan(Math.max(0, Math.min(127, drum.pan())));
	}

	private MseqVoice playVoice(MseqTrack track, int tuneID, float baseVolume, int baseDetune)
	{
		MseqVoice voice = new MseqVoice(track, tuneID);
		voices[claimVoice()] = voice;
		engine.addVoice(voice);

		voice.baseVolume = baseVolume;
		voice.baseDetune = baseDetune;
		voice.setInstrument(track.instrument);
		voice.setEnvelope(track.envelope);
		voice.setPan(track.pan);
		voice.setReverb(track.reverb);

		voice.updateVolume();
		voice.updatePitch();

		voice.play();
		return voice;
	}

	private int claimVoice()
	{
		for (int i = 0; i < voices.length; i++) {
			if (voices[i] == null)
				return i;
		}

		// try stealing the first voice -- an odd choice, but OK
		voices[0].kill();
		voices[0] = null;
		return 0;
	}
}
