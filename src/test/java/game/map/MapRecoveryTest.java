package game.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import app.Directories;
import app.Environment;
import assets.AssetManager;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.shading.FalloffType;

class MapRecoveryTest
{
	@TempDir
	Path tempDir;

	private Field projectPathField;
	private String previousProjectPath;
	private List<File> previousAssetDirectories;

	@BeforeEach
	void useTemporaryProject() throws Exception
	{
		projectPathField = Directories.class.getDeclaredField("projPath");
		projectPathField.setAccessible(true);
		previousProjectPath = (String) projectPathField.get(null);
		previousAssetDirectories = Environment.assetDirectories;
		Directories.setProjectDirectory(tempDir.toString());
		Environment.assetDirectories = List.of(tempDir.resolve("assets").toFile());
	}

	@AfterEach
	void restoreProject() throws Exception
	{
		projectPathField.set(null, previousProjectPath);
		Environment.assetDirectories = previousAssetDirectories;
	}

	private Map createMap(String name)
	{
		Map map = new Map(name);
		map.texName = "test_tex";
		return map;
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void recoveryRetainsUnsavedFeaturesWithoutChangingSavedFiles(boolean areaDirectories) throws Exception
	{
		if (areaDirectories)
			Files.createDirectories(tempDir.resolve("src/world/area/other/mac_00"));
		Map map = createMap("mac_00");
		map.features.locationName.set("Saved location");
		map.saveMap();
		Path source = AssetManager.getSaveMapFile(map.getName()).toPath();
		Path features = map.getProjDir().toPath().resolve("features.json");
		byte[] savedSource = Files.readAllBytes(source);
		byte[] savedFeatures = Files.readAllBytes(features);
		long savedTimestamp = map.lastModified;

		map.features.locationName.set("Unsaved <location> & camera");
		map.features.camVfov.set(64);
		map.features.camEnabledLast = true;
		map.features.hasSpriteShading.set(true);
		map.features.shadingProfileName.set("unsaved-profile");
		map.features.shadingBaseColor.set(0x123456);
		map.features.shadingOffset.set(71);
		map.markerTree.create(new Marker("Unsaved entry", MarkerType.Entry, 10, 20, 30, 45));
		Marker light = new Marker("Unsaved light", MarkerType.Light, 40, 50, 60, 0);
		light.lightComponent.color.set(0xABCDEF);
		light.lightComponent.falloffType = FalloffType.Linear;
		light.lightComponent.setByCoeff(0.025f);
		light.lightComponent.enabled.set(false);
		map.markerTree.create(light);
		map.modified = true;

		for (String suffix : List.of(Directories.MAP_BACKUP_SUFFIX, Directories.MAP_CRASH_SUFFIX)) {
			File backup = AssetManager.getMapRecoveryFile(map.getName(), suffix);
			map.saveBackup(backup);
			assertEquals(source.getParent(), backup.toPath().getParent());
			assertEquals("mac_00", map.getName());
			assertEquals(savedTimestamp, map.lastModified);
			assertTrue(map.modified);
			assertArrayEquals(savedSource, Files.readAllBytes(source));
			assertArrayEquals(savedFeatures, Files.readAllBytes(features));

			Map recovered = Map.loadMap(backup);
			assertEquals("mac_00", recovered.getName());
			assertTrue(recovered.modified);
			assertEquals("Unsaved <location> & camera", recovered.features.locationName.get());
			assertEquals(64, recovered.features.camVfov.get());
			assertTrue(recovered.features.camEnabledLast);
			assertEquals("unsaved-profile", recovered.features.shadingProfileName.get());
			assertEquals(0x123456, recovered.features.shadingBaseColor.get());
			assertEquals(71, recovered.features.shadingOffset.get());
			assertTrue(recovered.markerTree.getList().stream().anyMatch(m -> m.getName().equals("Unsaved entry")));
			Marker recoveredLight = recovered.markerTree.getList().stream()
				.filter(m -> m.getName().equals("Unsaved light")).findFirst().orElseThrow();
			assertEquals(0xABCDEF, recoveredLight.lightComponent.color.get());
			assertEquals(FalloffType.Linear, recoveredLight.lightComponent.falloffType);
			assertEquals(0.025f, recoveredLight.lightComponent.falloffCoeff);
			assertFalse(recoveredLight.lightComponent.enabled.get());

			Map geometryOnly = Map.loadMapWithoutFeatures(backup);
			assertFalse(geometryOnly.markerTree.getList().stream().anyMatch(m -> m.getName().equals("Unsaved entry")));
		}

		Map saved = Map.loadMap(source.toFile());
		assertFalse(saved.modified);
		assertEquals("Saved location", saved.features.locationName.get());
		assertFalse(Files.readString(source).contains("backup=\"true\""));
	}

	@Test
	void saveAsUsesTheSameAreaDirectoryAsLoading() throws Exception
	{
		Path targetDirectory = tempDir.resolve("src/world/area/other/jan_01");
		Files.createDirectories(targetDirectory);
		Map map = createMap("mac_00");
		map.features.locationName.set("New map location");
		File target = AssetManager.getSaveMapFile("jan_01");
		map.saveMapAs(target);

		assertEquals("jan_01", map.getName());
		assertEquals(targetDirectory, map.getProjDir().toPath());
		assertTrue(Files.isRegularFile(targetDirectory.resolve("features.json")));
		assertFalse(Files.exists(tempDir.resolve("src/world/area_jan/jan_01/features.json")));
		assertEquals("New map location", Map.loadMap(target).features.locationName.get());
	}

	@Test
	void battleStageFeaturesUseTheStageDirectory() throws Exception
	{
		Map map = createMap("mac_bt01");
		map.saveMap();

		Path directory = tempDir.resolve("src/battle/common/stage/area_mac/mac_bt01");
		assertEquals(directory, map.getProjDir().toPath());
		assertTrue(Files.isRegularFile(directory.resolve("features.json")));
		assertFalse(Files.exists(tempDir.resolve("src/world/area_mac/mac_bt01/features.json")));
	}
}
