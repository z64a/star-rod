package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.KeyframeAnimator.Keyframe;

public class SetKeyframeRotation extends AbstractCommand
{
	private final Keyframe kf;
	private final int coord;
	private final int next;
	private final int prev;
	private final Runnable callback;

	public SetKeyframeRotation(Keyframe kf, int coord, int next, Runnable callback)
	{
		super("Set Keyframe Rotation");

		this.kf = kf;
		this.coord = coord;
		this.next = next;

		if (coord == 0)
			this.prev = kf.rx;
		else if (coord == 1)
			this.prev = kf.ry;
		else
			this.prev = kf.rz;

		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		if (coord == 0)
			kf.rx = next;
		else if (coord == 1)
			kf.ry = next;
		else
			kf.rz = next;

		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		if (coord == 0)
			kf.rx = prev;
		else if (coord == 1)
			kf.ry = prev;
		else
			kf.rz = prev;

		kf.incrementModified();

		callback.run();
	}
}
