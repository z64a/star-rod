package app;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

public class SwingUtils
{
	public static final Insets TEXTBOX_INSETS = new Insets(2, 4, 2, 4);

	public static final void enableComponents(Container root, boolean enabled)
	{
		root.setEnabled(enabled);
		enableChildComponents(root, enabled);
	}

	private static final void enableChildComponents(Container container, boolean enabled)
	{
		Component[] components = container.getComponents();
		for (Component component : components) {
			component.setEnabled(enabled);
			if (component instanceof Container child)
				enableComponents(child, enabled);
		}
	}

	private static final StarRodFrame createDialogFrame(Component parentComponent, String title)
	{
		StarRodFrame dialogFrame = new StarRodFrame(title);
		dialogFrame.setUndecorated(true);
		dialogFrame.setVisible(true);
		dialogFrame.pack();
		dialogFrame.setLocationRelativeTo(parentComponent);
		return dialogFrame;
	}

	public static class OpenDialogCounter
	{
		private int count;

		public OpenDialogCounter()
		{
			count = 0;
		}

		public void increment()
		{
			count++;
		}

		public void decrement()
		{
			count--;
		}

		public void reset()
		{
			count = 0;
		}

		public boolean isZero()
		{
			return count == 0;
		}
	}

	private static enum DialogType
	{
		MESSAGE,
		CONFIRM,
		INPUT,
		OPTION,
		WARNING,
		ERROR
	}

	public static final DialogBuilder getMessageDialog()
	{
		return new DialogBuilder(DialogType.MESSAGE);
	}

	public static final DialogBuilder getConfirmDialog()
	{
		return new DialogBuilder(DialogType.CONFIRM);
	}

	public static final DialogBuilder getInputDialog()
	{
		return new DialogBuilder(DialogType.INPUT);
	}

	public static final DialogBuilder getOptionDialog()
	{
		return new DialogBuilder(DialogType.OPTION);
	}

	public static final DialogBuilder getWarningDialog()
	{
		return new DialogBuilder(DialogType.WARNING);
	}

	public static final DialogBuilder getErrorDialog()
	{
		return new DialogBuilder(DialogType.ERROR);
	}

	public static class DialogBuilder
	{
		private final DialogType type;
		private String title = "TITLE MISSING";
		private Object message = "MESSAGE MISSING";
		private int optionType;
		private int messageType;
		private Icon icon;
		private Object[] options = null;
		private Object initialValue = null;
		private Component parentComponent = null;
		private OpenDialogCounter counter = null;

		private DialogBuilder(DialogType type)
		{
			this.type = type;

			// set defaults
			switch (type) {
				case MESSAGE:
					optionType = JOptionPane.DEFAULT_OPTION;
					messageType = JOptionPane.INFORMATION_MESSAGE;
					break;
				case CONFIRM:
					optionType = JOptionPane.YES_NO_CANCEL_OPTION;
					messageType = JOptionPane.QUESTION_MESSAGE;
					break;
				case INPUT:
					break;
				case OPTION:
					break;
				case WARNING:
					messageType = JOptionPane.WARNING_MESSAGE;
					break;
				case ERROR:
					messageType = JOptionPane.ERROR_MESSAGE;
					icon = Environment.ICON_ERROR;
					break;
			}
		}

		public DialogBuilder setTitle(String title)
		{
			this.title = title;
			return this;
		}

		public DialogBuilder setMessage(Object message)
		{
			this.message = message;
			return this;
		}

		public DialogBuilder setMessage(String ... lines)
		{
			this.message = String.join(System.lineSeparator(), lines);
			return this;
		}

		public DialogBuilder setMessageType(int messageType)
		{
			this.messageType = messageType;
			return this;
		}

		public DialogBuilder setOptionsType(int optionType)
		{
			this.optionType = optionType;
			return this;
		}

		public DialogBuilder setIcon(Icon icon)
		{
			this.icon = icon;
			return this;
		}

		public DialogBuilder setOptions(String ... options)
		{
			this.options = options;
			return this;
		}

		public DialogBuilder setDefault(Object initialValue)
		{
			this.initialValue = initialValue;
			return this;
		}

		public DialogBuilder setParent(Component parentComponent)
		{
			this.parentComponent = parentComponent;
			return this;
		}

		public DialogBuilder setCounter(OpenDialogCounter counter)
		{
			this.counter = counter;
			return this;
		}

		public void show()
		{
			choose();
		}

		public void showLater()
		{
			if (SwingUtilities.isEventDispatchThread()) {
				choose();
			}
			else {
				SwingUtilities.invokeLater(() -> {
					choose();
				});
			}
		}

		public String prompt()
		{
			if (counter != null)
				counter.increment();

			StarRodFrame dialogFrame = createDialogFrame(parentComponent, title);

			Object result = JOptionPane.showInputDialog(parentComponent,
				message, title, messageType,
				icon, options, initialValue);

			dialogFrame.dispose();

			if (counter != null)
				counter.decrement();

			return (String) result;
		}

