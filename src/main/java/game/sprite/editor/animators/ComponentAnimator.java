package game.sprite.editor.animators;

import java.awt.Container;
import java.util.HashMap;

import game.sprite.RawAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.SpriteEditor;
import util.xml.XmlWrapper.XmlSerializable;

public interface ComponentAnimator extends XmlSerializable
{
	public boolean generateFrom(RawAnimation rawAnim);

	public RawAnimation getCommandList();

	public void reset();

	public void step();

	public boolean surpassed(AnimElement elem);

	public void calculateTiming();

	public void bind(SpriteEditor editor, Container commandListContainer, Container commandEditContainer);

	public void unbind();

	/**
	 * Call while deserializing a sprite to replace name/id references with object references
	 * @param imgMap
	 * @param palMap
	 * @param compMap
	 */
	void updateReferences(
		HashMap<String, SpriteRaster> imgMap,
		HashMap<String, SpritePalette> palMap,
		HashMap<String, SpriteComponent> compMap);
}
