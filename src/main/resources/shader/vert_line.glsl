#version 330 core

layout (location=0) in vec3 v_startPosition;
layout (location=1) in vec3 v_endPosition;
layout (location=2) in vec4 v_startColor;
layout (location=3) in vec4 v_endColor;
layout (location=4) in vec2 v_corner;

layout (std140) uniform Globals {
	mat4 g_projectionMatrix;
	mat4 g_viewMatrix;
	mat4 g_modelMatrix;
	ivec4 g_viewport;
	float g_time;
};

uniform float u_lineWidth;
uniform bool u_clipDepth;

out vec4 f_color;
noperspective out vec2 f_lineCoord;
flat out float f_lineLength;
out float f_dashCoord;

const float FEATHER_WIDTH = 1.0;

bool clipLineAgainstPlane(
	vec4 plane,
	inout vec4 clipStart,
	inout vec4 clipEnd,
	inout vec4 startColor,
	inout vec4 endColor,
	inout float dashStart,
	inout float dashEnd)
{
	float startDistance = dot(plane, clipStart);
	float endDistance = dot(plane, clipEnd);
	if (startDistance < 0.0 && endDistance < 0.0)
		return false;

	if (startDistance < 0.0) {
		float t = startDistance / (startDistance - endDistance);
		clipStart = mix(clipStart, clipEnd, t);
		startColor = mix(startColor, endColor, t);
		dashStart = mix(dashStart, dashEnd, t);
	}
	else if (endDistance < 0.0) {
		float t = endDistance / (endDistance - startDistance);
		clipEnd = mix(clipEnd, clipStart, t);
		endColor = mix(endColor, startColor, t);
		dashEnd = mix(dashEnd, dashStart, t);
	}

	return true;
}

void main()
{
	mat4 viewModel = g_viewMatrix * g_modelMatrix;
	vec4 viewStart = viewModel * vec4(v_startPosition, 1.0);
	vec4 viewEnd = viewModel * vec4(v_endPosition, 1.0);
	vec4 clipStart = g_projectionMatrix * viewStart;
	vec4 clipEnd = g_projectionMatrix * viewEnd;
	vec4 startColor = v_startColor;
	vec4 endColor = v_endColor;
	float dashStart = 0.0;
	float dashEnd = distance(viewStart.xyz / viewStart.w, viewEnd.xyz / viewEnd.w);

	vec2 viewportSize = max(vec2(g_viewport.zw), vec2(1.0));
	float outerRadius = max(u_lineWidth, 0.0) * 0.5 + FEATHER_WIDTH;
	vec2 sideMargin = 2.0 * vec2(outerRadius) / viewportSize;

	// Clip the center line before screen-space expansion. This avoids losing a
	// pixel-sized offset when a long line has a projected endpoint far outside
	// the viewport. The side planes include the line radius so grazing lines
	// remain visible. Explicit far clipping is needed while depth clamp is on.
	bool visible = true;
	if (u_clipDepth) {
		visible = clipLineAgainstPlane(vec4(0.0, 0.0, 1.0, 1.0),
			clipStart, clipEnd, startColor, endColor, dashStart, dashEnd);
		if (visible)
			visible = clipLineAgainstPlane(vec4(0.0, 0.0, -1.0, 1.0),
				clipStart, clipEnd, startColor, endColor, dashStart, dashEnd);
	}
	if (visible)
		visible = clipLineAgainstPlane(vec4(1.0, 0.0, 0.0, 1.0 + sideMargin.x),
			clipStart, clipEnd, startColor, endColor, dashStart, dashEnd);
	if (visible)
		visible = clipLineAgainstPlane(vec4(-1.0, 0.0, 0.0, 1.0 + sideMargin.x),
			clipStart, clipEnd, startColor, endColor, dashStart, dashEnd);
	if (visible)
		visible = clipLineAgainstPlane(vec4(0.0, 1.0, 0.0, 1.0 + sideMargin.y),
			clipStart, clipEnd, startColor, endColor, dashStart, dashEnd);
	if (visible)
		visible = clipLineAgainstPlane(vec4(0.0, -1.0, 0.0, 1.0 + sideMargin.y),
			clipStart, clipEnd, startColor, endColor, dashStart, dashEnd);

	if (!visible) {
		gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
		f_color = vec4(0.0);
		f_lineCoord = vec2(0.0);
		f_lineLength = 0.0;
		f_dashCoord = 0.0;
		return;
	}

	vec2 startPixel = (clipStart.xy / clipStart.w) * viewportSize * 0.5;
	vec2 endPixel = (clipEnd.xy / clipEnd.w) * viewportSize * 0.5;
	vec2 delta = endPixel - startPixel;
	float lengthPixels = length(delta);
	vec2 direction = lengthPixels > 0.0001 ? delta / lengthPixels : vec2(1.0, 0.0);
	vec2 normal = vec2(-direction.y, direction.x);

	float useEnd = step(0.0, v_corner.x);
	vec2 offsetPixels = direction * (v_corner.x * FEATHER_WIDTH)
		+ normal * (v_corner.y * outerRadius);

	vec4 endpoint = mix(clipStart, clipEnd, useEnd);
	endpoint.xy += (2.0 * offsetPixels / viewportSize) * endpoint.w;
	gl_Position = endpoint;

	f_color = mix(startColor, endColor, useEnd);
	f_lineCoord = vec2(mix(-FEATHER_WIDTH, lengthPixels + FEATHER_WIDTH, useEnd),
		v_corner.y * outerRadius);
	f_lineLength = lengthPixels;
	f_dashCoord = mix(dashStart, dashEnd, useEnd);
}
