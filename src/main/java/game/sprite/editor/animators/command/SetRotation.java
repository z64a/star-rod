package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import game.sprite.editor.animators.command.CommandAnimatorEditor.SetRotationPanel;

//4xxx yyyy zzzz
// set rotation (euler angles)
public class SetRotation extends AnimCommand
{
	public int x, y, z;

	public SetRotation(CommandAnimator animator)
	{
		this(animator, (short) 0, (short) 0, (short) 0);
	}

	public SetRotation(CommandAnimator animator, short s0, short s1, short s2)
	{
		super(animator);

		// sign extend (implicit cast to int)
		x = ((s0 << 20) >> 20);
		y = s1;
		z = s2;
	}

	@Override
	public SetRotation copy()
	{
		return new SetRotation(animator, (short) x, (short) y, (short) z);
	}

	@Override
	public AdvanceResult apply()
	{
		owner.rx = x;
		owner.ry = y;
		owner.rz = z;
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Rotation";
	}

	@Override
	public String toString()
	{
		return String.format("Rotation: (%d, %d, %d)", x, y, z);
	}

	@Override
	public int length()
	{
		return 3;
	}

	@Override
	public Component getPanel()
	{
		SetRotationPanel.instance().bind(this);
		return SetRotationPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		seq.add((short) (0x4000 | (x & 0xFFF)));
		seq.add((short) y);
		seq.add((short) z);
	}
}
