package game.sprite;

import javax.swing.DefaultListModel;

import game.sprite.SpriteLoader.Indexable;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.ComponentAnimator;

public class SpriteAnimation implements Indexable<SpriteAnimation>
{
	public final Sprite parentSprite;

	public final DefaultListModel<SpriteComponent> components = new DefaultListModel<>();

	// editor fields
	public transient String name = "";
	protected transient int listIndex;

	public transient int lastSelectedComp;

	public transient int animTime;

	public SpriteAnimation(Sprite parentSprite)
	{
		this.parentSprite = parentSprite;
	}

	// deep copy constructor
	public SpriteAnimation(SpriteAnimation original)
	{
		this.parentSprite = original.parentSprite;
		this.name = original.name;
		for (int i = 0; i < original.components.size(); i++) {
			SpriteComponent comp = original.components.elementAt(i);
			components.addElement(new SpriteComponent(this, comp));
		}
	}

	public int getComponentCount()
	{
		return components.size();
	}

	@Override
	public String toString()
	{
		return name.isEmpty() ? String.format("Anim %02X", listIndex) : name;
	}

	@Override
	public SpriteAnimation getObject()
	{
		return this;
	}

	@Override
	public int getIndex()
	{
		return listIndex;
	}

	public void step()
	{
		for (int i = 0; i < components.size(); i++) {
			SpriteComponent comp = components.elementAt(i);
			comp.step();
		}

		animTime += 2;
	}

	public void reset()
	{
		for (int i = 0; i < components.size(); i++) {
			SpriteComponent comp = components.elementAt(i);
			comp.reset();
		}

		animTime = 0;
	}

	public boolean end()
	{
		for (int i = 0; i < components.size(); i++) {
			SpriteComponent comp = components.elementAt(i);
			comp.reset();
		}

		boolean notDone = true;
		int iterations = 0;
		while (notDone && iterations < 1000) {
			notDone = false;
			for (int i = 0; i < components.size(); i++) {
				SpriteComponent comp = components.elementAt(i);

				if (!comp.complete) {
					comp.step();
					notDone = true;
				}
			}
			iterations++;
		}

		animTime = 2 * iterations;
		return !notDone;
	}

	public void advanceTo(ComponentAnimator animator, AnimElement elem)
	{
		boolean foundAnimator = false;
		for (int i = 0; i < components.size(); i++) {
			SpriteComponent comp = components.elementAt(i);
			foundAnimator |= (comp.animator == animator);
			comp.reset();
		}

		if (!foundAnimator)
			throw new IllegalStateException();

		int cmdCounter = 0;
		int maxCommands = 1024;
		boolean done = false;
		while (!done && cmdCounter++ < maxCommands) {
			for (int i = 0; i < components.getSize(); i++) {
				SpriteComponent comp = components.get(i);
				comp.step();

				if (comp.animator == animator)
					done |= animator.surpassed(elem);
			}
		}
	}

	public void setComponentHighlight(int highlightComp)
	{
		for (int i = 0; i < components.getSize(); i++) {
			SpriteComponent comp = components.get(i);
			comp.highlighted = (i == highlightComp);
		}
	}

	public void setComponentSelected(int selectedComp)
	{
		for (int i = 0; i < components.getSize(); i++) {
			SpriteComponent comp = components.get(i);
			comp.selected = (i == selectedComp);
		}
	}

	public void cleanDeletedRasters()
	{
		for (int i = 0; i < components.getSize(); i++) {
			SpriteComponent comp = components.get(i);
			comp.animator.cleanDeletedRasters();
		}
	}

	public void cleanDeletedPalettes()
	{
		for (int i = 0; i < components.getSize(); i++) {
			SpriteComponent comp = components.get(i);
			comp.animator.cleanDeletedPalettes();
		}
	}
}
