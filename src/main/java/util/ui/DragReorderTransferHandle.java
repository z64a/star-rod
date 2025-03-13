package util.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;

import util.Logger;

public class DragReorderTransferHandle<T> extends TransferHandler
{
	private DragReorderList<T> list;

	private DataFlavor dummyFlavor;
	private DataFlavor[] supportedFlavors = new DataFlavor[1];

	private int selectedIndex;
	private int dropDestination;

	private boolean isDragging = false;

	public DragReorderTransferHandle()
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

	public void attachToList(DragReorderList<T> list)
	{
		this.list = list;
	}

	@Override
	public boolean canImport(TransferSupport support)
	{
		if (!support.isDrop())
			return false;

		try {
			@SuppressWarnings("unchecked")
			DragReorderList<T> sourceList = (DragReorderList<T>) support.getTransferable().getTransferData(dummyFlavor);

			if (sourceList != list) // ensure the source and target are the same list
				return false;
		}
		catch (Exception e) {
			return false;
		}

		if (support.getComponent() != list)
			return false;

		support.setShowDropLocation(true);

		if (!support.isDataFlavorSupported(dummyFlavor))
			return false;

		JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
		dropDestination = dl.getIndex();

		return dropDestination >= 0;
	}

	@Override
	protected Transferable createTransferable(JComponent c)
	{
		isDragging = true;
		DragReorderList<?> list = (DragReorderList<?>) c;
		selectedIndex = list.getSelectedIndex();
		return new DummyTransferable(list);
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

		DefaultListModel<T> model = (DefaultListModel<T>) list.getModel();

		if (selectedIndex < 0 || selectedIndex >= model.getSize())
			return false;

		if (selectedIndex < dropDestination)
			dropDestination--;

		T obj = list.getSelectedValue();
		if (obj == null)
			return false;

		dropAction(obj, dropDestination);

		return true;
	}

	public void dropAction(T obj, int dropIndex)
	{
		DefaultListModel<T> model = (DefaultListModel<T>) list.getModel();

		model.removeElement(obj);
		model.insertElementAt(obj, dropIndex);
		list.setSelectedIndex(dropIndex);
	}

	@Override
	protected void exportDone(JComponent source, Transferable data, int action)
	{
		isDragging = false;
	}

	public boolean isDragging()
	{
		return isDragging;
	}

	// we don't actually transfer anything through the DnD system
	private class DummyTransferable implements Transferable
	{
		private final DragReorderList<?> sourceList;

		public DummyTransferable(DragReorderList<?> sourceList)
		{
			this.sourceList = sourceList;
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
		{
			if (!isDataFlavorSupported(flavor))
				throw new UnsupportedFlavorException(flavor);

			return sourceList;
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
