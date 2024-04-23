package game.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessageAnim
{
	/*
	[Func_1A:00:00:05][Sprite:00:39:1F]
	[Func_1A:00:01:06][Sprite:00:39:20]
	[Func_1A:00:02:05][Sprite:00:39:1F]
	[Func_1A:00:03:06][Sprite:00:39:20]
	[Func_1B:00:00][Func_1C:00]
	 */

	public int spriteID = -1;
	public int[] rasters = new int[4];
	public int[] delays = new int[4];

	private static enum DecodeAnimState
	{
		INIT, DELAY, SPRITE, ALMOST, DONE;
	}

	public MessageAnim(ByteBuffer buffer)
	{
		DecodeAnimState state = DecodeAnimState.INIT;
		int pos = -1;
		boolean hasSpriteID = false;

		while (state != DecodeAnimState.DONE) {
			int arg0, arg1;

			int call = buffer.get() & 0xFF;
			assert (call == 0xFF);
			int func = buffer.get() & 0xFF;

			switch (func) {
				case 0x16: // sprite
					int arg01 = buffer.getShort() & 0xFFFF;
					rasters[pos] = buffer.get() & 0xFF;
					if (!hasSpriteID) {
						spriteID = arg01;
						hasSpriteID = true;
					}
					assert (arg01 == spriteID);
					assert (state == DecodeAnimState.DELAY);
					state = DecodeAnimState.SPRITE;
					break;

				case 0x1A: // delay
					pos++;
					arg0 = buffer.get() & 0xFF;
					arg1 = buffer.get() & 0xFF;
					delays[pos] = buffer.get() & 0xFF;
					assert (pos < 4);
					assert (arg0 == 0);
					assert (arg1 == pos);
					assert (state == DecodeAnimState.INIT || state == DecodeAnimState.SPRITE);
					state = DecodeAnimState.DELAY;
					break;

				case 0x1B: // almost
					arg0 = buffer.get() & 0xFF;
					arg1 = buffer.get() & 0xFF;
					assert (arg0 == 0);
					assert (arg1 == 0);
					assert (state == DecodeAnimState.SPRITE);
					state = DecodeAnimState.ALMOST;
					break;

				case 0x1C: // done
					arg0 = buffer.get() & 0xFF;
					assert (arg0 == 0);
					assert (state == DecodeAnimState.ALMOST);
					state = DecodeAnimState.DONE;
					break;
			}
		}

		assert (pos <= 3);
		if (pos < 3) {
			rasters = Arrays.copyOf(rasters, pos + 1);
			delays = Arrays.copyOf(delays, pos + 1);
		}
	}

	public MessageAnim(int spriteID, Integer[] rasters, Integer[] delays)
	{
		this.spriteID = spriteID;
		this.rasters = new int[rasters.length];
		this.delays = new int[delays.length];
		for (int i = 0; i < rasters.length; i++) {
			this.rasters[i] = rasters[i];
			this.delays[i] = delays[i];
		}
	}

	public String getTag()
	{
		StringBuilder sbr = new StringBuilder(String.format("rasterIDs=0x%X", rasters[0]));
		for (int i = 1; i < rasters.length; i++)
			sbr.append(String.format(",0x%X", rasters[i]));

		StringBuilder sbd = new StringBuilder(String.format("delays=%d", delays[0]));
		for (int i = 1; i < delays.length; i++)
			sbd.append(String.format(",%d", delays[i]));

		return String.format("Animation spriteID=0x%X %s %s", spriteID, sbr.toString(), sbd.toString());
	}

	public List<Byte> getBytes()
	{
		List<Byte> bytes = new ArrayList<>();
		for (int i = 0; i < rasters.length; i++) {
			bytes.add((byte) 0xFF);
			bytes.add((byte) 0x1A);
			bytes.add((byte) 0);
			bytes.add((byte) i);
			bytes.add((byte) delays[i]);
			bytes.add((byte) 0xFF);
			bytes.add((byte) 0x16);
			bytes.add((byte) (spriteID >> 8));
			bytes.add((byte) (spriteID & 0xFF));
			bytes.add((byte) rasters[i]);
		}
		bytes.add((byte) 0xFF);
		bytes.add((byte) 0x1B);
		bytes.add((byte) 0);
		bytes.add((byte) 0);
		bytes.add((byte) 0xFF);
		bytes.add((byte) 0x1C);
		bytes.add((byte) 0);

		return bytes;
	}
}
