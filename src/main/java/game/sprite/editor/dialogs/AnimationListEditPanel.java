package game.sprite.editor.dialogs;

import javax.swing.DefaultListModel;
import javax.swing.JButton;

import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.editor.SpriteEditor;

public class AnimationListEditPanel extends ListEditPanel<SpriteAnimation>
{
	private final SpriteEditor editor;

	public AnimationListEditPanel(Sprite sprite, DefaultListModel<SpriteAnimation> listModel, SpriteEditor editor)
	{
		super(listModel);

		this.editor = editor;

		JButton addButton = new JButton("New Animation");
		addButton.addActionListener((e) -> {
			SpriteAnimation newAnim = new SpriteAnimation(sprite);
			SpriteComponent newComp = new SpriteComponent(newAnim);
			newAnim.name = "New Anim";
			newAnim.components.addElement(newComp);
			listModel.addElement(newAnim);
		});

		JButton dupeButton = new JButton("Duplicate Selected");
		dupeButton.addActionListener((e) -> {
			SpriteAnimation original = list.getSelectedValue();
			if (original == null)
				return;

			SpriteAnimation newAnim = new SpriteAnimation(original);
			newAnim.name = original + " (copy)";
			listModel.addElement(newAnim);
		});

		add(addButton, "sg but, growx, split 2");
		add(dupeButton, "sg but, growx");
	}

	@Override
	protected boolean canDelete(SpriteAnimation anim)
	{
		// SpriteEditor crashes when its current animation is deleted
		return editor.getAnimation() != anim;
	}

	@Override
	public void rename(int index, String newName)
	{
		SpriteAnimation anim = listModel.get(index);
		anim.name = newName;
	}
}
