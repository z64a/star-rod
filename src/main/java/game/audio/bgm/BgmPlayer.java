package game.audio.bgm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import game.audio.AudioTables.Drum;
import game.audio.AudioTables.Preset;
import game.audio.bgm.Composition.CompCommand;
import game.audio.bgm.Composition.EndLoopCompCommand;
import game.audio.bgm.Composition.PlayCompCommand;
import game.audio.bgm.Composition.StartLoopCompCommand;
import game.audio.bgm.Track.Branch;
import game.audio.bgm.Track.Delay;
import game.audio.bgm.Track.Detour;
import game.audio.bgm.Track.EventTrigger;
import game.audio.bgm.Track.InstrumentCoarseTune;
import game.audio.bgm.Track.InstrumentFineTune;
import game.audio.bgm.Track.InstrumentPan;
import game.audio.bgm.Track.InstrumentReverb;
import game.audio.bgm.Track.InstrumentVolume;
import game.audio.bgm.Track.InstrumentVolumeLerp;
import game.audio.bgm.Track.MasterTempoLerp;
import game.audio.bgm.Track.MasterVolumeLerp;
import game.audio.bgm.Track.Note;
import game.audio.bgm.Track.OverridePatch;
import game.audio.bgm.Track.ProxMixOverride;
import game.audio.bgm.Track.RandomPan;
import game.audio.bgm.Track.ReverbType;
import game.audio.bgm.Track.SeekCustomEnv;
import game.audio.bgm.Track.SetBusEffect;
import game.audio.bgm.Track.SetMasterDetune;
import game.audio.bgm.Track.SetMasterEffect;
import game.audio.bgm.Track.SetMasterTempo;
import game.audio.bgm.Track.SetMasterVolume;
import game.audio.bgm.Track.SetStereoDelay;
import game.audio.bgm.Track.TrackCommand;
import game.audio.bgm.Track.TrackDetune;
import game.audio.bgm.Track.TrackTremolo;
import game.audio.bgm.Track.TrackTremoloDepth;
import game.audio.bgm.Track.TrackTremoloRate;
import game.audio.bgm.Track.TrackTremoloStop;
import game.audio.bgm.Track.TrackVolume;
import game.audio.bgm.Track.TriggerSound;
import game.audio.bgm.Track.UseCustomEnv;
import game.audio.bgm.Track.UseInstrument;
import game.audio.bgm.Track.WriteCustomEnv;
import game.audio.engine.AudioClient;
import game.audio.engine.AudioEngine;
import game.audio.engine.EffectBus.EffectPreset;
import game.audio.engine.EnvelopePlayer.Script;
import game.audio.engine.Instrument;
import game.audio.engine.PlaybackSession;
import game.audio.engine.SoundLibrary;
import game.audio.engine.Voice;
import util.Logger;

/** Classic's BGM sequencer adapted to the current audio library and shared booth engine. */
public class BgmPlayer implements AudioClient, PlaybackSession
{
	private static final int NUM_TRACKS = 16;
	private static final int DEFAULT_BPM = 156;
	private static final int NUM_LOOP_LABELS = 32;
	private static final int MAX_LOOP_DEPTH = 4;
	private static final int MAX_COMMANDS_PER_TICK = 65536;
	private static final int MAX_PROXIMITY_VOLUME = 127;
	private static final int PROXIMITY_MIX_FADE_TICKS = 144;
	private static final int PROXIMITY_OVERRIDE_FADE_TICKS = 72;
	private static final int MAX_TIMELINE_FRAMES = 30 * 60 * AudioEngine.OUTPUT_RATE / AudioEngine.FRAME_SAMPLES;
	private static final int[] CUSTOM_ENV_TIMES = {
			0x5E, 0x5D, 0x5C, 0x5B, 0x5A, 0x58, 0x56, 0x53,
			0x51, 0x4F, 0x4A, 0x45, 0x40, 0x3B, 0x37, 0x35,
			0x33, 0x31, 0x2F, 0x2D, 0x2B, 0x29, 0x27, 0x26,
			0x25, 0x23, 0x21, 0x20, 0x1F, 0x1E, 0x1D, 0x1C,
			0x1B, 0x1A, 0x19, 0x18, 0x17, 0x16, 0x15, 0x14,
	};

	public interface Listener
	{
		void onEvent(int track, int eventInfo);

		void onSoundTrigger(int index);
	}

	private final AudioEngine engine;
	private final SoundLibrary bank;
	private boolean attached;
	private final BgmTrackPlayer[] tracks = new BgmTrackPlayer[NUM_TRACKS];
	private final List<BgmVoice> voices = new ArrayList<>();
	private final int[][] customPressEnvelopes = new int[8][18];
	private final int[] customEnvelopeWritePos = new int[8];
	private final int[] effectValues = new int[AudioEngine.NUM_EFFECT_BUSES];

	private Song selectedSong;
	private Song currentSong;
	private Composition composition;
	private List<CompCommand> compCommands;
	private int compPos;
	private final int[] compLoopStartPos = new int[NUM_LOOP_LABELS];
	private final LoopState[] compLoops = new LoopState[MAX_LOOP_DEPTH];
	private int compLoopDepth;
	private int[] compStartTimes;

