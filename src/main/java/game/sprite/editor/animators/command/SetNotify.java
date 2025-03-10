package game.sprite.editor.animators.command;

import static game.sprite.SpriteKey.ATTR_VALUE;
import static game.sprite.SpriteKey.TAG_CMD_SET_NOTIFY;

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
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

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
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_CMD_SET_NOTIFY, true);
		xmw.addInt(tag, ATTR_VALUE, value);
		xmw.printTag(tag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_VALUE))
			value = xmr.readInt(elem, ATTR_VALUE);
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

	private static class SetNotifyPanel extends JPanel
	{
		private static SetNotifyPanel instance;
		private SetNotify cmd;

		private JSpinner valueSpinner;
		private boolean ignoreChanges = false;

		private static SetNotifyPanel instance()
		{
			if (instance == null)
				instance = new SetNotifyPanel();
			return instance;
		}

		private SetNotifyPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			valueSpinner = new JSpinner();
			valueSpinner.setModel(new SpinnerNumberModel(0, 0, 255, 1));
			valueSpinner.addChangeListener((e) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetCommandNotify(cmd, (int) valueSpinner.getValue()));
			});

			SwingUtils.setFontSize(valueSpinner, 12);
			SwingUtils.centerSpinnerText(valueSpinner);
			SwingUtils.addBorderPadding(valueSpinner);

			add(SwingUtils.getLabel("Set Notify", 14), "gapbottom 4");
			add(valueSpinner, "w 30%!");
		}

		private void bind(SetNotify cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			valueSpinner.setValue(cmd.value);
			ignoreChanges = false;
		}

		private class SetCommandNotify extends AbstractCommand
		{
			private final SetNotify cmd;
			private final int next;
			private final int prev;

			private SetCommandNotify(SetNotify cmd, int next)
			{
				super("Set Notify");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.value;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.value = next;

				ignoreChanges = true;
				valueSpinner.setValue(next);
				ignoreChanges = false;

				cmd.incrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.value = prev;

				ignoreChanges = true;
				valueSpinner.setValue(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
