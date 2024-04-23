#version 330 core

in vec2 f_texCoords;
out vec4 o_color;
uniform sampler2D u_image;

void main()
{
	o_color = texture(u_image, f_texCoords);
}
