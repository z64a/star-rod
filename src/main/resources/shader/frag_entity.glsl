#version 330 core

in vec4 f_color;
in vec2 f_texCoords;
in vec2 f_aux;

in vec4 f_worldPosition;
in vec4 f_screenPosition;

out vec4 o_color;

uniform ivec2 g_viewportSize;

uniform bool u_selected = false;
uniform bool u_textured = false;

uniform int u_fmt = 0;
uniform sampler2D u_img;
uniform sampler1D u_pal;

uniform bool u_useFiltering = false;

uniform bool u_useFog = false;
uniform vec2 u_fogDist = vec2(950, 1000);
uniform vec4 u_fogColor = vec4(0.04f, 0.04f, 0.04f, 1.0f);

// retrieves a texel from the tile, using different methods depending on its u_fmt
vec4 getTexel(in sampler2D img, in sampler1D pal, in int fmt, in vec2 texCoord)
{
	vec4 texel;
	
	switch(fmt)
	{
	case 4: // I
	{
		vec4 sample = texture(img, texCoord);
		texel.r = sample.r;
		texel.g = sample.r;
		texel.b = sample.r;
		texel.a = 1.0;
	} break;
	case 3: // IA
	{
		vec4 sample = texture(img, texCoord);
		texel.r = sample.r;
		texel.g = sample.r;
		texel.b = sample.r;
		texel.a = sample.g;
	} break;
	case 2: // CI
	{
		// assume 256-color u_pal
		vec4 index = texture(img, texCoord);
		texel = texture(pal, index.x);
	
		// more general method
		//	float palIndex = texture2D(img, texCoord).r * 255.0;
		//	texel = texture1D(pal, palIndex / (textureSize(pal, 0) - 1));
	} break;
	case 0: // RGBA
	{
		texel = texture(img, texCoord);
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

#define TEX_OFFSET(off) getTexel(img, pal, fmt, texCoord - (off)/texSize)

vec4 filter3point(in sampler2D img, in sampler1D pal, in int fmt, in vec2 texCoord)
{
	vec2 texSize = vec2(textureSize(img,0));
	vec2 offset = fract(texCoord*texSize - vec2(0.5));
	offset -= step(1.0, offset.x + offset.y);
	vec4 c0 = TEX_OFFSET(offset);
	vec4 c1 = TEX_OFFSET(vec2(offset.x - sign(offset.x), offset.y));
	vec4 c2 = TEX_OFFSET(vec2(offset.x, offset.y - sign(offset.y)));
	return c0 + abs(offset.x)*(c1-c0) + abs(offset.y)*(c2-c0);
}

vec4 sampleTexture(in sampler2D img, in sampler1D pal, in int fmt, in vec2 texCoord)
{
	if(u_useFiltering)
		return filter3point(img, pal, fmt, texCoord);
	else
		return getTexel(img, pal, fmt, texCoord);
}

// n64 texture coordinates use 10.5 fixed u_fmt with each whole number giving one texel
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
	if(u_textured)
	{
		ivec2 imgSize = textureSize(u_img,0);
		
		vec4 mainColor = sampleTexture(u_img, u_pal, u_fmt, vec2(
				(0.5 + (f_texCoords.s / 32.0)) / imgSize.s,
				(0.5 + (f_texCoords.t / 32.0)) / imgSize.t
				));
		
		o_color = mainColor * f_color;
	}
	else
	{
		o_color = f_color;
	}
	
	if(u_selected)
	{
		o_color.r = 0.5 + 0.5 * o_color.r;
		o_color.g /= 2;
		o_color.b /= 2;
		if(o_color.a > 0.0)
			o_color.a += 0.4;
	}

	if(u_useFog)
	{
		float fogStart = u_fogDist.x;
		float fogEnd = u_fogDist.y;
		float fm = 500/(fogEnd-fogStart);
		float fo = (500-fogStart)/(fogEnd-fogStart);
		float fa = max(-1.0, f_screenPosition.z / f_screenPosition.w) * fm + fo;

		float alpha = clamp(fa, 0.0, 1.0);
		o_color.rgb = mix(o_color.rgb, u_fogColor.rgb, alpha);
	}

	if(o_color.a == 0.0f)
		discard;
}
