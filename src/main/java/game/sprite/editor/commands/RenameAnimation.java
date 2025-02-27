package game.sprite.editor.commands;

import java.awt.Component;

import common.commands.AbstractCommand;
import game.sprite.SpriteAnimation;

public class RenameAnimation extends AbstractCommand
{
	private final Component ui;
	private final SpriteAnimation anim;
	private final String newName;
	private final String oldName;

	public RenameAnimation(Component ui, SpriteAnimation anim, String newName)
	{
		super("Rename Animation");

		this.ui = ui;
		this.anim = anim;
		this.newName = newName;
		this.oldName = anim.name;
	}

	@Override
	public void exec()
	{
		super.exec();

		anim.name = newName;
		ui.repaint();
	}

	@Override
	public void undo()
	{
		super.undo();

		anim.name = oldName;
		ui.repaint();
	}
}
