package game.audio.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import game.audio.EnvelopeProgram;
import game.audio.SfxArchive;
import game.audio.SfxArchive.Command;
import game.audio.SfxArchive.Definition;
import game.audio.SfxArchive.Empty;
import game.audio.SfxArchive.Envelope;
import game.audio.SfxArchive.Label;
import game.audio.SfxArchive.Node;
import game.audio.SfxArchive.OneShot;
import game.audio.SfxArchive.Op;
import game.audio.SfxArchive.Sequence;
import game.audio.SfxArchive.Sound;
import game.audio.SfxArchive.SpawnedEffect;
import game.audio.SfxArchive.Track;
import game.audio.engine.EnvelopePlayer.Script;
import util.Logger;

public class SfxPlayer implements AudioClient, PlaybackSession
{
	private static final int ENV_END = 0xFF;
	private static final int MAX_COMMANDS_PER_FRAME = 65536;
	private static final int MAX_TRACK_PLAYERS = 1024;
	private static final int MAX_TIMELINE_FRAMES = 10 * 60 * AudioEngine.OUTPUT_RATE / AudioEngine.FRAME_SAMPLES;
	private static final int TRIGGER_NONE = 0;
	private static final int TRIGGER_ALTERNATIVE_SOUND = 1;
	private static final int TRIGGER_ALTERNATIVE_VOLUME = 2;
	private static final int UPDATE_STEP = 312500;
	private static final int UPDATE_INTERVAL = 434782;
	private static final Script SFX_ENVELOPE_FAST = new Script(
		new int[] {
				60, 127,
				45, 127,
				43, 95,
				29, 0,
				ENV_END, 0
		},
		new int[] {
				39, 0,
				ENV_END, 0
		}
	);

	private static final Script SFX_ENVELOPE_SLOW = new Script(
		new int[] {
				60, 127,
				35, 127,
				60, 63,
				42, 31,
				42, 15,
				42, 7,
				42, 3,
				54, 0,
				ENV_END, 0
		},
		new int[] {
				54, 0,
				ENV_END, 0
		}
	);

	private final AudioEngine engine;
	private final SoundLibrary library;
	private boolean attached;

	private final Map<Sequence, SfxProgram> programs = new IdentityHashMap<>();
	private final Map<String, int[]> pressEnvelopes = new HashMap<>();
	private final List<SfxTrackPlayer> trackPlayers = new ArrayList<>();
	private final List<SpawnedEffect> pendingSpawns = new ArrayList<>();

	private SfxArchive archive;
	private Sound selectedSound;
	private Sound currentSound;
	private boolean paused;
	private int frameCounter;
	private int randomValue;
	private int updateCounter;
	private int currentTime;
	private int duration;
	private int requestedTime;
	private int initialTrigger;
	private boolean trackLimitExceeded;

	public SfxPlayer(AudioEngine engine, SoundLibrary library)
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

	public void setArchive(SfxArchive archive)
	{
		stop();
		selectedSound = null;
		duration = 0;
		initialTrigger = TRIGGER_NONE;

		this.archive = archive;
		programs.clear();
		pressEnvelopes.clear();

		for (Envelope envelope : archive.envelopes) {
			byte[] bytes = EnvelopeProgram.encode(envelope.commands);
			int[] program = new int[bytes.length];
			for (int i = 0; i < bytes.length; i++)
				program[i] = bytes[i] & 0xFF;
			pressEnvelopes.put(envelope.name, program);
		}

		for (Sound sound : archive.sounds.values()) {
			compileTracks(sound.tracks);
			for (SpawnedEffect spawned : sound.spawnedEffects)
				compileTracks(spawned.tracks);
		}
	}

	private void compileTracks(List<Track> tracks)
	{
		for (Track track : tracks) {
			if (track.definition instanceof Sequence sequence && !programs.containsKey(sequence))
				programs.put(sequence, new SfxProgram(sequence));
		}
	}

	public void play(int soundID)
	{
		play(soundID, TRIGGER_NONE);
	}

	public void playAlternativeSound(int soundID)
	{
		play(soundID, TRIGGER_ALTERNATIVE_SOUND);
	}

