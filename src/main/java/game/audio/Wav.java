package game.audio;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import app.input.InputFileException;

/** Mono 16-bit PCM audio in a RIFF WAVE file. */
public record Wav(int sampleRate, short[] samples)
{
	private static final int FORMAT_PCM = 1;
	private static final int FORMAT_FLOAT = 3;
	private static final int FORMAT_EXTENSIBLE = 0xFFFE;
	private static final int HEADER_SIZE = 44;

	/** An empty file is an error unless allowEmpty is set. */
	public static Wav read(File file, boolean allowEmpty)
	{
		if (!file.isFile())
			throw new InputFileException(file, "file not found");

		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file.toPath());
		}
		catch (IOException e) {
			throw new InputFileException(file, e.getMessage());
		}

		ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		if (bytes.length < 12 || !tag(bytes, 0).equals("RIFF") || !tag(bytes, 8).equals("WAVE"))
			throw new InputFileException(file, "not a WAV file");

		int format = 0;
		int channels = 0;
		int rate = 0;
		int bits = 0;
		int dataAt = -1;
		int dataSize = 0;
		for (int at = 12; at + 8 <= bytes.length;) {
			int size = bb.getInt(at + 4);
			int body = at + 8;
			if (size < 0 || body + (long) size > bytes.length)
				size = bytes.length - body;

			if (tag(bytes, at).equals("fmt ") && size >= 16) {
				format = bb.getShort(body) & 0xFFFF;
				channels = bb.getShort(body + 2);
				rate = bb.getInt(body + 4);
				bits = bb.getShort(body + 14);
				if (format == FORMAT_EXTENSIBLE && size >= 26)
					format = bb.getShort(body + 24) & 0xFFFF;
			}
			else if (tag(bytes, at).equals("data")) {
				dataAt = body;
				dataSize = size;
				break;
			}
			at = body + size + (size & 1);
		}

		if (format == 0 || dataAt < 0)
			throw new InputFileException(file, "the WAV file has no format or no audio data");
		if (format == FORMAT_FLOAT)
			throw new InputFileException(file, "floating-point WAV files are not supported; use 16-bit PCM");
		if (format != FORMAT_PCM)
			throw new InputFileException(file, "the WAV file is not uncompressed PCM; use 16-bit PCM");
		if (channels != 1)
			throw new InputFileException(file, "the WAV file has %d channels; only mono is supported", channels);
		if (bits != 16)
			throw new InputFileException(file, "the WAV file has %d-bit samples; only 16-bit is supported", bits);
		if (rate <= 0)
			throw new InputFileException(file, "the WAV file has no sample rate");
		if (dataSize < 2 && !allowEmpty)
			throw new InputFileException(file, "the WAV file contains no audio samples");

		short[] samples = new short[dataSize / 2];
		bb.position(dataAt);
		bb.asShortBuffer().get(samples);
		return new Wav(rate, samples);
	}

	public void write(File file) throws IOException
	{
		int dataSize = samples.length * 2;
		ByteBuffer bb = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN);
		bb.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(HEADER_SIZE - 8 + dataSize);
		bb.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
		bb.putShort((short) FORMAT_PCM).putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2);
		bb.putShort((short) 2).putShort((short) 16);
		bb.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
		bb.asShortBuffer().put(samples);

		Files.createDirectories(file.toPath().getParent());
		Files.write(file.toPath(), bb.array());
	}

	private static String tag(byte[] bytes, int at)
	{
		return new String(bytes, at, 4, StandardCharsets.US_ASCII);
	}
}
