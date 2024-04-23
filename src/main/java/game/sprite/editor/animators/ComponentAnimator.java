package game.sprite.editor.animators;

import java.awt.Container;

import game.sprite.RawAnimation;
import game.sprite.editor.SpriteEditor;

public interface ComponentAnimator
{
	public boolean generate(RawAnimation rawAnim);

	public RawAnimation getCommandList();

	public void reset();

	public void step();

	public void bind(SpriteEditor editor, Container commandListContainer, Container commandEditContainer);

	public void cleanDeletedRasters();

	public void cleanDeletedPalettes();

	public boolean surpassed(AnimElement elem);
}
