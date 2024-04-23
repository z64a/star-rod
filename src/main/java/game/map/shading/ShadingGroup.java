package game.map.shading;

import java.util.ArrayList;
import java.util.function.Consumer;

import game.map.editor.UpdateProvider;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;

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
