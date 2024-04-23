package renderer.shaders.components;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1i;

public class TexUnit2D
{
	private final String name;
	private final int location;
	private final int texUnitID;

	public TexUnit2D(int program, int texUnit, String imgName)
	{
		location = glGetUniformLocation(program, imgName);
		this.texUnitID = texUnit;
		this.name = imgName;
	}

	public void bind(int glTexID)
	{
		glActiveTexture(GL_TEXTURE0 + texUnitID);
		glBindTexture(GL_TEXTURE_2D, glTexID);
		glUniform1i(location, texUnitID);
	}

	public void makeActive()
	{
		glActiveTexture(GL_TEXTURE0 + texUnitID);
	}

	public void print()
	{
		System.out.println(name);
		System.out.println(" [" + texUnitID + "] = " + location);
	}
}
