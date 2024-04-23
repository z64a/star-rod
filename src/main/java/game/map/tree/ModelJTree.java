package game.map.tree;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import game.map.editor.MapEditor;
import game.map.editor.ui.MapObjectPanel;
import game.map.editor.ui.SwingGUI;
import game.map.shape.Model;

public class ModelJTree extends MapObjectJTree<Model>
{
	public ModelJTree(MapEditor editor, SwingGUI gui, MapObjectPanel panel)
	{
		super(editor, gui, panel);
	}

	@Override
	protected JPopupMenu createPopupMenu(JPopupMenu popupMenu)
	{
		JMenuItem item = new JMenuItem("Move Selected Here");
		addButtonCommand(item, TreeCommand.POPUP_MOVE_SELECTION);
		popupMenu.add(item);

		item = new JMenuItem("Select Children");
		addButtonCommand(item, TreeCommand.POPUP_SELECT_CHILDREN);
		popupMenu.add(item);

		popupMenu.addSeparator();

		item = new JMenuItem("Create Subgroup");
		addButtonCommand(item, TreeCommand.POPUP_NEW_GROUP);
		popupMenu.add(item);

		item = new JMenuItem("Create Primitive");
		addButtonCommand(item, TreeCommand.POPUP_NEW_PRIMITIVE);
		popupMenu.add(item);

		popupMenu.addSeparator();

		item = new JMenuItem("Import Models");
		addButtonCommand(item, TreeCommand.POPUP_IMPORT_HERE);
		popupMenu.add(item);

		item = new JMenuItem("Paste Models");
		addButtonCommand(item, TreeCommand.POPUP_PASTE_HERE);
		popupMenu.add(item);

		return popupMenu;
	}

	@Override
	public void handleTreeCommand(TreeCommand cmd)
	{
		super.handleTreeCommand(cmd);

		switch (cmd) {
			case DND_MOVE_SELECTION:
				editor.doNextFrame(() -> {
					editor.action_MoveSelectedModels(dropDestination, dropChildIndex);
				});
				break;

			case POPUP_MOVE_SELECTION:
				editor.doNextFrame(() -> {
					editor.action_MoveSelectedModels(popupSource, -1);
				});
				break;

			default:
		}
	}
}
