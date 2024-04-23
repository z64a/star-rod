package game.map.scripts;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;

import app.SwingUtils;
import game.map.Map;
import game.map.editor.MapEditor;
import game.map.editor.commands.ApplyPannerToList;
import game.map.editor.selection.SelectionManager;
import game.map.editor.ui.SwingGUI;
import game.map.shape.Model;
import game.map.shape.TexturePanner;
import net.miginfocom.swing.MigLayout;

public class PannerListPanel extends JPanel
{
	private JList<TexturePanner> pannerList;

	public void setMap(Map m)
	{
		pannerList.setModel(m.scripts.texPanners);
		pannerList.setSelectedIndex(0);
	}

	public void updateFields()
	{
		pannerList.repaint();
	}

	public PannerListPanel()
	{
		pannerList = new JList<>();
		pannerList.setCellRenderer(new PannerCellRenderer());

		pannerList.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));

		pannerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		pannerList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2) {
					int index = pannerList.locationToIndex(e.getPoint());

					Rectangle cellBounds = pannerList.getCellBounds(index, index);
					if (!cellBounds.contains(e.getPoint()))
						return;

					TexturePanner panner = pannerList.getModel().getElementAt(index);
					if (panner != null)
						SwingGUI.instance().prompt_EditTexPanner(panner);
				}
			}
		});

		JButton editButton = new JButton("Edit");
		editButton.addActionListener((e) -> {
			TexturePanner panner = pannerList.getSelectedValue();
			if (panner != null)
				SwingGUI.instance().prompt_EditTexPanner(panner);
		});
		SwingUtils.addBorderPadding(editButton);

		JButton selectButton = new JButton("Select All");
		selectButton.addActionListener((e) -> {
			TexturePanner panner = pannerList.getSelectedValue();
			if (panner == null)
				return;

			SelectionManager selectionManager = MapEditor.instance().selectionManager;

			List<Model> mdlList = new LinkedList<>();
			for (Model mdl : MapEditor.instance().map.modelTree) {
				if (mdl.pannerID.get() == panner.panID)
					mdlList.add(mdl);
			}
			if (!mdlList.isEmpty()) {
				MapEditor.execute(selectionManager.getSetObjects(mdlList));
			}
		});
		SwingUtils.addBorderPadding(selectButton);

		JButton applyButton = new JButton("Apply");
		applyButton.addActionListener((e) -> {
			TexturePanner panner = pannerList.getSelectedValue();
			if (panner == null)
				return;

			SelectionManager selectionManager = MapEditor.instance().selectionManager;
			List<Model> mdlList = selectionManager.getSelectedObjects(Model.class);
			if (mdlList.size() > 0)
				MapEditor.execute(new ApplyPannerToList(mdlList, panner.panID));
		});
		SwingUtils.addBorderPadding(applyButton);

		setLayout(new MigLayout("fill, wrap, hidemode 3"));

		add(new JLabel("Panner", SwingConstants.CENTER), "split 3, w 60!, gapleft 10, gaptop 8");
		add(new JLabel("Main Tile UV", SwingConstants.CENTER), "growx, sg header");
		add(new JLabel("Aux Tile UV", SwingConstants.CENTER), "growx, sg header, gapright 10");
		add(pannerList, "growx");
		add(editButton, "split 3, growx, sg but");
		add(applyButton, "growx, sg but");
		add(selectButton, "growx, sg but");
		add(new JPanel(), "grow, pushy");
	}

	private static class PannerCellRenderer extends JPanel implements ListCellRenderer<TexturePanner>
	{
		private JLabel nameLabel;
		private JLabel mainLabel;
		private JLabel auxLabel;

		public PannerCellRenderer()
		{
			nameLabel = SwingUtils.getLabel("", SwingConstants.CENTER, 12);
			mainLabel = new JLabel("", SwingConstants.CENTER);
			auxLabel = new JLabel("", SwingConstants.CENTER);

			setLayout(new MigLayout("ins 0, fillx", "[60][grow, sg m][grow, sg m]"));
			add(nameLabel, "growx");
			add(mainLabel, "growx");
			add(auxLabel, "growx");
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends TexturePanner> list,
			TexturePanner panner,
			int index,
			boolean isSelected,
			boolean cellHasFocus)
		{
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

			if (panner.params.generate || panner.isNonzero())
				nameLabel.setText("<html><b>Unit " + panner.panID + "</b></html>");
			else
				nameLabel.setText("Unit " + panner.panID);

			panner.setLabels(mainLabel, auxLabel);
			return this;
		}
	}
}
