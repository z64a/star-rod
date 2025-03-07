package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.keyframe.Keyframe;

public class SetKeyframeDuration extends AbstractCommand
{
	private final Keyframe kf;
	private final int next;
	private final int prev;
	private final Runnable callback;

	public SetKeyframeDuration(Keyframe kf, int next, Runnable callback)
	{
		super("Set Keyframe Duration");

		this.kf = kf;
		this.next = next;
		this.prev = kf.duration;
		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		kf.duration = next;
		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		kf.duration = prev;
		kf.incrementModified();

		callback.run();
	}
}
