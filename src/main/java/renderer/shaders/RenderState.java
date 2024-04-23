package renderer.shaders;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Stack;

import org.lwjgl.BufferUtils;

import game.map.editor.render.Color4f;
import game.map.editor.render.PresetColor;
import game.map.shape.TransformMatrix;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.buffers.TriangleRenderQueue;

public abstract class RenderState
{
	public static final void init()
	{
		rec = new StateRecord();
		stack = new Stack<>();

		initProgram();
		initVAO();
		initUBO();
		initPolygonMode();
		initDepthTest();
		initDepthWrite();
		initDepthFunc();
		initColor();
		initLineWidth();
		initPointSize();
		initCullFace();
		initBlendEnabled();
		initBlendFunc();

		ShaderManager.init();

		TriangleRenderQueue.init();
		PointRenderQueue.init();
		LineRenderQueue.init();
	}

	private static class StateRecord
	{
		private int glViewSizeX = 256;
		private int glViewSizeY = 256;

		private Color4f glColor = new Color4f();
		private float glLineWidth = 1.0f;
		private float glPointSize = 1.0f;

		private int glVertexArray;
		private BaseShader currentShader;
		private PolygonMode glPolygonMode;

		private boolean enableDepthTest;
		private boolean glDepthMask;
		public int glDepthFunc;

		private boolean enableBlend;
		public int blendSrcFactor;
		public int blendDestFactor;

		private StateRecord()
		{}

		private StateRecord(StateRecord other)
		{
			glViewSizeX = other.glViewSizeX;
			glViewSizeY = other.glViewSizeY;

			glColor = new Color4f(other.glColor);
			glLineWidth = other.glLineWidth;
			glPointSize = other.glPointSize;

			glVertexArray = other.glVertexArray;
			currentShader = other.currentShader;
			glPolygonMode = other.glPolygonMode;

			enableDepthTest = other.enableDepthTest;
			glDepthMask = other.glDepthMask;
			glDepthFunc = other.glDepthFunc;

			enableBlend = other.enableBlend;
			blendSrcFactor = other.blendSrcFactor;
			blendDestFactor = other.blendDestFactor;
		}
	}

	public static enum PolygonMode
	{
		// @formatter:off
		POINT	(GL_POINT),
		LINE	(GL_LINE),
		FILL	(GL_FILL);
		// @formatter:on

		private final int value;

		private PolygonMode(int value)
		{
			this.value = value;
		}
	}

	// --------------------------------------------------------------------------
	// state stack

	private static StateRecord rec;
	private static Stack<StateRecord> stack = new Stack<>();

	public static void pushState()
	{
		rec = new StateRecord(stack.push(rec));
	}

	public static void popState()
	{
		StateRecord oldRec = stack.pop();

		// skip viewport

		setColor(oldRec.glColor.r, oldRec.glColor.g, oldRec.glColor.b, oldRec.glColor.a);
		setLineWidth(oldRec.glLineWidth);
		setPointSize(oldRec.glPointSize);

		setVAO(oldRec.glVertexArray);
		setShaderProgram(oldRec.currentShader);
		setPolygonMode(oldRec.glPolygonMode);

		enableDepthTest(oldRec.enableDepthTest);
		setDepthWrite(oldRec.glDepthMask);
		setDepthFunc(oldRec.glDepthFunc);

		enableBlend(oldRec.enableBlend);
		setBlendFunc(oldRec.blendSrcFactor, oldRec.blendDestFactor);

		rec = oldRec;
	}

	// --------------------------------------------------------------------------
	// cache color

	public static final void initColor()
	{
		rec.glColor.r = 1.0f;
		rec.glColor.g = 1.0f;
		rec.glColor.b = 1.0f;
		rec.glColor.a = 1.0f;
	}

	public static void setColor(PresetColor color)
	{
		setColor(color.r, color.g, color.b, 1.0f);
	}

	public static final void setColor(float r, float g, float b)
	{
		setColor(r, g, b, 1.0f);
	}

	public static final void setColor(float r, float g, float b, float a)
	{
		if (rec.glColor.r == r && rec.glColor.g == g && rec.glColor.b == b && rec.glColor.a == a)
			return;

		rec.glColor.r = r;
		rec.glColor.g = g;
		rec.glColor.b = b;
		rec.glColor.a = a;
	}

	public static final Color4f getColor()
	{
		return rec.glColor; //TODO encapsulation violated
	}

	// --------------------------------------------------------------------------
	// cache line width

	public static final void initLineWidth()
	{
		rec.glLineWidth = 1.0f;
		glLineWidth(rec.glLineWidth);
	}

	public static final void setLineWidth(float width)
	{
		if (width == rec.glLineWidth)
			return;

		rec.glLineWidth = width;
		glLineWidth(rec.glLineWidth);
	}

	public static final float getLineWidth()
	{ return rec.glLineWidth; }

	// --------------------------------------------------------------------------
	// cache point size

