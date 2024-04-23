#version 330 core

in vec2 f_texCoords;
out vec4 o_color;

layout (std140) uniform Globals {
	mat4 g_projectionMatrix;
	mat4 g_viewMatrix;
	mat4 g_modelMatrix;
	ivec4 g_viewport;
	float g_time;
};

uniform sampler2D u_scene;
uniform sampler2D u_image;
uniform sampler2D u_lut;
uniform int u_pass;

uniform vec4 u_viewport;		// relative to full frame buffer; ie, within [0,1]
uniform vec4 u_sceneViewport;	// relative to full frame buffer; ie, within [0,1]
uniform vec2 resolution;

uniform float weight[5] = float[] (0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
const vec3 rgb2luma = vec3(0.2125, 0.7154, 0.0721);

// convert normalized vp coords to framebuffer sampling coords
// use this before sampling the frambuffer
vec2 vp2fb(vec2 uv)
{
	return u_viewport.xy + uv * (u_viewport.zw - u_viewport.xy);
}

// convert uv coords of the frame buffer to normalized viewport coords
vec2 fb2vp(vec2 fragCoord)
{
	return (fragCoord - u_viewport.xy) / (u_viewport.zw - u_viewport.xy);
}

// convert normalized vp coords to scene framebuffer sampling coords
// use this before sampling the scene frambuffer
vec2 vp2sb(vec2 uv)
{
	return u_sceneViewport.xy + uv * (u_sceneViewport.zw - u_sceneViewport.xy);
}

// convert uv coords of the scene frame buffer to normalized viewport coords
vec2 sb2vp(vec2 fragCoord)
{
	return (fragCoord - u_sceneViewport.xy) / (u_sceneViewport.zw - u_sceneViewport.xy);
}

float random(vec2 p)
{
	vec2 K1 = vec2(
			23.14069263277926, // e^pi (Gelfond's constant)
			2.665144142690225 // 2^sqrt(2) (Gelfond–Schneider constant)
	);
	return fract(cos(dot(p,K1)) * 12345.6789);
}

#define MAXCOLOR 15.0
#define COLORS 16.0
#define WIDTH 256.0
#define HEIGHT 16.0

// https://defold.com/tutorials/grading/
vec3 grade(vec3 color)
{
	float cell = color.b * MAXCOLOR;

	float cell_l = floor(cell);
	float cell_h = ceil(cell);

	float half_px_x = 0.5 / WIDTH;
	float half_px_y = 0.5 / HEIGHT;
	float r_offset = half_px_x + color.r / COLORS * (MAXCOLOR / COLORS);
	float g_offset = half_px_y + color.g * (MAXCOLOR / COLORS);

	vec2 lut_pos_l = vec2(cell_l / COLORS + r_offset, g_offset);
	vec2 lut_pos_h = vec2(cell_h / COLORS + r_offset, g_offset);

	vec4 graded_color_l = texture(u_lut, lut_pos_l);
	vec4 graded_color_h = texture(u_lut, lut_pos_h);

	return mix(graded_color_l.rgb, graded_color_h.rgb, fract(cell));
}

vec4 getBloomSample(vec2 uv, int level)
{
	vec2 min;
	vec2 size;
	switch(level)
	{
	default:
		min = vec2(0, 0);
		size = vec2(0.5, 1.0);
		break;
	case 1:
		min = vec2(0.5, 0);
		size = vec2(0.25, 0.5);
		break;
	case 2:
		min = vec2(0.5, 0.5);
		size = vec2(0.125, 0.25);
		break;
	case 3:
		min = vec2(0.5, 0.75);
		size = vec2(0.0625, 0.125);
		break;
	case 4:
		min = vec2(0.5, 0.875);
		size = vec2(0.03125, 0.0625);
		break;
	}
	return texture(u_image, min + uv * size);
}

const int filterHalfWidth = 6;
const float filterWeight = 1.0 / 13.0;

void main()
{
	vec4 fragColor = texture(u_image, f_texCoords);
	vec4 sceneColor = texture(u_scene, vp2sb(fb2vp(f_texCoords)));

	vec2 tex_offset = 1.0 / textureSize(u_image, 0);
	vec3 blurAccum = fragColor.rgb * filterWeight;

	switch(u_pass)
	{
	case 0:
		vec3 graded = grade(sceneColor.rgb);
		float luma = dot(sceneColor.rgb, rgb2luma);
		if(luma > 0.7)
			o_color.rgb = graded;
		else
			o_color.rgb = vec3(0.0);
		break;
	case 1:
	case 3:
	case 5:
	case 7:
		for(int i = 1; i < filterHalfWidth; ++i)
		{
			blurAccum += texture(u_image, f_texCoords + vec2(tex_offset.x * i, 0.0)).rgb * filterWeight;
			blurAccum += texture(u_image, f_texCoords - vec2(tex_offset.x * i, 0.0)).rgb * filterWeight;
		}
		o_color.rgb = vec3(blurAccum);
		break;
	case 2:
	case 4:
	case 6:
	case 8:
		for(int i = 1; i < filterHalfWidth; ++i)
		{
			blurAccum += texture(u_image, f_texCoords + vec2(0.0, tex_offset.y * i)).rgb * filterWeight;
			blurAccum += texture(u_image, f_texCoords - vec2(0.0, tex_offset.y * i)).rgb * filterWeight;
		}
		o_color.rgb = blurAccum;
		break;
	case 9:
		vec2 uv = fb2vp(f_texCoords);
		vec4 level0 = getBloomSample(uv, 0);
		vec4 level1 = getBloomSample(uv, 1);
		vec4 level2 = getBloomSample(uv, 2);
		vec4 level3 = getBloomSample(uv, 3);

		vec4 bloomColor = 0.1 * level0 + 0.25 * level1 + 0.5 * level2 + 3.0 * level3;

		float r = 0.1 * random(fb2vp(f_texCoords) + vec2(fract(g_time)));
		o_color.rgb = grade(sceneColor.rgb) + bloomColor.rgb + vec3(r);
		break;
	}
	o_color.a = 1.0;
}
