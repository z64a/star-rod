package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.keyframe.Keyframe;

public class SetKeyframeName extends AbstractCommand
{
	private final Keyframe kf;
	private final String next;
	private final String prev;
	private final Runnable callback;

	public SetKeyframeName(Keyframe kf, String next, Runnable callback)
	{
		super("Set Keyframe Name");

		this.kf = kf;
		this.next = next;
		this.prev = kf.name;
		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		kf.name = next;
		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		kf.name = prev;
		kf.incrementModified();

		callback.run();
	}
}