	public static final void initPointSize()
	{
		rec.glPointSize = 1.0f;
		glPointSize(rec.glPointSize);
	}

	public static final void setPointSize(float size)
	{
		if (size == rec.glPointSize)
			return;

		rec.glPointSize = size;
		glPointSize(rec.glPointSize);
	}

	public static final float getPointSize()
	{ return rec.glPointSize; }

	// --------------------------------------------------------------------------
	// cache shader program

	public static final void initProgram()
	{
		rec.currentShader = null;
		glUseProgram(0);
	}

	public static final void setShaderProgram(BaseShader shader)
	{
		if (shader == rec.currentShader)
			return;

		rec.currentShader = shader;
		glUseProgram(shader == null ? 0 : rec.currentShader.program);
	}

	// --------------------------------------------------------------------------
	// cache vao

	public static final void initVAO()
	{
		rec.glVertexArray = 0;
		glBindVertexArray(rec.glVertexArray);
	}

	public static final void useDefaultVAO()
	{
		glBindVertexArray(0);
	}

	public static final void setVAO(int vao)
	{
		if (rec.glVertexArray == vao)
			return;

		rec.glVertexArray = vao;
		glBindVertexArray(rec.glVertexArray);
	}

	// --------------------------------------------------------------------------
	// globals UBO

	private static int matrixUBO;

	private static final int GLOBALS_UBO_POS_PERSPMTX = 0;
	private static final int GLOBALS_UBO_POS_VIEWMTX = 0x40;
	private static final int GLOBALS_UBO_POS_MODELMTX = 0x80;
	private static final int GLOBALS_UBO_POS_VIEWPORT = 0xC0;
	private static final int GLOBALS_UBO_POS_TIME = 0xD0;
	private static final int GLOBALS_UBO_TOTAL_SIZE = 0xD4;

	private static void initUBO()
	{
		ByteBuffer bb = BufferUtils.createByteBuffer(GLOBALS_UBO_TOTAL_SIZE);

		matrixUBO = glGenBuffers();
		glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
		glBufferData(GL_UNIFORM_BUFFER, bb, GL_DYNAMIC_DRAW);
		glBindBuffer(GL_UNIFORM_BUFFER, 0);
	}

	public static int getGlobalsUBO()
	{ return matrixUBO; }

	// --------------------------------------------------------------------------
	// global matrix state

	private static final TransformMatrix defaultProjectionMatrix;
	private static final TransformMatrix defaultViewMatrix;
	private static final TransformMatrix defaultModelMatrix;

	protected static TransformMatrix projectionMatrix;
	protected static TransformMatrix viewMatrix;
	protected static TransformMatrix modelMatrix;

	protected static Stack<TransformMatrix> modelMatrixStack;

	static {
		defaultProjectionMatrix = TransformMatrix.identity();
		defaultProjectionMatrix.perspective(50.0f, 1.0f, 1.0f, 0x8000);
		projectionMatrix = new TransformMatrix(defaultProjectionMatrix);

		defaultViewMatrix = TransformMatrix.identity();
		viewMatrix = new TransformMatrix(defaultViewMatrix);

		defaultModelMatrix = TransformMatrix.identity();
		modelMatrix = new TransformMatrix(defaultModelMatrix);

		modelMatrixStack = new Stack<>();
	}

	public static void setProjectionMatrix(TransformMatrix mtx)
	{
		TransformMatrix newMtx = (mtx != null) ? mtx : defaultProjectionMatrix;
		if (!projectionMatrix.equals(newMtx)) {
			projectionMatrix.set(newMtx);

			glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
			glBufferSubData(GL_UNIFORM_BUFFER, GLOBALS_UBO_POS_PERSPMTX, projectionMatrix.toFloatBuffer());
			glBindBuffer(GL_UNIFORM_BUFFER, 0);
		}
	}

	public static void setViewMatrix(TransformMatrix mtx)
	{
		TransformMatrix newMtx = (mtx != null) ? mtx : defaultViewMatrix;
		if (!viewMatrix.equals(newMtx)) {
			viewMatrix.set(newMtx);

			glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
			glBufferSubData(GL_UNIFORM_BUFFER, GLOBALS_UBO_POS_VIEWMTX, viewMatrix.toFloatBuffer());
			glBindBuffer(GL_UNIFORM_BUFFER, 0);
		}
	}

	public static void setModelMatrix(TransformMatrix mtx)
	{
		TransformMatrix newMtx = (mtx != null) ? mtx : defaultModelMatrix;
		if (!modelMatrix.equals(newMtx)) {
			modelMatrix.set(newMtx);

			glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
			glBufferSubData(GL_UNIFORM_BUFFER, GLOBALS_UBO_POS_MODELMTX, modelMatrix.toFloatBuffer());
			glBindBuffer(GL_UNIFORM_BUFFER, 0);
		}
	}

