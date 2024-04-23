#version 330 core

in vec4 f_color;
in vec2 f_texCoords;
in vec4 f_worldPosition;
in vec4 f_screenPosition;

flat in vec3 f_vertStartPos;

out vec4 o_color;

layout (std140) uniform Globals {
	mat4 g_projectionMatrix;
	mat4 g_viewMatrix;
	mat4 g_modelMatrix;
	ivec4 g_viewport;
	float g_time;
};

uniform float u_dashSize;
uniform float u_dashRatio;
uniform float u_dashSpeedRate;

uniform vec4 u_color;
uniform bool u_useVertexColor;

void main()
{
	if(u_useVertexColor)
		o_color = f_color;
	else
		o_color = u_color;

	float dashSize = u_dashSize * (u_dashRatio);
	float gapSize =  u_dashSize * (1 - u_dashRatio);

	if(u_dashRatio < 1.0)
	{
		vec3 dir = f_worldPosition.xyz - f_vertStartPos;
		float dist = length(dir.xyz) + g_time * u_dashSpeedRate * u_dashSize;
		float phase = fract(dist / (dashSize + gapSize));

		o_color.a = smoothstep(0, 0.08, phase)
				-   smoothstep(u_dashRatio, u_dashRatio + 0.08, phase);

	//	if (phase > u_dashRatio)
	//		discard;
	}

	if(o_color.a == 0.0f)
		discard;
}
