package game.yay0;

/**
 * Yay0 is an implementation of LZSS.
 */
public final class Yay0Helper
{
	/*
	public static void main(String args[]) throws IOException
	{
		File source = new File("./yay0/decoded/0210ACFC.bin");
		encode(FileUtils.readFileToByteArray(source));

		/*
		File sourceFile = new File("./test/dro_01_shape.yay0");
		byte[] source = FileUtils.readFileToByteArray(sourceFile);

		byte[] decoded = decode(source);

		File out = new File("./test/dro_01_shape.bin");
		FileUtils.writeByteArrayToFile(out, decoded);
		 */

	/*
	File sourceRefFile = new File("./yay0/ref/comp/024F64DC.bin");
	decode(FileUtils.readFileToByteArray(sourceRefFile));
	 */

	/*

	File sourceFile = new File("./yay0/ver/decomp/024F64DC.bin");
	byte[] yay0 = encode(FileUtils.readFileToByteArray(sourceFile));

	for(int i = 0; i < yay0.length;)
	{
		System.out.print(String.format("%02X", yay0[i]));
		i++;
		if(i % 4 == 0)
			System.out.print(" ");
		if(i % 16 == 0)
			System.out.println("");
	}
	 */
	//}

	public static byte[] encode(byte[] source)
	{
		return encode(source, false);
	}

	public static byte[] encode(byte[] source, boolean logUpdates)
	{
		if (source.length < 64)
			throw new IllegalArgumentException("Source is too small to compress!");

		Yay0EncodeHelper helper = new Yay0EncodeHelper(source, logUpdates);
		return helper.getFile();
	}

	/**
	 *
	 * @param source
	 * @return
	 */
	public static byte[] decode(byte[] source)
	{
		assert (getInteger(source, 0) == 0x59617930); // "Yay0"
		int decompressedSize = getInteger(source, 4);
		int linkOffset = getInteger(source, 8);
		int sourceOffset = getInteger(source, 12);

		byte currentCommand = 0;
		int commandOffset = 16;
		int remainingBits = 0;

		byte[] decoded = new byte[decompressedSize];
		int decodedBytes = 0;

		do {
			// get the next command
			if (remainingBits == 0) {
				currentCommand = source[commandOffset];
				commandOffset++;
				remainingBits = 8;
			}

			// bit == 1 --> copy directly from source
			if ((currentCommand & 0x80) != 0) {
				//	Logger.logfDetail("%-5X : Adding copy %02X", decodedBytes, source[sourceOffset], Priority.DETAIL);

				decoded[decodedBytes] = source[sourceOffset];
				sourceOffset++;
				decodedBytes++;

				// bit == 0 --> copy from decoded buffer
			}
			else {
				// find out where to copy from
				short link = getShort(source, linkOffset);
				linkOffset += 2;

				int dist = link & 0x0FFF;
				int copySrc = decodedBytes - (dist + 1);
				int length = ((link >> 12) & 0x0F);

				// determine how many bytes to copy
				if (length == 0) {
					length = (source[sourceOffset] & 0x0FF);
					length += (byte) 0x10;
					sourceOffset++;
				}

				length += 2;

				//	Logger.logfDetail("%-5X : Linking %d bytes from offset %d", decodedBytes, length, dist + 1);

				// copy
				for (int i = 0; i < length; i++) {
					decoded[decodedBytes] = decoded[copySrc + i];
					decodedBytes++;
				}

			}

			currentCommand <<= 1;
			remainingBits--;
		}
		while (decodedBytes < decompressedSize);

		return decoded;
	}

	private static short getShort(byte[] buffer, int start)
	{
		return (short) ((buffer[start + 1] & 0xFF) | (buffer[start] & 0xFF) << 8);
	}

	private static int getInteger(byte[] buffer, int start)
	{
		return (buffer[start + 3] & 0xFF) |
			(buffer[start + 2] & 0xFF) << 8 |
			(buffer[start + 1] & 0xFF) << 16 |
			(buffer[start + 0] & 0xFF) << 24;
	}
}
