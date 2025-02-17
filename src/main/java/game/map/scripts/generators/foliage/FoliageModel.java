package game.map.scripts.generators.foliage;

import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;

public class FoliageModel extends FoliageData
{
	public final Foliage owner;
	public final EditableField<String> modelName;

	public FoliageModel(Foliage owner, String name)
	{
		this.owner = owner;

		modelName = EditableFieldFactory.create(name)
			.setCallback((o) -> {
				FoliageInfoPanel.instance().updateFields(owner);
			}).setName("Set Model Name").build();
	}

	@Override
	public FoliageModel deepCopy()
	{
		return new FoliageModel(owner, modelName.get());
	}

	@Override
	public String toString()
	{
		return modelName.get();
	}
}
