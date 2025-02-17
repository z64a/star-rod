package game.map.editor.ui;

import java.awt.Component;
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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListSelectionModel;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.border.EtchedBorder;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.map.editor.MapEditor;
import game.map.editor.selection.SelectablePoint.SetPointPosition;
import game.map.marker.PathPoint;
import net.miginfocom.swing.MigLayout;
import util.IterableListModel;
import util.Logger;

public class PathList extends JList<PathPoint>
{
	private boolean ignoreListSelectionEvents = false;
	private boolean additiveSelection = false;

	private int dropDestination;

	public PathList()
	{
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));

		setCellRenderer(new PathPointCellRenderer());

		// selection listening

		setSelectionModel(new DefaultListSelectionModel() {
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

		setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting() || ignoreListSelectionEvents)
				return;

			List<PathPoint> added = new LinkedList<>();
			List<PathPoint> removed = new LinkedList<>();

			if (!additiveSelection)
				MapEditor.instance().selectionManager.clearPointsFromGUI();

			IterableListModel<PathPoint> listModel = getIterableModel();
			boolean[] lastState = new boolean[listModel.size()];
			boolean[] newState = new boolean[listModel.size()];

			for (int i = 0; i < listModel.size(); i++) {
				PathPoint wp = listModel.getElementAt(i);
				lastState[i] = wp.isSelected();
				newState[i] = isSelectedIndex(i);

				if ((lastState[i] != newState[i])) {
					if (newState[i])
						added.add(wp);
					else
						removed.add(wp);
				}
			}

			MapEditor.instance().selectionManager.selectPointsFromGUI(added);
			MapEditor.instance().selectionManager.deselectPointsFromGUI(removed);
		});

		// drag and drop

		setTransferHandler(new PathPointsTransferHandler());
		setDropMode(DropMode.INSERT);
		setDragEnabled(true);

		// double click to edit

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2) {
					int index = locationToIndex(e.getPoint());

					Rectangle cellBounds = getCellBounds(index, index);
					if (!cellBounds.contains(e.getPoint()))
						return;

					PathPoint point = getModel().getElementAt(index);
					if (point != null) {
						int pos[] = SwingGUI.instance().prompt_GetPositionVector(
							point.getX(), point.getY(), point.getZ());
						if (pos != null) {
							MapEditor.execute(new SetPointPosition("Set Path Point Position",
								point, pos[0], pos[1], pos[2]));
						}
					}
				}
			}
		});

		// key bindings

		getInputMap().put(KeyStroke.getKeyStroke("control D"), "DuplicateSelected");
		getActionMap().put("DuplicateSelected", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (!isSelectionEmpty())
					MapEditor.execute(new DuplicatePathPoints(getIterableModel()));
			}
		});

		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "DeleteCommands");
		getActionMap().put("DeleteCommands", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (!isSelectionEmpty())
					MapEditor.execute(new RemovePathPoints(getIterableModel()));
			}
		});
	}

	private IterableListModel<PathPoint> getIterableModel()
	{
		return (IterableListModel<PathPoint>) getModel();
	}

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

	private class PathPointsTransferHandler extends TransferHandler
	{
		private DataFlavor nodesFlavor;
		private DataFlavor[] flavors = new DataFlavor[1];

		public PathPointsTransferHandler()
		{
			try {
				String mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + PathPoint[].class.getName() + "\"";
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

			MapEditor.execute(new ReorderPathPoints(getIterableModel(), dropDestination));
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

	private static class PathPointCellRenderer extends JPanel implements ListCellRenderer<PathPoint>
	{
		private JLabel desc;
		private JLabel labelX;
		private JLabel labelY;
		private JLabel labelZ;

		public PathPointCellRenderer()
		{
			desc = new JLabel("  Position: ");
			labelX = new JLabel("", JLabel.CENTER);
			labelY = new JLabel("", JLabel.CENTER);
			labelZ = new JLabel("", JLabel.CENTER);

			setLayout(new MigLayout("ins 3, fillx", "[15%][sg coord, grow][sg coord, grow][sg coord, grow]"));
			add(desc);
			add(labelX, "growx");
			add(labelY, "growx");
			add(labelZ, "growx");
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends PathPoint> list,
			PathPoint wp,
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

			desc.setForeground(null);
			labelX.setForeground(null);
			labelY.setForeground(null);
			labelZ.setForeground(null);

			if (wp != null) {
				if (wp.degenerate) {
					desc.setForeground(SwingUtils.getRedTextColor());
					labelX.setForeground(SwingUtils.getRedTextColor());
					labelY.setForeground(SwingUtils.getRedTextColor());
					labelZ.setForeground(SwingUtils.getRedTextColor());
				}

				labelX.setText(Integer.toString(wp.getX()));
				labelY.setText(Integer.toString(wp.getY()));
				labelZ.setText(Integer.toString(wp.getZ()));
			}
			else {
				labelX.setText("???");
				labelY.setText("???");
				labelZ.setText("???");
			}

			return this;
		}
	}

	private class ModifyPathPoints extends AbstractCommand
	{
		protected final IterableListModel<PathPoint> listModel;
		private final List<AbstractCommand> additionalEDTCommands;

		private PathPoint[] oldOrder;
		private PathPoint[] newOrder;
		private int[] oldSelected;
		private int[] newSelected;

		public ModifyPathPoints(IterableListModel<PathPoint> listModel, String name)
		{
			super(name);

			this.listModel = listModel;
			additionalEDTCommands = new LinkedList<>();

			int num = listModel.size();
			oldOrder = new PathPoint[num];
			int numSelected = 0;

			for (int i = 0; i < num; i++) {
				oldOrder[i] = listModel.get(i);
				if (isSelectedIndex(i))
					numSelected++;
			}

			oldSelected = new int[numSelected];

			int j = 0;
			for (int i = 0; i < num; i++) {
				if (isSelectedIndex(i))
					oldSelected[j++] = i;
			}
		}

		public void setNewOrder(List<PathPoint> newOrderList, List<Integer> newSelectedList)
		{
			newOrder = new PathPoint[newOrderList.size()];
			int j = 0;
			for (PathPoint wp : newOrderList)
				newOrder[j++] = wp;

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
				listModel.clear();

				for (PathPoint wp : newOrder) {
					listModel.addElement(wp);
				}

				setSelectedIndices(newSelected);
				ignoreListSelectionEvents = false;

				for (AbstractCommand cmd : additionalEDTCommands)
					cmd.exec();
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
				listModel.clear();

				for (PathPoint wp : oldOrder) {
					listModel.addElement(wp);
				}

				setSelectedIndices(oldSelected);
				ignoreListSelectionEvents = false;
			});
		}
	}

	private final class AddPathPoints extends AbstractCommand
	{
		protected final IterableListModel<PathPoint> listModel;
		private final PathPoint point;

		public AddPathPoints(IterableListModel<PathPoint> listModel, PathPoint point)
		{
			super("Add Path Point");

			this.listModel = listModel;
			this.point = point;
		}

		@Override
		public void exec()
		{
			super.exec();

			SwingUtilities.invokeLater(() -> {
				listModel.addElement(point);
			});
		}

		@Override
		public void undo()
		{
			super.undo();

			SwingUtilities.invokeLater(() -> {
				listModel.removeElement(point);
			});
		}
	}

	private final class RemovePathPoints extends ModifyPathPoints
	{
		private final List<AbstractCommand> deselectCommands;

		public RemovePathPoints(IterableListModel<PathPoint> listModel)
		{
			super(listModel, "Remove Path Points");

			ArrayList<PathPoint> newOrder = new ArrayList<>();
			ArrayList<Integer> newSelection = new ArrayList<>();

			deselectCommands = new ArrayList<>();

			for (int i = 0; i < listModel.size(); i++) {
				PathPoint wp = listModel.get(i);

				if (isSelectedIndex(i))
					deselectCommands.add(MapEditor.instance().selectionManager.getModifyPoints(Collections.singleton(wp), null));
				else
					newOrder.add(wp);
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

	private final class DuplicatePathPoints extends ModifyPathPoints
	{
		public DuplicatePathPoints(IterableListModel<PathPoint> listModel)
		{
			super(listModel, "Duplicate Path Points");

			ArrayList<PathPoint> newOrder = new ArrayList<>();
			ArrayList<Integer> newSelection = new ArrayList<>();

			for (int i = 0; i < listModel.size(); i++) {
				PathPoint wp = listModel.get(i);

				if (isSelectedIndex(i)) {
					newSelection.add(newOrder.size());
					newOrder.add(wp.deepCopy());
					newSelection.add(newOrder.size());
				}
				newOrder.add(wp);
			}

			super.setNewOrder(newOrder, newSelection);
		}
	}

	private final class ReorderPathPoints extends ModifyPathPoints
	{
		public ReorderPathPoints(IterableListModel<PathPoint> listModel, int dropIndex)
		{
			super(listModel, "Reorder Path Points");

			ArrayList<Integer> beforeDrop = new ArrayList<>();
			ArrayList<Integer> afterDrop = new ArrayList<>();
			ArrayList<Integer> selection = new ArrayList<>();

			int num = listModel.size();

			for (int i = 0; i < num; i++) {
				if (!isSelectedIndex(i)) {
					if (i < dropIndex)
						beforeDrop.add(i);
					else
						afterDrop.add(i);
				}
				else
					selection.add(i);
			}

			ArrayList<PathPoint> newOrder = new ArrayList<>();
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
}
