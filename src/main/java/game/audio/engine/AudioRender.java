package game.audio.engine;

import java.io.File;
import java.io.IOException;

import app.input.InputFileException;
import game.audio.SfxArchive;

/** Renders a sound to a WAV file through the same engine the Audio Booth plays with, without an audio device. */
public class AudioRender
{
	private static final double MAX_SECONDS = 5.0;

	private AudioRender()
	{}

	/**
	 * Renders a sound effect (by name or hexadecimal ID), an ambient sequence, or a bank sample until it ends
	 * or five seconds have passed.
	 */
	public static void render(String kind, String name, File out) throws IOException
	{
		SoundLibrary library = new SoundLibrary();

		try (WavWriter writer = new WavWriter(out)) {
			AudioEngine engine = new AudioEngine(writer);
			PlaybackSession session = switch (kind.toLowerCase()) {
				case "sfx" -> playSfx(engine, library, name);
				case "mseq" -> playMseq(engine, library, name);
				case "sample" -> playSample(engine, library, name);
				default -> throw new InputFileException(out, "the kind of sound must be sfx, mseq, or sample: %s", kind);
			};

			int frames = (int) Math.ceil(MAX_SECONDS * AudioEngine.TARGET_FPS);
			for (int i = 0; i < frames && (session.isPlaying() || engine.hasActiveVoices()); i++)
				engine.renderFrame(AudioEngine.FRAME_TIME, false);
			session.close();
		}
	}

	private static PlaybackSession playSfx(AudioEngine engine, SoundLibrary library, String name)
	{
		SfxArchive archive = library.readSfx();
		Integer id = parseId(name);
		SfxArchive.Sound sound = archive.sounds.values().stream().filter(s -> s.name.equals(name)).findFirst()
			.orElseGet(() -> id == null ? null : archive.sounds.get(id));
		if (sound == null)
			throw new InputFileException(new File("sound_effects.xml"), "there is no sound effect named %s", name);

		SfxPlayer player = new SfxPlayer(engine, library);
		player.attach();
		player.setArchive(archive);
		player.play(sound.id);
		return player;
	}

	private static Integer parseId(String text)
	{
		try {
			return Integer.parseInt(text.replaceFirst("^0[xX]", ""), 16);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static PlaybackSession playMseq(AudioEngine engine, SoundLibrary library, String name)
	{
		MseqPlayer player = new MseqPlayer(engine, library);
		player.attach();
		player.setMseq(library.readMseq(name), name);
		return player;
	}

	private static PlaybackSession playSample(AudioEngine engine, SoundLibrary library, String name)
	{
		Instrument instrument = library.getInstrument(name);
		if (instrument == null)
			throw new InputFileException(new File("bank"), "there is no sample named %s", name);

		SamplePlayer player = new SamplePlayer(engine);
		player.play(instrument, instrument.getEnvelope(0));
		return player;
	}
}
