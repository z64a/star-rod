package game.map.editor.commands;

import java.util.HashMap;

import common.commands.AbstractCommand;
import game.map.shape.Model;
import game.texture.ModelTexture;

public class ApplyTextureToList extends AbstractCommand
{
	private final Iterable<Model> modelList;
	private final HashMap<Model, ModelTexture> textureMap;
	private final ModelTexture newTexture;

	public ApplyTextureToList(Iterable<Model> modelList, ModelTexture newTexture)
	{
		super("Apply Texture to List");
		this.modelList = modelList;
		this.newTexture = newTexture;
		textureMap = new HashMap<>();

		for (Model mdl : this.modelList) {
			textureMap.put(mdl, mdl.getMesh().texture);
		}
	}

	@Override
	public void exec()
	{
		super.exec();

		for (Model mdl : this.modelList)
			mdl.getMesh().changeTexture(newTexture);
	}

	@Override
	public void undo()
	{
		super.undo();

		for (Model mdl : this.modelList)
			mdl.getMesh().changeTexture(textureMap.get(mdl));
	}
}
