package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.editor.Editable;
import game.sprite.editor.SpriteEditor;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.ListAdapterComboboxModel;

//81XX parent to component XX
public class SetParent extends AnimCommand
{
	public SpriteComponent parent;

	public SetParent(CommandAnimator animator)
	{
		this(animator, (short) 0);
	}

	public SetParent(CommandAnimator animator, short s0)
	{
		super(animator);

		int id = (s0 & 0xFF);
		if (id < owner.parentAnimation.components.size())
			parent = owner.parentAnimation.components.get(id);
	}

	public SetParent(CommandAnimator animator, SpriteComponent parent)
	{
		super(animator);

		this.parent = parent;
	}

	@Override
	public SetParent copy()
	{
		SetParent clone = new SetParent(animator);
		clone.parent = parent;
		return clone;
	}

	@Override
	public AdvanceResult apply()
	{
		owner.parent = parent;
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Parent";
	}

	@Override
	public String toString()
	{
		if (parent == null)
			return "Parent: (missing)";
		else if (parent.deleted)
			return "Parent: " + parent.name + " (missing)";
		else
			return "Parent: " + parent.name;
	}

	@Override
	public int length()
	{
		return 1;
	}

	@Override
	public Component getPanel()
	{
		SetParentPanel.instance().bind(this);
		return SetParentPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		if (parent != null)
			seq.add((short) (0x8100 | (parent.getIndex() & 0xFF)));
		else {
			Logger.logError("No parent selected for SetParent in " + owner);
			seq.add((short) (0x8100));
		}
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{
		if (parent != null)
			downstream.add(parent);
	}

	@Override
	public String checkErrorMsg()
	{
		if (parent == null)
			return "SetParent Command: undefined parent";

		if (parent == owner)
			return "SetParent Command: parented to itself";

		return null;
	}

	private static class SetParentPanel extends JPanel
	{
		private static SetParentPanel instance;
		private SetParent cmd;

		private JComboBox<SpriteComponent> componentComboBox;
		private boolean ignoreChanges = false;

		private static SetParentPanel instance()
		{
			if (instance == null)
				instance = new SetParentPanel();
			return instance;
		}

		private SetParentPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			componentComboBox = new JComboBox<>();
			SwingUtils.setFontSize(componentComboBox, 14);
			componentComboBox.setMaximumRowCount(24);
			componentComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					SpriteComponent comp = (SpriteComponent) componentComboBox.getSelectedItem();
					SpriteEditor.execute(new SetCommandParent(cmd, comp));
				}
			});

			add(SwingUtils.getLabel("Set Parent", 14), "gapbottom 4");
			add(componentComboBox, "growx, pushx");
		}

		private void bind(SetParent cmd)
		{
			this.cmd = cmd;
			SpriteAnimation anim = cmd.owner.parentAnimation;

			ignoreChanges = true;
			componentComboBox.setModel(new ListAdapterComboboxModel<>(anim.components));
			componentComboBox.setSelectedItem(cmd.parent);
			ignoreChanges = false;
		}

		private class SetCommandParent extends AbstractCommand
		{
			private final SetParent cmd;
			private final SpriteComponent next;
			private final SpriteComponent prev;

			private SetCommandParent(SetParent cmd, SpriteComponent next)
			{
				super("Set Parent");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.parent;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.parent = next;

				ignoreChanges = true;
				componentComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.incrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.parent = prev;

				ignoreChanges = true;
				componentComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
