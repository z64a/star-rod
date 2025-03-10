package game.sprite.editor.animators.command;

import static game.sprite.SpriteKey.*;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.w3c.dom.Element;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.keyframe.Keyframe;
import net.miginfocom.swing.MigLayout;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

//2VVV
// goto -- jump to another position in the list
public class Goto extends AnimCommand
{
	public static final int NO_FIXED_POS = -1;

	public Label label;

	// only used for generating commands from raw bytes
	public transient int fixedPos = NO_FIXED_POS;

	// used during deserialization
	public transient String labelName;

	// used during conversion from Keyframes
	public transient Keyframe target;

	public Goto(CommandAnimator animator)
	{
		this(animator, null, NO_FIXED_POS);
	}

	// used during conversion from Keyframes
	public Goto(CommandAnimator animator, Keyframe target)
	{
		this(animator);

		this.target = target;
	}

	public Goto(CommandAnimator animator, Label label, int fixedPos)
	{
		super(animator);

		this.label = label;
		this.fixedPos = fixedPos;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_CMD_GOTO, true);
		if (label != null)
			xmw.addAttribute(tag, ATTR_DEST, label.name);
		xmw.printTag(tag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_POS))
			fixedPos = xmr.readInt(elem, ATTR_POS);

		if (xmr.hasAttribute(elem, ATTR_DEST))
			labelName = xmr.getAttribute(elem, ATTR_DEST);
	}

	@Override
	public Goto copy()
	{
		return new Goto(animator, label, -1);
	}

	@Override
	public AdvanceResult apply()
	{
		if (label == null)
			return AdvanceResult.NEXT;

		// goto: self infinite loops add a 1 frame delay
		if (animator.findCommand(label) < animator.findCommand(this))
			owner.complete = (owner.gotoTime == 0);

		animator.gotoLabel(label);
		return AdvanceResult.JUMP;
	}

	@Override
	public String getName()
	{
		return "Goto";
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
			return "Goto: (missing)";
		else if (animator.findCommand(label) < 0)
			return "<html>Goto: <i>" + label.name + "</i>  (missing)</html>";
		else
			return "<html>Goto: <i>" + label.name + "</i></html>";
	}

	@Override
	public int length()
	{
		return 1;
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

		GotoPanel.instance().bind(this, comboBoxModel);
		return GotoPanel.instance();
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
				throw new RuntimeException("Goto is missing label: " + label.name);
		}

		seq.add((short) (0x2000 | (pos & 0xFFF)));
	}

	@Override
	public String checkErrorMsg()
	{
		if (label == null || animator.findCommand(label) < 0)
			return "Goto Command: missing label";

		return null;
	}

	private static class GotoPanel extends JPanel
	{
		private static GotoPanel instance;
		private Goto cmd;

		private JComboBox<Label> labelComboBox;
		private boolean ignoreChanges = false;

		private static GotoPanel instance()
		{
			if (instance == null)
				instance = new GotoPanel();
			return instance;
		}

		private GotoPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			labelComboBox = new JComboBox<>();
			SwingUtils.setFontSize(labelComboBox, 14);
			labelComboBox.setMaximumRowCount(16);
			labelComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					Label label = (Label) labelComboBox.getSelectedItem();
					SpriteEditor.execute(new SetCommandGotoLabel(cmd, label));
				}
			});

			add(SwingUtils.getLabel("Goto Label", 14), "gapbottom 4");
			add(labelComboBox, "growx, pushx");
		}

		private void bind(Goto cmd, ComboBoxModel<Label> labels)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			labelComboBox.setModel(labels);
			labelComboBox.setSelectedItem(cmd.label);
			ignoreChanges = false;
		}

		private class SetCommandGotoLabel extends AbstractCommand
		{
			private final Goto cmd;
			private final Label next;
			private final Label prev;

			private SetCommandGotoLabel(Goto cmd, Label next)
			{
				super("Set Goto Label");

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
