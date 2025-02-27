package game.sprite;

import game.sprite.SpriteLoader.Indexable;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.ComponentAnimator;
import util.IterableListModel;

public class SpriteAnimation implements Indexable<SpriteAnimation>
{
	public final Sprite parentSprite;

	public final IterableListModel<SpriteComponent> components = new IterableListModel<>();

	// editor fields
	public transient String name = "";
	protected transient int listIndex;

	public transient int animTime;

	public transient boolean deleted;
	public transient boolean hasError;

	public transient int lastSelectedComp = -1;

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

		for (int i = 0; i < components.size(); i++) {
			SpriteComponent comp = components.elementAt(i);
			if (comp.parentID == -1) {
				comp.parent = null;
			}
			else {
				comp.parent = components.getElementAt(comp.parentID);
			}
		}
	}

	public SpriteAnimation copy()
	{
		return new SpriteAnimation(this);
	}

	public void prepareForEditor()
	{
		for (SpriteComponent comp : components)
			comp.prepareForEditor();

		if (components.size() > 0)
			lastSelectedComp = 0;
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

	public String createUniqueName(String name)
	{
		String baseName = name;

		for (int iteration = 0; iteration < 256; iteration++) {
			boolean conflict = false;

			// compare to all other names
			for (SpriteAnimation other : parentSprite.animations) {
				if (other != this && other.name.equals(name)) {
					conflict = true;
					break;
				}
			}

			if (!conflict) {
				// name is valid
				return name;
			}
			else {
				// try next iteration
				name = baseName + "_" + iteration;
				iteration++;
			}
		}

		// could not form a valid name
		return null;
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

	@Deprecated
	public void cleanDeletedRasters()
	{
		for (int i = 0; i < components.getSize(); i++) {
			SpriteComponent comp = components.get(i);
			comp.animator.cleanDeletedRasters();
		}
	}

	@Deprecated
	public void cleanDeletedPalettes()
	{
		for (int i = 0; i < components.getSize(); i++) {
			SpriteComponent comp = components.get(i);
			comp.animator.cleanDeletedPalettes();
		}
	}
}
