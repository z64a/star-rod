package game.globals.editor;

import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

import util.Logger;
import util.ui.FilteredListModel;

// a special JList that supports drag and drop
public class FilteredDragList<T> extends JList<T>
{
	public FilteredDragList()
	{
		super();

		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setTransferHandler(new ReorderingTransferHandler());
		setDropMode(DropMode.INSERT);
		setDragEnabled(true);
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

	private class ReorderingTransferHandler extends TransferHandler
	{
		private DataFlavor dummyFlavor;
		private DataFlavor[] supportedFlavors = new DataFlavor[1];

		private int dropDestination;

		public ReorderingTransferHandler()
		{
			try {
				dummyFlavor = new DataFlavor(
					DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + Integer.class.getName() + "\"");
				supportedFlavors[0] = dummyFlavor;
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

			if (support.getComponent() != FilteredDragList.this)
				return false;

			support.setShowDropLocation(true);

			if (!support.isDataFlavorSupported(dummyFlavor))
				return false;

			JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
			dropDestination = dl.getIndex();

			return true;
		}

		@Override
		protected Transferable createTransferable(JComponent c)
		{
			JList<?> list = (JList<?>) c;
			int selectedIndex = list.getSelectedIndex();
			return (selectedIndex == -1) ? null : new DummyTransferable();
		}

		@Override
		public int getSourceActions(JComponent c)
		{
			return MOVE;
		}

		@Override
		public boolean importData(TransferHandler.TransferSupport support)
		{
			if (!canImport(support))
				return false;

			FilteredListModel<T> model = (FilteredListModel<T>) getModel();
			DefaultListModel<T> source = model.getSource();

			int sourceDropIndex = model.getIndexFor(dropDestination);

			T obj = getSelectedValue();
			int selectedIndex = getSelectedIndex();

			if (selectedIndex < sourceDropIndex)
				sourceDropIndex--;

			source.removeElement(obj);
			source.insertElementAt(obj, sourceDropIndex);
			setSelectedIndex(sourceDropIndex);

			return true;
		}

		// we don't actually transfer anything through the DnD system
		private class DummyTransferable implements Transferable
		{
			public DummyTransferable()
			{}

			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
			{
				if (!isDataFlavorSupported(flavor))
					throw new UnsupportedFlavorException(flavor);

				return null;
			}

			@Override
			public DataFlavor[] getTransferDataFlavors()
			{
				return supportedFlavors;
			}

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor)
			{
				return dummyFlavor.equals(flavor);
			}
		}
	}
}
