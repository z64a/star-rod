package game.yay0;

public class Yay0Encoder
{
	private final int decompressedSize;

	private short[] linkBuffer;
	private byte[] maskBuffer, chunkBuffer;
	private int maskCount, linkCount, chunkCount;

	private int mask, maskBit;

	public Yay0Encoder(int length)
	{
		decompressedSize = length;

		maskBuffer = new byte[length];
		maskCount = 0;

		linkBuffer = new short[length];
		linkCount = 0;

		chunkBuffer = new byte[length];
		chunkCount = 0;

		mask = 0;
		maskBit = 1 << 7;
	}

	public void addCopy(byte b)
	{
		//	Logger.logfDetail("Adding copy %02X, b);

		chunkBuffer[chunkCount] = b;
		chunkCount++;

		addMaskBit(false);
	}

	public void addLink(int linkedLength, int distance)
	{
		assert (linkedLength > 2);
		assert (linkedLength < 274);
		assert (distance > 0);
		assert (distance <= 0x1000);

		//	Logger.logDetail("Linking " + linkedLength + " bytes from offset " + distance);

		short link = 0;

		linkedLength -= 2;
		distance--;

		if (linkedLength > 15) {
			linkedLength -= 16;

			chunkBuffer[chunkCount] = (byte) linkedLength;
			chunkCount++;
		}
		else {
			link += linkedLength << 12;
		}

		link += distance;

		linkBuffer[linkCount] = link;
		linkCount++;

		addMaskBit(true);
	}

	private void addMaskBit(boolean linked)
	{
		if (!linked)
			mask += maskBit;

		maskBit >>>= 1;
		if (maskBit == 0) {
			maskBuffer[maskCount] = (byte) mask;
			maskCount++;

			mask = 0;
			maskBit = 1 << 7;
		}
	}

	public void flush()
	{
		if (maskBit == (1 << 7))
			return;

		maskBuffer[maskCount] = (byte) mask;
		maskCount++;
	}

	public byte[] getFile()
	{
		int maskSize = (maskCount + 3) & 0xFFFFFFFC; // masks are 4 byte aligned
		int linkSize = 2 * linkCount;
		int chunkSize = chunkCount;
		int size = 0x10 + maskSize + linkSize + chunkSize;
		size = (size + 1) & 0xFFFFFFFE; // total file is 2 byte aligned

		byte[] buffer = new byte[size];
		putInteger(buffer, 0, 0x59617930); // magic word: 'Yay0'
		putInteger(buffer, 4, decompressedSize);

		// write mask
		int pos = 0x10;
		for (int i = 0; i < maskCount; i++)
			buffer[pos++] = maskBuffer[i];
		pos = (pos + 3) & 0xFFFFFFFC; // 4 byte align

		putInteger(buffer, 8, pos); // link offset
		for (int i = 0; i < linkCount; i++) {
			buffer[pos++] = (byte) (linkBuffer[i] >> 8);
			buffer[pos++] = (byte) (linkBuffer[i]);
		}

		putInteger(buffer, 12, pos); // chunk offset
		for (int i = 0; i < chunkCount; i++)
			buffer[pos++] = chunkBuffer[i];

		return buffer;
	}

	private void putInteger(byte[] buffer, int position, int val)
	{
		buffer[position++] = (byte) (val >> 24);
		buffer[position++] = (byte) (val >> 16);
		buffer[position++] = (byte) (val >> 8);
		buffer[position] = (byte) val;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();

		sb.append(" Mask Buffer: ");
		for (int i = 0; i < maskCount;) {
			sb.append(String.format("%02X", maskBuffer[i]));
			if (++i % 4 == 0)
				sb.append(" ");
		}
		sb.append("\n");

		sb.append(" Link Buffer: ");
		for (int i = 0; i < linkCount;) {
			sb.append(String.format("%04X", linkBuffer[i]));
			if (++i % 2 == 0)
				sb.append(" ");
		}
		sb.append("\n");

		sb.append("Chunk Buffer: ");
		for (int i = 0; i < chunkCount;) {
			sb.append(String.format("%02X", chunkBuffer[i]));
			if (++i % 4 == 0)
				sb.append(" ");
		}
		sb.append("\n");

		return sb.toString();
	}
}
