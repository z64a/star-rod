package app.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IOUtilsSafetyTest
{
	@TempDir
	Path tempDir;

	@Test
	public void stripsUtf8BomFromFirstInputLine() throws IOException
	{
		byte[] input = "\uFEFFfirst\nsecond".getBytes(StandardCharsets.UTF_8);
		List<Line> lines = IOUtils.readPlainInputStream(
			new DummySource("bom.txt"), new ByteArrayInputStream(input));

		assertEquals(List.of("first", "second"), lines.stream().map(line -> line.str).toList());
	}

	@Test
	public void rejectsMalformedUtf8()
	{
		byte[] input = { (byte) 0xC3, (byte) 0x28 };

		assertThrows(InputFileException.class, () -> IOUtils.readPlainInputStream(
			new DummySource("malformed.txt"), new ByteArrayInputStream(input)));
	}

	@Test
	public void atomicallyReplacesTextFileAndCleansTemporaryFile() throws IOException
	{
		Path destination = tempDir.resolve("output.txt");
		Files.writeString(destination, "old", StandardCharsets.UTF_8);

		IOUtils.atomicWriteLines(List.of("first", "second"), destination.toFile());

		assertEquals("first" + System.lineSeparator() + "second" + System.lineSeparator(),
			Files.readString(destination, StandardCharsets.UTF_8));
		try (var files = Files.list(tempDir)) {
			assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("StarRod_")));
		}
	}

	@Test
	public void refusesOutputWhenParentDirectoryDoesNotExist()
	{
		Path destination = tempDir.resolve("missing").resolve("output.txt");

		assertThrows(IOException.class, () -> IOUtils.atomicWriteLines(List.of("data"), destination.toFile()));
		assertFalse(Files.exists(destination));
	}

	@Test
	public void failedAtomicWriterPreservesDestinationAndCleansTemporaryFile() throws IOException
	{
		Path destination = tempDir.resolve("output.txt");
		Files.writeString(destination, "old", StandardCharsets.UTF_8);

		assertThrows(IOException.class, () -> IOUtils.atomicWrite(destination.toFile(), ".txt", temporaryFile -> {
			Files.writeString(temporaryFile.toPath(), "new", StandardCharsets.UTF_8);
			throw new IOException("simulated write failure");
		}));

		assertEquals("old", Files.readString(destination, StandardCharsets.UTF_8));
		try (var files = Files.list(tempDir)) {
			assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("StarRod_")));
		}
	}
}
