package renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT32;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.glFramebufferTexture;

import java.nio.ByteBuffer;

import renderer.shaders.RenderState;

public final class FrameBuffer
{
	private final int frameBuffer;
	private final boolean hasDepth;

	private int colorTexture;
	private int depthTexture;

	public int sizeX;
	public int sizeY;

	public int viewMinX;
	public int viewMinY;
	public int viewMaxX;
	public int viewMaxY;

	public FrameBuffer(boolean hasDepth)
	{
		this.hasDepth = hasDepth;

		frameBuffer = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer);
		glDrawBuffer(GL_COLOR_ATTACHMENT0);

		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}

	public void bind(int sizeX, int sizeY)
	{
		glBindTexture(GL_TEXTURE_2D, 0); // make sure the texture isn't bound
		glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer);

		if (sizeX > this.sizeX || sizeY > this.sizeY) {
			glDeleteTextures(colorTexture);
			glDeleteTextures(depthTexture);

			colorTexture = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, colorTexture);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, sizeX, sizeY,
				0, GL_RGB, GL_UNSIGNED_BYTE, (ByteBuffer) null);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, colorTexture, 0);

			if (hasDepth) {
				depthTexture = glGenTextures();
				glBindTexture(GL_TEXTURE_2D, depthTexture);
				glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32, sizeX, sizeY,
					0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
				glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTexture, 0);
			}

			this.sizeX = sizeX;
			this.sizeY = sizeY;
		}
	}

	public void delete()
	{
		glDeleteFramebuffers(frameBuffer);
		glDeleteTextures(colorTexture);
		glDeleteTextures(depthTexture);
	}

	public void unbind()
	{
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}

	public int getFrameBuffer()
	{
		return frameBuffer;
	}

	public int getColorTexture()
	{
		return colorTexture;
	}

	public int getDepthTexture()
	{
		return depthTexture;
	}

	public void setViewport(int minX, int minY, int sizeX, int sizeY)
	{
		RenderState.setViewport(minX, minY, sizeX, sizeY);
		viewMinX = minX;
		viewMinY = minY;
		viewMaxX = minX + sizeX;
		viewMaxY = minY + sizeY;
	}
}
