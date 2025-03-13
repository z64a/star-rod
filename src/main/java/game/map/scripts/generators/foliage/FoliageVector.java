package game.map.scripts.generators.foliage;

import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;

public class FoliageVector extends FoliageData
{
	public final Foliage owner;
	public final EditableField<String> modelName;

	public FoliageVector(Foliage owner, String name)
	{
		this.owner = owner;

		modelName = EditableFieldFactory.create(name)
			.setCallback((o) -> {
				FoliageInfoPanel.instance().updateFields(owner);
			}).setName("Set Model Name").build();
	}

	@Override
	public FoliageVector deepCopy()
	{
		return new FoliageVector(owner, modelName.get());
	}

	@Override
	public String toString()
	{
		return modelName.get();
	}
}
