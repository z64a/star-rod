package game.map.editor.commands;

import game.map.shape.Model;
import game.texture.ModelTexture;

public class ApplyTexture extends AbstractCommand
{
	private Model mdl;
	private ModelTexture oldTexture;
	private ModelTexture newTexture;

	public ApplyTexture(Model mdl, ModelTexture selectedTexture)
	{
		super("Apply Texture");
		this.mdl = mdl;
		this.newTexture = selectedTexture;
		this.oldTexture = mdl.getMesh().texture;
	}

	@Override
	public void exec()
	{
		super.exec();
		mdl.getMesh().changeTexture(newTexture);
	}

	@Override
	public void undo()
	{
		super.undo();
		mdl.getMesh().changeTexture(oldTexture);
	}
}
