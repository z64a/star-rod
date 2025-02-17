package game.map.scripts;

import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;
import common.commands.EditableField.StandardBoolName;

public class FogSettings
{
	public EditableField<Boolean> enabled;

	public EditableField<Integer> start;
	public EditableField<Integer> end;

	public EditableField<Integer> R;
	public EditableField<Integer> G;
	public EditableField<Integer> B;
	public EditableField<Integer> A;

	public FogSettings(ScriptData data)
	{
		enabled = EditableFieldFactory.create(false)
			.setCallback(data.notifyCamera).setName(new StandardBoolName("Fog")).build();

		start = EditableFieldFactory.create(950)
			.setCallback(data.notifyCamera).setName("Set Fog Start").build();

		end = EditableFieldFactory.create(1000)
			.setCallback(data.notifyCamera).setName("Set Fog End").build();

		R = EditableFieldFactory.create(10)
			.setCallback(data.notifyCamera).setName("Set Fog Red").build();

		G = EditableFieldFactory.create(10)
			.setCallback(data.notifyCamera).setName("Set Fog Green").build();

		B = EditableFieldFactory.create(10)
			.setCallback(data.notifyCamera).setName("Set Fog Blue").build();

		A = EditableFieldFactory.create(255)
			.setCallback(data.notifyCamera).setName("Set Fog Alpha").build();
	}

	public void load(int[] packed)
	{
		enabled.set(packed[0] == 1);
		R.set(packed[1]);
		G.set(packed[2]);
		B.set(packed[3]);
		A.set(packed[4]);
		start.set(packed[5]);
		end.set(packed[6]);
	}

	public int[] pack()
	{
		int[] packed = new int[7];
		packed[0] = enabled.get() ? 1 : 0;
		packed[1] = R.get();
		packed[2] = G.get();
		packed[3] = B.get();
		packed[4] = A.get();
		packed[5] = start.get();
		packed[6] = end.get();
		return packed;
	}
}
