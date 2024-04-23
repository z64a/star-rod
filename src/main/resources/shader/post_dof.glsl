#version 330 core

in vec2 f_texCoords;
out vec4 o_color;

uniform sampler2D u_scene;
uniform sampler2D u_depth;
uniform sampler2D u_image;

uniform vec4 u_viewport;		// relative to full frame buffer; ie, within [0,1]
uniform vec4 u_sceneViewport;	// relative to full frame buffer; ie, within [0,1]

uniform int u_pass;
uniform float u_znear;
uniform float u_zfar;

// depth of field
const float u_focalDistance = 415.0;
const float u_focalRange = 500.0;

// 9-tap gaussian blur
uniform float weight[5] = float[] (0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

float linearize_depth(float d, float n, float f)
{
	float ndc = 2.0 * d - 1.0; // [0,1] -> [-1,1]
	return 2.0 * n * f / (f + n - ndc * (f - n));
}

vec2 fbclamp(vec2 uv)
{
	return clamp(uv, u_viewport.xy, u_viewport.zw);
}

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

vec4 getDownSample(vec2 uv, int level)
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
	case 5:
		min = vec2(0.0, 0.0);
		size = vec2(1.0, 1.0);
		break;
	}

	vec2 clamped = clamp(uv, vec2(0), vec2(1));
	return texture(u_image, min + clamped * size);
}

void main()
{
	vec2 texelOffset = 1.0 / textureSize(u_image, 0);

	vec4 sceneColor = texture(u_scene, vp2sb(fb2vp(f_texCoords)));
	vec4 fragColor = texture(u_image, f_texCoords);

	vec3 colorSum = fragColor.rgb * weight[0];
	float alphaSum = fragColor.r * weight[0];

	switch(u_pass)
	{
	case 0:

		float nearStart = 300;
		float nearEnd = 350;
		float farStart = 500;
		float farEnd = 800;

		float depth = texture(u_depth, f_texCoords).x;
		float z = linearize_depth(depth, u_znear, u_zfar);
		o_color = vec4(0,0,0,1);
		//	o_color.r = (z < u_zfar) ? clamp(abs(z - u_focalDistance) / u_focalRange, 0.0, 1.0) : 0.0;
		// o_color.r = clamp(abs(z - u_focalDistance) / u_focalRange, 0.0, 1.0);
		// o_color.r = smoothstep(nearEnd, nearStart, z) + smoothstep(farStart, farEnd, z);
		o_color.r = smoothstep(nearEnd, nearStart, z);
		o_color.b = smoothstep(farStart, farEnd, z);
		o_color.g = 1.0 - o_color.r - o_color.b;
		break;

		// half-size down-sample, bokeh, and blur
	case 1:
		// pass through to down-sample scene
		o_color = sceneColor;
		break;
	case 2:
		// bokeh

		/*
		int size = 5;
		float separation = 1;
		float minThreshold = 0.2;
		float maxThreshold = 0.5;

		float mx = 0.0;
		vec4 cmx = sceneColor;

		for (int i = -size; i <= size; i++)
		{
			for (int j = -size; j <= size; j++)
			{
				// circle
				if (!(distance(vec2(i, j), vec2(0, 0)) <= size)) { continue; }

				vec4 c = texture(u_scene, vp2sb(fb2vp(f_texCoords)) + vec2(i, j) * texelOffset * separation);

				float mxt = dot(c.rgb, vec3(0.3, 0.59, 0.11));

				if (mxt < 0.9 && mxt > mx)
				{
					mx = mxt;
					cmx = c;
				}
			}
		}
		o_color = fragColor;
		o_color.rgb = mix (fragColor.rgb, cmx.rgb, smoothstep(minThreshold, maxThreshold, mx));
		 */

		//TODO skip for now
		o_color = sceneColor;
		break;
	case 3:
		for(int i = 1; i < 5; i++)
		{
			colorSum += texture(u_image, fbclamp(f_texCoords + vec2(texelOffset.x * i, 0.0))).rgb * weight[i];
			colorSum += texture(u_image, fbclamp(f_texCoords - vec2(texelOffset.x * i, 0.0))).rgb * weight[i];
		}
		o_color = fragColor;
		o_color.rgb = colorSum;
		break;
	case 4:
		for(int i = 1; i < 5; i++)
		{
			colorSum += texture(u_image, fbclamp(f_texCoords + vec2(0.0, texelOffset.y * i))).rgb * weight[i];
			colorSum += texture(u_image, fbclamp(f_texCoords - vec2(0.0, texelOffset.y * i))).rgb * weight[i];
		}
		o_color = fragColor;
		o_color.rgb = colorSum;
		break;

	case 5:
		vec2 uv = fb2vp(f_texCoords);
		vec4 intensitySample = getDownSample(uv, 0);
		vec4 blurredSample = getDownSample(uv, 1);
	//	o_color = mix(sceneColor, blurredSample, intensitySample.r);
		o_color = intensitySample.r * blurredSample + intensitySample.g * sceneColor + intensitySample.b * blurredSample;
		o_color.a = 1.0;
		break;
	}
}

