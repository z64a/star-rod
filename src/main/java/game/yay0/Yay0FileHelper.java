package game.yay0;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class Yay0FileHelper
{
	/**
	'Yay0'	Magic word identifying Yay0 compressed block.
	0x04	decompressed size
	0x08	offset to link table
	0x0C	offset to non-linked chunks and count modifiers table
	0x10	packed data

	The packed data is a bitstream (padded to a multiple of 32bits), with each bit having the following meaning:

	0	linked chunk, copy block from the link table (offset 0x0008)
	1	non linked chunk, copy next byte from non-linked chunks and count modifiers table (offset at 0x000c)
	 **/

	public static void decode(File source, File dest) throws IOException
	{
		if (source == null || dest == null)
			throw new IllegalArgumentException("File arguments may not be null!");

		if (!dest.isDirectory())
			throw new IllegalArgumentException("Destination must be a directory!");

		RandomAccessFile raf = new RandomAccessFile(source, "r");

		int magicNumber = raf.readInt();
		assert (magicNumber == 0x59617930); // "Yay0"
		int decompressedSize = raf.readInt();
		int linkOffset = raf.readInt();
		int sourceOffset = raf.readInt();

		byte currentCommand = 0;
		int commandOffset = 16;
		int remainingBits = 0;

		byte[] decoded = new byte[decompressedSize];
		int decodedBytes = 0;

		do {
			// get the next command
			if (remainingBits == 0) {
				raf.seek(commandOffset);
				currentCommand = raf.readByte();
				commandOffset++;
				remainingBits = 8;
			}

			// bit == 1 --> copy directly from source
			if ((currentCommand & 0x80) != 0) {
				raf.seek(sourceOffset);
				decoded[decodedBytes] = raf.readByte();
				sourceOffset++;
				decodedBytes++;

				// bit == 0 --> copy from decoded buffer
			}
			else {
				// find out where to copy from
				raf.seek(linkOffset);
				short link = raf.readShort();
				linkOffset += 2;
				int dist = (link & 0x0FFF);
				int copySrc = decodedBytes - (dist + 1);
				int length = ((link >> 12) & 0x0F);

				// determine how many bytes to copy
				if (length == 0) {
					raf.seek(sourceOffset);
					length = (raf.readByte() & 0x0FF) + 0x10;
					sourceOffset++;
				}

				length += 2;

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

		raf.close();

		FileOutputStream out = new FileOutputStream(new File("./test/hos_bt02_hit.bin"));
		out.write(decoded);
		out.close();
	}
}
