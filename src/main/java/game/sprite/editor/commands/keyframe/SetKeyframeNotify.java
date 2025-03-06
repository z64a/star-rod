package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.KeyframeAnimator.Keyframe;

public class SetKeyframeNotify extends AbstractCommand
{
	private final Keyframe kf;
	private final int next;
	private final int prev;
	private final Runnable callback;

	public SetKeyframeNotify(Keyframe kf, int next, Runnable callback)
	{
		super("Set Keyframe Notify Value");

		this.kf = kf;
		this.next = next;
		this.prev = kf.notifyValue;
		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		kf.notifyValue = next;
		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		kf.notifyValue = prev;
		kf.incrementModified();

		callback.run();
	}
}
