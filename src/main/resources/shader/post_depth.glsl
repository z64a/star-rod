#version 330 core

in vec2 f_texCoords;
out vec4 o_color;

uniform sampler2D u_depth;

uniform float u_znear;
uniform float u_zfar;

float linearize_depth(float d, float n, float f)
{
    float ndc = 2.0 * d - 1.0; // [0,1] -> [-1,1]
    return 2.0 * n * f / (f + n - ndc * (f - n));
}

void main()
{
	float depth = texture(u_depth, f_texCoords).x;
	float z = linearize_depth(depth, u_znear, u_zfar);
	z = z / 1024.0;
	o_color = vec4(z, z, z, 1.0);
}
