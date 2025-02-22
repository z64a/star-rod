package util.ui;

import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JList;
import javax.swing.ListSelectionModel;

// a special JList that supports drag and drop
public class DragReorderList<T> extends JList<T>
{
	public DragReorderList()
	{
		super();

		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setTransferHandler(new DragReorderTransferHandle<T>());
		setDropMode(DropMode.INSERT);
		setDragEnabled(true);
	}

	public DefaultListModel<T> getDefaultModel()
	{
		return (DefaultListModel<T>) getModel();
	}

	public void setTransferHandler(DragReorderTransferHandle<T> handler)
	{
		super.setTransferHandler(handler);
		handler.attachToList(this);
	}

	public boolean isDragging()
	{
		@SuppressWarnings("unchecked")
		DragReorderTransferHandle<T> handler = (DragReorderTransferHandle<T>) getTransferHandler();
		return handler.isDragging();
	}
}
