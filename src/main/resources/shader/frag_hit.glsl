#version 330 core

in vec4 f_color;
in vec2 f_aux;

out vec4 o_color;

const int MODE_FILL_SOLID = 0;
const int MODE_FILL_OUTLINE = 1;
const int MODE_LINE_SOLID = 4;
const int MODE_LINE_OUTLINE = 5;

uniform int u_drawMode;
uniform vec4 u_selectColor;

void main()
{
	if(u_drawMode == MODE_FILL_SOLID || u_drawMode == MODE_LINE_SOLID)
		o_color = f_color;
	else if(u_drawMode == MODE_FILL_OUTLINE || u_drawMode == MODE_LINE_OUTLINE)
		o_color = vec4(f_color.rgb, 0.8);
}
