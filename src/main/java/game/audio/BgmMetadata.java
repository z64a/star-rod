package game.audio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import app.input.InputFileException;

/**
 * The metadata Mamar writes after the end of a BGM file: "MAMAR" padded to 8 bytes at the BGM's size rounded up to
 * 8, then a MessagePack array of the track names, beats per bar, mix names, aux banks, and import link. Of these,
 * the game needs only the aux banks, the BK files the song loads into the engine's aux banks.
 */
public abstract class BgmMetadata
{
	public static final int AUX_BANKS = 3;

	private static final byte[] MAGIC = Arrays.copyOf("MAMAR".getBytes(StandardCharsets.US_ASCII), 8);
	private static final int AUX_BANKS_FIELD = 3;

	/** The BK file each aux bank loads, such as "SPC0.bk", or null for a bank the song leaves empty. */
	public static String[] readAuxBanks(File source, byte[] bgm)
	{
		String[] banks = new String[AUX_BANKS];
		if (bgm.length < 8)
			return banks;
		int start = align8(ByteBuffer.wrap(bgm).getInt(4));
		if (bgm.length < start + MAGIC.length || !Arrays.equals(bgm, start, start + MAGIC.length, MAGIC, 0, MAGIC.length))
			return banks;

		Reader r = new Reader(source, bgm, start + MAGIC.length);
		int header = r.u8();
		if (!isArray(header))
			throw r.error("expected an array");
		int fields = r.arrayLength(header);
		for (int i = 0; i < fields; i++) {
			if (i == AUX_BANKS_FIELD)
				readBankNames(r, banks);
			else
				r.skip();
		}
		return banks;
	}

	private static void readBankNames(Reader r, String[] banks)
	{
		int header = r.u8();
		if (!isArray(header))
			throw r.error("the aux banks must be an array");
		int count = r.arrayLength(header);
		for (int i = 0; i < count; i++) {
			String name = r.string();
			if (name.isEmpty())
				continue;
			if (i >= AUX_BANKS)
				throw new InputFileException(r.source, "the song loads bank %s into aux bank %d, but the engine has %d aux banks",
					name, i, AUX_BANKS);
			banks[i] = name + ".bk";
		}
	}

	/** The BGM with metadata that loads each of banks, BK file names or null for none, into its aux bank. */
	public static byte[] withAuxBanks(byte[] bgm, String[] banks)
	{
		int last = banks.length - 1;
		while (last >= 0 && banks[last] == null)
			last--;
		if (last < 0)
			return bgm;

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int size = ByteBuffer.wrap(bgm).getInt(4);
		out.write(bgm, 0, size);
		out.writeBytes(new byte[align8(size) - size]);
		out.writeBytes(MAGIC);

		out.write(0x95); // array of 5
		out.write(0x80); // no track names
		out.write(0xC0); // no beats per bar
		out.write(0x80); // no mix names
		out.write(0x90 | (last + 1));
		for (int i = 0; i <= last; i++) {
			byte[] name = banks[i] == null ? new byte[0]
				: banks[i].substring(0, banks[i].length() - ".bk".length()).getBytes(StandardCharsets.UTF_8);
			out.write(0xA0 | name.length);
			out.writeBytes(name);
		}
		out.write(0xC0); // no import link
		return out.toByteArray();
	}

	private static int align8(int value)
	{
		return (value + 7) & ~7;
	}

	private static boolean isArray(int header)
	{
		return (header & 0xF0) == 0x90 || header == 0xDC || header == 0xDD;
	}

	private static boolean isMap(int header)
	{
		return (header & 0xF0) == 0x80 || header == 0xDE || header == 0xDF;
	}

	private static class Reader
	{
		private final File source;
		private final ByteBuffer buf;

		private Reader(File source, byte[] data, int position)
		{
			this.source = source;
			buf = ByteBuffer.wrap(data);
			buf.position(position);
		}

		private InputFileException error(String message)
		{
			return new InputFileException(source, "the Mamar metadata at 0x%X can't be read: %s", buf.position(), message);
		}

		private int u8()
		{
			if (!buf.hasRemaining())
				throw error("it ends early");
			return buf.get() & 0xFF;
		}

		private int length(int bytes)
		{
			if (buf.remaining() < bytes)
				throw error("it ends early");
			return switch (bytes) {
				case 1 -> buf.get() & 0xFF;
				case 2 -> buf.getShort() & 0xFFFF;
				default -> buf.getInt();
			};
		}

		private int arrayLength(int header)
		{
			return header == 0xDC ? length(2) : header == 0xDD ? length(4) : header & 0xF;
		}

		private int mapLength(int header)
		{
			return header == 0xDE ? length(2) : header == 0xDF ? length(4) : header & 0xF;
		}

		private void advance(int bytes)
		{
			if (bytes < 0 || buf.remaining() < bytes)
				throw error("it ends early");
			buf.position(buf.position() + bytes);
		}

		private String string()
		{
			int header = u8();
			int length;
			if ((header & 0xE0) == 0xA0)
				length = header & 0x1F;
			else if (header >= 0xD9 && header <= 0xDB)
				length = length(1 << (header - 0xD9));
			else
				throw error("expected a string");
			int start = buf.position();
			advance(length);
			return new String(buf.array(), start, length, StandardCharsets.UTF_8);
		}

		private void skip()
		{
			int header = u8();
			if (header < 0x80 || header >= 0xE0 || header == 0xC0 || header == 0xC2 || header == 0xC3)
				return;
			if ((header & 0xE0) == 0xA0) {
				advance(header & 0x1F);
				return;
			}
			if (isArray(header)) {
				for (int i = arrayLength(header); i > 0; i--)
					skip();
				return;
			}
			if (isMap(header)) {
				for (int i = mapLength(header) * 2; i > 0; i--)
					skip();
				return;
			}
			switch (header) {
				case 0xC4, 0xD9 -> advance(length(1)); // bin 8, str 8
				case 0xC5, 0xDA -> advance(length(2)); // bin 16, str 16
				case 0xC6, 0xDB -> advance(length(4)); // bin 32, str 32
				case 0xC7 -> advance(length(1) + 1); // ext 8
				case 0xC8 -> advance(length(2) + 1); // ext 16
				case 0xC9 -> advance(length(4) + 1); // ext 32
				case 0xCC, 0xD0 -> advance(1);
				case 0xCD, 0xD1 -> advance(2);
				case 0xCA, 0xCE, 0xD2 -> advance(4);
				case 0xCB, 0xCF, 0xD3 -> advance(8);
				case 0xD4 -> advance(2); // fixext 1
				case 0xD5 -> advance(3); // fixext 2
				case 0xD6 -> advance(5); // fixext 4
				case 0xD7 -> advance(9); // fixext 8
				case 0xD8 -> advance(17); // fixext 16
				default -> throw error(String.format("unknown type 0x%02X", header));
			}
		}
	}
}
