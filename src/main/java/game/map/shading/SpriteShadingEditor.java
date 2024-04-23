package game.map.shading;

import static app.Directories.FN_SPRITE_SHADING;
import static game.map.shading.ShadingKey.TAG_GROUP;
import static game.map.shading.ShadingKey.TAG_SPRITE_SHADING;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import app.StarRodException;
import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetSubdir;
import util.Logger;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class SpriteShadingEditor
{
	public static void saveShadingProfiles(SpriteShadingData data) throws IOException
	{
		AssetHandle ah = AssetManager.get(AssetSubdir.SPRITE, FN_SPRITE_SHADING);
		saveShadingProfiles(ah, data);
	}

	private static void saveShadingProfiles(File xmlFile, SpriteShadingData data) throws IOException
	{
		data.validateNames();
		data.assignCustomKeys();

		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			XmlTag rootTag = xmw.createTag(TAG_SPRITE_SHADING, false);
			xmw.openTag(rootTag);

			ArrayList<ArrayList<ShadingProfile>> groups = data.getGroupList();

			for (ArrayList<ShadingProfile> profileList : groups) {
				XmlTag groupTag = xmw.createTag(TAG_GROUP, false);
				xmw.openTag(groupTag);

				for (ShadingProfile profile : profileList)
					profile.toXML(xmw);

				xmw.closeTag(groupTag);
			}

			xmw.closeTag(rootTag);
			xmw.save();
			data.modified = false;
		}
	}

	public static SpriteShadingData loadData()
	{
		AssetHandle ah = AssetManager.get(AssetSubdir.SPRITE, FN_SPRITE_SHADING);
		if (!ah.exists())
			throw new StarRodException("Could not find sprite shading definitions!");

		SpriteShadingData profileData;

		try {
			profileData = SpriteShadingEditor.readJSON(ah);
			profileData.validateNames();
			profileData.assignCustomKeys();
			Logger.logf("Loaded %d shading profiles.", profileData.listModel.getSize());
		}
		catch (IOException e) {
			Logger.logError(e.getMessage().replaceAll("\\r?\\n", " "));
			profileData = null;
		}

		return profileData;
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
		String mode;
	}

	private static SpriteShadingData readJSON(File jsonFile) throws IOException
	{
		Gson gson = new Gson();
		JsonReader jsonReader = new JsonReader(new FileReader(jsonFile));
		JsonShadingGroup[] jsonGroups = gson.fromJson(jsonReader, JsonShadingGroup[].class);

		SpriteShadingData shadingData = new SpriteShadingData();
		ArrayList<ShadingGroup> groups = new ArrayList<>();

		for (int i = 0; i < jsonGroups.length; i++) {
			JsonShadingGroup jsonGroup = jsonGroups[i];

			ShadingGroup group = new ShadingGroup();
			groups.add(group);
			group.name.set(jsonGroup.area);

			for (int j = 0; j < jsonGroup.profiles.length; j++) {
				JsonShadingProfile jsonProfile = jsonGroup.profiles[j];
				ShadingProfile profile = ShadingProfile.read(jsonProfile, i, j);
				group.profiles.add(profile);
			}
		}

		shadingData.createModel(groups);
		return shadingData;
	}
}
