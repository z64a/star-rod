package game.map.editor.render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.GL_MIRRORED_REPEAT;
import static renderer.buffers.BufferedMesh.VBO_COLOR;
import static renderer.buffers.BufferedMesh.VBO_INDEX;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import game.map.editor.geometry.Vector3f;

import assets.ExpectedAsset;
import game.map.editor.common.BaseCamera;
import game.map.shape.TransformMatrix;
import renderer.buffers.BufferedMesh;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicSolidShader;
import renderer.shaders.scene.BasicTexturedShader;
import util.Logger;

public class ShadowRenderer
{
	private static boolean initialized = false;
	private static int glCircularShadowTexture = -1;
	private static int glSquareShadowTexture = -1;

	private static BufferedMesh circleMesh;
	private static BufferedMesh squareMesh;

	private static int bindBufferedImage(BufferedImage image)
	{
		ByteBuffer buffer = TextureManager.createByteBuffer(image);

		int textureID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, textureID);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_MIRRORED_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_MIRRORED_REPEAT);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, image.getWidth(),
			image.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		return textureID;
	}

	// simple conversion for I-4 image --> black, alpha-modulated image suitable to be rendered as a shadow
	// since the texture dumped by decomp may have type TYPE_BYTE_GRAY, we create a new image of the desired type
	private static final BufferedImage convertImage(BufferedImage in)
	{
		int width = in.getWidth();
		int height = in.getHeight();
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int i = 0; i < height; i++)
			for (int j = 0; j < width; j++) {
				int pixel = in.getRGB(i, j);
				// all channels are the same, so just use blue for alpha and leave all channels zero
				int B = (pixel & 0xFF);
				out.setRGB(i, j, (B << 24));
			}
		return out;
	}

	public static void init()
	{
		if (initialized) {
			glDeleteTextures(glCircularShadowTexture);
			glDeleteTextures(glCircularShadowTexture);
		}

		try {
			File imgFile = ExpectedAsset.CIRCLE_SHADOW.getFile();
			BufferedImage image = ImageIO.read(imgFile);
			image = convertImage(image);
			glCircularShadowTexture = bindBufferedImage(image);
		}
		catch (IOException e) {
			Logger.logError("Unable to load asset " + ExpectedAsset.CIRCLE_SHADOW.getPath());
		}

		try {
			File imgFile = ExpectedAsset.SQUARE_SHADOW.getFile();
			BufferedImage image = ImageIO.read(imgFile);
			image = convertImage(image);
			glSquareShadowTexture = bindBufferedImage(image);
		}
		catch (IOException e) {
			Logger.logError("Unable to load asset " + ExpectedAsset.SQUARE_SHADOW.getPath());
		}

		if (glCircularShadowTexture == -1)
			makeCircleMesh();

		if (glSquareShadowTexture == -1)
			makeSquareMesh();

		initialized = true;
	}

	private static void draw(Vector3f pos, Vector3f norm, float height, boolean battle, boolean circularShadow, float baseScale)
	{
		Vector3f point = pos;
		float scale;
		if (battle)
			scale = 0.13f - (height / 2600);
		else
			scale = 0.12f - (height / 3600);
		if (scale < 0.01f)
			scale = 0.01f;

		scale = baseScale * scale;

		TransformMatrix mtx = TransformMatrix.identity();

		// rotation from up=(0,1,0) to normal=(nx,ny,nz)
		// axis  = cross(N, N')
		// angle = arccos(dot(N, N') / (|N| * |N'|))
		if (norm != null) {
			float rotAngle = (float) Math.toDegrees(Math.acos(norm.y));
			mtx.rotate(norm.z, 0.0f, -norm.x, rotAngle);
		}
		mtx.scale(scale);
		mtx.translate(point.x, point.y + 0.1f, point.z);

		RenderState.setPolygonMode(PolygonMode.FILL);
		RenderState.setDepthWrite(false);

		int texID = circularShadow ? glCircularShadowTexture : glSquareShadowTexture;
		if (texID == -1) {
			ShaderManager.use(BasicSolidShader.class);
			BufferedMesh fallbackMesh = circularShadow ? circleMesh : squareMesh;
			fallbackMesh.renderWithTransform(mtx);
		}
		else {
			BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
			shader.texture.bind(texID);
			shader.baseColor.set(1.0f, 1.0f, 1.0f, 75 / 255.0f);
			shader.multiplyBaseColor.set(true);

			shader.setXZQuadCoords(-1.25f, -1.25f, 1.25f, 1.25f, 0);
			shader.setQuadTexCoords(0.0f, -1.0f, 2.0f, 1.0f);

			shader.renderQuad(mtx);
		}

		RenderState.setDepthWrite(true);
	}

	private static void makeCircleMesh()
	{
		circleMesh = new BufferedMesh(VBO_INDEX | VBO_COLOR);

		int N = 16;
		int[] ring = new int[N + 1];

		circleMesh.addVertex().setPosition(0, 0, 0).setColor(0, 0, 0, 0.5f).getIndex();

		for (int i = 0; i <= N; i++) {
			float x = (float) Math.cos(2 * i * Math.PI / N);
			float z = (float) Math.sin(2 * i * Math.PI / N);
			ring[i] = circleMesh.addVertex().setPosition(x, 0, z).setColor(0, 0, 0, 0).getIndex();
		}

		circleMesh.addFan(0, ring);
		circleMesh.loadBuffers();
	}

	private static void makeSquareMesh()
	{
		squareMesh = new BufferedMesh(VBO_INDEX | VBO_COLOR);

		float innerSize = 0.5f;
		float innerMostAlpha = 0.4f;
		float innerAlpha = 0.4f;

		int o = squareMesh.addVertex().setPosition(0, 0, 0).setColor(0, 0, 0, innerMostAlpha).getIndex();
		int mm = squareMesh.addVertex().setPosition(-innerSize, 0, -innerSize).setColor(0, 0, 0, innerAlpha).getIndex();
		int pm = squareMesh.addVertex().setPosition(innerSize, 0, -innerSize).setColor(0, 0, 0, innerAlpha).getIndex();
		int mp = squareMesh.addVertex().setPosition(-innerSize, 0, innerSize).setColor(0, 0, 0, innerAlpha).getIndex();
		int pp = squareMesh.addVertex().setPosition(innerSize, 0, innerSize).setColor(0, 0, 0, innerAlpha).getIndex();
		int MM = squareMesh.addVertex().setPosition(-1, 0, -1).setColor(0, 0, 0, 0).getIndex();
		int PM = squareMesh.addVertex().setPosition(1, 0, -1).setColor(0, 0, 0, 0).getIndex();
		int MP = squareMesh.addVertex().setPosition(-1, 0, 1).setColor(0, 0, 0, 0).getIndex();
		int PP = squareMesh.addVertex().setPosition(1, 0, 1).setColor(0, 0, 0, 0).getIndex();

		squareMesh.addFan(o, pp, pm, mm, mp, pp);
		squareMesh.addQuad(MM, MP, mp, mm);
		squareMesh.addQuad(MP, PP, pp, mp);
		squareMesh.addQuad(PP, PM, pm, pp);
		squareMesh.addQuad(PM, MM, mm, pm);

		squareMesh.loadBuffers();
	}

	public static class RenderableShadow implements SortedRenderable
	{
		private final Vector3f pos;
		private final Vector3f norm;
		private final float height;
		private final float baseScale;
		private final boolean battle;
		private final boolean circularShadow;
		private int depth;

		public RenderableShadow(Vector3f pos, Vector3f norm, float height, boolean battle, boolean circularShadow, float baseScale)
		{
			this.pos = pos;
			this.norm = norm;
			this.height = height;
			this.battle = battle;
			this.circularShadow = circularShadow;
			this.baseScale = baseScale;
		}

		@Override
		public RenderMode getRenderMode()
		{
			return RenderMode.SURF_XLU_AA; //TODO should really be RenderMode.SHADOW;
		}

		@Override
		public Vector3f getCenterPoint()
		{ return pos; }

		@Override
		public void render(RenderingOptions opts, BaseCamera camera)
		{
			ShadowRenderer.draw(pos, norm, height, battle, circularShadow, baseScale);
		}

		@Override
		public void setDepth(int normalizedDepth)
		{ depth = normalizedDepth; }

		@Override
		public int getDepth()
		{ return depth; }
	}
}