	private final Lerp masterTempo = new Lerp(DEFAULT_BPM);
	private final Lerp masterVolume = new Lerp(1.0f);
	private int masterPitchShift;
	private int detune;
	private double tickAccumulator;
	private int maximumTempo;

	private int compositionIndex;
	private int timelineLoopCount;
	private int proximityMixID;
	private int proximityMixVolume;
	private boolean proximityMixInstant;
	private int frameCounter;
	private int randomValue1;
	private int randomValue2;
	private int currentTime;
	private int duration;
	private int requestedTime;
	private boolean paused;
	private boolean phraseFinished;
	private boolean initLinkMute;
	private int writingCustomEnvelope;
	private Listener listener;

	public BgmPlayer(AudioEngine engine, SoundLibrary bank)
	{
		this.engine = engine;
		this.bank = bank;

		for (int i = 0; i < tracks.length; i++)
			tracks[i] = new BgmTrackPlayer(i);
		for (int i = 0; i < compLoops.length; i++)
			compLoops[i] = new LoopState();
	}

	@Override
	public void attach()
	{
		if (!attached) {
			engine.addClient(this);
			attached = true;
		}
	}

	public void setListener(Listener listener)
	{
		this.listener = listener;
	}

	public void play(Song song, int compositionIndex)
	{
		stop();
		selectedSong = null;
		duration = 0;
		if (song == null)
			return;
		proximityMixID = 0;
		proximityMixVolume = 0;
		proximityMixInstant = false;

		Composition selected = song.getComposition(compositionIndex);
		if (selected == null) {
			compositionIndex = 0;
			selected = song.getComposition(compositionIndex);
		}
		if (selected == null) {
			Logger.logfWarning("BGM %s has no playable composition", song.name);
			return;
		}

		selectedSong = song;
		this.compositionIndex = compositionIndex;
		engine.resetRenderState();
		duration = calculateDuration(song);
		restart();
	}

	@Override
	public void stop()
	{
		clearActivePlayback();
		engine.resetEffects();
	}

	@Override
	public void restart()
	{
		if (selectedSong == null)
			return;
		engine.resetRenderState();
		beginPlayback(selectedSong);
		engine.resetRenderState();
	}

	@Override
	public boolean isPlaying()
	{
		return currentSong != null;
	}

	@Override
	public boolean isPaused()
	{
		return paused;
	}

	@Override
	public void setPaused(boolean paused)
	{
		if (currentSong == null || this.paused == paused)
			return;

		this.paused = paused;
		for (BgmVoice voice : voices)
			voice.setPaused(paused);
	}

	@Override
	public int getTime()
	{
		return Math.min(currentTime, duration) * AudioEngine.FRAME_SAMPLES;
	}

	@Override
	public int getDuration()
	{
		return duration * AudioEngine.FRAME_SAMPLES;
	}

	public int getCompositionIndex()
	{
		return compositionIndex;
	}

	@Override
	public int getTimelineLoopCount()
	{
		return timelineLoopCount;
	}

	public int getProximityMixID()
	{
		return proximityMixID;
	}

	public int getProximityMixVolume()
	{
		return proximityMixVolume;
	}

	public void setProximityMix(int mixID, int volume)
	{
		setProximityMix(mixID, volume, false);
	}

	public void resetProximityMix()
	{
		if (selectedSong == null)
			return;

		boolean mixChanged = proximityMixID != 0;
		proximityMixID = 0;
		proximityMixVolume = 0;
		proximityMixInstant = false;
		for (BgmTrackPlayer track : tracks) {
			if (mixChanged)
				track.proximityMixChanged = true;
			track.proximityValueChanged = false;
			track.proximityVolume.setImmediate(1.0f);
		}
	}

	public void setProximityMix(int mixID, int volume, boolean instant)
	{
		if (selectedSong == null)
			return;

		int max = Math.max(1, selectedSong.branchOptions);
		mixID = Math.max(0, Math.min(max - 1, mixID));
		volume = Math.max(0, Math.min(MAX_PROXIMITY_VOLUME, volume));
		if (proximityMixID == mixID && proximityMixVolume == volume
			&& proximityMixInstant == instant)
			return;

		boolean mixChanged = proximityMixID != mixID;
		boolean finishFade = instant && !proximityMixInstant;
		proximityMixID = mixID;
		proximityMixVolume = volume;
		proximityMixInstant = instant;
		if (finishFade) {
			for (BgmTrackPlayer track : tracks)
				track.proximityVolume.finish();
		}
		markProximityMixChanged(mixChanged);
	}

	@Override
	public void seekTime(int seekTime)
	{
		if (selectedSong == null || getDuration() == 0)
			return;
		if (seekTime < 0 || seekTime >= getDuration() || seekTime == getTime())
			return;

		boolean wasPaused = paused;
		beginPlayback(selectedSong);
		engine.prepareForSeek();
		while (currentSong != null && getTime() < seekTime)
			engine.renderFrame(AudioEngine.MIXER_BLOCK_TIME, true);

		for (BgmVoice voice : voices)
			voice.setLoopingAllowed(true);
		if (wasPaused)
			setPaused(true);

		engine.finishSeek();
	}

