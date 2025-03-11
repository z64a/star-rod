package game.sprite.editor.commands;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.sprite.editor.SpriteEditor;

public class CreatePalette extends AbstractCommand
{
	private final Sprite sprite;
	private final SpritePalette pal;
	private final int pos;

	public CreatePalette(String name, Sprite sprite, SpritePalette pal)
	{
		super(name);
		this.sprite = sprite;
		this.pal = pal;
		pos = sprite.palettes.size();
	}

	public CreatePalette(String name, Sprite sprite, SpritePalette pal, int pos)
	{
		super(name);
		this.sprite = sprite;
		this.pal = pal;
		this.pos = pos;
	}

	@Override
	public void exec()
	{
		super.exec();

		SpriteEditor.instance().palBoxRegistry.lockBoxes();

		sprite.palettes.add(pos, pal);
		sprite.reindex();
		sprite.incrementModified();

		SpriteEditor.instance().palBoxRegistry.updateModels(true);
		SpriteEditor.instance().palBoxRegistry.unlockBoxes();
	}

	@Override
	public void undo()
	{
		super.undo();

		SpriteEditor.instance().palBoxRegistry.lockBoxes();

		sprite.palettes.remove(pos);
		sprite.reindex();
		sprite.decrementModified();

		SpriteEditor.instance().palBoxRegistry.updateModels(true);
		SpriteEditor.instance().palBoxRegistry.unlockBoxes();
	}
}