	public static TransformMatrix pushModelMatrix()
	{
		assert (modelMatrix != null);
		modelMatrixStack.push(modelMatrix.deepCopy());
		return modelMatrix;
	}

	public static void popModelMatrix()
	{
		assert (!modelMatrixStack.isEmpty());
		setModelMatrix(modelMatrixStack.pop());
	}

	// --------------------------------------------------------------------------
	// save viewport size -- do NOT cache this

	public static void setViewport(int minX, int minY, int sizeX, int sizeY)
	{
		glViewport(minX, minY, sizeX, sizeY);
		glScissor(minX, minY, sizeX, sizeY);
		rec.glViewSizeX = sizeX;
		rec.glViewSizeY = sizeY;

		IntBuffer ib = BufferUtils.createIntBuffer(4);
		ib.put(minX);
		ib.put(minY);
		ib.put(sizeX);
		ib.put(sizeY);
		ib.rewind();

		glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
		glBufferSubData(GL_UNIFORM_BUFFER, GLOBALS_UBO_POS_VIEWPORT, ib);
		glBindBuffer(GL_UNIFORM_BUFFER, 0);
	}

	public static int getViewportSizeX()
	{ return rec.glViewSizeX; }

	public static int getViewportSizeY()
	{ return rec.glViewSizeY; }

	// --------------------------------------------------------------------------
	// global time

	public static void setTime(double time)
	{
		FloatBuffer fb = BufferUtils.createFloatBuffer(1);
		fb.put((float) time);
		fb.rewind();

		glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
		glBufferSubData(GL_UNIFORM_BUFFER, GLOBALS_UBO_POS_TIME, fb);
		glBindBuffer(GL_UNIFORM_BUFFER, 0);
	}

	// --------------------------------------------------------------------------
	// cache glPolygonMode

	public static final void initPolygonMode()
	{
		rec.glPolygonMode = PolygonMode.FILL;
		glPolygonMode(GL_FRONT_AND_BACK, rec.glPolygonMode.value);
	}

	public static final void setPolygonMode(PolygonMode mode)
	{
		if (rec.glPolygonMode != mode) {
			rec.glPolygonMode = mode;
			glPolygonMode(GL_FRONT_AND_BACK, mode.value);
		}
	}

	// --------------------------------------------------------------------------
	// cache glDepthMask

	public static final void initDepthWrite()
	{
		rec.glDepthMask = true;
		glDepthMask(true);
	}

	public static void setDepthWrite(boolean enabled)
	{
		if (rec.glDepthMask != enabled) {
			rec.glDepthMask = enabled;
			glDepthMask(enabled);
		}
	}

	// --------------------------------------------------------------------------
	// cache GL_DEPTH_TEST

	public static final void initDepthTest()
	{
		rec.enableDepthTest = true;
		glEnable(GL_DEPTH_TEST);
	}

	public static void enableDepthTest(boolean enabled)
	{
		if (rec.enableDepthTest == enabled)
			return;

		rec.enableDepthTest = enabled;
		if (enabled)
			glEnable(GL_DEPTH_TEST);
		else
			glDisable(GL_DEPTH_TEST);
	}

	// --------------------------------------------------------------------------
	// cache glDepthFunc

	public static final void initDepthFunc()
	{
		rec.glDepthFunc = GL_LEQUAL;
		glDepthFunc(GL_LEQUAL);
	}

	public static void setDepthFunc(int value)
	{
		if (rec.glDepthFunc != value) {
			rec.glDepthFunc = value;
			glDepthFunc(value);
		}
	}

	// --------------------------------------------------------------------------
	// cache face culling (caching not yet implemented)

	public static final void initCullFace()
	{
		glDisable(GL_CULL_FACE);
	}

	public static void setEnabledCullFace(boolean enabled)
	{
		if (enabled)
			glEnable(GL_CULL_FACE);
		else
			glDisable(GL_CULL_FACE);
	}

	// --------------------------------------------------------------------------
	// cache blend enabled and blend func

	public static final void initBlendEnabled()
	{
		rec.enableBlend = true;
		glEnable(GL_BLEND);
	}

	public static void enableBlend(boolean enabled)
	{
		if (rec.enableBlend == enabled)
			return;

		rec.enableBlend = enabled;
		if (enabled)
			glEnable(GL_BLEND);
		else
			glDisable(GL_BLEND);
	}

	public static final void initBlendFunc()
	{
		rec.blendSrcFactor = GL_SRC_ALPHA;
		rec.blendDestFactor = GL_ONE_MINUS_SRC_ALPHA;
		glBlendFunc(rec.blendSrcFactor, rec.blendDestFactor);
	}

	public static void setBlendFunc(int srcFactor, int destFactor)
	{
		if (srcFactor == rec.blendDestFactor && destFactor == rec.blendDestFactor)
			return;

		rec.blendSrcFactor = srcFactor;
		rec.blendDestFactor = destFactor;

		glBlendFunc(rec.blendSrcFactor, rec.blendDestFactor);
	}
}
