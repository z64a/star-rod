package game.sprite.editor.commands;

import java.awt.Component;

import common.commands.AbstractCommand;
import game.sprite.SpriteComponent;

public class ToggleComponentHidden extends AbstractCommand
{
	private final Component ui;
	private final SpriteComponent comp;
	private final boolean next;
	private final boolean prev;

	public ToggleComponentHidden(Component ui, SpriteComponent comp)
	{
		super(comp.hidden ? "Show Component" : "Hide Component");

		this.ui = ui;
		this.comp = comp;
		this.prev = comp.hidden;
		this.next = !prev;
	}

	@Override
	public void exec()
	{
		super.exec();

		comp.hidden = next;
		ui.repaint();
	}

	@Override
	public void undo()
	{
		super.undo();

		comp.hidden = prev;
		ui.repaint();
	}
}
