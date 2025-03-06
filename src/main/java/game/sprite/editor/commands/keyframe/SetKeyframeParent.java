package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.SpriteComponent;
import game.sprite.editor.animators.KeyframeAnimator.Keyframe;

public class SetKeyframeParent extends AbstractCommand
{
	private final Keyframe kf;
	private final SpriteComponent next;
	private final SpriteComponent prev;
	private final Runnable callback;

	public SetKeyframeParent(Keyframe kf, SpriteComponent next, Runnable callback)
	{
		super("Set Keyframe Parent");

		this.kf = kf;
		this.next = next;
		this.prev = kf.parentComp;
		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		kf.parentComp = next;
		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		kf.parentComp = prev;
		kf.incrementModified();

		callback.run();
	}
}
