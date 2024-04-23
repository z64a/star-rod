package renderer.shaders.postprocess;

import renderer.FrameBuffer;
import renderer.shaders.BaseShader;
import renderer.shaders.components.TexUnit2D;
import renderer.shaders.components.UniformFloatVector;
import renderer.shaders.components.UniformInt;

public abstract class PostProcessShader extends BaseShader
{
	public final TexUnit2D scene;
	public final TexUnit2D texture;
	public final TexUnit2D depth;
	public final TexUnit2D lut;
	public final UniformInt pass;
	public final UniformFloatVector sceneViewport;
	public final UniformFloatVector inViewport;
	public final UniformFloatVector resolution;

	public PostProcessShader(String name, String frag)
	{
		super(name, VS_VERT, frag);

		scene = new TexUnit2D(program, 0, "u_scene");
		texture = new TexUnit2D(program, 1, "u_image");
		depth = new TexUnit2D(program, 2, "u_depth");
		lut = new TexUnit2D(program, 3, "u_lut");

		pass = new UniformInt(program, "u_pass", 0);
		sceneViewport = new UniformFloatVector(program, "u_sceneViewport", 0.0f, 0.0f, 1.0f, 1.0f);
		inViewport = new UniformFloatVector(program, "u_viewport", 0.0f, 0.0f, 1.0f, 1.0f);
		resolution = new UniformFloatVector(program, "u_resolution", 1.0f, 1.0f);
	}

	public void apply(int pass, FrameBuffer sceneBuffer, FrameBuffer outBuffer, FrameBuffer inBuffer, float time)
	{
		if (outBuffer != null) {
			int outStartPosX = 0;
			int outStartPosY = 0;

			int baseSizeX = useFixedSizeDownsamples() ? 800 : sceneBuffer.sizeX;
			int baseSizeY = useFixedSizeDownsamples() ? 600 : sceneBuffer.sizeY;

			int outSizeX = baseSizeX;
			int outSizeY = baseSizeY;
			switch (getViewportLevel(pass)) {
				case 0:
					outStartPosX = 0;
					outStartPosY = 0;
					outSizeX = baseSizeX;
					outSizeY = baseSizeY;
					break;
				case 1:
					outStartPosX = baseSizeX;
					outStartPosY = 0;
					outSizeX = baseSizeX / 2;
					outSizeY = baseSizeY / 2;
					break;
				case 2:
					outStartPosX = baseSizeX;
					outStartPosY = baseSizeY / 2;
					outSizeX = baseSizeX / 4;
					outSizeY = baseSizeY / 4;
					break;
				case 3:
					outStartPosX = baseSizeX;
					outStartPosY = (baseSizeY / 2) + (baseSizeY / 4);
					outSizeX = baseSizeX / 8;
					outSizeY = baseSizeY / 8;
					break;
				case 4:
					outStartPosX = baseSizeX;
					outStartPosY = (baseSizeY / 2) + (baseSizeY / 4) + (baseSizeY / 8);
					outSizeX = baseSizeX / 16;
					outSizeY = baseSizeY / 16;
					break;
				case 5:
					outStartPosX = 0;
					outStartPosY = 0;
					outSizeX = Math.round(baseSizeX * 1.5f);
					outSizeY = baseSizeY;
					break;
			}

			outBuffer.bind(outSizeX, outSizeY);
			outBuffer.setViewport(outStartPosX, outStartPosY, outSizeX, outSizeY);
		}

		float su1 = (float) sceneBuffer.viewMinX / sceneBuffer.sizeX;
		float sv1 = (float) sceneBuffer.viewMinY / sceneBuffer.sizeY;
		float su2 = (float) sceneBuffer.viewMaxX / sceneBuffer.sizeX;
		float sv2 = (float) sceneBuffer.viewMaxY / sceneBuffer.sizeY;

		float u1 = (float) inBuffer.viewMinX / inBuffer.sizeX;
		float v1 = (float) inBuffer.viewMinY / inBuffer.sizeY;
		float u2 = (float) inBuffer.viewMaxX / inBuffer.sizeX;
		float v2 = (float) inBuffer.viewMaxY / inBuffer.sizeY;

		useProgram(true);
		this.pass.set(pass);

		scene.bind(sceneBuffer.getColorTexture());
		depth.bind(sceneBuffer.getDepthTexture());
		texture.bind(inBuffer.getColorTexture());

		sceneViewport.set(su1, sv1, su2, sv2);
		if (outBuffer != null)
			resolution.set(outBuffer.sizeX, outBuffer.sizeY);
		else
			resolution.set(sceneBuffer.sizeX, sceneBuffer.sizeY);
		inViewport.set(u1, v1, u2, v2);
		setAdditionalUniforms();
		setXYQuadCoords(0, 0, 1, 1, -1);
		setQuadTexCoords(u1, v1, u2, v2);
		renderQuad();
	}

	// optional for subclasses to implement
	protected void setAdditionalUniforms()
	{}

	// allow subclasses to downsample or upsample depending on pass
	protected boolean useFixedSizeDownsamples()
	{
		return false;
	}

	// allow subclasses to downsample or upsample depending on pass
	protected int getViewportLevel(int pass)
	{
		return 0;
	}
}
