#version 330 core

in vec2 f_texCoords;
out vec4 o_color;
uniform sampler2D u_image;

#define M_PI 3.1415926535897932384626433832795

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

void main(void)
{
	vec2 texSize = textureSize(u_image, 0);
	float w = 1.0 / texSize.x;
	float h = 1.0 / texSize.y;

	vec4 sp[9]; // samples
	sp[0] = texture(u_image, f_texCoords + vec2(-w, -h));
	sp[1] = texture(u_image, f_texCoords + vec2( 0, -h));
	sp[2] = texture(u_image, f_texCoords + vec2( w, -h));
	sp[3] = texture(u_image, f_texCoords + vec2(-w,  0));
	sp[4] = texture(u_image, f_texCoords);
	sp[5] = texture(u_image, f_texCoords + vec2( w, 0));
	sp[6] = texture(u_image, f_texCoords + vec2(-w, h));
	sp[7] = texture(u_image, f_texCoords + vec2( 0, h));
	sp[8] = texture(u_image, f_texCoords + vec2( w, h));

	vec4 sobelDx = sp[2] + 2.0*sp[5] + sp[8] - (sp[0] + 2.0*sp[3] + sp[6]);
  	vec4 sobelDy = sp[0] + 2.0*sp[1] + sp[2] - (sp[6] + 2.0*sp[7] + sp[8]);

	float dx = dot(sobelDx, sobelDx) / 4.0;
	float dy = dot(sobelDy, sobelDy) / 4.0;
	float intensity = sqrt((dx * dx) + (dy * dy));

	o_color.rgb = vec3(intensity);
	o_color.a = 1.0;
}
