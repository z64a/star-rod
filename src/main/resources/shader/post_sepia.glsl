#version 330 core

in vec2 f_texCoords;
out vec4 o_color;
uniform sampler2D u_image;

void main()
{
	vec4 texColor = texture(u_image, f_texCoords);

	// apply color matrix
	o_color.r = (texColor.r * 0.393) + (texColor.g * 0.769) + (texColor.b * 0.189);
	o_color.g = (texColor.r * 0.349) + (texColor.g * 0.686) + (texColor.b * 0.168);
	o_color.b = (texColor.r * 0.272) + (texColor.g * 0.534) + (texColor.b * 0.131);
	o_color.a = 1.0;
}
