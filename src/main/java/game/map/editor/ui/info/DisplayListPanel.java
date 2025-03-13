package game.map.editor.ui.info;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.alexandriasoftware.swing.JSplitButton;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.ui.SwingGUI;
import game.map.mesh.AbstractMesh;
import game.map.mesh.TexturedMesh.DisplayListModel;
import game.map.mesh.Triangle;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import game.map.shape.commands.ChangeGeometryFlags;
import game.map.shape.commands.ChangeGeometryFlags.GeometryFlagsPanel;
import game.map.shape.commands.ClearGeometryFlags;
import game.map.shape.commands.CustomCommand;
import game.map.shape.commands.CustomCommand.CustomCommandPanel;
import game.map.shape.commands.DisplayCommand;
import game.map.shape.commands.DisplayCommand.CmdType;
import game.map.shape.commands.FlushPipeline;
import game.map.shape.commands.SetGeometryFlags;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.identity.IdentityHashSet;

public class DisplayListPanel extends JPanel implements IShutdownListener, ListSelectionListener
{
	// singleton
	private static DisplayListPanel instance = null;

	public static DisplayListPanel instance()
	{
		if (instance == null) {
			instance = new DisplayListPanel();
			MapEditor.instance().registerOnShutdown(instance);
		}
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
	}

	private final JList<DisplayCommand> commandList;
	private boolean ignoreListSelectionEvents = false;
	private boolean additiveSelection = false;

	private Model mdl;
	private DisplayCommand popupCommand;

	private int dropDestination;