		public int choose()
		{
			if (counter != null)
				counter.increment();

			StarRodFrame dialogFrame = createDialogFrame(parentComponent, title);

			int result = JOptionPane.showOptionDialog(parentComponent,
				message, title, optionType, messageType,
				icon, options, initialValue);

			dialogFrame.dispose();

			if (counter != null)
				counter.decrement();

			return result;
		}
	}

	public static final void showDialog(JDialog dialog, String title)
	{
		dialog.setTitle(title);
		dialog.setIconImage(Environment.getDefaultIconImage());

		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	public static final void showModalDialog(JDialog dialog, String title)
	{
		dialog.setTitle(title);
		dialog.setIconImage(Environment.getDefaultIconImage());

		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setModal(true);
		dialog.setVisible(true);

		dialog.dispose();
	}

	/**
	 * Installs a listener to receive notification when the text of any
	 * {@code JTextComponent} is changed. Internally, it installs a
	 * {@link DocumentListener} on the text component's {@link Document},
	 * and a {@link PropertyChangeListener} on the text component to detect
	 * if the {@code Document} itself is replaced.
	 *
	 * @param text any text component, such as a {@link JTextField}
	 *        or {@link JTextArea}
	 * @param changeListener a listener to receieve {@link ChangeEvent}s
	 *        when the text is changed; the source object for the events
	 *        will be the text component
	 * @throws NullPointerException if either parameter is null
	 * @autor  stackoverflow user: Boann
	 */
	public static void addChangeListener(JTextComponent text, ChangeListener changeListener)
	{
		Objects.requireNonNull(text);
		Objects.requireNonNull(changeListener);
		DocumentListener dl = new DocumentListener() {
			private int lastChange = 0, lastNotifiedChange = 0;

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				changedUpdate(e);
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				changedUpdate(e);
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				lastChange++;
				SwingUtilities.invokeLater(() -> {
					if (lastNotifiedChange != lastChange) {
						lastNotifiedChange = lastChange;
						changeListener.stateChanged(new ChangeEvent(text));
					}
				});
			}
		};
		text.addPropertyChangeListener("document", (PropertyChangeEvent e) -> {
			Document d1 = (Document) e.getOldValue();
			Document d2 = (Document) e.getNewValue();
			if (d1 != null)
				d1.removeDocumentListener(dl);
			if (d2 != null)
				d2.addDocumentListener(dl);
			dl.changedUpdate(null);
		});
		Document d = text.getDocument();
		if (d != null)
			d.addDocumentListener(dl);
	}

	public static final void addTextFieldFilter(JTextField field, String filterPattern)
	{
		((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
			@Override
			public void replace(FilterBypass fb, int offs, int length, String str, AttributeSet a) throws BadLocationException
			{
				super.replace(fb, offs, length, str != null ? str.replaceAll(filterPattern, "") : "", a);
			}

			@Override
			public void insertString(FilterBypass fb, int offs, String str, AttributeSet a) throws BadLocationException
			{
				super.insertString(fb, offs, str.replaceAll(filterPattern, ""), a);
			}
		});
	}

	private static final String REDO_KEY = "redo";
	private static final String UNDO_KEY = "undo";

	private static final KeyStroke undoKeyStroke = KeyStroke.getKeyStroke("control Z");
	private static final KeyStroke redoKeyStroke = KeyStroke.getKeyStroke("control Y");

	public static UndoManager addUndoRedo(JTextComponent component)
	{
		if (component == null)
			return null;

		UndoManager manager = new UndoManager();
		Document document = component.getDocument();
		document.addUndoableEditListener(event -> manager.addEdit(event.getEdit()));

		component.getActionMap().put(REDO_KEY, new AbstractAction(REDO_KEY) {
			@Override
			public void actionPerformed(ActionEvent evt)
			{
				try {
					if (manager.canRedo()) {
						manager.redo();
					}
				}
				catch (CannotRedoException ignore) {}
			}
		});
		component.getInputMap().put(redoKeyStroke, REDO_KEY);

		component.getActionMap().put(UNDO_KEY, new AbstractAction(UNDO_KEY) {
			@Override
			public void actionPerformed(ActionEvent evt)
			{
				try {
					if (manager.canUndo()) {
						manager.undo();
					}
				}
				catch (CannotUndoException ignore) {}
			}
		});
		component.getInputMap().put(undoKeyStroke, UNDO_KEY);

		return manager;
	}

	public static final JLabel getLabel(String text, float point)
	{
		JLabel lbl = new JLabel(text);
		setFontSize(lbl, point);
		return lbl;
	}

	public static final JLabel getCenteredLabel(String text, float point)
	{
		JLabel lbl = new JLabel(text, SwingConstants.CENTER);
		setFontSize(lbl, point);
		return lbl;
	}

