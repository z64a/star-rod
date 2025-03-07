package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import app.SwingUtils;
import game.sprite.editor.animators.command.CommandAnimatorEditor.LabelPanel;

//Used with Goto and Loop to specify targets
public class Label extends AnimCommand
{
	public String labelName;

	// serialization only
	public int listPos;

	// only used during conversion from Keyframes
	public transient boolean inUse;

	public Label(CommandAnimator animator)
	{
		this(animator, "New Label");
	}

	public Label(CommandAnimator animator, String name)
	{
		super(animator);

		this.labelName = name;
		animator.labels.addElement(this);
	}

	@Override
	public Label copy()
	{
		return new Label(animator, labelName);
	}

	@Override
	public AdvanceResult apply()
	{
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Label";
	}

	@Override
	public String toString()
	{
		return "<html>" + SwingUtils.makeFontTag(SwingUtils.getGreenTextColor())
			+ "Label: <i>" + labelName + "</i></font></html>";
	}

	@Override
	public int length()
	{
		return 0;
	}

	@Override
	public Component getPanel()
	{
		LabelPanel.instance().bind(this);
		return LabelPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		throw new RuntimeException("Tried to add label to command sequence.");
	}
}
