package game.sprite.editor;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

import app.SwingUtils;
import common.commands.CommandBatch;
import game.sprite.SpritePalette;
import game.sprite.editor.commands.CreatePalette;
import game.sprite.editor.commands.DeletePalette;
import game.sprite.editor.commands.RenamePalette;
import game.sprite.editor.commands.ReorderPalette;
import game.sprite.editor.commands.SelectPalette;
import util.EnableCounter;
import util.Logger;
import util.ui.DragReorderList;
import util.ui.DragReorderTransferHandle;

public class PalettesList extends DragReorderList<SpritePalette>
{
	private final SpriteEditor editor;
	private SpritePalette clipboard = null;

	public EnableCounter ignoreChanges = new EnableCounter();

	public PalettesList(SpriteEditor editor, PalettesTab tab)
	{
		this.editor = editor;

		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setCellRenderer(new PaletteCellRenderer());

		setTransferHandler(new SpritePaletteTransferHandle());

		addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			PalettesList list = PalettesList.this;
			if (ignoreChanges.disabled())
				SpriteEditor.execute(new SelectPalette(list, editor.getSprite(), list.getSelectedValue(), tab::setPalette));
		});

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				// double click to rename
				if (e.getClickCount() == 2) {
					int index = locationToIndex(e.getPoint());
					if (index != -1) {
						SpritePalette pal = getModel().getElementAt(index);
						promptRename(pal);
					}
				}
			}
		});

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e)
			{
				// rename with 'F2' key
				if (e.getKeyCode() == KeyEvent.VK_F2) {
					int index = getSelectedIndex();
					if (index != -1) {
						SpritePalette pal = getModel().getElementAt(index);
						promptRename(pal);
					}
				}
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
				SpritePalette cur = getSelectedValue();
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
				if (i == -1 || clipboard == null || clipboard.parentSprite != editor.getSprite()) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				SpritePalette copy = clipboard.copy();
				String newName = copy.createUniqueName(copy.name);

				if (newName == null) {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				copy.name = newName;
				SpriteEditor.execute(new CreatePalette("Paste Palette", editor.getSprite(), copy, i + 1));
			}
		});

		am.put("duplicate", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SpritePalette cur = getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				int i = getSelectedIndex();
				SpritePalette copy = cur.copy();
				String newName = copy.createUniqueName(copy.name);

				if (newName == null) {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				copy.name = newName;
				SpriteEditor.execute(new CreatePalette("Duplicate Palette", editor.getSprite(), copy, i + 1));
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

				PalettesList list = PalettesList.this;
				CommandBatch batch = new CommandBatch("Delete Palette");
				batch.addCommand(new SelectPalette(list, editor.getSprite(), list.getSelectedValue(), tab::setPalette));
				batch.addCommand(new DeletePalette(editor.getSprite(), i));
				SpriteEditor.execute(batch);
			}
		});
	}

	private void promptRename(SpritePalette pal)
	{
		String name = SwingUtils.getInputDialog()
			.setParent(editor.getFrame())
			.setTitle("Set Palette Name")
			.setMessage("Choose a unique palette name")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.setDefault(pal.name)
			.prompt();

		if (name == null) {
			// prompt canceled
			return;
		}

		name = name.trim();

		if (name.isBlank() || name.equals(pal.name)) {
			// invalid name provided
			return;
		}

		String newName = pal.createUniqueName(name);
		if (newName == null) {
			Logger.logError("Could not generate valid name from input!");
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		SpriteEditor.execute(new RenamePalette(this, pal, newName));
	}

	private class SpritePaletteTransferHandle extends DragReorderTransferHandle<SpritePalette>
	{
		@Override
		public void dropAction(SpritePalette pal, int dropIndex)
		{
			SpriteEditor.execute(new ReorderPalette(PalettesList.this, pal, dropIndex));
		}
	}
}
