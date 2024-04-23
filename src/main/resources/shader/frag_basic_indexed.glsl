#version 330 core

in vec4 f_color;
in vec2 f_texCoords;
in vec4 f_worldPosition;
in vec4 f_screenPosition;

out vec4 o_color;

uniform sampler2D u_image;
uniform sampler1D u_palette;
uniform vec4 u_baseColor;

uniform bool u_multiplyBaseColor;
uniform bool u_multiplyVertexColor;
uniform bool u_useFiltering;

// retrieves a texel from the tile, using different methods depending on its format
vec4 getTexel(in sampler2D img, in sampler1D pal, in vec2 texCoord)
{
//	vec2 texSize = vec2(textureSize(img,0));
//	vec2 uvs = vec2(texCoord.x + 0.5/texSize.x, texCoord.y + 0.5/texSize.y);

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
	if(u_useFiltering)
		return filter3point(img, pal, texCoord);
	else
		return getTexel(img, pal, texCoord);
}

void main()
{
	o_color = sampleTexture(u_image, u_palette, f_texCoords);

	if(u_multiplyVertexColor)
		o_color *= f_color;

	if(u_multiplyBaseColor)
		o_color *= u_baseColor;

	if(o_color.a == 0.0f)
		discard;
}
