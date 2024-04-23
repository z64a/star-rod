package game.texture;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import app.input.IOUtils;
import app.input.InputFileException;

public class TextureArchive
{
	public final String name;
	public final List<Texture> textureList;

	private TextureArchive(String name)
	{
		this.name = name;
		textureList = new LinkedList<>();
	}

	public static class JsonTile
	{
		String format;
		String hwrap;
		String vwrap;
	}

	public static class JsonTexture
	{
		String name;
		String ext;
		JsonTile main;
		JsonTile aux;
		String combine;
		boolean filter;
		boolean hasMipmaps;
		boolean variant;
	}

	public static TextureArchive load(File texFile) throws IOException
	{
		String texName = FilenameUtils.getBaseName(texFile.getName());
		TextureArchive ta = new TextureArchive(texName);

		Gson gson = new Gson();
		JsonReader jsonReader = new JsonReader(new FileReader(texFile));
		JsonTexture[] jsonTextures = gson.fromJson(jsonReader, JsonTexture[].class);

		for (JsonTexture tex : jsonTextures) {
			ta.textureList.add(Texture.parseTexture(texFile, tex));
		}

		return ta;
	}

	public static TextureArchive loadLegacy(File texFile) throws IOException
	{
		String texName = FilenameUtils.getBaseName(texFile.getName());
		TextureArchive ta = new TextureArchive(texName);

		File parentDirectory = texFile.getParentFile();
		String subdir = parentDirectory.getAbsolutePath() + "/" + texName + "/";
		List<String> lines = IOUtils.readFormattedTextFile(texFile, false);

		Iterator<String> iter = lines.iterator();
		while (iter.hasNext()) {
			String line = iter.next();
			if (!line.startsWith("tex:"))
				throw new InputFileException(texFile, "Invalid texture declaration: " + line);

			String[] tokens = line.split(":\\s*");
			if (tokens.length != 2)
				throw new InputFileException(texFile, "Invalid texture name: " + line);

			String name = tokens[1];

			line = iter.next();
			if (!line.equals("{"))
				throw new InputFileException(texFile, "Texture " + name + " is missing open curly bracket.");

			int curlyBalance = 1;
			List<String> textureLines = new LinkedList<>();

			while (true) {
				if (!iter.hasNext())
					throw new InputFileException(texFile, "Texture " + name + " is missing closed curly bracket.");

				line = iter.next();
				if (line.equals("{"))
					curlyBalance++;
				else if (line.equals("}")) {
					curlyBalance--;
					if (curlyBalance == 0)
						break;
				}
				if (line.startsWith("tex:"))
					throw new InputFileException(texFile, "Texture " + name + " is missing closed curly bracket.");

				textureLines.add(line);
			}

			Texture tx = Texture.parseTexture(texFile, subdir, name, textureLines);
			ta.textureList.add(tx);
		}

		return ta;
	}
}
