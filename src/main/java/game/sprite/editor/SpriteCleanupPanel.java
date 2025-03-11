package game.sprite.editor;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

import game.sprite.ImgAsset;
import game.sprite.PalAsset;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.SpriteCleanup.Unused;
import game.sprite.editor.SpriteCleanup.UnusedAsset;
import game.sprite.editor.SpriteCleanup.UnusedLabel;
import net.miginfocom.swing.MigLayout;
import util.ui.ThemedIcon;

public class SpriteCleanupPanel extends JPanel
{
	private final SpriteEditor editor;
	private final SpriteCleanup cleanup;

	public SpriteCleanupPanel(SpriteEditor editor, Sprite sprite)
	{
		this.editor = editor;
		cleanup = new SpriteCleanup(sprite);

		String btnText = "View List";

		JCheckBox cbUnusedLabels = new JCheckBox("Delete unused labels (" + cleanup.unusedLabels.size() + " found)");
		cbUnusedLabels.setIconTextGap(12);
		cbUnusedLabels.setSelected(false);
		cbUnusedLabels.addActionListener((e) -> {
			cleanup.removeUnusedLabels = cbUnusedLabels.isSelected();
		});
		cbUnusedLabels.setToolTipText("<html>Removes unused labels when using commands.<br>"
			+ "This should always be safe to use.</html>");

		JButton btnShowLabels = new JButton(btnText);
		btnShowLabels.setEnabled(cleanup.unusedLabels.size() > 0);
		btnShowLabels.addActionListener((e) -> {
			JList<UnusedLabel> list = new JList<>(cleanup.unusedLabels);
			list.setCellRenderer(new UnusedLabelCellRenderer());
			showListDialog("Unused Labels", list);
		});

		JLabel warnUnusedRasters = new JLabel(ThemedIcon.WARNING_16);
		warnUnusedRasters.setVisible(false);

		JCheckBox cbUnusedRasters = new JCheckBox("Delete unused rasters (" + cleanup.unusedRasters.size() + " found)");
		cbUnusedRasters.setIconTextGap(12);
		cbUnusedRasters.setSelected(false);
		cbUnusedRasters.addActionListener((e) -> {
			cleanup.removeUnusedRasters = cbUnusedRasters.isSelected();
			warnUnusedRasters.setVisible(cbUnusedRasters.isSelected());
		});
		cbUnusedRasters.setToolTipText("<html>Removes unused rasters.<br>"
			+ "Be careful with this option, as some rasters may be<br>"
			+ "loaded directly in the engine.</html>");

		JButton btnShowRasters = new JButton(btnText);
		btnShowRasters.setEnabled(cleanup.unusedRasters.size() > 0);
		btnShowRasters.addActionListener((e) -> {
			JList<Unused<SpriteRaster>> list = new JList<>(cleanup.unusedRasters);
			list.setCellRenderer(new UnusedCellRenderer<SpriteRaster>());
			showListDialog("Unused Rasters", list);
		});

		JLabel warnUnusedPalettes = new JLabel(ThemedIcon.WARNING_16);
		warnUnusedPalettes.setVisible(false);

		JCheckBox cbUnusedPalettes = new JCheckBox("Delete unused palettes (" + cleanup.unusedPalettes.size() + " found)");
		cbUnusedPalettes.setIconTextGap(12);
		cbUnusedPalettes.setSelected(false);
		cbUnusedPalettes.addActionListener((e) -> {
			cleanup.removeUnusedPalettes = cbUnusedPalettes.isSelected();
			warnUnusedPalettes.setVisible(cbUnusedPalettes.isSelected());
		});
		cbUnusedPalettes.setToolTipText("<html>Removes unused palettes.<br>"
			+ "This is generally a bad idea since a fixed number are<br>"
			+ "expected based on the number of variations.<br>"
			+ "Consider reviewing the list and cleaning them up manually.</html>");

		JButton btnShowPalettes = new JButton(btnText);
		btnShowPalettes.setEnabled(cleanup.unusedPalettes.size() > 0);
		btnShowPalettes.addActionListener((e) -> {
			JList<Unused<SpritePalette>> list = new JList<>(cleanup.unusedPalettes);
			list.setCellRenderer(new UnusedCellRenderer<SpritePalette>());
			showListDialog("Unused Palettes", list);
		});

		JCheckBox cbUnusedImgAssets = new JCheckBox("Delete unused image assets (" + cleanup.unusedImgAssets.size() + " found)");
		cbUnusedImgAssets.setIconTextGap(12);
		cbUnusedImgAssets.setSelected(false);
		cbUnusedImgAssets.addActionListener((e) -> {
			cleanup.removeUnusedImgAssets = cbUnusedImgAssets.isSelected();
		});
		cbUnusedImgAssets.setToolTipText("<html>Removes and deletes from disk unused image assets.<br>"
			+ "This will not delete assets used by unused rasters.</html>");
		cbUnusedImgAssets.setEnabled(!sprite.metadata.isPlayer);

		JButton btnShowImgAssets = new JButton(btnText);
		btnShowImgAssets.setEnabled(cleanup.unusedImgAssets.size() > 0);
		btnShowImgAssets.addActionListener((e) -> {
			JList<UnusedAsset<ImgAsset>> list = new JList<>(cleanup.unusedImgAssets);
			list.setCellRenderer(new UnusedAssetCellRenderer<ImgAsset>());
			showListDialog("Unused Image Assets", list);
		});

		JCheckBox cbUnusedPalAssets = new JCheckBox("Delete unused palette assets (" + cleanup.unusedPalAssets.size() + " found)");
		cbUnusedPalAssets.setIconTextGap(12);
		cbUnusedPalAssets.setSelected(false);
		cbUnusedPalAssets.addActionListener((e) -> {
			cleanup.removeUnusedPalAssets = cbUnusedPalAssets.isSelected();
		});
		cbUnusedPalAssets.setToolTipText("<html>Removes and deletes from disk unused palette assets.<br>"
			+ "This will not delete assets used by unused palettes.</html>");
		cbUnusedPalAssets.setEnabled(!sprite.metadata.isPlayer);

		JButton btnShowPalAssets = new JButton(btnText);
		btnShowPalAssets.setEnabled(cleanup.unusedPalAssets.size() > 0);
		btnShowPalAssets.addActionListener((e) -> {
			JList<UnusedAsset<PalAsset>> list = new JList<>(cleanup.unusedPalAssets);
			list.setCellRenderer(new UnusedAssetCellRenderer<PalAsset>());
			showListDialog("Unused Palette Assets", list);
		});

		setLayout(new MigLayout("fill, wrap 3", "[][]32[]"));

		add(new JLabel());
		add(cbUnusedLabels, "growx");
		add(btnShowLabels, "growx");

		add(warnUnusedRasters);
		add(cbUnusedRasters, "growx");
		add(btnShowRasters, "growx");

		add(warnUnusedPalettes);
		add(cbUnusedPalettes, "growx");
		add(btnShowPalettes, "growx");

		add(new JLabel());
		add(cbUnusedImgAssets, "growx");
		add(btnShowImgAssets, "growx");

		add(new JLabel());
		add(cbUnusedPalAssets, "growx");
		add(btnShowPalAssets, "growx");
	}

