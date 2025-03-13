package game.sprite;

import java.util.HashMap;

public class SpriteRefLookup
{
	public HashMap<String, SpriteRaster> imgMap;
	public HashMap<String, SpritePalette> palMap;
	public HashMap<String, SpriteAnimation> animMap;

	public SpriteRefLookup(Sprite spr)
	{
		imgMap = new HashMap<>();
		palMap = new HashMap<>();
		animMap = new HashMap<>();

		for (SpriteRaster img : spr.rasters) {
			imgMap.put(img.name, img);
		}

		for (SpritePalette pal : spr.palettes) {
			palMap.put(pal.name, pal);
		}

		for (SpriteAnimation anim : spr.animations) {
			animMap.put(anim.name, anim);
		}
	}
}
