#version 330 core

layout (location=0) in vec4 v_position;
layout (location=1) in vec4 v_color;
layout (location=2) in vec2 v_texCoords;
layout (location=3) in vec2 v_aux;

layout (std140) uniform Globals {
	mat4 g_projectionMatrix;
	mat4 g_viewMatrix;
	mat4 g_modelMatrix;
	ivec4 g_viewport;
	float g_time;
};

out vec4 f_color;

void main()
{
	f_color = v_color;

	mat4 modelView = g_viewMatrix * g_modelMatrix;
	vec4 worldPosition = (modelView * v_position);
	vec4 screenPosition = (g_projectionMatrix * worldPosition);
	gl_Position = screenPosition;

	gl_PointSize = v_aux.s;
}
