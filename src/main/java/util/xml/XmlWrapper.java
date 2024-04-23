package util.xml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import app.input.IOUtils;
import app.input.InputFileException;
import util.Logger;

public class XmlWrapper
{
	public static interface XmlSerializable
	{
		public void fromXML(XmlReader xmr, Element elem);

		public void toXML(XmlWriter xmw);
	}

	public static class XmlReader
	{
		private final File xmlFile;
		private Element rootElement;

		public XmlReader(File xmlFile)
		{
			this.xmlFile = xmlFile;
			Document document;

			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				document = builder.parse(xmlFile);
			}
			catch (ParserConfigurationException | SAXException | IOException e) {
				throw new InputFileException(xmlFile, e.getMessage());
			}

			document.getDocumentElement().normalize();
			rootElement = document.getDocumentElement();
		}

		public File getSourceFile()
		{ return xmlFile; }

		public void complain(String message)
		{
			throw new InputFileException(xmlFile, message);
		}

		public Element getRootElement()
		{ return rootElement; }

		public NodeList getRootElements(XmlKey key) throws IOException
		{
			return rootElement.getElementsByTagName(key.toString());
		}

		public String getRootAttribute(XmlKey key) throws IOException
		{
			return getAttribute(rootElement, key);
		}

		public String getAttribute(Element elem, XmlKey key)
		{
			String value = elem.getAttribute(key.toString());
			if (value == null)
				complain("Missing attribute for " + key + " in " + elem.getTagName());

			return value.trim();
		}

		public <E extends Enum<E>> E readEnum(Element elem, XmlKey key, Class<E> enumClass)
		{
			String value = getAttribute(elem, key);
			E enumVal = Enum.valueOf(enumClass, value);

			if (enumVal == null)
				complain("Unknown value for enum " + enumClass.getSimpleName() + ": " + value);

			return enumVal;
		}

		public String[] readStringArray(Element elem, XmlKey key, int len)
		{
			String value = getAttribute(elem, key);
			String[] tokens = value.split("\\s*,\\s*");

			if (len > 0 && tokens.length != len)
				complain("Length of array does not equal " + len + ": " + value);

			return tokens;
		}

		public List<String> readStringList(Element elem, XmlKey key)
		{
			String value = getAttribute(elem, key);
			String[] tokens = value.split("\\s*,\\s*");
			return Arrays.asList(tokens);
		}

		public boolean readBoolean(Element elem, XmlKey key)
		{
			String value = getAttribute(elem, key);

			if (value.equalsIgnoreCase("true"))
				return true;
			else if (value.equalsIgnoreCase("false"))
				return false;
			else
				complain("Invalid boolean value for " + key + ": " + value);

			return false;
		}

		public int readInt(Element elem, XmlKey key)
		{
			String value = getAttribute(elem, key);

			int v = 0;
			try {
				v = Integer.parseInt(value);
			}
			catch (NumberFormatException e) {
				complain("Invalid integer value for " + key + ": " + value);
			}
			return v;
		}

		public int readHex(Element elem, XmlKey key)
		{
			String value = getAttribute(elem, key);

			int v = 0;
			try {
				v = (int) Long.parseLong(value, 16);
			}
			catch (NumberFormatException e) {
				complain("Invalid hex value for " + key + ": " + value);
			}
			return v;
		}

		public float readFloat(Element elem, XmlKey key)
		{
			String value = getAttribute(elem, key);

			float v = 0;
			try {
				v = Float.parseFloat(value);
			}
			catch (NumberFormatException e) {
				complain("Invalid float value for " + key + ": " + value);
			}
			return v;
		}

		public double readDouble(Element elem, XmlKey key)
		{
			String value = getAttribute(elem, key);

			double v = 0;
			try {
				v = Double.parseDouble(value);
			}
			catch (NumberFormatException e) {
				complain("Invalid float value for " + key + ": " + value);
			}
			return v;
		}

		public byte[] readByteArray(Element elem, XmlKey key, int len)
		{
			String value = getAttribute(elem, key);
			String[] tokens = value.split("\\s*,\\s*");

			if (len > 0 && tokens.length != len)
				complain("Length of array does not equal " + len + ": " + value);

			byte[] values = new byte[tokens.length];
			int i = 0;
			try {
				for (; i < values.length; i++)
					values[i] = (byte) Integer.parseInt(tokens[i]);
			}
			catch (NumberFormatException e) {
				complain("Invalid byte value for " + key + " array: " + values[i]);
			}
			return values;
		}

