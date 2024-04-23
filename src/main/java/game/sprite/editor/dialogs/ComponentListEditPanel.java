package game.sprite.editor.dialogs;

import javax.swing.DefaultListModel;
import javax.swing.JButton;

import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;

public class ComponentListEditPanel extends ListEditPanel<SpriteComponent>
{
	public ComponentListEditPanel(SpriteAnimation anim, DefaultListModel<SpriteComponent> listModel)
	{
		super(listModel);

		JButton addButton = new JButton("New Component");
		addButton.addActionListener((e) -> {
			SpriteComponent newComp = new SpriteComponent(anim);
			newComp.name = "New Comp";
			anim.components.addElement(newComp);
		});

		JButton dupeButton = new JButton("Duplicate Selected");
		dupeButton.addActionListener((e) -> {
			SpriteComponent selected = list.getSelectedValue();
			if (selected == null)
				return;

			SpriteComponent newComp = new SpriteComponent(anim, selected);
			newComp.name = selected + " (copy)";
			listModel.addElement(newComp);
		});

		add(addButton, "sg but, growx, split 2");
		add(dupeButton, "sg but, growx");
	}

	@Override
	public boolean canDelete(SpriteComponent comp)
	{
		// animations must have at least one component
		return list.getModel().getSize() > 1;
	}

	@Override
	public void rename(int index, String newName)
	{
		SpriteComponent comp = listModel.get(index);
		comp.name = newName;
	}
}