	public int getActionsCount()
	{
		return cleanup.getActionsCount();
	}

	public void doCleanup()
	{
		editor.runThreadsafe(cleanup::execute);
	}

	private void showListDialog(String title, JList<?> list)
	{
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JPanel listPanel = new JPanel(new MigLayout("ins 0, fill"));
		listPanel.add(new JScrollPane(list,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), "grow");

		editor.getMessageDialog(title, listPanel)
			.setMessageType(JOptionPane.PLAIN_MESSAGE)
			.show();
	}

	private static class UnusedLabelCellRenderer extends JPanel implements ListCellRenderer<UnusedLabel>
	{
		private JLabel animLabel;
		private JLabel compLabel;
		private JLabel nameLabel;

		public UnusedLabelCellRenderer()
		{
			animLabel = new JLabel("", SwingConstants.CENTER);
			compLabel = new JLabel("", SwingConstants.CENTER);
			nameLabel = new JLabel("", SwingConstants.CENTER);

			setLayout(new MigLayout("ins 0, fillx", "[grow, sg col][grow, sg col][grow, sg col]"));
			add(animLabel, "growx");
			add(compLabel, "growx");
			add(nameLabel, "growx");
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends UnusedLabel> list,
			UnusedLabel unused,
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

			setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			if (unused != null) {
				SpriteComponent comp = unused.label.owner;
				SpriteAnimation anim = comp.parentAnimation;

				animLabel.setText(anim.name);
				compLabel.setText(comp.name);
				nameLabel.setText(unused.label.name);
			}
			else {
				animLabel.setText("xxx");
				compLabel.setText("xxx");
				nameLabel.setText("NULL");
			}

			return this;
		}
	}

	private static class UnusedCellRenderer<T> extends JPanel implements ListCellRenderer<Unused<T>>
	{
		private JLabel nameLabel;

		public UnusedCellRenderer()
		{
			nameLabel = new JLabel();

			setLayout(new MigLayout("ins 0, fillx"));
			add(nameLabel, "gapleft 8, growx");
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends Unused<T>> list,
			Unused<T> unused,
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

			setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			if (unused != null)
				nameLabel.setText(unused.item.toString());
			else
				nameLabel.setText("NULL");

			return this;
		}
	}

	private static class UnusedAssetCellRenderer<T> extends JPanel implements ListCellRenderer<UnusedAsset<T>>
	{
		private JLabel nameLabel;

		public UnusedAssetCellRenderer()
		{
			nameLabel = new JLabel();

			setLayout(new MigLayout("ins 0, fillx"));
			add(nameLabel, "gapleft 8, growx");
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends UnusedAsset<T>> list,
			UnusedAsset<T> unused,
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

			setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			if (unused != null)
				nameLabel.setText(unused.item.toString());
			else
				nameLabel.setText("NULL");

			return this;
		}
	}
}
