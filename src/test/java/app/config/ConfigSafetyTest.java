package app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.config.Options.Scope;
import app.input.InputFileException;

public class ConfigSafetyTest
{
	@TempDir
	Path tempDir;

	@Test
	public void stringValueMayContainAssignmentCharacters() throws IOException
	{
		Path source = tempDir.resolve("main.cfg");
		Files.writeString(source, "GameVersion = custom=variant\n", StandardCharsets.UTF_8);
		Config config = new Config(source.toFile(), Scope.Main);

		config.readConfig();

		assertEquals("custom=variant", config.getString(Options.GameVersion));
	}

	@Test
	public void failedReloadPreservesPreviouslyLoadedSettings() throws IOException
	{
		Path source = tempDir.resolve("main.cfg");
		Files.writeString(source, "GameVersion = valid\n", StandardCharsets.UTF_8);
		Config config = new Config(source.toFile(), Scope.Main);
		config.readConfig();

		Path malformed = tempDir.resolve("malformed.cfg");
		Files.writeString(malformed, "GameVersion = replacement\nmissing-assignment\n", StandardCharsets.UTF_8);

		assertThrows(InputFileException.class, () -> config.readConfig(malformed.toFile()));
		assertEquals("valid", config.getString(Options.GameVersion));
	}
}
