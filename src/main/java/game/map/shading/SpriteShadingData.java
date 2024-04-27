package game.map.shading;

import java.util.ArrayList;
import java.util.HashMap;

import game.map.editor.commands.AbstractCommand;
import util.IterableListModel;

public class SpriteShadingData
{
	public static final String NO_SHADING_NAME = "None";
	public static final int NO_SHADING_KEY = -1;
	public static final int CUSTOM_GROUP_ID = 13;

	public final IterableListModel<ShadingProfile> listModel;

	public boolean modified = false;

	public SpriteShadingData()
	{
		listModel = new IterableListModel<>();
	}

	public void addProfile(ShadingProfile profile)
	{
		listModel.insertElementAt(profile, 0);
	}

	public boolean removeProfile(ShadingProfile profile)
	{
		return listModel.removeElement(profile);
	}

	public ShadingProfile getShadingProfile(String name)
	{
		if (name.equalsIgnoreCase(NO_SHADING_NAME))
			return null;

		for (ShadingProfile profile : listModel) {
			if (profile == null)
				continue;
			if (name.equalsIgnoreCase(profile.name.get()))
				return profile;
		}

		// not found
		return null;
	}

	public ShadingProfile getShadingProfile(int key)
	{
		if (key == NO_SHADING_KEY)
			return null;

		for (ShadingProfile profile : listModel) {
			if (profile == null)
				continue;
			if (key == profile.key)
				return profile;
		}

		// not found
		return null;
	}

	public String getShadingName(int key)
	{
		if (key == NO_SHADING_KEY)
			return NO_SHADING_NAME;

		ShadingProfile profile = getShadingProfile(key);
		if (profile == null)
			return null;
		else
			return profile.name.get();
	}

	public Integer getShadingKey(String name)
	{
		if (name.equals(NO_SHADING_NAME))
			return NO_SHADING_KEY;

		ShadingProfile profile = getShadingProfile(name);
		if (profile == null)
			return null;
		else
			return profile.key;
	}

	public void createModel(ArrayList<ShadingGroup> groups)
	{
		listModel.clear();

		listModel.addElement(null);
		for (ShadingGroup group : groups) {
			for (ShadingProfile profile : group.profiles) {
				listModel.addElement(profile);
			}
		}
	}

	public ArrayList<ArrayList<ShadingProfile>> getGroupList()
	{
		int maxGroup = -1;
		for (ShadingProfile profile : listModel) {
			if (profile == null)
				continue;
			if (profile.group > maxGroup)
				maxGroup = profile.group;
		}

		ArrayList<ArrayList<ShadingProfile>> groups = new ArrayList<>(maxGroup + 1);
		for (int i = 0; i <= maxGroup; i++)
			groups.add(new ArrayList<ShadingProfile>());

		for (ShadingProfile profile : listModel) {
			if (profile == null)
				continue;
			groups.get(profile.group).add(profile);
		}

		return groups;
	}

	public static final class CreateProfile extends AbstractCommand
	{
		private final SpriteShadingData data;
		private final ShadingProfile profile;

		public CreateProfile(SpriteShadingData data)
		{
			super("Create Shading Profile");
			this.data = data;
			profile = new ShadingProfile(CUSTOM_GROUP_ID, data.assignCustomKeys());
			profile.name.set(String.format("%08X", profile.key));
		}

		public ShadingProfile getProfile()
		{
			return profile;
		}

		@Override
		public boolean modifiesMap()
		{
			return false;
		}

		@Override
		public void exec()
		{
			super.exec();
			data.listModel.addElement(profile);
			data.assignCustomKeys();
			data.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			data.listModel.removeElement(profile);
			data.assignCustomKeys();
			data.modified = true;
		}
	}

	public static final class DeleteProfile extends AbstractCommand
	{
		private final SpriteShadingData data;
		private final ShadingProfile profile;
		private final int index;

		public DeleteProfile(SpriteShadingData data, ShadingProfile profile)
		{
			super("Remove Shading Profile");
			this.data = data;
			this.profile = profile;
			index = data.listModel.indexOf(profile);
		}

		@Override
		public boolean modifiesMap()
		{
			return false;
		}

		@Override
		public boolean shouldExec()
		{
			return index >= 0;
		}

		@Override
		public void exec()
		{
			super.exec();
			data.listModel.remove(index);
			data.assignCustomKeys();
			data.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			data.listModel.add(index, profile);
			data.assignCustomKeys();
			data.modified = true;
		}
	}

	public void validateNames()
	{
		HashMap<String, ShadingProfile> nameMap = new HashMap<>();

		for (ShadingProfile profile : listModel) {
			if (profile == null)
				continue;

			profile.invalidName = false;

			String name = profile.name.get();

			if (name == null || name.isEmpty()) {
				profile.invalidName = true;
				continue;
			}

			if (nameMap.containsKey(name)) {
				ShadingProfile other = nameMap.get(name);
				other.invalidName = true;
				profile.invalidName = true;
			}
			nameMap.put(name, profile);
		}
	}

	public int assignCustomKeys()
	{
		int currentProfileID = 0;
		for (ShadingProfile profile : listModel) {
			if (profile == null)
				continue;

			if (profile.group == CUSTOM_GROUP_ID)
				profile.key = currentProfileID++;
		}
		return currentProfileID;
	}
}