	@Override
	public void nextFrame(boolean fastForward)
	{
		if (currentSong == null || paused)
			return;

		frameCounter++;
		randomValue1 = (randomValue1 & 0xFFFF) + (currentTime & 0xFFFF) + (frameCounter & 0xFFFF);
		randomValue2 = (randomValue2 & 0xFFFF) + ((currentTime << 4) & 0xFFFF) + ((frameCounter >> 4) & 0xFFFF);

		if (requestedTime >= 0) {
			currentTime = requestedTime;
			requestedTime = -1;
		}
		else {
			currentTime++;
		}

		double bpm = Math.max(1.0f, masterTempo.current);
		double ticksPerBlock = bpm * currentSong.getTicksPerBeat()
			* AudioEngine.FRAME_SAMPLES / (60.0 * AudioEngine.OUTPUT_RATE);
		tickAccumulator += ticksPerBlock;
		while (tickAccumulator >= 1.0 && currentSong != null) {
			tickAccumulator -= 1.0;
			updateTick(fastForward);
		}
	}

	private int calculateDuration(Song song)
	{
		Composition selected = song.getComposition(compositionIndex);
		if (selected == null)
			selected = song.getComposition(0);
		if (selected == null)
			return 0;
		compStartTimes = new int[selected.getCommands().size()];
		Arrays.fill(compStartTimes, -1);
		beginPlayback(song);
		while (currentSong != null && currentTime < MAX_TIMELINE_FRAMES)
			engine.renderFrame(AudioEngine.MIXER_BLOCK_TIME, true);

		int result = currentTime;
		if (currentSong != null)
			Logger.logfWarning("BGM %s exceeded the thirty-minute timeline limit", song.name);
		clearActivePlayback();
		return result;
	}

	private void beginPlayback(Song song)
	{
		clearActivePlayback();
		composition = song.getComposition(compositionIndex);
		if (composition == null)
			composition = song.getComposition(0);
		if (composition == null)
			return;

		currentSong = song;
		maximumTempo = calculateMaximumTempo(song);
		compCommands = composition.getCommands();
		compPos = 0;
		Arrays.fill(compLoopStartPos, 0);
		for (int i = 0; i < compLoops.length; i++)
			compLoops[i].clear();
		compLoopDepth = 0;

		masterTempo.setImmediate(tempo(DEFAULT_BPM));
		masterVolume.setImmediate(1.0f);
		masterPitchShift = 0;
		detune = 0;
		tickAccumulator = 1.0;
		frameCounter = 0;
		randomValue1 = 0;
		randomValue2 = 0;
		currentTime = 0;
		timelineLoopCount = 0;
		requestedTime = -1;
		paused = false;
		phraseFinished = false;
		initLinkMute = true;
		writingCustomEnvelope = 0;
		for (int i = 0; i < customPressEnvelopes.length; i++)
			clearCustomEnvelope(i);
		Arrays.fill(effectValues, 0);
		for (BgmTrackPlayer track : tracks)
			track.reset();
		if (proximityMixID != 0 || proximityMixVolume != 0)
			markProximityMixChanged(proximityMixID != 0);
		engine.resetEffects();
		advanceComposition(false);
	}

	private void markProximityMixChanged(boolean mixChanged)
	{
		for (BgmTrackPlayer track : tracks) {
			if (mixChanged)
				track.proximityMixChanged = true;
			track.proximityValueChanged = true;
		}
	}

	private static int calculateMaximumTempo(Song song)
	{
		return Math.max(1, 60 * AudioEngine.OUTPUT_RATE
			/ (AudioEngine.FRAME_SAMPLES * song.getTicksPerBeat()));
	}

	private int tempo(int bpm)
	{
		return Math.max(1, Math.min(maximumTempo, bpm));
	}

