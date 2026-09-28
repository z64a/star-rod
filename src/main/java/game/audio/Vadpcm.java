package game.audio;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * VADPCM audio. Each 9-byte frame holds 16 samples: a header byte with a scale (high nibble) and a
 * predictor index (low nibble), then sixteen 4-bit residuals. A sample is its residual times the
 * scale plus a prediction from the previous two samples, saturated to 16 bits.
 */
public abstract class Vadpcm
{
	public static final int FRAME_SAMPLES = 16;
	public static final int FRAME_BYTES = 9;

	private static final int ORDER = 2;
	private static final int MAX_SCALE = 12;
	private static final int PREDICTOR_BYTES = 2 * ORDER * 8;

	/** Saturated samples let many residuals match, so the search for one scale and predictor gives up after this many steps. */
	private static final int SEARCH_LIMIT = 1024;

	/** A set of predictors, stored as in a sound bank: for each predictor, two rows of eight coefficients. */
	public static class CodeBook
	{
		public final int numPred;
		private final byte[] data;

		/** [predictor][sample in half frame][previous two samples, then the residuals before it in the half frame] */
		private final int[][][] vectors;

		public CodeBook(byte[] data)
		{
			if (data.length == 0 || data.length % PREDICTOR_BYTES != 0)
				throw new IllegalArgumentException("a codebook is a multiple of " + PREDICTOR_BYTES + " bytes");

			this.data = data;
			numPred = data.length / PREDICTOR_BYTES;
			vectors = new int[numPred][8][ORDER + 8];

			ByteBuffer bb = ByteBuffer.wrap(data);
			for (int i = 0; i < numPred; i++) {
				int[][] p = vectors[i];
				for (int j = 0; j < ORDER; j++) {
					for (int k = 0; k < 8; k++)
						p[k][j] = bb.getShort();
				}

				for (int k = 1; k < 8; k++)
					p[k][ORDER] = p[k - 1][ORDER - 1];
				p[0][ORDER] = 1 << 11;

				for (int k = 1; k < 8; k++) {
					for (int j = k; j < 8; j++)
						p[j][k + ORDER] = p[j - k][ORDER];
				}
			}
		}

		public static CodeBook fromHex(String hex)
		{
			return new CodeBook(HexFormat.of().parseHex(hex));
		}

		public byte[] toBytes()
		{
			return data;
		}

		public String toHex()
		{
			return HexFormat.of().formatHex(data);
		}
	}

	/** Decodes whole frames, starting from silence. */
	public static short[] decode(byte[] data, int offset, int frames, CodeBook book)
	{
		short[] samples = new short[frames * FRAME_SAMPLES];
		int[] nibbles = new int[FRAME_SAMPLES];
		int[] frame = new int[FRAME_SAMPLES];
		int[] history = new int[ORDER];

		for (int f = 0; f < frames; f++) {
			int at = offset + f * FRAME_BYTES;
			int scale = data[at] >> 4 & 0xF;
			int predictor = Math.min(data[at] & 0xF, book.numPred - 1);
			for (int i = 0; i < 8; i++) {
				nibbles[2 * i] = data[at + 1 + i] >> 4;
				nibbles[2 * i + 1] = data[at + 1 + i] << 28 >> 28;
			}

			decodeFrame(book, predictor, scale, nibbles, history, frame);
			for (int i = 0; i < FRAME_SAMPLES; i++)
				samples[f * FRAME_SAMPLES + i] = (short) frame[i];
			history[0] = frame[14];
			history[1] = frame[15];
		}
		return samples;
	}

	private static void decodeFrame(CodeBook book, int predictor, int scale, int[] nibbles, int[] history, int[] out)
	{
		int[] in = new int[ORDER + 8];
		for (int half = 0; half < 2; half++) {
			in[0] = half == 0 ? history[0] : out[6];
			in[1] = half == 0 ? history[1] : out[7];
			for (int i = 0; i < 8; i++) {
				int residual = nibbles[half * 8 + i] * (1 << scale);
				in[ORDER + i] = residual;
				out[half * 8 + i] = saturate(residual + predict(ORDER + i, book.vectors[predictor][i], in));
			}
		}
	}