	private DisplayListPanel()
	{
		// list selection

		commandList = new JList<>() {
			@Override
			public int locationToIndex(Point location)
			{
				int index = super.locationToIndex(location);
				if (index != -1 && !getCellBounds(index, index).contains(location)) {
					return -1;
				}
				else {
					return index;
				}
			}
		};

		commandList.setSelectionModel(new DefaultListSelectionModel() {
			@Override
			public void addSelectionInterval(int i, int j)
			{
				additiveSelection = true;
				super.addSelectionInterval(i, j);
			}

			@Override
			public void setSelectionInterval(int anchor, int lead)
			{
				additiveSelection = false;
				super.setSelectionInterval(anchor, lead);
			}

		});

		commandList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		commandList.addListSelectionListener(this);

		commandList.setTransferHandler(new DisplayCommandTransferHandler());
		commandList.setDropMode(DropMode.INSERT);
		commandList.setDragEnabled(true);

		// key bindings

		commandList.getInputMap().put(KeyStroke.getKeyStroke("control D"), "DuplicateSelected");
		commandList.getActionMap().put("DuplicateSelected", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (!commandList.isSelectionEmpty())
					MapEditor.execute(new DuplicateDisplayCommands(mdl));
			}
		});

		commandList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "DeleteCommands");
		commandList.getActionMap().put("DeleteCommands", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (!commandList.isSelectionEmpty())
					MapEditor.execute(new RemoveDisplayCommands(mdl));
			}
		});

		// popup menu

		JMenuItem editFlagsMenuItem = new JMenuItem("Edit");
		editFlagsMenuItem.addActionListener((e) -> {
			ChangeGeometryFlags cmd = (ChangeGeometryFlags) popupCommand;
			GeometryFlagsPanel pane = new GeometryFlagsPanel(cmd);
			SwingGUI.instance().prompt_ConfirmDialog(pane, "Geometry Mode Flags", () -> {
				MapEditor.execute(cmd.new SetFlags(mdl, pane));
			});
		});

		JPopupMenu geometryModeOptionsMenu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(geometryModeOptionsMenu);

		geometryModeOptionsMenu.add(editFlagsMenuItem);

		JMenuItem mergeMenuItem = new JMenuItem("Add Selected Triangles");
		mergeMenuItem.addActionListener((e) -> {
			MapEditor.execute(new MergeTriangles(mdl, (TriangleBatch) popupCommand));
		});

		JMenuItem primitiveMenuItem = new JMenuItem("Add Primitive Triangles");
		primitiveMenuItem.addActionListener((e) -> {
			SwingGUI.instance().prompt_AddPrimitiveTriangles((TriangleBatch) popupCommand);
		});

		JPopupMenu drawTriangleOptionsMenu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(drawTriangleOptionsMenu);

		drawTriangleOptionsMenu.add(mergeMenuItem);
		drawTriangleOptionsMenu.add(primitiveMenuItem);

		JMenuItem editCustomMenuItem = new JMenuItem("Edit");
		editCustomMenuItem.addActionListener((e) -> {
			CustomCommand cmd = (CustomCommand) popupCommand;
			CustomCommandPanel pane = new CustomCommandPanel(cmd);
			SwingGUI.instance().prompt_ConfirmDialog(pane, "Custom F3DEX2 Command", () -> {
				MapEditor.execute(cmd.new SetValues(mdl, pane));
			});
		});

		JPopupMenu customOptionsMenu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(customOptionsMenu);

		customOptionsMenu.add(editCustomMenuItem);

		commandList.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e)
			{
				check(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				check(e);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				// double click
				if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
					int index = commandList.locationToIndex(e.getPoint());

					Rectangle cellBounds = commandList.getCellBounds(index, index);
					if (!cellBounds.contains(e.getPoint()))
						return;

					DisplayCommand clickedCommand = commandList.getModel().getElementAt(index);
					switch (clickedCommand.getType()) {
						case SetGeometryFlags:
						case ClrGeometryFlags:
							ChangeGeometryFlags cmd = (ChangeGeometryFlags) clickedCommand;
							GeometryFlagsPanel pane = new GeometryFlagsPanel(cmd);
							SwingGUI.instance().prompt_ConfirmDialog(pane, "Geometry Mode Flags", () -> {
								MapEditor.execute(cmd.new SetFlags(mdl, pane));
							});
							break;
						default:
					}
				}
			}

			public void check(MouseEvent e)
			{
				int index = commandList.locationToIndex(e.getPoint());
				if (e.isPopupTrigger()) {
					if (index < 0)
						return;

					Rectangle cellBounds = commandList.getCellBounds(index, index);
					if (!cellBounds.contains(e.getPoint()))
						return;

					JPopupMenu optionsMenu;
					popupCommand = commandList.getModel().getElementAt(index);
					switch (popupCommand.getType()) {
						case SetGeometryFlags:
							optionsMenu = geometryModeOptionsMenu;
							break;
						case ClrGeometryFlags:
							optionsMenu = geometryModeOptionsMenu;
							break;
						case DrawTriangleBatch:
							optionsMenu = drawTriangleOptionsMenu;
							break;
						case Custom:
							optionsMenu = customOptionsMenu;
							break;
						default:
							optionsMenu = null;
					}

					if (optionsMenu != null) {
						commandList.addSelectionInterval(index, index);
						optionsMenu.show(commandList, e.getX(), e.getY());
					}
				}
				else {
					// click in blank region clears selection (called twice per click)
					if (index < 0)
						MapEditor.instance().selectionManager.clearDisplayCommandsFromGUI();
				}
			}
		});

		// buttons

		JButton cleanupButton = new JButton("Clean Up");
		cleanupButton.addActionListener(e -> {
			MapEditor.execute(new CleanupDisplayList(mdl));
		});
		cleanupButton.setToolTipText("Merge adjacent triangle batches and remove degenerates.");
		SwingUtils.addBorderPadding(cleanupButton);

		JMenuItem item;
		JPopupMenu commandMenu = new JPopupMenu();
		SwingGUI.instance().registerPopupMenu(commandMenu);
		JSplitButton addCommandButton = new JSplitButton("Add");
		addCommandButton.setPopupMenu(commandMenu);
		addCommandButton.setAlwaysPopup(true);
		SwingUtils.addBorderPadding(addCommandButton);

		item = new JMenuItem(CmdType.DrawTriangleBatch.toString());
		item.addActionListener(e -> {
			TriangleBatch newBatch = new TriangleBatch(mdl.getMesh());
			newBatch.setParent(mdl.getMesh());
			MapEditor.execute(new AddDisplayCommand(mdl, newBatch));
		});
		commandMenu.add(item);

		item = new JMenuItem(CmdType.PipeSync.toString());
		item.addActionListener(e -> {
			MapEditor.execute(new AddDisplayCommand(mdl, new FlushPipeline(mdl.getMesh())));
		});
		commandMenu.add(item);

		item = new JMenuItem(CmdType.SetGeometryFlags.toString());
		item.addActionListener(e -> {
			MapEditor.execute(new AddDisplayCommand(mdl, new SetGeometryFlags(mdl.getMesh())));
		});
		commandMenu.add(item);

		item = new JMenuItem(CmdType.ClrGeometryFlags.toString());
		item.addActionListener(e -> {
			MapEditor.execute(new AddDisplayCommand(mdl, new ClearGeometryFlags(mdl.getMesh())));
		});
		commandMenu.add(item);

		item = new JMenuItem(CmdType.Custom.toString());
		item.addActionListener(e -> {
			MapEditor.execute(new AddDisplayCommand(mdl, new CustomCommand(mdl.getMesh())));
		});
		commandMenu.add(item);

		// layout
		commandList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JScrollPane listScrollPane = new JScrollPane(commandList);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		setLayout(new MigLayout("fill, ins n 16 0 16, wrap"));
		add(listScrollPane, "grow, pushy");

		add(addCommandButton, "growx, pushx, sg buttons, split 2");
		add(cleanupButton, "growx, pushx, sg buttons");
	}

	public void setModel(Model mdl)
	{
		this.mdl = mdl;
		DefaultListModel<DisplayCommand> listModel = mdl.getMesh().displayListModel;

		ignoreListSelectionEvents = true;
		commandList.setModel(listModel);
		for (int i = 0; i < listModel.size(); i++) {
			DisplayCommand cmd = listModel.getElementAt(i);
			if (cmd.selected)
				commandList.addSelectionInterval(i, i);
		}
		ignoreListSelectionEvents = false;
	}

	@Override
	public void valueChanged(ListSelectionEvent e)
	{
		if (e.getValueIsAdjusting() || ignoreListSelectionEvents)
			return;

		List<DisplayCommand> added = new LinkedList<>();
		List<DisplayCommand> removed = new LinkedList<>();

		if (!additiveSelection)
			MapEditor.instance().selectionManager.clearDisplayCommandsFromGUI();

		DefaultListModel<DisplayCommand> listModel = mdl.getMesh().displayListModel;
		boolean[] lastState = new boolean[listModel.size()];
		boolean[] newState = new boolean[listModel.size()];

		for (int i = 0; i < listModel.size(); i++) {
			DisplayCommand cmd = listModel.getElementAt(i);
			lastState[i] = cmd.selected;
			newState[i] = commandList.isSelectedIndex(i);

			if ((lastState[i] != newState[i])) {
				if (newState[i])
					added.add(cmd);
				else
					removed.add(cmd);
			}
		}

		MapEditor.instance().selectionManager.selectDisplayCommandsFromGUI(added);
		MapEditor.instance().selectionManager.deselectDisplayCommandsFromGUI(removed);
	}

	private class DisplayCommandTransferHandler extends TransferHandler
	{
		private DataFlavor nodesFlavor;
		private DataFlavor[] flavors = new DataFlavor[1];

		public DisplayCommandTransferHandler()
		{
			try {
				String mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + DisplayCommand[].class.getName() + "\"";
				nodesFlavor = new DataFlavor(mimeType);
				flavors[0] = nodesFlavor;
			}
			catch (ClassNotFoundException e) {
				Logger.logWarning("ClassNotFound: " + e.getMessage());
			}
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			if (!support.isDrop())
				return false;

			support.setShowDropLocation(true);

			if (!support.isDataFlavorSupported(nodesFlavor))
				return false;

			JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
			dropDestination = dl.getIndex();

			return true;
		}

		@Override
		protected Transferable createTransferable(JComponent c)
		{
			JList<?> list = (JList<?>) c;
			int[] indicies = list.getSelectedIndices();
			if (indicies != null) {
				String[] names = { "star", "rod" };
				return new DummyTransferable(names);
			}
			return null;
		}

		@Override
		public int getSourceActions(JComponent c)
		{
			return COPY_OR_MOVE;
		}

		@Override
		public boolean importData(TransferHandler.TransferSupport support)
		{
			if (!canImport(support))
				return false;

			MapEditor.execute(new MoveCommandsTo(mdl, dropDestination));
			return true;
		}

		// we don't actually transfer anything
		private class DummyTransferable implements Transferable
		{
			public String[] objectNames;

			public DummyTransferable(String[] names)
			{
				objectNames = names;
			}

			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
			{
				if (!isDataFlavorSupported(flavor))
					throw new UnsupportedFlavorException(flavor);

				return objectNames;
			}

			@Override
			public DataFlavor[] getTransferDataFlavors()
			{
				return flavors;
			}

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor)
			{
				return nodesFlavor.equals(flavor);
			}
		}
	}

	private class ModifyDisplayList extends AbstractCommand
	{
		private final Model mdl;
		protected final DisplayListModel listModel;
		private final List<AbstractCommand> additionalEDTCommands;

		private DisplayCommand[] oldOrder;
		private DisplayCommand[] newOrder;
		private int[] oldSelected;
		private int[] newSelected;

		public ModifyDisplayList(Model mdl, String name)
		{
			super(name);

			this.mdl = mdl;
			this.listModel = mdl.getMesh().displayListModel;
			additionalEDTCommands = new LinkedList<>();

			int num = listModel.size();
			oldOrder = new DisplayCommand[num];
			int numSelected = 0;

			for (int i = 0; i < num; i++) {
				oldOrder[i] = listModel.get(i);
				if (commandList.isSelectedIndex(i))
					numSelected++;
			}

			oldSelected = new int[numSelected];

			int j = 0;
			for (int i = 0; i < num; i++) {
				if (commandList.isSelectedIndex(i))
					oldSelected[j++] = i;
			}
		}

		public void setNewOrder(List<DisplayCommand> newOrderList, List<Integer> newSelectedList)
		{
			newOrder = new DisplayCommand[newOrderList.size()];
			int j = 0;
			for (DisplayCommand cmd : newOrderList)
				newOrder[j++] = cmd;

			newSelected = new int[newSelectedList.size()];
			j = 0;
			for (int index : newSelectedList)
				newSelected[j++] = index;
		}

		public void addCommandEDT(AbstractCommand cmd)
		{
			additionalEDTCommands.add(cmd);
		}

		@Override
		public boolean shouldExec()
		{
			if (newOrder == null || newSelected == null)
				return false;

			if (newOrder.length != oldOrder.length)
				return true;

			if (newSelected.length != oldSelected.length)
				return true;

			for (int i = 0; i < newOrder.length; i++)
				if (newOrder[i] != oldOrder[i])
					return true;

			for (int i = 0; i < newSelected.length; i++)
				if (newSelected[i] != oldSelected[i])
					return true;

			if (!additionalEDTCommands.isEmpty())
				return true;

			return false;
		}

		@Override
		public void exec()
		{
			super.exec();

			SwingUtilities.invokeLater(() -> {
				ignoreListSelectionEvents = true;
				mdl.getMesh().displayListModel.clear();

				for (DisplayCommand cmd : newOrder) {
					mdl.getMesh().displayListModel.addElement(cmd);
				}

				commandList.setSelectedIndices(newSelected);
				ignoreListSelectionEvents = false;

				for (AbstractCommand cmd : additionalEDTCommands)
					cmd.exec();

				mdl.displayListChanged();
			});
		}

		@Override
		public void undo()
		{
			super.undo();

			SwingUtilities.invokeLater(() -> {
				for (AbstractCommand cmd : additionalEDTCommands)
					cmd.undo();

				ignoreListSelectionEvents = true;
				mdl.getMesh().displayListModel.clear();

				for (DisplayCommand cmd : oldOrder) {
					mdl.getMesh().displayListModel.addElement(cmd);
				}

				commandList.setSelectedIndices(oldSelected);
				ignoreListSelectionEvents = false;

				mdl.displayListChanged();
			});
		}
	}

	private final class AddDisplayCommand extends AbstractCommand
	{
		private final Model mdl;
		private final DisplayCommand cmd;

		public AddDisplayCommand(Model mdl, DisplayCommand cmd)
		{
			super("Add Display Command");

			this.mdl = mdl;
			this.cmd = cmd;
		}

		@Override
		public void exec()
		{
			super.exec();

			SwingUtilities.invokeLater(() -> {
				mdl.getMesh().displayListModel.addElement(cmd);
				mdl.displayListChanged();
			});
		}

		@Override
		public void undo()
		{
			super.undo();

			SwingUtilities.invokeLater(() -> {
				mdl.getMesh().displayListModel.removeElement(cmd);
				mdl.displayListChanged();
			});
		}
	}

	private final class RemoveDisplayCommands extends ModifyDisplayList
	{
		private final List<AbstractCommand> deselectCommands;

		public RemoveDisplayCommands(Model mdl)
		{
			super(mdl, "Remove Display Commands");

			ArrayList<DisplayCommand> newOrder = new ArrayList<>();
			ArrayList<Integer> newSelection = new ArrayList<>();

			deselectCommands = new ArrayList<>();

			for (int i = 0; i < listModel.size(); i++) {
				DisplayCommand cmd = listModel.get(i);

				if (commandList.isSelectedIndex(i)) {
					if (cmd instanceof TriangleBatch batch) {
						deselectCommands.add(MapEditor.instance().selectionManager.getModifyTriangles(null, batch.triangles, false));
					}
				}
				else
					newOrder.add(cmd);
			}

			super.setNewOrder(newOrder, newSelection);
		}

		@Override
		public void exec()
		{
			super.exec();

			for (AbstractCommand cmd : deselectCommands)
				cmd.exec();
		}

		@Override
		public void undo()
		{
			for (AbstractCommand cmd : deselectCommands)
				cmd.undo();

			super.undo();
		}
	}

	private final class DuplicateDisplayCommands extends ModifyDisplayList
	{
		public DuplicateDisplayCommands(Model mdl)
		{
			super(mdl, "Duplicate Display Commands");

			ArrayList<DisplayCommand> newOrder = new ArrayList<>();
			ArrayList<Integer> newSelection = new ArrayList<>();

			for (int i = 0; i < listModel.size(); i++) {
				DisplayCommand cmd = listModel.get(i);

				if (commandList.isSelectedIndex(i)) {
					newSelection.add(newOrder.size());
					newOrder.add(cmd.deepCopy());
					newSelection.add(newOrder.size());
				}
				newOrder.add(cmd);
			}

			super.setNewOrder(newOrder, newSelection);
		}
	}

	private final class MoveCommandsTo extends ModifyDisplayList
	{
		public MoveCommandsTo(Model mdl, int dropIndex)
		{
			super(mdl, "Reorder Display Commands");

			ArrayList<Integer> beforeDrop = new ArrayList<>();
			ArrayList<Integer> afterDrop = new ArrayList<>();
			ArrayList<Integer> selection = new ArrayList<>();

			int num = listModel.size();

			for (int i = 0; i < num; i++) {
				if (!commandList.isSelectedIndex(i)) {
					if (i < dropIndex)
						beforeDrop.add(i);
					else
						afterDrop.add(i);
				}
				else
					selection.add(i);
			}

			ArrayList<DisplayCommand> newOrder = new ArrayList<>();
			ArrayList<Integer> newSelection = new ArrayList<>();

			for (int index : beforeDrop)
				newOrder.add(listModel.get(index));

			for (int index : selection) {
				newOrder.add(listModel.get(index));
				newSelection.add(index);
			}

			for (int index : afterDrop)
				newOrder.add(listModel.get(index));

			super.setNewOrder(newOrder, newSelection);
		}
	}

	private final class CleanupDisplayList extends ModifyDisplayList
	{
		private final List<AddTriangles> addCommands;

		public CleanupDisplayList(Model mdl)
		{
			super(mdl, "Cleanup Display List");

			ArrayList<DisplayCommand> newOrder = new ArrayList<>();
			ArrayList<Integer> newSelection = new ArrayList<>();

			addCommands = new LinkedList<>();
			TriangleBatch mergeBatch = null;
			List<Triangle> mergeTriangles = null;
			boolean mergeSelected = false;

			for (int i = 0; i < listModel.size(); i++) {
				DisplayCommand cmd = listModel.get(i);

				if (cmd instanceof TriangleBatch batch) {
					if (commandList.isSelectedIndex(i))
						mergeSelected = true;

					// accumulate merged triangles
					if (mergeBatch == null) {
						mergeBatch = batch;
						mergeTriangles = new ArrayList<>();
					}
					else {
						mergeTriangles.addAll(batch.triangles);
					}
				}
				else {
					// add merged batch
					if (mergeBatch != null) {
						if (!mergeBatch.triangles.isEmpty() || !mergeTriangles.isEmpty()) {
							newOrder.add(mergeBatch);

							if (mergeSelected)
								newSelection.add(newOrder.size());

							if (!mergeTriangles.isEmpty())
								addCommands.add(new AddTriangles(mergeBatch, mergeTriangles));
						}
					}

					// end merge
					mergeBatch = null;
					mergeTriangles = new ArrayList<>();
					mergeSelected = false;
					if (commandList.isSelectedIndex(i))
						newSelection.add(newOrder.size());
					newOrder.add(cmd);
				}
			}

			// handle any trailing triangle batches
			if (mergeBatch != null) {
				if (!mergeBatch.triangles.isEmpty() || !mergeTriangles.isEmpty()) {
					newOrder.add(mergeBatch);

					if (mergeSelected)
						newSelection.add(newOrder.size());

					if (!mergeTriangles.isEmpty())
						addCommands.add(new AddTriangles(mergeBatch, mergeTriangles));
				}
			}

			super.setNewOrder(newOrder, newSelection);
		}

		@Override
		public void exec()
		{
			super.exec();

			for (AbstractCommand cmd : addCommands)
				cmd.exec();
		}

		@Override
		public void undo()
		{
			for (AbstractCommand cmd : addCommands)
				cmd.undo();

			super.undo();
		}
	}

	private final class MergeTriangles extends AbstractCommand
	{
		private final TriangleBatch newParent;

		private final List<Triangle> triangles;
		private final List<TriangleBatch> oldParent;
		private final IdentityHashSet<AbstractMesh> meshes;

		public MergeTriangles(Model mdl, TriangleBatch newParent)
		{
			super("Moving Triangles");
			this.newParent = newParent;

			triangles = MapEditor.instance().selectionManager.getTrianglesFromSelection(Model.class);
			meshes = new IdentityHashSet<>();
			meshes.add(mdl.getMesh());

			Iterator<Triangle> iter = triangles.iterator();
			while (iter.hasNext()) {
				TriangleBatch parent = iter.next().parentBatch;
				if (parent == newParent)
					iter.remove();
			}

			oldParent = new ArrayList<>(triangles.size());
			for (int i = 0; i < triangles.size(); i++) {
				TriangleBatch parentBatch = triangles.get(i).parentBatch;
				oldParent.add(parentBatch);
				meshes.add(parentBatch.parentMesh);
			}
		}

		@Override
		public boolean shouldExec()
		{
			return triangles.size() > 0;
		}

		@Override
		public void exec()
		{
			super.exec();

			for (int i = 0; i < triangles.size(); i++) {
				Triangle t = triangles.get(i);
				oldParent.get(i).triangles.remove(t);
				newParent.triangles.add(t);
				t.setParent(newParent);
			}

			for (AbstractMesh mesh : meshes)
				mesh.parentObject.dirtyAABB = true;

			// update the display command descriptions
			commandList.repaint();
		}

		@Override
		public void undo()
		{
			super.undo();

			for (int i = 0; i < triangles.size(); i++) {
				Triangle t = triangles.get(i);
				newParent.triangles.remove(t);
				oldParent.get(i).triangles.add(t);
				t.setParent(oldParent.get(i));
			}

			for (AbstractMesh mesh : meshes)
				mesh.parentObject.dirtyAABB = true;

			// update the display command descriptions
			commandList.repaint();
		}
	}

	public static final class AddTriangles extends AbstractCommand
	{
		private final TriangleBatch batch;
		private final List<Triangle> triangles;

		public AddTriangles(TriangleBatch parent, List<Triangle> triangles)
		{
			super("Add " + triangles.size() + " Triangles");
			this.batch = parent;
			this.triangles = triangles;
		}

		@Override
		public boolean shouldExec()
		{
			return triangles.size() > 0;
		}

		@Override
		public void exec()
		{
			super.exec();

			for (Triangle t : triangles) {
				batch.triangles.add(t);
				t.setParent(batch);
			}

			batch.parentMesh.parentObject.dirtyAABB = true;

			// update the display command descriptions
			DisplayListPanel.instance().commandList.repaint();
		}

		@Override
		public void undo()
		{
			super.undo();

			for (Triangle t : triangles) {
				batch.triangles.remove(t);
			}

			batch.parentMesh.parentObject.dirtyAABB = true;

			// update the display command descriptions
			DisplayListPanel.instance().commandList.repaint();
		}
	}
}
