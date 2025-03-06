package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.KeyframeAnimator.Keyframe;

public class SetKeyframeEnableParent extends AbstractCommand
{
	private final Keyframe kf;
	private final boolean next;
	private final boolean prev;
	private final Runnable callback;

	public SetKeyframeEnableParent(Keyframe kf, boolean next, Runnable callback)
	{
		super((next ? "Enable" : "Disable") + " Keyframe Parent");

		this.kf = kf;
		this.next = next;
		this.prev = kf.setParent;
		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		kf.setParent = next;
		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		kf.setParent = prev;
		kf.incrementModified();

		callback.run();
	}
}
