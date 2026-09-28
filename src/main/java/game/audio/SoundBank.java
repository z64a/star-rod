package game.audio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import app.input.InputFileException;
import game.audio.Vadpcm.CodeBook;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

/**
 * A sound bank: up to 16 instruments and the envelopes they share. On disk it is a directory with
 * SoundBank.xml and a mono WAV file for each sample, and in the archive it is a BK file.
 *
 * An instrument that has a loop uses two WAV files, the attack and the loop. The instrument keeps the
 * codebook, trailing samples, and padding of the file it was dumped from in its Encoding tag. While the
 * audio still decodes exactly with that codebook, the original bytes are rebuilt from it; otherwise the
 * samples are encoded with a new codebook.
 */
public class SoundBank
{
	public enum Key implements XmlKey
	{
		// @formatter:off
		SoundBank, Instruments, Instrument, Envelopes, Encoding,
		wav, loop, loopCount, envName, keyBase, numPred, codebook, tail, padding
		// @formatter:on
	}

	public static final String XML_FILE = "SoundBank.xml";
	public static final int MAX_INSTRUMENTS = 16;
	public static final int LOOP_FOREVER = -1;

	private static final int HEADER_SIZE = 0x40;
	private static final int INSTRUMENT_SIZE = 0x30;
	private static final int LOOP_STATE_SIZE = 0x20;
	private static final int CODEBOOK_UNIT = 0x20;

	public static class Instrument
	{
		public String wav;
		public String loopWav;
		public int loopCount = LOOP_FOREVER;
		public String envName;
		public int keyBase;
		public int numPred = 1;

		public int sampleRate;
		public short[] attack;
		public short[] loop;

		public CodeBook codebook;
		public short[] tail = new short[0];
		public int padding;

		private Envelope envelope;

		private short[] samples()
		{
			short[] all = Arrays.copyOf(attack, attack.length + (loop == null ? 0 : loop.length));
			if (loop != null)
				System.arraycopy(loop, 0, all, attack.length, loop.length);
			return all;
		}

		/** Every decoded sample: the attack, the loop, and the frames after the loop. */
		public short[] decoded()
		{
			short[] all = samples();
			short[] full = Arrays.copyOf(all, all.length + tail.length);
			System.arraycopy(tail, 0, full, all.length, tail.length);
			return full;
		}

		private int loopStart()
		{
			return loop == null ? 0 : attack.length;
		}

		private int loopEnd()
		{
			return loop == null ? 0 : attack.length + loop.length;
		}
	}

	public final String name;
	public final List<Instrument> instruments = new ArrayList<>();
	public final List<Envelope> envelopes = new ArrayList<>();

	private SoundBank(String name)
	{
		this.name = name;
	}

	/** Reads a BK file, naming the samples after the bank. */
	public static SoundBank read(File source, String name, byte[] data)
	{
		try {
			return read(name, data);
		}
		catch (IllegalArgumentException | IndexOutOfBoundsException e) {
			throw new InputFileException(source, "invalid sound bank: %s", e.getMessage());
		}
	}

	private static SoundBank read(String name, byte[] data)
	{
		ByteBuffer bb = ByteBuffer.wrap(data);
		SoundBank bank = new SoundBank(name);
		Map<Integer, Envelope> envelopeAt = new TreeMap<>();

		int count = 0;
		while (count < MAX_INSTRUMENTS && bb.getShort(0x12 + 2 * count) != 0)
			count++;
		for (int i = count; i < MAX_INSTRUMENTS; i++) {
			if (bb.getShort(0x12 + 2 * i) != 0)
				throw new IllegalArgumentException("instrument " + count + " is missing");
		}

		for (int i = 0; i < count; i++) {
			int at = bb.getShort(0x12 + 2 * i) & 0xFFFF;

			Instrument ins = new Instrument();
			int wavOffset = bb.getInt(at);
			int wavLength = bb.getInt(at + 4);
			int loopStart = bb.getInt(at + 0x0C);
			int loopEnd = bb.getInt(at + 0x10);
			boolean hasLoop = loopEnd != 0;
			ins.loopCount = bb.getInt(at + 0x14);
			int predictorOffset = bb.getInt(at + 0x18);
			int predictorSize = bb.getShort(at + 0x1C) & 0xFFFF;
			ins.keyBase = bb.getShort(at + 0x1E);
			ins.sampleRate = bb.getInt(at + 0x20);

			int frames = wavLength / Vadpcm.FRAME_BYTES;
			ins.padding = wavLength % Vadpcm.FRAME_BYTES;
			ins.numPred = Math.max(1, predictorSize / CODEBOOK_UNIT);
			ins.codebook = new CodeBook(Arrays.copyOfRange(data, predictorOffset, predictorOffset + predictorSize));
			short[] samples = Vadpcm.decode(data, wavOffset, frames, ins.codebook);

			String raw = String.format("%s_%02X", name, i);
			ins.wav = AudioNames.getSampleName(raw);
			if (hasLoop) {
				if (loopStart > loopEnd || loopEnd > samples.length)
					throw new IllegalArgumentException("instrument " + i + " has a loop outside its samples");
				ins.loopWav = AudioNames.getSampleName(raw + "_Loop");
				ins.attack = Arrays.copyOfRange(samples, 0, loopStart);
				ins.loop = Arrays.copyOfRange(samples, loopStart, loopEnd);
				ins.tail = Arrays.copyOfRange(samples, loopEnd, samples.length);
			}
			else {
				ins.attack = samples;
			}

			int envelopeOffset = bb.getInt(at + 0x2C);
			if (!envelopeAt.containsKey(envelopeOffset))
				envelopeAt.put(envelopeOffset, Envelope.read(data, envelopeOffset));
			ins.envelope = envelopeAt.get(envelopeOffset);
			bank.instruments.add(ins);
		}

		bank.envelopes.addAll(envelopeAt.values());
		for (int i = 0; i < bank.envelopes.size(); i++)
			bank.envelopes.get(i).name = "env" + (i + 1);
		for (Instrument ins : bank.instruments)
			ins.envName = ins.envelope.name;
		return bank;
	}

