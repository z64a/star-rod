package game.map.shading;

import static app.Directories.FN_SPRITE_SHADING;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import app.Environment;
import app.StarRodException;
import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetSubdir;
import util.Logger;

public class SpriteShadingEditor
{
	private static final Gson SHADING_GSON = new GsonBuilder().setPrettyPrinting().create();

	private static void toJson(JsonShadingGroup[] groups, File file) throws IOException
	{
		try (Writer writer = new FileWriter(file)) {
			SHADING_GSON.toJson(groups, writer);
		}
	}

	private static JsonShadingGroup[] fromJson(File file) throws IOException
	{
		try (JsonReader jsonReader = new JsonReader(new BufferedReader(new FileReader(file)))) {
			return SHADING_GSON.fromJson(jsonReader, JsonShadingGroup[].class);
		}
	}

	public static class JsonShadingGroup
	{
		String area;
		JsonShadingProfile[] profiles;
	}

	public static class JsonShadingProfile
	{
		String name;
		int[] ambient;
		int power;
		JsonShadingLight[] lights;
	}

	public static class JsonShadingLight
	{
		int[] rgb;
		int[] pos;
		float falloff;
		FalloffType mode;
		Boolean enabled; // can be omitted, defaults to true
	}

	public static EditableShadingData load()
	{
		assert (!Environment.isDX());

		AssetHandle ah = AssetManager.get(AssetSubdir.SPRITE, FN_SPRITE_SHADING);
		if (!ah.exists())
			throw new StarRodException("Could not find sprite shading definitions!");

		EditableShadingData profileData = null;

		try {
			JsonShadingGroup[] groups = fromJson(ah);
			profileData = new EditableShadingData(groups);
			Logger.logf("Loaded shading profiles.");
		}
		catch (IOException e) {
			Logger.logError(e.getMessage().replaceAll("\\r?\\n", " "));
		}

		return profileData;
	}

	public static void save(EditableShadingData spriteShading) throws IOException
	{
		assert (!Environment.isDX());

		AssetHandle ah = AssetManager.get(AssetSubdir.SPRITE, FN_SPRITE_SHADING);
		if (!ah.exists())
			throw new StarRodException("Could not find sprite shading definitions!");

		JsonShadingGroup[] groups = spriteShading.toJson();
		toJson(groups, ah);
	}
}
