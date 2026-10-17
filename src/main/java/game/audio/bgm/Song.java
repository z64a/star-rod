package game.audio.bgm;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.input.InputFileException;
import game.audio.AudioTables.Drum;
import game.audio.AudioTables.Preset;
import game.audio.Presets;

/** A BGM binary decoded for playback, with its own auxiliary instrument banks. */
public final class Song
{
	public static final int NUM_COMPOSITIONS = 4;
	public static final int MAX_BRANCH_OPTIONS = 16;
	private static final int[] TICKS_PER_BEAT = { 48, 24, 32, 40, 48, 56, 64, 48 };

	public final String name;
	public final String code;
	public final int branchTicks = 96;
	public int branchOptions = 1;
	private final int timingPreset;
	private final File source;
	private final Presets.Addresses addresses;
	private final Composition[] compositions = new Composition[NUM_COMPOSITIONS];
	private final Map<Integer, Phrase> phrases = new HashMap<>();
	private final List<Preset> instruments = new ArrayList<>();
	private final List<Drum> drums = new ArrayList<>();
	private final List<Preset> instrumentView = Collections.unmodifiableList(instruments);
	private final List<Drum> drumView = Collections.unmodifiableList(drums);

	public Song(File source, byte[] data, Presets.Addresses addresses)
	{
		this.source = source;
		this.addresses = addresses;
		name = source.getName();
		if (data.length < 0x24)
			throw new InputFileException(source, "BGM header is truncated");
		ByteBuffer bb = ByteBuffer.wrap(data);
		if (bb.getInt() != 0x42474D20)
			throw new InputFileException(source, "not a BGM file");
		int length = bb.getInt();
		if (length < 0x24 || length > data.length)
			throw new InputFileException(source, "invalid BGM length: %X", length);
		bb.limit(length);
		code = new String(data, 8, 4, StandardCharsets.US_ASCII).trim();
		timingPreset = data[0x10] & 7;
		try {
			int drumOffset = (bb.getShort(0x1C) & 0xFFFF) << 2;
			int drumCount = bb.getShort(0x1E) & 0xFFFF;
			int instrumentOffset = (bb.getShort(0x20) & 0xFFFF) << 2;
			int instrumentCount = bb.getShort(0x22) & 0xFFFF;
			bb.position(drumOffset);
			for (int i = 0; i < drumCount; i++)
				drums.add(Presets.readDrum(source, bb, i, addresses));
			bb.position(instrumentOffset);
			for (int i = 0; i < instrumentCount; i++)
				instruments.add(Presets.readPreset(source, bb, i, addresses));
			for (int i = 0; i < compositions.length; i++) {
				int offset = (bb.getShort(0x14 + 2 * i) & 0xFFFF) << 2;
				if (offset != 0) {
					compositions[i] = new Composition(this, bb, i, offset);
				}
			}
		}
		catch (RuntimeException e) {
			if (e instanceof InputFileException)
				throw e;
			throw new InputFileException(source, "invalid BGM data near %X: %s", bb.position(), e.getMessage());
		}
	}

	Phrase addPhrase(ByteBuffer bb, int offset)
	{
		Phrase phrase = phrases.get(offset);
		if (phrase == null) {
			phrase = new Phrase(this, bb, offset);
			phrases.put(offset, phrase);
		}
		return phrase;
	}

	String resolveInstrument(int bank, int patch)
	{
		return (bank >> 4 & 7) == 2 ? null : addresses.name(source, "BGM", bank, patch);
	}

	public Composition getComposition(int index)
	{
		return index >= 0 && index < compositions.length ? compositions[index] : null;
	}

	public int getTicksPerBeat()
	{
		return TICKS_PER_BEAT[timingPreset];
	}

	public List<Preset> getInstruments()
	{
		return instrumentView;
	}

	public List<Drum> getDrums()
	{
		return drumView;
	}
}
