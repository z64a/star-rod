package renderer.buffers;

public class CubeMesh extends BufferedMesh
{
	// @formatter:off
	public CubeMesh()
	{
		super(0, 0, VBO_UV);

		int v1, v2, v3, v4;

		// left
		v1 = addVertex().setPosition(-1.0f, -1.0f, -1.0f).setUV(0.0f, 0.0f).getIndex();
		v2 = addVertex().setPosition(-1.0f,  1.0f, -1.0f).setUV(0.0f, 1.0f).getIndex();
		v3 = addVertex().setPosition(-1.0f,  1.0f,  1.0f).setUV(1.0f, 1.0f).getIndex();
		v4 = addVertex().setPosition(-1.0f, -1.0f,  1.0f).setUV(1.0f, 0.0f).getIndex();
		addQuad(v1, v2, v3, v4);

		// front
		v1 = addVertex().setPosition( 1.0f, -1.0f, -1.0f).setUV(1.0f, 0.0f).getIndex();
		v2 = addVertex().setPosition( 1.0f,  1.0f, -1.0f).setUV(1.0f, 1.0f).getIndex();
		v3 = addVertex().setPosition(-1.0f,  1.0f, -1.0f).setUV(2.0f, 1.0f).getIndex();
		v4 = addVertex().setPosition(-1.0f, -1.0f, -1.0f).setUV(2.0f, 0.0f).getIndex();
		addQuad(v1, v2, v3, v4);

		// right
		v1 = addVertex().setPosition( 1.0f, -1.0f,  1.0f).setUV(2.0f, 0.0f).getIndex();
		v2 = addVertex().setPosition( 1.0f,  1.0f,  1.0f).setUV(2.0f, 1.0f).getIndex();
		v3 = addVertex().setPosition( 1.0f,  1.0f, -1.0f).setUV(3.0f, 1.0f).getIndex();
		v4 = addVertex().setPosition( 1.0f, -1.0f, -1.0f).setUV(3.0f, 0.0f).getIndex();
		addQuad(v1, v2, v3, v4);

		// back
		v1 = addVertex().setPosition(-1.0f, -1.0f,  1.0f).setUV(3.0f, 0.0f).getIndex();
		v2 = addVertex().setPosition(-1.0f,  1.0f,  1.0f).setUV(3.0f, 1.0f).getIndex();
		v3 = addVertex().setPosition( 1.0f,  1.0f,  1.0f).setUV(4.0f, 1.0f).getIndex();
		v4 = addVertex().setPosition( 1.0f, -1.0f,  1.0f).setUV(4.0f, 0.0f).getIndex();
		addQuad(v1, v2, v3, v4);

		// bottom
		v1 = addVertex().setPosition(-1.0f, -1.0f, -1.0f).setUV(1.0f, 1.0f).getIndex();
		v2 = addVertex().setPosition( 1.0f, -1.0f, -1.0f).setUV(0.0f, 1.0f).getIndex();
		v3 = addVertex().setPosition( 1.0f, -1.0f,  1.0f).setUV(0.0f, 0.0f).getIndex();
		v4 = addVertex().setPosition(-1.0f, -1.0f,  1.0f).setUV(1.0f, 0.0f).getIndex();
		addQuad(v1, v2, v3, v4);

		// top
		v1 = addVertex().setPosition(-1.0f,  1.0f, -1.0f).setUV(0.0f, 1.0f).getIndex();
		v2 = addVertex().setPosition( 1.0f,  1.0f, -1.0f).setUV(1.0f, 1.0f).getIndex();
		v3 = addVertex().setPosition( 1.0f,  1.0f,  1.0f).setUV(1.0f, 0.0f).getIndex();
		v4 = addVertex().setPosition(-1.0f,  1.0f,  1.0f).setUV(0.0f, 0.0f).getIndex();
		addQuad(v1, v2, v3, v4);

		loadBuffers();
	}
	// @formatter:on
}
