package game.map.shading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import game.map.shading.SpriteShadingEditor.JsonShadingGroup;
import game.map.shading.SpriteShadingEditor.JsonShadingLight;
import game.map.shading.SpriteShadingEditor.JsonShadingProfile;

public class EditableShadingData
{
	protected final List<EditableShadingGroup> groups = new ArrayList<>();
	private boolean modified = false;

	// for deep copying
	private EditableShadingData()
	{}

	public EditableShadingData(JsonShadingGroup[] jsonGroups)
	{
		if (jsonGroups == null)
			return;

		for (JsonShadingGroup jsonGroup : jsonGroups) {
			EditableShadingGroup group = new EditableShadingGroup();
			group.name = jsonGroup.area;

			if (jsonGroup.profiles != null) {
				for (JsonShadingProfile jsonProfile : jsonGroup.profiles) {
					group.profiles.add(new EditableShadingProfile(jsonProfile));
				}
			}

			groups.add(group);
		}
	}

	public EditableShadingProfile find(String name)
	{
		for (EditableShadingGroup group : groups) {
			for (EditableShadingProfile profile : group.profiles) {
				if (Objects.equals(profile.name, name))
					return profile;
			}
		}
		return null;
	}

	public EditableShadingProfile copy(String name)
	{
		EditableShadingProfile p = find(name);
		return (p == null) ? null : p.deepCopy();
	}

	public boolean changed(String originalName, EditableShadingProfile editedCopy)
	{
		EditableShadingProfile current = find(originalName);
		return current != null && !current.deepEquals(editedCopy);
	}

	public boolean update(String originalName, EditableShadingProfile editedCopy)
	{
		for (EditableShadingGroup group : groups) {
			for (int i = 0; i < group.profiles.size(); i++) {
				EditableShadingProfile current = group.profiles.get(i);
				if (Objects.equals(current.name, originalName)) {
					if (current.deepEquals(editedCopy))
						return false;

					group.profiles.set(i, editedCopy.deepCopy());
					modified = true;
					return true;
				}
			}
		}
		return false;
	}

	public EditableShadingData deepCopy()
	{
		EditableShadingData copy = new EditableShadingData();
		for (EditableShadingGroup group : groups)
			copy.groups.add(group.deepCopy());
		return copy;
	}

	public static final class EditableShadingGroup
	{
		protected String name;
		protected final List<EditableShadingProfile> profiles = new ArrayList<>();

		public EditableShadingGroup()
		{}

		public EditableShadingGroup(String name)
		{
			this.name = name;
		}

		public JsonShadingGroup toJson()
		{
			JsonShadingGroup group = new JsonShadingGroup();
			group.area = name;

			group.profiles = new JsonShadingProfile[profiles.size()];
			for (int i = 0; i < profiles.size(); i++)
				group.profiles[i] = profiles.get(i).toJson();

			return group;
		}

		public EditableShadingGroup deepCopy()
		{
			EditableShadingGroup copy = new EditableShadingGroup();
			copy.name = this.name;
			for (EditableShadingProfile profile : profiles)
				copy.profiles.add(profile.deepCopy());
			return copy;
		}

		@Override
		public String toString()
		{
			return (name == null || name.isBlank()) ? "<unnamed group>" : name;
		}
	}

	public static final class EditableShadingProfile
	{
		protected String name;
		public int[] ambient;
		public int power;
		public final List<EditableShadingLight> lights = new ArrayList<>();

		public EditableShadingProfile()
		{}

		private EditableShadingProfile(JsonShadingProfile jsonProfile)
		{
			name = jsonProfile.name;
			ambient = jsonProfile.ambient == null ? null : jsonProfile.ambient.clone();
			power = jsonProfile.power;

			if (jsonProfile.lights != null) {
				for (JsonShadingLight jsonLight : jsonProfile.lights)
					lights.add(new EditableShadingLight(jsonLight));
			}
		}

		public JsonShadingProfile toJson()
		{
			JsonShadingProfile profile = new JsonShadingProfile();
			profile.name = name;
			profile.ambient = (ambient == null) ? null : ambient.clone();
			profile.power = power;

			profile.lights = new JsonShadingLight[lights.size()];
			for (int i = 0; i < lights.size(); i++)
				profile.lights[i] = lights.get(i).toJson();

			return profile;
		}

		public EditableShadingProfile deepCopy()
		{
			EditableShadingProfile copy = new EditableShadingProfile();
			copy.name = this.name;
			copy.ambient = (this.ambient == null) ? null : this.ambient.clone();
			copy.power = this.power;
			for (EditableShadingLight light : lights)
				copy.lights.add(light.deepCopy());
			return copy;
		}

		public boolean deepEquals(EditableShadingProfile other)
		{
			if (other == null)
				return false;

			if (!Objects.equals(name, other.name))
				return false;
			if (!Arrays.equals(ambient, other.ambient))
				return false;
			if (power != other.power)
				return false;
			if (lights.size() != other.lights.size())
				return false;

			for (int i = 0; i < lights.size(); i++) {
				if (!lights.get(i).deepEquals(other.lights.get(i)))
					return false;
			}

			return true;
		}

		@Override
		public String toString()
		{
			return (name == null || name.isBlank()) ? "<unnamed profile>" : name;
		}
	}

	public static final class EditableShadingLight
	{
		public int[] rgb;
		public int[] pos;
		public float falloff;
		public FalloffType falloffType;
		public boolean enabled;

		// for deep copy
		private EditableShadingLight()
		{}

		private EditableShadingLight(JsonShadingLight jsonLight)
		{
			rgb = jsonLight.rgb == null ? null : jsonLight.rgb.clone();
			pos = jsonLight.pos == null ? null : jsonLight.pos.clone();
			falloff = jsonLight.falloff;
			falloffType = jsonLight.mode;
			enabled = jsonLight.enabled == null ? true : jsonLight.enabled;
		}

		public JsonShadingLight toJson()
		{
			JsonShadingLight light = new JsonShadingLight();

			light.rgb = (rgb == null) ? null : rgb.clone();
			light.pos = (pos == null) ? null : pos.clone();
			light.falloff = falloff;
			light.mode = falloffType;

			// omit when disabled
			if (!enabled)
				light.enabled = false;

			return light;
		}

		public EditableShadingLight deepCopy()
		{
			EditableShadingLight copy = new EditableShadingLight();
			copy.rgb = (rgb == null) ? null : rgb.clone();
			copy.pos = (pos == null) ? null : pos.clone();
			copy.falloff = falloff;
			copy.falloffType = falloffType;
			copy.enabled = enabled;
			return copy;
		}

		public boolean deepEquals(EditableShadingLight other)
		{
			if (other == null)
				return false;

			return enabled == other.enabled
				&& Arrays.equals(rgb, other.rgb)
				&& Arrays.equals(pos, other.pos)
				&& Float.compare(falloff, other.falloff) == 0
				&& Objects.equals(falloffType, other.falloffType);
		}
	}

	public boolean isModified()
	{
		// TODO Auto-generated method stub
		return false;
	}

	public JsonShadingGroup[] toJson()
	{
		// TODO Auto-generated method stub
		return null;
	}
}
