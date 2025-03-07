package game.sprite.editor.animators.command;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import app.SwingUtils;
import game.sprite.editor.animators.command.CommandAnimatorEditor.SetNotifyPanel;

//82VV
public class SetNotify extends AnimCommand
{
	public int value;

	public SetNotify(CommandAnimator animator)
	{
		this(animator, (short) 0);
	}

	public SetNotify(CommandAnimator animator, short s0)
	{
		super(animator);

		value = (s0 & 0xFF);
	}

	@Override
	public SetNotify copy()
	{
		SetNotify clone = new SetNotify(animator);
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
