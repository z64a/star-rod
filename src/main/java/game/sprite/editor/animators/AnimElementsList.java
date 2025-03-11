package game.sprite.editor.animators;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

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
import javax.swing.SwingConstants;

import app.SwingUtils;
import common.commands.CommandBatch;
import game.sprite.Sprite;
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
	private final ComponentAnimationEditor parent;

	private AnimElement clipboard = null;

	public boolean ignoreSelectionChange = false;

	public AnimElementsList(ComponentAnimationEditor parent)
	{
		this.parent = parent;

		SpriteEditor.instance().registerDragList(this);

		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setCellRenderer(new AnimCommandCellRenderer());

		setTransferHandler(new CommandTransferHandle());

		addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			AnimElementsList<?> list = AnimElementsList.this;
			if (!ignoreSelectionChange)
				SpriteEditor.execute(new SelectCommand(list, parent, list.getSelectedValue()));
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
				if (i == -1 || clipboard == null || clipboard.owner != SpriteEditor.instance().getComponent()) {
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

	private class AnimCommandCellRenderer extends JPanel implements ListCellRenderer<T>
	{
		private JLabel timeLabel;
		private JLabel textLabel;

		public AnimCommandCellRenderer()
		{
			timeLabel = new JLabel("", SwingConstants.CENTER);
			textLabel = new JLabel();

			setLayout(new MigLayout("ins 0, fillx", "[20, center]12[grow]"));
			add(timeLabel);
			add(textLabel);

			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends T> list,
			T cmd,
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

			setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
			if (cmd != null) {
				if (cmd.animTime == -1)
					timeLabel.setText("");
				else
					timeLabel.setText(cmd.animTime + "");
				textLabel.setText(cmd.getFormattedText());
				textLabel.setForeground(cmd.getTextColor());
			}
			else {
				timeLabel.setText("?");
				textLabel.setText("NULL");
				textLabel.setForeground(SwingUtils.getRedTextColor());
			}

			return this;
		}
	}
}
