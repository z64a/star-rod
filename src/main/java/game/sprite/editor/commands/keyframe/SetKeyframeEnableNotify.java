package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.KeyframeAnimator.Keyframe;

public class SetKeyframeEnableNotify extends AbstractCommand
{
	private final Keyframe kf;
	private final boolean next;
	private final boolean prev;
	private final Runnable callback;

	public SetKeyframeEnableNotify(Keyframe kf, boolean next, Runnable callback)
	{
		super((next ? "Enable" : "Disable") + " Keyframe Notify");

		this.kf = kf;
		this.next = next;
		this.prev = kf.setNotify;
		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		kf.setNotify = next;
		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		kf.setNotify = prev;
		kf.incrementModified();

		callback.run();
	}
}
