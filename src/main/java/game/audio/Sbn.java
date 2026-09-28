package game.audio;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.input.InputFileException;

/**
 * The SBN archive: a table of audio files followed by the INIT block, which lists the songs,
 * the banks loaded at startup, and the files the engine looks up by fixed position.
 */
public class Sbn
{
	/** File types in the order they are stored. */
	public static final List<String> TYPES = List.of("BGM", "SEF", "BK", "PER", "PRG", "MSEQ");

	public static final int NO_FILE = 0xFFFF;

	private static final int HEADER_SIZE = 0x40;
	private static final int INIT_HEADER_SIZE = 0x20;
	private static final int MEMBER_HEADER_SIZE = 12;
	private static final int MAX_FILE_SIZE = 0xFFFFF0;

	/** An audio file. Its data is exactly the size its own header declares. */
	public static class Member
	{
		public final String type;
		public final String name;
		public final byte[] data;

		private Member(String type, String name, byte[] data)
		{
			this.type = type;
			this.name = name;
			this.data = data;
		}

		public static Member read(File source, byte[] bytes, int offset)
		{
			if (offset < 0 || offset + MEMBER_HEADER_SIZE > bytes.length)
				throw new InputFileException(source, "file is too short for an audio header");

			ByteBuffer bb = ByteBuffer.wrap(bytes);
			String type = readString(bytes, offset, 4);
			if (!TYPES.contains(type))
				throw new InputFileException(source, "unsupported audio file type '%s'", type);

			int size = bb.getInt(offset + 4);
			if (size < MEMBER_HEADER_SIZE || size > MAX_FILE_SIZE || offset + size > bytes.length)
				throw new InputFileException(source, "invalid declared size 0x%X", size);
			if (type.equals("BK") && size < 0x40)
				throw new InputFileException(source, "sound bank is too short for its header");

			return new Member(type, readString(bytes, offset + 8, 4), Arrays.copyOfRange(bytes, offset, offset + size));
		}

		public int getFormat()
		{
			return switch (type) {
				case "BGM" -> 0x10;
				case "SEF" -> 0x20;
				case "BK" -> 0x30;
				default -> 0x40;
			};
		}
	}

	public final List<Member> files = new ArrayList<>();

	/** The bytes between the file table and the first file. */
	public byte[] extra = new byte[0];

	/** Song list entries: BGM file, then three auxiliary bank files (0 for none). */
	public final List<int[]> songs = new ArrayList<>();

	/** Bank list entries: BK file, index within its group, group. */
	public final List<int[]> banks = new ArrayList<>();

	/** Files the engine finds by position, or NO_FILE. */
	public final List<Integer> resources = new ArrayList<>();

	public static Sbn read(File source, byte[] data)
	{
		ByteBuffer bb = ByteBuffer.wrap(data);
		if (data.length < HEADER_SIZE || !readString(data, 0, 4).equals("SBN"))
			throw new InputFileException(source, "not an SBN archive");

		int size = bb.getInt(4);
		if (size < HEADER_SIZE || size > data.length)
			throw new InputFileException(source, "invalid SBN size 0x%X", size);

		int tableOffset = bb.getInt(0x10);
		int count = bb.getInt(0x14);
		int extraEnd = bb.getInt(0x18);
		int extraStart = bb.getInt(0x1C);
		int initOffset = bb.getInt(0x24);
		if (count < 0 || tableOffset < 0 || tableOffset + 8L * count > size)
			throw new InputFileException(source, "file table is out of bounds");
		if (extraStart < 0 || extraStart > extraEnd || extraEnd > size)
			throw new InputFileException(source, "extra data is out of bounds");

		Sbn sbn = new Sbn();
		sbn.extra = Arrays.copyOfRange(data, extraStart, extraEnd);

		byte[] body = Arrays.copyOf(data, size);
		for (int i = 0; i < count; i++) {
			int offset = bb.getInt(tableOffset + 8 * i);
			try {
				sbn.files.add(Member.read(source, body, offset));
			}
			catch (InputFileException e) {
				throw new InputFileException(source, "file %d: %s", i, e.getMessage());
			}
		}

		if (initOffset < 0 || initOffset + INIT_HEADER_SIZE > size || !readString(data, initOffset, 4).equals("INIT"))
			throw new InputFileException(source, "INIT block is missing");

		int bankOffset = initOffset + readShort(bb, initOffset + 0x08);
		int bankCount = readCount(source, bb, initOffset + 0x0A, 4, bankOffset, size, "bank list");
		int songOffset = initOffset + readShort(bb, initOffset + 0x0C);
		int songCount = readCount(source, bb, initOffset + 0x0E, 8, songOffset, size, "song list");
		int resourceOffset = initOffset + readShort(bb, initOffset + 0x10);
		int resourceCount = readCount(source, bb, initOffset + 0x12, 2, resourceOffset, size, "resource list");

		for (int i = 0; i < bankCount; i++) {
			int at = bankOffset + 4 * i;
			int file = readShort(bb, at);
			checkIndex(source, file, count, "bank list");
			sbn.banks.add(new int[] { file, data[at + 2] & 0xFF, data[at + 3] & 0xFF });
		}

		for (int i = 0; i < songCount; i++) {
			int at = songOffset + 8 * i;
			int[] song = new int[4];
			for (int j = 0; j < 4; j++) {
				song[j] = readShort(bb, at + 2 * j);
				checkIndex(source, song[j], count, "song list");
			}
			sbn.songs.add(song);
		}

		for (int i = 0; i < resourceCount; i++) {
			int file = readShort(bb, resourceOffset + 2 * i);
			if (file != NO_FILE)
				checkIndex(source, file, count, "resource list");
			sbn.resources.add(file);
		}

		return sbn;
	}