	/** Writes SoundBank.xml and the WAV files into a directory. */
	public void dump(File dir) throws IOException
	{
		for (Instrument ins : instruments) {
			new Wav(ins.sampleRate, ins.attack).write(new File(dir, ins.wav + ".wav"));
			if (ins.loop != null)
				new Wav(ins.sampleRate, ins.loop).write(new File(dir, ins.loopWav + ".wav"));
		}

		try (XmlWriter xmw = new XmlWriter(new File(dir, XML_FILE))) {
			XmlTag root = xmw.createTag(Key.SoundBank, false);
			xmw.openTag(root);

			XmlTag list = xmw.createTag(Key.Instruments, false);
			xmw.openTag(list);
			for (Instrument ins : instruments) {
				XmlTag tag = xmw.createTag(Key.Instrument, false);
				xmw.addAttribute(tag, Key.wav, ins.wav);
				if (ins.loop != null) {
					xmw.addAttribute(tag, Key.loop, ins.loopWav);
					if (ins.loopCount != LOOP_FOREVER)
						xmw.addInt(tag, Key.loopCount, ins.loopCount);
				}
				xmw.addAttribute(tag, Key.envName, ins.envName);
				xmw.addInt(tag, Key.keyBase, ins.keyBase);
				if (ins.numPred > 1)
					xmw.addInt(tag, Key.numPred, ins.numPred);
				xmw.openTag(tag);

				XmlTag encoding = xmw.createTag(Key.Encoding, true);
				xmw.addAttribute(encoding, Key.codebook, ins.codebook.toHex());
				if (ins.tail.length > 0)
					xmw.addAttribute(encoding, Key.tail, hex(ins.tail));
				if (ins.padding > 0)
					xmw.addInt(encoding, Key.padding, ins.padding);
				xmw.printTag(encoding);
				xmw.closeTag(tag);
			}
			xmw.closeTag(list);

			XmlTag envs = xmw.createTag(Key.Envelopes, false);
			xmw.openTag(envs);
			for (Envelope env : envelopes)
				env.toXml(xmw);
			xmw.closeTag(envs);

			xmw.closeTag(root);
			xmw.save();
		}
	}

	private static String hex(short[] samples)
	{
		ByteBuffer bb = ByteBuffer.allocate(samples.length * 2);
		bb.asShortBuffer().put(samples);
		return HexFormat.of().formatHex(bb.array());
	}

	private static short[] unhex(String text)
	{
		byte[] bytes = HexFormat.of().parseHex(text);
		short[] samples = new short[bytes.length / 2];
		ByteBuffer.wrap(bytes).asShortBuffer().get(samples);
		return samples;
	}

