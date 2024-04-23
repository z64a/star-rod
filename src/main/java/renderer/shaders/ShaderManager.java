package renderer.shaders;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import util.Logger;

public abstract class ShaderManager
{
	// maintain a set of instances for each shader class
	private static HashMap<Class<? extends BaseShader>, BaseShader> instanceMap;

	protected static void init()
	{
		instanceMap = new HashMap<>();
	}

	@SuppressWarnings("unchecked")
	public static <T extends BaseShader> T get(Class<T> cls)
	{
		BaseShader shader = instanceMap.get(cls);
		if (shader == null) {
			try {
				shader = cls.getDeclaredConstructor().newInstance();
				instanceMap.put(cls, shader);
			}
			catch (Exception e) {
				throw new StarRodException("Failed to create shader %s %n%s", cls.getSimpleName(), e.getMessage());
			}
		}

		return (T) shader;
	}

	public static <T extends BaseShader> T use(Class<T> cls)
	{
		T shader = get(cls);
		if (shader != null)
			shader.useProgram(true);
		return shader;
	}

	protected static int createShader(String name, String vert, String frag)
	{
		Logger.log("Compiling shader: " + name);

		int vertShader = createShader(vert, GL_VERTEX_SHADER);
		int fragShader = createShader(frag, GL_FRAGMENT_SHADER);
		int programID = glCreateProgram();

		glAttachShader(programID, vertShader);
		glAttachShader(programID, fragShader);

		glLinkProgram(programID);

		if (glGetProgrami(programID, GL_LINK_STATUS) == GL_FALSE) {
			glDeleteProgram(programID);
			throw new StarRodException("Failed to link shader: %n%s", getInfoLogMessage(programID));
		}

		glDetachShader(programID, vertShader);
		glDetachShader(programID, fragShader);

		glDeleteShader(fragShader);
		glDeleteShader(vertShader);

		return programID;
	}

	private static int createShader(String resourceName, int type)
	{
		String typename = "shader";
		if (type == GL_VERTEX_SHADER)
			typename = "vertex shader";
		else if (type == GL_FRAGMENT_SHADER)
			typename = "fragment shader";

		InputStream is = Resource.getStream(ResourceType.Shader, resourceName);
		if (is == null)
			throw new StarRodException("Unable to find %s: %s", typename, resourceName);

		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
			String line;
			while ((line = reader.readLine()) != null)
				sb.append(line).append('\n');
		}
		catch (IOException e) {
			throw new StarRodException("IOException while reading %s %s %n%s", typename, resourceName, e.getMessage());
		}

		int shaderID = glCreateShader(type);
		glShaderSource(shaderID, sb.toString());
		glCompileShader(shaderID);

		int logLen = glGetShaderi(shaderID, GL_INFO_LOG_LENGTH);
		int status = glGetShaderi(shaderID, GL_COMPILE_STATUS);

		String[] log = null;
		if (logLen > 1) {
			String s = glGetShaderInfoLog(shaderID, logLen);
			if (s != null && s.length() != 0)
				log = s.split("\r?\n");
		}

		if (status == GL_FALSE) {
			if (log != null) {
				for (String line : log) {
					if (line.contains("warning"))
						Logger.logWarning(line);
					if (line.contains("error"))
						Logger.logError(line);
				}
			}

			String k = getInfoLogMessage(shaderID);
			glDeleteShader(shaderID);
			throw new StarRodException("Failed to compile %s %s %n%s", typename, resourceName, k);
		}

		return shaderID;
	}

	private static String getInfoLogMessage(int shaderID)
	{
		int messageLength = glGetShaderi(shaderID, GL_INFO_LOG_LENGTH);
		return glGetShaderInfoLog(shaderID, messageLength);
	}
}