	public void playAlternativeVolume(int soundID)
	{
		play(soundID, TRIGGER_ALTERNATIVE_VOLUME);
	}

	private void play(int soundID, int trigger)
	{
		stop();
		selectedSound = null;
		duration = 0;
		if (archive == null)
			return;

		Sound sound = archive.sounds.get(soundID);
		if (sound == null) {
			Logger.logfWarning("SFX %04X does not exist", soundID);
			return;
		}
		if (sound.isEmpty())
			return;

		selectedSound = sound;
		initialTrigger = trigger;
		engine.resetRenderState();
		duration = calculateDuration(sound);
		restart();
	}

	private int calculateDuration(Sound sound)
	{
		for (SfxProgram program : programs.values())
			program.resetTiming();

		beginPlayback(sound);
		while (currentSound != null && currentTime < MAX_TIMELINE_FRAMES)
			engine.renderFrame(AudioEngine.MIXER_BLOCK_TIME, true);

		int result = currentTime;
		if (currentSound != null) {
			Logger.logfWarning("SFX %s exceeded the ten-minute timeline limit", sound.name);
		}
		clearActivePlayback();
		return result;
	}

	private void beginPlayback(Sound sound)
	{
		clearActivePlayback();
		currentSound = sound;
		paused = false;
		frameCounter = 0;
		randomValue = 0;
		updateCounter = UPDATE_INTERVAL;
		currentTime = 0;
		requestedTime = -1;
		trackLimitExceeded = false;
		if (!startTracks(sound.tracks, true))
			return;
		setTrigger(initialTrigger);
	}

	@Override
	public void stop()
	{
		clearActivePlayback();
	}

	@Override
	public void restart()
	{
		if (selectedSound == null)
			return;
		engine.resetRenderState();
		beginPlayback(selectedSound);
		engine.resetRenderState();
	}

	private void clearActivePlayback()
	{
		for (SfxTrackPlayer player : trackPlayers)
			player.killVoice();

		trackPlayers.clear();
		pendingSpawns.clear();
		currentSound = null;
		paused = false;
		currentTime = 0;
		requestedTime = -1;
		trackLimitExceeded = false;
	}

	@Override
	public boolean isPlaying()
	{
		return currentSound != null;
	}

