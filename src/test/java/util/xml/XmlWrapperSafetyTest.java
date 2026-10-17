package util.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.input.InputFileException;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class XmlWrapperSafetyTest
{
	private enum Key implements XmlKey
	{
		ROOT;

		@Override
		public String toString()
		{
			return "Root";
		}
	}

	@TempDir
	Path tempDir;

	@Test
	public void rejectsDocumentTypeDeclarations()
	{
		String xml = "<!DOCTYPE Root [<!ENTITY xxe SYSTEM \"file:///does-not-matter\">]><Root>&xxe;</Root>";

		assertThrows(InputFileException.class, () -> new XmlReader(
			new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "unsafe.xml"));
	}

	@Test
	public void abandonedWriterDoesNotReplaceDestination() throws IOException
	{
		Path destination = tempDir.resolve("output.xml");
		Files.writeString(destination, "original", StandardCharsets.UTF_8);

		try (XmlWriter writer = new XmlWriter(destination.toFile())) {
			XmlTag root = writer.createTag(Key.ROOT, true);
			writer.printTag(root);
		}

		assertEquals("original", Files.readString(destination, StandardCharsets.UTF_8));
		try (var files = Files.list(tempDir)) {
			assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("StarRod_")));
		}
	}

	@Test
	public void stringAttributesRoundTripSpecialCharactersAndWhitespace() throws IOException
	{
		Path destination = tempDir.resolve("attributes.xml");
		String text = "Mario & Luigi <test> \"quoted\"\nnext\tline\r";
		try (XmlWriter writer = new XmlWriter(destination.toFile())) {
			XmlTag root = writer.createTag(Key.ROOT, true);
			writer.addAttribute(root, Key.ROOT, text);
			writer.printTag(root);
			writer.save();
		}
		XmlReader reader = new XmlReader(destination.toFile());
		assertEquals(text, reader.getRootElement().getAttribute(Key.ROOT.toString()));
	}

	@Test
	public void saveAtomicallyReplacesDestination() throws IOException
	{
		Path destination = tempDir.resolve("output.xml");
		Files.writeString(destination, "original", StandardCharsets.UTF_8);

		try (XmlWriter writer = new XmlWriter(destination.toFile())) {
			XmlTag root = writer.createTag(Key.ROOT, true);
			writer.printTag(root);
			writer.save();
		}

		XmlReader reader = new XmlReader(destination.toFile());
		assertEquals("Root", reader.getRootElement().getTagName());
	}
}
