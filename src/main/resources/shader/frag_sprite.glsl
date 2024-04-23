#version 330 core

in vec4 f_color;
in vec2 f_texCoords;
in vec4 f_worldPosition;
in vec4 f_screenPosition;

out vec4 o_color;

uniform bool u_highlighted;
uniform bool u_selected;
uniform bool u_selectedShading;

uniform sampler2D u_image;
uniform sampler1D u_palette;

uniform float u_alpha;

uniform bool u_useFiltering;

uniform bool u_useShading = false;
uniform vec2 u_shadingOffset = vec2(0, 0);
uniform vec3 u_shadingShadow = vec3(1.0f, 1.0f, 1.0f);
uniform vec3 u_shadingHighlight = vec3(1.0f, 1.0f, 1.0f);

vec4 getTexel(in vec2 texCoord)
{
	// assume 256-color palette
	vec4 index = texture(u_image, texCoord);
	return texture(u_palette, index.x);
}

// n64 3-point filtering
// Original author: ArthurCarvalho
// GLSL implementation: twinaphex, mupen64plus-libretro project.

#define TEX_OFFSET(off) getTexel(texCoord - (off)/texSize)

vec4 filter3point(in vec2 texCoord)
{
	vec2 texSize = vec2(textureSize(u_image,0));
	vec2 offset = fract(texCoord*texSize - vec2(0.5));
	offset -= step(1.0, offset.x + offset.y);
	vec4 c0 = TEX_OFFSET(offset);
	vec4 c1 = TEX_OFFSET(vec2(offset.x - sign(offset.x), offset.y));
	vec4 c2 = TEX_OFFSET(vec2(offset.x, offset.y - sign(offset.y)));
	return c0 + abs(offset.x)*(c1-c0) + abs(offset.y)*(c2-c0);
}

void main()
{
	if(u_useFiltering)
		o_color = filter3point(f_texCoords);
	else
		o_color = getTexel(f_texCoords);

	if(u_useShading)
	{
		vec2 offsetScale = 4.0 * (vec2(textureSize(u_image,0)) - vec2(1.0, 1.0));
		vec2 shiftedCoord = f_texCoords - (u_shadingOffset/offsetScale);
		shiftedCoord = clamp(shiftedCoord, 0.0, 1.0);
		vec4 shadowTexel = getTexel(shiftedCoord);

		if(shadowTexel.a > 0.1f)
			o_color.rgb *= u_shadingShadow;
		else
			o_color.rgb *= u_shadingHighlight;
	}

	// outline
	if(u_highlighted || u_selected)
	{
		vec3 edgeColor = vec3(1.0f, 1.0f, 1.0f);
		if(u_highlighted && u_selected)
			edgeColor = vec3(0.0f, 1.0f, 1.0f);
		else if(u_selected)
			edgeColor = vec3(1.0f, 0.0f, 0.0f);
		else if(u_highlighted)
			edgeColor = vec3(1.0f, 1.0f, 1.0f);

		// only test edges for fully transparent texels
		if(getTexel(f_texCoords.st).a == 0)
		{
			float greatest = 0;
			int width = 2;  // outline thickness
			vec2 size = 1.0f / textureSize(u_image, 0);
			for (int i = -width; i <= width; i++)
				for (int j = -width; j <= width; j++)
				{
					if (i == 0 && j == 0)
						continue;

					vec2 offset = vec2(i, j) * size;
					vec2 samplePos = vec2(
							clamp(f_texCoords.s + offset.s, 0.01, 0.99),
							clamp(f_texCoords.t + offset.t, 0.01, 0.99));

					if(getTexel(samplePos).a != 0)
					{
						float a = 1.0 - pow(sqrt(i*i + j*j) / 4.0, 2); // use pow(x,3) for sharper edge
						if(a > greatest)
							greatest = a;
					}
				}

			if(greatest > 0)
				o_color = vec4(edgeColor, greatest);
		}
	}

	if(u_selectedShading)
	{
		o_color.r = 0.5 + 0.5 * o_color.r;
		o_color.g /= 2;
		o_color.b /= 2;
		if(o_color.a > 0.0)
			o_color.a += 0.4;
	}
	
	o_color.a *= u_alpha;

	// 'alpha test' required for stencil buffer read to work correctly
	if(o_color.a == 0.0f)
		discard;
}
