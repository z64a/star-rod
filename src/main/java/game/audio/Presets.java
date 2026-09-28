package game.audio;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.input.InputFileException;
import game.audio.AudioTables.Drum;
import game.audio.AudioTables.Key;
import game.audio.AudioTables.Preset;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

/**
 * The PRG file (instrument presets) and the PER file (drums). Both refer to instruments by bank and
 * patch bytes; in XML they name the sample, and the banks listed in Banks.xml say where it is.
 */
public abstract class Presets
{
	private static final int HEADER_SIZE = 0x10;
	private static final int PRESET_SIZE = 8;
	private static final int DRUM_SIZE = 12;
	private static final int DRUMS_PER_SET = 12;

	/** The bank byte and patch byte of the instrument the engine uses when a slot is empty. */
	private static final int DEFAULT_BANK = 0x20;
	private static final int DEFAULT_PATCH = 0x10;

	/** The instruments of the banks listed in Banks.xml, by sample name and by address. */
	public static class Addresses
	{
		private final Map<String, Integer> byName = new HashMap<>();
		private final Map<Integer, String> byAddress = new HashMap<>();

		/** Banks maps bank file names to the banks that are built from sources. */
		public Addresses(List<AudioTables.Bank> table, Map<String, SoundBank> banks)
		{
			for (AudioTables.Bank entry : table) {
				SoundBank bank = banks.get(entry.file());
				if (bank == null)
					continue;

				int set = entry.group() == 2 ? 1 : entry.group();
				for (int i = 0; i < bank.instruments.size(); i++) {
					int address = set << 8 | entry.index() << 4 | i;
					byName.put(bank.instruments.get(i).wav, address);
					byAddress.put(address, bank.instruments.get(i).wav);
				}
			}
		}

		int[] address(File source, String user, String wav, int envelope)
		{
			Integer address = byName.get(wav);
			if (address == null)
				throw new InputFileException(source, "%s uses %s, which is not an instrument of a bank in Banks.xml", user,
					wav);
			return new int[] { address >> 4 & 0xF0 | envelope, address & 0xFF };
		}

		String name(File source, String user, int bank, int patch)
		{
			String wav = byAddress.get((bank >> 4 & 7) << 8 | patch);
			if (wav == null)
				throw new InputFileException(source, "%s uses bank %02X patch %02X, which no bank in Banks.xml provides", user,
					bank, patch);
			return wav;
		}
	}

	public static byte[] writePresets(Preset[] presets, Addresses addresses)
	{
		ByteBuffer bb = header("PRG ", HEADER_SIZE + PRESET_SIZE * presets.length);
		for (Preset p : presets) {
			if (p == null) {
				bb.put((byte) DEFAULT_BANK).put((byte) DEFAULT_PATCH).put((byte) AudioTables.DEFAULT_VOLUME);
				bb.put((byte) AudioTables.DEFAULT_PAN).position(bb.position() + 4);
				continue;
			}

			int[] address = addresses.address(p.source(), "preset " + p.name(), p.wav(), p.envelope());
			bb.put((byte) address[0]).put((byte) address[1]).put((byte) p.volume()).put((byte) p.pan());
			bb.put((byte) p.reverb()).put((byte) p.coarseTune()).put((byte) p.fineTune()).put((byte) 0);
		}
		return bb.array();
	}

	public static byte[] writeDrums(Drum[] drums, Addresses addresses)
	{
		int count = (drums.length + DRUMS_PER_SET - 1) / DRUMS_PER_SET * DRUMS_PER_SET;
		ByteBuffer bb = header("PER ", HEADER_SIZE + DRUM_SIZE * count);
		for (int i = 0; i < count; i++) {
			Drum d = i < drums.length ? drums[i] : null;
			if (d == null) {
				bb.put((byte) DEFAULT_BANK).put((byte) DEFAULT_PATCH).putShort((short) AudioTables.DEFAULT_KEY_BASE);
				bb.put((byte) AudioTables.DEFAULT_VOLUME).put((byte) AudioTables.DEFAULT_PAN).position(bb.position() + 4);
				continue;
			}

			int[] address = addresses.address(d.source(), "drum " + d.name(), d.wav(), d.envelope());
			bb.put((byte) address[0]).put((byte) address[1]).putShort((short) d.keyBase());
			bb.put((byte) d.volume()).put((byte) d.pan()).put((byte) d.reverb()).put((byte) d.randTune());
			bb.put((byte) d.randVolume()).put((byte) d.randPan()).put((byte) d.randReverb()).put((byte) 0);
		}
		return bb.array();
	}

