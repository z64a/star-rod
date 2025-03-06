package game.sprite.editor.commands.keyframe;

import common.commands.AbstractCommand;
import game.sprite.editor.animators.KeyframeAnimator.Keyframe;

public class SetKeyframeEnablePalette extends AbstractCommand
{
	private final Keyframe kf;
	private final boolean next;
	private final boolean prev;
	private final Runnable callback;

	public SetKeyframeEnablePalette(Keyframe kf, boolean next, Runnable callback)
	{
		super((next ? "Enable" : "Disable") + " Keyframe Palette");

		this.kf = kf;
		this.next = next;
		this.prev = kf.setPalette;
		this.callback = callback;
	}

	@Override
	public void exec()
	{
		super.exec();

		kf.setPalette = next;
		kf.incrementModified();

		callback.run();
	}

	@Override
	public void undo()
	{
		super.undo();

		kf.setPalette = prev;
		kf.incrementModified();

		callback.run();
	}
}
