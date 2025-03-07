package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import game.sprite.editor.animators.command.CommandAnimatorEditor.SetScalePanel;

//5VVV UUUU
// set scale (%)
public class SetScale extends AnimCommand
{
	public int type;
	public int scalePercent;

	public SetScale(CommandAnimator animator)
	{
		this(animator, (short) 0, (short) 100);
	}

	public SetScale(CommandAnimator animator, short s0, short s1)
	{
		super(animator);

		type = (s0 & 0xFFF);
		scalePercent = s1;
	}

	@Override
	public SetScale copy()
	{
		return new SetScale(animator, (short) type, (short) scalePercent);
	}

	@Override
	public AdvanceResult apply()
	{
		switch (type) {
			case 0:
				owner.scaleX = scalePercent;
				owner.scaleY = scalePercent;
				owner.scaleZ = scalePercent;
				break;
			case 1:
				owner.scaleX = scalePercent;
				break;
			case 2:
				owner.scaleY = scalePercent;
			case 3:
				owner.scaleZ = scalePercent;
				break;
			default:
				throw new RuntimeException(String.format("Invalid scale command type: %X", type));
		}
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Scale";
	}

	@Override
	public String toString()
	{
		String typeName;
		switch (type) {
			case 0:
				typeName = "All";
				break;
			case 1:
				typeName = "X";
				break;
			case 2:
				typeName = "Y";
			case 3:
				typeName = "Z";
				break;
			default:
				throw new RuntimeException(String.format("Invalid scale command type: %X", type));
		}
		return "Scale " + typeName + ": " + scalePercent + "%";
	}

	@Override
	public int length()
	{
		return 2;
	}

	@Override
	public Component getPanel()
	{
		SetScalePanel.instance().bind(this);
		return SetScalePanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		seq.add((short) (0x5000 | (type & 0xFFF)));
		seq.add((short) scalePercent);
	}
}
