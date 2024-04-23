#version 330 core

in vec4 f_color;

uniform vec4 u_color;

out vec4 o_color;

void main()
{
	o_color = u_color * f_color;

	// get distance from center of point square
	vec2 coord = gl_PointCoord - vec2(0.5);
	float dist = length(coord);

	// feather the edges a bit
	o_color.a *= 1.0 - smoothstep(0.3, 0.5, dist);

	if(o_color.a == 0.0f)
		discard;
}
