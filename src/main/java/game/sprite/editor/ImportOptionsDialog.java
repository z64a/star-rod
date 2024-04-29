package game.sprite.editor;

import java.awt.Dimension;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SwingConstants;

import app.StarRodFrame;
import app.SwingUtils;
import net.miginfocom.swing.MigLayout;

public class ImportOptionsDialog extends JDialog
{
	public static final int CANCEL = -1;
	public static final int RASTER = 1;
	public static final int PALETTE = 2;

	private StarRodFrame dialogFrame;
	private int selected = CANCEL;

	public static int display(StarRodFrame parent)
	{
		StarRodFrame dialogFrame = new StarRodFrame("Import Options");
		dialogFrame.setUndecorated(true);
		dialogFrame.setVisible(true);

		ImportOptionsDialog dialog = new ImportOptionsDialog(dialogFrame);
		return dialog.selected;
	}

	private ImportOptionsDialog(StarRodFrame dialogFrame)
	{
		super(dialogFrame, "Import Options", true);
		this.dialogFrame = dialogFrame;

		JButton[] buttons = new JButton[3];
		buttons[0] = getButton(RASTER | PALETTE, "Raster and Palette");
		buttons[1] = getButton(RASTER, "Raster Only");
		buttons[2] = getButton(PALETTE, "Palette Only");

		JButton cancelButton = getButton(CANCEL, "Cancel");

		setMinimumSize(new Dimension(200, 100));
		setLocationRelativeTo(null);

		setLayout(new MigLayout("fill, wrap, inset 8"));
		add(SwingUtils.getLabel("What should be imported?", SwingConstants.CENTER, 12), "gaptop 4, gapbottom 8, growx");
		for (JButton button : buttons)
			add(button, "h 32, grow");
		add(cancelButton, "gaptop 8, h 28, w 50%, center");

		pack();
		setResizable(false);
		setVisible(true);
	}

	private JButton getButton(int value, String text)
	{
		JButton button = new JButton(text);
		button.addActionListener((e) -> {
			selected = value;
			dialogFrame.dispose();
		});
		SwingUtils.setFontSize(button, 12);
		return button;
	}
}