		public byte[] readHexByteArray(Element elem, XmlKey key, int len)
		{
			String value = getAttribute(elem, key);
			String[] tokens = value.split("\\s*,\\s*");

			if (len > 0 && tokens.length != len)
				complain("Length of array does not equal " + len + ": " + value);

			byte[] values = new byte[tokens.length];
			int i = 0;
			try {
				for (; i < values.length; i++)
					values[i] = (byte) Integer.parseInt(tokens[i], 16);
			}
			catch (NumberFormatException e) {
				complain("Invalid hex value for " + key + " array: " + values[i]);
			}
			return values;
		}

		public int[] readIntArray(Element elem, XmlKey key, int len)
		{
			String value = getAttribute(elem, key);
			String[] tokens = value.split("\\s*,\\s*");

			if (len > 0 && tokens.length != len)
				complain("Length of array does not equal " + len + ": " + value);

			int[] values = new int[tokens.length];
			int i = 0;
			try {
				for (; i < values.length; i++)
					values[i] = Integer.parseInt(tokens[i]);
			}
			catch (NumberFormatException e) {
				complain("Invalid integer value for " + key + " array: " + values[i]);
			}
			return values;
		}

		public int[] readHexArray(Element elem, XmlKey key)
		{
			String value = getAttribute(elem, key);
			String[] tokens = value.split("\\s*,\\s*");

			return makeHexArray(key, tokens);
		}

		public int[] readHexArray(Element elem, XmlKey key, int len)
		{
			String value = getAttribute(elem, key);
			String[] tokens = value.split("\\s*,\\s*");

			if (len > 0 && tokens.length != len)
				complain("Length of array does not equal " + len + ": " + value);

			return makeHexArray(key, tokens);
		}

		private int[] makeHexArray(XmlKey key, String[] tokens)
		{
			int[] values = new int[tokens.length];
			int i = 0;
			try {
				for (; i < values.length; i++)
					values[i] = (int) Long.parseLong(tokens[i], 16);
			}
			catch (NumberFormatException e) {
				complain("Invalid hex value for " + key + " array: " + values[i]);
			}
			return values;
		}

		public float[] readFloatArray(Element elem, XmlKey key, int len)
		{
			String value = getAttribute(elem, key);
			String[] tokens = value.split("\\s*,\\s*");

			if (len > 0 && tokens.length != len)
				complain("Length of array does not equal " + len + ": " + value);

			float[] values = new float[tokens.length];
			int i = 0;
			try {
				for (; i < values.length; i++)
					values[i] = Float.parseFloat(tokens[i]);
			}
			catch (NumberFormatException e) {
				complain("Invalid float value for " + key + " array: " + values[i]);
			}
			return values;
		}

		public double[] readDoubleArray(Element elem, XmlKey key, int len)
		{
			String value = getAttribute(elem, key);
			String[] tokens = value.split("\\s*,\\s*");

			if (len > 0 && tokens.length != len)
				complain("Length of array does not equal " + len + ": " + value);

			double[] values = new double[tokens.length];
			int i = 0;
			try {
				for (; i < values.length; i++)
					values[i] = Double.parseDouble(tokens[i]);
			}
			catch (NumberFormatException e) {
				complain("Invalid float value for " + key + " array: " + values[i]);
			}
			return values;
		}

		/**
		 * Returns a single child element if it exists.
		 * Complains if more than one is found.
		 * Returns null if none are found.
		 */
		public Element getUniqueTag(Element elem, XmlKey key)
		{
			String keyname = key.toString();
			List<Element> found = new LinkedList<>();

			for (Node child = elem.getFirstChild(); child != null; child = child.getNextSibling()) {
				if (child instanceof Element && keyname.equals(child.getNodeName()))
					found.add((Element) child);
			}

			if (found.size() > 1)
				complain(elem.getTagName() + " cannot have more than one " + key);
			if (found.size() == 0)
				return null;
			else
				return found.get(0);
		}

		public List<Element> getTags(Element elem, XmlKey key)
		{
			String keyname = key.toString();
			List<Element> found = new LinkedList<>();

			for (Node child = elem.getFirstChild(); child != null; child = child.getNextSibling()) {
				if (child instanceof Element && keyname.equals(child.getNodeName()))
					found.add((Element) child);
			}

			return found;
		}

