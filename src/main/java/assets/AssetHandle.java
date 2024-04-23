package assets;

import java.io.File;

public class AssetHandle extends File
{
	public final File assetDir;
	public final String assetPath; // relative path from assetDir

	public AssetHandle(AssetHandle other)
	{
		this(other.assetDir, other.assetPath);
	}

	public AssetHandle(File assetDir, String path)
	{
		super(assetDir, path);

		this.assetDir = assetDir;
		assetPath = path.replaceAll("\\\\", "/"); // resolve all paths with '/' as separator
	}
}
