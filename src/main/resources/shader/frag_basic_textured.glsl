#version 330 core

in vec4 f_color;
in vec2 f_texCoords;
in vec4 f_worldPosition;
in vec4 f_screenPosition;

out vec4 o_color;

uniform sampler2D u_image;
uniform vec4 u_baseColor;
uniform float u_saturation;

uniform bool u_selected;
uniform bool u_multiplyBaseColor;
uniform bool u_multiplyVertexColor;
uniform bool u_useFiltering;

// n64 3-point filtering
// Original author: ArthurCarvalho
// GLSL implementation: twinaphex, mupen64plus-libretro project.

#define TEX_OFFSET(off) texture(img, texCoord - (off)/texSize)

vec4 filter3point(in sampler2D img, in vec2 texCoord)
{
	vec2 texSize = vec2(textureSize(img,0));
	vec2 offset = fract(texCoord*texSize - vec2(0.5));
	offset -= step(1.0, offset.x + offset.y);
	vec4 c0 = TEX_OFFSET(offset);
	vec4 c1 = TEX_OFFSET(vec2(offset.x - sign(offset.x), offset.y));
	vec4 c2 = TEX_OFFSET(vec2(offset.x, offset.y - sign(offset.y)));
	return c0 + abs(offset.x)*(c1-c0) + abs(offset.y)*(c2-c0);
}

vec4 sampleTexture(in sampler2D img, in vec2 texCoord)
{
	if(u_useFiltering)
		return filter3point(img, texCoord);
	else
		return texture(img, texCoord);
}

vec3 rgb2hsv(vec3 c)
{
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c)
{
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main()
{
	o_color = sampleTexture(u_image, f_texCoords);

	if(u_multiplyVertexColor)
		o_color *= f_color;

	if(u_multiplyBaseColor)
		o_color *= u_baseColor;

	if(u_saturation < 1.0f)
	{
		vec3 hsv = rgb2hsv(o_color.rgb);
		o_color.rgb = hsv2rgb(vec3(hsv.r, u_saturation * hsv.g, hsv.b));
	}

	if(u_selected)
	{
		o_color.r = 0.5 + 0.5 * o_color.r;
		o_color.g /= 2;
		o_color.b /= 2;
		if(o_color.a > 0.0)
			o_color.a += 0.4;
	}

	if(o_color.a == 0.0f)
		discard;
}
