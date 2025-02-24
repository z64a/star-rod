package game.sprite.editor.animators;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import common.commands.CommandBatch;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.commands.CreateCommand;
import game.sprite.editor.commands.DeleteCommand;
import game.sprite.editor.commands.ReorderCommand;
import game.sprite.editor.commands.SelectCommand;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.DragReorderList;
import util.ui.DragReorderTransferHandle;

public class AnimElementsList<T extends AnimElement> extends DragReorderList<T>
{
	private final SpriteEditor editor;
	private final AnimationEditor parent;

	private AnimElement clipboard = null;

	public boolean ignoreSelectionChange = false;

	public AnimElementsList(SpriteEditor editor, AnimationEditor parent)
	{
		this.editor = editor;
		this.parent = parent;

		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		//TODO	setCellRenderer(new SpriteAnimCellRenderer());

		setTransferHandler(new CommandTransferHandle());

		addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			AnimElementsList<?> list = AnimElementsList.this;
			if (!ignoreSelectionChange)
				SpriteEditor.execute(new SelectCommand(list, parent, list.getSelectedValue()));
		});

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				//TODO
			}
		});

		InputMap im = getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap am = getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "duplicate");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");

		am.put("copy", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				AnimElement cur = getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				clipboard = cur.copy();
			}
		});

		am.put("paste", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = getSelectedIndex();
				if (i == -1 || clipboard == null || clipboard.ownerComp != editor.getComponent()) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (getDefaultModel().size() >= Sprite.MAX_ANIMATIONS) {
					Logger.logError("Cannot have more than " + Sprite.MAX_ANIMATIONS + " animations!");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				AnimElement copy = clipboard.copy();
				SpriteEditor.execute(new CreateCommand("Paste " + copy.getName(), AnimElementsList.this, copy, i + 1));
			}
		});

		am.put("duplicate", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				AnimElement cur = getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (getDefaultModel().size() >= Sprite.MAX_ANIMATIONS) {
					Logger.logError("Cannot have more than " + Sprite.MAX_ANIMATIONS + " animations!");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				int i = getSelectedIndex();
				AnimElement copy = cur.copy();
				SpriteEditor.execute(new CreateCommand("Duplicate " + copy.getName(), AnimElementsList.this, copy, i + 1));
			}
		});

		am.put("delete", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = getSelectedIndex();
				if (i == -1) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				AnimElement cmd = getModel().getElementAt(i);
				String cmdName = (cmd == null) ? null : cmd.getName();

				CommandBatch batch = new CommandBatch("Delete " + cmdName);
				batch.addCommand(new SelectCommand(AnimElementsList.this, parent, null));
				batch.addCommand(new DeleteCommand(AnimElementsList.this, i));
				SpriteEditor.execute(batch);
			}
		});
	}

	private class CommandTransferHandle extends DragReorderTransferHandle<T>
	{
		@Override
		public void dropAction(AnimElement elem, int dropIndex)
		{
			SpriteEditor.execute(new ReorderCommand(AnimElementsList.this, elem, dropIndex));
		}
	}

	private static class SpriteAnimCellRenderer extends JPanel implements ListCellRenderer<SpriteAnimation>
	{
		private JLabel nameLabel;
		private JLabel idLabel;

		public SpriteAnimCellRenderer()
		{
			idLabel = new JLabel();
			nameLabel = new JLabel();

			setLayout(new MigLayout("ins 0, fillx"));
			add(idLabel, "gapleft 16, w 32!");
			add(nameLabel, "growx, pushx, gapright push");

			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends SpriteAnimation> list,
			SpriteAnimation anim,
			int index,
			boolean isSelected,
			boolean cellHasFocus)
		{
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}

			setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			if (anim != null) {
				idLabel.setText(String.format("%02X", anim.getIndex()));
				nameLabel.setText(anim.name);
			}
			else {
				idLabel.setText("XXX");
				nameLabel.setText("error!");
			}

			return this;
		}
	}
}
