package game.sprite.editor.animators.command;

import static game.sprite.SpriteKey.*;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.w3c.dom.Element;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.keyframe.Keyframe;
import net.miginfocom.swing.MigLayout;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

//7VVV UUUU
// loop
public class Loop extends AnimCommand
{
	public static final int NO_FIXED_POS = -1;

	public Label label;
	public int count;

	// only used for generating commands from raw bytes
	public transient int fixedPos = NO_FIXED_POS;

	// used during deserialization
	public transient String labelName;

	// used during conversion from Keyframes
	public transient Keyframe target;

	public Loop(CommandAnimator animator)
	{
		this(animator, null, NO_FIXED_POS, (short) 3);
	}

	// used during conversion from Keyframes
	public Loop(CommandAnimator animator, Keyframe target, int count)
	{
		this(animator, null, NO_FIXED_POS, (short) count);

		this.target = target;
	}

	public Loop(CommandAnimator animator, Label label, int fixedPos, short s1)
	{
		super(animator);

		this.label = label;
		this.fixedPos = fixedPos;
		count = s1;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_CMD_LOOP, true);
		if (label != null)
			xmw.addAttribute(tag, ATTR_DEST, label.name);
		xmw.addInt(tag, ATTR_COUNT, count);
		xmw.printTag(tag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_DEST))
			labelName = xmr.getAttribute(elem, ATTR_DEST);

		if (xmr.hasAttribute(elem, ATTR_POS))
			fixedPos = xmr.readInt(elem, ATTR_POS);

		if (xmr.hasAttribute(elem, ATTR_COUNT))
			count = xmr.readInt(elem, ATTR_COUNT);
	}

	@Override
	public Loop copy()
	{
		return new Loop(animator, label, NO_FIXED_POS, (short) count);
	}

	@Override
	public AdvanceResult apply()
	{
		if (animator.comp.repeatCount == 0) {
			animator.comp.repeatCount = count;
			animator.gotoLabel(label);
			return AdvanceResult.JUMP;
		}
		else {
			animator.comp.repeatCount--;
			if (animator.comp.repeatCount != 0) {
				animator.gotoLabel(label);
				return AdvanceResult.JUMP;
			}
		}
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Loop";
	}

	@Override
	public Color getTextColor()
	{
		if (hasError())
			return SwingUtils.getRedTextColor();
		else
			return SwingUtils.getBlueTextColor();
	}

	@Override
	public String toString()
	{
		if (label == null)
			return "Repeat: (missing) (x" + count + ")";
		else if (animator.findCommand(label) < 0)
			return "<html>Repeat: <i>" + label.name + "</i>  (missing) (x" + count + ")</html>";
		else
			return "<html>Repeat: <i>" + label.name + "</i>  (x" + count + ")</html>";
	}

	@Override
	public int length()
	{
		return 2;
	}

	@Override
	public Component getPanel()
	{
		List<Label> labels = animator.getLabelsList();

		DefaultComboBoxModel<Label> comboBoxModel = new DefaultComboBoxModel<>();

		// if the label is missing from the command, add it
		if (!labels.contains(label))
			comboBoxModel.addElement(label);

		// add all the labels for this animator
		comboBoxModel.addAll(labels);

		LoopPanel.instance().bind(this, comboBoxModel);
		return LoopPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		int pos = 0;

		if (label.name.startsWith("#"))
			pos = Integer.parseInt(label.name.substring(1), 16);
		else {
			pos = animator.getCommandOffset(label);
			if (pos < 0)
				throw new RuntimeException("Repeat is missing label: " + label.name);
		}

		seq.add((short) (0x7000 | (pos & 0xFFF)));
		seq.add((short) count);
	}

	@Override
	public String checkErrorMsg()
	{
		if (label == null || animator.findCommand(label) < 0)
			return "Loop Command: missing label";

		return null;
	}

	private static class LoopPanel extends JPanel
	{
		private static LoopPanel instance;
		private Loop cmd;

		private JComboBox<Label> labelComboBox;
		private JSpinner countSpinner;
		private boolean ignoreChanges = false;

		private static LoopPanel instance()
		{
			if (instance == null)
				instance = new LoopPanel();
			return instance;
		}

		private LoopPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			labelComboBox = new JComboBox<>();
			SwingUtils.setFontSize(labelComboBox, 14);
			labelComboBox.setMaximumRowCount(16);
			labelComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					Label label = (Label) labelComboBox.getSelectedItem();
					SpriteEditor.execute(new SetCommandLoopLabel(cmd, label));
				}
			});

			countSpinner = new JSpinner();
			countSpinner.setModel(new SpinnerNumberModel(1, 0, 300, 1));
			countSpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int loopCount = (int) countSpinner.getValue();
					SpriteEditor.execute(new SetCommandLoopCount(cmd, loopCount));
				}
			});

			SwingUtils.setFontSize(countSpinner, 12);
			SwingUtils.centerSpinnerText(countSpinner);
			SwingUtils.addBorderPadding(countSpinner);

			add(SwingUtils.getLabel("Repeat from Label", 14), "gapbottom 4");
			add(labelComboBox, "growx, pushx");
			add(SwingUtils.getLabel("Repetitions: ", 12), "split 2");
			add(countSpinner, "w 30%");
		}

		private void bind(Loop cmd, ComboBoxModel<Label> labels)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			labelComboBox.setModel(labels);
			labelComboBox.setSelectedItem(cmd.label);
			ignoreChanges = false;

			countSpinner.setValue(cmd.count);
		}

		private class SetCommandLoopCount extends AbstractCommand
		{
			private final Loop cmd;
			private final int next;
			private final int prev;

			private SetCommandLoopCount(Loop cmd, int next)
			{
				super("Set Loop Count");

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

		private class SetCommandLoopLabel extends AbstractCommand
		{
			private final Loop cmd;
			private final Label next;
			private final Label prev;

			private SetCommandLoopLabel(Loop cmd, Label next)
			{
				super("Set Loop Label");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.label;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.label = next;

				ignoreChanges = true;
				labelComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.incrementModified();
				cmd.owner.calculateTiming();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.label = prev;

				ignoreChanges = true;
				labelComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				cmd.owner.calculateTiming();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
