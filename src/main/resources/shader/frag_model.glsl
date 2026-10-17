#version 330 core

in vec4 f_color;
in vec2 f_texCoords;
in vec2 f_aux;

in vec4 f_worldPosition;
in vec4 f_screenPosition;

out vec4 o_color;

layout (std140) uniform Globals {
	mat4 g_projectionMatrix;
	mat4 g_viewMatrix;
	mat4 g_modelMatrix;
	ivec4 g_viewport;
	float g_time;
};

const int MODE_FILL_SOLID = 0;
const int MODE_FILL_OUTLINE = 1;
const int MODE_FILL_OUTLINE_HIGHLIGHT = 2;
const int MODE_LINE_SOLID = 4;
const int MODE_LINE_OUTLINE = 5;

uniform int drawMode;

uniform bool textured = false;

uniform int mainFormat = 0;
uniform sampler2D mainImage;
uniform sampler1D mainPalette;

uniform int auxFormat = 0;
uniform sampler2D auxImage;
uniform sampler1D auxPalette;

uniform int auxCombineMode = 0;
uniform vec2 auxOffset = vec2(0,0);
uniform vec2 auxScale = vec2(1,1);

// scrolling preview
uniform vec2 mainScroll = vec2(0,0);
uniform vec2 auxScroll = vec2(0,0);

uniform bool translucent = false;
uniform bool useFiltering = false;

uniform bool useFog = false;
uniform vec2 fogDist = vec2(950, 1000);
uniform vec4 fogColor = vec4(0.04f, 0.04f, 0.04f, 1.0f);

uniform bool useLOD = false;
uniform int maxLOD = 0;

// retrieves a texel from the tile, using different methods depending on its format
vec4 getTexel(in sampler2D img, in sampler1D pal, in int fmt, in vec2 texCoord, int lodLevel)
{
	vec4 texel;

	if(!useLOD)
		lodLevel = 0;

	switch(fmt)
	{
	case 4: // I
	{
		vec4 sample = textureLod(img, texCoord, float(lodLevel));
		if(translucent)
		{
			texel.r = 1.0;
			texel.g = 1.0;
			texel.b = 1.0;
			texel.a = sample.r;
		}
		else
		{
			texel.r = sample.r;
			texel.g = sample.r;
			texel.b = sample.r;
			texel.a = 1.0;
		}
	} break;
	case 3: // IA
	{
		vec4 sample = textureLod(img, texCoord, float(lodLevel));
		texel.r = sample.r;
		texel.g = sample.r;
		texel.b = sample.r;
		texel.a = sample.g;
	} break;
	case 2: // CI -- assume 256-color palette
	{
		// Palette lookup must happen before filtering or blending mip levels.
		vec4 index = textureLod(img, texCoord, float(lodLevel));
		texel = texture(pal, index.x);
	} break;
	case 0: // RGBA
	{
		texel = textureLod(img, texCoord, float(lodLevel));
	} break;
	default: // unsupported or invalid
		texel = vec4(1.0f, 0.0f, 1.0f, 1.0f);
		break;
	}

	return texel;
}

// n64 3-point filtering
// Original author: ArthurCarvalho
// GLSL implementation: twinaphex, mupen64plus-libretro project.

#define TEX_OFFSET(off) getTexel(img, pal, fmt, texCoord - (off)/texSize, lodLevel)

vec4 filter3point(in sampler2D img, in sampler1D pal, in int fmt, in vec2 texCoord, int lodLevel)
{
	vec2 texSize = vec2(textureSize(img, lodLevel));
	vec2 offset = fract(texCoord*texSize - vec2(0.5));
	offset -= step(1.0, offset.x + offset.y);
	vec4 c0 = TEX_OFFSET(offset);
	vec4 c1 = TEX_OFFSET(vec2(offset.x - sign(offset.x), offset.y));
	vec4 c2 = TEX_OFFSET(vec2(offset.x, offset.y - sign(offset.y)));
	return c0 + abs(offset.x)*(c1-c0) + abs(offset.y)*(c2-c0);
}

vec4 sampleTextureLevel(in sampler2D img, in sampler1D pal, in int fmt, in vec2 texCoord, int lodLevel)
{
	vec2 baseSize = vec2(textureSize(img, 0));
	vec2 levelSize = vec2(textureSize(img, lodLevel));
	vec2 levelScale = baseSize / levelSize;
	vec2 baseTexelCoord = texCoord * baseSize - vec2(0.5);
	vec2 levelTexCoord = (baseTexelCoord / levelScale + vec2(0.5)) / levelSize;

	if(useFiltering)
		return filter3point(img, pal, fmt, levelTexCoord, lodLevel);
	else
		return getTexel(img, pal, fmt, levelTexCoord, lodLevel);
}

vec4 sampleTexture(in sampler2D img, in sampler1D pal, in int fmt, in vec2 texCoord)
{
	if(!useLOD || maxLOD <= 0)
		return sampleTextureLevel(img, pal, fmt, texCoord, 0);

	// See N64 manual section 13.7, "Texture Mapping -- Tile Selection".
	// LOD is the largest perspective-corrected texture-coordinate change between
	// adjacent pixels, expressed in texels rather than normalized texture units.
	vec2 texelCoord = texCoord * vec2(textureSize(img, 0));
	vec2 maxDerivative = max(abs(dFdx(texelCoord)), abs(dFdy(texelCoord)));
	// Halving the footprint delays mip selection by one level to match the N64.
	float lod = 0.5 * max(maxDerivative.x, maxDerivative.y);

	float lodTile = 0.0;
	float lodFrac = 0.0;
	if(lod > 1.0)
	{
		lodTile = floor(log2(lod));
		lodFrac = clamp(lod / exp2(lodTile) - 1.0, 0.0, 1.0);
	}

	int tile0 = clamp(int(lodTile), 0, maxLOD);
	int tile1 = clamp(tile0 + 1, 0, maxLOD);
	vec4 color0 = sampleTextureLevel(img, pal, fmt, texCoord, tile0);
	if(tile0 == tile1)
		return color0;

	vec4 color1 = sampleTextureLevel(img, pal, fmt, texCoord, tile1);
	return mix(color0, color1, lodFrac);
}

