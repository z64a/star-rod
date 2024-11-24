package game.map.editor.geometry.primitive;

import common.Vector3f;
import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.shape.TriangleBatch;

public abstract class ShapeGenerator
{
	public enum Primitive
	{
		// @formatter:off
		CUBE			("Cube"),
		CUBE_BEVEL		("Beveled Cube"),
		CYLINDER		("Cylinder"),
		CONE			("Cone"),
		RING			("Ring"),
		SPHERE			("Sphere"),
		HEMISPHERE		("Hemisphere"),
		TORUS			("Torus"),
		PLANAR_GRID		("Planar Grid"),
		RADIAL_GRID		("Radial Grid"),
		STAIR			("Staircase"),
		SPIRAL_STAIR	("Spiral Staircase"),
		SPIRAL_RAMP		("Spiral Ramp");
		// @formatter:on

		public final String name;

		private Primitive(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static final String RENDER_PREVIEW_NAME = "new_primitive";
	public static final int UV_SCALE = 1024; //TODO

	public final Primitive type;

	protected ShapeGenerator(Primitive type)
	{
		this.type = type;
	}

	public TriangleBatch generateTriangles(Vector3f pos)
	{
		if (pos == null)
			return generateTriangles(0, 0, 0);
		else
			return generateTriangles((int) pos.x, (int) pos.y, (int) pos.z);
	}

	public TriangleBatch generateTriangles(int centerX, int centerY, int centerZ)
	{
		return generate(centerX, centerY, centerZ);
	}

	public abstract void addFields(GeneratePrimitiveOptionsDialog panel);

	public abstract void setVisible(boolean b);

	public abstract TriangleBatch generate(int centerX, int centerY, int centerZ);
}
