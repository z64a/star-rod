package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import game.sprite.editor.animators.command.CommandAnimatorEditor.SetPositionPanel;

//3VVV XXXX YYYY ZZZZ
// set position -- flag: doesn't do anything
public class SetPosition extends AnimCommand
{
	public boolean unknown;
	public int x, y, z;

	public SetPosition(CommandAnimator animator)
	{
		this(animator, (short) 0, (short) 0, (short) 0, (short) 0);
	}

	public SetPosition(CommandAnimator animator, short s0, short s1, short s2, short s3)
	{
		super(animator);

		unknown = (s0 & 0xFFF) == 1;
		x = s1;
		y = s2;
		z = s3;
	}

	@Override
	public SetPosition copy()
	{
		SetPosition clone = new SetPosition(animator);
		clone.x = x;
		clone.y = y;
		clone.z = z;
		clone.unknown = unknown;
		return clone;
	}

	@Override
	public AdvanceResult apply()
	{
		owner.dx = x;
		owner.dy = y;
		owner.dz = z;
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Position";
	}

	@Override
	public String toString()
	{
		return String.format("Position: (%d, %d, %d)", x, y, z);
	}

	@Override
	public int length()
	{
		return 4;
	}

	@Override
	public Component getPanel()
	{
		SetPositionPanel.instance().bind(this);
		return SetPositionPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		seq.add(unknown ? (short) 0x3001 : (short) 0x3000);
		seq.add((short) x);
		seq.add((short) y);
		seq.add((short) z);
	}
}
