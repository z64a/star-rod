package game.message.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.Environment;
import assets.AssetHandle;
import game.message.Message;

class MessageAssetSafetyTest
{
	@TempDir
	Path tempDir;

	@Test
	void externalEditIsNotOverwrittenAndDirtyStateIsRetained() throws IOException
	{
		Path source = tempDir.resolve("messages.msg");
		Files.writeString(source, "#message:0A:(Test)\n{\nOriginal[End]\n}\n", StandardCharsets.UTF_8);
		long loadedTimestamp = Files.getLastModifiedTime(source).toMillis();

		MessageAsset asset = new MessageAsset(new AssetHandle(tempDir.toFile(), "messages.msg"));
		Message message = asset.messages.get(0);
		message.setMarkup("Edited in Star Rod[End]");
		message.setModified();

		String externalContents = "#message:0A:(Test)\n{\nEdited externally[End]\n}\n";
		Files.writeString(source, externalContents, StandardCharsets.UTF_8);
		Files.setLastModifiedTime(source, FileTime.fromMillis(loadedTimestamp + 5_000));

		assertFalse(asset.saveChanges());
		assertEquals(externalContents, Files.readString(source, StandardCharsets.UTF_8));
		assertTrue(asset.hasModified);
		assertTrue(message.modified);
	}

	@Test
	void successfulSaveReportsSuccessAndClearsDirtyState() throws IOException
	{
		Path source = tempDir.resolve("messages.msg");
		Files.writeString(source, "#message:0A:(Test)\n{\nOriginal[End]\n}\n", StandardCharsets.UTF_8);
		List<File> previousAssetDirectories = Environment.assetDirectories;
		Environment.assetDirectories = List.of(tempDir.toFile());

		try {
			MessageAsset asset = new MessageAsset(new AssetHandle(tempDir.toFile(), "messages.msg"));
			Message message = asset.messages.get(0);
			message.setMarkup("Saved safely[End]");
			message.setModified();

			assertTrue(asset.saveChanges());
			assertTrue(Files.readString(source, StandardCharsets.UTF_8).contains("Saved safely[End]"));
			assertFalse(asset.hasModified);
			assertFalse(message.modified);
		}
		finally {
			Environment.assetDirectories = previousAssetDirectories;
		}
	}

	@Test
	void editDuringSaveIsNotCleared() throws Exception
	{
		Path source = tempDir.resolve("messages.msg");
		Files.writeString(source, "#message:0A:(Test)\n{\nOriginal[End]\n}\n", StandardCharsets.UTF_8);
		List<File> previousAssetDirectories = Environment.assetDirectories;
		Environment.assetDirectories = List.of(tempDir.toFile());
		ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
		CountDownLatch snapshotReady = new CountDownLatch(1);
		CountDownLatch finishSave = new CountDownLatch(1);

		try {
			MessageAsset asset = new MessageAsset(new AssetHandle(tempDir.toFile(), "messages.msg"));
			Message message = asset.messages.get(0);
			message.setMarkup("Queued {snapshot}[End]");
			message.setModified();
			long queuedRevision = asset.getModificationRevision();

			Future<Boolean> saveResult = saveExecutor.submit(() -> asset.saveChanges(queuedRevision, () -> {
				snapshotReady.countDown();
				try {
					if (!finishSave.await(5, TimeUnit.SECONDS))
						throw new AssertionError("Timed out waiting to finish the save");
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new AssertionError("Save was interrupted", e);
				}
			}));

			assertTrue(snapshotReady.await(5, TimeUnit.SECONDS));
			message.setMarkupAndModified("Edited while saving[End]");
			finishSave.countDown();

			assertFalse(saveResult.get(5, TimeUnit.SECONDS));
			String firstSave = Files.readString(source, StandardCharsets.UTF_8);
			assertTrue(firstSave.contains("Queued \\{snapshot\\}[End]"));
			assertFalse(firstSave.contains("Edited while saving[End]"));
			assertEquals("Edited while saving[End]", message.getMarkup());
			assertTrue(asset.hasModified);
			assertTrue(message.modified);

			assertTrue(asset.saveChanges());
			assertTrue(Files.readString(source, StandardCharsets.UTF_8).contains("Edited while saving[End]"));
			assertFalse(asset.hasModified);
			assertFalse(message.modified);
		}
		finally {
			finishSave.countDown();
			saveExecutor.shutdownNow();
			Environment.assetDirectories = previousAssetDirectories;
		}
	}

	@Test
	void failedWriteDoesNotChangeLiveLineRanges() throws IOException
	{
		Path source = tempDir.resolve("messages.msg");
		Files.writeString(source, "#message:0A:(Test)\n{\nOriginal[End]\n}\n", StandardCharsets.UTF_8);
		List<File> previousAssetDirectories = Environment.assetDirectories;
		Environment.assetDirectories = List.of(tempDir.resolve("missing-output-directory").toFile());

		try {
			MessageAsset asset = new MessageAsset(new AssetHandle(tempDir.toFile(), "messages.msg"));
			Message message = asset.messages.get(0);
			int originalStartLine = message.startLineNum;
			int originalEndLine = message.endLineNum;
			message.setMarkupAndModified("A much longer\nmessage body[End]");

			assertFalse(asset.saveChanges());
			assertEquals(originalStartLine, message.startLineNum);
			assertEquals(originalEndLine, message.endLineNum);
			assertTrue(asset.hasModified);
			assertTrue(message.modified);
		}
		finally {
			Environment.assetDirectories = previousAssetDirectories;
		}
	}

	@Test
	void structuralMessageEditsAdvanceAssetRevision() throws IOException
	{
		Path source = tempDir.resolve("messages.msg");
		Files.writeString(source, "#message:0A:(Test)\n{\nOriginal[End]\n}\n", StandardCharsets.UTF_8);
		MessageAsset asset = new MessageAsset(new AssetHandle(tempDir.toFile(), "messages.msg"));
		Message message = asset.messages.get(0);

		long revision = asset.getModificationRevision();
		message.setName("Renamed");
		assertTrue(asset.getModificationRevision() > revision);

		revision = asset.getModificationRevision();
		message.setSection(0x0B);
		assertTrue(asset.getModificationRevision() > revision);

		revision = asset.getModificationRevision();
		asset.addMessage(new Message(asset));
		assertTrue(asset.getModificationRevision() > revision);
		assertTrue(asset.hasModified);
	}
}
