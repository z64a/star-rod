#version 330 core

in vec4 f_color;
noperspective in vec2 f_lineCoord;
flat in float f_lineLength;
in float f_dashCoord;

out vec4 o_color;

layout (std140) uniform Globals {
	mat4 g_projectionMatrix;
	mat4 g_viewMatrix;
	mat4 g_modelMatrix;
	ivec4 g_viewport;
	float g_time;
};

uniform float u_lineWidth;
uniform float u_dashSize;
uniform float u_dashRatio;
uniform float u_dashSpeedRate;

uniform vec4 u_color;
uniform bool u_useVertexColor;

const float FEATHER_WIDTH = 1.0;

void main()
{
	vec4 lineColor = u_useVertexColor ? f_color : u_color;

	float nearestX = clamp(f_lineCoord.x, 0.0, f_lineLength);
	float edgeDistance = length(f_lineCoord - vec2(nearestX, 0.0));
	float halfWidth = max(u_lineWidth, 0.0) * 0.5;
	float coverage = 1.0 - smoothstep(halfWidth, halfWidth + FEATHER_WIDTH, edgeDistance);

	float dashRatio = clamp(u_dashRatio, 0.0, 1.0);
	if (dashRatio < 1.0) {
		float period = max(u_dashSize, 0.0001);
		float distance = f_dashCoord + g_time * u_dashSpeedRate * period;
		float distanceInPeriod = mod(mod(distance, period) + period, period);
		float phase = distanceInPeriod / period;
		float feather = max(fwidth(f_dashCoord) / period, 0.0001);
		float dashCoverage = smoothstep(0.0, feather, phase)
			- smoothstep(dashRatio, dashRatio + feather, phase);
		coverage *= dashCoverage;
	}

	o_color = vec4(lineColor.rgb, lineColor.a * coverage);
	if (o_color.a <= 0.0)
		discard;
}
