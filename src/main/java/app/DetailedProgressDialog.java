package app;

import java.awt.Dimension;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;

import net.miginfocom.swing.MigLayout;

public class DetailedProgressDialog extends JFrame
{
	public static void main(String args[])
	{
		final JDialog dlg = new JDialog(new DetailedProgressDialog(), "Progress Dialog", true);

		dlg.setLocationRelativeTo(null);
		dlg.setMinimumSize(new Dimension(480, 32));
		dlg.setVisible(true);
	}

	private final JProgressBar progressBar;
	private final JLabel progressLabel;

	public DetailedProgressDialog()
	{
		setMinimumSize(new Dimension(480, 32));
		setLocationRelativeTo(null);

		setLayout(new MigLayout("fillx, ins 16 16 n 16"));
		add(new JLabel("Test"));

		progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		progressLabel = new JLabel("more to do...");

		setLayout(new MigLayout("fillx"));
		add(progressLabel, "center, wrap");
		add(progressBar, "grow");
		//	setVisible(false);
	}

}
