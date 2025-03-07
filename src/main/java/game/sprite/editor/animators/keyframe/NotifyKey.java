package game.sprite.editor.animators.keyframe;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import app.SwingUtils;
import game.sprite.editor.animators.keyframe.KeyframeAnimatorEditor.SetNotifyPanel;

public class NotifyKey extends AnimKeyframe
{
	public int value;

	public NotifyKey(KeyframeAnimator animator)
	{
		this(animator, (short) 0);
	}

	public NotifyKey(KeyframeAnimator animator, int v)
	{
		super(animator);

		value = (v & 0xFF);
	}

	@Override
	public NotifyKey copy()
	{
		NotifyKey clone = new NotifyKey(animator);
		clone.value = value;
		return clone;
	}

	@Override
	public AdvanceResult apply()
	{
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Notify";
	}

	@Override
	public Color getTextColor()
	{
		if (hasError())
			return SwingUtils.getRedTextColor();
		else
			return SwingUtils.getYellowTextColor();
	}

	@Override
	public String toString()
	{
		return "Set Notify: " + value;
	}

	@Override
	public int length()
	{
		return 1;
	}

	@Override
	public Component getPanel()
	{
		SetNotifyPanel.instance().bind(this);
		return SetNotifyPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		seq.add((short) (0x8200 | (value & 0xFF)));
	}
}
