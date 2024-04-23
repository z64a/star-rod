#version 330 core

in vec4 f_color;
in vec2 f_texCoords;
in vec4 f_worldPosition;
in vec4 f_screenPosition;

out vec4 o_color;

uniform sampler2D u_image;
uniform vec4 u_tintColor;

uniform float u_time;

const mat2 m2 = mat2(0.8,-0.6,0.6,0.8);

float rand(vec2 n)
{
	return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

float noise(vec2 p)
{
	vec2 ip = floor(p);
	vec2 u = fract(p);
	u = u*u*(3.0-2.0*u);

	float res = mix(
			mix(rand(ip),rand(ip+vec2(1.0,0.0)),u.x),
			mix(rand(ip+vec2(0.0,1.0)),rand(ip+vec2(1.0,1.0)),u.x),u.y);
	return res*res;
}

float fbm(in vec2 p)
{
	float f = 0.0;
	f += 0.5000*noise(p); p = m2*p*2.02;
	f += 0.2500*noise(p); p = m2*p*2.03;
	f += 0.1250*noise(p); p = m2*p*2.01;
	f += 0.0625*noise(p);
	return f/0.769;
}

float pattern(in vec2 p)
{
	vec2 q = vec2(fbm(p + vec2(0.0,0.0)));
	vec2 r = vec2(fbm(p + 4.0*q + vec2(1.7,9.2)));
	r+= u_time * 0.15;
	return fbm(p + 1.760*r);
}

void main()
{
	vec4 tex_color = texture(u_image, f_texCoords);
	tex_color *= u_tintColor;

	float displacement = pattern(0.5 * f_texCoords);
	vec4 color = vec4(displacement * 1.2, 0.2, displacement * 5.0, 1.0);

	o_color = mix(tex_color, color, min(color.r * 0.5, 1.0));
	o_color = min(o_color, tex_color);
	o_color.a = 1.0f;
}