float calcScaleForShift(int shift)
{
	if (shift <= 10) {
		return 1.0f / (1 << shift);
	} else {
		return float(1 << (16 - shift));
	}
}

// n64 texture coordinates use 10.5 fixed format with each whole number giving one texel
// consider textures of different widths:
// w = 32: u = 1024 means 32 texels --> s coordinate = 1.0
// w = 64: u = 1024 means 32 texels --> s coordinate = 0.5
// w = 64: u = 2048 means 64 texels --> s coordinate = 1.0
// openGL maps all textures onto the range (0,1).
// To convert from UVs to ST coordinates,
//   S = U / (32 * width)
//   T = V / (32 * height)

void main()
{
	ivec2 imgSize = textureSize(mainImage,0);

	/*
	// visualize UVs
	o_color = vec4(
			(0.5 + ((f_texCoords.s / 32.0) - (mainScroll.s / 1024.0))) / imgSize.s,
			(0.5 + ((f_texCoords.t / 32.0) - (mainScroll.t / 1024.0))) / imgSize.t,
			1.0, 1.0);
	*/

	bool selected = f_aux[0] > 0.0;

	if(drawMode == MODE_FILL_SOLID || drawMode == MODE_LINE_OUTLINE)
	{
		if(textured)
		{
			ivec2 imgSize = textureSize(mainImage,0);

			vec4 mainColor = sampleTexture(mainImage, mainPalette, mainFormat, vec2(
					(0.5 + ((f_texCoords.s / 32.0) - (mainScroll.s / 1024.0))) / imgSize.s,
					(0.5 + ((f_texCoords.t / 32.0) - (mainScroll.t / 1024.0))) / imgSize.t
			));

			if(auxCombineMode == 0)
			{
				o_color = mainColor * f_color;
			}
			else
			{
				ivec2 auxSize = textureSize(auxImage,0);

				vec4 auxColor = sampleTexture(auxImage, auxPalette, auxFormat, vec2(
						(0.5 + auxScale.s * (f_texCoords.s / 32.0) - (auxScroll.s / 1024.0) - (auxOffset.s / 4.0)) / auxSize.s,
						(0.5 + auxScale.t * (f_texCoords.t / 32.0) - (auxScroll.t / 1024.0) - (auxOffset.t / 4.0)) / auxSize.t
				));

				switch(auxCombineMode)
				{
				case 1: // mode 00/08
					o_color = mainColor * auxColor * f_color;
					break;
				case 2: // mode 0D -- vert colors with modulated alpha?
					o_color.rgb = f_color.rgb;
					o_color.a = (mainColor.a - auxColor.a) * f_color.a;
					break;
				case 3: // mode 10 -- vert alpha lerp aux to main
					o_color.rgb = (mainColor.rgb - auxColor.rgb) * f_color.a + auxColor.rgb;
					o_color.a = mainColor.a;
					break;
				default: // MODULATE
					o_color = mainColor * f_color;
				}
			}
		}
		else
		{
			o_color = f_color;
		}

		if(selected)
		{
			if(drawMode == MODE_LINE_OUTLINE)
			{
				o_color = vec4(1.0f, 0.0f, 0.0f, 1.0f);
			}
			else
			{
				o_color.r = 0.5 + 0.5 * o_color.r;
				o_color.g /= 2;
				o_color.b /= 2;
				if(o_color.a > 0.0)
					o_color.a += 0.4;
			}
		}

		if(useFog)
		{
			//float distance = length(f_worldPosition);
			//float distance = -f_worldPosition.z;
			float fogStart = fogDist.x;
			float fogEnd = fogDist.y;
			float fm = 500/(fogEnd-fogStart);
			float fo = (500-fogStart)/(fogEnd-fogStart);
			float fa = max(-1.0, f_screenPosition.z / f_screenPosition.w) * fm + fo;

			float alpha = clamp(fa, 0.0, 1.0);
			o_color.rgb = mix(o_color.rgb, fogColor.rgb, alpha);

			//	if(distance > fogDist.y) // end
			//		o_color.rgb = fogColor.rgb;
			//	else if(distance > fogDist.x) // start
			//		o_color.rgb = mix(o_color.rgb, fogColor.rgb, (distance - fogDist.x) / (fogDist.y - fogDist.x));
		}
	}
	else if(drawMode == MODE_LINE_SOLID)
	{
		if(selected)
			o_color = vec4(0.8f, 0.0f, 0.0f, 0.5f);
		else
			discard;
	}
	else if(drawMode == MODE_FILL_OUTLINE)
	{
		if(selected)
			o_color = vec4(1.0f, 0.0f, 0.0f, 1.0f);
		else
			discard;
	}
	else if(drawMode == MODE_FILL_OUTLINE_HIGHLIGHT)
	{
		if(selected)
			o_color = vec4(1.0f, 0.0f, 0.0f, 1.0f);
		else
			o_color = vec4(0.0f, 0.0f, 1.0f, 1.0f);
	}

	if(o_color.a == 0.0f)
		discard;
}
