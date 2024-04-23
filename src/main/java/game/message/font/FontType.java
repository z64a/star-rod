package game.message.font;

import static game.message.font.FontKey.*;

import org.w3c.dom.Element;

import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public enum FontType implements XmlSerializable
{
	// @formatter:off
	Normal		(0, CharSet.Normal,   0x802EB4F0, 16, 14, 4, new int[] {10,10,10,10}, new int[] {0,-2,0,0}),
	Menus		(1, CharSet.Normal,   0x802EB4FC, 16, 14, 4, new int[] {9,9,9,9}, new int[] {-2,0,0,0}),
	Title		(3, CharSet.Title,    0x802EB590, 16, 14, 1, new int[] {14}, new int[] {0}),
	Subtitle	(4, CharSet.Subtitle, 0x802EB59C, 10, 10, 1, new int[] {10}, new int[] {0});
	// @formatter:on

	public static final int PTR_TO_OFFSET = 0x801DE7C0; // offset = ptr - PTR_TO_OFFSET; ptr = offset + PTR_TO_OFFSET

	public final int key;
	public final CharSet chars;
	private final int ptrData;

	public int lineHeight;
	public int unk;

	// can change among variants
	public final int numVariants;
	public int fullspaceWidth[];
	public int baseHeightOffset[];

	private FontType(int key, CharSet chars, int ptrData, int lineHeight, int unk, int numVariants, int[] fullspaceWidth, int[] baseHeightOffset)
	{
		this.key = key;
		this.chars = chars;
		this.ptrData = ptrData;

		this.lineHeight = lineHeight;
		this.unk = unk;

		this.numVariants = numVariants;
		this.fullspaceWidth = fullspaceWidth;
		this.baseHeightOffset = baseHeightOffset;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		xmr.requiresAttribute(elem, ATTR_UNK);
		xmr.requiresAttribute(elem, ATTR_LINE_HEIGHT);
		xmr.requiresAttribute(elem, ATTR_SPACE_WIDTH);
		xmr.requiresAttribute(elem, ATTR_BASE_HEIGHT);

		unk = xmr.readInt(elem, ATTR_UNK);
		lineHeight = xmr.readInt(elem, ATTR_LINE_HEIGHT);
		fullspaceWidth = xmr.readIntArray(elem, ATTR_SPACE_WIDTH, numVariants);
		baseHeightOffset = xmr.readIntArray(elem, ATTR_BASE_HEIGHT, numVariants);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag fontTag = xmw.createTag(TAG_FONT, true);
		xmw.addAttribute(fontTag, ATTR_NAME, name());

		xmw.addInt(fontTag, ATTR_LINE_HEIGHT, lineHeight);
		xmw.addInt(fontTag, ATTR_UNK, unk);

		xmw.addIntArray(fontTag, ATTR_SPACE_WIDTH, fullspaceWidth);
		xmw.addIntArray(fontTag, ATTR_BASE_HEIGHT, baseHeightOffset);

		xmw.printTag(fontTag);
	}
}
