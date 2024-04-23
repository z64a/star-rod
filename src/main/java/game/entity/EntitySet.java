package game.entity;

public enum EntitySet
{
	// @formatter:off
	DUMMY					(-1, -1, -1),
	COMMON					(0x102610, 0x10CC10, 0x802E0D90),	// available in all areas
	OVERLAY_STANDARD		(0xE2B530, 0xE2D730, 0x802BAE00),	// (All Other Areas)
	OVERLAY_JUNGLE_RUGGED	(0xE2D730, 0xE2F750, 0x802BAE00),	// (Mt Rugged and Jade Jungle)
	OVERLAY_TOYBOX_DESERT	(0xE2F750, 0xE31530, 0x802BAE00);	// (Dry Dry Desert or Shy Guy's Toybox)
	// @formatter:on

	public final int dmaStart;
	public final int dmaEnd;
	public final int dmaDest;

	private EntitySet(int dmaStart, int dmaEnd, int dmaDest)
	{
		this.dmaStart = dmaStart;
		this.dmaEnd = dmaEnd;
		this.dmaDest = dmaDest;
	}

	public int toOffset(int addr)
	{
		if ((addr < dmaDest) || (addr >= dmaDest + (dmaEnd - dmaStart)))
			return -1;

		return dmaStart + (addr - dmaDest);
	}

	public int toAddress(int offset)
	{
		if ((offset < dmaStart) || (offset >= dmaEnd))
			return -1;

		return dmaDest + (offset - dmaStart);
	}
}
