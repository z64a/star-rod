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
uniform sampler3D u_lut;
uniform int u_pass;

uniform vec4 u_viewport;		// relative to full frame buffer; ie, within [0,1]
uniform vec4 u_sceneViewport;	// relative to full frame buffer; ie, within [0,1]

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

float random(vec2 p)
{
  vec2 K1 = vec2(
		  23.14069263277926, // e^pi (Gelfond's constant)
		  2.665144142690225 // 2^sqrt(2) (Gelfond–Schneider constant)
  	  	  );
  return fract(cos(dot(p,K1)) * 12345.6789);
}


void main()
{
	vec4 fragColor = texture(u_image, f_texCoords);
	vec3 hsv;
	vec3 desaturated;

	vec2 tex_offset = 1.0 / textureSize(u_image, 0); // gets size of single texel
	vec3 result = fragColor.rgb * weight[0]; // current fragment's contribution

	switch(u_pass)
	{
	case 0:
		float luma = dot(fragColor.rgb, rgb2luma);
		if(luma > 0.7)
			o_color = vec4(luma, luma, luma, 1.0);
		else
			o_color = vec4(0.0, 0.0, 0.0, 1.0);
		break;
	case 1:
		for(int i = 1; i < 5; ++i)
		{
			result += texture(u_image, f_texCoords + vec2(tex_offset.x * i, 0.0)).rgb * weight[i];
			result += texture(u_image, f_texCoords - vec2(tex_offset.x * i, 0.0)).rgb * weight[i];
		}
		o_color = vec4(result, 1.0);
		break;
	case 2:
		for(int i = 1; i < 5; ++i)
		{
			result += texture(u_image, f_texCoords + vec2(0.0, tex_offset.y * i)).rgb * weight[i];
			result += texture(u_image, f_texCoords - vec2(0.0, tex_offset.y * i)).rgb * weight[i];
		}
		o_color = vec4(result, 1.0);
		break;
	case 3:
		vec4 sceneColor = texture(u_scene, vp2sb(fb2vp(f_texCoords)));

		hsv = rgb2hsv(sceneColor.rgb);
		float ch = hsv.x;
		float cs = hsv.y; // * 0.5
		float cv = hsv.z;
		float v = smoothstep(0, 1, cv*cv);

		v += 0.1 * random(f_texCoords + vec2(g_time));

	//	float color dist = clamp(0, 1, abs(ch - 0.12));
		desaturated = vec3(ch, 0.5 * cs*cs, v);

		o_color = vec4(hsv2rgb(desaturated), 1.0) + fragColor;
		break;
	}
}