		public List<Element> getRequiredTags(Element elem, XmlKey key)
		{
			List<Element> tags = getTags(elem, key);

			if (tags.size() < 1)
				complain(elem.getTagName() + " is missing required tag: " + key);
			return tags;
		}

		public Element getUniqueRequiredTag(Element elem, XmlKey key)
		{
			List<Element> tags = getTags(elem, key);

			if (tags.size() < 1)
				complain(elem.getTagName() + " is missing required tag: " + key);
			if (tags.size() != 1)
				complain(elem.getTagName() + " cannot have more than one " + key);
			return tags.get(0);
		}

		public boolean hasAttribute(Element elem, XmlKey key)
		{
			if (!elem.hasAttribute(key.toString()))
				return false;

			return !elem.getAttribute(key.toString()).isBlank();
		}

		public void requiresAttribute(Element elem, XmlKey key)
		{
			if (!elem.hasAttribute(key.toString()))
				complain(elem.getTagName() + " is missing required attribute: " + key);

			if (elem.getAttribute(key.toString()).isBlank())
				complain(elem.getTagName() + " has blank required attribute: " + key);
		}

		public void requires(NodeList nodes, XmlKey key)
		{
			if (nodes.getLength() < 1)
				complain(key.toString() + " list cannot be empty.");
		}

