package game.map.impex;

import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import app.Environment;
import app.SwingUtils;
import game.map.editor.ui.dialogs.SaveFileChooser;
import net.miginfocom.swing.MigLayout;

public class ExportDialog extends JDialog
{
	public static final String FRAME_TITLE = "Export";

	private JComboBox<ImpexFormat> fileTypeComboBox;
	private JCheckBox cbSelectedOnly;

	public ExportDialog(JFrame parent)
	{
		super(parent);

		JButton selectButton = new JButton("Export");
		SwingUtils.addBorderPadding(selectButton);
		selectButton.addActionListener((e) -> {

			File mapDir = Environment.getProjectDirectory();
			SaveFileChooser chooser = new SaveFileChooser(mapDir, "Export Geometry",
				"Exportables", "obj", "fbx", "prefab");

			//TODO select file
			setVisible(false);
		});

		JButton cancelButton = new JButton("Cancel");
		SwingUtils.addBorderPadding(cancelButton);
		cancelButton.addActionListener((e) -> {
			setVisible(false);
		});

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				setVisible(false);
			}
		});

		fileTypeComboBox = new JComboBox<>(ImpexFormat.values());
		SwingUtils.addBorderPadding(fileTypeComboBox);

		cbSelectedOnly = new JCheckBox(" Selected object only");
		cbSelectedOnly.setSelected(true);

		setLayout(new MigLayout("ins 16, fill, hidemode 3, wrap 2"));

		add(new JLabel("File Format"));
		add(fileTypeComboBox, "growx");
		add(cbSelectedOnly, "span, growx, gapbottom 8");

		add(new JPanel(), "span, split 3, growx, sg but");
		add(cancelButton, "growx, sg but");
		add(selectButton, "growx, sg but");

		pack();
		setResizable(false);

		setTitle(FRAME_TITLE);
		setIconImage(Environment.getDefaultIconImage());
		setLocationRelativeTo(parent);
		setModal(false);
	}
}
