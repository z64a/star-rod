#version 330 core

in vec2 f_texCoords;
out vec4 o_color;
uniform sampler2D u_image;

#define M_PI 3.1415926535897932384626433832795

vec3 hsv2rgb(vec3 c)
{
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float intensity(vec4 c)
{
	return 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b;
//	return c.x*c.x + c.y*c.y + c.z*c.z;
}

float convolve(mat3 samples, mat3 kernel)
{
	return dot(matrixCompMult(samples, kernel) * vec3(1,1,1), vec3(1,1,1));
}

void main(void)
{
	vec2 texSize = textureSize(u_image, 0);
	float w = 1.0 / texSize.x;
	float h = 1.0 / texSize.y;

	mat3 samples; // samples
	for(int i = 0; i < 3; i++)
	for(int j = 0; j < 3; j++)
		samples[i][j] = intensity(texture(u_image, f_texCoords + vec2(w*(i-1), h*(j-1))));

	mat3 sobel3 = mat3(
			1, 0, -1,
			2, 0, -2,
			1, 0, -1);

	float dx = convolve(samples, sobel3);
	float dy = convolve(transpose(samples), sobel3);

	float intensity = sqrt((dx * dx) + (dy * dy));
	float hue = 0.5 + atan(dy, dx) / M_PI; // normalized to [0,1]

	o_color.rgb = hsv2rgb(vec3(hue, 1.0, intensity));
	o_color.a = 1.0;
}
