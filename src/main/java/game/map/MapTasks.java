package game.map;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;

import app.Environment;
import assets.AssetExtractor;
import assets.AssetManager;
import game.map.compiler.CollisionCompiler;
import game.map.compiler.GeometryCompiler;

/** The -DumpMaps and -BuildMap command line tasks. */
public class MapTasks
{
	/** Whether the first command line argument names a map task. */
	public static boolean isMapTask(String[] args)
	{
		return args.length > 0 && switch (args[0].toUpperCase()) {
			case "-DUMPMAPS", "-BUILDMAP" -> true;
			default -> false;
		};
	}

	/**
	 * Runs the task named by args[index] and returns the number of arguments it consumed.
	 * Exits with a non-zero status if the task fails.
	 */
	public static int run(String[] args, int index)
	{
		try {
			if (args[index].equalsIgnoreCase("-DumpMaps")) {
				AssetExtractor.extractMaps(AssetManager.getBaseAssetDir());
				return 0;
			}

			if (args.length <= index + 2)
				throw new IOException(args[index] + " expects <map source> <out dir>");
			build(new File(args[index + 1]), new File(args[index + 2]));
			return 2;
		}
		catch (IOException | RuntimeException e) {
			System.err.println(e.getMessage());
			Environment.exit(1);
			return 0;
		}
	}

	/**
	 * Compiles a map source into outDir as name_shape.bin and name_hit.bin, with headers giving the index of
	 * each model, collider, and zone. Files whose contents are unchanged are left untouched, and name.d lists
	 * the files that were read.
	 */
	public static void build(File source, File outDir) throws IOException
	{
		if (!source.isFile())
			throw new IOException(source.getPath() + ": file not found");

		Map map = Map.loadMap(source);
		String name = map.getName();
		Set<File> inputs = new TreeSet<>(List.of(source));
		File features = new File(map.getProjDir(), "features.json");
		if (features.isFile())
			inputs.add(features);

		FileUtils.forceMkdir(outDir);
		File tempDir = Files.createTempDirectory(outDir.toPath(), name).toFile();
		Set<File> outputs = new TreeSet<>();
		try {
			new GeometryCompiler(map, new File(tempDir, name + "_shape.bin"), new File(tempDir, name + "_shape.h"));
			new CollisionCompiler(map, new File(tempDir, name + "_hit.bin"), new File(tempDir, name + "_hit.h"));

			for (File built : tempDir.listFiles()) {
				File output = new File(outDir, built.getName());
				moveIfChanged(built, output);
				outputs.add(output);
			}
		}
		finally {
			FileUtils.deleteQuietly(tempDir);
		}

		writeDepfile(new File(outDir, name + ".d"), outputs, inputs);
	}

	private static void moveIfChanged(File from, File to) throws IOException
	{
		if (to.isFile() && Arrays.equals(Files.readAllBytes(from.toPath()), Files.readAllBytes(to.toPath())))
			return;
		Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	private static void writeDepfile(File depfile, Set<File> outputs, Set<File> inputs) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		for (File output : outputs)
			sb.append(escape(output)).append(" ");
		sb.append(":");
		for (File input : inputs)
			sb.append(" \\\n  ").append(escape(input));
		FileUtils.writeStringToFile(depfile, sb.append("\n").toString(), StandardCharsets.UTF_8);
	}

	private static String escape(File file)
	{
		return file.toPath().normalize().toString().replace(" ", "\\ ");
	}
}
