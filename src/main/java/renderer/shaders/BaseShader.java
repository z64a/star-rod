package renderer.shaders;

import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL31.*;
import static renderer.buffers.BufferedMesh.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import app.StarRodMain;
import game.map.Axis;
import game.map.editor.render.PresetColor;
import game.map.shape.TransformMatrix;
import renderer.buffers.BufferedMesh;
import renderer.shaders.components.TexUnit1D;
import renderer.shaders.components.TexUnit2D;
import renderer.shaders.components.UniformFloatVector;

public abstract class BaseShader
{
	// @formatter:off
	protected static final String VS_VERT				= "vert.glsl";
	protected static final String VS_POINT				= "vert_point.glsl";

	protected static final String FS_LINE				= "frag_line.glsl";
	protected static final String FS_POINT				= "frag_point.glsl";
	protected static final String FS_POINT_WS			= "frag_point_ws.glsl";

	protected static final String FS_BASIC_SOLID		= "frag_basic_solid.glsl";
	protected static final String FS_BASIC_TEXTURED		= "frag_basic_textured.glsl";
	protected static final String FS_BASIC_INDEXED		= "frag_basic_indexed.glsl";

	protected static final String FS_SPRITE				= "frag_sprite.glsl";
	protected static final String FS_MODEL				= "frag_model.glsl";
	protected static final String FS_MARKER				= "frag_marker.glsl";
	protected static final String FS_HIT				= "frag_hit.glsl";
	protected static final String FS_ENTITY				= "frag_entity.glsl";
	protected static final String FS_FONT				= "frag_font.glsl";
	protected static final String FS_TEXT				= "frag_text.glsl";
	// @formatter:on

	protected final String name;
	protected final int program;

	public final UniformFloatVector quadTexShift;
	public final UniformFloatVector quadTexScale;

	private final BufferedMesh mesh;
	private TransformMatrix modelMatrix;

	public BaseShader(String name, String vertName, String fragName)
	{
		this(name, ShaderManager.createShader(name, vertName, fragName));
	}

	private BaseShader(String name, int programID)
	{
		this.name = name;
		this.program = programID;

		RenderState.setShaderProgram(this);

		quadTexShift = new UniformFloatVector(program, "g_quadTexShift", 0.0f, 0.0f);
		quadTexScale = new UniformFloatVector(program, "g_quadTexScale", 1.0f, 1.0f);

		glBindBufferBase(GL_UNIFORM_BUFFER, 0, RenderState.getGlobalsUBO());
		int globalsIndex = glGetUniformBlockIndex(program, "Globals");
		glUniformBlockBinding(program, globalsIndex, 0);

		modelMatrix = TransformMatrix.identity();

		mesh = new BufferedMesh(4, 2, VBO_INDEX | VBO_UV | VBO_COLOR | VBO_AUX);
		RenderState.setColor(PresetColor.WHITE);
		int v1 = mesh.addVertex().setPosition(0.0f, 0.0f, 0.0f).setUV(0.0f, 0.0f).getIndex();
		int v2 = mesh.addVertex().setPosition(1.0f, 0.0f, 0.0f).setUV(1.0f, 0.0f).getIndex();
		int v3 = mesh.addVertex().setPosition(1.0f, 1.0f, 0.0f).setUV(1.0f, 1.0f).getIndex();
		int v4 = mesh.addVertex().setPosition(0.0f, 1.0f, 0.0f).setUV(0.0f, 1.0f).getIndex();
		mesh.addQuad(v1, v2, v3, v4);
		mesh.loadBuffers();
	}

	public final void useProgram(boolean resetState)
	{
		RenderState.setShaderProgram(this);

		if (resetState) {
			for (BaseUniform u : getUniforms()) {
				if (!u.resets || u.location == -1)
					continue;
				u.reset();
			}
		}
	}

	public final void initializeCache()
	{
		resetCache();
	}

	public final void resetCache()
	{
		useProgram(false);

		for (BaseUniform u : getUniforms()) {
			if (u.location == -1)
				continue;
			u.reset();
		}
	}