	private static int predict(int length, int[] vector, int[] in)
	{
		int sum = 0;
		for (int k = 0; k < length; k++)
			sum += vector[k] * in[k];
		return Math.floorDiv(sum, 1 << 11);
	}

	private static int saturate(int value)
	{
		return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
	}

	/**
	 * Finds an encoding that decodes to exactly the given samples with the given codebook, or returns
	 * null if some frame has none. When a frame could use two adjacent scales with the same predictor,
	 * the larger one is used; otherwise the smallest scale and then the smallest predictor. A residual
	 * that saturates to the wanted sample can stand in for another that does, and the first that lets
	 * the rest of the frame match is used. The samples are padded with silence to whole frames.
	 */
	public static byte[] encodeExact(short[] samples, CodeBook book)
	{
		int frames = (samples.length + FRAME_SAMPLES - 1) / FRAME_SAMPLES;
		byte[] encoded = new byte[frames * FRAME_BYTES];
		int[] target = new int[FRAME_SAMPLES];
		int[] history = new int[ORDER];
		int[] nibbles = new int[FRAME_SAMPLES];
		int[][] found = new int[(MAX_SCALE + 1) * book.numPred][];

		for (int f = 0; f < frames; f++) {
			for (int i = 0; i < FRAME_SAMPLES; i++)
				target[i] = f * FRAME_SAMPLES + i < samples.length ? samples[f * FRAME_SAMPLES + i] : 0;
			int count = 0;
			for (int scale = 0; scale <= MAX_SCALE; scale++) {
				for (int predictor = 0; predictor < book.numPred; predictor++) {
					if (solve(book, predictor, scale, history, target, nibbles, 0, new int[] { SEARCH_LIMIT }))
						found[count++] = new int[] { scale, predictor };
				}
			}
			if (count == 0)
				return null;

			int[] choice = found[0];
			if (count == 2 && found[0][1] == found[1][1] && found[1][0] == found[0][0] + 1)
				choice = found[1];

			solve(book, choice[1], choice[0], history, target, nibbles, 0, new int[] { SEARCH_LIMIT });
			int at = f * FRAME_BYTES;
			encoded[at] = (byte) (choice[0] << 4 | choice[1]);
			for (int i = 0; i < 8; i++)
				encoded[at + 1 + i] = (byte) (nibbles[2 * i] << 4 | nibbles[2 * i + 1] & 0xF);

			history[0] = target[14];
			history[1] = target[15];
		}
		return encoded;
	}

	/** Fills the residuals from index onward so the frame decodes to target. */
	private static boolean solve(CodeBook book, int predictor, int scale, int[] history, int[] target, int[] nibbles,
		int index, int[] steps)
	{
		if (index == FRAME_SAMPLES)
			return true;
		if (--steps[0] < 0)
			return false;

		int half = index / 8;
		int i = index % 8;
		int[] in = new int[ORDER + 8];
		in[0] = half == 0 ? history[0] : target[6];
		in[1] = half == 0 ? history[1] : target[7];
		for (int k = 0; k < i; k++)
			in[ORDER + k] = nibbles[half * 8 + k] * (1 << scale);

		int predicted = predict(ORDER + i, book.vectors[predictor][i], in);
		int wanted = target[index];
		for (int pass = 0; pass < 2; pass++) {
			for (int n = -8; n <= 7; n++) {
				int value = n * (1 << scale) + predicted;
				if (pass == 0 ? value != wanted : value == wanted || saturate(value) != wanted)
					continue;
				nibbles[index] = n;
				if (solve(book, predictor, scale, history, target, nibbles, index + 1, steps))
					return true;
			}
		}
		return false;
	}

	/**
	 * Encodes samples the way the N64 SDK's vadpcm_enc does: each frame uses the predictor with the
	 * least error and the smallest scale that fits its residuals. The samples are padded with silence
	 * to whole frames.
	 */
	public static byte[] encode(short[] samples, CodeBook book)
	{
		int frames = (samples.length + FRAME_SAMPLES - 1) / FRAME_SAMPLES;
		byte[] encoded = new byte[frames * FRAME_BYTES];
		short[] frame = new short[FRAME_SAMPLES];
		int[] state = new int[FRAME_SAMPLES];

		for (int f = 0; f < frames; f++) {
			Arrays.fill(frame, (short) 0);
			System.arraycopy(samples, f * FRAME_SAMPLES, frame, 0,
				Math.min(FRAME_SAMPLES, samples.length - f * FRAME_SAMPLES));
			encodeFrame(encoded, f * FRAME_BYTES, book, frame, state);
		}
		return encoded;
	}

