package game.map.shading;

import java.util.ArrayList;
import java.util.function.Consumer;

import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;
import game.map.editor.UpdateProvider;

public class ShadingGroup extends UpdateProvider
{
	private final Consumer<Object> notifyCallback = (o) -> {
		notifyListeners();
	};

	public EditableField<String> name = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Name").build();

	public ArrayList<ShadingProfile> profiles;

	public ShadingGroup()
	{
		profiles = new ArrayList<>();
	}
}
