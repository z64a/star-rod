#version 330 core

in vec4 f_color;
in vec2 f_texCoords;
in vec4 f_worldPosition;
in vec4 f_screenPosition;

out vec4 o_color;

uniform bool u_multiplyVertexColor;
uniform vec4 u_baseColor;

void main()
{
	if(u_multiplyVertexColor)
		o_color = u_baseColor * f_color;
	else
		o_color = u_baseColor;
}
