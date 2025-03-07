package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.SpritePalette;
import game.sprite.editor.animators.keyframe.Keyframe;

public class SetKeyframePalette extends AbstractCommand
{
	private final Keyframe kf;
	private final SpritePalette next;
	private final SpritePalette prev;
	private final Runnable callback;

	public SetKeyframePalette(Keyframe kf, SpritePalette next, Runnable callback)
	{
		super("Set Keyframe Palette");

		this.kf = kf;
		this.next = next;
		this.prev = kf.pal;
		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		kf.pal = next;
		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		kf.pal = prev;
		kf.incrementModified();

		callback.run();
	}
}
