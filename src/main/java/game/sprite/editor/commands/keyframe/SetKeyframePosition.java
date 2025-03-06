package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.KeyframeAnimator.Keyframe;

public class SetKeyframePosition extends AbstractCommand
{
	private final Keyframe kf;
	private final int coord;
	private final int next;
	private final int prev;
	private final Runnable callback;

	public SetKeyframePosition(Keyframe kf, int coord, int next, Runnable callback)
	{
		super("Set Keyframe Position");

		this.kf = kf;
		this.coord = coord;
		this.next = next;

		if (coord == 0)
			this.prev = kf.dx;
		else if (coord == 1)
			this.prev = kf.dy;
		else
			this.prev = kf.dz;

		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		if (coord == 0)
			kf.dx = next;
		else if (coord == 1)
			kf.dy = next;
		else
			kf.dz = next;

		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		if (coord == 0)
			kf.dx = prev;
		else if (coord == 1)
			kf.dy = prev;
		else
			kf.dz = prev;

		kf.incrementModified();

		callback.run();
	}
}
