#version 330 core

in vec2 f_texCoords;
out vec4 o_color;

uniform sampler2D u_image;
uniform float u_alpha;
uniform vec3 u_color;

uniform bool u_hasOutline;
uniform vec3 u_outlineColor;

uniform float u_width;
uniform float u_outlineWidth;

uniform float u_edge;
uniform float u_outlineEdge;

void main()
{
	float dist = 1.0 - texture(u_image, f_texCoords).a;
	float alpha = 1.0 - smoothstep(u_width, u_width + u_edge, dist);

	if(u_hasOutline)
	{
		float alphaEdge = 1.0 - smoothstep(u_outlineWidth, u_outlineWidth + u_outlineEdge, dist);
		float combinedAlpha = mix(alphaEdge, 1.0, alpha);

		o_color = vec4(mix(u_outlineColor, u_color, alpha / combinedAlpha), combinedAlpha);
	}
	else
	{
		o_color = vec4(u_color, alpha);
	}

	o_color.a *= u_alpha;

	if(o_color.a == 0.0f)
		discard;
}
