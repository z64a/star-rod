package game.audio.engine;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;

import game.audio.engine.AudioEngine.PcmOutput;

public class WavWriter implements Closeable, PcmOutput
{
	private static final int HEADER_SIZE = 44;
	private static final int CHANNELS = 2;
	private static final int BITS_PER_SAMPLE = 16;
	private static final int BLOCK_ALIGN = CHANNELS * BITS_PER_SAMPLE / 8;

	private final RandomAccessFile file;
	private long dataSize;

	public WavWriter(File outputFile) throws IOException
	{
		file = new RandomAccessFile(outputFile, "rw");
		file.setLength(0);
		file.write(new byte[HEADER_SIZE]);
	}

	@Override
	public void write(byte[] data, int offset, int length)
	{
		try {
			file.write(data, offset, length);
			dataSize += length;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void close() throws IOException
	{
		try {
			file.seek(0);
			writeAscii("RIFF");
			writeIntLE((int) (36 + dataSize));
			writeAscii("WAVE");
			writeAscii("fmt ");
			writeIntLE(16);
			writeShortLE(1);
			writeShortLE(CHANNELS);
			writeIntLE(AudioEngine.OUTPUT_RATE);
			writeIntLE(AudioEngine.OUTPUT_RATE * BLOCK_ALIGN);
			writeShortLE(BLOCK_ALIGN);
			writeShortLE(BITS_PER_SAMPLE);
			writeAscii("data");
			writeIntLE((int) dataSize);
		}
		finally {
			file.close();
		}
	}

	public long getSampleCount()
	{
		return dataSize / BLOCK_ALIGN;
	}

	private void writeAscii(String value) throws IOException
	{
		for (int i = 0; i < value.length(); i++)
			file.writeByte(value.charAt(i));
	}

	private void writeShortLE(int value) throws IOException
	{
		file.writeByte(value);
		file.writeByte(value >> 8);
	}

	private void writeIntLE(int value) throws IOException
	{
		file.writeByte(value);
		file.writeByte(value >> 8);
		file.writeByte(value >> 16);
		file.writeByte(value >> 24);
	}
}
