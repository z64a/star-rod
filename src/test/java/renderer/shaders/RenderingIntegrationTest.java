package renderer.shaders;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.*;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import game.map.shape.TransformMatrix;
import renderer.FrameBuffer;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.TriangleRenderQueue;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.scene.ModelShader;
import renderer.shaders.scene.SpriteShader;

/** Runs against a real driver in a hidden window; opt in on machines with OpenGL. */
@EnabledIfEnvironmentVariable(named = "STAR_ROD_GL_TESTS", matches = "true")
class RenderingIntegrationTest
{
	@Test
	void shadersRenderLinesAndPaletteColorsWithCorrectFramebufferScaling()
	{
		assertTrue(glfwInit(), "GLFW initialization");
		long window = 0;
		try {
			glfwDefaultWindowHints();
			glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
			glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
			window = glfwCreateWindow(128, 128, "Star Rod rendering test", 0, 0);
			assertNotEquals(0, window, "OpenGL 3.3 context");
			glfwMakeContextCurrent(window);
			GL.createCapabilities();
			RenderState.init();
			RenderState.setDefaultFramebufferScale(2, 2);
			RenderState.setViewport(3, 5, 32, 32);
			assertArrayEquals(new int[] { 6, 10, 64, 64 }, integers(GL_VIEWPORT));
			assertArrayEquals(new int[] { 6, 10, 64, 64 }, integers(GL_SCISSOR_BOX));
			RenderState.setLineWidth(4);
			assertEquals(8, RenderState.getLineWidthPixels());
			assertEquals(6, RenderState.getPointSizePixels(3));

			FrameBuffer framebuffer = new FrameBuffer(true);
			try {
				framebuffer.bind(64, 64);
				framebuffer.setViewport(0, 0, 64, 64);
				assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
				assertArrayEquals(new int[] { 0, 0, 64, 64 }, integers(GL_VIEWPORT));
				assertEquals(4, RenderState.getLineWidthPixels());
				assertEquals(3, RenderState.getPointSizePixels(3));
				RenderState.setProjectionMatrix(TransformMatrix.identity());
				RenderState.setViewMatrix(null);
				RenderState.setModelMatrix(null);
				RenderState.enableDepthTest(false);
				RenderState.setEnabledCullFace(false);
				RenderState.enableBlend(false);
				glClearColor(0, 0, 0, 1);
				glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
				RenderState.setPolygonMode(PolygonMode.LINE);
				LineRenderQueue.addVertex().setPosition(-100000, 0, 0).setColor(255, 0, 0);
				LineRenderQueue.addVertex().setPosition(100000, 0, 0).setColor(255, 0, 0);
				LineRenderQueue.addLine(0, 1);
				LineRenderQueue.render(true);
				assertEquals(PolygonMode.LINE, RenderState.getPolygonMode());
				assertTrue(pixel(32, 32)[0] > 240, "Long clipped line remains visible");
				assertTrue(pixel(32, 33)[0] > 240, "Line has the requested width");
				assertEquals(0, pixel(32, 36)[0], "Line does not fill the screen");

				RenderState.setPolygonMode(PolygonMode.FILL);
				verifyPaletteFiltering();
				assertEquals(GL_NO_ERROR, glGetError(), "OpenGL error after rendering");
			}
			finally {
				framebuffer.unbind();
				framebuffer.delete();
			}
		}
		finally {
			RenderState.setDefaultFramebufferScale(1, 1);
			if (window != 0) {
				GL.setCapabilities(null);
				glfwMakeContextCurrent(0);
				glfwDestroyWindow(window);
			}
			glfwTerminate();
		}
	}

	private static void verifyPaletteFiltering()
	{
		ModelShader shader = ShaderManager.use(ModelShader.class);
		int image = glGenTextures(), palette = glGenTextures();
		try {
			shader.mainImg.bind(image);
			glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
			ByteBuffer indices = BufferUtils.createByteBuffer(2).put((byte) 0).put((byte) 255).flip();
			glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, 2, 1, 0, GL_RED, GL_UNSIGNED_BYTE, indices);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			shader.mainPal.bind(palette);
			ByteBuffer colors = BufferUtils.createByteBuffer(256 * 4);
			for (int i = 0; i < 256; i++)
				colors.put((byte) (i == 0 ? 255 : 0)).put((byte) (i > 0 && i < 255 ? 255 : 0))
					.put((byte) (i == 255 ? 255 : 0)).put((byte) 255);
			colors.flip();
			glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA8, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, colors);
			glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			shader.mainFmt.set(2);
			shader.textured.set(true);
			shader.useFiltering.set(true);
			// Constant UV lies halfway between red and blue texels. Filtering indices first would produce green.
			TriangleRenderQueue.addVertex().setPosition(-1, -1, 0).setUV(16, 0).setColor(255, 255, 255);
			TriangleRenderQueue.addVertex().setPosition(3, -1, 0).setUV(16, 0).setColor(255, 255, 255);
			TriangleRenderQueue.addVertex().setPosition(-1, 3, 0).setUV(16, 0).setColor(255, 255, 255);
			TriangleRenderQueue.addTriangle(0, 1, 2);
			TriangleRenderQueue.render(shader, true);
			int[] color = pixel(32, 32);
			assertEquals(128, color[0], 1);
			assertEquals(0, color[1]);
			assertEquals(128, color[2], 1);

			SpriteShader sprite = ShaderManager.use(SpriteShader.class);
			sprite.texture.bind(image);
			sprite.palette.bind(palette);
			RenderState.enableBlend(true);
			RenderState.setBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
			glClearColor(1, 1, 1, 1);
			glClear(GL_COLOR_BUFFER_BIT);
			sprite.color.set(0.0f, 0.0f, 0.0f, 64.0f / 255);
			drawSpriteSample(sprite);
			for (int channel : pixel(32, 32))
				assertEquals(191, channel, 1, "Spin blur tint and opacity over white");
			ShaderManager.use(SpriteShader.class);
			drawSpriteSample(sprite);
			assertArrayEquals(new int[] { 255, 0, 0 }, pixel(32, 32), "Tint must reset before the next sprite");
		}
		finally {
			glDeleteTextures(image);
			glDeleteTextures(palette);
		}
	}

	private static void drawSpriteSample(SpriteShader shader)
	{
		TriangleRenderQueue.addVertex().setPosition(-1, -1, 0).setUV(0, 0);
		TriangleRenderQueue.addVertex().setPosition(3, -1, 0).setUV(0, 0);
		TriangleRenderQueue.addVertex().setPosition(-1, 3, 0).setUV(0, 0);
		TriangleRenderQueue.addTriangle(0, 1, 2);
		TriangleRenderQueue.render(shader, true);
	}

	private static int[] integers(int name)
	{
		int[] values = new int[4];
		glGetIntegerv(name, values);
		return values;
	}

	private static int[] pixel(int x, int y)
	{
		ByteBuffer pixel = BufferUtils.createByteBuffer(4);
		glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
		return new int[] { pixel.get(0) & 255, pixel.get(1) & 255, pixel.get(2) & 255 };
	}
}
