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

uniform vec2 g_quadTexScale;
uniform vec2 g_quadTexShift;

out vec4 f_color;
out vec2 f_texCoords;
out vec2 f_aux;

out vec4 f_worldPosition;
out vec4 f_screenPosition;

out vec3 f_vertPos;
flat out vec3 f_vertStartPos;

void main()
{
	f_color = v_color;
    f_texCoords = g_quadTexShift + (g_quadTexScale * v_texCoords);
    f_aux = v_aux;

	f_worldPosition = (g_viewMatrix * g_modelMatrix * v_position);
	f_screenPosition = (g_projectionMatrix * f_worldPosition);
	gl_Position = f_screenPosition;

	f_vertPos   = f_worldPosition.xyz / f_worldPosition.w;
	f_vertStartPos = f_vertPos;
}
