package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.KeyframeAnimator.Keyframe;

public class SetKeyframeEnableRaster extends AbstractCommand
{
	private final Keyframe kf;
	private final boolean next;
	private final boolean prev;
	private final Runnable callback;

	public SetKeyframeEnableRaster(Keyframe kf, boolean next, Runnable callback)
	{
		super((next ? "Enable" : "Disable") + " Keyframe Raster");

		this.kf = kf;
		this.next = next;
		this.prev = kf.setImage;
		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		kf.setImage = next;
		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		kf.setImage = prev;
		kf.incrementModified();

		callback.run();
	}
}
