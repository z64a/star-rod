package game.map.editor.ui.info.marker;

import java.util.Collection;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import app.StarRodException;
import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.marker.NpcComponent;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.SpriteMetadata;
import game.sprite.SpriteLoader.SpriteSet;
import game.sprite.SpritePalette;
import game.sprite.editor.IndexableComboBoxRenderer;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

public class NpcSubpanel extends JPanel
{
	private final MarkerInfoPanel parent;

	private JPanel npcAnimPanel;

	private RangeCheckComboBox<SpriteMetadata> spriteBox;
	private RangeCheckComboBox<SpritePalette> paletteBox;
	private RangeCheckComboBox<SpriteAnimation> animBox;

	public NpcSubpanel(MarkerInfoPanel parent)
	{
		this.parent = parent;

		//		SpriteLoader.initialize(); // make sure the sprite files are ready

		spriteBox = new RangeCheckComboBox<>();
		spriteBox.setRenderer(new IndexableComboBoxRenderer());
		SwingUtils.addBorderPadding(spriteBox);
		spriteBox.setMaximumRowCount(24);

		Collection<SpriteMetadata> spriteNames = SpriteLoader.getValidSprites(SpriteSet.Npc);
		if (spriteNames.isEmpty())
			throw new StarRodException("No valid NPC sprites could be found!");

		for (SpriteMetadata sp : spriteNames)
			spriteBox.addItem(sp);

		spriteBox.addActionListener((e) -> {
			if (parent.ignoreEvents())
				return;
			SpriteMetadata spr = (SpriteMetadata) spriteBox.getSelectedItem();
			NpcComponent npc = parent.getData().npcComponent;
			MapEditor.execute(npc.spriteID.mutator(spr.id));
		});

		paletteBox = new RangeCheckComboBox<>();
		paletteBox.setRenderer(new IndexableComboBoxRenderer());
		SwingUtils.addBorderPadding(paletteBox);

		paletteBox.addActionListener((e) -> {
			if (parent.ignoreEvents())
				return;
			int palID = paletteBox.getSelectedIndex();
			NpcComponent npc = parent.getData().npcComponent;
			MapEditor.execute(npc.paletteID.mutator(palID));
		});

		animBox = new RangeCheckComboBox<>();
		animBox.setRenderer(new IndexableComboBoxRenderer());
		SwingUtils.addBorderPadding(animBox);

		animBox.addActionListener((e) -> {
			if (parent.ignoreEvents())
				return;
			int animID = animBox.getSelectedIndex();
			NpcComponent npc = parent.getData().npcComponent;
			MapEditor.execute(npc.animIndex.mutator(animID));
		});

		npcAnimPanel = new JPanel(new MigLayout("ins 0, wrap, fill"));

		npcAnimPanel.add(new JLabel("Palette"), "w 15%, split 2");
		npcAnimPanel.add(paletteBox, "growx");

		npcAnimPanel.add(new JLabel("Anim"), "w 15%, split 2");
		npcAnimPanel.add(animBox, "growx");

		setLayout(new MigLayout("ins 0, wrap, fill"));
		add(new JLabel("Sprite"), "w 15%, split 2");
		add(spriteBox, "growx, wrap");
		add(npcAnimPanel, "growx, span");
	}

	public void updateFields()
	{
		NpcComponent npc = parent.getData().npcComponent;

		Sprite previewSprite = npc.previewSprite;
		spriteBox.setSelectedIndex(npc.spriteID.get() - 1);

		if (previewSprite == null) {
			npcAnimPanel.setVisible(false);
			return;
		}
		else {
			npcAnimPanel.setVisible(true);
		}

		paletteBox.setModel(new ListAdapterComboboxModel<>(previewSprite.palettes));
		paletteBox.setSelectedIndex(npc.paletteID.get());

		((JComboBox<SpriteAnimation>) animBox).setModel(new ListAdapterComboboxModel<>(previewSprite.animations));
		animBox.setSelectedIndex(npc.animIndex.get());
	}

	private static class RangeCheckComboBox<T> extends JComboBox<T>
	{
		@Override
		public void setSelectedIndex(int index)
		{
			int maxValue = getModel().getSize() - 1;
			if (index > maxValue) {
				setForeground(SwingUtils.getRedTextColor());
				super.setSelectedIndex(maxValue);
			}
			else {
				setForeground(SwingUtils.getTextColor());
				super.setSelectedIndex(index);
			}
		}
	}
}
