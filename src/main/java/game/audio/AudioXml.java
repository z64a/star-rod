package game.audio;

import org.w3c.dom.Element;

import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

/** Range-checked attribute access for the audio XML files. */
public abstract class AudioXml
{
	public static int readInt(XmlReader xmr, Element elem, XmlKey key, int min, int max)
	{
		xmr.requiresAttribute(elem, key);
		return check(xmr, key, xmr.readInt(elem, key), min, max);
	}

	public static int readInt(XmlReader xmr, Element elem, XmlKey key, int defaultValue, int min, int max)
	{
		return xmr.hasAttribute(elem, key) ? readInt(xmr, elem, key, min, max) : defaultValue;
	}

	public static int readHex(XmlReader xmr, Element elem, XmlKey key, int min, int max)
	{
		xmr.requiresAttribute(elem, key);
		String text = xmr.getAttribute(elem, key);
		if (!text.matches("-?[0-9A-Fa-f]{1,8}"))
			xmr.complain(key + " must be a hexadecimal number: " + text);
		return check(xmr, key, (int) Long.parseLong(text, 16), min, max);
	}

	/** Writes a signed hexadecimal number with at least the given number of digits. */
	public static void addHex(XmlWriter xmw, XmlTag tag, XmlKey key, int digits, int value)
	{
		xmw.addAttribute(tag, key, (value < 0 ? "-" : "") + String.format("%0" + digits + "X", Math.abs((long) value)));
	}

	private static int check(XmlReader xmr, XmlKey key, int value, int min, int max)
	{
		if (value < min || value > max)
			xmr.complain(key + " must be from " + min + " to " + max + ": " + value);
		return value;
	}
}