	public final void print()
	{
		List<BaseUniform> uniforms = getUniforms();
		List<TexUnit1D> tex1Units = getTex1Ds();
		List<TexUnit2D> tex2Units = getTex2Ds();

		useProgram(false);

		System.out.println("[" + name + "]");
		if (!tex2Units.isEmpty() || !tex1Units.isEmpty()) {
			System.out.println("### Textures ###");
			for (TexUnit2D t : tex2Units)
				t.print();
			for (TexUnit1D t : tex1Units)
				t.print();
		}
		if (!uniforms.isEmpty()) {
			System.out.println("### Uniforms ###");
			for (BaseUniform u : uniforms)
				u.print();
		}
		System.out.println();
	}

	private final List<BaseUniform> getUniforms()
	{
		List<BaseUniform> uniforms = new ArrayList<>();

		try {
			for (Field f : getClass().getFields()) {
				if (BaseUniform.class.isAssignableFrom(f.getType()))
					uniforms.add((BaseUniform) f.get(this));
			}
		}
		catch (Exception e) {
			StarRodMain.displayStackTrace(e);
		}

		return uniforms;
	}

	private final List<TexUnit1D> getTex1Ds()
	{
		List<TexUnit1D> texUnits = new ArrayList<>();

		try {
			for (Field f : getClass().getFields()) {
				if (TexUnit1D.class.isAssignableFrom(f.getType()))
					texUnits.add((TexUnit1D) f.get(this));
			}
		}
		catch (Exception e) {
			StarRodMain.displayStackTrace(e);
		}

		return texUnits;
	}

	private final List<TexUnit2D> getTex2Ds()
	{
		List<TexUnit2D> texUnits = new ArrayList<>();

		try {
			for (Field f : getClass().getFields()) {
				if (TexUnit2D.class.isAssignableFrom(f.getType()))
					texUnits.add((TexUnit2D) f.get(this));
			}
		}
		catch (Exception e) {
			StarRodMain.displayStackTrace(e);
		}

		return texUnits;
	}

	public void setXYQuadPosSize(float x1, float y1, float w, float h, float z)
	{
		modelMatrix.setScale(w, h, 1.0f);
		modelMatrix.translate(x1, y1, z);
	}

	public void setXYQuadCoords(float x1, float y1, float x2, float y2, float z)
	{
		float dX = x2 - x1;
		float dY = y2 - y1;
		setXYQuadPosSize(x1, y1, dX, dY, z);
	}

	public void setXZQuadPosSize(float x1, float z1, float w, float h, float y)
	{
		modelMatrix.setScale(w, h, 1.0f);
		modelMatrix.rotate(Axis.X, 90);
		modelMatrix.translate(x1, y, z1);
	}

	public void setXZQuadCoords(float x1, float z1, float x2, float z2, float y)
	{
		float dX = x2 - x1;
		float dZ = z2 - z1;
		setXZQuadPosSize(x1, z1, dX, dZ, y);
	}

	public void setYZQuadPosSize(float y1, float z1, float w, float h, float x)
	{
		modelMatrix.setScale(w, h, 1.0f);
		modelMatrix.rotate(Axis.Y, 90);
		modelMatrix.translate(x, y1, z1);
	}

	public void setYZQuadCoords(float y1, float z1, float y2, float z2, float x)
	{
		float dY = y2 - y1;
		float dZ = z2 - z1;
		setXZQuadPosSize(y1, z1, dY, dZ, x);
	}

	public void setQuadTexCoords(float u1, float v1, float u2, float v2)
	{
		float dU = u2 - u1;
		float dV = v2 - v1;
		quadTexScale.set(dU, dV);
		quadTexShift.set(u1, v1);
	}

	public void renderQuad()
	{
		renderQuad(null);
	}

	public void renderQuad(TransformMatrix catMtx)
	{
		TransformMatrix mtx = modelMatrix;
		if (catMtx != null)
			mtx = TransformMatrix.multiply(catMtx, modelMatrix);
		mesh.renderWithTransform(mtx);
	}
}