	public static JLabel getLabel(String text, int horizontalAlignment, int point)
	{
		JLabel lbl = new JLabel(text, horizontalAlignment);
		setFontSize(lbl, point);
		return lbl;
	}

	public static final void setFontSize(JComponent comp, float point)
	{
		comp.setFont(comp.getFont().deriveFont(point));
	}

	public static void centerSpinnerText(JSpinner spinner)
	{
		((DefaultEditor) spinner.getEditor()).getTextField().setHorizontalAlignment(SwingConstants.CENTER);
	}

	public static void centerComboBoxText(JComboBox<?> box)
	{
		((JLabel) box.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
	}

	// adapted from https://www.codejava.net/java-se/graphics/drawing-an-image-with-automatic-scaling
	// original author: Nam Ha Minh
	public static void centerAndFitImage(Image image, Component canvas, Graphics2D g2)
	{
		int imgWidth = image.getWidth(null);
		int imgHeight = image.getHeight(null);

		double imgAspect = (double) imgHeight / imgWidth;

		int canvasWidth = canvas.getWidth();
		int canvasHeight = canvas.getHeight();

		double canvasAspect = (double) canvasHeight / canvasWidth;

		int x1 = 0; // top left X position
		int y1 = 0; // top left Y position
		int x2 = 0; // bottom right X position
		int y2 = 0; // bottom right Y position

		if (imgWidth < canvasWidth && imgHeight < canvasHeight) {
			// the image is smaller than the canvas
			x1 = (canvasWidth - imgWidth) / 2;
			y1 = (canvasHeight - imgHeight) / 2;
			x2 = imgWidth + x1;
			y2 = imgHeight + y1;
		}
		else {
			if (canvasAspect > imgAspect) {
				y1 = canvasHeight;
				// keep image aspect ratio
				canvasHeight = (int) (canvasWidth * imgAspect);
				y1 = (y1 - canvasHeight) / 2;
			}
			else {
				x1 = canvasWidth;
				// keep image aspect ratio
				canvasWidth = (int) (canvasHeight / imgAspect);
				x1 = (x1 - canvasWidth) / 2;
			}
			x2 = canvasWidth + x1;
			y2 = canvasHeight + y1;
		}

		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(image, x1, y1, x2, y2, 0, 0, imgWidth, imgHeight, null);
	}

	public static void addBorderPadding(JComponent c)
	{
		c.setBorder(BorderFactory.createCompoundBorder(
			c.getBorder(),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
	}

	public static void addVerticalBorderPadding(JComponent c)
	{
		c.setBorder(BorderFactory.createCompoundBorder(
			c.getBorder(),
			BorderFactory.createEmptyBorder(2, 0, 2, 0)));
	}

	public static JLabel getLabelWithTooltip(String text, String toolTip)
	{
		JLabel lbl = getLabel(text, SwingConstants.RIGHT, 12);
		lbl.setToolTipText(toolTip);
		return lbl;
	}

	public static JLabel getLabelWithTooltip(String text, int alignment, String toolTip)
	{
		JLabel lbl = getLabel(text, alignment, 12);
		lbl.setToolTipText(toolTip);
		return lbl;
	}

	public static Color getTextColor()
	{
		return UIManager.getColor("Label.foreground");
	}

	private static Color getBackgoundColor()
	{
		return UIManager.getColor("Label.background");
	}

	private static int getBackgroundLuminance()
	{
		Color c = getBackgoundColor();
		return (int) (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
	}

	public static Color getRedTextColor()
	{
		int lum = getBackgroundLuminance();
		if (lum > 110)
			return new Color(220, 0, 0);
		else
			return new Color(255, 100, 100);
	}

	public static Color getGreenTextColor()
	{
		int lum = getBackgroundLuminance();
		if (lum > 110)
			return new Color(0, 120, 0);
		else
			return new Color(80, 250, 80);
	}

	public static Color getBlueTextColor()
	{
		int lum = getBackgroundLuminance();
		if (lum > 110)
			return new Color(0, 40, 255);
		else
			return new Color(0, 180, 255);
	}

	public static Color getGreyTextColor()
	{
		int lum = getBackgroundLuminance();
		if (lum > 110)
			return new Color(40, 40, 40);
		else
			return new Color(180, 180, 180);
	}

	public static String makeFontTag(Color c)
	{
		return "<font color=rgb(" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + ")>";
	}

	public static enum TextColor
	{
		NORMAL, RED, GREEN, BLUE
	}

	public static void setTextAndColor(JLabel label, TextColor color, String text)
	{
		label.setText(text);
		switch (color) {
			case NORMAL:
				label.setForeground(null);
				break;
			case RED:
				label.setForeground(getRedTextColor());
				break;
			case GREEN:
				label.setForeground(getGreenTextColor());
				break;
			case BLUE:
				label.setForeground(getBlueTextColor());
				break;
		}
	}
}
