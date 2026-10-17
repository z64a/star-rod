package game.audio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.commons.io.FileUtils;

import app.input.InputFileException;
import util.Logger;

/** One-time extraction of the US base ROM's audio into the base asset layer. */
public class AudioExtractor
{
	private static final String SENTINEL = ".star_rod_audio_extracted";
	private static final int SBN_OFFSET = 0xF00000;
	private static final int MAX_SBN_SIZE = 0x1000000;

	public static boolean isExtracted(File assetDir)
	{
		return new File(assetDir, SENTINEL).isFile();
	}

	/** Existing sources and binary overrides are kept, including after an interrupted extraction. */
	public static void extract(File romFile, File assetDir) throws IOException
	{
		if (isExtracted(assetDir))
			return;

		Logger.log("Extracting audio from baserom");
		byte[] archive = readArchive(romFile);
		Path staging = Files.createTempDirectory(assetDir.toPath(), ".star_rod_audio_");
		try {
			// Decode everything before installing files, so a decoding failure leaves the assets alone.
			AudioTasks.dump(romFile, archive, staging.toFile());
			installMissing(staging.resolve("audio"), new File(assetDir, "audio").toPath());
			FileUtils.touch(new File(assetDir, SENTINEL));
			Logger.log("Audio extraction complete");
		}
		finally {
			FileUtils.deleteDirectory(staging.toFile());
		}
	}

	private static byte[] readArchive(File romFile) throws IOException
	{
		try (RandomAccessFile rom = new RandomAccessFile(romFile, "r")) {
			if (rom.length() < SBN_OFFSET + 0x40)
				throw new InputFileException(romFile, "the US base ROM is too short to contain its audio archive");
			rom.seek(SBN_OFFSET);
			if (rom.readInt() != 0x53424E20)
				throw new InputFileException(romFile, "the US base ROM has no SBN archive at 0x%X", SBN_OFFSET);
			int size = rom.readInt();
			if (size < 0x40 || size > MAX_SBN_SIZE || SBN_OFFSET + (long) size > rom.length())
				throw new InputFileException(romFile, "invalid SBN size 0x%X", size);
			byte[] data = new byte[size];
			rom.seek(SBN_OFFSET);
			rom.readFully(data);
			return data;
		}
	}

	private static void installMissing(Path source, Path destination) throws IOException
	{
		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
			{
				Path relative = source.relativize(dir);
				if (relative.getNameCount() == 2 && relative.getName(0).toString().equals("bank")
					&& Files.isRegularFile(destination.resolve(relative + ".bk")))
					return FileVisitResult.SKIP_SUBTREE;
				Files.createDirectories(destination.resolve(relative));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
			{
				Path relative = source.relativize(file);
				Path target = destination.resolve(relative);
				if (Files.isRegularFile(target) || hasBinaryOverride(destination, relative))
					return FileVisitResult.CONTINUE;
				try {
					Files.move(file, target);
				}
				catch (FileAlreadyExistsException e) {
					// Keep a file created while extraction was running as well.
					if (!Files.isRegularFile(target))
						throw e;
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static boolean hasBinaryOverride(Path audioDir, Path relative)
	{
		String path = relative.toString().replace(File.separatorChar, '/');
		String binary = null;
		switch (path) {
			case "presets.xml":
				binary = "prg/SET1.prg";
				break;
			case "drums.xml":
				binary = "per/SET1.per";
				break;
			case "sound_effects.xml":
			case "sfx/envelopes.xml":
				binary = "sfx/DAT1.sef";
				break;
			default:
				if (path.startsWith("mseq/") && path.endsWith(".xml"))
					binary = path.substring(0, path.length() - 4) + ".mseq";
				break;
		}
		return binary != null && Files.isRegularFile(audioDir.resolve(binary));
	}
}
