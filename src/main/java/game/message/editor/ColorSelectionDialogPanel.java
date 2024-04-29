package game.message.editor;

import java.awt.Component;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;

import app.Environment;
import app.StarRodFrame;
import app.SwingUtils;
import net.miginfocom.swing.MigLayout;

public class ColorSelectionDialogPanel extends JDialog
{
	private static final String TITLE = "Choose Text Color";
	private int result = -1;

	private static ImageIcon[] previews;

	public static void setButtonIcons(ImageIcon[] images)
	{
		previews = images;
	}

	private ColorSelectionDialogPanel(StarRodFrame parent)
	{
		super(parent, true);

		setLayout(new MigLayout("fill"));

		for (int i = 0; i < previews.length; i++) {
			String layout = (((i + 1) & 7) == 0) ? "sg but, grow, wrap" : "sg but, grow";
			add(makeButton(previews[i], i), layout);
		}

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener((e) -> {
			result = -1;
			setVisible(false);
		});
		SwingUtils.setFontSize(cancelButton, 14);

		add(cancelButton, "span, center, w 50%, gaptop 12, gapbottom 4");
	}

	private JButton makeButton(ImageIcon icon, int value)
	{
		JButton button = new JButton();
		if (icon != null)
			button.setIcon(icon);
		else
			button.setText(String.format("%02X", value));
		button.addActionListener((e) -> {
			result = value;
			setVisible(false);
		});
		return button;
	}

	public static int showFramedDialog(Component parentComponent)
	{
		StarRodFrame dialogFrame = createDialogFrame(parentComponent, TITLE);
		ColorSelectionDialogPanel panel = new ColorSelectionDialogPanel(dialogFrame);
		dialogFrame.setResizable(false);
		dialogFrame.pack();

		panel.setTitle(TITLE);
		panel.setIconImage(Environment.getDefaultIconImage());
		panel.pack();

		panel.result = -1;

		panel.setLocationRelativeTo(parentComponent);
		panel.setVisible(true);

		dialogFrame.dispose();
		return panel.result;
	}

	private static final StarRodFrame createDialogFrame(Component parentComponent, String title)
	{
		StarRodFrame dialogFrame = new StarRodFrame(title);
		dialogFrame.setUndecorated(true);
		dialogFrame.setVisible(true);
		dialogFrame.setLocationRelativeTo(parentComponent);
		return dialogFrame;
	}
}
