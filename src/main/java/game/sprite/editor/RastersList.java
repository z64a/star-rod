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
import game.sprite.SpriteRaster;
import game.sprite.editor.commands.CreateRaster;
import game.sprite.editor.commands.DeleteRaster;
import game.sprite.editor.commands.RenameRaster;
import game.sprite.editor.commands.ReorderRaster;
import game.sprite.editor.commands.SelectRaster;
import util.Logger;
import util.ui.DragReorderList;
import util.ui.DragReorderTransferHandle;

public class RastersList extends DragReorderList<SpriteRaster>
{
	private final SpriteEditor editor;
	private SpriteRaster clipboard = null;

	public boolean ignoreSelectionChange = false;

	public RastersList(SpriteEditor editor, RastersTab tab)
	{
		this.editor = editor;

		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setCellRenderer(new RasterCellRenderer());

		setTransferHandler(new SpriteRasterTransferHandle());

		addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			RastersList list = RastersList.this;
			if (!ignoreSelectionChange)
				SpriteEditor.execute(new SelectRaster(list, editor.getSprite(), list.getSelectedValue(), tab::setRaster));
		});

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				// double click to rename
				if (e.getClickCount() == 2) {
					int index = locationToIndex(e.getPoint());
					if (index != -1) {
						SpriteRaster img = getModel().getElementAt(index);
						promptRename(img);
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
						SpriteRaster img = getModel().getElementAt(index);
						promptRename(img);
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
				SpriteRaster cur = getSelectedValue();
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
				if (i == -1 || clipboard == null || clipboard.getSprite() != editor.getSprite()) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				SpriteRaster copy = clipboard.copy();
				String newName = copy.createUniqueName(copy.name);

				if (newName == null) {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				copy.name = newName;
				SpriteEditor.execute(new CreateRaster("Paste Raster", editor.getSprite(), copy, i + 1));
			}
		});

		am.put("duplicate", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SpriteRaster cur = getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				int i = getSelectedIndex();
				SpriteRaster copy = cur.copy();
				String newName = copy.createUniqueName(copy.name);

				if (newName == null) {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				copy.name = newName;
				SpriteEditor.execute(new CreateRaster("Duplicate Raster", editor.getSprite(), copy, i + 1));
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

				RastersList list = RastersList.this;
				CommandBatch batch = new CommandBatch("Delete Raster");
				batch.addCommand(new SelectRaster(list, editor.getSprite(), list.getSelectedValue(), tab::setRaster));
				batch.addCommand(new DeleteRaster(editor.getSprite(), i));
				SpriteEditor.execute(batch);
			}
		});
	}

	private void promptRename(SpriteRaster img)
	{
		String name = SwingUtils.getInputDialog()
			.setParent(editor.getFrame())
			.setTitle("Set Raster Name")
			.setMessage("Choose a unique raster name")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.setDefault(img.name)
			.prompt();

		if (name == null) {
			// prompt canceled
			return;
		}

		name = name.trim();

		if (name.isBlank() || name.equals(img.name)) {
			// invalid name provided
			return;
		}

		String newName = img.createUniqueName(name);
		if (newName == null) {
			Logger.logError("Could not generate valid name from input!");
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		SpriteEditor.execute(new RenameRaster(this, img, newName));
	}

	private class SpriteRasterTransferHandle extends DragReorderTransferHandle<SpriteRaster>
	{
		@Override
		public void dropAction(SpriteRaster img, int dropIndex)
		{
			SpriteEditor.execute(new ReorderRaster(RastersList.this, img, dropIndex));
		}
	}
}