	public byte[] write()
	{
		int extraOffset = align(HEADER_SIZE + 8 * files.size());
		int position = align(extraOffset + extra.length);
		int filesOffset = position;

		int[] offsets = new int[files.size()];
		for (int i = 0; i < offsets.length; i++) {
			offsets[i] = position;
			position = align(position + files.get(i).data.length);
		}

		int initOffset = position;
		int bankOffset = INIT_HEADER_SIZE;
		int songOffset = align(bankOffset + 4 * (banks.size() + 1));
		int resourceOffset = align(songOffset + 8 * (songs.size() + 1));
		int initSize = resourceOffset + 2 * (resources.size() + 1);
		int total = align(initOffset + initSize);

		ByteBuffer bb = ByteBuffer.allocate(total);
		bb.put(0, "SBN ".getBytes(StandardCharsets.US_ASCII));
		bb.putInt(0x04, total);
		bb.putInt(0x10, HEADER_SIZE);
		bb.putInt(0x14, files.size());
		bb.putInt(0x18, filesOffset);
		bb.putInt(0x1C, extraOffset);
		bb.putInt(0x24, initOffset);
		bb.put(extraOffset, extra);

		for (int i = 0; i < offsets.length; i++) {
			Member file = files.get(i);
			bb.putInt(HEADER_SIZE + 8 * i, offsets[i]);
			bb.putInt(HEADER_SIZE + 8 * i + 4, file.getFormat() << 24 | file.data.length);
			bb.put(offsets[i], file.data);
		}

		bb.put(initOffset, "INIT".getBytes(StandardCharsets.US_ASCII));
		bb.putInt(initOffset + 0x04, initSize);
		bb.putShort(initOffset + 0x08, (short) bankOffset);
		bb.putShort(initOffset + 0x0A, (short) (4 * (banks.size() + 1)));
		bb.putShort(initOffset + 0x0C, (short) songOffset);
		bb.putShort(initOffset + 0x0E, (short) (8 * (songs.size() + 1)));
		bb.putShort(initOffset + 0x10, (short) resourceOffset);
		bb.putShort(initOffset + 0x12, (short) (2 * (resources.size() + 1)));

		bb.position(initOffset + bankOffset);
		for (int[] bank : banks) {
			bb.putShort((short) bank[0]);
			bb.put((byte) bank[1]);
			bb.put((byte) bank[2]);
		}
		bb.putShort((short) NO_FILE);

		bb.position(initOffset + songOffset);
		for (int[] song : songs) {
			for (int file : song)
				bb.putShort((short) file);
		}
		bb.putShort((short) NO_FILE);

		bb.position(initOffset + resourceOffset);
		for (int file : resources)
			bb.putShort((short) file);
		bb.putShort((short) NO_FILE);

		return bb.array();
	}

	private static int align(int value)
	{
		return (value + 0xF) & ~0xF;
	}

	private static int readShort(ByteBuffer bb, int offset)
	{
		return bb.getShort(offset) & 0xFFFF;
	}

	/** Reads the byte size of a list that ends in a terminator and returns how many entries precede it. */
	private static int readCount(File source, ByteBuffer bb, int sizeOffset, int entrySize, int listOffset, int limit,
		String what)
	{
		int listSize = readShort(bb, sizeOffset);
		if (listSize < entrySize || listSize % entrySize != 0 || listOffset + listSize > limit)
			throw new InputFileException(source, "%s is out of bounds", what);
		return listSize / entrySize - 1;
	}

	private static void checkIndex(File source, int file, int count, String what)
	{
		if (file >= count)
			throw new InputFileException(source, "%s refers to file %d, but there are only %d", what, file, count);
	}

	/** Reads ASCII up to the first NUL, without trailing spaces. */
	private static String readString(byte[] bytes, int offset, int length)
	{
		int end = offset;
		while (end < offset + length && bytes[end] != 0)
			end++;
		return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII).stripTrailing();
	}
}
