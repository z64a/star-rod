#version 330 core

in vec2 f_texCoords;
out vec4 o_color;

uniform sampler2D u_scene;
uniform sampler2D u_image;
uniform int u_pass;

uniform vec4 u_viewport;		// relative to full frame buffer; ie, within [0,1]
uniform vec4 u_sceneViewport;	// relative to full frame buffer; ie, within [0,1]

uniform float weight[5] = float[] (0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

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
	vec2 tex_offset = 1.0 / textureSize(u_image, 0);
	vec3 result = fragColor.rgb * filterWeight;

	switch(u_pass)
	{
	case 0:
		float luma = dot(fragColor.rgb, vec3(0.2125, 0.7154, 0.0721));
		if(luma > 0.7)
			o_color = fragColor;
		else
			o_color = vec4(0.0, 0.0, 0.0, 1.0);
		break;
	case 1:
	case 3:
	case 5:
	case 7:
		for(int i = 1; i < filterHalfWidth; i++)
		{
			result += texture(u_image, f_texCoords + vec2(tex_offset.x * i, 0.0)).rgb * filterWeight;
			result += texture(u_image, f_texCoords - vec2(tex_offset.x * i, 0.0)).rgb * filterWeight;
		}
		o_color = vec4(result, 1.0);
		break;
	case 2:
	case 4:
	case 6:
	case 8:
		for(int i = 1; i < filterHalfWidth; i++)
		{
			result += texture(u_image, f_texCoords + vec2(0.0, tex_offset.y * i)).rgb * filterWeight;
			result += texture(u_image, f_texCoords - vec2(0.0, tex_offset.y * i)).rgb * filterWeight;
		}
		o_color = vec4(result, 1.0);
		break;
	case 9:
		vec2 uv = fb2vp(f_texCoords);
		vec4 level0 = getBloomSample(uv, 0);
		vec4 level1 = getBloomSample(uv, 1);
		vec4 level2 = getBloomSample(uv, 2);
		vec4 level3 = getBloomSample(uv, 3);

		vec4 bloomColor = 0.1 * level0 + 0.2 * level1 + 0.5 * level2 + 1.0 * level3;
		vec4 sceneColor = texture(u_scene, vp2sb(fb2vp(f_texCoords)));
		o_color = sceneColor + bloomColor;

	//	o_color = texture(u_image, fb2vp(f_texCoords));
		o_color.a = 1.0;
		break;
	}
}