	/** Reads bank/NAME/SoundBank.xml and its WAV files, each from the highest layer that has it. */
	public static SoundBank load(AudioLayers layers, String name)
	{
		File xmlFile = layers.getFile(directory(name) + XML_FILE);
		if (xmlFile == null)
			throw new InputFileException(new File(directory(name)), "Bank '%s': there is no %s%s file in any layer", name,
				directory(name), XML_FILE);

		if (name.length() > 4)
			throw new InputFileException(xmlFile, "a bank name must be 4 characters or fewer: %s", name);

		XmlReader xmr = new XmlReader(xmlFile);
		Element root = xmr.getRootElement();
		if (!root.getTagName().equals(Key.SoundBank.toString()))
			xmr.complain("expected root tag " + Key.SoundBank);

		SoundBank bank = new SoundBank(name);
		Map<String, Envelope> byName = new HashMap<>();
		for (Element elem : xmr.getTags(xmr.getUniqueRequiredTag(root, Key.Envelopes), Envelope.Key.Envelope)) {
			Envelope env = Envelope.fromXml(xmr, elem);
			if (byName.put(env.name, env) != null)
				xmr.complain("envelope " + env.name + " is listed more than once");
			bank.envelopes.add(env);
		}

		List<Element> elems = xmr.getTags(xmr.getUniqueRequiredTag(root, Key.Instruments), Key.Instrument);
		if (elems.size() > MAX_INSTRUMENTS)
			xmr.complain("a bank has at most " + MAX_INSTRUMENTS + " instruments, but there are " + elems.size());

		Set<String> files = new HashSet<>();
		for (Element elem : elems) {
			Instrument ins = new Instrument();
			xmr.requiresAttribute(elem, Key.wav);
			ins.wav = xmr.getAttribute(elem, Key.wav);
			if (xmr.hasAttribute(elem, Key.loop)) {
				ins.loopWav = xmr.getAttribute(elem, Key.loop);
				ins.loopCount = AudioXml.readInt(xmr, elem, Key.loopCount, LOOP_FOREVER, 0, Integer.MAX_VALUE);
			}
			xmr.requiresAttribute(elem, Key.envName);
			ins.envName = xmr.getAttribute(elem, Key.envName);
			ins.envelope = byName.get(ins.envName);
			if (ins.envelope == null)
				xmr.complain("instrument " + ins.wav + " uses unknown envelope " + ins.envName);
			ins.keyBase = AudioXml.readInt(xmr, elem, Key.keyBase, -32768, 32767);
			ins.numPred = AudioXml.readInt(xmr, elem, Key.numPred, 1, 1, 4);
			if (ins.numPred == 3)
				xmr.complain(Key.numPred + " must be 1, 2, or 4: 3");

			for (String file : new String[] { ins.wav, ins.loopWav }) {
				if (file != null && !files.add(file))
					xmr.complain("the file name " + file + " is used more than once");
			}

			if (xmr.hasTag(elem, Key.Encoding)) {
				Element encoding = xmr.getUniqueTag(elem, Key.Encoding);
				try {
					ins.codebook = CodeBook.fromHex(xmr.getAttribute(encoding, Key.codebook));
					if (xmr.hasAttribute(encoding, Key.tail))
						ins.tail = unhex(xmr.getAttribute(encoding, Key.tail));
				}
				catch (IllegalArgumentException e) {
					xmr.complain("instrument " + ins.wav + " has an invalid Encoding: " + e.getMessage());
				}
				ins.padding = AudioXml.readInt(xmr, encoding, Key.padding, 0, 0, Vadpcm.FRAME_BYTES - 1);
			}
			bank.instruments.add(ins);
		}

		for (Instrument ins : bank.instruments) {
			Wav attack = Wav.read(wavFile(layers, xmlFile, name, ins.wav), ins.loopWav != null);
			ins.sampleRate = attack.sampleRate();
			ins.attack = attack.samples();
			if (ins.loopWav != null) {
				File loopFile = wavFile(layers, xmlFile, name, ins.loopWav);
				Wav loop = Wav.read(loopFile, false);
				if (loop.sampleRate() != attack.sampleRate())
					throw new InputFileException(loopFile, "the sample rate is %d, but %s.wav is %d", loop.sampleRate(),
						ins.wav, attack.sampleRate());
				ins.loop = loop.samples();
			}
		}
		return bank;
	}

	private static String directory(String bank)
	{
		return "bank/" + bank + "/";
	}

	private static File wavFile(AudioLayers layers, File xmlFile, String bank, String wav)
	{
		File file = layers.getFile(directory(bank) + wav + ".wav");
		if (file == null)
			throw new InputFileException(xmlFile, "Bank '%s': there is no %s%s.wav file in any layer", bank, directory(bank),
				wav);
		return file;
	}

