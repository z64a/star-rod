package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import game.sprite.editor.animators.command.CommandAnimatorEditor.SetUnknownPanel;

//80XX
public class SetUnknown extends AnimCommand
{
	public int value;

	public SetUnknown(CommandAnimator animator)
	{
		this(animator, (short) 0);
	}

	public SetUnknown(CommandAnimator animator, short s0)
	{
		super(animator);

		value = (s0 & 0xFF);
	}

	@Override
	public SetUnknown copy()
	{
		SetUnknown clone = new SetUnknown(animator);
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
		return "Set Unknown";
	}

	@Override
	public String toString()
	{
		return "Set Unknown: " + value;
	}

	@Override
	public int length()
	{
		return 1;
	}

	@Override
	public Component getPanel()
	{
		SetUnknownPanel.instance().bind(this);
		return SetUnknownPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		seq.add((short) (0x8000 | (value & 0xFF)));
	}
}
