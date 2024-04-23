#version 330 core

in vec4 f_color;
in vec2 f_texCoords;

out vec4 o_color;

uniform sampler2D mainImage;
uniform sampler1D mainPalette;

uniform bool useFiltering;
uniform bool enableDropShadow;

uniform int noiseMode;
uniform float noiseAlpha;
uniform sampler2D noiseImage;
uniform vec2 noiseOffset = vec2(0,0);

uniform float fadeAlpha;

// retrieves a texel from the tile, using different methods depending on its format
vec4 getTexel(in sampler2D img, in sampler1D pal, in vec2 texCoord)
{
	// assume 256-color palette
	vec4 index = texture(img, texCoord);
	return texture(pal, index.x);
}

// n64 3-point filtering
// Original author: ArthurCarvalho
// GLSL implementation: twinaphex, mupen64plus-libretro project.

#define TEX_OFFSET(off) getTexel(img, pal, texCoord - (off)/texSize)

vec4 filter3point(in sampler2D img, in sampler1D pal, in vec2 texCoord)
{
	vec2 texSize = vec2(textureSize(img,0));
	vec2 offset = fract(texCoord*texSize - vec2(0.5));
	offset -= step(1.0, offset.x + offset.y);
	vec4 c0 = TEX_OFFSET(offset);
	vec4 c1 = TEX_OFFSET(vec2(offset.x - sign(offset.x), offset.y));
	vec4 c2 = TEX_OFFSET(vec2(offset.x, offset.y - sign(offset.y)));
	return c0 + abs(offset.x)*(c1-c0) + abs(offset.y)*(c2-c0);
}

vec4 sampleTexture(in sampler2D img, in sampler1D pal, in vec2 texCoord)
{
	if(useFiltering)
		return filter3point(img, pal, texCoord);
	else
		return getTexel(img, pal, texCoord);
}

void main()
{
//	o_color = sampleTexture(mainImage, mainPalette, f_texCoords);
	o_color = getTexel(mainImage, mainPalette, f_texCoords);

	if(enableDropShadow)
		o_color = o_color.a * vec4(0.1569f, 0.1569f, 0.1569f, 0.2824f); // 28282848

	if(noiseMode != 0)
	{
		vec4 noise = texture(noiseImage, (f_texCoords + noiseOffset) / 16);

		switch(noiseMode)
		{
		case 2: // standard noise
			noise.rgb = (noise.rgb / 2.0) + 0.5;
			o_color.rgb *= noise.r;
			break;
		case 3: // blended noise
			noise.rgb = (noise.rgb / 2.0) + 0.5;
			o_color.rgb = mix(o_color.rgb, noise.rgb, noiseAlpha); // blended noise
			break;
		case 7: // dithered fade
			if(noise.r > noiseAlpha)
				discard;
			break;
		}
	}

//	o_color = vec4(1, 0, 0, 1);

	o_color.a *= fadeAlpha;
}