		public void limit(NodeList nodes, XmlKey key, int limit)
		{
			if (nodes.getLength() > limit)
				complain(key.toString() + " list cannot contain more than " + limit + "elements.");
		}
	}

	public static class XmlTag
	{
		private final String name;
		private final boolean selfClose;
		private final List<String> attributes;
		private final PrintWriter pw;

		private XmlTag(XmlKey tag, boolean selfClose, PrintWriter pw)
		{
			name = tag.toString();
			this.selfClose = selfClose;
			attributes = new LinkedList<>();
			this.pw = pw;
		}

		private void add(String attribute)
		{
			attributes.add(attribute);
		}

		private void print()
		{
			if (!selfClose)
				throw new IllegalStateException("Only print self-closing tags!");

			pw.print("<" + name);
			for (String attr : attributes)
				pw.print(" " + attr);
			pw.print("/>");
		}

		private void printOpen()
		{
			if (selfClose)
				throw new IllegalStateException("Don't open a self-closing tag!");

			pw.print("<" + name);
			for (String attr : attributes)
				pw.print(" " + attr);
			pw.print(">");
		}

		private void printClose()
		{
			if (selfClose)
				throw new IllegalStateException("Don't close a self-closing tag!");

			pw.print("</" + name + ">");
		}
	}

	public static class XmlWriter implements AutoCloseable
	{
		private File file;
		private File temp;
		private PrintWriter pw;
		private String indentString = "";

		public XmlWriter(File xmlFile, String ... headerComments) throws FileNotFoundException
		{
			try {
				temp = File.createTempFile(FilenameUtils.getBaseName("StarRod_" + xmlFile.getName()), null);
				temp.deleteOnExit();

				file = xmlFile;
				pw = IOUtils.getBufferedPrintWriter(temp);
				pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

				for (String comment : headerComments)
					pw.println("<!-- " + comment + " -->");
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void save()
		{
			if (pw != null) {
				pw.close();

				try {
					FileUtils.copyFile(temp, file);
				}
				catch (IOException e) {
					Logger.printStackTrace(e);
				}
			}
		}

		@Override
		public void close()
		{
			if (temp != null)
				temp.delete();
			if (pw != null)
				pw.close();
		}

		public void printComment(String comment)
		{
			pw.print(indentString);
			pw.println("<!-- " + comment + " -->");
		}

		public XmlTag createTag(XmlKey key, boolean selfClose)
		{
			return new XmlTag(key, selfClose, pw);
		}

		public void printTag(XmlTag tag)
		{
			printTag(tag, null);
		}

		public void printTag(XmlTag tag, String comment)
		{
			pw.print(indentString);
			tag.print();

			if (comment != null)
				pw.println(" <!-- " + comment + "-->");
			else
				pw.println();
		}

		public void openTag(XmlTag tag)
		{
			openTag(tag, null);
		}

		public void openTag(XmlTag tag, String comment)
		{
			pw.print(indentString);
			tag.printOpen();

			if (comment != null)
				pw.println(" <!-- " + comment + "-->");
			else
				pw.println();

			if (!tag.selfClose)
				indentString = indentString + "\t";
		}

		public void closeTag(XmlTag tag)
		{
			closeTag(tag, null);
		}

		public void closeTag(XmlTag tag, String comment)
		{
			indentString = indentString.substring(1);

			pw.print(indentString);
			tag.printClose();

			if (comment != null)
				pw.println(" <!-- " + comment + "-->");
			else
				pw.println();
		}

		public void addLineBreak(XmlTag tag)
		{
			tag.add(System.lineSeparator() + indentString + "\t");
		}

		public void addNonEmptyAttribute(XmlTag tag, XmlKey key, String value)
		{
			if (value != null && !value.isBlank())
				tag.add(key + "=\"" + value + "\"");
		}

		public void addAttribute(XmlTag tag, XmlKey key, String value)
		{
			tag.add(key + "=\"" + value + "\"");
		}

		public void addAttribute(XmlTag tag, XmlKey key, String fmt, Object ... args)
		{
			tag.add(String.format(key + "=\"" + fmt + "\"", args));
		}

		public <E extends Enum<?>> void addEnum(XmlTag tag, XmlKey key, E val)
		{
			addAttribute(tag, key, val.name());
		}

		public void addBoolean(XmlTag tag, XmlKey key, boolean b)
		{
			addAttribute(tag, key, b ? "true" : "false");
		}

		public void addInt(XmlTag tag, XmlKey key, int i)
		{
			addAttribute(tag, key, String.format("%d", i));
		}

		public void addHex(XmlTag tag, XmlKey key, int i)
		{
			addAttribute(tag, key, String.format("%X", i));
		}

		public void addHex(XmlTag tag, XmlKey key, String fmt, int i)
		{
			addAttribute(tag, key, String.format(fmt, i));
		}

		public void addFloat(XmlTag tag, XmlKey key, float f)
		{
			addAttribute(tag, key, String.format(Locale.US, "%f", f));
		}

		public void addDouble(XmlTag tag, XmlKey key, double d)
		{
			addAttribute(tag, key, String.format(Locale.US, "%f", d));
		}

		public void addByteArray(XmlTag tag, XmlKey key, byte ... values)
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < values.length; i++) {
				if (i == 0)
					sb.append(String.format("%d", values[i]));
				else
					sb.append(String.format(",%d", values[i]));
			}

			addAttribute(tag, key, sb.toString());
		}

		public void addIntArray(XmlTag tag, XmlKey key, int ... values)
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < values.length; i++) {
				if (i == 0)
					sb.append(String.format("%d", values[i]));
				else
					sb.append(String.format(",%d", values[i]));
			}

			addAttribute(tag, key, sb.toString());
		}

		public void addHexArray(XmlTag tag, XmlKey key, int ... values)
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < values.length; i++) {
				if (i == 0)
					sb.append(String.format("%X", values[i]));
				else
					sb.append(String.format(",%X", values[i]));
			}

			addAttribute(tag, key, sb.toString());
		}

		public void addFloatArray(XmlTag tag, XmlKey key, float ... values)
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < values.length; i++) {
				if (i == 0)
					sb.append(String.format(Locale.US, "%f", values[i]));
				else
					sb.append(String.format(Locale.US, ",%f", values[i]));
			}

			addAttribute(tag, key, sb.toString());
		}

		public void addDoubleArray(XmlTag tag, XmlKey key, double ... values)
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < values.length; i++) {
				if (i == 0)
					sb.append(String.format(Locale.US, "%f", values[i]));
				else
					sb.append(String.format(Locale.US, ",%f", values[i]));
			}

			addAttribute(tag, key, sb.toString());
		}

		public void addStringArray(XmlTag tag, XmlKey key, String ... values)
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < values.length; i++) {
				if (i == 0)
					sb.append(values[i]);
				else
					sb.append(",").append(values[i]);
			}

			addAttribute(tag, key, sb.toString());
		}

		public void addStringList(XmlTag tag, XmlKey key, List<String> values)
		{
			StringBuilder sb = new StringBuilder();
			int i = 0;
			for (String s : values) {
				if (i++ == 0)
					sb.append(s);
				else
					sb.append(",").append(s);
			}

			addAttribute(tag, key, sb.toString());
		}
	}
}
