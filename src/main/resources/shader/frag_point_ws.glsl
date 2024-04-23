#version 330 core

in vec4 f_color;
in vec2 f_texCoords;
in vec4 f_worldPosition;
in vec4 f_screenPosition;

out vec4 o_color;

uniform vec4 u_color;

void main()
{
	o_color = u_color * f_color;

	// get distance from center of point square
	vec2 fromCenter = f_texCoords - vec2(0.5);
	float dist = length(fromCenter);

	// feather the edges a bit
	o_color.a *= 1.0 - smoothstep(0.4, 0.5, dist);

	if(o_color.a == 0.0f)
		discard;
}