	/**
	 * Builds the BK file. Each instrument that is encoded with a new codebook is reported to the
	 * log consumer.
	 */
	public byte[] build(Consumer<String> log)
	{
		Map<Instrument, byte[]> encoded = new HashMap<>();
		for (Instrument ins : instruments) {
			short[] samples = ins.samples();
			byte[] data = null;
			boolean hadCodebook = ins.codebook != null;
			if (hadCodebook) {
				short[] all = Arrays.copyOf(samples, samples.length + ins.tail.length);
				System.arraycopy(ins.tail, 0, all, samples.length, ins.tail.length);
				data = Vadpcm.encodeExact(all, ins.codebook);
			}

			if (data != null) {
				data = Arrays.copyOf(data, data.length + ins.padding);
			}
			else {
				int pow = Integer.numberOfTrailingZeros(ins.numPred);
				ins.codebook = new CodeBook(TableDesign.makeTable(samples, 2, pow).buffer.array());
				ins.padding = 0;
				data = Vadpcm.encode(samples, ins.codebook);
				log.accept(String.format("%s %s/%s: new codebook", hadCodebook ? "re-encoded" : "encoded", name, ins.wav));
			}
			encoded.put(ins, data);
		}

		ByteArrayOutputStream loopStates = new ByteArrayOutputStream();
		Map<Instrument, Integer> loopStateAt = new HashMap<>();
		int loopStatesStart = HEADER_SIZE + INSTRUMENT_SIZE * instruments.size();
		for (Instrument ins : instruments) {
			if (ins.loop == null)
				continue;

			short[] decoded = Vadpcm.decode(encoded.get(ins), 0, encoded.get(ins).length / Vadpcm.FRAME_BYTES,
				ins.codebook);
			int frame = ins.loopStart() / Vadpcm.FRAME_SAMPLES * Vadpcm.FRAME_SAMPLES;
			loopStateAt.put(ins, loopStatesStart + loopStates.size());
			for (int i = 0; i < Vadpcm.FRAME_SAMPLES; i++) {
				loopStates.write(decoded[frame + i] >> 8);
				loopStates.write(decoded[frame + i]);
			}
		}

		ByteArrayOutputStream codebooks = new ByteArrayOutputStream();
		Map<String, Integer> codebookAt = new LinkedHashMap<>();
		int codebooksStart = align(loopStatesStart + loopStates.size());
		for (Instrument ins : instruments) {
			if (!codebookAt.containsKey(ins.codebook.toHex())) {
				codebookAt.put(ins.codebook.toHex(), codebooksStart + codebooks.size());
				codebooks.writeBytes(ins.codebook.toBytes());
			}
		}

		ByteArrayOutputStream envelopeData = new ByteArrayOutputStream();
		Map<Envelope, Integer> envelopeAt = new HashMap<>();
		int envelopesStart = align(codebooksStart + codebooks.size());
		for (Envelope env : envelopes) {
			envelopeAt.put(env, envelopesStart + envelopeData.size());
			envelopeData.writeBytes(env.write());
		}

		int samplesStart = align(envelopesStart + envelopeData.size());
		int position = samplesStart;
		Map<Instrument, Integer> sampleAt = new HashMap<>();
		for (Instrument ins : instruments) {
			sampleAt.put(ins, position);
			position = align(position + encoded.get(ins).length);
		}

		ByteBuffer bb = ByteBuffer.allocate(position);
		bb.put("BK  ".getBytes(StandardCharsets.US_ASCII)).putInt(position);
		bb.put(String.format("%-4s", name).getBytes(StandardCharsets.US_ASCII));
		bb.put("CR".getBytes(StandardCharsets.US_ASCII));
		bb.position(0x12);
		for (int i = 0; i < instruments.size(); i++)
			bb.putShort((short) (HEADER_SIZE + INSTRUMENT_SIZE * i));

		bb.position(0x32);
		bb.putShort((short) (INSTRUMENT_SIZE * instruments.size()));
		bb.putShort((short) loopStatesStart).putShort((short) loopStates.size());
		bb.putShort((short) codebooksStart).putShort((short) codebooks.size());
		bb.putShort((short) envelopesStart).putShort((short) envelopeData.size());

		for (int i = 0; i < instruments.size(); i++) {
			Instrument ins = instruments.get(i);
			bb.position(HEADER_SIZE + INSTRUMENT_SIZE * i);
			bb.putInt(sampleAt.get(ins)).putInt(encoded.get(ins).length);
			if (ins.loop != null) {
				bb.putInt(loopStateAt.get(ins)).putInt(ins.loopStart()).putInt(ins.loopEnd()).putInt(ins.loopCount);
			}
			else {
				bb.position(bb.position() + 0x10);
			}
			bb.putInt(codebookAt.get(ins.codebook.toHex())).putShort((short) ins.codebook.toBytes().length);
			bb.putShort((short) ins.keyBase).putInt(ins.sampleRate);
			bb.position(bb.position() + 8);
			bb.putInt(envelopeAt.get(ins.envelope));
		}

		bb.position(loopStatesStart).put(loopStates.toByteArray());
		bb.position(codebooksStart).put(codebooks.toByteArray());
		bb.position(envelopesStart).put(envelopeData.toByteArray());
		for (Instrument ins : instruments)
			bb.position(sampleAt.get(ins)).put(encoded.get(ins));
		return bb.array();
	}

	private static int align(int value)
	{
		return (value + 0xF) & ~0xF;
	}
}
