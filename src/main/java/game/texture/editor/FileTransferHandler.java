package game.texture.editor;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.TransferHandler;

import game.map.editor.common.BaseEditor;

public class FileTransferHandler extends TransferHandler
{
	public static interface FileImporter
	{
		public void importFiles(List<File> fileList);
	}

	private BaseEditor editor;
	private FileImporter importer;

	public FileTransferHandler(BaseEditor editor, FileImporter importer)
	{
		this.editor = editor;
		this.importer = importer;
	}

	@Override
	public boolean canImport(TransferSupport support)
	{
		if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
			return false;

		if (editor.areDialogsOpen())
			return false;

		boolean copySupported = (COPY & support.getSourceDropActions()) == COPY;
		if (!copySupported)
			return false;

		support.setDropAction(COPY);

		return true;
	}

	@Override
	public boolean importData(TransferSupport support)
	{
		if (!canImport(support))
			return false;

		Transferable t = support.getTransferable();

		try {
			@SuppressWarnings("unchecked")
			List<File> fileList = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
			if (!fileList.isEmpty())
				importer.importFiles(fileList);
		}
		catch (UnsupportedFlavorException | IOException e) {
			return false;
		}

		return true;
	}
}