	private void clearActivePlayback()
	{
		for (BgmVoice voice : voices)
			voice.kill();
		voices.clear();
		for (BgmTrackPlayer track : tracks)
			track.disable();
		currentSong = null;
		composition = null;
		compCommands = null;
		paused = false;
		phraseFinished = false;
		currentTime = 0;
		requestedTime = -1;
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

	private void updateTick(boolean fastForward)
	{
		masterTempo.update();
		masterVolume.update();
		removeFinishedVoices();

		for (BgmTrackPlayer track : tracks)
			track.updateParameters();
		int phraseTransitions = 0;
		do {
			phraseFinished = false;
			for (BgmTrackPlayer track : tracks) {
				if (track.enabled && track.updateCommands(fastForward))
					phraseFinished = true;
			}
			if (phraseFinished) {
				advanceComposition(fastForward);
				phraseTransitions++;
			}
		}
		while (phraseFinished && currentSong != null && phraseTransitions < 256);

		if (phraseTransitions == 256) {
			Logger.logfError("BGM %s changed phrase too many times in one tick", currentSong.name);
			finishPlayback();
		}

		for (BgmVoice voice : voices)
			voice.updateNote();
	}

	private void advanceComposition(boolean fastForward)
	{
		int commandCount = 0;
		while (currentSong != null) {
			if (++commandCount > MAX_COMMANDS_PER_TICK) {
				Logger.logfError("BGM %s has a composition loop with no playable phrase", currentSong.name);
				finishPlayback();
				return;
			}
			if (compPos >= compCommands.size()) {
				finishPlayback();
				return;
			}

			int commandPos = compPos;
			CompCommand command = compCommands.get(compPos++);
			if (compStartTimes != null && commandPos < compStartTimes.length && compStartTimes[commandPos] < 0)
				compStartTimes[commandPos] = currentTime;

			if (command instanceof PlayCompCommand play) {
				loadPhrase(play.getPhrase());
				return;
			}
			if (command instanceof StartLoopCompCommand start) {
				int index = start.getLoopIndex() & 0x1F;
				compLoopStartPos[index] = compPos;
				continue;
			}
			if (command instanceof EndLoopCompCommand end) {
				if (fastForward)
					continue;
				endCompositionLoop(end);
			}
		}
	}

	private void endCompositionLoop(EndLoopCompCommand command)
	{
		int loopEndPos = compPos;
		LoopState loop = compLoops[compLoopDepth];
		if (loop.active && loop.endPos == loopEndPos) {
			if (loop.remaining != 0) {
				loop.remaining--;
				if (loop.remaining == 0) {
					loop.clear();
					if (compLoopDepth > 0)
						compLoopDepth--;
					return;
				}
			}
		}
		else {
			if (loop.active && compLoopDepth < compLoops.length - 1) {
				compLoopDepth++;
				loop = compLoops[compLoopDepth];
			}
			loop.active = true;
			loop.endPos = loopEndPos;
			loop.remaining = command.getLoopCount();
		}

		if (command.getLoopCount() == 0)
			timelineLoopCount++;

		int loopStart = compLoopStartPos[command.getLoopIndex() & 0x1F];
		compPos = loopStart;
		if (compStartTimes != null && loopStart < compStartTimes.length)
			requestTime(compStartTimes[loopStart]);
	}

	private void loadPhrase(Phrase phrase)
	{
		boolean foundLinked = false;
		for (BgmTrackPlayer track : tracks)
			track.disable();

		for (int i = 0; i < tracks.length; i++) {
			Track source = phrase.tracks[i];
			if (!source.enabled || !source.unkFlag)
				continue;
			if (source.copyOf >= 0 && source.copyOf < phrase.tracks.length)
				source = phrase.tracks[source.copyOf];
			tracks[i].load(source);
		}

		for (int i = 0; i < tracks.length; i++) {
			BgmTrackPlayer track = tracks[i];
			if (!track.enabled || track.linkedIndex < 0)
				continue;
			int linked = track.linkedIndex;
			if (linked < 0 || linked >= i || !tracks[linked].enabled) {
				track.disable();
				continue;
			}
			track.voiceOwner = tracks[linked].voiceOwner;
			track.polyphonicIndex = tracks[linked].polyphonicIndex;
			track.polyphony = tracks[linked].polyphony;
			if (initLinkMute)
				track.muted = true;
			foundLinked = true;
		}
		if (foundLinked)
			initLinkMute = false;
	}

	private void finishPlayback()
	{
		for (BgmVoice voice : voices)
			voice.kill();
		voices.clear();
		for (BgmTrackPlayer track : tracks)
			track.disable();
		currentSong = null;
	}

	private void requestTime(int time)
	{
		if (time >= 0)
			requestedTime = time;
	}

	private void removeFinishedVoices()
	{
		Iterator<BgmVoice> iterator = voices.iterator();
		while (iterator.hasNext()) {
			BgmVoice voice = iterator.next();
			if (voice.isDone())
				iterator.remove();
		}
	}

	private BgmVoice acquireVoice(BgmTrackPlayer track)
	{
		if (track.muted || track.polyphony <= 0)
			return null;

		BgmVoice candidate = null;
		int count = 0;
		for (BgmVoice voice : voices) {
			if (voice.owner != track.voiceOwner)
				continue;
			count++;
			if (track.polyphonicIndex < 5) {
				candidate = voice;
			}
			else if (!voice.pendingTick && (candidate == null || voice.length < candidate.length)) {
				candidate = voice;
			}
		}

		if (count >= track.polyphony && candidate == null)
			return null;
		if (count >= track.polyphony) {
			candidate.kill();
			voices.remove(candidate);
		}
		return new BgmVoice(track);
	}

	private void resetVoices(BgmTrackPlayer track)
	{
		Iterator<BgmVoice> iterator = voices.iterator();
		while (iterator.hasNext()) {
			BgmVoice voice = iterator.next();
			if (voice.owner == track.voiceOwner) {
				voice.kill();
				iterator.remove();
			}
		}
	}

	private void clearCustomEnvelope(int index)
	{
		Arrays.fill(customPressEnvelopes[index], 0xFF);
		for (int i = 1; i < customPressEnvelopes[index].length; i += 2)
			customPressEnvelopes[index][i] = 0;
		customEnvelopeWritePos[index] = 0;
	}

	private void writeCustomEnvelope(int value)
	{
		int index = writingCustomEnvelope - 1;
		if (index < 0 || index >= customPressEnvelopes.length)
			return;
		int pos = customEnvelopeWritePos[index];
		if (pos >= 8)
			return;

		int time = (value >> 8) & 0xFF;
		int volume = value & 0xFF;
		if (time < CUSTOM_ENV_TIMES.length)
			time = CUSTOM_ENV_TIMES[time];
		customPressEnvelopes[index][pos * 2] = time;
		customPressEnvelopes[index][pos * 2 + 1] = volume;
		customEnvelopeWritePos[index]++;
	}

	private EffectPreset effectPreset(int type)
	{
		switch (type) {
			case 1:
				return EffectPreset.SMALL_ROOM;
			case 2:
				return EffectPreset.BIG_ROOM;
			case 3:
				return EffectPreset.CHORUS;
			case 4:
				return EffectPreset.FLANGE;
			case 5:
				return EffectPreset.ECHO;
			case 10:
				return EffectPreset.BIG_ROOM;
			default:
				return EffectPreset.NONE;
		}
	}

	private record InstrumentQueryResult(Instrument instrument, Script envelope) {}

	private InstrumentQueryResult resolveInstrument(String wav, int envelope)
	{
		if (bank == null)
			return null;
		Instrument instrument = wav == null ? bank.getInstrument(0x20, 0x10) : bank.getInstrument(wav);
		return instrument == null ? null : new InstrumentQueryResult(instrument, instrument.getEnvelope(envelope));
	}

	private void warnMissingInstrument(BgmTrackPlayer track)
	{
		if (bank == null)
			return;
		Logger.logfWarning("BGM %s track %X tried to play without an instrument",
			currentSong.name, track.index);
	}

	private final class BgmTrackPlayer
	{
		private final int index;
		private final Deque<StreamCursor> returns = new ArrayDeque<>();
		private final Lerp instrumentVolume = new Lerp(1.0f);

		private boolean enabled;
		private boolean isDrum;
		private boolean muted;
		private int linkedIndex;
		private int polyphonicIndex;
		private int polyphony;
		private BgmTrackPlayer voiceOwner;
		private CommandStream stream;
		private int pos;
		private int delay;

		private Instrument instrument;
		private Script envelope;
		private int pan;
		private int reverb;
		private int trackVolume;
		private int coarseTune;
		private int fineTune;
		private int trackDetune;
		private int randomPan;
		private int tremoloDelay;
		private int tremoloRate;
		private int tremoloDepth;
		private int pressOverride;
		private int effectBus;
		private final Lerp proximityVolume = new Lerp(1.0f);
		private boolean proximityMixChanged;
		private boolean proximityValueChanged;
		private int proxVol1;
		private int proxVol2;

		private BgmTrackPlayer(int index)
		{
			this.index = index;
			reset();
		}

		private void reset()
		{
			disable();
			instrument = null;
			envelope = null;
			instrumentVolume.setImmediate(1.0f);
			pan = 64;
			reverb = 0;
			trackVolume = 127;
			coarseTune = 0;
			fineTune = 0;
			trackDetune = 0;
			randomPan = 0;
			tremoloDelay = 0;
			tremoloRate = 0;
			tremoloDepth = 0;
			pressOverride = 0;
			effectBus = 0;
			proximityVolume.setImmediate(1.0f);
			proximityMixChanged = false;
			proximityValueChanged = false;
			proxVol1 = 0;
			proxVol2 = 0;
			muted = false;
		}

		private void load(Track track)
		{
			enabled = true;
			isDrum = track.isDrum();
			linkedIndex = track.linkedIndex;
			polyphonicIndex = track.polyphonicIndex;
			polyphony = track.polyphonicVoiceCount;
			voiceOwner = this;
			stream = track.getCommands();
			pos = 0;
			delay = 1;
			returns.clear();
		}

		private void disable()
		{
			enabled = false;
			stream = null;
			pos = 0;
			delay = 0;
			returns.clear();
		}

		private void updateParameters()
		{
			instrumentVolume.update();
			proximityVolume.update();
		}

		private boolean updateCommands(boolean fastForward)
		{
			if (delay > 0)
				delay--;

			int commandCount = 0;
			while (enabled && delay == 0) {
				if (++commandCount > MAX_COMMANDS_PER_TICK) {
					Logger.logfError("BGM %s track %X executed too many commands in one tick",
						currentSong.name, index);
					disable();
					return true;
				}

				if (pos >= stream.getCommands().size()) {
					if (!returns.isEmpty()) {
						StreamCursor cursor = returns.pop();
						stream = cursor.stream;
						pos = cursor.pos;
						continue;
					}
					disable();
					return true;
				}

				TrackCommand command = stream.getCommands().get(pos++);
				if (command instanceof Delay cmd) {
					delay = cmd.ticks;
				}
				else if (command instanceof Note cmd) {
					playNote(cmd, fastForward);
				}
				else {
					execute(command, fastForward);
				}
			}
			return false;
		}

		private void execute(TrackCommand command, boolean fastForward)
		{
			if (command instanceof SetMasterTempo cmd) {
				masterTempo.setImmediate(tempo(cmd.bpm));
			}
			else if (command instanceof SetMasterVolume cmd) {
				masterVolume.setImmediate(volume(cmd.value));
			}
			else if (command instanceof SetMasterDetune cmd) {
				masterPitchShift = cmd.cents * 100;
			}
			else if (command instanceof SetBusEffect cmd) {
				engine.setEffectPreset(0, effectPreset(cmd.effectType));
			}
			else if (command instanceof MasterTempoLerp cmd) {
				masterTempo.set(cmd.time, tempo(cmd.bpm));
			}
			else if (command instanceof MasterVolumeLerp cmd) {
				masterVolume.set(cmd.time, volume(cmd.value));
			}
			else if (command instanceof SetMasterEffect cmd) {
				if (cmd.index >= 0 && cmd.index < AudioEngine.NUM_EFFECT_BUSES
					&& effectValues[cmd.index] != cmd.value) {
					engine.setEffectPreset(cmd.index, effectPreset(cmd.value));
					effectValues[cmd.index] = cmd.value;
				}
			}
			else if (command instanceof OverridePatch cmd) {
				setInstrument(resolveInstrument(cmd.wav, cmd.envelope));
			}
			else if (command instanceof InstrumentVolume cmd) {
				instrumentVolume.setImmediate(volume(cmd.value));
			}
			else if (command instanceof InstrumentPan cmd) {
				pan = cmd.value;
				randomPan = 0;
				updateVoiceMix();
			}
			else if (command instanceof InstrumentReverb cmd) {
				reverb = cmd.value;
				updateVoiceMix();
			}
			else if (command instanceof TrackVolume cmd) {
				trackVolume = cmd.value;
			}
			else if (command instanceof InstrumentCoarseTune cmd) {
				coarseTune = cmd.semitones * 100;
			}
			else if (command instanceof InstrumentFineTune cmd) {
				fineTune = cmd.cents;
			}
			else if (command instanceof TrackDetune cmd) {
				trackDetune = cmd.cents;
			}
			else if (command instanceof TrackTremolo cmd) {
				tremoloDelay = cmd.delay;
				tremoloRate = cmd.speed;
				tremoloDepth = cmd.depth;
			}
			else if (command instanceof TrackTremoloRate cmd) {
				tremoloRate = cmd.value;
			}
			else if (command instanceof TrackTremoloDepth cmd) {
				tremoloDepth = cmd.value;
			}
			else if (command instanceof TrackTremoloStop) {
				tremoloDepth = 0;
			}
			else if (command instanceof RandomPan cmd) {
				pan = cmd.pan1;
				randomPan = cmd.pan2;
			}
			else if (command instanceof UseInstrument cmd) {
				useInstrument(cmd);
			}
			else if (command instanceof InstrumentVolumeLerp cmd) {
				instrumentVolume.set(cmd.time, volume(cmd.value));
			}
			else if (command instanceof ReverbType cmd) {
				effectBus = cmd.index < AudioEngine.NUM_EFFECT_BUSES ? cmd.index : 0;
			}
			else if (command instanceof Branch cmd) {
				enterBranch(cmd);
			}
			else if (command instanceof EventTrigger cmd) {
				if (!fastForward && listener != null)
					listener.onEvent(index, cmd.eventInfo >> 8);
			}
			else if (command instanceof Detour cmd) {
				enterStream(cmd.detour.getCommands());
			}
			else if (command instanceof SetStereoDelay cmd) {
				if (cmd.index < AudioEngine.NUM_EFFECT_BUSES)
					engine.setStereoDelay(cmd.index, cmd.side, cmd.length);
			}
			else if (command instanceof SeekCustomEnv cmd) {
				if (cmd.index >= 1 && cmd.index <= customPressEnvelopes.length) {
					writingCustomEnvelope = cmd.index;
					clearCustomEnvelope(cmd.index - 1);
				}
				else {
					writingCustomEnvelope = 0;
				}
			}
			else if (command instanceof WriteCustomEnv cmd) {
				writeCustomEnvelope(cmd.value);
			}
			else if (command instanceof UseCustomEnv cmd) {
				pressOverride = cmd.index;
			}
			else if (command instanceof TriggerSound cmd) {
				if (!fastForward && listener != null)
					listener.onSoundTrigger(cmd.index);
			}
			else if (command instanceof ProxMixOverride cmd) {
				if (cmd.vol1 != 0) {
					proxVol1 = cmd.vol1;
					proxVol2 = cmd.vol2;
				}
				else if (proximityValueChanged) {
					proximityValueChanged = false;
					applyProximityOverrides();
				}
			}
		}

		private void applyProximityOverrides()
		{
			boolean full = proximityMixVolume == MAX_PROXIMITY_VOLUME;
			for (BgmTrackPlayer track : tracks) {
				int value = full ? track.proxVol1 : track.proxVol2;
				if (value == 0)
					continue;
				track.proximityValueChanged = false;
				track.setProximityVolume(value, PROXIMITY_OVERRIDE_FADE_TICKS);
			}
		}

		private void setProximityVolume(int value, int fadeTicks)
		{
			float target = volume(value);
			if (proximityMixInstant)
				proximityVolume.setImmediate(target);
			else
				proximityVolume.set(fadeTicks, target);
		}

		private void useInstrument(UseInstrument command)
		{
			Preset preset;
			InstrumentQueryResult result;
			if (command.useGlobal) {
				if (bank == null) {
					setInstrument(null);
					return;
				}
				preset = bank.getPreset(command.index);
				if (preset == null) {
					setInstrument(null);
					return;
				}
				result = resolveInstrument(preset.wav(), preset.envelope());
			}
			else {
				if (command.index < 0 || command.index >= currentSong.getInstruments().size()) {
					Logger.logfWarning("BGM %s has no local instrument %X", currentSong.name, command.index);
					setInstrument(null);
					return;
				}
				preset = currentSong.getInstruments().get(command.index);
				result = resolveInstrument(preset.wav(), preset.envelope());
			}

			setInstrument(result);
			instrumentVolume.setImmediate(volume(preset.volume()));
			pan = preset.pan();
			reverb = preset.reverb();
			coarseTune = preset.coarseTune() * 100;
			fineTune = preset.fineTune();
			updateVoiceMix();
		}

		private void setInstrument(InstrumentQueryResult result)
		{
			if (result == null) {
				instrument = null;
				envelope = null;
			}
			else {
				instrument = result.instrument();
				envelope = result.envelope();
			}
		}

		private void enterBranch(Branch command)
		{
			CommandStream option = command.branch.getOption(proximityMixID);
			if (option == null)
				option = command.branch.getOption(0);
			if (option == null)
				return;

			isDrum = option.isDrum();
			if (proximityMixChanged) {
				proximityMixChanged = false;
				proximityVolume.setImmediate(
					proximityMixID == 0 && proximityMixVolume == 0 ? 1.0f : 0.0f);
				resetVoices(this);
			}
			if (proximityValueChanged) {
				proximityValueChanged = false;
				setProximityVolume(proximityMixVolume, PROXIMITY_MIX_FADE_TICKS);
			}
			coarseTune = 0;
			fineTune = 0;
			pressOverride = 0;
			trackDetune = 0;
			tremoloDepth = 0;
			instrumentVolume.cancel();
			randomPan = 0;
			effectBus = 0;
			enterStream(option);
		}

		private void enterStream(CommandStream next)
		{
			returns.push(new StreamCursor(stream, pos));
			stream = next;
			pos = 0;
		}

		private void playNote(Note note, boolean fastForward)
		{
			if (note.getLength() <= 1)
				return;
			if (isDrum)
				playDrum(note, fastForward);
			else
				playInstrument(note, fastForward);
		}

		private void playInstrument(Note note, boolean fastForward)
		{
			if (instrument == null) {
				warnMissingInstrument(this);
				return;
			}

			BgmVoice voice = acquireVoice(this);
			if (voice == null)
				return;
			voice.instrument = instrument;
			voice.baseDetune = note.getPitch() * 100 + coarseTune + masterPitchShift + fineTune - instrument.keyBase;
			voice.velocity = note.getVelocity() == 0 ? 0 : Math.min(128, note.getVelocity() + 1);
			voice.pan = randomPan == 0 ? pan : randomPan(randomValue1, pan, randomPan);
			voice.reverb = reverb;
			voice.start(note.getLength(), envelopeForVoice(), fastForward);
		}

		private void playDrum(Note note, boolean fastForward)
		{
			Drum drum;
			InstrumentQueryResult result;
			int pitch = note.getPitch();
			if (pitch < 72) {
				if (bank == null)
					return;
				drum = bank.getDrum(pitch);
				if (drum == null)
					return;
				result = resolveInstrument(drum.wav(), drum.envelope());
				if (result == null)
					return;
			}
			else {
				List<Drum> drums = currentSong.getDrums();
				if (drums.isEmpty()) {
					Logger.logfWarning("BGM %s has no local drums", currentSong.name);
					return;
				}
				int drumIndex = pitch - 72;
				if (drumIndex >= drums.size())
					drumIndex = 0;
				drum = drums.get(drumIndex);
				result = resolveInstrument(drum.wav(), drum.envelope());
				if (result == null)
					return;
			}

			BgmVoice voice = acquireVoice(this);
			if (voice == null)
				return;
			voice.instrument = result.instrument();
			voice.baseDetune = drum.keyBase() + coarseTune + fineTune - voice.instrument.keyBase;
			voice.randomDetune = drum.randTune() == 0
				? 0
				: randomPitch(randomValue1, voice.baseDetune + trackDetune + detune, drum.randTune())
					- (voice.baseDetune + trackDetune + detune);
			voice.velocity = note.getVelocity() == 0 ? 0 : Math.min(128, note.getVelocity() + 1);
			voice.drumVolume = drum.randVolume() == 0
				? volume(drum.volume())
				: randomVolume(randomValue1, drum.volume(), drum.randVolume()) / 127.0f;
			voice.pan = drum.randPan() == 0 ? drum.pan() : randomPan(randomValue2, drum.pan(), drum.randPan());
			voice.reverb = drum.randReverb() == 0
				? drum.reverb()
				: randomReverb(randomValue1, drum.reverb(), drum.randReverb());
			voice.start(note.getLength(), result.envelope(), fastForward);
		}

		private Script envelopeForVoice()
		{
			if (envelope == null || pressOverride == 0)
				return envelope;
			int index = pressOverride - 1;
			if (index < 0 || index >= customPressEnvelopes.length)
				return envelope;
			return envelope.withPress(customPressEnvelopes[index]);
		}

		private void updateVoiceMix()
		{
			for (BgmVoice voice : voices) {
				if (voice.track == this && !voice.drum) {
					voice.pan = pan;
					voice.reverb = reverb;
					voice.updateMix();
				}
			}
		}
	}

	private final class BgmVoice extends Voice
	{
		private final BgmTrackPlayer track;
		private final BgmTrackPlayer owner;
		private Instrument instrument;
		private boolean drum;
		private boolean pendingTick;
		private int length;
		private int velocity;
		private int baseDetune;
		private int randomDetune;
		private float drumVolume = 1.0f;
		private int pan;
		private int reverb;
		private int tremoloDepth;
		private int tremoloDelay;
		private int tremoloPhase;

		private BgmVoice(BgmTrackPlayer track)
		{
			this.track = track;
			this.owner = track.voiceOwner;
			this.drum = track.isDrum;
		}

		private void start(int length, Script envelope, boolean fastForward)
		{
			this.length = length;
			pendingTick = true;
			tremoloDepth = track.tremoloDepth;
			tremoloDelay = track.tremoloDelay;
			tremoloPhase = 0;

			voices.add(this);
			engine.addVoice(this);
			setInstrument(instrument);
			setEnvelope(envelope);
			setLoopingAllowed(!fastForward);
			setEffectBus(track.effectBus);
			updateMix();
			updatePitch();
			play();
		}

		private void updateNote()
		{
			if (isDone())
				return;
			if (pendingTick) {
				pendingTick = false;
				return;
			}

			if (length > 0) {
				length--;
				if (length == 0)
					release();
			}
			if (tremoloDepth != 0) {
				if (tremoloDelay > 0)
					tremoloDelay--;
				else
					tremoloPhase = (tremoloPhase + track.tremoloRate) & 0xFF;
			}
			updateMix();
			updatePitch();
		}

		private void updateMix()
		{
			float noteVolume = velocity / 128.0f;
			float value = masterVolume.current * track.instrumentVolume.current * track.proximityVolume.current
				* volume(track.trackVolume) * noteVolume * drumVolume;
			setVolume(value);
			setPan(pan);
			setReverb(reverb);
		}

		private void updatePitch()
		{
			int value = baseDetune + randomDetune + track.trackDetune + detune;
			if (!drum && tremoloDepth != 0 && tremoloDelay == 0)
				value += tremolo(tremoloPhase, tremoloDepth);
			setPitch(AudioEngine.detuneToPitchRatio(value));
		}
	}

	private static final class StreamCursor
	{
		private final CommandStream stream;
		private final int pos;

		private StreamCursor(CommandStream stream, int pos)
		{
			this.stream = stream;
			this.pos = pos;
		}
	}

	private static final class LoopState
	{
		private boolean active;
		private int endPos;
		private int remaining;

		private void clear()
		{
			active = false;
			endPos = 0;
			remaining = 0;
		}
	}

	private static final class Lerp
	{
		private float current;
		private float goal;
		private float step;
		private int time;

		private Lerp(float current)
		{
			setImmediate(current);
		}

		private void setImmediate(float value)
		{
			current = value;
			goal = value;
			step = 0.0f;
			time = 0;
		}

		private void set(int time, float goal)
		{
			if (time <= 0)
				time = 1;
			this.time = time;
			this.goal = goal;
			step = (goal - current) / time;
		}

		private void cancel()
		{
			time = 0;
			goal = current;
			step = 0.0f;
		}

		private void finish()
		{
			setImmediate(goal);
		}

		private boolean update()
		{
			if (time == 0)
				return false;
			time--;
			if (time == 0)
				current = goal;
			else
				current += step;
			return true;
		}
	}

	private static float volume(int value)
	{
		return (value & 0x7F) / 127.0f;
	}

	private static int tremolo(int phase, int depth)
	{
		int quadrant = (phase >> 6) & 3;
		int offset = phase & 0x3F;
		int triangle;
		if (quadrant == 0)
			triangle = offset * 4;
		else if (quadrant == 1)
			triangle = (63 - offset) * 4;
		else if (quadrant == 2)
			triangle = -offset * 4;
		else
			triangle = -(63 - offset) * 4;
		return triangle * depth >> 8;
	}

	private static int randomPan(int seed, int pan, int amplitude)
	{
		int parity = ((seed >> 7) + (seed >> 2)) & 1;
		int random = ((seed >> 8) & 0x3F) + ((seed << 4) & 0xC0);
		int delta = amplitude * random >> 8;
		int result = parity != 0 ? pan + delta : pan - delta;
		return Math.max(0, Math.min(127, result));
	}

	private static int randomPitch(int seed, int pitch, int amplitude)
	{
		int parity = ((seed >> 4) + (seed >> 1)) & 1;
		int random = ((seed >> 6) & 0xF) + ((seed << 2) & 0xF0);
		int delta = amplitude * 5 * random >> 8;
		return parity != 0 ? pitch + delta : pitch - delta;
	}

	private static int randomVolume(int seed, int volume, int amplitude)
	{
		int random = ((seed >> 8) & 0x1F) + (seed & 0xE0);
		return Math.max(0, Math.min(127, volume * (0x8000 - amplitude * random) >> 15));
	}

	private static int randomReverb(int seed, int reverb, int amplitude)
	{
		int random = ((seed >> 7) & 7) + ((seed << 3) & 0xF8);
		return Math.max(0, Math.min(127, reverb * (0x8000 - amplitude * random) >> 15));
	}
}
