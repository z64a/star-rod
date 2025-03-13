package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.SpriteRaster;
import game.sprite.editor.animators.keyframe.Keyframe;

public class SetKeyframeRaster extends AbstractCommand
{
	private final Keyframe kf;
	private final SpriteRaster next;
	private final SpriteRaster prev;
	private final Runnable callback;

	public SetKeyframeRaster(Keyframe kf, SpriteRaster next, Runnable callback)
	{
		super("Set Keyframe Raster");

		this.kf = kf;
		this.next = next;
		this.prev = kf.img;
		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		kf.img = next;
		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		kf.img = prev;
		kf.incrementModified();

		callback.run();
	}
}
