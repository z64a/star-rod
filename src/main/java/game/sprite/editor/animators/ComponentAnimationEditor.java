package game.sprite.editor.animators;

public abstract class ComponentAnimationEditor
{
	public abstract AnimElement getSelected();

	public abstract void setSelected(AnimElement elem);

	public abstract void restoreSelection(int lastSelectedIndex);

	public abstract int getSelection();
}
