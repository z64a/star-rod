package app;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

import app.input.InputFileException;
import util.Logger;
import util.Priority;

public class StackTraceDialog extends JDialog implements PropertyChangeListener
{
	private JOptionPane optionPane;
	private JTextArea textArea;
	private File inputFile = null;
	private File logFile = null;

	private static final String OPT_OK = "OK";
	private static final String OPT_DETAILS = "Details";
	private static final String OPT_FILE = "Open File";
	private static final String OPT_LOG = "Open Log";

	public static void display(Throwable t)
	{
		display(t, null);
	}

	public static void display(Throwable t, File log)
	{
		StarRodFrame dialogFrame = new StarRodFrame("Exception");
		dialogFrame.setUndecorated(true);
		dialogFrame.setVisible(true);

		new StackTraceDialog(dialogFrame, t, log);
		dialogFrame.dispose();
	}

	private StackTraceDialog(StarRodFrame frame, Throwable e, File log)
	{
		super(frame, true);
		logFile = log;

		StackTraceElement[] stackTrace = e.getStackTrace();

		textArea = new JTextArea(20, 50);
		textArea.setEditable(false);

		String title = e.getClass().getSimpleName();
		if (title.isEmpty())
			title = "Anonymous Exception";

		if (e instanceof AssertionError)
			title = "Assertion Failed";

		Logger.log(title, Priority.ERROR);
		Logger.log(e.getMessage(), Priority.IMPORTANT);

		frame.setTitle(title);

		setTitle(title);

		textArea.append(e.getClass() + System.lineSeparator());
		for (StackTraceElement ele : stackTrace) {
			Logger.log("  at " + ele, Priority.IMPORTANT);
			textArea.append("  at " + ele + System.lineSeparator());
		}

		if (Environment.isCommandLine())
			return;

		StringBuilder msgBuilder = new StringBuilder();

		if (e instanceof InputFileException) {
			InputFileException ifx = (InputFileException) e;
			msgBuilder.append(ifx.getOrigin());
			msgBuilder.append(System.lineSeparator());
			inputFile = ifx.getSourceFile();
		}
		else if (e instanceof StarRodException) {
			logFile = ((StarRodException) e).log;
		}

		if (e.getMessage() != null)
			msgBuilder.append(e.getMessage());
		else if (stackTrace.length > 0)
			msgBuilder.append("at " + stackTrace[0].toString() + System.lineSeparator());

		String[] options;
		if (logFile != null)
			options = new String[] { OPT_OK, OPT_DETAILS, OPT_LOG };
		else if (inputFile != null)
			options = new String[] { OPT_OK, OPT_DETAILS, OPT_FILE };
		else
			options = new String[] { OPT_OK, OPT_DETAILS };

		optionPane = new JOptionPane(
			msgBuilder.toString(),
			JOptionPane.ERROR_MESSAGE,
			JOptionPane.YES_NO_CANCEL_OPTION,
			Environment.ICON_ERROR,
			options,
			options[0]);

		setContentPane(optionPane);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		optionPane.addPropertyChangeListener(this);

		pack();

		setLocationRelativeTo(null);
		setResizable(false);
		setVisible(true);
	}

	@Override
	public void propertyChange(PropertyChangeEvent e)
	{
		String prop = e.getPropertyName();

		if (isVisible() && (e.getSource() == optionPane) && (JOptionPane.VALUE_PROPERTY.equals(prop)
			|| JOptionPane.INPUT_VALUE_PROPERTY.equals(prop))) {
			Object value = optionPane.getValue();

			if (value == JOptionPane.UNINITIALIZED_VALUE) {
				//ignore reset
				return;
			}

			//Reset the JOptionPane's value.
			//If you don't do this, then if the user
			//presses the same button next time, no
			//property change event will be fired.
			optionPane.setValue(
				JOptionPane.UNINITIALIZED_VALUE);

			if (value.equals(OPT_DETAILS)) {
				JScrollPane detailScrollPane = new JScrollPane(textArea);
				detailScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

				int choice = SwingUtils.getOptionDialog()
					.setTitle("Exception Details")
					.setMessage(detailScrollPane)
					.setMessageType(JOptionPane.ERROR_MESSAGE)
					.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
					.setIcon(Environment.ICON_ERROR)
					.setOptions("OK", "Copy to Clipboard")
					.choose();

				if (choice == 1) {
					StringSelection stringSelection = new StringSelection(textArea.getText());
					Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
					cb.setContents(stringSelection, null);
				}

			}
			else if (value.equals(OPT_FILE))
				StarRodMain.openTextFile(inputFile);
			else if (value.equals(OPT_LOG))
				StarRodMain.openTextFile(logFile);
			else
				dispose();
		}
	}
}
