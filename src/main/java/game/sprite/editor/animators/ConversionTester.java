package game.sprite.editor.animators;

import java.util.Collection;

import app.Environment;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.SpriteMetadata;
import game.sprite.SpriteLoader.SpriteSet;

public abstract class ConversionTester
{
	public static void main(String[] args)
	{
		Environment.initialize();

		SpriteLoader loader = new SpriteLoader();
		Collection<SpriteMetadata> spriteData;

		spriteData = SpriteLoader.getValidSprites(SpriteSet.Npc);
		for (SpriteMetadata mdata : spriteData) {
			Sprite spr = loader.getSprite(SpriteSet.Npc, mdata.id);
			validate(spr);
			//	Sprite.validate(spr);
		}

		spriteData = SpriteLoader.getValidSprites(SpriteSet.Player);
		for (SpriteMetadata mdata : spriteData) {
			Sprite spr = loader.getSprite(SpriteSet.Player, mdata.id);
			validate(spr);
			//	Sprite.validate(spr);
		}

		Environment.exit();
	}

	private static void validate(Sprite spr)
	{
		System.out.println("Checking " + spr.name);
		for (int i = 0; i < spr.animations.size(); i++) {
			SpriteAnimation anim = spr.animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);
				comp.validateGenerators();
			}
		}
	}
}