	private static ByteBuffer header(String type, int size)
	{
		ByteBuffer bb = ByteBuffer.allocate(size);
		bb.put(type.getBytes(StandardCharsets.US_ASCII)).putInt(size);
		bb.put("SET1".getBytes(StandardCharsets.US_ASCII)).position(HEADER_SIZE);
		return bb;
	}

	/** Slots the engine fills itself have no sample to name. */
	private static boolean isDefault(byte[] data, int at)
	{
		return (data[at] & 0xFF) == DEFAULT_BANK && (data[at + 1] & 0xFF) == DEFAULT_PATCH;
	}

	private static void addInstrument(XmlWriter xmw, XmlTag tag, String wav, int bank)
	{
		xmw.addAttribute(tag, Key.wav, wav);
		if ((bank & 3) != 0)
			xmw.addInt(tag, Key.envelope, bank & 3);
	}

	public static void dumpPresets(File source, byte[] data, Addresses addresses, File out) throws IOException
	{
		try (XmlWriter xmw = new XmlWriter(out)) {
			XmlTag root = xmw.createTag(Key.Instruments, false);
			xmw.openTag(root);
			for (int id = 0; (id + 1) * PRESET_SIZE + HEADER_SIZE <= data.length; id++) {
				int at = HEADER_SIZE + id * PRESET_SIZE;
				if (isDefault(data, at))
					continue;
				XmlTag tag = xmw.createTag(Key.Instrument, true);
				xmw.addAttribute(tag, Key.name, String.format("Preset%02X", id));
				xmw.addHex(tag, Key.id, "%02X", id);
				addInstrument(xmw, tag, addresses.name(source, "preset " + id, data[at] & 0xFF, data[at + 1] & 0xFF), data[at]);
				AudioXml.addHex(xmw, tag, Key.volume, 2, data[at + 2] & 0xFF);
				xmw.addInt(tag, Key.pan, data[at + 3]);
				xmw.addInt(tag, Key.reverb, data[at + 4] & 0xFF);
				xmw.addInt(tag, Key.coarseTune, data[at + 5]);
				xmw.addInt(tag, Key.fineTune, data[at + 6]);
				xmw.printTag(tag);
			}
			xmw.closeTag(root);
			xmw.save();
		}
	}

	public static void dumpDrums(File source, byte[] data, Addresses addresses, File out) throws IOException
	{
		ByteBuffer bb = ByteBuffer.wrap(data);
		try (XmlWriter xmw = new XmlWriter(out)) {
			XmlTag root = xmw.createTag(Key.Drums, false);
			xmw.openTag(root);
			for (int id = 0; (id + 1) * DRUM_SIZE + HEADER_SIZE <= data.length; id++) {
				int at = HEADER_SIZE + id * DRUM_SIZE;
				if (isDefault(data, at))
					continue;
				XmlTag tag = xmw.createTag(Key.Drum, true);
				xmw.addAttribute(tag, Key.name, String.format("Drum%02X", id));
				xmw.addHex(tag, Key.id, "%02X", id);
				addInstrument(xmw, tag, addresses.name(source, "drum " + id, data[at] & 0xFF, data[at + 1] & 0xFF), data[at]);
				xmw.addInt(tag, Key.keyBase, bb.getShort(at + 2) & 0xFFFF);
				AudioXml.addHex(xmw, tag, Key.volume, 2, data[at + 4] & 0xFF);
				xmw.addInt(tag, Key.pan, data[at + 5]);
				xmw.addInt(tag, Key.reverb, data[at + 6] & 0xFF);
				if (data[at + 7] != 0)
					xmw.addInt(tag, Key.randTune, data[at + 7] & 0xFF);
				if (data[at + 8] != 0)
					AudioXml.addHex(xmw, tag, Key.randVolume, 2, data[at + 8] & 0xFF);
				if (data[at + 9] != 0)
					xmw.addInt(tag, Key.randPan, data[at + 9] & 0xFF);
				if (data[at + 10] != 0)
					xmw.addInt(tag, Key.randReverb, data[at + 10] & 0xFF);
				xmw.printTag(tag);
			}
			xmw.closeTag(root);
			xmw.save();
		}
	}
}
