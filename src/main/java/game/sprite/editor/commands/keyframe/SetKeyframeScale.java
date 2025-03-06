package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.KeyframeAnimator.Keyframe;

public class SetKeyframeScale extends AbstractCommand
{
	private final Keyframe kf;
	private final int coord;
	private final int next;
	private final int prev;
	private final Runnable callback;

	public SetKeyframeScale(Keyframe kf, int coord, int next, Runnable callback)
	{
		super("Set Keyframe Scale");

		this.kf = kf;
		this.coord = coord;
		this.next = next;

		if (coord == 0)
			this.prev = kf.sx;
		else if (coord == 1)
			this.prev = kf.sy;
		else
			this.prev = kf.sz;

		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		if (coord == 0)
			kf.sx = next;
		else if (coord == 1)
			kf.sy = next;
		else
			kf.sz = next;

		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		if (coord == 0)
			kf.sx = prev;
		else if (coord == 1)
			kf.sy = prev;
		else
			kf.sz = prev;

		kf.incrementModified();

		callback.run();
	}
}