	@Override
	public boolean isPaused()
	{
		return paused;
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

	@Override
	public int getTimelineLoopCount()
	{
		int count = 0;
		for (SfxTrackPlayer player : trackPlayers)
			count = Math.max(count, player.timelineLoopCount);
		return count;
	}

	@Override
	public void seekTime(int seekTime)
	{
		if (selectedSound == null || getDuration() == 0)
			return;

		if (seekTime < 0 || seekTime >= getDuration())
			return;

		if (seekTime == getTime())
			return;

		boolean wasPaused = paused;
		beginPlayback(selectedSound);

		engine.prepareForSeek();
		while (getTime() < seekTime && currentSound != null)
			engine.renderFrame(AudioEngine.MIXER_BLOCK_TIME, true);

		for (SfxTrackPlayer player : trackPlayers)
			player.resumeNormalPlayback();

		if (wasPaused)
			setPaused(true);

		engine.finishSeek();
	}

	@Override
	public void setPaused(boolean paused)
	{
		if (currentSound == null || this.paused == paused)
			return;

		this.paused = paused;
		for (SfxTrackPlayer player : trackPlayers) {
			if (player.voice != null)
				player.voice.setPaused(paused);
		}
	}

	public void triggerAlternativeSound()
	{
		setTrigger(TRIGGER_ALTERNATIVE_SOUND);
	}

	public void triggerAlternativeVolume()
	{
		setTrigger(TRIGGER_ALTERNATIVE_VOLUME);
	}

	private void setTrigger(int trigger)
	{
		for (SfxTrackPlayer player : trackPlayers) {
			if (player.rootTrack)
				player.trigger = trigger;
		}
	}

	public int getActiveTrackCount()
	{
		return trackPlayers.size();
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

	@Override
	public void nextFrame(boolean fastForward)
	{
		if (currentSound == null || paused)
			return;

		frameCounter++;
		requestedTime = -1;
		updateCounter -= UPDATE_STEP;
		boolean updatePlayers = updateCounter <= 0;
		if (updatePlayers) {
			updateCounter += UPDATE_INTERVAL;
			randomValue = (randomValue & 0xFFFF) + (frameCounter & 0xFFFF);
		}

		if (updatePlayers && !pendingSpawns.isEmpty()) {
			List<SpawnedEffect> ready = new ArrayList<>(pendingSpawns);
			pendingSpawns.clear();
			for (SpawnedEffect spawned : ready) {
				if (!startTracks(spawned.tracks, false))
					return;
			}
		}

		if (updatePlayers) {
			for (SfxTrackPlayer player : trackPlayers)
				player.update(fastForward);
		}

		if (trackLimitExceeded) {
			Logger.logfError("SFX %s exceeded the active track limit", currentSound.name);
			clearActivePlayback();
			return;
		}

		Iterator<SfxTrackPlayer> iterator = trackPlayers.iterator();
		while (iterator.hasNext()) {
			if (iterator.next().done)
				iterator.remove();
		}

		if (trackPlayers.isEmpty() && pendingSpawns.isEmpty()) {
			currentSound = null;
		}
		else if (updatePlayers && !fastForward && requestedTime >= 0) {
			currentTime = requestedTime;
		}
		else {
			currentTime++;
		}
	}

	private void requestTime(int time)
	{
		if (time < 0)
			return;
		if (requestedTime < 0 || time < requestedTime)
			requestedTime = time;
	}

	private boolean startTracks(List<Track> tracks, boolean rootTrack)
	{
		for (Track track : tracks) {
			if (track.definition instanceof Empty)
				continue;
			if (trackPlayers.size() >= MAX_TRACK_PLAYERS) {
				Logger.logfError("SFX %s exceeded the active track limit", currentSound.name);
				clearActivePlayback();
				return false;
			}
			trackPlayers.add(new SfxTrackPlayer(track, rootTrack));
		}
		return true;
	}

	private final class SfxTrackPlayer
	{
		private final int slot;
		private final boolean rootTrack;
		private final OneShot oneShot;
		private final SfxProgram program;
		private final Set<Long> timingEdges = new HashSet<>();

		private Voice voice;
		private Instrument instrument;
		private Script envelope;
		private int[] customPressEnvelope;

		private boolean done;
		private boolean initialized;
		private boolean changedVolume;
		private boolean changedPan;
		private boolean changedReverb;
		private boolean changedTune;

		private int pos;
		private int returnPos;
		private int alternativePos = -1;
		private int trigger;
		private int loopStartPos = -1;
		private int loopCount;
		private int timelineLoopCount;
		private int delay = 1;
		private int playLength;

		private int pan = 64;
		private int reverb;
		private int coarseTune;
		private int fineTune;
		private int playVelocity = 127;
		private int randomPitch;
		private int randomVelocity;

		private float sfxVolume = 1.0f;
		private float alternativeVolume = 1.0f;
		private final Lerp volumeLerp = new Lerp(1.0f);
		private final Lerp tuneLerp = new Lerp(0.0f);

		private final boolean lockReverb;

		private SfxTrackPlayer(Track track, boolean rootTrack)
		{
			this.slot = track.slot;
			this.rootTrack = rootTrack;

			Definition definition = track.definition;
			if (definition instanceof OneShot value) {
				oneShot = value;
				program = null;
				lockReverb = value.lockReverb;
			}
			else if (definition instanceof Sequence value) {
				oneShot = null;
				program = programs.get(value);
				lockReverb = value.lockReverb;
				pos = program.entryPos;
				returnPos = program.entryPos;
			}
			else {
				throw new IllegalArgumentException("Unknown SFX track definition: " + definition);
			}
		}

		private void update(boolean fastForward)
		{
			if (oneShot != null)
				updateOneShot(fastForward);
			else
				updateSequence(fastForward);
		}

		private void updateOneShot(boolean fastForward)
		{
			if (!initialized) {
				initialized = true;
				setInstrument(oneShot.bank, oneShot.patch);
				if (instrument == null) {
					missingInstrument();
					done = true;
					return;
				}

				sfxVolume = volume16(oneShot.volume);
				pan = oneShot.pan;
				reverb = lockReverb ? 0 : oneShot.reverb;
				tuneLerp.current = randomPitch(randomValue, oneShot.randomPitch, oneShot.pitch * 100);
				startVoice(fastForward);
				return;
			}

			if (voice == null || voice.isDone())
				done = true;
		}

		private void updateSequence(boolean fastForward)
		{
			boolean startedNewVoice = false;

			if (alternativePos >= 0 && trigger == TRIGGER_ALTERNATIVE_SOUND) {
				pos = alternativePos;
				if (!fastForward)
					requestTime(program.getStartTime(pos));
				alternativePos = -1;
				trigger = 0;
				delay = 1;
			}
			if (trigger == TRIGGER_ALTERNATIVE_VOLUME) {
				sfxVolume = alternativeVolume;
				changedVolume = true;
			}

			delay--;
			int executed = 0;
			while (delay == 0 && !done) {
				if (pos < 0 || pos >= program.instructions.length) {
					Logger.logfError("SFX %s track %d ran past the end of its program",
						currentSound.name, slot);
					finish();
					return;
				}
				if (++executed > MAX_COMMANDS_PER_FRAME) {
					Logger.logfError("SFX %s track %d exceeded the per-frame command limit",
						currentSound.name, slot);
					finish();
					return;
				}

				Instruction instruction = program.instructions[pos++];
				if (fastForward && instruction.startTime < 0)
					instruction.startTime = currentTime;
				if (execute(instruction, fastForward))
					startedNewVoice = true;
			}

			if (done)
				return;

			if (volumeLerp.update())
				changedVolume = true;

			if (!startedNewVoice) {
				if (playLength != 0) {
					playLength--;
					if (playLength == 0 && voice != null && !voice.isDone())
						voice.release();
				}
				if (tuneLerp.update())
					changedTune = true;

				if ((changedPan || changedReverb) && voice != null && !voice.isDone()) {
					voice.setPan(pan);
					voice.setReverb(reverb);
				}
			}

			if (changedVolume && voice != null && !voice.isDone())
				updateVoiceVolume();
			if (changedTune && voice != null && !voice.isDone())
				updateVoicePitch();

			changedVolume = false;
			changedPan = false;
			changedReverb = false;
			changedTune = false;
		}

		private boolean execute(Instruction instruction, boolean fastForward)
		{
			Command command = instruction.command;
			switch (command.op) {
				case END:
					finish();
					break;
				case DELAY:
					delay = command.a;
					break;
				case PLAY:
					tuneLerp.current = randomPitch != 0
						? randomPitch(randomValue, randomPitch, command.a * 100)
						: command.a * 100;
					playVelocity = randomVelocity != 0
						? randomVelocity(randomValue, randomVelocity, command.b)
						: command.b;
					playLength = command.c;
					startVoice(fastForward);
					changedTune = true;
					return true;
				case SET_VOLUME:
					sfxVolume = volume16(command.a);
					changedVolume = true;
					break;
				case SET_PAN:
					pan = command.a;
					changedPan = true;
					break;
				case SET_INSTRUMENT:
					setInstrument(command.a, command.b);
					break;
				case SET_REVERB:
					reverb = lockReverb ? 0 : command.a;
					changedReverb = true;
					break;
				case SET_ENVELOPE:
					setEnvelopePreset(command.a);
					break;
				case COARSE_TUNE:
					coarseTune = command.a * 100;
					break;
				case FINE_TUNE:
					fineTune = command.a;
					break;
				case WAIT_FOR_END:
					if (voice != null && !voice.isDone()) {
						delay = 2;
						pos--;
					}
					break;
				case PITCH_SWEEP:
					tuneLerp.set(command.a, command.b * 100.0f);
					break;
				case START_LOOP:
					loopStartPos = pos;
					loopCount = command.a;
					break;
				case END_LOOP:
					if (loopStartPos < 0) {
						Logger.logfError("SFX %s track %d reached EndLoop without StartLoop",
							currentSound.name, slot);
						finish();
					}
					else if (fastForward) {
						loopStartPos = -1;
						loopCount = 0;
						if (pos == program.instructions.length)
							finish();
					}
					else if (loopCount == 0 || --loopCount != 0) {
						if (loopCount == 0)
							timelineLoopCount++;
						pos = loopStartPos;
						requestTime(program.getStartTime(pos));
					}
					break;
				case WAIT_FOR_RELEASE:
					if (playLength != 0) {
						delay = 3;
						pos--;
					}
					break;
				case SET_CURRENT_VOLUME:
					volumeLerp.current = volume32(command.a);
					changedVolume = true;
					break;
				case VOLUME_RAMP:
					volumeLerp.set(command.a, volume16(command.b));
					break;
				case SET_ALTERNATIVE:
					alternativePos = instruction.targetPos;
					break;
				case STOP:
					killVoice();
					break;
				case JUMP:
					if (!fastForward || followTimingEdge(pos - 1, instruction.targetPos)) {
						returnPos = pos;
						pos = instruction.targetPos;
						if (!fastForward && program.getStartTime(pos) < currentTime)
							requestTime(program.getStartTime(pos));
					}
					break;
				case RESTART:
					if (!fastForward || followTimingEdge(pos - 1, returnPos)) {
						pos = returnPos;
						if (!fastForward && program.getStartTime(pos) < currentTime)
							requestTime(program.getStartTime(pos));
					}
					break;
				case NOP:
					break;
				case SET_RANDOM_PITCH:
					randomPitch = command.a;
					break;
				case SET_RANDOM_VELOCITY:
					randomVelocity = command.a;
					break;
				case SET_RANDOM_PAN:
					break;
				case SET_PRESS_ENVELOPE:
					customPressEnvelope = command.ref == null ? null : pressEnvelopes.get(command.ref);
					break;
				case SPAWN:
					queueSpawn(command.ref);
					break;
				case SET_ALTERNATIVE_VOLUME:
					alternativeVolume = volume16(command.a);
					break;
			}
			return false;
		}

		private boolean followTimingEdge(int sourcePos, int targetPos)
		{
			long edge = ((long) sourcePos << 32) | (targetPos & 0xFFFFFFFFL);
			return timingEdges.add(edge);
		}

		private void setInstrument(int bankID, int patch)
		{
			instrument = library.getInstrument(bankID, patch);
			envelope = instrument == null ? null : instrument.getEnvelope(bankID & 3);
		}

		private void setEnvelopePreset(int preset)
		{
			int index = preset & 0x7F;
			if (index == 0) {
				envelope = SFX_ENVELOPE_FAST;
			}
			else if (index < 16) {
				envelope = SFX_ENVELOPE_SLOW;
			}
			else {
				Logger.logfWarning("SFX %s track %d uses invalid envelope preset %d",
					currentSound.name, slot, index);
			}
		}

		private void queueSpawn(String name)
		{
			for (SpawnedEffect spawned : currentSound.spawnedEffects) {
				if (spawned.name.equals(name)) {
					if (trackPlayers.size() + pendingSpawns.size() >= MAX_TRACK_PLAYERS)
						trackLimitExceeded = true;
					else
						pendingSpawns.add(spawned);
					return;
				}
			}
			Logger.logfError("SFX %s references missing spawned effect %s", currentSound.name, name);
		}

		private void startVoice(boolean fastForward)
		{
			killVoice();
			if (instrument == null) {
				missingInstrument();
				return;
			}

			voice = new Voice();
			engine.addVoice(voice);
			voice.setInstrument(instrument);
			voice.setLoopingAllowed(!fastForward);
			if (envelope != null) {
				Script selectedEnvelope = customPressEnvelope == null
					? envelope
					: envelope.withPress(customPressEnvelope);
				voice.setEnvelope(selectedEnvelope);
			}
			voice.setPan(pan);
			voice.setReverb(reverb);
			updateVoiceVolume();
			updateVoicePitch();
			voice.play();
		}

		private void resumeNormalPlayback()
		{
			if (voice != null)
				voice.setLoopingAllowed(true);
		}

		private void updateVoiceVolume()
		{
			float velocity = playVelocity / 127.0f;
			voice.setVolume(sfxVolume * velocity * volumeLerp.current);
		}

		private void updateVoicePitch()
		{
			int detune = Math.round(tuneLerp.current) + coarseTune + fineTune - instrument.keyBase;
			voice.setPitch(AudioEngine.detuneToPitchRatio(detune));
		}

		private void missingInstrument()
		{
			Logger.logfWarning("SFX %s track %d tried to play without an instrument",
				currentSound.name, slot);
		}

		private void finish()
		{
			killVoice();
			done = true;
		}

		private void killVoice()
		{
			if (voice != null) {
				voice.kill();
				voice = null;
			}
		}
	}

	private static final class Lerp
	{
		private float current;
		private float step;
		private float goal;
		private int time;

		private Lerp(float current)
		{
			this.current = current;
		}

		private void set(int time, float goal)
		{
			if (time <= 0)
				time = 1;
			this.time = time;
			this.goal = goal;
			step = (goal - current) / time;
		}

		private boolean update()
		{
			if (time == 0)
				return false;

			time--;
			if (time != 0)
				current += step;
			else
				current = goal;
			return true;
		}
	}

	private static final class SfxProgram
	{
		private final Instruction[] instructions;
		private final int entryPos;

		private SfxProgram(Sequence sequence)
		{
			Map<String, Integer> labels = new HashMap<>();
			List<Command> commands = new ArrayList<>();
			for (Node node : sequence.nodes) {
				if (node instanceof Label label)
					labels.put(label.name(), commands.size());
				else
					commands.add((Command) node);
			}

			Integer entryPos = labels.get(Sequence.START_LABEL);
			if (entryPos == null)
				throw new IllegalArgumentException("Missing SFX start label");
			this.entryPos = entryPos;

			instructions = new Instruction[commands.size()];
			for (int i = 0; i < commands.size(); i++) {
				Command command = commands.get(i);
				int targetPos = -1;
				if (command.op == Op.JUMP || command.op == Op.SET_ALTERNATIVE) {
					Integer resolved = labels.get(command.ref);
					if (resolved == null)
						throw new IllegalArgumentException("Missing SFX label: " + command.ref);
					targetPos = resolved;
				}
				instructions[i] = new Instruction(command, targetPos);
			}
		}

		private int getStartTime(int pos)
		{
			if (pos < 0 || pos >= instructions.length)
				return -1;
			return instructions[pos].startTime;
		}

		private void resetTiming()
		{
			for (Instruction instruction : instructions)
				instruction.startTime = -1;
		}
	}

	private static final class Instruction
	{
		private final Command command;
		private final int targetPos;
		private int startTime = -1;

		private Instruction(Command command, int targetPos)
		{
			this.command = command;
			this.targetPos = targetPos;
		}
	}

	private static float volume16(int value)
	{
		if (value == 0)
			return 0.0f;
		return (((value & 0xFF) << 8) | 0xFF) / 32767.0f;
	}

	private static float volume32(int value)
	{
		if (value == 0)
			return 0.0f;
		long expanded = ((long) (value & 0xFF) << 24) | 0xFFFFFFL;
		return expanded / 2147483647.0f;
	}

	private static int randomPitch(int seed, int amplitude, int pitch)
	{
		int lo = (seed >> 7) & 0xF;
		int hi = (seed << 3) & 0xF0;
		int random = lo + hi;
		int parity = ((seed >> 5) + (seed >> 2)) & 1;
		int delta = (amplitude * 5 * random) >> 8;
		return parity != 0 ? pitch + delta : pitch - delta;
	}

	private static int randomVelocity(int seed, int amplitude, int volume)
	{
		int lo = (seed & 0xCC) >> 2;
		int hi = (seed & 0x13) << 2;
		int random = lo + hi;
		return volume * (0x8000 - amplitude * random) >> 15;
	}
}
