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
import javax.swing.JFileChooser;
import javax.swing.JFrame;
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

	private static final JFrame createDialogFrame(Component parentComponent, String title)
	{
		JFrame dialogFrame = new JFrame(title);
		dialogFrame.setUndecorated(true);
		dialogFrame.setVisible(true);
		dialogFrame.pack();
		dialogFrame.setLocationRelativeTo(parentComponent);
		dialogFrame.setIconImage(Environment.getDefaultIconImage());
		return dialogFrame;
	}

	public static final int showFramedOpenDialog(JFileChooser chooser, Component parentComponent)
	{
		JFrame dialogFrame = createDialogFrame(parentComponent, chooser.getDialogTitle());
		int choice = chooser.showOpenDialog(dialogFrame);
		dialogFrame.dispose();
		return choice;
	}

	public static final int showFramedSaveDialog(JFileChooser chooser, Component parentComponent)
	{
		JFrame dialogFrame = createDialogFrame(parentComponent, chooser.getDialogTitle());
		int choice = chooser.showSaveDialog(dialogFrame);
		dialogFrame.dispose();
		return choice;
	}

	public static final void showFramedMessageDialog(
		Component parentComponent,
		Object message,
		String title,
		int messageType,
		Icon icon)
	{
		JFrame dialogFrame = createDialogFrame(parentComponent, title);
		JOptionPane.showMessageDialog(dialogFrame, message, title, messageType, icon);
		dialogFrame.dispose();
	}

	public static final void showFramedMessageDialog(
		Component parentComponent,
		Object message,
		String title,
		int messageType)
	{
		JFrame dialogFrame = createDialogFrame(parentComponent, title);
		JOptionPane.showMessageDialog(dialogFrame, message, title, messageType);
		dialogFrame.dispose();
	}

	public static final void showFramedErrorMessage(
		Component parentComponent,
		Object message,
		String title)
	{
		showFramedMessageDialog(parentComponent, message,
			title, JOptionPane.ERROR_MESSAGE, Environment.ICON_ERROR);
	}

	/*
	public static final void showFramedMessageDialog(
			Component parentComponent,
			Object message,
			String title)
	{
		JFrame dialogFrame = createDiaglogFrame(parentComponent, title);
		JOptionPane.showMessageDialog(dialogFrame, message);
		dialogFrame.dispose();
	}
	 */

	public static final int showFramedOptionDialog(
		Component parentComponent,
		Object message,
		String title,
		int optionType,
		int messageType,
		Object[] options,
		Object initialValue)
	{
		JFrame dialogFrame = createDialogFrame(parentComponent, title);
		int choice = JOptionPane.showOptionDialog(
			dialogFrame, message, title,
			optionType, messageType, null,
			options, initialValue);
		dialogFrame.dispose();
		return choice;
	}

	public static final int showFramedOptionDialog(
		Component parentComponent,
		Object message,
		String title,
		int optionType,
		int messageType,
		Icon icon,
		Object[] options,
		Object initialValue)
	{
		JFrame dialogFrame = createDialogFrame(parentComponent, title);
		int choice = JOptionPane.showOptionDialog(
			dialogFrame, message, title,
			optionType, messageType, icon,
			options, initialValue);
		dialogFrame.dispose();
		return choice;
	}

	public static final int showFramedConfirmDialog(
		Component parentComponent,
		Object message,
		String title,
		int optionType,
		int messageType)
	{
		JFrame dialogFrame = createDialogFrame(parentComponent, title);
		int choice = JOptionPane.showConfirmDialog(
			dialogFrame, message, title,
			optionType, messageType);
		dialogFrame.dispose();
		return choice;
	}

	public static final int showFramedConfirmDialog(
		Component parentComponent,
		Object message,
		String title,
		int optionType)
	{
		JFrame dialogFrame = createDialogFrame(parentComponent, title);
		int choice = JOptionPane.showConfirmDialog(
			dialogFrame, message, title, optionType);
		dialogFrame.dispose();
		return choice;
	}

	public static final String showFramedInputDialog(
		Component parentComponent,
		Object message,
		String title,
		int messageType)
	{
		JFrame dialogFrame = createDialogFrame(parentComponent, title);
		String reply = JOptionPane.showInputDialog(dialogFrame, message, title, messageType);
		dialogFrame.dispose();
		return reply;
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
	{ return UIManager.getColor("Label.foreground"); }

	private static Color getBackgoundColor()
	{ return UIManager.getColor("Label.background"); }

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
