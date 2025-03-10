package game.sprite.editor.animators.command;

import static game.sprite.SpriteKey.ATTR_DURATION;
import static game.sprite.SpriteKey.TAG_CMD_WAIT;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.w3c.dom.Element;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import net.miginfocom.swing.MigLayout;
import util.ui.EvenSpinner;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

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
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_CMD_WAIT, true);
		xmw.addInt(tag, ATTR_DURATION, count);
		xmw.printTag(tag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_DURATION))
			count = xmr.readInt(elem, ATTR_DURATION);
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
			owner.gotoTime += count;

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

		if (count % 2 == 1 && SpriteEditor.instance().optStrictErrorChecking)
			return "Wait duration should be even";

		return null;
	}

	private static class WaitPanel extends JPanel
	{
		private static WaitPanel instance;
		private Wait cmd;

		private JSpinner countSpinner;
		private boolean ignoreChanges = false;

		private static WaitPanel instance()
		{
			if (instance == null)
				instance = new WaitPanel();
			return instance;
		}

		private WaitPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			countSpinner = new EvenSpinner();
			countSpinner.setModel(new SpinnerNumberModel(1, 0, 300, 1)); // longest used = 260
			countSpinner.addChangeListener((e) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetCommandWaitDelay(cmd, (int) countSpinner.getValue()));
			});

			SwingUtils.setFontSize(countSpinner, 12);
			SwingUtils.centerSpinnerText(countSpinner);
			SwingUtils.addBorderPadding(countSpinner);

			add(SwingUtils.getLabel("Wait Duration", 14), "gapbottom 4");
			add(countSpinner, "w 30%, split 2");
			add(SwingUtils.getLabel(" frames", 12));
		}

		private void bind(Wait cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			countSpinner.setValue(cmd.count);
			ignoreChanges = false;
		}

		private class SetCommandWaitDelay extends AbstractCommand
		{
			private final Wait cmd;
			private final int next;
			private final int prev;

			private SetCommandWaitDelay(Wait cmd, int next)
			{
				super("Set Wait Delay");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.count;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.count = next;

				ignoreChanges = true;
				countSpinner.setValue(next);
				ignoreChanges = false;

				cmd.incrementModified();
				cmd.owner.calculateTiming();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.count = prev;

				ignoreChanges = true;
				countSpinner.setValue(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				cmd.owner.calculateTiming();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
