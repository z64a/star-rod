#version 330 core

in vec2 f_texCoords;
out vec4 o_color;

uniform sampler2D u_scene;
uniform sampler2D u_image;
uniform int u_pass;

uniform float weight[5] = float[] (0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main()
{
	vec2 tex_offset = 1.0 / textureSize(u_image, 0); // gets size of single texel
	vec3 result = texture(u_image, f_texCoords).rgb * weight[0]; // current fragment's contribution

	switch(u_pass)
	{
	case 0:
		for(int i = 1; i < 5; ++i)
		{
			result += texture(u_image, f_texCoords + vec2(tex_offset.x * i, 0.0)).rgb * weight[i];
			result += texture(u_image, f_texCoords - vec2(tex_offset.x * i, 0.0)).rgb * weight[i];
		}
		break;
	case 1:
		for(int i = 1; i < 5; ++i)
		{
			result += texture(u_image, f_texCoords + vec2(0.0, tex_offset.y * i)).rgb * weight[i];
			result += texture(u_image, f_texCoords - vec2(0.0, tex_offset.y * i)).rgb * weight[i];
		}
		break;
	}

	o_color = vec4(result, 1.0);
}
