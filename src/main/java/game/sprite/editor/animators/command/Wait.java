package game.sprite.editor.animators.command;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import app.SwingUtils;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.command.CommandAnimatorEditor.WaitPanel;

//0VVV
public class Wait extends AnimCommand
{
	public int count;

	public Wait(CommandAnimator animator)
	{
		this(animator, (short) 2);
	}

	public Wait(CommandAnimator animator, short s0)
	{
		super(animator);

		count = (s0 & 0xFFF);
		if (count == 0)
			count = 4095;
	}

	@Override
	public Wait copy()
	{
		return new Wait(animator, (short) count);
	}

	@Override
	public AdvanceResult apply()
	{
		animator.comp.delayCount = count;
		if (count > 0)
			owner.keyframeCount++;

		return (count > 0) ? AdvanceResult.BLOCK : AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Wait";
	}

	@Override
	public Color getTextColor()
	{
		if (hasError())
			return SwingUtils.getRedTextColor();
		else if (count % 2 == 1)
			return SwingUtils.getYellowTextColor();
		else
			return null;
	}

	@Override
	public String toString()
	{
		if (highlighted && SpriteEditor.instance().highlightCommand)
			return "<html><b>Wait " + count + "</b></html>";
		else
			return "Wait " + count;
	}

	@Override
	public int length()
	{
		return 1;
	}

	@Override
	public Component getPanel()
	{
		WaitPanel.instance().bind(this);
		return WaitPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		seq.add((short) (0x0000 | (count & 0xFFF)));
	}

	@Override
	public String checkErrorMsg()
	{
		if (count == 0)
			return "Wait Command: invalid duration";

		return null;
	}
}
