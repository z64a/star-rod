package game.sprite.editor.commands;

import java.util.function.Consumer;

import javax.swing.JCheckBox;

import common.commands.AbstractCommand;
import game.sprite.Sprite;

public class TogglePaletteOverride extends AbstractCommand
{
	private final JCheckBox checkbox;
	private final Sprite sprite;
	private final boolean prev;
	private final boolean next;
	private final Consumer<Boolean> callback;

	public TogglePaletteOverride(JCheckBox checkbox, Sprite sprite, Consumer<Boolean> callback)
	{
		super("Toggle Palette Override");

		this.checkbox = checkbox;
		this.sprite = sprite;

		this.next = checkbox.isSelected();
		this.prev = !next;
		this.callback = callback;
	}

	@Override
	public boolean shouldExec()
	{
		return prev != next;
	}

	@Override
	public boolean modifiesData()
	{
		return false;
	}

	@Override
	public void exec()
	{
		super.exec();

		sprite.usingOverridePalette = next;
		checkbox.setSelected(next);
		callback.accept(next);
	}

	@Override
	public void undo()
	{
		super.undo();

		sprite.usingOverridePalette = prev;
		checkbox.setSelected(prev);
		callback.accept(prev);
	}
}