	private static void encodeFrame(byte[] out, int at, CodeBook book, short[] samples, int[] state)
	{
		int[] prediction = new int[FRAME_SAMPLES];
		int[] in = new int[FRAME_SAMPLES];
		int[] saved = new int[FRAME_SAMPLES];
		float[] error = new float[FRAME_SAMPLES];
		int[] roundedError = new int[FRAME_SAMPLES];
		int[] residuals = new int[FRAME_SAMPLES];

		int bestPredictor = 0;
		float leastError = Float.MAX_VALUE;
		for (int k = 0; k < book.numPred; k++) {
			predictErrors(state, samples, in, error, prediction, book.vectors[k]);

			float sum = 0.0f;
			for (float e : error)
				sum += e * e;
			if (sum < leastError) {
				leastError = sum;
				bestPredictor = k;
			}
		}

		int[][] vectors = book.vectors[bestPredictor];
		predictErrors(state, samples, in, error, prediction, vectors);

		for (int i = 0; i < FRAME_SAMPLES; i++) {
			float e = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, error[i]));
			roundedError[i] = (int) (e > 0.0f ? e + 0.5f : e - 0.5f);
		}

		int max = 0;
		for (int e : roundedError) {
			if (Math.abs(e) > Math.abs(max))
				max = e;
		}

		int scale;
		for (scale = 0; scale <= 16 - 4; scale++) {
			if (max <= 7 && max >= -8)
				break;
			max /= 2;
		}

		System.arraycopy(state, 0, saved, 0, FRAME_SAMPLES);
		scale--;
		int maxClip;
		int iterations = 0;
		do {
			iterations++;
			scale = Math.min(scale + 1, MAX_SCALE);
			maxClip = 0;

			for (int half = 0; half < 2; half++) {
				if (half == 0)
					System.arraycopy(saved, 16 - ORDER, in, 0, ORDER);
				else
					System.arraycopy(state, 8 - ORDER, in, 0, ORDER);

				for (int i = 0; i < 8; i++) {
					int idx = half * 8 + i;
					prediction[idx] = predict(ORDER + i, vectors[i], in);
					float err = samples[idx] - prediction[idx];
					int residual = quantize(err, 1 << scale);
					int clipped = Math.max(-8, Math.min(7, residual));
					maxClip = Math.max(maxClip, Math.abs(clipped - residual));
					residuals[idx] = clipped;
					in[ORDER + i] = clipped * (1 << scale);
					state[idx] = saturate(prediction[idx] + in[ORDER + i]);
				}
			}
		}
		while (maxClip >= 2 && iterations < 2);

		out[at] = (byte) (scale << 4 | bestPredictor);
		for (int i = 0; i < 8; i++)
			out[at + 1 + i] = (byte) (residuals[2 * i] << 4 | residuals[2 * i + 1] & 0xF);
	}

	/** The error left by predicting a frame from the previous state, with each half using the true samples before it. */
	private static void predictErrors(int[] state, short[] samples, int[] in, float[] error, int[] prediction,
		int[][] vectors)
	{
		System.arraycopy(state, 16 - ORDER, in, 0, ORDER);

		for (int i = 0; i < 8; i++) {
			prediction[i] = predict(i + ORDER, vectors[i], in);
			in[i + ORDER] = samples[i] - prediction[i];
			error[i] = in[i + ORDER];
		}

		for (int i = 0; i < ORDER; i++)
			in[i] = prediction[8 - ORDER + i] + in[i + 8];

		for (int i = 0; i < 8; i++) {
			prediction[8 + i] = predict(ORDER + i, vectors[i], in);
			in[i + ORDER] = samples[i + 8] - prediction[i + 8];
			error[i + 8] = in[i + ORDER];
		}
	}

	private static int quantize(float x, int scale)
	{
		return (short) (x > 0.0f ? x / scale + 0.4999999f : x / scale - 0.4999999f);
	}
}
