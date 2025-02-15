package game.sprite.editor;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.sprite.SpriteRaster;
import net.miginfocom.swing.MigLayout;
import util.IterableListModel;

public class RasterSelectDialog extends JDialog
{
	private SpriteRaster selected = null;

	public RasterSelectDialog(IterableListModel<SpriteRaster> rasters)
	{
		super(null, java.awt.Dialog.ModalityType.TOOLKIT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		// choose column
		int columns = 5;
		if (rasters.size() > 25)
			columns = (int) Math.sqrt(rasters.size());
		if (columns > 9)
			columns = 9;

		JPanel gridPanel = new JPanel(new MigLayout("wrap " + columns + ", gap 10"));

		for (SpriteRaster raster : rasters) {
			JButton button = new JButton(raster.getFront().icon);
			button.setText(raster.name);
			button.setVerticalTextPosition(SwingConstants.BOTTOM);
			button.setHorizontalTextPosition(SwingConstants.CENTER);

			button.setContentAreaFilled(false);
			button.setBorder(BorderFactory.createEmptyBorder());
			button.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e)
				{
					selected = raster;
					dispose();
				}
			});

			button.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e)
				{
					button.setForeground(SwingUtils.getBlueTextColor());
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					button.setForeground(null);
				}
			});

			gridPanel.add(button);
		}

		/*
		JButton btnCancel = new JButton("Cancel");
		btnCancel.addActionListener(e -> {
			selected = null;
			dispose();
		});
		SwingUtils.addBorderPadding(btnCancel);
		*/

		JScrollPane scrollPane = new JScrollPane(gridPanel);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(20);

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int maxHeight = (int) (screenSize.height * 0.70);

		setLayout(new MigLayout("fill, insets 10, wrap"));

		add(scrollPane, "grow, push, hmax " + maxHeight);
		//	add(btnCancel, "w 20%, align right");
	}

	public SpriteRaster getSelected()
	{
		return selected;
	}
}
